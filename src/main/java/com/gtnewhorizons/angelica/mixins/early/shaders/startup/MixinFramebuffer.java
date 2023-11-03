package com.gtnewhorizons.angelica.mixins.early.shaders.startup;

import net.coderbot.iris.rendertarget.IRenderTargetExt;
import net.minecraft.client.shader.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Framebuffer.class)
public abstract class MixinFramebuffer implements IRenderTargetExt {
    private int iris$depthBufferVersion;

    private int iris$colorBufferVersion;


    @Inject(method = "deleteFramebuffer()V", at = @At(value="INVOKE", target="Lnet/minecraft/client/shader/Framebuffer;unbindFramebuffer()V", shift = At.Shift.AFTER))
    private void iris$onDestroyBuffers(CallbackInfo ci) {
        iris$depthBufferVersion++;
        iris$colorBufferVersion++;
    }

    @Override
    public int iris$getDepthBufferVersion() {
        return iris$depthBufferVersion;
    }

    @Override
    public int iris$getColorBufferVersion() {
        return iris$colorBufferVersion;
    }

}
