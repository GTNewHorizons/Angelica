package com.gtnewhorizons.angelica.utils;

import net.minecraft.client.renderer.texture.TextureUtil;

public final class SpritePadding {

    private static int currentGutter;

    private static int[][] scratch;

    private SpritePadding() {}

    public static int gutterFor(int mipmapLevels, boolean terrainAtlas) {
        if (mipmapLevels <= 0) {
            return terrainAtlas ? 1 : 0;
        }
        return 1 << mipmapLevels;
    }

    public static int setGutter(int gutter) {
        final int previous = currentGutter;
        currentGutter = gutter;
        return previous;
    }

    public static int currentGutter() {
        return currentGutter;
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
            final int size = (levelWidth + 2 * levelGutter) * (levelHeight + 2 * levelGutter);

            int[] target = padded[level];
            if (target == null || target.length != size) {
                target = new int[size];
                padded[level] = target;
            }
            System.arraycopy(levelData, 0, target, 0, Math.min(levelData.length, levelWidth * levelHeight));
            padClampToEdge(target, levelWidth, levelHeight, levelGutter);
        }
        return padded;
    }

    private static void padClampToEdge(int[] data, int width, int height, int border) {
        if (border <= 0 || width <= 0 || height <= 0) {
            return;
        }

        final int stride = width + 2 * border;

        for (int y = height - 1; y >= 0; y--) {
            System.arraycopy(data, y * width, data, border + (y + border) * stride, width);
        }

        clampBorderToEdge(data, stride, height + 2 * border, border);
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
