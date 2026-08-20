package com.gtnewhorizons.angelica.glsm.hooks;

public final class GLSMConfig {

    // Lightmap (replaces OpenGlHelper.lastBrightnessX/Y)
    public static float lastBrightnessX;
    public static float lastBrightnessY;

    public static int packBrightness(float x, float y) {
        return ((int) y << 16) | ((int) x & 0xFFFF);
    }

    public static int packedLastBrightness() {
        return packBrightness(lastBrightnessX, lastBrightnessY);
    }

    public static boolean hudCacheOverride;

    public static boolean extendedAttribsExpected;
    public static volatile boolean expandVertexFormats;

    private GLSMConfig() {}
}
