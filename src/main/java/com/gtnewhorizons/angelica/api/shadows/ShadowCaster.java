package com.gtnewhorizons.angelica.api.shadows;

/**
 * Draws extra geometry into the shaderpack shadow map. {@code RenderWorldLastEvent} geometry never reaches it, and TESRs
 * only do when the pack shadows block entities. Register with {@link ShadowCasters#register}.
 */
@FunctionalInterface
public interface ShadowCaster {

    /**
     * Called once per shadow pass per stage, with the shadow modelview loaded and the block entity shadow program bound.
     * Positions are camera relative. Restore any GL state you change.
     *
     * @param stage {@link ShadowStage#SOLID} with the other solid shadows, {@link ShadowStage#TRANSLUCENT} after
     *              translucent terrain for geometry that should tint light rather than block it
     */
    void renderShadow(ShadowStage stage, double cameraX, double cameraY, double cameraZ, float partialTicks);
}
