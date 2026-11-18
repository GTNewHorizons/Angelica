package com.gtnewhorizons.angelica.utils;

import net.minecraft.client.renderer.texture.TextureUtil;

import java.util.Arrays;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutInt;

public final class SpritePadding {

    private static int[][] scratch;

    private SpritePadding() {}

    /**
     * The gutter has to survive being halved once per mip level, and the stitcher slot has to stay a multiple
     * of 2^mipmapLevels so every level's origin lands on a texel, so 1 &lt;&lt; mipmapLevels is the only workable
     * width. At mip 0 the terrain atlas still needs one texel for the RGSS taps to land in.
     */
    public static int gutterFor(int mipmapLevels, boolean terrainAtlas) {
        if (mipmapLevels <= 0) {
            return terrainAtlas ? 1 : 0;
        }
        return 1 << mipmapLevels;
    }

    public static int gutterForLevel(int gutter, int level) {
        return gutter >> level;
    }

    public static void writePaddedLevel(int[] src, int levelW, int levelH, int gutter, long dstAddress,
        int dstStrideBytes, int dstX, int dstY, int clipY0, int clipY1, boolean swapRB) {
        writeLevel(src, levelW, levelH, gutter, levelH, null, 0, 0, dstAddress, dstStrideBytes, dstX, dstY, clipY0,
            clipY1, swapRB);
    }

    private static void writeLevel(int[] src, int levelW, int levelH, int gutter, int availableRows,
        int[] dstArray, int dstArrayStride, int dstArrayOffset, long dstAddress, int dstStrideBytes, int dstX,
        int dstY, int clipY0, int clipY1, boolean swapRB) {
        final int paddedH = levelH + 2 * gutter;
        final int rowStart = Math.max(0, clipY0 - dstY);
        final int rowEnd = Math.min(paddedH, clipY1 - dstY);
        final int lastAvailableRow = availableRows - 1;

        for (int py = rowStart; py < rowEnd; py++) {
            int cy = clampCoord(py - gutter, levelH);
            if (cy > lastAvailableRow) {
                cy = lastAvailableRow;
            }
            if (cy < 0) {
                continue;
            }
            final int rowBase = cy * levelW;
            final int firstValue = src[rowBase];
            final int lastValue = src[rowBase + levelW - 1];

            if (dstArray != null) {
                final int rowOffset = dstArrayOffset + (dstY + py) * dstArrayStride + dstX;
                Arrays.fill(dstArray, rowOffset, rowOffset + gutter, firstValue);
                System.arraycopy(src, rowBase, dstArray, rowOffset + gutter, levelW);
                Arrays.fill(dstArray, rowOffset + gutter + levelW, rowOffset + gutter + levelW + gutter, lastValue);
            } else {
                final long rowAddress = dstAddress + (long) (dstY + py) * dstStrideBytes + (long) dstX * 4;
                final long interiorAddress = rowAddress + (long) gutter * 4;
                final long rightAddress = interiorAddress + (long) levelW * 4;
                if (swapRB) {
                    final int firstSwapped = swapRedBlue(firstValue);
                    final int lastSwapped = swapRedBlue(lastValue);
                    for (int i = 0; i < gutter; i++) {
                        memPutInt(rowAddress + (long) i * 4, firstSwapped);
                    }
                    for (int i = 0; i < levelW; i++) {
                        memPutInt(interiorAddress + (long) i * 4, swapRedBlue(src[rowBase + i]));
                    }
                    for (int i = 0; i < gutter; i++) {
                        memPutInt(rightAddress + (long) i * 4, lastSwapped);
                    }
                } else {
                    for (int i = 0; i < gutter; i++) {
                        memPutInt(rowAddress + (long) i * 4, firstValue);
                    }
                    for (int i = 0; i < levelW; i++) {
                        memPutInt(interiorAddress + (long) i * 4, src[rowBase + i]);
                    }
                    for (int i = 0; i < gutter; i++) {
                        memPutInt(rightAddress + (long) i * 4, lastValue);
                    }
                }
            }
        }
    }

    private static int swapRedBlue(int argb) {
        return (argb & 0xFF00FF00) | ((argb & 0x00FF0000) >>> 16) | ((argb & 0x000000FF) << 16);
    }

    private static int clampCoord(int coord, int limit) {
        if (coord < 0) {
            return 0;
        }
        if (coord >= limit) {
            return limit - 1;
        }
        return coord;
    }

    public static void uploadPadded(int[][] frameData, int width, int height, int originX, int originY, int gutter,
        boolean blur, boolean clamp) {
        if (gutter <= 0) {
            TextureUtil.uploadTextureMipmap(frameData, width, height, originX, originY, blur, clamp);
            return;
        }

        scratch = padFrame(frameData, width, height, gutter, scratch);
        TextureUtil.uploadTextureMipmap(scratch, width + 2 * gutter, height + 2 * gutter,
            originX - gutter, originY - gutter, blur, clamp);
    }

    private static int[][] padFrame(int[][] frameData, int width, int height, int gutter, int[][] scratch) {
        final int[][] padded = scratch != null && scratch.length == frameData.length ? scratch
            : new int[frameData.length][];

        for (int level = 0; level < frameData.length; level++) {
            final int[] levelData = frameData[level];
            if (levelData == null) {
                padded[level] = null;
                continue;
            }

            final int levelWidth = width >> level;
            final int levelHeight = height >> level;
            final int levelGutter = gutterForLevel(gutter, level);
            final int stride = levelWidth + 2 * levelGutter;
            final int paddedHeight = levelHeight + 2 * levelGutter;
            final int size = stride * paddedHeight;

            int[] target = padded[level];
            if (target == null || target.length < size) {
                target = new int[size];
                padded[level] = target;
            }

            final int availableRows = levelWidth > 0 ? Math.min(levelHeight, levelData.length / levelWidth) : 0;
            writeLevel(levelData, levelWidth, levelHeight, levelGutter, availableRows, target, stride, 0, 0L, 0, 0,
                0, 0, paddedHeight, false);
        }
        return padded;
    }
}
