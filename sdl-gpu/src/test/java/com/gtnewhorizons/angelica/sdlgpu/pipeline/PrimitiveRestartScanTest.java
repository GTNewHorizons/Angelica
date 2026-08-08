package com.gtnewhorizons.angelica.sdlgpu.pipeline;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PrimitiveRestartScanTest {

    private static ByteBuffer shortBuf(int... values) {
        final ByteBuffer b = MemoryUtil.memAlloc(values.length * 2).order(ByteOrder.nativeOrder());
        for (int v : values) b.putShort((short) v);
        b.position(0);
        return b;
    }

    private static ByteBuffer intBuf(int... values) {
        final ByteBuffer b = MemoryUtil.memAlloc(values.length * 4).order(ByteOrder.nativeOrder());
        for (int v : values) b.putInt(v);
        b.position(0);
        return b;
    }

    private static void assertSplits(EBOSplitScanner.EboSplit[] actual, int... pairs) {
        final EBOSplitScanner.EboSplit[] expected = new EBOSplitScanner.EboSplit[pairs.length / 2];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = new EBOSplitScanner.EboSplit(pairs[i * 2], pairs[i * 2 + 1]);
        }
        assertArrayEquals(expected, actual);
    }

    private static EBOSplitScanner.EboSplit[] slice(EBOSplitScanner.EboSplit[] full, int firstIndex, int count) {
        final int[] firsts = new int[full.length];
        final int[] counts = new int[full.length];
        final int n = EBOSplitScanner.sliceSplits(full, firstIndex, count, firsts, counts);
        final EBOSplitScanner.EboSplit[] out = new EBOSplitScanner.EboSplit[n];
        for (int i = 0; i < n; i++) out[i] = new EBOSplitScanner.EboSplit(firsts[i], counts[i]);
        return out;
    }

    @Test
    void shortBuffer_noSentinel_singleFullRun() {
        final ByteBuffer b = shortBuf(0, 1, 2, 3, 4);
        try {
            assertSplits(EBOSplitScanner.scanIndices(b, 5, GL11.GL_UNSIGNED_SHORT, 0xFFFF), 0, 5);
        } finally { MemoryUtil.memFree(b); }
    }

    @Test
    void shortBuffer_sentinelMiddle_twoRuns() {
        final ByteBuffer b = shortBuf(0, 1, 2, 0xFFFF, 4, 5, 6);
        try {
            assertSplits(EBOSplitScanner.scanIndices(b, 7, GL11.GL_UNSIGNED_SHORT, 0xFFFF), 0, 3, 4, 3);
        } finally { MemoryUtil.memFree(b); }
    }

    @Test
    void shortBuffer_sentinelLeading_skipsToFirstRun() {
        final ByteBuffer b = shortBuf(0xFFFF, 1, 2, 3);
        try {
            assertSplits(EBOSplitScanner.scanIndices(b, 4, GL11.GL_UNSIGNED_SHORT, 0xFFFF), 1, 3);
        } finally { MemoryUtil.memFree(b); }
    }

    @Test
    void shortBuffer_sentinelTrailing_dropsTail() {
        final ByteBuffer b = shortBuf(0, 1, 2, 0xFFFF);
        try {
            assertSplits(EBOSplitScanner.scanIndices(b, 4, GL11.GL_UNSIGNED_SHORT, 0xFFFF), 0, 3);
        } finally { MemoryUtil.memFree(b); }
    }

    @Test
    void shortBuffer_consecutiveSentinels_collapse() {
        final ByteBuffer b = shortBuf(0, 1, 0xFFFF, 0xFFFF, 0xFFFF, 5, 6);
        try {
            assertSplits(EBOSplitScanner.scanIndices(b, 7, GL11.GL_UNSIGNED_SHORT, 0xFFFF), 0, 2, 5, 2);
        } finally { MemoryUtil.memFree(b); }
    }

    @Test
    void shortBuffer_allSentinels_emptyOutput() {
        final ByteBuffer b = shortBuf(0xFFFF, 0xFFFF, 0xFFFF);
        try {
            assertEquals(0, EBOSplitScanner.scanIndices(b, 3, GL11.GL_UNSIGNED_SHORT, 0xFFFF).length);
        } finally { MemoryUtil.memFree(b); }
    }

    @Test
    void intBuffer_largeSentinel() {
        final ByteBuffer b = intBuf(0, 1, 2, 0xFFFFFFFF, 4, 5);
        try {
            assertSplits(EBOSplitScanner.scanIndices(b, 6, GL11.GL_UNSIGNED_INT, 0xFFFFFFFF), 0, 3, 4, 2);
        } finally { MemoryUtil.memFree(b); }
    }

    @Test
    void intBuffer_customSentinelZero() {
        final ByteBuffer b = intBuf(5, 6, 0, 7, 8, 0, 9);
        try {
            assertSplits(EBOSplitScanner.scanIndices(b, 7, GL11.GL_UNSIGNED_INT, 0), 0, 2, 3, 2, 6, 1);
        } finally { MemoryUtil.memFree(b); }
    }

    @Test
    void sliceSplits_emptyFull_returnsEmpty() {
        assertEquals(0, slice(new EBOSplitScanner.EboSplit[0], 0, 10).length);
    }

    @Test
    void sliceSplits_windowContainsOneRun_returnsTranslated() {
        final EBOSplitScanner.EboSplit[] full = { new EBOSplitScanner.EboSplit(0, 3), new EBOSplitScanner.EboSplit(4, 3) };
        assertSplits(slice(full, 4, 3), 0, 3);
    }

    @Test
    void sliceSplits_windowSpansMultipleRuns() {
        final EBOSplitScanner.EboSplit[] full = { new EBOSplitScanner.EboSplit(0, 3), new EBOSplitScanner.EboSplit(4, 3), new EBOSplitScanner.EboSplit(8, 2) };
        assertSplits(slice(full, 2, 7), 0, 1, 2, 3, 6, 1);
    }

    @Test
    void sliceSplits_windowOutsideAllRuns_returnsEmpty() {
        final EBOSplitScanner.EboSplit[] full = { new EBOSplitScanner.EboSplit(0, 3), new EBOSplitScanner.EboSplit(4, 3) };
        assertEquals(0, slice(full, 100, 50).length);
    }

    @Test
    void sliceSplits_windowClipsRunStartAndEnd() {
        final EBOSplitScanner.EboSplit[] full = { new EBOSplitScanner.EboSplit(2, 10) }; // [2, 12)
        assertSplits(slice(full, 5, 4), 0, 4);
    }
}
