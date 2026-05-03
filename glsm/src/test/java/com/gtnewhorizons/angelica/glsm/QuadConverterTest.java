package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuadConverterTest {

    @Test
    void triangulateQuads_ubyteToUint_lastVertexConvention() {
        // 2 quads: [0,1,2,3] and [4,5,6,7]. Expected LAST-convention output:
        //   quad0 → (0,1,3), (1,2,3)
        //   quad1 → (4,5,7), (5,6,7)
        final ByteBuffer src = MemoryUtilities.memAlloc(8);
        for (int i = 0; i < 8; i++) src.put(i, (byte) i);

        final ByteBuffer dst = MemoryUtilities.memAlloc(12 * 4);
        try {
            QuadConverter.triangulateQuads(memAddress0(src), GL11.GL_UNSIGNED_BYTE, memAddress0(dst), GL11.GL_UNSIGNED_INT, 2);

            assertUintIndices(dst, 0, 1, 3, 1, 2, 3, 4, 5, 7, 5, 6, 7);
        } finally {
            MemoryUtilities.memFree(src);
            MemoryUtilities.memFree(dst);
        }
    }

    @Test
    void triangulateQuads_ushortToUshort_preservesType() {
        // USHORT in, USHORT out — same-size path for live draws.
        final ByteBuffer src = MemoryUtilities.memAlloc(4 * 2);
        for (int i = 0; i < 4; i++) src.putShort(i * 2, (short) (i + 100));

        final ByteBuffer dst = MemoryUtilities.memAlloc(6 * 2);
        try {
            QuadConverter.triangulateQuads(memAddress0(src), GL11.GL_UNSIGNED_SHORT, memAddress0(dst), GL11.GL_UNSIGNED_SHORT, 1);

            assertUshortIndices(dst, 100, 101, 103, 101, 102, 103);
        } finally {
            MemoryUtilities.memFree(src);
            MemoryUtilities.memFree(dst);
        }
    }

    @Test
    void triangulateQuads_ushortToUint_widens() {
        final ByteBuffer src = MemoryUtilities.memAlloc(4 * 2);
        for (int i = 0; i < 4; i++) src.putShort(i * 2, (short) (0xFF00 | i));

        final ByteBuffer dst = MemoryUtilities.memAlloc(6 * 4);
        try {
            QuadConverter.triangulateQuads(memAddress0(src), GL11.GL_UNSIGNED_SHORT, memAddress0(dst), GL11.GL_UNSIGNED_INT, 1);

            // Zero-extended USHORT→UINT, LAST winding.
            assertUintIndices(dst, 0xFF00, 0xFF01, 0xFF03, 0xFF01, 0xFF02, 0xFF03);
        } finally {
            MemoryUtilities.memFree(src);
            MemoryUtilities.memFree(dst);
        }
    }

    @Test
    void triangulateQuads_uintToUint_identity() {
        final ByteBuffer src = MemoryUtilities.memAlloc(4 * 4);
        for (int i = 0; i < 4; i++) src.putInt(i * 4, 1_000_000 + i);

        final ByteBuffer dst = MemoryUtilities.memAlloc(6 * 4);
        try {
            QuadConverter.triangulateQuads(memAddress0(src), GL11.GL_UNSIGNED_INT, memAddress0(dst), GL11.GL_UNSIGNED_INT, 1);

            assertUintIndices(dst, 1_000_000, 1_000_001, 1_000_003, 1_000_001, 1_000_002, 1_000_003);
        } finally {
            MemoryUtilities.memFree(src);
            MemoryUtilities.memFree(dst);
        }
    }

    @Test
    void triangulateQuads_zeroQuads_noop() {
        QuadConverter.triangulateQuads(0L, GL11.GL_UNSIGNED_INT, 0L, GL11.GL_UNSIGNED_INT, 0);
    }

    @Test
    void triangulateQuads_unsupportedTypes_throw() {
        final ByteBuffer src = MemoryUtilities.memAlloc(16);
        final ByteBuffer dst = MemoryUtilities.memAlloc(24);
        try {
            assertThrows(IllegalArgumentException.class, () -> QuadConverter.triangulateQuads(memAddress0(src), GL11.GL_FLOAT, memAddress0(dst), GL11.GL_UNSIGNED_INT, 1));
            assertThrows(IllegalArgumentException.class, () -> QuadConverter.triangulateQuads(memAddress0(src), GL11.GL_UNSIGNED_INT, memAddress0(dst), GL11.GL_UNSIGNED_BYTE, 1));
        } finally {
            MemoryUtilities.memFree(src);
            MemoryUtilities.memFree(dst);
        }
    }

    @Test
    void widenIndices_ubyteToUint() {
        final ByteBuffer src = MemoryUtilities.memAlloc(5);
        for (int i = 0; i < 5; i++) src.put(i, (byte) (200 + i));

        final ByteBuffer dst = MemoryUtilities.memAlloc(5 * 4);
        try {
            QuadConverter.widenIndices(memAddress0(src), GL11.GL_UNSIGNED_BYTE, memAddress0(dst), GL11.GL_UNSIGNED_INT, 5);

            assertUintIndices(dst, 200, 201, 202, 203, 204);
        } finally {
            MemoryUtilities.memFree(src);
            MemoryUtilities.memFree(dst);
        }
    }

    @Test
    void widenIndices_ushortToUint_zeroExtends() {
        final ByteBuffer src = MemoryUtilities.memAlloc(3 * 2);
        src.putShort(0, (short) 0xFFFF);
        src.putShort(2, (short) 0x8001);
        src.putShort(4, (short) 0);

        final ByteBuffer dst = MemoryUtilities.memAlloc(3 * 4);
        try {
            QuadConverter.widenIndices(memAddress0(src), GL11.GL_UNSIGNED_SHORT, memAddress0(dst), GL11.GL_UNSIGNED_INT, 3);

            assertUintIndices(dst, 0xFFFF, 0x8001, 0);
        } finally {
            MemoryUtilities.memFree(src);
            MemoryUtilities.memFree(dst);
        }
    }

    @Test
    void widenIndices_uintToUint_copiesBitsExactly() {
        final ByteBuffer src = MemoryUtilities.memAlloc(3 * 4);
        src.putInt(0,  0x01234567);
        src.putInt(4,  0xDEADBEEF);
        src.putInt(8,  0);

        final ByteBuffer dst = MemoryUtilities.memAlloc(3 * 4);
        try {
            QuadConverter.widenIndices(memAddress0(src), GL11.GL_UNSIGNED_INT, memAddress0(dst), GL11.GL_UNSIGNED_INT, 3);

            assertUintIndices(dst, 0x01234567, 0xDEADBEEF, 0);
        } finally {
            MemoryUtilities.memFree(src);
            MemoryUtilities.memFree(dst);
        }
    }

    @Test
    void scanMinMaxIndex_ushort_typical() {
        final ByteBuffer src = MemoryUtilities.memAlloc(6 * 2);
        final int[] data = {42, 7, 1000, 99, 500, 3};
        for (int i = 0; i < data.length; i++) src.putShort(i * 2, (short) data[i]);

        try {
            final long packed = QuadConverter.scanMinMaxIndex(memAddress0(src), GL11.GL_UNSIGNED_SHORT, data.length);
            final int min = (int) (packed & 0xFFFFFFFFL);
            final int max = (int) (packed >>> 32);
            assertEquals(3, min);
            assertEquals(1000, max);
        } finally {
            MemoryUtilities.memFree(src);
        }
    }

    @Test
    void scanMinMaxIndex_emptyCountReturnsMinusOne() {
        assertEquals(-1L, QuadConverter.scanMinMaxIndex(0L, GL11.GL_UNSIGNED_SHORT, 0));
    }

    @Test
    void scanMinMaxIndex_unsupportedTypeReturnsMinusOne() {
        final ByteBuffer src = MemoryUtilities.memAlloc(4);
        try {
            assertEquals(-1L, QuadConverter.scanMinMaxIndex(memAddress0(src), GL11.GL_FLOAT, 1));
        } finally {
            MemoryUtilities.memFree(src);
        }
    }

    @Test
    void scanMinMaxIndex_uintOverIntMaxRejected() {
        // readIndex/writeIndex/rebase route UINT through signed int; values above
        // Integer.MAX_VALUE would silently wrap. scanMinMaxIndex must bail.
        final ByteBuffer src = MemoryUtilities.memAlloc(2 * 4);
        src.putInt(0, 10);
        src.putInt(4, 0x80000000); // 2^31
        try {
            assertEquals(-1L, QuadConverter.scanMinMaxIndex(memAddress0(src), GL11.GL_UNSIGNED_INT, 2));
        } finally {
            MemoryUtilities.memFree(src);
        }
    }

    @Test
    void scanMinMaxIndex_uintUpToIntMaxAccepted() {
        final ByteBuffer src = MemoryUtilities.memAlloc(2 * 4);
        src.putInt(0, 5);
        src.putInt(4, Integer.MAX_VALUE);
        try {
            final long packed = QuadConverter.scanMinMaxIndex(memAddress0(src), GL11.GL_UNSIGNED_INT, 2);
            assertEquals(5, (int) (packed & 0xFFFFFFFFL));
            assertEquals(Integer.MAX_VALUE, (int) (packed >>> 32));
        } finally {
            MemoryUtilities.memFree(src);
        }
    }

    @Test
    void widenIndices_ubyteDstRejected() {
        // UBYTE is not a supported destination — widening only.
        final ByteBuffer src = MemoryUtilities.memAlloc(2);
        src.put(0, (byte) 1).put(1, (byte) 2);
        final ByteBuffer dst = MemoryUtilities.memAlloc(2);
        try {
            assertThrows(IllegalArgumentException.class,
                () -> QuadConverter.widenIndices(memAddress0(src), GL11.GL_UNSIGNED_BYTE, memAddress0(dst), GL11.GL_UNSIGNED_BYTE, 2));
        } finally {
            MemoryUtilities.memFree(src);
            MemoryUtilities.memFree(dst);
        }
    }

    @Test
    void triangulateQuads_ubyteDstRejected() {
        final ByteBuffer src = MemoryUtilities.memAlloc(4);
        for (int i = 0; i < 4; i++) src.put(i, (byte) i);
        final ByteBuffer dst = MemoryUtilities.memAlloc(6);
        try {
            assertThrows(IllegalArgumentException.class,
                () -> QuadConverter.triangulateQuads(memAddress0(src), GL11.GL_UNSIGNED_BYTE, memAddress0(dst), GL11.GL_UNSIGNED_BYTE, 1));
        } finally {
            MemoryUtilities.memFree(src);
            MemoryUtilities.memFree(dst);
        }
    }

    // === helpers ===

    private static void assertUintIndices(ByteBuffer buf, int... expected) {
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], buf.getInt(i * 4), "uint index[" + i + "]");
        }
    }

    private static void assertUshortIndices(ByteBuffer buf, int... expected) {
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], buf.getShort(i * 2) & 0xFFFF, "ushort index[" + i + "]");
        }
    }
}
