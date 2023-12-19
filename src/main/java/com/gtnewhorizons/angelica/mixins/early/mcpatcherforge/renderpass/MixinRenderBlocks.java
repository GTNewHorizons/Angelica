package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.renderpass;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.world.IBlockAccess;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.prupe.mcpatcher.renderpass.RenderPass;

/*
 * For readability changes to the biggest methods get their own classes
 */
@Mixin(RenderBlocks.class)
public abstract class MixinRenderBlocks {

    @Shadow
    public IBlockAccess blockAccess;

    @Redirect(
        method = { "renderBlockBed(Lnet/minecraft/block/Block;III)Z",
            "renderStandardBlockWithAmbientOcclusion(Lnet/minecraft/block/Block;IIIFFF)Z",
            "renderStandardBlockWithColorMultiplier(Lnet/minecraft/block/Block;IIIFFF)Z",
            "renderStandardBlockWithAmbientOcclusionPartial(Lnet/minecraft/block/Block;IIIFFF)Z",
            "renderBlockCactusImpl(Lnet/minecraft/block/Block;IIIFFF)Z",
            "renderBlockLiquid(Lnet/minecraft/block/Block;III)Z" },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/Block;shouldSideBeRendered(Lnet/minecraft/world/IBlockAccess;IIII)Z"))
    private boolean redirectShouldSideBeRendered(Block block, IBlockAccess worldIn, int x, int y, int z, int side) {
        return RenderPass.shouldSideBeRendered(block, worldIn, x, y, z, side);
    }


}
