package com.gtnewhorizons.angelica.mixins.early.angelica.tesr;

import com.gtnewhorizons.angelica.glsm.DepthEcho;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundHandler.class)
public class MixinSoundHandler_GlowDepthReplay {

    @Inject(method = "playSound", at = @At("HEAD"), cancellable = true)
    private void angelica$dropDuringGlowReplay(ISound sound, CallbackInfo ci) {
        if (DepthEcho.replaying()) ci.cancel();
    }

    @Inject(method = "playDelayedSound", at = @At("HEAD"), cancellable = true)
    private void angelica$dropDelayedDuringGlowReplay(ISound sound, int delay, CallbackInfo ci) {
        if (DepthEcho.replaying()) ci.cancel();
    }
}
