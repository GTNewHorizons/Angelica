package com.gtnewhorizons.angelica.mixins.late.client.malisisdoors;

import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizons.angelica.rendering.BlockMaterialAttribute;
import com.gtnewhorizons.angelica.rendering.StateAwareTessellator;
import net.coderbot.iris.layer.GbufferPrograms;
import net.irisshaders.iris.api.v0.IrisApi;
import net.malisis.core.renderer.MalisisRenderer;
import net.malisis.core.renderer.RenderParameters;
import net.malisis.core.renderer.RenderType;
import net.malisis.core.renderer.element.Shape;
import net.malisis.core.renderer.element.Face;
import net.malisis.core.renderer.element.Vertex;
import net.malisis.doors.door.renderer.BigDoorRenderer;
import net.malisis.doors.renderer.MixedBlockRenderer;
import net.minecraft.block.Block;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;

/** Applies copied blocks' shader material IDs to Mixed Block portions and large-door frames. */
@Mixin(value = {MixedBlockRenderer.class, BigDoorRenderer.class}, remap = false)
public abstract class MixinMixedBlockRenderer extends MalisisRenderer {

    @Override
    public void drawShape(Shape shape, RenderParameters parameters) {
        final StateAwareTessellator tessellator = (StateAwareTessellator) TessellatorManager.get();
        if (renderType != RenderType.ISBRH_WORLD
            || !IrisApi.getInstance().isShaderPackInUse() || overrideTexture != null) {
            super.drawShape(shape, parameters);
            return;
        }

        if (!tessellator.angelica$isCeleritasMeshing()) {
            final Block enclosingBlock = world.getBlock(x, y, z);
            final int enclosingMetadata = world.getBlockMetadata(x, y, z);
            boolean fading = false;
            if (parameters != null && parameters.usePerVertexAlpha.get()) {
                for (Face face : shape.getFaces()) {
                    for (Vertex vertex : face.getVertexes()) fading |= vertex.getAlpha() != 255;
                }
            }
            next(GL11.GL_QUADS);
            GbufferPrograms.pushOverridePhase(BlockMaterialAttribute.renderingPhase(block, fading));
            BlockMaterialAttribute.set(block, blockMetadata);
            try {
                super.drawShape(shape, parameters);
            } finally {
                try {
                    next(GL11.GL_QUADS);
                } finally {
                    BlockMaterialAttribute.set(enclosingBlock, enclosingMetadata);
                    GbufferPrograms.popOverridePhase();
                }
            }
            return;
        }

        final short previousId = tessellator.angelica$getShaderOverrideBlockId();
        final int materialId = BlockMaterialAttribute.blockMaterialId(block, blockMetadata);
        tessellator.angelica$setShaderOverrideBlockId(materialId == -1
            ? StateAwareTessellator.UNMAPPED_SHADER_BLOCK_ID : (short) materialId);
        try {
            super.drawShape(shape, parameters);
        } finally {
            tessellator.angelica$setShaderOverrideBlockId(previousId);
        }
    }
}
