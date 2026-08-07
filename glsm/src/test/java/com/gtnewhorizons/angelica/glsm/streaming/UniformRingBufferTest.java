package com.gtnewhorizons.angelica.glsm.streaming;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL31;

import java.nio.ByteBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAlloc;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memFree;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
class UniformRingBufferTest {

    private static final int BLOCK = 64;
    private static final int CAPACITY = 1024; // 4 regions at 256-byte stride

    private static void fill(ByteBuffer src, int seed) {
        for (int i = 0; i < BLOCK; i++) {
            src.put(i, (byte) (seed + i));
        }
    }

    private static void assertBlockAt(UniformRingBuffer ring, int offset, int seed) {
        final ByteBuffer readback = memAlloc(BLOCK);
        try {
            GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, ring.getBufferId());
            GL15.glGetBufferSubData(GL31.GL_UNIFORM_BUFFER, offset, readback);
            GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, 0);
            for (int i = 0; i < BLOCK; i++) {
                assertEquals((byte) (seed + i), readback.get(i), "byte " + i + " at offset " + offset);
            }
        } finally {
            memFree(readback);
        }
    }

    @Test
    void offsetsAdvanceByStrideAndWrap() {
        final ByteBuffer src = memAlloc(BLOCK);
        final UniformRingBuffer ring = new UniformRingBuffer(CAPACITY, BLOCK);
        try {
            assertEquals(256, ring.getStride(), "stride rounds the block up to UBO offset alignment");
            fill(src, 7);
            assertEquals(0, ring.writeBlock(src));
            assertEquals(256, ring.writeBlock(src));
            assertEquals(512, ring.writeBlock(src));
            assertEquals(768, ring.writeBlock(src));
            assertEquals(0, ring.writeBlock(src), "fifth write wraps to the start");
            assertEquals(1, ring.getWraps());
            if (ring.isPersistent()) {
                assertTrue(ring.getFencesIssued() > 0, "small ring must self-fence before wrapping");
            }
            assertBlockAt(ring, 0, 7);
            ring.endFrame();
        } finally {
            ring.destroy();
            memFree(src);
        }
    }

    @Test
    void orphanFallbackAdvancesAndWraps() {
        final ByteBuffer src = memAlloc(BLOCK);
        UniformRingBuffer ring = null;
        try {
            ring = new UniformRingBuffer(CAPACITY, BLOCK, true);
            assertEquals(false, ring.isPersistent(), "forceOrphan must select the orphan path");
            fill(src, 11);
            assertEquals(0, ring.writeBlock(src));
            fill(src, 42);
            assertEquals(256, ring.writeBlock(src));
            assertEquals(BLOCK, src.remaining(), "writeBlock must not leave the staging buffer resized");
            assertBlockAt(ring, 0, 11);
            assertBlockAt(ring, 256, 42);
            assertEquals(512, ring.writeBlock(src));
            assertEquals(768, ring.writeBlock(src));
            assertEquals(0, ring.writeBlock(src), "orphan wrap restarts at zero");
            assertEquals(1, ring.getWraps());
            assertBlockAt(ring, 0, 42);
            ring.endFrame();
        } finally {
            if (ring != null) ring.destroy();
            memFree(src);
        }
    }
}
