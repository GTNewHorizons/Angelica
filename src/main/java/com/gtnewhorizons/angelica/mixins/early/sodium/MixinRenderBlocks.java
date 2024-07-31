package com.gtnewhorizons.angelica.mixins.early.sodium;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderBlocks.class)
public abstract class MixinRenderBlocks {

    @Unique
    private boolean isRenderingByType = false;

    @Inject(method = "renderBlockByRenderType", at = @At("HEAD"))
    private void renderingByTypeEnable(CallbackInfoReturnable<Boolean> ci) {
        this.isRenderingByType = true;
    }

    @Inject(method = "renderBlockByRenderType", at = @At("TAIL"))
    private void renderingByTypeDisable(CallbackInfoReturnable<Boolean> ci) {
        this.isRenderingByType = false;
    }

    @Redirect(method = "renderStandardBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;isAmbientOcclusionEnabled()Z"))
    private boolean checkAOEnabled() {
        if ((this.isRenderingByType && Minecraft.isAmbientOcclusionEnabled()) || (AngelicaConfig.enableIris && BlockRenderingSettings.INSTANCE.shouldUseSeparateAo())) {
            return false; // Force sodium pipeline with Iris or for standard blocks rendered from renderBlockByRenderType
        }

        return Minecraft.isAmbientOcclusionEnabled();
    }
}
