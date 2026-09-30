package com.gtnewhorizons.angelica.utils;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpritePaddingTest {

    private static final int[] GUTTERS = { 0, 1, 2, 4 };
    private static final int DST_X = 3;
    private static final int DST_Y = 2;
    private static final int MARGIN = 5;
    private static final byte SENTINEL = (byte) 0xCD;

    private static int[][] padFrame(int[][] frameData, int width, int height, int gutter) {
        try {
            final Method method = SpritePadding.class.getDeclaredMethod("padFrame", int[][].class, int.class, int.class, int.class, int[][].class);
            method.setAccessible(true);
            return (int[][]) method.invoke(null, frameData, width, height, gutter, (Object) null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void putLittleEndian(byte[] dst, int offset, int value) {
        dst[offset] = (byte) value;
        dst[offset + 1] = (byte) (value >>> 8);
        dst[offset + 2] = (byte) (value >>> 16);
        dst[offset + 3] = (byte) (value >>> 24);
    }

    private static final class Level {

        final int[] src;
        final int levelW;
        final int levelH;
        final int gutter;
        final int paddedW;
        final int paddedH;
        final int strideBytes;
        final int totalHeight;

        Level(Random rnd, int level, int baseGutter, int levelW, int levelH) {
            this.levelW = levelW;
            this.levelH = levelH;
            this.gutter = SpritePadding.gutterForLevel(baseGutter, level);
            this.src = new int[levelW * levelH];
            for (int i = 0; i < src.length; i++) {
                src[i] = rnd.nextInt();
            }
            this.paddedW = levelW + 2 * gutter;
            this.paddedH = levelH + 2 * gutter;
            this.strideBytes = (DST_X + paddedW + MARGIN) * 4;
            this.totalHeight = DST_Y + paddedH + MARGIN;
        }
    }

    private static final class NativeBuffer {

        final ByteBuffer buffer;
        final long address;

        NativeBuffer(int size) {
            this.buffer = ByteBuffer.allocateDirect(size);
            for (int i = 0; i < size; i++) {
                this.buffer.put(i, SENTINEL);
            }
            this.address = memAddress(this.buffer, 0);
        }

        byte[] snapshot() {
            final byte[] out = new byte[buffer.capacity()];
            for (int i = 0; i < out.length; i++) {
                out[i] = buffer.get(i);
            }
            return out;
        }
    }

    @Test
    void clippedHalvesReassembleWhole() {
        final Random rnd = new Random(987654321);

        for (int level = 0; level <= 4; level++) {
            for (int baseGutter : GUTTERS) {
                final int fLevel = level;
                final int levelW = 1 + rnd.nextInt(64);
                final int levelH = 1 + rnd.nextInt(64);
                final Level fixture = new Level(rnd, level, baseGutter, levelW, levelH);
                final int splitRow = fixture.paddedH <= 1 ? 0 : 1 + rnd.nextInt(fixture.paddedH - 1);
                final int size = fixture.strideBytes * fixture.totalHeight;

                for (boolean swapRB : new boolean[] { false, true }) {
                    final NativeBuffer whole = new NativeBuffer(size);
                    SpritePadding.writePaddedLevel(fixture.src, fixture.levelW, fixture.levelH, fixture.gutter,
                        whole.address, fixture.strideBytes, DST_X, DST_Y, DST_Y, DST_Y + fixture.paddedH, swapRB);

                    final NativeBuffer split = new NativeBuffer(size);
                    SpritePadding.writePaddedLevel(fixture.src, fixture.levelW, fixture.levelH, fixture.gutter, split.address, fixture.strideBytes, DST_X, DST_Y, DST_Y, DST_Y + splitRow, swapRB);
                    SpritePadding.writePaddedLevel(fixture.src, fixture.levelW, fixture.levelH, fixture.gutter, split.address, fixture.strideBytes, DST_X, DST_Y, DST_Y + splitRow, DST_Y + fixture.paddedH,
                        swapRB);

                    assertTrue(Arrays.equals(whole.snapshot(), split.snapshot()), () -> "split reassembly mismatch level=" + fLevel + " gutter=" + baseGutter + " swapRB=" + swapRB + " splitRow=" + splitRow);
                }
            }
        }
    }

    @Test
    void bytesOutsideRectAreUntouched() {
        final Random rnd = new Random(555111333);

        for (int level = 0; level <= 4; level++) {
            for (int baseGutter : GUTTERS) {
                final int fLevel = level;
                final int levelW = 1 + rnd.nextInt(64);
                final int levelH = 1 + rnd.nextInt(64);
                final Level fixture = new Level(rnd, level, baseGutter, levelW, levelH);
                final NativeBuffer nb = new NativeBuffer(fixture.strideBytes * fixture.totalHeight);

                SpritePadding.writePaddedLevel(fixture.src, fixture.levelW, fixture.levelH, fixture.gutter, nb.address, fixture.strideBytes, DST_X, DST_Y, DST_Y, DST_Y + fixture.paddedH, false);
                final byte[] actual = nb.snapshot();

                for (int row = 0; row < fixture.totalHeight; row++) {
                    final boolean insideRow = row >= DST_Y && row < DST_Y + fixture.paddedH;
                    for (int col = 0; col < fixture.strideBytes; col++) {
                        final boolean insideCol = col >= DST_X * 4 && col < (DST_X + fixture.paddedW) * 4;
                        if (!(insideRow && insideCol)) {
                            final int index = row * fixture.strideBytes + col;
                            final int fRow = row;
                            final int fCol = col;
                            assertTrue(actual[index] == SENTINEL, () -> "byte outside rect touched at row=" + fRow + " col=" + fCol + " level=" + fLevel + " gutter=" + baseGutter);
                        }
                    }
                }
            }
        }
    }

    @Test
    void intArraySinkMatchesNativeSink() {
        final Random rnd = new Random(24681012);
        final int gutter = 4;
        final int levels = 3;
        final int width = 32;
        final int height = 24;

        final int[][] frameData = new int[levels][];
        for (int level = 0; level < levels; level++) {
            final int lw = width >> level;
            final int lh = height >> level;
            final int[] data = new int[lw * lh];
            for (int i = 0; i < data.length; i++) {
                data[i] = rnd.nextInt();
            }
            frameData[level] = data;
        }

        final int[][] padded = padFrame(frameData, width, height, gutter);

        for (int level = 0; level < levels; level++) {
            final int fLevel = level;
            final int lw = width >> level;
            final int lh = height >> level;
            final int lg = SpritePadding.gutterForLevel(gutter, level);
            final int paddedW = lw + 2 * lg;
            final int paddedH = lh + 2 * lg;
            final int strideBytes = paddedW * 4;

            final NativeBuffer nb = new NativeBuffer(strideBytes * paddedH);
            SpritePadding.writePaddedLevel(frameData[level], lw, lh, lg, nb.address, strideBytes, 0, 0, 0, paddedH,
                false);

            final int[] fromArray = padded[level];
            final byte[] expected = new byte[strideBytes * paddedH];
            for (int py = 0; py < paddedH; py++) {
                for (int px = 0; px < paddedW; px++) {
                    putLittleEndian(expected, py * strideBytes + px * 4, fromArray[py * paddedW + px]);
                }
            }

            assertTrue(Arrays.equals(expected, nb.snapshot()), () -> "int[] vs native sink mismatch level=" + fLevel);

            final NativeBuffer nbSwapped = new NativeBuffer(strideBytes * paddedH);
            SpritePadding.writePaddedLevel(frameData[level], lw, lh, lg, nbSwapped.address, strideBytes, 0, 0, 0,
                paddedH, true);

            final byte[] expectedSwapped = new byte[strideBytes * paddedH];
            for (int py = 0; py < paddedH; py++) {
                for (int px = 0; px < paddedW; px++) {
                    final int v = fromArray[py * paddedW + px];
                    final int swapped = (v & 0xFF00FF00) | ((v >> 16) & 0xFF) | ((v & 0xFF) << 16);
                    putLittleEndian(expectedSwapped, py * strideBytes + px * 4, swapped);
                }
            }

            assertTrue(Arrays.equals(expectedSwapped, nbSwapped.snapshot()), () -> "int[] vs native sink (swapRB) mismatch level=" + fLevel);
        }
    }
}
