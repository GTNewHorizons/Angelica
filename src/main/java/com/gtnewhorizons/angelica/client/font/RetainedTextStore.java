package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizons.angelica.glsm.FeedbackManager;
import com.gtnewhorizons.angelica.glsm.GLSMBuffers;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.backend.RenderBackend;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;
import java.util.Arrays;

final class RetainedTextStore {

    private static final int INITIAL_QUADS = 4096;
    private static final long EVICT_AFTER_NANOS = 5_000_000_000L;

    // Retired regions and buffers wait MAX_FRAMES_IN_FLIGHT frames: SDL-GPU runs a frame's uploads before its draws, so reusing one sooner overwrites bytes an in-flight draw still reads.
    private static final QuadRegionAllocator allocator = new QuadRegionAllocator(0);
    private static final ObjectArrayList<TextMesh> resident = new ObjectArrayList<>();
    private static int buffer;
    private static int capacityQuads = INITIAL_QUADS;
    private static int generation;
    private static long frame;
    private static long frameNanos = System.nanoTime();
    private static int[] retiredBuffers = new int[4];
    private static long[] retiredBufferFrames = new long[4];
    private static int retiredBufferCount;

    private RetainedTextStore() {}

    static int buffer() {
        return buffer;
    }

    static boolean ensure(TextMesh mesh) {
        if (!GLStateManager.isMainThread() || mesh.quadCount > BatchingFontRenderer.QUAD_BUDGET || GLStateManager.isRecordingDisplayList() || FeedbackManager.isFeedbackMode()) {
            return false;
        }
        if (mesh.regionStart >= 0 && mesh.regionGeneration == generation) {
            mesh.lastUsedNanos = frameNanos;
            return true;
        }
        final int quads = mesh.quadCount;
        if (buffer == 0) {
            create(Math.max(capacityQuads, quads));
        }
        int start = allocator.allocate(quads);
        if (start < 0) {
            int capacity = capacityQuads * 2;
            while (capacity < quads) capacity *= 2;
            retireBuffer();
            create(capacity);
            start = allocator.allocate(quads);
        }
        upload(mesh, start);
        mesh.regionStart = start;
        mesh.regionQuads = quads;
        mesh.regionGeneration = generation;
        mesh.lastUsedNanos = frameNanos;
        resident.add(mesh);
        return true;
    }

    static void release(TextMesh mesh) {
        if (mesh.regionStart >= 0 && mesh.regionGeneration == generation) {
            final int i = resident.indexOf(mesh);
            if (i >= 0) {
                evict(i);
                return;
            }
        }
        mesh.regionStart = -1;
        mesh.regionQuads = 0;
    }

    static void endFrame() {
        frame++;
        final long now = System.nanoTime();
        frameNanos = now;
        allocator.advance(frame);
        if (retiredBufferCount > 0) {
            deleteExpiredBuffers();
        }
        if (resident.isEmpty()) {
            retireBuffer();
            return;
        }
        for (int i = resident.size() - 1; i >= 0; i--) {
            if (now - resident.get(i).lastUsedNanos > EVICT_AFTER_NANOS) {
                evict(i);
            }
        }
        if (resident.isEmpty()) {
            retireBuffer();
        }
    }

    private static void evict(int index) {
        final TextMesh mesh = resident.get(index);
        allocator.retire(mesh.regionStart, mesh.regionQuads, frame);
        final TextMesh last = resident.pop();
        if (index < resident.size()) {
            resident.set(index, last);
        }
        mesh.regionStart = -1;
        mesh.regionQuads = 0;
    }

    private static void create(int capacity) {
        final int previous = GLStateManager.ctx().boundVBO;
        buffer = GLSMBuffers.glGenBuffers();
        GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        GLSMBuffers.glBufferData(GL15.GL_ARRAY_BUFFER, (long) capacity * BatchingFontRenderer.QUAD_BYTES, GL15.GL_DYNAMIC_DRAW);
        GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, previous);
        capacityQuads = capacity;
        allocator.reset(capacity);
        resident.clear();
        generation++;
    }

    private static void upload(TextMesh mesh, int start) {
        final int previous = GLStateManager.ctx().boundVBO;
        final ByteBuffer vertices = mesh.vertices;
        vertices.position(0);
        vertices.limit(mesh.quadCount * BatchingFontRenderer.QUAD_BYTES);
        GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        GLSMBuffers.glBufferSubData(GL15.GL_ARRAY_BUFFER, (long) start * BatchingFontRenderer.QUAD_BYTES, vertices);
        GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, previous);
        vertices.clear();
    }

    private static void retireBuffer() {
        if (buffer == 0) return;
        if (retiredBufferCount == retiredBuffers.length) {
            retiredBuffers = Arrays.copyOf(retiredBuffers, retiredBufferCount * 2);
            retiredBufferFrames = Arrays.copyOf(retiredBufferFrames, retiredBufferCount * 2);
        }
        retiredBuffers[retiredBufferCount] = buffer;
        retiredBufferFrames[retiredBufferCount] = frame;
        retiredBufferCount++;
        buffer = 0;
        resident.clear();
        generation++;
    }

    private static void deleteExpiredBuffers() {
        final long deletableUpTo = frame - RenderBackend.MAX_FRAMES_IN_FLIGHT;
        int kept = 0;
        for (int i = 0; i < retiredBufferCount; i++) {
            final int id = retiredBuffers[i];
            if (retiredBufferFrames[i] <= deletableUpTo) {
                BatchingFontRenderer.forgetVertexSource(id);
                GLSMBuffers.glDeleteBuffers(id);
            } else {
                retiredBuffers[kept] = id;
                retiredBufferFrames[kept] = retiredBufferFrames[i];
                kept++;
            }
        }
        retiredBufferCount = kept;
    }
}
