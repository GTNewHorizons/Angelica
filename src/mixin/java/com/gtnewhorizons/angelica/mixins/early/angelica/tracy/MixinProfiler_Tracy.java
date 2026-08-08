package com.gtnewhorizons.angelica.mixins.early.angelica.tracy;

import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import net.minecraft.profiler.Profiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Profiler.class)
public class MixinProfiler_Tracy {
    @Inject(method = "startSection(Ljava/lang/String;)V", at = @At("HEAD"))
    private void angelica$tracyStartSection(String name, CallbackInfo ci) {
        Tracy.beginSection(name);
    }

    @Inject(method = "endSection()V", at = @At("HEAD"))
    private void angelica$tracyEndSection(CallbackInfo ci) {
        Tracy.endZone();
    }
}
