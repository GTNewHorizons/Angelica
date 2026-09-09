package com.gtnewhorizons.angelica.sdlgpu.resource;

public final class CopyRectClip {

    public static final long EMPTY = 0L;

    private static final long FIELD_MASK = 0xFFFFL;

    private CopyRectClip() {}

    public static long clipCopyRect(int srcX, int srcY, int dstX, int dstY, int width, int height,
        int srcWidth, int srcHeight, int dstWidth, int dstHeight) {
        int sx = srcX;
        int sy = srcY;
        int dx = dstX;
        int dy = dstY;
        int w = width;
        int h = height;
        if (sx < 0) {
            w += sx;
            dx -= sx;
            sx = 0;
        }
        if (sy < 0) {
            h += sy;
            dy -= sy;
            sy = 0;
        }
        if (dx < 0) {
            w += dx;
            sx -= dx;
            dx = 0;
        }
        if (dy < 0) {
            h += dy;
            sy -= dy;
            dy = 0;
        }
        if (srcWidth > 0 && sx + w > srcWidth) w = srcWidth - sx;
        if (srcHeight > 0 && sy + h > srcHeight) h = srcHeight - sy;
        if (dstWidth > 0 && dx + w > dstWidth) w = dstWidth - dx;
        if (dstHeight > 0 && dy + h > dstHeight) h = dstHeight - dy;
        if (w <= 0 || h <= 0) return EMPTY;
        if ((sx | sy | w | h) > FIELD_MASK) return EMPTY;
        return (sx & FIELD_MASK) | ((sy & FIELD_MASK) << 16) | ((w & FIELD_MASK) << 32) | ((h & FIELD_MASK) << 48);
    }

    public static int srcX(long clip) {
        return (int) (clip & FIELD_MASK);
    }

    public static int srcY(long clip) {
        return (int) ((clip >>> 16) & FIELD_MASK);
    }

    public static int width(long clip) {
        return (int) ((clip >>> 32) & FIELD_MASK);
    }

    public static int height(long clip) {
        return (int) ((clip >>> 48) & FIELD_MASK);
    }

    public static int dstX(long clip, int origSrcX, int origDstX) {
        return origDstX + (srcX(clip) - origSrcX);
    }

    public static int dstY(long clip, int origSrcY, int origDstY) {
        return origDstY + (srcY(clip) - origSrcY);
    }
}
