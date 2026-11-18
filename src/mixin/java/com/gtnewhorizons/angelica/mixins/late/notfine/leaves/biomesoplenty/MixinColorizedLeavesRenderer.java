package com.gtnewhorizons.angelica.mixins.late.notfine.leaves.biomesoplenty;

import biomesoplenty.client.render.blocks.ColorizedLeavesRenderer;
import com.llamalad7.mixinextras.sugar.Local;
import com.prupe.mcpatcher.mal.block.RenderBlocksUtils;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.init.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ColorizedLeavesRenderer.class, remap = false)
public abstract class MixinColorizedLeavesRenderer {

    @Redirect(
        method = "renderWorldBlock",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderBlocks;renderStandardBlock(Lnet/minecraft/block/Block;III)Z", remap = true))
    private boolean angelica$renderOverlayWithLeafCulling(RenderBlocks renderer, Block block, int x, int y, int z, @Local(argsOnly = true) Block leaf) {
        if (block != Blocks.stone) {
            return renderer.renderStandardBlock(block, x, y, z);
        }
        RenderBlocksUtils.setupColorMultiplier(block, renderer.blockAccess, x, y, z, renderer.hasOverrideBlockTexture(), 1f, 1f, 1f);
        if (Minecraft.isAmbientOcclusionEnabled() && leaf.getLightValue() == 0) {
            return renderer.partialRenderBounds
                ? renderer.renderStandardBlockWithAmbientOcclusionPartial(leaf, x, y, z, 1f, 1f, 1f)
                : renderer.renderStandardBlockWithAmbientOcclusion(leaf, x, y, z, 1f, 1f, 1f);
        }
        return renderer.renderStandardBlockWithColorMultiplier(leaf, x, y, z, 1f, 1f, 1f);
    }

}
