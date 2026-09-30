package com.gtnewhorizons.angelica.mixins.late.client.malisisdoors;

import com.gtnewhorizons.angelica.rendering.BlockMaterialAttribute;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.irisshaders.iris.api.v0.IrisApi;
import net.malisis.core.renderer.RenderParameters;
import net.malisis.core.renderer.RenderType;
import net.malisis.core.renderer.element.Face;
import net.malisis.core.renderer.element.Shape;
import net.malisis.doors.door.renderer.CustomDoorRenderer;
import net.malisis.doors.door.renderer.DoorRenderer;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.Tessellator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/** Gives the frame and each panel the shader material of the block supplying its texture. */
@Mixin(value = CustomDoorRenderer.class, remap = false)
public abstract class MixinCustomDoorRenderer extends DoorRenderer {

    @Shadow private Block frameBlock;
    @Shadow private Block topMaterialBlock;
    @Shadow private Block bottomMaterialBlock;
    @Shadow private int frameMetadata;
    @Shadow private int topMaterialMetadata;
    @Shadow private int bottomMaterialMetadata;
    @Shadow @Final private Shape top;

    @Unique private boolean angelica$drawingMaterials;
    @Unique private Block angelica$currentMaterial;
    @Unique private int angelica$currentMetadata;
    @Unique private WorldRenderingPhase angelica$currentPhase;
    @Unique private boolean angelica$phasePushed;

    @Override
    public void drawShape(Shape shape, RenderParameters parameters) {
        if (renderType != RenderType.TESR_WORLD || destroyBlockProgress != null || overrideTexture != null
            || !IrisApi.getInstance().isShaderPackInUse()) {
            super.drawShape(shape, parameters);
            return;
        }

        angelica$drawingMaterials = true;
        angelica$currentMaterial = block;
        angelica$currentMetadata = blockMetadata;
        angelica$currentPhase = GbufferPrograms.getCurrentPhase();
        angelica$phasePushed = false;
        try {
            super.drawShape(shape, parameters);
        } finally {
            try {
                next();
            } finally {
                BlockMaterialAttribute.set(block, blockMetadata);
                if (angelica$phasePushed) GbufferPrograms.popOverridePhase();
                angelica$phasePushed = false;
                angelica$drawingMaterials = false;
                angelica$currentMaterial = null;
            }
        }
    }

    @Override
    protected void drawFace(Face face, RenderParameters parameters, Tessellator tessellator) {
        if (angelica$drawingMaterials && face != null) {
            Block material = block;
            int metadata = blockMetadata;
            if ("frame".equals(face.name())) {
                material = frameBlock;
                metadata = frameMetadata;
            } else if ("material".equals(face.name())) {
                final boolean upperPanel = shape == top;
                material = upperPanel ? topMaterialBlock : bottomMaterialBlock;
                metadata = upperPanel ? topMaterialMetadata : bottomMaterialMetadata;
            }

            final WorldRenderingPhase phase = BlockMaterialAttribute.renderingPhase(material, false);
            if (material != angelica$currentMaterial || metadata != angelica$currentMetadata || phase != angelica$currentPhase) {
                next();
                if (phase != angelica$currentPhase) {
                    if (angelica$phasePushed) GbufferPrograms.popOverridePhase();
                    GbufferPrograms.pushOverridePhase(phase);
                    angelica$phasePushed = true;
                    angelica$currentPhase = phase;
                }
                BlockMaterialAttribute.set(material, metadata);
                angelica$currentMaterial = material;
                angelica$currentMetadata = metadata;
            }
        }
        super.drawFace(face, parameters, tessellator);
    }
}
