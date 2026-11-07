package com.gtnewhorizons.angelica.mixins.early.angelica.rendering;

import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.rendering.translucency.TranslucencyLayers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Framebuffer.class)
public class MixinFramebuffer_TranslucencyLayers {

    @Inject(method = "bindFramebuffer", at = @At("HEAD"), cancellable = true)
    private void angelica$bindOpenTranslucencyLayer(boolean viewport, CallbackInfo ci) {
        final Framebuffer framebuffer = (Framebuffer) (Object) this;
        if (!TranslucencyLayers.INSTANCE.redirectsMainBind() || framebuffer != Minecraft.getMinecraft().getFramebuffer()) return;
        TranslucencyLayers.INSTANCE.bindOpenLayer();
        if (viewport) GLSMRasterizer.glViewport(0, 0, framebuffer.framebufferWidth, framebuffer.framebufferHeight);
        ci.cancel();
    }
}
