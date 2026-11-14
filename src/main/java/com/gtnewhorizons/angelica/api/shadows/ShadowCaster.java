package com.gtnewhorizons.angelica.api.shadows;

/**
 * Draws extra geometry into the shaderpack shadow map, for things that should cast shadows but aren't terrain or
 * entities. RenderWorldLastEvent geometry never gets there, and TESRs only do when the pack shadows block entities.
 * Register with {@link ShadowCasters#register}.
 */
@FunctionalInterface
public interface ShadowCaster {

    /**
     * Called once per shadow pass for each stage, with the shadow modelview loaded and the block entity shadow program
     * bound. Positions are relative to the camera position passed in, so translate by worldX - cameraX etc. Put back
     * any GL state you change.
     *
     * @param stage {@link ShadowStage#SOLID} with the other solid shadows, {@link ShadowStage#TRANSLUCENT} after
     *              translucent terrain for things the pack should tint light through instead of blocking it
     */
    void renderShadow(ShadowStage stage, double cameraX, double cameraY, double cameraZ, float partialTicks);
}
