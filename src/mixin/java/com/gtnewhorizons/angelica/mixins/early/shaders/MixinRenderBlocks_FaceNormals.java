package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.rendering.FallingBlockRendering;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Emits a per-face normal while a falling block is rendering, so shader packs can light it.
 */
@Mixin(RenderBlocks.class)
public class MixinRenderBlocks_FaceNormals {

    @WrapOperation(
        method = {"renderBlockSandFalling", "renderStandardBlockWithColorMultiplier",
                  "renderStandardBlockWithAmbientOcclusion", "renderStandardBlockWithAmbientOcclusionPartial"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderBlocks;renderFaceYNeg(Lnet/minecraft/block/Block;DDDLnet/minecraft/util/IIcon;)V")
    )
    private void angelica$normalYNeg(RenderBlocks instance, Block block, double x, double y, double z, IIcon icon, Operation<Void> original) {
        if (FallingBlockRendering.isActive()) Tessellator.instance.setNormal(0.0F, -1.0F, 0.0F);
        original.call(instance, block, x, y, z, icon);
    }

    @WrapOperation(
        method = {"renderBlockSandFalling", "renderStandardBlockWithColorMultiplier",
                  "renderStandardBlockWithAmbientOcclusion", "renderStandardBlockWithAmbientOcclusionPartial"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderBlocks;renderFaceYPos(Lnet/minecraft/block/Block;DDDLnet/minecraft/util/IIcon;)V")
    )
    private void angelica$normalYPos(RenderBlocks instance, Block block, double x, double y, double z, IIcon icon, Operation<Void> original) {
        if (FallingBlockRendering.isActive()) Tessellator.instance.setNormal(0.0F, 1.0F, 0.0F);
        original.call(instance, block, x, y, z, icon);
    }

    @WrapOperation(
        method = {"renderBlockSandFalling", "renderStandardBlockWithColorMultiplier",
                  "renderStandardBlockWithAmbientOcclusion", "renderStandardBlockWithAmbientOcclusionPartial"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderBlocks;renderFaceZNeg(Lnet/minecraft/block/Block;DDDLnet/minecraft/util/IIcon;)V")
    )
    private void angelica$normalZNeg(RenderBlocks instance, Block block, double x, double y, double z, IIcon icon, Operation<Void> original) {
        if (FallingBlockRendering.isActive()) Tessellator.instance.setNormal(0.0F, 0.0F, -1.0F);
        original.call(instance, block, x, y, z, icon);
    }

    @WrapOperation(
        method = {"renderBlockSandFalling", "renderStandardBlockWithColorMultiplier",
                  "renderStandardBlockWithAmbientOcclusion", "renderStandardBlockWithAmbientOcclusionPartial"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderBlocks;renderFaceZPos(Lnet/minecraft/block/Block;DDDLnet/minecraft/util/IIcon;)V")
    )
    private void angelica$normalZPos(RenderBlocks instance, Block block, double x, double y, double z, IIcon icon, Operation<Void> original) {
        if (FallingBlockRendering.isActive()) Tessellator.instance.setNormal(0.0F, 0.0F, 1.0F);
        original.call(instance, block, x, y, z, icon);
    }

    @WrapOperation(
        method = {"renderBlockSandFalling", "renderStandardBlockWithColorMultiplier",
                  "renderStandardBlockWithAmbientOcclusion", "renderStandardBlockWithAmbientOcclusionPartial"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderBlocks;renderFaceXNeg(Lnet/minecraft/block/Block;DDDLnet/minecraft/util/IIcon;)V")
    )
    private void angelica$normalXNeg(RenderBlocks instance, Block block, double x, double y, double z, IIcon icon, Operation<Void> original) {
        if (FallingBlockRendering.isActive()) Tessellator.instance.setNormal(-1.0F, 0.0F, 0.0F);
        original.call(instance, block, x, y, z, icon);
    }

    @WrapOperation(
        method = {"renderBlockSandFalling", "renderStandardBlockWithColorMultiplier",
                  "renderStandardBlockWithAmbientOcclusion", "renderStandardBlockWithAmbientOcclusionPartial"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderBlocks;renderFaceXPos(Lnet/minecraft/block/Block;DDDLnet/minecraft/util/IIcon;)V")
    )
    private void angelica$normalXPos(RenderBlocks instance, Block block, double x, double y, double z, IIcon icon, Operation<Void> original) {
        if (FallingBlockRendering.isActive()) Tessellator.instance.setNormal(1.0F, 0.0F, 0.0F);
        original.call(instance, block, x, y, z, icon);
    }
}
