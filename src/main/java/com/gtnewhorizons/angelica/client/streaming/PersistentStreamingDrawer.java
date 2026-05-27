package com.gtnewhorizons.angelica.client.streaming;

import org.lwjgl.opengl.ARBBufferStorage;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL44;
import org.lwjgl.opengl.GLSync;

import java.nio.ByteBuffer;

public final class PersistentStreamingDrawer extends StreamingDrawer {

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    /** Number of buffer sections for round-robin writes (triple buffering). */
    private static final int NUM_SECTIONS = 3;

    /**
     * Persistent mapping flags:
     *   WRITE — client will write into the mapping.
     *   PERSISTENT — mapping stays valid across multiple GL commands.
     *   COHERENT — no manual glMemoryBarrier needed between writes and GL reads.
     */
    private static final int MAP_FLAGS =
        GL30.GL_MAP_WRITE_BIT
            | GL44.GL_MAP_PERSISTENT_BIT
            | GL44.GL_MAP_COHERENT_BIT;

    /**
     * Storage creation flags must match the mapping flags,
     * plus MAP_WRITE on the storage side.
     */
    private static final int STORAGE_FLAGS =
        GL30.GL_MAP_WRITE_BIT
            | GL44.GL_MAP_PERSISTENT_BIT
            | GL44.GL_MAP_COHERENT_BIT;

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------

    /** GL name of the VBO (or SSBO / any buffer target). */
    private int bufferId;

    /** Total byte capacity of the buffer (sectionSize * NUM_SECTIONS). */
    private int totalCapacity;

    /** Byte size of each individual section. */
    private int sectionSize;

    /** Persistent mapped view of the entire buffer. */
    private ByteBuffer persistentMapping;

    /** One fence sync object per section; null means the section is free. */
    private final GLSync[] fences = new GLSync[NUM_SECTIONS];

    /** Index of the section we are currently writing into (0..NUM_SECTIONS-1). */
    private int currentSection;

    /**
     * Byte offset within the *current section* — reset to 0 at the start
     * of every section.  The absolute write pointer into persistentMapping
     * is therefore: currentSection * sectionSize + sectionWriteOffset.
     */
    private int sectionWriteOffset;

    // -----------------------------------------------------------------------
    // Initialisation
    // -----------------------------------------------------------------------

    /**
     * Creates the immutable buffer storage and maps it persistently.
     *
     * @param initialSectionSize Bytes per section; the actual GL buffer will be
     *                           {@code initialSectionSize * NUM_SECTIONS}.
     * @param target             GL buffer target, e.g. {@code GL15.GL_ARRAY_BUFFER}.
     */
    public void init(int initialSectionSize, int target) {
        this.sectionSize   = initialSectionSize;
        this.totalCapacity = initialSectionSize * NUM_SECTIONS;

        // Create and bind the buffer.
        bufferId = GL15.glGenBuffers();
        GL15.glBindBuffer(target, bufferId);

        // Allocate immutable storage — cannot be resized after this call.
        ARBBufferStorage.glBufferStorage(target, totalCapacity, STORAGE_FLAGS);

        // Map the whole buffer once; the mapping remains valid for the
        // lifetime of the buffer object.
        persistentMapping = GL30.glMapBufferRange(
            target,
            0,
            totalCapacity,
            MAP_FLAGS,
            null   // LWJGL re-uses its internal cached ByteBuffer if possible
        );

        if (persistentMapping == null) {
            throw new RuntimeException(
                "glMapBufferRange returned null — persistent mapping failed. "
                    + "Check that ARB_buffer_storage is supported.");
        }

        currentSection     = 0;
        sectionWriteOffset = 0;
    }

    // -----------------------------------------------------------------------
    // StreamingDrawer contract
    // -----------------------------------------------------------------------

    /**
     * Returns the absolute byte offset into the mapped buffer where the next
     * write will land.
     */
    @Override
    public long getWritePointer() {
        return (long) currentSection * sectionSize + sectionWriteOffset;
    }

    /**
     * Ensures that at least {@code needed} bytes are available in the current
     * section.  If the current section cannot accommodate the request, we
     * advance to the next section (waiting on its fence if necessary) and
     * reset the write cursor.
     *
     * @param needed Number of bytes required contiguously.
     * @throws IllegalArgumentException if {@code needed > sectionSize}.
     */
    @Override
    public void ensureCapacity(int needed) {
        if (needed > sectionSize) {
            throw new IllegalArgumentException(
                "Requested " + needed + " bytes but section size is only "
                    + sectionSize + " bytes. Re-initialise with a larger section size.");
        }

        if (sectionWriteOffset + needed > sectionSize) {
            // Current section is full — place a fence on it so we know when
            // the GPU finishes reading it, then move to the next section.
            lockCurrentSection();
            advanceSection();
        }
    }

    // -----------------------------------------------------------------------
    // Write helpers
    // -----------------------------------------------------------------------

    /**
     * Writes a raw byte array starting at the current write pointer and
     * advances the pointer by {@code data.length}.
     *
     * @param data Bytes to write.
     */
    public void write(byte[] data) {
        ensureCapacity(data.length);
        int absOffset = currentSection * sectionSize + sectionWriteOffset;
        persistentMapping.position(absOffset);
        persistentMapping.put(data);
        sectionWriteOffset += data.length;
    }

    /**
     * Writes a float array (4 bytes per element) at the current write pointer.
     *
     * @param floats Float values to write.
     */
    public void writeFloats(float[] floats) {
        int bytes = floats.length * Float.BYTES;
        ensureCapacity(bytes);
        int absOffset = currentSection * sectionSize + sectionWriteOffset;
        persistentMapping.position(absOffset);
        persistentMapping.asFloatBuffer().put(floats);
        sectionWriteOffset += bytes;
    }

    // -----------------------------------------------------------------------
    // Frame lifecycle
    // -----------------------------------------------------------------------

    /**
     * Call at the END of each frame (after issuing draw calls) to place a
     * fence on the section that was just written.  This records the point in
     * the GL command stream after which this section's data is consumed.
     */
    public void endFrame() {
        lockCurrentSection();
    }

    /**
     * Call at the BEGINNING of each frame to advance to the next section and
     * wait (if necessary) for the GPU to finish with it.
     */
    public void beginFrame() {
        advanceSection();
    }

    // -----------------------------------------------------------------------
    // Cleanup
    // -----------------------------------------------------------------------

    /**
     * Deletes all fence sync objects and the buffer.  Call when the OpenGL
     * context is still active.
     *
     * @param target The same GL buffer target used in {@link #init}.
     */
    public void destroy(int target) {
        // Delete any outstanding fences.
        for (int i = 0; i < NUM_SECTIONS; i++) {
            if (fences[i] != null) {
                GL32.glDeleteSync(fences[i]);
                fences[i] = null;
            }
        }

        // Unmap and delete the buffer.
        GL15.glBindBuffer(target, bufferId);
        GL15.glUnmapBuffer(target);
        GL15.glDeleteBuffers(bufferId);

        persistentMapping = null;
        bufferId          = 0;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Places a fence sync at the current point in the GL command stream for
     * the section we just finished writing.  The GPU must pass this fence
     * before we write into this section again.
     */
    private void lockCurrentSection() {
        // Delete any pre-existing fence (shouldn't normally happen, but be safe).
        if (fences[currentSection] != null) {
            GL32.glDeleteSync(fences[currentSection]);
        }
        fences[currentSection] = GL32.glFenceSync(
            GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
    }

    /**
     * Advances {@link #currentSection} to the next slot (wrapping around) and
     * blocks the CPU until the GPU has signalled the fence for that slot,
     * ensuring we never overwrite data the GPU is still reading.
     */
    private void advanceSection() {
        currentSection     = (currentSection + 1) % NUM_SECTIONS;
        sectionWriteOffset = 0;
        waitForSection(currentSection);
    }

    /**
     * Busy-waits (with a 1-second timeout per iteration) until the GPU
     * signals the fence for the given section, then deletes the fence.
     *
     * @param section Section index to wait on.
     */
    private void waitForSection(int section) {
        if (fences[section] == null) {
            return; // Section was never locked — safe to write immediately.
        }

        while (true) {
            // GL_SYNC_FLUSH_COMMANDS_BIT flushes the command queue if it hasn't
            // been flushed yet, preventing a deadlock when the fence was placed
            // in the same frame.
            int result = GL32.glClientWaitSync(
                fences[section],
                GL32.GL_SYNC_FLUSH_COMMANDS_BIT,
                1_000_000_000L // 1-second timeout in nanoseconds
            );

            if (result == GL32.GL_ALREADY_SIGNALED
                || result == GL32.GL_CONDITION_SATISFIED) {
                break; // GPU is done with this section.
            }

            if (result == GL32.GL_WAIT_FAILED) {
                throw new RuntimeException(
                    "glClientWaitSync returned GL_WAIT_FAILED for section "
                        + section);
            }
            // GL_TIMEOUT_EXPIRED — loop and try again.
        }

        GL32.glDeleteSync(fences[section]);
        fences[section] = null;
    }

}
