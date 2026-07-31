package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.rendering.FallingBlockRendering;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Emits a per-face normal while a falling block is rendering, so shader packs can light it.
 */
@Mixin(RenderBlocks.class)
public class MixinRenderBlocks_FaceNormals {

    @Inject(method = "renderFaceYNeg", at = @At("HEAD"))
    private void angelica$normalYNeg(Block block, double x, double y, double z, IIcon icon, CallbackInfo ci) {
        if (FallingBlockRendering.active) Tessellator.instance.setNormal(0.0F, -1.0F, 0.0F);
    }

    @Inject(method = "renderFaceYPos", at = @At("HEAD"))
    private void angelica$normalYPos(Block block, double x, double y, double z, IIcon icon, CallbackInfo ci) {
        if (FallingBlockRendering.active) Tessellator.instance.setNormal(0.0F, 1.0F, 0.0F);
    }

    @Inject(method = "renderFaceZNeg", at = @At("HEAD"))
    private void angelica$normalZNeg(Block block, double x, double y, double z, IIcon icon, CallbackInfo ci) {
        if (FallingBlockRendering.active) Tessellator.instance.setNormal(0.0F, 0.0F, -1.0F);
    }

    @Inject(method = "renderFaceZPos", at = @At("HEAD"))
    private void angelica$normalZPos(Block block, double x, double y, double z, IIcon icon, CallbackInfo ci) {
        if (FallingBlockRendering.active) Tessellator.instance.setNormal(0.0F, 0.0F, 1.0F);
    }

    @Inject(method = "renderFaceXNeg", at = @At("HEAD"))
    private void angelica$normalXNeg(Block block, double x, double y, double z, IIcon icon, CallbackInfo ci) {
        if (FallingBlockRendering.active) Tessellator.instance.setNormal(-1.0F, 0.0F, 0.0F);
    }

    @Inject(method = "renderFaceXPos", at = @At("HEAD"))
    private void angelica$normalXPos(Block block, double x, double y, double z, IIcon icon, CallbackInfo ci) {
        if (FallingBlockRendering.active) Tessellator.instance.setNormal(1.0F, 0.0F, 0.0F);
    }
}
