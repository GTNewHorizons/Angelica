package com.gtnewhorizons.angelica.utils;

import it.unimi.dsi.fastutil.ints.IntArrays;
import org.embeddedt.embeddium.api.util.ColorARGB;

/**
 * Minecraft 1.7.10's TextureUtil mipmap generator without its shared mutable color buffer.
 */
final class VanillaMipmapGenerator {

    private static final float[] TO_LINEAR = new float[256];

    static {
        for (int i = 0; i < TO_LINEAR.length; i++) {
            TO_LINEAR[i] = (float) Math.pow(i / 255.0f, 2.2);
        }
    }

    private VanillaMipmapGenerator() {
    }

    static int[][] generateMipLevels(int mipLevel, int width, int[][] currentMips) {
        final int[][] result = new int[mipLevel + 1][];
        result[0] = currentMips[0];
        if (mipLevel <= 0) {
            return result;
        }

        boolean hasTransparentPixel = false;
        for (int i = 0; i < Math.min(currentMips.length, result[0].length); i++) {
            if (ColorARGB.unpackAlpha(result[0][i]) == 0) {
                hasTransparentPixel = true;
                break;
            }
        }

        for (int level = 1; level <= mipLevel; level++) {
            final int[] provided = level < currentMips.length ? currentMips[level] : null;
            if (provided != null) {
                result[level] = provided;
                continue;
            }

            final int levelWidth = width >> level;
            if (levelWidth == 0) {
                result[level] = IntArrays.EMPTY_ARRAY;
                continue;
            }
            final int[] input = result[level - 1];
            final int[] output = new int[input.length >> 2];
            final int levelHeight = output.length / levelWidth;
            final int stride = levelWidth << 1;
            for (int y = 0; y < levelHeight; y++) {
                for (int x = 0; x < levelWidth; x++) {
                    final int i = 2 * (x + y * stride);
                    output[x + y * levelWidth] = blend(input[i], input[i + 1], input[i + stride], input[i + stride + 1], hasTransparentPixel);
                }
            }
            result[level] = output;
        }
        return result;
    }

    private static int blend(int a, int b, int c, int d, boolean hasTransparentPixel) {
        if (hasTransparentPixel) {
            int alpha = transparentChannel(a, b, c, d, 24);
            if (alpha < 96) {
                alpha = 0;
            }
            return ColorARGB.pack(transparentChannel(a, b, c, d, 16), transparentChannel(a, b, c, d, 8), transparentChannel(a, b, c, d, 0), alpha);
        }
        return ColorARGB.pack(meanChannel(a, b, c, d, 16), meanChannel(a, b, c, d, 8), meanChannel(a, b, c, d, 0), meanChannel(a, b, c, d, 24));
    }

    private static int meanChannel(int a, int b, int c, int d, int shift) {
        final float sum = TO_LINEAR[(a >>> shift) & 255] + TO_LINEAR[(b >>> shift) & 255] + TO_LINEAR[(c >>> shift) & 255] + TO_LINEAR[(d >>> shift) & 255];
        final float value = (float) Math.pow(sum * 0.25, 1.0 / 2.2);
        return (int) (value * 255.0);
    }

    private static int transparentChannel(int a, int b, int c, int d, int shift) {
        float sum = 0.0f;
        if (ColorARGB.unpackAlpha(a) != 0) {
            sum += TO_LINEAR[(a >>> shift) & 255];
        }
        if (ColorARGB.unpackAlpha(b) != 0) {
            sum += TO_LINEAR[(b >>> shift) & 255];
        }
        if (ColorARGB.unpackAlpha(c) != 0) {
            sum += TO_LINEAR[(c >>> shift) & 255];
        }
        if (ColorARGB.unpackAlpha(d) != 0) {
            sum += TO_LINEAR[(d >>> shift) & 255];
        }
        return (int) (Math.pow(sum / 4.0f, 1.0 / 2.2) * 255.0);
    }
}
