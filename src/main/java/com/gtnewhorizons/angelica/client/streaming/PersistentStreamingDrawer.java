package com.gtnewhorizons.angelica.client.streaming;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL44;

import java.nio.ByteBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.*;

/**
 * Triple-buffered persistent VAO/VBO streamer.
 * <br>
 * Unlike {@link com.gtnewhorizons.angelica.glsm.streaming.PersistentStreamingBuffer},
 * this provides direct writes into persistently mapped GPU memory, bypassing
 * temporary upload buffers for lower-overhead streaming.
 */
public final class PersistentStreamingDrawer extends StreamingDrawer {

    private static final int NUM_SECTIONS = 4;

    private static final int FLAGS =
        GL30.GL_MAP_WRITE_BIT
            | GL44.GL_MAP_PERSISTENT_BIT
            | GL44.GL_MAP_COHERENT_BIT;

    private int sectionSize;
    //private int totalCapacity;
    private ByteBuffer persistentMapping;
    private long baseAddress;
    private final long[] fences = new long[NUM_SECTIONS];
    private int currentSection;
    private int sectionWriteOffset;
    private int sectionWriteStart;
    private long sectionWritePointer;
    private int sectionDrawCount;

    private static int globalFPSCount;

    PersistentStreamingDrawer(int stride, int elementCapacity, VAOConsumer initVAO) {
        super(initVAO, stride);
        sectionSize   = elementCapacity * stride;
        final int totalCapacity = getTotalCapacity();

        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
        GLStateManager.glBufferStorage(GL15.GL_ARRAY_BUFFER, totalCapacity, FLAGS);

        persistentMapping = GLStateManager.glMapBufferRange(
            GL15.GL_ARRAY_BUFFER, 0, totalCapacity, FLAGS);
        baseAddress = memAddress0(persistentMapping);
        sectionWritePointer = baseAddress;

        if (persistentMapping == null)
            throw new RuntimeException("Persistent mapping failed — check ARB_buffer_storage support.");
    }

    public static void incrementFPS() { //TODO make this use Display
        globalFPSCount = (globalFPSCount + 1) % NUM_SECTIONS;
    }

    private void ensureCapacity(int needed) {
        if (needed + sectionWriteOffset > sectionSize) {
            System.out.println("Resizing to " + sectionSize * 2 + " bytes.");
            new Exception().printStackTrace();
            resize(sectionSize * 2);
        }
    }

    @Override
    public long writeSection(int needed) {
        if (globalFPSCount != currentSection) {
            lockCurrentSection();
            advanceSection();
            //System.out.println("Moving to section " + currentSection);
        }
        ensureCapacity(needed);
        long pointer = sectionWritePointer + sectionWriteOffset;
        sectionWriteOffset += needed;
        sectionDrawCount++;
        return pointer;
    }

    // AI slop resize, seems to work well
    // Essentially copies any pending draw operations into the new buffer
    public void resize(int newSectionSize) {
        // Preserve pending batch
        int sectionBase = currentSection * sectionSize;

        int batchSize = (sectionBase + sectionWriteOffset) - sectionWriteStart;

        long tempBuffer = nmemAllocChecked(batchSize);

        memCopy(
            sectionWritePointer,
            tempBuffer,
            batchSize
        );

        // Wait for GPU
        for (int i = 0; i < NUM_SECTIONS; i++) {
            waitForSection(i);
        }

        // Destroy old buffer
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
        GLStateManager.glUnmapBuffer(GL15.GL_ARRAY_BUFFER);
        GLStateManager.glDeleteBuffers(vboId);

        // Create new buffer
        sectionSize = newSectionSize;
        sectionWriteStart = currentSection * sectionSize;

        vboId = GLStateManager.glGenBuffers();

        final int totalCapacity = getTotalCapacity();

        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
        GLStateManager.glBufferStorage(
            GL15.GL_ARRAY_BUFFER,
            totalCapacity,
            FLAGS
        );
        persistentMapping = GLStateManager.glMapBufferRange(
            GL15.GL_ARRAY_BUFFER,
            0,
            totalCapacity,
            FLAGS
        );
        baseAddress = memAddress0(persistentMapping);

        sectionWritePointer = baseAddress + sectionWriteStart;
        sectionWriteOffset = batchSize;
        vaoId = 0;

        // Restore pending batch at offset 0
        memCopy(
            tempBuffer,
            sectionWritePointer,
            batchSize
        );

        nmemFree(tempBuffer);
    }

    public int getTotalCapacity() {
        return sectionSize * NUM_SECTIONS;
    }

    public void destroy() {
        for (int i = 0; i < NUM_SECTIONS; i++) {
            if (fences[i] != 0) {
                GLStateManager.glDeleteSync(fences[i]);
                fences[i] = 0;
            }
        }
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
        GLStateManager.glUnmapBuffer(GL15.GL_ARRAY_BUFFER);
        GLStateManager.glDeleteBuffers(vboId);
        persistentMapping = null;
        vboId          = 0;
    }

    private void lockCurrentSection() {
        if (fences[currentSection] != 0L)
            GLStateManager.glDeleteSync(fences[currentSection]);
        fences[currentSection] = GLStateManager.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
    }

    private void advanceSection() {
        currentSection     = (currentSection + 1) % NUM_SECTIONS;
        sectionWriteOffset = 0;
        sectionWriteStart = currentSection * sectionSize;
        sectionWritePointer = baseAddress + sectionWriteStart;
        waitForSection(currentSection);
    }

    private void waitForSection(int section) {
        if (fences[section] == 0) return;

        while (true) {
            int result = GLStateManager.glClientWaitSync(
                fences[section], GL32.GL_SYNC_FLUSH_COMMANDS_BIT, 1_000_000_000L);

            if (result == GL32.GL_ALREADY_SIGNALED || result == GL32.GL_CONDITION_SATISFIED) break;
            if (result == GL32.GL_WAIT_FAILED)
                throw new RuntimeException("glClientWaitSync failed for section " + section);
            // GL_TIMEOUT_EXPIRED — retry
        }

        GLStateManager.glDeleteSync(fences[section]);
        fences[section] = 0;
    }


    @Override
    public void drawElementsInstanced(
        int mode, int indices_count, int type, long indices_buffer_offset
    ) {
        if (this.vaoId == 0) {
            super.initVAO();
        }
        final int start = sectionWriteStart;
        final int offset = start / stride;
        GL42.glDrawElementsInstancedBaseInstance(
            mode,
            indices_count,
            type,
            indices_buffer_offset,
            sectionDrawCount,
            offset
        );
        sectionDrawCount = 0;

        sectionWriteStart = currentSection * sectionSize + sectionWriteOffset;
    }

    @Override
    public boolean isEmpty() {
        return sectionDrawCount == 0;
    }
}
