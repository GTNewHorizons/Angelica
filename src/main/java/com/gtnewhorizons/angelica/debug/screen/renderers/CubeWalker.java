package com.gtnewhorizons.angelica.debug.screen.renderers;

final class CubeWalker {

    interface Sink {

        boolean cell(int x, int y, int z);
    }

    private static final int MIN_Y = 0;
    private static final int MAX_Y = 255;

    private CubeWalker() {}

    static void walk(int cx, int cy, int cz, int radius, Sink sink) {
        for (int r = 0; r <= radius; r++) {
            final int low = cy - r;
            final int high = cy + r;
            final boolean lowIn = low >= MIN_Y && low <= MAX_Y;
            final boolean highIn = high >= MIN_Y && high <= MAX_Y;
            final int from = Math.max(low, MIN_Y);
            final int to = Math.min(high, MAX_Y);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx == -r || dx == r || dz == -r || dz == r) {
                        for (int y = from; y <= to; y++) {
                            if (!sink.cell(cx + dx, y, cz + dz)) return;
                        }
                    } else {
                        if (lowIn && !sink.cell(cx + dx, low, cz + dz)) return;
                        if (highIn && !sink.cell(cx + dx, high, cz + dz)) return;
                    }
                }
            }
        }
    }
}
