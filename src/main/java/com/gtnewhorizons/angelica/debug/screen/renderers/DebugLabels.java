package com.gtnewhorizons.angelica.debug.screen.renderers;

import java.util.Arrays;

final class DebugLabels {

    private static final int INITIAL = 64;
    private static final float[] NO_FLOATS = new float[0];
    private static final int[] NO_INTS = new int[0];

    float[] pos = NO_FLOATS;
    int[] value = NO_INTS;
    int[] color = NO_INTS;
    int count;

    void add(float x, float y, float z, int v, int argb) {
        final int n = count;
        if (n == value.length) {
            final int grown = Math.max(INITIAL, n * 2);
            pos = Arrays.copyOf(pos, grown * 3);
            value = Arrays.copyOf(value, grown);
            color = Arrays.copyOf(color, grown);
        }
        pos[n * 3] = x;
        pos[n * 3 + 1] = y;
        pos[n * 3 + 2] = z;
        value[n] = v;
        color[n] = argb;
        count = n + 1;
    }

    void copyFrom(DebugLabels from) {
        final int n = from.count;
        if (n > value.length) {
            pos = new float[from.pos.length];
            value = new int[from.value.length];
            color = new int[from.color.length];
        }
        System.arraycopy(from.pos, 0, pos, 0, n * 3);
        System.arraycopy(from.value, 0, value, 0, n);
        System.arraycopy(from.color, 0, color, 0, n);
        count = n;
    }

    void free() {
        pos = NO_FLOATS;
        value = NO_INTS;
        color = NO_INTS;
        count = 0;
    }
}
