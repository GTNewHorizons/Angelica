package com.gtnewhorizons.angelica.mixins.early.angelica.blur;

import com.gtnewhorizons.angelica.blur.MenuBlur;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer_MenuBlur {

    @Inject(
        method = "updateCameraAndRender",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;renderWorld(FJ)V", shift = At.Shift.AFTER))
    private void angelica$blurWorldBehindGui(float partialTicks, CallbackInfo ci) {
        MenuBlur.renderBlur();
    }
}
