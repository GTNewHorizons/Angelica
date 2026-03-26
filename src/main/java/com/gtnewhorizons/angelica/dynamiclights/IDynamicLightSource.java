package com.gtnewhorizons.angelica.dynamiclights;

import com.gtnewhorizons.angelica.api.BlockLightProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public interface IDynamicLightSource {
    /**
     * Returns the dynamic light source X coordinate.
     *
     * @return the X coordinate
     */
    double angelica$getDynamicLightX();

    /**
     * Returns the dynamic light source Y coordinate.
     *
     * @return the Y coordinate
     */
    double angelica$getDynamicLightY();

    /**
     * Returns the dynamic light source Z coordinate.
     *
     * @return the Z coordinate
     */
    double angelica$getDynamicLightZ();

    /**
     * Returns the dynamic light source world renderer.
     *
     * @return the world renderer instance
     */
    default IDynamicLightWorldRenderer angelica$getDynamicLightWorld() {
        return DynamicLights.getActiveRenderer();
    }

    /**
     * Returns whether the dynamic light is enabled or not.
     *
     * @return {@code true} if the dynamic light is enabled, else {@code false}
     */
    default boolean angelica$isDynamicLightEnabled() {
        return DynamicLights.isEnabled() && DynamicLights.get().containsLightSource(this);
    }

    /**
     * Sets whether the dynamic light is enabled or not.
     * <p>
     * Note: please do not call this function in your mod or you will break things.
     *
     * @param enabled {@code true} if the dynamic light is enabled, else {@code false}
     */
    @ApiStatus.Internal
    default void angelica$setDynamicLightEnabled(boolean enabled) {
        this.angelica$resetDynamicLight();
        if (enabled)
            DynamicLights.get().addLightSource(this);
        else
            DynamicLights.get().removeLightSource(this);
    }

    void angelica$resetDynamicLight();

    /**
     * Returns the luminance of the light source.
     * The maximum is 15, below 1 values are ignored.
     *
     * @return the luminance of the light source
     */
    int angelica$getLuminance();

    /**
     * Executed at each tick.
     */
    void angelica$dynamicLightTick();
    default void angelica$updateLights() {}

    boolean angelica$updateDynamicLight(@NotNull IDynamicLightWorldRenderer renderer);

    void angelica$scheduleTrackedChunksRebuild(@NotNull IDynamicLightWorldRenderer renderer);

    /**
     * Returns the RGB luminance of the light source. Each channel 0-15, packed as {@code (r << 8) | (g << 4) | b}.
     * Default implementation returns white light at the scalar luminance level.
     */
    default int angelica$getLuminanceRGB() {
        final int l = angelica$getLuminance();
        return BlockLightProvider.packRGB(l, l, l);
    }
}
