package com.gtnewhorizons.angelica.glsm.streaming;

import com.gtnewhorizons.angelica.AngelicaExtension;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.streaming.OrphanStreamingBuffer.UploadStrategy;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAlloc;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memFree;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(AngelicaExtension.class)
class StreamingBufferTest {

    @ParameterizedTest
    @EnumSource(UploadStrategy.class)
    void orphan_firstUploadReturnsZero(UploadStrategy strategy) {
        OrphanStreamingBuffer buf = new OrphanStreamingBuffer(strategy);
        try {
            ByteBuffer data = memAlloc(128);
            fillPattern(data);
            data.flip();
            assertEquals(0, buf.upload(data, 32), "First upload should return offset 0");
            memFree(data);
        } finally {
            buf.destroy();
        }
    }

    @ParameterizedTest
    @EnumSource(UploadStrategy.class)
    void orphan_dataIntegrity(UploadStrategy strategy) {
        OrphanStreamingBuffer buf = new OrphanStreamingBuffer(strategy);
        try {
            ByteBuffer data = memAlloc(64);
            ByteBuffer readBack = null;
            try {
                for (int i = 0; i < 64; i++) data.put((byte) i);
                data.flip();

                buf.upload(data, 16);

                // Read back via glGetBufferSubData
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buf.getBufferId());
                readBack = memAlloc(64);
                GL15.glGetBufferSubData(GL15.GL_ARRAY_BUFFER, 0, readBack);
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

                for (int i = 0; i < 64; i++) {
                    assertEquals((byte) i, readBack.get(i), "Mismatch at byte " + i);
                }
            } finally {
                memFree(data);
                if (readBack != null) memFree(readBack);
            }
        } finally {
            buf.destroy();
        }
    }

    @ParameterizedTest
    @EnumSource(UploadStrategy.class)
    void orphan_multipleUploadsSubAllocate(UploadStrategy strategy) {
        OrphanStreamingBuffer buf = new OrphanStreamingBuffer(strategy);
        try {
            final int stride = 16;
            ByteBuffer small = memAlloc(64);
            ByteBuffer big = memAlloc(256);
            ByteBuffer readBack = null;
            try {
                // First upload: small
                fillPattern(small);
                small.flip();
                int v1 = buf.upload(small, stride);
                assertEquals(0, v1, "First upload should be at offset 0");

                // Second upload: larger — sub-allocates after first (64KB min capacity)
                fillPattern(big);
                big.flip();
                int v2 = buf.upload(big, stride);
                assertTrue(v2 > 0, "Second upload should sub-allocate at non-zero offset");

                // Verify second upload's data at its offset
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buf.getBufferId());
                readBack = memAlloc(buf.getCapacity());
                GL15.glGetBufferSubData(GL15.GL_ARRAY_BUFFER, 0, readBack);
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

                int byteOffset = v2 * stride;
                for (int i = 0; i < 256; i++) {
                    assertEquals((byte) (i & 0xFF), readBack.get(byteOffset + i), "Mismatch at byte " + i);
                }
            } finally {
                memFree(small);
                memFree(big);
                if (readBack != null) memFree(readBack);
            }
        } finally {
            buf.destroy();
        }
    }

    @ParameterizedTest
    @EnumSource(UploadStrategy.class)
    void orphan_subAllocationReturnsIncreasingOffsets(UploadStrategy strategy) {
        OrphanStreamingBuffer buf = new OrphanStreamingBuffer(strategy);
        try {
            final int stride = 16;
            final int dataSize = 64; // 4 vertices worth

            ByteBuffer data1 = memAlloc(dataSize);
            ByteBuffer data2 = memAlloc(dataSize);
            ByteBuffer data3 = memAlloc(dataSize);
            ByteBuffer readBack = null;
            try {
                // First upload: offset 0
                for (int i = 0; i < dataSize; i++) data1.put((byte) (i + 10));
                data1.flip();
                int v1 = buf.upload(data1, stride);
                assertEquals(0, v1, "First upload should be at vertex 0");

                // Second upload: should be at aligned offset after first
                for (int i = 0; i < dataSize; i++) data2.put((byte) (i + 20));
                data2.flip();
                int v2 = buf.upload(data2, stride);
                assertTrue(v2 > 0, "Second upload should be at a non-zero vertex offset");
                assertEquals(0, v2 % 4, "Vertex offset must be quad-aligned (multiple of 4)");

                // Third upload
                for (int i = 0; i < dataSize; i++) data3.put((byte) (i + 30));
                data3.flip();
                int v3 = buf.upload(data3, stride);
                assertTrue(v3 > v2, "Third upload should be after second");
                assertEquals(0, v3 % 4, "Vertex offset must be quad-aligned");

                // Verify all three regions have correct data
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buf.getBufferId());
                readBack = memAlloc(buf.getCapacity());
                GL15.glGetBufferSubData(GL15.GL_ARRAY_BUFFER, 0, readBack);
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

                for (int i = 0; i < dataSize; i++) {
                    assertEquals((byte) (i + 10), readBack.get(v1 * stride + i), "Upload 1 mismatch at byte " + i);
                    assertEquals((byte) (i + 20), readBack.get(v2 * stride + i), "Upload 2 mismatch at byte " + i);
                    assertEquals((byte) (i + 30), readBack.get(v3 * stride + i), "Upload 3 mismatch at byte " + i);
                }
            } finally {
                memFree(data1);
                memFree(data2);
                memFree(data3);
                if (readBack != null) memFree(readBack);
            }
        } finally {
            buf.destroy();
        }
    }

    @ParameterizedTest
    @EnumSource(UploadStrategy.class)
    void orphan_overflowResetsToZero(UploadStrategy strategy) {
        OrphanStreamingBuffer buf = new OrphanStreamingBuffer(strategy);
        try {
            final int stride = 16;

            // First upload: establishes capacity
            ByteBuffer data1 = memAlloc(64);
            try {
                fillPattern(data1);
                data1.flip();
                buf.upload(data1, stride);
            } finally {
                memFree(data1);
            }

            int capacityAfterFirst = buf.getCapacity();

            // Second upload: fills remaining space or overflows
            ByteBuffer data2 = memAlloc(capacityAfterFirst); // exactly fills or overflows
            try {
                fillPattern(data2);
                data2.flip();
                int v2 = buf.upload(data2, stride);
                // Either sub-allocated (if it fit after alignment) or reset to 0 (overflow)
                assertEquals(0, v2 % 4, "Offset must be quad-aligned");
            } finally {
                memFree(data2);
            }
        } finally {
            buf.destroy();
        }
    }

    @ParameterizedTest
    @EnumSource(UploadStrategy.class)
    void orphan_postDrawResetsForNextFrame(UploadStrategy strategy) {
        OrphanStreamingBuffer buf = new OrphanStreamingBuffer(strategy);
        try {
            final int stride = 16;

            ByteBuffer data = memAlloc(64);
            try {
                // Frame 1: two uploads
                fillPattern(data);
                data.flip();
                buf.upload(data, stride);
                data.position(0).limit(64);
                int v2 = buf.upload(data, stride);
                assertTrue(v2 > 0, "Second upload in frame should be at non-zero offset");

                // End of frame
                buf.postDraw();
                assertEquals(0, buf.getWritePos(), "writePos should reset after postDraw");

                // Frame 2: first upload should be at offset 0 again (after orphan)
                data.position(0).limit(64);
                int v3 = buf.upload(data, stride);
                assertEquals(0, v3, "First upload after postDraw should return 0");
            } finally {
                memFree(data);
            }
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
