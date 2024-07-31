package com.gtnewhorizons.angelica.mixins.early.sodium;

import net.minecraft.client.renderer.RenderBlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RenderBlocks.class)
public abstract class MixinRenderBlocks {
    @Redirect(method = "renderStandardBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;isAmbientOcclusionEnabled()Z"))
    private boolean checkAOEnabled() {
        return false;
    }
}
