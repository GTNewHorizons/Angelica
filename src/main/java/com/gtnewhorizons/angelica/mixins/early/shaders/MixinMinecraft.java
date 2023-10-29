package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.client.Shaders;
import net.coderbot.iris.rendertarget.IRenderTargetExt;
import net.minecraft.client.Minecraft;
import net.minecraft.profiler.IPlayerUsage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft implements IPlayerUsage, IRenderTargetExt {
    private int iris$depthBufferVersion;

    private int iris$colorBufferVersion;


    @Inject(method = "startGame()V",
            at = @At(ordinal = 1, shift = At.Shift.AFTER, value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;checkGLError(Ljava/lang/String;)V"))
    private void angelica$Startup(CallbackInfo ci) {
        // Start the Shaders
        Shaders.startup(((Minecraft) ((Object) this)));
    }


    @Inject(method = "updateFramebufferSize()V", at = @At("HEAD"))
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
