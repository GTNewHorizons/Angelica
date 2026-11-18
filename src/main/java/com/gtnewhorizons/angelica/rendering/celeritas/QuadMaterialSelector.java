package com.gtnewhorizons.angelica.rendering.celeritas;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.sprite.SpriteTransparencyLevel;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;

class QuadMaterialSelector {
    private Material material;
    private boolean canDemote;
    private int facesAtBaseMaterial;
    private TextureAtlasSprite lastSprite;
    private Material lastMaterial;

    void reset(Material material, boolean isShaderPackOverride, boolean useRenderPassOptimization) {
        this.material = material;
        // Don't apply transparency-based optimization when shader pack explicitly overrides the material
        this.canDemote = useRenderPassOptimization && !isShaderPackOverride;
        facesAtBaseMaterial = 0;
        lastMaterial = null;
    }

    Material select(ModelQuadFacing facing, TextureAtlasSprite sprite, boolean hasVertexTransparency) {
        final int faceBit = 1 << facing.ordinal();
        final Material correctMaterial;
        if ((facesAtBaseMaterial & faceBit) != 0 || (material == AngelicaRenderPassConfiguration.TRANSLUCENT_MATERIAL && hasVertexTransparency)) {
            correctMaterial = material;
        } else if (lastMaterial != null && sprite == lastSprite) {
            correctMaterial = lastMaterial;
        } else {
            correctMaterial = demote(sprite);
            lastSprite = sprite;
            lastMaterial = correctMaterial;
        }
        if (correctMaterial == material) {
            facesAtBaseMaterial |= faceBit;
        }
        return correctMaterial;
    }

    private Material demote(TextureAtlasSprite sprite) {
        if (canDemote && sprite != null && sprite.getClass() == TextureAtlasSprite.class && !sprite.hasAnimationMetadata()) {
            final var transparencyLevel = transparencyLevel(sprite);
            if (transparencyLevel == SpriteTransparencyLevel.OPAQUE && material == AngelicaRenderPassConfiguration.CUTOUT_MIPPED_MATERIAL) {
                return AngelicaRenderPassConfiguration.SOLID_MATERIAL;
            } else if (material == AngelicaRenderPassConfiguration.TRANSLUCENT_MATERIAL && transparencyLevel != SpriteTransparencyLevel.TRANSLUCENT) {
                return AngelicaRenderPassConfiguration.CUTOUT_MIPPED_MATERIAL;
            }
        }

        return material;
    }

    SpriteTransparencyLevel transparencyLevel(TextureAtlasSprite sprite) {
        return ((SpriteExtension) sprite).celeritas$getTransparencyLevel();
    }
}
