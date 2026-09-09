package com.gtnewhorizons.angelica.mixins.early.angelica.fpsreducer;

import com.gtnewhorizons.angelica.rendering.FpsReducer;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.client.audio.SoundManager$1")
public class MixinSoundManagerLoader_FpsReducerVolume {

    @SuppressWarnings("UnresolvedMixinReference")
    @ModifyExpressionValue(method = "run()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/settings/GameSettings;getSoundLevel(Lnet/minecraft/client/audio/SoundCategory;)F"))
    private float angelica$scaleMasterOnReload(float original) {
        return FpsReducer.scaleVolume(original);
    }
}
