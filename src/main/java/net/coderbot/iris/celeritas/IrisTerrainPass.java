package net.coderbot.iris.celeritas;

import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;

public enum IrisTerrainPass {
    SHADOW("shadow"),
    SHADOW_CUTOUT("shadow"),
    GBUFFER_SOLID("gbuffers_terrain"),
    GBUFFER_CUTOUT("gbuffers_terrain_cutout"),
    GBUFFER_TRANSLUCENT("gbuffers_water");

    public static final IrisTerrainPass[] VALUES = values();

    private final String name;

    IrisTerrainPass(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public boolean isShadow() {
        return this == SHADOW || this == SHADOW_CUTOUT;
    }

    public TerrainRenderPass toTerrainPass(RenderPassConfiguration<?> config) {
        return switch (this) {
            case SHADOW, GBUFFER_SOLID -> config.defaultSolidMaterial().pass;
            case SHADOW_CUTOUT, GBUFFER_CUTOUT -> config.defaultCutoutMippedMaterial().pass;
            case GBUFFER_TRANSLUCENT -> config.defaultTranslucentMaterial().pass;
        };
    }

    public static IrisTerrainPass fromTerrainPass(TerrainRenderPass pass, boolean isShadow) {
        if (isShadow) {
            return pass.supportsFragmentDiscard() ? SHADOW_CUTOUT : SHADOW;
        } else if (pass.supportsFragmentDiscard()) {
            return GBUFFER_CUTOUT;
        } else if (pass.isReverseOrder()) {
            return GBUFFER_TRANSLUCENT;
        } else {
            return GBUFFER_SOLID;
        }
    }
}
