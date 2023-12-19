package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.hd;

import net.minecraft.client.renderer.texture.TextureClock;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.prupe.mcpatcher.hd.FancyDial;

@Mixin(TextureClock.class)
public abstract class MixinTextureClock {

    @Inject(method = "<init>(Ljava/lang/String;)V", at = @At("RETURN"))
    private void modifyConstructor(String iconName, CallbackInfo ci) {
        FancyDial.setup((TextureClock) (Object) this);
    }

    @Inject(
        method = "updateAnimation()V",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/renderer/texture/TextureClock;field_94239_h:D",
            ordinal = 1,
            shift = At.Shift.AFTER),
        cancellable = true)
    private void modifyUpdateAnimation(CallbackInfo ci) {
        if (FancyDial.update((TextureClock) (Object) this, false)) {
            ci.cancel();
        }
    }
}
