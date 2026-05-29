package com.gtnewhorizons.angelica.client.streaming;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL44;

import java.nio.ByteBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;

/** Triple-buffered persistent VBO using ARB_buffer_storage. */
public final class PersistentStreamingDrawer extends StreamingDrawer {



    private static final int NUM_SECTIONS = 3;

    private static final int FLAGS =
        GL30.GL_MAP_WRITE_BIT
            | GL44.GL_MAP_PERSISTENT_BIT
            | GL44.GL_MAP_COHERENT_BIT;

    private int sectionSize;
    private int totalCapacity;
    private ByteBuffer persistentMapping;
    private final long[] fences = new long[NUM_SECTIONS];
    private int currentSection;
    //private long sectionWritePointer;
    private int sectionWriteOffset;
    private int sectionWriteStart;
    private static int globalFPSCount;

    public PersistentStreamingDrawer(int capacity) {
        super(capacity);
        sectionSize   = capacity;
        totalCapacity = capacity * NUM_SECTIONS;

        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
        GLStateManager.glBufferStorage(GL15.GL_ARRAY_BUFFER, totalCapacity, FLAGS);

        persistentMapping = GLStateManager.glMapBufferRange(
            GL15.GL_ARRAY_BUFFER, 0, totalCapacity, FLAGS);

        if (persistentMapping == null)
            throw new RuntimeException("Persistent mapping failed — check ARB_buffer_storage support.");
    }

    public static void incrementFPS() {
        globalFPSCount = (globalFPSCount + 1) % NUM_SECTIONS;
    }

    private void ensureCapacity(int needed) {
        if (needed + sectionWriteOffset > sectionSize) {
            System.out.println("Resizing");
            new Exception().printStackTrace();
            resize(nextPowerOfTwo(sectionSize + needed));
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
        long pointer = memAddress0(persistentMapping)
            + (long) currentSection * sectionSize + sectionWriteOffset;
        sectionWriteOffset += needed;
        return pointer;
    }

    @Override
    public int finishUploading() {
//        final int count = sectionElementCount;
//        sectionElementCount = 0;
//        return count;
        final int start = sectionWriteStart;
        sectionWriteStart = currentSection * sectionSize + sectionWriteOffset;
        return start;
    }

    /**
     * Destroys and recreates the buffer at a larger section size.
     * All fences are waited on first; existing data is lost.
     */
    public void resize(int newSectionSize) {
        for (int i = 0; i < NUM_SECTIONS; i++) waitForSection(i);

        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
        GLStateManager.glUnmapBuffer(GL15.GL_ARRAY_BUFFER);
        GLStateManager.glDeleteBuffers(vboId);

        sectionSize        = newSectionSize;
        totalCapacity      = newSectionSize * NUM_SECTIONS;
        currentSection     = 0;
        sectionWriteOffset = 0;

        vboId = GLStateManager.glGenBuffers();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
        GLStateManager.glBufferStorage(GL15.GL_ARRAY_BUFFER, totalCapacity, FLAGS);

        persistentMapping = GLStateManager.glMapBufferRange(
            GL15.GL_ARRAY_BUFFER, 0, totalCapacity, FLAGS);
    }

    /** Call at the start of each frame to advance to the next section. */
    public void beginFrame() {
        advanceSection();
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

    private static int nextPowerOfTwo(int value) {
        if (value <= 0) return 1;
        int v = value - 1;
        v |= v >> 1;
        v |= v >> 2;
        v |= v >> 4;
        v |= v >> 8;
        v |= v >> 16;
        return v + 1;
    }
}
