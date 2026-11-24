package com.gtnewhorizons.angelica.mixins.early.angelica.tesr;

import com.gtnewhorizons.angelica.glsm.DepthEcho;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityFX;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EffectRenderer.class)
public class MixinEffectRenderer_GlowDepthReplay {

    @Inject(method = "addEffect", at = @At("HEAD"), cancellable = true)
    private void angelica$dropDuringGlowReplay(EntityFX effect, CallbackInfo ci) {
        if (DepthEcho.replaying()) ci.cancel();
    }
}
