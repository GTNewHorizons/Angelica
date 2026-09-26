package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.StateSet;

import java.util.Arrays;

public final class TesrBlendScope {

    private TesrBlendScope() {}

    private static int[] depths = new int[4];
    private static int depth;

    public static void enter() {
        if (depth == depths.length) {
            depths = Arrays.copyOf(depths, depth * 2);
        }
        depths[depth++] = GLStateManager.pushState(StateSet.BLEND);
    }

    public static void exit() {
        if (depth == 0) return;
        GLStateManager.popStateTo(depths[--depth]);
    }

    public static void reset() {
        depth = 0;
    }
}
