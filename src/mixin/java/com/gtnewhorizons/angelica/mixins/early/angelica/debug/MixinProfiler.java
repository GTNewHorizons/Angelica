package com.gtnewhorizons.angelica.mixins.early.angelica.debug;

import com.gtnewhorizons.angelica.glsm.GLDebug;
import net.minecraft.profiler.Profiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Profiler.class)
public class MixinProfiler {
    @Inject(method = "startSection(Ljava/lang/String;)V", at = @At("HEAD"))
    private void debugStartSection(String name, CallbackInfo ci) {
        GLDebug.pushGroup(0, "minecraft:" + name);
    }

    @Inject(method = "endSection()V", at = @At("HEAD"))
    private void debugEndSection(CallbackInfo ci) {
        GLDebug.popGroup();
    }
}
