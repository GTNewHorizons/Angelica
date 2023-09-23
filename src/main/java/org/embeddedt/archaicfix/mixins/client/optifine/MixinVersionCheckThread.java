package org.embeddedt.archaicfix.mixins.client.optifine;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = {"VersionCheckThread"}, remap = false)
public abstract class MixinVersionCheckThread extends Thread {
    @Inject(method = "run", at = @At("HEAD"), cancellable = true)
    private void skipCheck(CallbackInfo ci) {
        ci.cancel();
    }
}
