package com.gtnewhorizons.angelica.glsm.streaming;

import com.gtnewhorizons.angelica.AngelicaExtension;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAlloc;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memFree;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(AngelicaExtension.class)
class StreamingBufferTest {

    @Test
    void orphan_uploadReturnsZero() {
        OrphanStreamingBuffer buf = new OrphanStreamingBuffer();
        try {
            ByteBuffer data = memAlloc(128);
            fillPattern(data);
            data.flip();
            assertEquals(0, buf.upload(data, 32));
            memFree(data);
        } finally {
            buf.destroy();
        }
    }

    @Test
    void orphan_dataIntegrity() {
        OrphanStreamingBuffer buf = new OrphanStreamingBuffer();
        try {
            ByteBuffer data = memAlloc(64);
            for (int i = 0; i < 64; i++) data.put((byte) i);
            data.flip();

            buf.upload(data, 16);

            // Read back via glGetBufferSubData
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buf.getBufferId());
            ByteBuffer readBack = memAlloc(64);
            GL15.glGetBufferSubData(GL15.GL_ARRAY_BUFFER, 0, readBack);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

            for (int i = 0; i < 64; i++) {
                assertEquals((byte) i, readBack.get(i), "Mismatch at byte " + i);
            }
            memFree(readBack);
        } finally {
            buf.destroy();
        }
    }

    @Test
    void orphan_multipleUploadsGrowCapacity() {
        OrphanStreamingBuffer buf = new OrphanStreamingBuffer();
        try {
            // First upload: small
            ByteBuffer small = memAlloc(64);
            fillPattern(small);
            small.flip();
            buf.upload(small, 16);
            memFree(small);

            // Second upload: larger
            ByteBuffer big = memAlloc(256);
            fillPattern(big);
            big.flip();
            buf.upload(big, 16);
            memFree(big);

            // Verify larger data is present
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buf.getBufferId());
            ByteBuffer readBack = memAlloc(256);
            GL15.glGetBufferSubData(GL15.GL_ARRAY_BUFFER, 0, readBack);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

            for (int i = 0; i < 256; i++) {
                assertEquals((byte) (i & 0xFF), readBack.get(i), "Mismatch at byte " + i);
            }
            memFree(readBack);
        } finally {
            buf.destroy();
        }
    }

    @Test
    void persistent_uploadAlignment() {
        Assumptions.assumeTrue(RenderSystem.supportsBufferStorage(), "GL4.4+ required");

        PersistentStreamingBuffer buf = new PersistentStreamingBuffer(4096);
        try {
            ByteBuffer data = memAlloc(128);
            fillPattern(data);
            data.flip();

            int firstVertex = buf.upload(data, 32);
            assertTrue(firstVertex >= 0, "Upload should succeed");
            assertEquals(0, firstVertex % 4, "firstVertex must be quad-aligned");
            memFree(data);
        } finally {
            buf.destroy();
        }
    }

    @Test
    void persistent_dataIntegrity() {
        Assumptions.assumeTrue(RenderSystem.supportsBufferStorage(), "GL4.4+ required");

        PersistentStreamingBuffer buf = new PersistentStreamingBuffer(4096);
        try {
            ByteBuffer data = memAlloc(128);
            for (int i = 0; i < 128; i++) data.put((byte) i);
            data.flip();

            int stride = 32;
            int firstVertex = buf.upload(data, stride);
            assertTrue(firstVertex >= 0);

            // Read back from persistently mapped buffer (CPU-visible, no fence needed)
            ByteBuffer mapped = buf.getMappedBuffer();
            int byteOffset = firstVertex * stride;
            for (int i = 0; i < 128; i++) {
                assertEquals((byte) i, mapped.get(byteOffset + i), "Mismatch at byte " + i);
            }
            memFree(data);
        } finally {
            buf.destroy();
        }
    }

    @Test
    void persistent_ringWraparound() {
        Assumptions.assumeTrue(RenderSystem.supportsBufferStorage(), "GL4.4+ required");

        // Small ring: 1024 bytes. stride=16, alignUnit=64.
        PersistentStreamingBuffer buf = new PersistentStreamingBuffer(1024);
        try {
            int stride = 16;

            // Fill most of the ring (768 bytes)
            ByteBuffer data1 = memAlloc(768);
            fillPattern(data1);
            data1.flip();
            int v1 = buf.upload(data1, stride);
            assertTrue(v1 >= 0);
            buf.postDraw();
            memFree(data1);

            // Wait for GPU to finish so fence is signaled
            GL11.glFinish();

            // Upload again — should wrap to start after reclaim
            ByteBuffer data2 = memAlloc(512);
            for (int i = 0; i < 512; i++) data2.put((byte) ((i + 100) & 0xFF));
            data2.flip();
            int v2 = buf.upload(data2, stride);
            assertTrue(v2 >= 0, "Should succeed after reclaim/wrap");

            // Verify data2 integrity
            ByteBuffer mapped = buf.getMappedBuffer();
            int offset2 = v2 * stride;
            for (int i = 0; i < 512; i++) {
                assertEquals((byte) ((i + 100) & 0xFF), mapped.get(offset2 + i), "Mismatch at byte " + i);
            }

            buf.postDraw();
            memFree(data2);
        } finally {
            buf.destroy();
        }
    }

    @Test
    void persistent_fenceReclaim() {
        Assumptions.assumeTrue(RenderSystem.supportsBufferStorage(), "GL4.4+ required");

        PersistentStreamingBuffer buf = new PersistentStreamingBuffer(4096);
        try {
            ByteBuffer data = memAlloc(1024);
            fillPattern(data);
            data.flip();

            buf.upload(data, 32);
            buf.postDraw();

            int remainingAfterUpload = buf.getRemaining();

            GL11.glFinish(); // ensure fence completion

            // New upload triggers reclaim of first upload's bytes
            data.position(0).limit(512);
            buf.upload(data, 32);

            // After reclaim, remaining should be higher than before reclaim (minus new upload)
            assertTrue(buf.getRemaining() > remainingAfterUpload,
                "Remaining should increase after fence reclaim (minus new upload cost)");

            buf.postDraw();
            memFree(data);
        } finally {
            buf.destroy();
        }
    }

    @Test
    void persistent_overflowReturnsNegative() {
        Assumptions.assumeTrue(RenderSystem.supportsBufferStorage(), "GL4.4+ required");

        // Tiny ring: 256 bytes
        PersistentStreamingBuffer buf = new PersistentStreamingBuffer(256);
        try {
            ByteBuffer data = memAlloc(512);
            fillPattern(data);
            data.flip();

            int result = buf.upload(data, 32);
            assertEquals(-1, result, "Should return -1 when data exceeds total capacity");
            memFree(data);
        } finally {
            buf.destroy();
        }
    }

    // --- Utilities ---

    private static void fillPattern(ByteBuffer buf) {
        for (int i = 0; i < buf.capacity(); i++) {
            buf.put((byte) (i & 0xFF));
        }
    }
}
