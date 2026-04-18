package com.gtnewhorizons.angelica.api;

/**
 * Computes an RGB tint from block light and sky light channel values. The lightmap handles brightness; the tint only controls color.
 */
@FunctionalInterface
public interface TintComputer {

    /**
     * Blend block light RGB and sky light RGB into a tint color.
     *
     * @param br block light red (0-15)
     * @param bg block light green (0-15)
     * @param bb block light blue (0-15)
     * @param sr sky light red (0-15, already adjusted for sky subtraction)
     * @param sg sky light green (0-15)
     * @param sb sky light blue (0-15)
     * @param out float[3] output: R, G, B tint multipliers (typically 0-1)
     */
    void computeTint(float br, float bg, float bb, float sr, float sg, float sb, float[] out);
}
