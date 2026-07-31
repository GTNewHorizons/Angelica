package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.rendering.FallingBlockRendering;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.RenderBlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Replace vanilla's baked per-face directional shading while a falling block is rendering.
 */
@Mixin(RenderBlocks.class)
public class MixinRenderBlocks_FallingShading {

    @WrapOperation(
        method = "renderStandardBlock",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;isAmbientOcclusionEnabled()Z")
    )
    private boolean angelica$forceNonAoForFallingBlocks(Operation<Boolean> original) {
        return !FallingBlockRendering.active && original.call();
    }

    @ModifyConstant(method = "renderStandardBlockWithColorMultiplier", constant = @Constant(floatValue = 0.5F))
    private float angelica$flattenBottomShading(float original) {
        return FallingBlockRendering.skipDirectionalShading() ? 1.0F : original;
    }

    @ModifyConstant(method = "renderStandardBlockWithColorMultiplier", constant = @Constant(floatValue = 0.8F))
    private float angelica$flattenNorthSouthShading(float original) {
        return FallingBlockRendering.skipDirectionalShading() ? 1.0F : original;
    }

    @ModifyConstant(method = "renderStandardBlockWithColorMultiplier", constant = @Constant(floatValue = 0.6F))
    private float angelica$flattenEastWestShading(float original) {
        return FallingBlockRendering.skipDirectionalShading() ? 1.0F : original;
    }

    @ModifyConstant(method = "renderBlockSandFalling", constant = @Constant(floatValue = 0.5F))
    private float angelica$flattenSandBottomShading(float original) {
        return FallingBlockRendering.skipDirectionalShading() ? 1.0F : original;
    }

    @ModifyConstant(method = "renderBlockSandFalling", constant = @Constant(floatValue = 0.8F))
    private float angelica$flattenSandNorthSouthShading(float original) {
        return FallingBlockRendering.skipDirectionalShading() ? 1.0F : original;
    }

    @ModifyConstant(method = "renderBlockSandFalling", constant = @Constant(floatValue = 0.6F))
    private float angelica$flattenSandEastWestShading(float original) {
        return FallingBlockRendering.skipDirectionalShading() ? 1.0F : original;
    }
}
