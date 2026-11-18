package com.gtnewhorizons.angelica.utils;

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntArrays;
import org.embeddedt.embeddium.api.util.ColorARGB;

import java.util.Arrays;

import static com.gtnewhorizons.angelica.utils.MipmapGenerator.ALPHA_BIAS;
import static org.joml.Math.clamp;

/**
 * Minecraft 26.2's mipmap generation and texture preparation.
 */
final class ModernMipmapGenerator {

    private static final float[] NORMALIZED_ALPHA = new float[256];

    static {
        for (int i = 0; i < NORMALIZED_ALPHA.length; i++) {
            NORMALIZED_ALPHA[i] = i / 255.0f;
        }
    }

    private ModernMipmapGenerator() {
    }

    static int[][] generateMipLevels(int mipLevel, int width, int[][] currentMips, boolean isCutout, boolean darkCutout, float cutoutRef, float alphaCutoffBias, boolean isItem) {
        final int[] base = currentMips[0];

        // Unlike modern NativeImage arrays, 1.7.10 preallocates null slots for missing mip levels.
        boolean hasProvidedMips = false;
        boolean complete = mipLevel < currentMips.length;
        int maxPixels = base.length;
        for (int level = 1; level < currentMips.length; level++) {
            if (currentMips[level] != null) {
                hasProvidedMips = true;
                if (level <= mipLevel) {
                    maxPixels = Math.max(maxPixels, currentMips[level].length);
                }
            } else if (level <= mipLevel) {
                complete = false;
            }
        }
        if (!hasProvidedMips && !isItem && isCutout) {
            if (darkCutout) {
                MipmapGenerator.fillEmptyAreasWithDarkColor(base, width);
            } else {
                solidify(base, width);
            }
        }

        if (complete) {
            return currentMips.length == mipLevel + 1 ? currentMips : Arrays.copyOf(currentMips, mipLevel + 1);
        }

        final int[][] result = Arrays.copyOf(currentMips, mipLevel + 1);
        final float[] alphaScratch = isCutout ? new float[maxPixels] : null;
        final float originalCoverage = isCutout ? alphaTestCoverage(base, width, cutoutRef, 1.0f, alphaScratch) : 0.0f;

        for (int level = 1; level <= mipLevel; level++) {
            final int levelWidth = width >> level;
            if (levelWidth == 0) {
                if (result[level] == null) {
                    result[level] = IntArrays.EMPTY_ARRAY;
                }
                continue;
            }
            if (result[level] == null) {
                final int[] input = result[level - 1];
                final int prevWidth = width >> (level - 1);
                final int levelHeight = (input.length / prevWidth) >> 1;
                if (levelHeight == 0) {
                    result[level] = IntArrays.EMPTY_ARRAY;
                    continue;
                }
                final int[] output = new int[levelWidth * levelHeight];
                for (int y = 0; y < levelHeight; y++) {
                    for (int x = 0; x < levelWidth; x++) {
                        final int i = x * 2 + y * 2 * prevWidth;
                        output[x + y * levelWidth] = ModernMipmapBlender.blend(input[i], input[i + 1], input[i + prevWidth], input[i + prevWidth + 1], darkCutout);
                    }
                }
                result[level] = output;
            }
            if (isCutout && result[level].length > 0) {
                scaleAlphaToCoverage(result[level], levelWidth, originalCoverage, cutoutRef, alphaCutoffBias, alphaScratch);
            }
        }
        return result;
    }

    private static void solidify(int[] image, int width) {
        boolean hasTransparentPixel = false;
        boolean hasOpaquePixel = false;
        for (int color : image) {
            if (ColorARGB.unpackAlpha(color) == 0) {
                hasTransparentPixel = true;
            } else {
                hasOpaquePixel = true;
            }
            if (hasTransparentPixel && hasOpaquePixel) {
                break;
            }
        }
        if (!hasTransparentPixel) {
            return;
        }

        if (!hasOpaquePixel) {
            Arrays.fill(image, 0);
            return;
        }
        final int height = image.length / width;
        final int[] nearestColor = new int[image.length];
        final IntArrayFIFOQueue queue = new IntArrayFIFOQueue(64);

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                final int i = x + y * width;
                if (ColorARGB.unpackAlpha(image[i]) != 0) {
                    nearestColor[i] = image[i];
                    if (hasTransparentNeighbor(image, width, i, x)) {
                        queue.enqueue(i);
                    }
                }
            }
        }
        while (!queue.isEmpty()) {
            final int i = queue.dequeueInt();
            final int x = i % width;
            final int color = nearestColor[i];
            if (x < width - 1) {
                fill(i + 1, color, nearestColor, queue);
            }
            if (x > 0) {
                fill(i - 1, color, nearestColor, queue);
            }
            if (i + width < image.length) {
                fill(i + width, color, nearestColor, queue);
            }
            if (i >= width) {
                fill(i - width, color, nearestColor, queue);
            }
        }
        for (int i = 0; i < image.length; i++) {
            if (ColorARGB.unpackAlpha(image[i]) == 0) {
                image[i] = nearestColor[i] & 0x00FFFFFF;
            }
        }
    }

    private static boolean hasTransparentNeighbor(int[] image, int width, int i, int x) {
        return (x < width - 1 && ColorARGB.unpackAlpha(image[i + 1]) == 0) || (x > 0 && ColorARGB.unpackAlpha(image[i - 1]) == 0) || (i + width < image.length && ColorARGB.unpackAlpha(image[i + width]) == 0)
            || (i >= width && ColorARGB.unpackAlpha(image[i - width]) == 0);
    }

    private static void fill(int i, int color, int[] nearestColor, IntArrayFIFOQueue queue) {
        if (nearestColor[i] == 0) {
            nearestColor[i] = color;
            queue.enqueue(i);
        }
    }

    private static float alphaTestCoverage(int[] image, int width, float alphaRef, float alphaScale, float[] alphaScratch) {
        final int height = image.length / width;
        for (int i = 0; i < image.length; i++) {
            alphaScratch[i] = clamp(0.0f, 1.0f, NORMALIZED_ALPHA[ColorARGB.unpackAlpha(image[i])] * alphaScale);
        }
        final float margin = 8.0f * Math.ulp(alphaRef);
        final float safelyAbove = alphaRef + margin;
        final float safelyBelow = alphaRef - margin;
        float coverage = 0.0f;
        for (int y = 0; y < height - 1; y++) {
            for (int x = 0; x < width - 1; x++) {
                final int i = x + y * width;
                final float a00 = alphaScratch[i];
                final float a10 = alphaScratch[i + 1];
                final float a01 = alphaScratch[i + width];
                final float a11 = alphaScratch[i + width + 1];
                if (a00 > safelyAbove && a10 > safelyAbove && a01 > safelyAbove && a11 > safelyAbove) {
                    coverage += 1.0f;
                    continue;
                }
                if (a00 < safelyBelow && a10 < safelyBelow && a01 < safelyBelow && a11 < safelyBelow) {
                    continue;
                }
                float texelCoverage = 0.0f;
                for (int sy = 0; sy < 4; sy++) {
                    final float fy = (sy + 0.5f) / 4.0f;
                    for (int sx = 0; sx < 4; sx++) {
                        final float fx = (sx + 0.5f) / 4.0f;
                        final float alpha = a00 * (1.0f - fx) * (1.0f - fy) + a10 * fx * (1.0f - fy) + a01 * (1.0f - fx) * fy + a11 * fx * fy;
                        if (alpha > alphaRef) {
                            texelCoverage++;
                        }
                    }
                }
                coverage += texelCoverage / 16.0f;
            }
        }
        return coverage / ((width - 1) * (height - 1));
    }

    private static void scaleAlphaToCoverage(int[] image, int width, float desiredCoverage, float alphaRef, float alphaCutoffBias, float[] alphaScratch) {
        float min = 0.0f;
        float max = 4.0f;
        float scale = 1.0f;
        float bestScale = 1.0f;
        float bestError = Float.MAX_VALUE;

        if (width > 1 && image.length / width > 1) {
            for (int i = 0; i < 5; i++) {
                final float coverage = alphaTestCoverage(image, width, alphaRef, scale, alphaScratch);
                final float error = Math.abs(coverage - desiredCoverage);
                if (error < bestError) {
                    bestError = error;
                    bestScale = scale;
                }
                if (coverage < desiredCoverage) {
                    min = scale;
                } else if (coverage > desiredCoverage) {
                    max = scale;
                } else {
                    break;
                }
                scale = (min + max) * 0.5f;
            }
        }

        for (int i = 0; i < image.length; i++) {
            final int color = image[i];
            final float alpha = NORMALIZED_ALPHA[ColorARGB.unpackAlpha(color)] * bestScale + alphaCutoffBias + ALPHA_BIAS;
            image[i] = ColorARGB.withAlpha(color, (int) (clamp(0.0f, 1.0f, alpha) * 255.0f));
        }
    }

}
