package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.rendering.FallingBlockMetaAccess;
import com.gtnewhorizons.angelica.rendering.FallingBlockRendering;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
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

    @WrapMethod(method = "doRender(Lnet/minecraft/entity/item/EntityFallingBlock;DDDFF)V")
    private void angelica$fallingBlockTerrainState(EntityFallingBlock entity, double x, double y, double z, float entityYaw, float partialTicks, Operation<Void> original) {
        final int prevBlockEntityId = CapturedRenderingState.INSTANCE.getCurrentRenderedBlockEntity();
        FallingBlockRendering.active = true;

        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(0);
        GbufferPrograms.setOverridePhase(WorldRenderingPhase.TERRAIN_SOLID);
        FallingBlockRendering.setEntityAttribute(entity.func_145805_f(), entity.field_145814_a);

        try {
            original.call(entity, x, y, z, entityYaw, partialTicks);
        } finally {
            FallingBlockRendering.active = false;

            FallingBlockRendering.resetEntityAttribute();
            GbufferPrograms.setOverridePhase(null);
            CapturedRenderingState.INSTANCE.setCurrentBlockEntity(prevBlockEntityId);
        }
    }

    @WrapOperation(
        method = "doRender(Lnet/minecraft/entity/item/EntityFallingBlock;DDDFF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderBlocks;renderBlockSandFalling(Lnet/minecraft/block/Block;Lnet/minecraft/world/World;IIII)V")
    )
    private void angelica$aoFallingBlock(RenderBlocks renderBlocks, Block block, World world, int x, int y, int z, int metadata, Operation<Void> original) {
        if (!FallingBlockRendering.isActive()) {
            original.call(renderBlocks, block, world, x, y, z, metadata);
            return;
        }

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
