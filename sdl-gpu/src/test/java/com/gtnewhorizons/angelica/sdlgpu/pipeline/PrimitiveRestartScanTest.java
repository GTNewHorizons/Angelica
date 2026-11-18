package com.gtnewhorizons.angelica.sdlgpu.pipeline;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

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
        assertArrayEquals(runs(pairs), actual);
    }

    private static EBOSplitScanner.EboSplit[] slice(EBOSplitScanner.EboSplit[] full, int firstIndex, int count) {
        final int[] firsts = new int[full.length];
        final int[] counts = new int[full.length];
        final int n = EBOSplitScanner.sliceSplits(full, firstIndex, count, firsts, counts);
        final EBOSplitScanner.EboSplit[] out = new EBOSplitScanner.EboSplit[n];
        for (int i = 0; i < n; i++) out[i] = new EBOSplitScanner.EboSplit(firsts[i], counts[i]);
        return out;
    }

    private static final int SHORT = GL11.GL_UNSIGNED_SHORT;
    private static final int INT = GL11.GL_UNSIGNED_INT;

    private static Arguments scan(String name, int glType, int sentinel, int[] indices, int... expectedPairs) {
        return Arguments.of(name, glType, sentinel, indices, expectedPairs);
    }

    static Stream<Arguments> scans() {
        return Stream.of(
            scan("short, no sentinel: single full run", SHORT, 0xFFFF, new int[]{0, 1, 2, 3, 4}, 0, 5),
            scan("short, sentinel in the middle: two runs", SHORT, 0xFFFF, new int[]{0, 1, 2, 0xFFFF, 4, 5, 6}, 0, 3, 4, 3),
            scan("short, leading sentinel: skips to the first run", SHORT, 0xFFFF, new int[]{0xFFFF, 1, 2, 3}, 1, 3),
            scan("short, trailing sentinel: drops the tail", SHORT, 0xFFFF, new int[]{0, 1, 2, 0xFFFF}, 0, 3),
            scan("short, consecutive sentinels collapse", SHORT, 0xFFFF, new int[]{0, 1, 0xFFFF, 0xFFFF, 0xFFFF, 5, 6}, 0, 2, 5, 2),
            scan("short, all sentinels: no runs", SHORT, 0xFFFF, new int[]{0xFFFF, 0xFFFF, 0xFFFF}),
            scan("int, 0xFFFFFFFF sentinel", INT, 0xFFFFFFFF, new int[]{0, 1, 2, 0xFFFFFFFF, 4, 5}, 0, 3, 4, 2),
            scan("int, custom sentinel 0", INT, 0, new int[]{5, 6, 0, 7, 8, 0, 9}, 0, 2, 3, 2, 6, 1));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scans")
    void scanIndicesSplitsAtTheSentinel(String name, int glType, int sentinel, int[] indices, int[] expectedPairs) {
        final ByteBuffer b = glType == SHORT ? shortBuf(indices) : intBuf(indices);
        try {
            assertSplits(EBOSplitScanner.scanIndices(b, indices.length, glType, sentinel), expectedPairs);
        } finally {
            MemoryUtil.memFree(b);
        }
    }

    private static EBOSplitScanner.EboSplit[] runs(int... pairs) {
        final EBOSplitScanner.EboSplit[] out = new EBOSplitScanner.EboSplit[pairs.length / 2];
        for (int i = 0; i < out.length; i++) out[i] = new EBOSplitScanner.EboSplit(pairs[i * 2], pairs[i * 2 + 1]);
        return out;
    }

    private static Arguments window(String name, EBOSplitScanner.EboSplit[] full, int firstIndex, int count, int... expectedPairs) {
        return Arguments.of(name, full, firstIndex, count, expectedPairs);
    }

    static Stream<Arguments> windows() {
        return Stream.of(
            window("no runs", runs(), 0, 10),
            window("window contains one run: translated to the window", runs(0, 3, 4, 3), 4, 3, 0, 3),
            window("window spans several runs", runs(0, 3, 4, 3, 8, 2), 2, 7, 0, 1, 2, 3, 6, 1),
            window("window outside every run", runs(0, 3, 4, 3), 100, 50),
            window("window clips a run's start and end", runs(2, 10), 5, 4, 0, 4));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("windows")
    void sliceSplitsClipsRunsToTheWindow(String name, EBOSplitScanner.EboSplit[] full, int firstIndex, int count, int[] expectedPairs) {
        assertSplits(slice(full, firstIndex, count), expectedPairs);
    }
}
