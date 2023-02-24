package com.gtnewhorizons.angelica.mixins.early.StartShaders;

import net.minecraft.client.Minecraft;
import net.minecraft.profiler.IPlayerUsage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizons.angelica.client.Shaders;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft implements IPlayerUsage {

    @Inject(
            method = "Lnet/minecraft/client/Minecraft;startGame()V",
            at = @At(
                    ordinal = 1,
                    shift = At.Shift.AFTER,
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Minecraft;checkGLError(Ljava/lang/String;)V"))
    private void angelica$Startup(CallbackInfo ci) {
        // Start the Shaders
        Shaders.startup(((Minecraft) ((Object) this)));
    }
}
