package com.gtnewhorizons.angelica.api.dynamiclights;

import com.gtnewhorizons.angelica.dynamiclights.DynamicLights;
import com.gtnewhorizons.angelica.dynamiclights.PositionalLightSource;

public final class DynamicLightsApi {

    private DynamicLightsApi() {}

    /** True when dynamic lights are on (the setting is on, and there's no shaderpack or shader force is on). */
    public static boolean isEnabled() {
        return DynamicLights.isEnabled();
    }

    /**
     * True when lights made with this {@link PositionalLight#setLitUnderShaderpacks} setting give light right now. With
     * true that's whenever dynamic lights are on, shaderpack or not.
     */
    public static boolean isEnabled(boolean litUnderShaderpacks) {
        return DynamicLights.isEnabled(litUnderShaderpacks);
    }

    /** A new light, off until it gets a luminance. */
    public static PositionalLight createLight() {
        return new PositionalLightSource();
    }

    /**
     * Calls the visitor for every dynamic light within radius blocks of the point that gives light right now, entities
     * included. Positional lights are passed as themselves, other lights as null. Under a shaderpack without shader
     * force that's only the lights lit under shaderpacks.
     */
    public static void forEachLightNear(double x, double y, double z, double radius, LightVisitor visitor) {
        if (!DynamicLights.isActive()) return;
        DynamicLights.get().forEachSourceNear(x, y, z, radius, source -> visitor.visit(
            source instanceof PositionalLight light ? light : null,
            source.angelica$getDynamicLightX(),
            source.angelica$getDynamicLightY(),
            source.angelica$getDynamicLightZ(),
            source.angelica$getLuminance()));
    }

    /** How bright a held torch reach dynamic light of this luminance is at this distance, 0 past 7.75 blocks. */
    public static double lightLevelAt(int luminance, double distance) {
        return DynamicLights.falloff(luminance, distance);
    }

    /**
     * How bright a dynamic light of this luminance and reach (see {@link PositionalLight#setReach}) is at this distance.
     * Other lights from {@link #forEachLightNear} come with null and light like {@link PositionalLight#TORCH_REACH}.
     */
    public static double lightLevelAt(int luminance, float reach, double distance) {
        return DynamicLights.falloff(luminance, reach, distance);
    }

    @FunctionalInterface
    public interface LightVisitor {

        void visit(PositionalLight light, double x, double y, double z, int luminance);
    }
}
