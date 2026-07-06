package com.gtnewhorizons.angelica.render;

import java.nio.ByteBuffer;

final class CloudEdges {
    private static final int CELL_EMPTY_THRESHOLD = 10;

    final int width, height;
    private final int wMask, hMask;
    private final boolean whPow2;
    private final long[] opaqueBits;
    final int opaqueCount;

    CloudEdges(int width, int height, ByteBuffer rgba) {
        this.width = width;
        this.height = height;
        this.wMask = width - 1;
        this.hMask = height - 1;
        this.whPow2 = (width & wMask) == 0 && (height & hMask) == 0;
        final int total = width * height;
        final long[] bits = new long[(total + 63) >>> 6];
        int op = 0;
        for (int i = 0; i < total; i++) {
            if ((rgba.get(i * 4 + 3) & 0xFF) >= CELL_EMPTY_THRESHOLD) {
                bits[i >>> 6] |= 1L << (i & 63);
                op++;
            }
        }
        this.opaqueBits = bits;
        this.opaqueCount = op;
    }

    boolean cellOpaque(int x, int z) {
        final int xw, zh;
        if (whPow2) {
            xw = x & wMask;
            zh = z & hMask;
        } else {
            xw = Math.floorMod(x, width);
            zh = Math.floorMod(z, height);
        }
        final int idx = xw + zh * width;
        return ((opaqueBits[idx >>> 6] >>> (idx & 63)) & 1L) != 0L;
    }

    long readOpaqueRowBits(int x0, int z) {
        final int zMod, x0Mod;
        if (whPow2) {
            zMod = z & hMask;
            x0Mod = x0 & wMask;
        } else {
            zMod = Math.floorMod(z, height);
            x0Mod = Math.floorMod(x0, width);
        }
        if (x0Mod + 10 <= width) {
            return readContigBits(opaqueBits, (long) zMod * width + x0Mod, 10);
        }
        final int colsBeforeWrap = width - x0Mod;
        final long firstPart = readContigBits(opaqueBits, (long) zMod * width + x0Mod, colsBeforeWrap);
        final long secondPart = readContigBits(opaqueBits, (long) zMod * width, 10 - colsBeforeWrap);
        return firstPart | (secondPart << colsBeforeWrap);
    }

    private static long readContigBits(long[] bits, long startBitIdx, int count) {
        final int wordIdx = (int) (startBitIdx >>> 6);
        final int bitOff = (int) (startBitIdx & 63);
        final long mask = count == 64 ? -1L : (1L << count) - 1L;
        final long w0 = bits[wordIdx];
        if (bitOff + count <= 64) {
            return (w0 >>> bitOff) & mask;
        }
        final long w1 = bits[wordIdx + 1];
        return ((w0 >>> bitOff) | (w1 << (64 - bitOff))) & mask;
    }
}
