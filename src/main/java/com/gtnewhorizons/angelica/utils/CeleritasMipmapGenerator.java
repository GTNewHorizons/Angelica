package com.gtnewhorizons.angelica.utils;

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import org.embeddedt.embeddium.api.util.ColorARGB;
import org.embeddedt.embeddium.impl.texture.MipmapHelper;

import static com.gtnewhorizons.angelica.utils.MipmapGenerator.ALPHA_BIAS;
import static org.joml.Math.clamp;

final class CeleritasMipmapGenerator {

    private CeleritasMipmapGenerator() {
    }

    static int[][] generateMipLevels(int mipLevel, int width, int[][] currentMips,
                                     boolean isCutout, boolean darkCutout, float cutoutRef) {
        final int[][] result = new int[mipLevel + 1][];
        final int[] base = currentMips[0];

        if (mipLevel > 0 && (currentMips.length == 1 || currentMips[1] == null)) {
            if (darkCutout) {
                MipmapGenerator.fillEmptyAreasWithDarkColor(base, 1);
            } else {
                solidify(base, width, base.length / width);
            }
        }

        result[0] = base;
        if (mipLevel <= 0) {
            return result;
        }

        final float originalCoverage = isCutout ? alphaTestCoverage(base, width, cutoutRef, 1.0f) : 0.0f;

        for (int level = 1; level <= mipLevel; level++) {
            final int levelWidth = width >> level;
            final int[] provided = level < currentMips.length ? currentMips[level] : null;

            if (provided != null) {
                result[level] = provided;
            } else {
                final int[] input = result[level - 1];
                final int prevWidth = width >> (level - 1);
                final int prevHeight = input.length / prevWidth;
                final int levelHeight = prevHeight >> 1;
                final int[] output = new int[levelWidth * levelHeight];

                for (int y = 0; y < levelHeight; y++) {
                    for (int x = 0; x < levelWidth; x++) {
                        final int i = x * 2 + y * 2 * prevWidth;
                        output[x + y * levelWidth] = MipmapHelper.weightedAverageColor(
                            MipmapHelper.weightedAverageColor(input[i], input[i + 1]),
                            MipmapHelper.weightedAverageColor(input[i + prevWidth], input[i + prevWidth + 1]));
                    }
                }
                result[level] = output;
            }

            if (isCutout) {
                scaleAlphaToCoverage(result[level], levelWidth, originalCoverage, cutoutRef);
            }
        }

        return result;
    }

    private static void solidify(int[] image, int width, int height) {
        final int size = Math.min(width * height, image.length);
        if (size <= 0) {
            return;
        }

        boolean hasTransparent = false;
        for (int i = 0; i < size; i++) {
            if (ColorARGB.unpackAlpha(image[i]) == 0) {
                hasTransparent = true;
                break;
            }
        }
        if (!hasTransparent) {
            return;
        }

        final int[] color = new int[size];
        final IntArrayFIFOQueue queue = new IntArrayFIFOQueue(64);

        for (int i = 0; i < size; i++) {
            final int pixel = image[i];
            if (ColorARGB.unpackAlpha(pixel) == 0) {
                continue;
            }
            color[i] = pixel;
            if (hasTransparentNeighbor(image, i, width, size)) {
                queue.enqueue(i);
            }
        }

        if (queue.isEmpty()) {
            return;
        }

        while (!queue.isEmpty()) {
            final int index = queue.dequeueInt();
            final int x = index % width;
            final int source = color[index];

            if (x > 0) {
                fill(index - 1, source, color, queue);
            }
            if (x < width - 1) {
                fill(index + 1, source, color, queue);
            }
            if (index >= width) {
                fill(index - width, source, color, queue);
            }
            if (index + width < size) {
                fill(index + width, source, color, queue);
            }
        }

        for (int i = 0; i < size; i++) {
            if (ColorARGB.unpackAlpha(image[i]) == 0) {
                image[i] = color[i] & 0x00FFFFFF;
            }
        }
    }

    private static boolean hasTransparentNeighbor(int[] image, int index, int width, int size) {
        final int x = index % width;
        return (x > 0 && ColorARGB.unpackAlpha(image[index - 1]) == 0)
            || (x < width - 1 && ColorARGB.unpackAlpha(image[index + 1]) == 0)
            || (index >= width && ColorARGB.unpackAlpha(image[index - width]) == 0)
            || (index + width < size && ColorARGB.unpackAlpha(image[index + width]) == 0);
    }

    private static void fill(int neighbor, int source, int[] color, IntArrayFIFOQueue queue) {
        if (color[neighbor] == 0) {
            color[neighbor] = source;
            queue.enqueue(neighbor);
        }
    }

    private static float alphaTestCoverage(int[] image, int width, float alphaRef, float alphaScale) {
        final int height = image.length / width;
        final float alphaFactor = alphaScale / 255.0f;
        float total = 0.0f;
        int quads = 0;

        for (int y = 0; y < height - 1; y++) {
            for (int x = 0; x < width - 1; x++) {
                final int i = x + y * width;
                final float a00 = scaledAlpha(image[i], alphaFactor);
                final float a10 = scaledAlpha(image[i + 1], alphaFactor);
                final float a01 = scaledAlpha(image[i + width], alphaFactor);
                final float a11 = scaledAlpha(image[i + width + 1], alphaFactor);

                final float lo = Math.min(Math.min(a00, a10), Math.min(a01, a11));
                if (lo > alphaRef) {
                    total += 1.0f;
                    quads++;
                    continue;
                }
                final float hi = Math.max(Math.max(a00, a10), Math.max(a01, a11));
                if (hi <= alphaRef) {
                    quads++;
                    continue;
                }

                int hits = 0;
                for (int sy = 0; sy < 4; sy++) {
                    final float fy = (sy + 0.5f) / 4.0f;
                    for (int sx = 0; sx < 4; sx++) {
                        final float fx = (sx + 0.5f) / 4.0f;
                        final float top = a00 + (a10 - a00) * fx;
                        final float bottom = a01 + (a11 - a01) * fx;
                        if (top + (bottom - top) * fy > alphaRef) {
                            hits++;
                        }
                    }
                }
                total += hits / 16.0f;
                quads++;
            }
        }

        if (quads == 0) {
            return scaledAlpha(image[0], alphaFactor) > alphaRef ? 1.0f : 0.0f;
        }
        return total / quads;
    }

    private static float scaledAlpha(int color, float alphaFactor) {
        return clamp(0.0f, 1.0f, ColorARGB.unpackAlpha(color) * alphaFactor);
    }

    private static void scaleAlphaToCoverage(int[] image, int width, float desiredCoverage, float alphaRef) {
        float min = 0.0f;
        float max = 4.0f;
        float scale = 1.0f;
        float bestScale = 1.0f;
        float bestError = Float.POSITIVE_INFINITY;

        for (int i = 0; i < 5; i++) {
            final float coverage = alphaTestCoverage(image, width, alphaRef, scale);
            final float error = Math.abs(coverage - desiredCoverage);
            if (error < bestError) {
                bestError = error;
                bestScale = scale;
            }

            if (coverage < desiredCoverage) {
                min = scale;
            } else if (!(coverage > desiredCoverage)) {
                break;
            } else {
                max = scale;
            }
            scale = (min + max) / 2.0f;
        }

        for (int i = 0; i < image.length; i++) {
            final int color = image[i];
            final int rawAlpha = ColorARGB.unpackAlpha(color);
            final float bias = rawAlpha > 0 ? ALPHA_BIAS : 0.0f;
            final int alpha = (int) Math.floor(clamp(0.0f, 1.0f, rawAlpha / 255.0f * bestScale + bias) * 255.0f);
            image[i] = ColorARGB.withAlpha(color, alpha);
        }
    }

}
