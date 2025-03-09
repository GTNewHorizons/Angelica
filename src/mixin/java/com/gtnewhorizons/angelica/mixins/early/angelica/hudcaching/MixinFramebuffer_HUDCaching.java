package com.gtnewhorizons.angelica.mixins.early.angelica.hudcaching;

import com.gtnewhorizons.angelica.hudcaching.HUDCaching;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Framebuffer.class)
public class MixinFramebuffer_HUDCaching {

    @Inject(method = "bindFramebuffer", at = @At("HEAD"), cancellable = true)
    public void angelica$bindHUDCachingBuffer(boolean viewport, CallbackInfo ci) {
        final Framebuffer framebuffer = (Framebuffer) (Object) this;
        if (HUDCaching.INSTANCE.renderingCacheOverride && framebuffer == Minecraft.getMinecraft().getFramebuffer()) {
            HUDCaching.INSTANCE.framebuffer.bindFramebuffer(viewport);
            ci.cancel();
        }
    }

}
