package com.gtnewhorizons.angelica.glsm.hooks;

import com.gtnewhorizons.angelica.glsm.GLContextState;
import com.gtnewhorizons.angelica.glsm.GLStateManager;

public final class GLSMConfig {

    public static int packBrightness(float x, float y) {
        return ((int) y << 16) | ((int) x & 0xFFFF);
    }

    public static int packedLastBrightness() {
        final GLContextState glCtx = GLStateManager.ctx();
        return packBrightness(glCtx.lastBrightnessX, glCtx.lastBrightnessY);
    }

    public static boolean hudCacheOverride;

    public static boolean extendedAttribsExpected;
    public static volatile boolean expandVertexFormats;
    public static int workerThreadCount;

    private GLSMConfig() {}
}
