package com.gtnewhorizons.angelica.textures;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.renderer.StitcherException;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.MathHelper;

public final class FastTextureStitcher {

    private FastTextureStitcher() {}

    public static Result stitch(Stitcher.Holder[] holders, int maxWidth, int maxHeight, boolean forcePowerOf2) {
        if (holders.length == 0) {
            return new Result(0, 0, Collections.<TextureAtlasSprite>emptyList());
        }

        long area = 0L;
        int minWidth = 1;
        int minHeight = 1;
        boolean[] initialRotations = new boolean[holders.length];

        for (int i = 0; i < holders.length; i++) {
            Stitcher.Holder holder = holders[i];
            initialRotations[i] = holder.isRotated();
            int width = holder.getWidth();
            int height = holder.getHeight();

            if (width > maxWidth || height > maxHeight) {
                throw unableToFit(holder, maxWidth, maxHeight);
            }

            area += (long) width * height;
            minWidth = Math.max(minWidth, width);
            minHeight = Math.max(minHeight, height);
        }

        int width = roundDimension(Math.max(minWidth, (int) Math.ceil(Math.sqrt(area))), forcePowerOf2);
        width = Math.min(width, maxWidth);
        int height = roundDimension(Math.max(minHeight, (int) ((area + width - 1L) / width)), forcePowerOf2);
        height = Math.min(height, maxHeight);

        while (width <= maxWidth && height <= maxHeight) {
            restoreRotations(holders, initialRotations);
            Result result = tryStitch(holders, width, height, forcePowerOf2);
            if (result != null) {
                return result;
            }

            if ((width <= height && width < maxWidth) || height >= maxHeight) {
                width = grow(width, maxWidth, forcePowerOf2);
            } else {
                height = grow(height, maxHeight, forcePowerOf2);
            }
        }

        throw unableToFit(holders[0], maxWidth, maxHeight);
    }

    private static Result tryStitch(Stitcher.Holder[] holders, int atlasWidth, int atlasHeight, boolean forcePowerOf2) {
        List<Node> skyline = new ArrayList<>();
        List<TextureAtlasSprite> sprites = new ArrayList<>(holders.length);
        int[] xs = new int[holders.length];
        int[] ys = new int[holders.length];
        skyline.add(new Node(0, 0, atlasWidth));

        int usedWidth = 0;
        int usedHeight = 0;

        for (int i = 0; i < holders.length; i++) {
            Stitcher.Holder holder = holders[i];
            Placement placement = findPlacement(skyline, atlasWidth, atlasHeight, holder);
            if (placement == null) {
                return null;
            }

            if (holder.isRotated() != placement.rotated) {
                holder.rotate();
            }

            place(skyline, placement);
            int width = holder.getWidth();
            int height = holder.getHeight();
            usedWidth = Math.max(usedWidth, placement.x + width);
            usedHeight = Math.max(usedHeight, placement.y + height);
            sprites.add(holder.getAtlasSprite());
            xs[i] = placement.x;
            ys[i] = placement.y;
        }

        int resultWidth = forcePowerOf2 ? atlasWidth : usedWidth;
        int resultHeight = forcePowerOf2 ? atlasHeight : usedHeight;

        for (int i = 0; i < holders.length; i++) {
            Stitcher.Holder holder = holders[i];
            TextureAtlasSprite sprite = holder.getAtlasSprite();
            sprite.initSprite(resultWidth, resultHeight, xs[i], ys[i], holder.isRotated());
        }

        return new Result(resultWidth, resultHeight, sprites);
    }

    private static Placement findPlacement(List<Node> skyline, int atlasWidth, int atlasHeight, Stitcher.Holder holder) {
        Placement best = findPlacement(skyline, atlasWidth, atlasHeight, holder.getWidth(), holder.getHeight(), holder.isRotated());

        holder.rotate();
        Placement rotated = findPlacement(skyline, atlasWidth, atlasHeight, holder.getWidth(), holder.getHeight(), holder.isRotated());
        holder.rotate();

        if (best == null) {
            return rotated;
        }

        if (rotated == null) {
            return best;
        }

        return rotated.bottom < best.bottom || rotated.bottom == best.bottom && rotated.x < best.x ? rotated : best;
    }

    private static Placement findPlacement(List<Node> skyline, int atlasWidth, int atlasHeight, int width, int height, boolean rotated) {
        Placement best = null;

        for (int i = 0; i < skyline.size(); i++) {
            Node node = skyline.get(i);
            int y = fit(skyline, i, width, height, atlasWidth, atlasHeight);

            if (y >= 0) {
                int bottom = y + height;
                if (best == null || bottom < best.bottom || bottom == best.bottom && node.x < best.x) {
                    best = new Placement(i, node.x, y, width, bottom, rotated);
                }
            }
        }

        return best;
    }

    private static int fit(List<Node> skyline, int index, int width, int height, int atlasWidth, int atlasHeight) {
        Node node = skyline.get(index);
        if (node.x + width > atlasWidth) {
            return -1;
        }

        int y = node.y;
        int widthLeft = width;

        for (int i = index; widthLeft > 0; i++) {
            if (i >= skyline.size()) {
                return -1;
            }

            Node current = skyline.get(i);
            y = Math.max(y, current.y);
            if (y + height > atlasHeight) {
                return -1;
            }

            widthLeft -= current.width;
        }

        return y;
    }

    private static void place(List<Node> skyline, Placement placement) {
        skyline.add(placement.index, new Node(placement.x, placement.bottom, placement.width));

        for (int i = placement.index + 1; i < skyline.size(); i++) {
            Node previous = skyline.get(i - 1);
            Node current = skyline.get(i);
            int shrink = previous.x + previous.width - current.x;

            if (shrink <= 0) {
                break;
            }

            current.x += shrink;
            current.width -= shrink;

            if (current.width <= 0) {
                skyline.remove(i--);
            } else {
                break;
            }
        }

        for (int i = 0; i < skyline.size() - 1; i++) {
            Node current = skyline.get(i);
            Node next = skyline.get(i + 1);

            if (current.y == next.y) {
                current.width += next.width;
                skyline.remove(i-- + 1);
            }
        }
    }

    private static void restoreRotations(Stitcher.Holder[] holders, boolean[] rotations) {
        for (int i = 0; i < holders.length; i++) {
            if (holders[i].isRotated() != rotations[i]) {
                holders[i].rotate();
            }
        }
    }

    private static int roundDimension(int value, boolean forcePowerOf2) {
        return forcePowerOf2 ? MathHelper.roundUpToPowerOfTwo(value) : value;
    }

    private static int grow(int value, int max, boolean forcePowerOf2) {
        if (value >= max) {
            return max + 1;
        }

        int next = forcePowerOf2 ? MathHelper.roundUpToPowerOfTwo(value + 1) : value * 2;
        return Math.min(Math.max(next, value + 1), max);
    }

    private static StitcherException unableToFit(Stitcher.Holder holder, int maxWidth, int maxHeight) {
        return new StitcherException(holder, "Unable to fit: " + holder.getAtlasSprite().getIconName()
            + " - size: " + holder.getAtlasSprite().getIconWidth() + "x" + holder.getAtlasSprite().getIconHeight()
            + " - max atlas: " + maxWidth + "x" + maxHeight);
    }

    public static final class Result {
        public final int width;
        public final int height;
        public final List<TextureAtlasSprite> sprites;

        private Result(int width, int height, List<TextureAtlasSprite> sprites) {
            this.width = width;
            this.height = height;
            this.sprites = sprites;
        }
    }

    private static final class Node {
        int x;
        int y;
        int width;

        private Node(int x, int y, int width) {
            this.x = x;
            this.y = y;
            this.width = width;
        }
    }

    private static final class Placement {
        final int index;
        final int x;
        final int y;
        final int width;
        final int bottom;
        final boolean rotated;

        private Placement(int index, int x, int y, int width, int bottom, boolean rotated) {
            this.index = index;
            this.x = x;
            this.y = y;
            this.width = width;
            this.bottom = bottom;
            this.rotated = rotated;
        }
    }
}
