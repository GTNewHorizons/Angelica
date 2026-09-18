package com.gtnewhorizons.angelica.utils;

import net.minecraft.client.renderer.texture.TextureUtil;

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
            final int levelGutter = gutter >> level;
            final int stride = levelWidth + 2 * levelGutter;
            final int size = stride * (levelHeight + 2 * levelGutter);

            int[] target = padded[level];
            if (target == null || target.length < size) {
                target = new int[size];
                padded[level] = target;
            }

            int remaining = Math.min(levelData.length, levelWidth * levelHeight);
            for (int y = 0; y < levelHeight && remaining > 0; y++) {
                final int run = Math.min(levelWidth, remaining);
                System.arraycopy(levelData, y * levelWidth, target,
                    (y + levelGutter) * stride + levelGutter, run);
                remaining -= run;
            }
            clampBorderToEdge(target, stride, levelHeight + 2 * levelGutter, levelGutter);
        }
        return padded;
    }

    private static void clampBorderToEdge(int[] image, int paddedWidth, int paddedHeight, int border) {
        if (border <= 0) {
            return;
        }

        final int contentWidth = paddedWidth - 2 * border;
        final int contentHeight = paddedHeight - 2 * border;
        if (contentWidth <= 0 || contentHeight <= 0) {
            return;
        }

        for (int y = 0; y < contentHeight; y++) {
            final int row = border + (y + border) * paddedWidth;
            final int left = image[row];
            final int right = image[row + contentWidth - 1];
            for (int i = 1; i <= border; i++) {
                image[row - i] = left;
                image[row + contentWidth - 1 + i] = right;
            }
        }

        final int top = border * paddedWidth;
        final int bottom = (border + contentHeight - 1) * paddedWidth;
        for (int i = 1; i <= border; i++) {
            System.arraycopy(image, top, image, top - i * paddedWidth, paddedWidth);
            System.arraycopy(image, bottom, image, bottom + i * paddedWidth, paddedWidth);
        }
    }
}
