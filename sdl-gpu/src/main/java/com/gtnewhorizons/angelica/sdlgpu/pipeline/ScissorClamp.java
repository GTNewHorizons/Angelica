package com.gtnewhorizons.angelica.sdlgpu.pipeline;

public final class ScissorClamp {

    public static final int X = 0;
    public static final int Y = 1;
    public static final int W = 2;
    public static final int H = 3;

    private ScissorClamp() {}

    public static boolean clamp(int x, int y, int w, int h, int targetW, int targetH, int[] out) {
        if (targetW <= 0 || targetH <= 0) {
            out[X] = x;
            out[Y] = y;
            out[W] = w;
            out[H] = h;
            return w > 0 && h > 0;
        }
        final int x0 = Math.max(x, 0);
        final int y0 = Math.max(y, 0);
        final int x1 = (int) Math.min((long) x + w, targetW);
        final int y1 = (int) Math.min((long) y + h, targetH);
        out[X] = x0;
        out[Y] = y0;
        out[W] = Math.max(x1 - x0, 0);
        out[H] = Math.max(y1 - y0, 0);
        return out[W] > 0 && out[H] > 0;
    }
}
