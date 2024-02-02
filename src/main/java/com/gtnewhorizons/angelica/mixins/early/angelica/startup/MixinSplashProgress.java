package com.gtnewhorizons.angelica.mixins.early.angelica.startup;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets="cpw/mods/fml/client/SplashProgress$3", remap = false)
public abstract class MixinSplashProgress {
    @Inject(method = "setGL", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/locks/Lock;lock()V", shift = At.Shift.AFTER))
    private void angelica$startSplash(CallbackInfo ci) {
        GLStateManager.setRunningSplash(true);
    }

}
