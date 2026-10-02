package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.rendering.BlockMaterialAttribute;
import com.gtnewhorizons.angelica.rendering.FallingBlockMetaAccess;
import com.gtnewhorizons.angelica.rendering.FallingBlockRendering;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderFallingBlock;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Renders falling blocks through terrain.
 */
@Mixin(RenderFallingBlock.class)
public class MixinRenderFallingBlock {

    @Surround(id = "beginFallingBlock", method = "doRender(Lnet/minecraft/entity/item/EntityFallingBlock;DDDFF)V")
    private void angelica$beginFallingBlock(EntityFallingBlock entity, double x, double y, double z, float entityYaw, float partialTicks) {
        FallingBlockRendering.active = true;
        CapturedRenderingState.INSTANCE.pushCurrentBlockEntity();

        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(0);
        GbufferPrograms.pushOverridePhase(WorldRenderingPhase.TERRAIN_SOLID);
        BlockMaterialAttribute.set(entity.func_145805_f(), entity.field_145814_a);
    }

    @Surround.Finally("beginFallingBlock")
    private void angelica$endFallingBlock() {
        FallingBlockRendering.active = false;

        BlockMaterialAttribute.reset();
        GbufferPrograms.popOverridePhase();
        CapturedRenderingState.INSTANCE.popCurrentBlockEntity();
    }

    @Surround(
        id = "aoFallingBlock",
        method = "doRender(Lnet/minecraft/entity/item/EntityFallingBlock;DDDFF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderBlocks;renderBlockSandFalling(Lnet/minecraft/block/Block;Lnet/minecraft/world/World;IIII)V")
    )
    private void angelica$aoFallingBlock() {
        @Surround.Skip
        boolean skip = FallingBlockRendering.isActive();
    }

    @Surround.Skipped("aoFallingBlock")
    private void angelica$renderFallingBlockAo(RenderBlocks renderBlocks, Block block, World world, int x, int y, int z, int metadata) {
        final IBlockAccess prevAccess = renderBlocks.blockAccess;
        final boolean prevRenderAllFaces = renderBlocks.renderAllFaces;
        final FallingBlockMetaAccess access = FallingBlockRendering.metaAccess(world, x, y, z, metadata);
        renderBlocks.blockAccess = access;
        renderBlocks.renderAllFaces = true;

        final Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setTranslation(-x - 0.5D, -y - 0.5D, -z - 0.5D);

        try {
            renderBlocks.renderStandardBlock(block, x, y, z);
        } finally {
            tessellator.setTranslation(0.0D, 0.0D, 0.0D);
            tessellator.draw();
            renderBlocks.renderAllFaces = prevRenderAllFaces;
            renderBlocks.blockAccess = prevAccess;
            access.clear();
        }
    }
}
