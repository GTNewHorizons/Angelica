package com.gtnewhorizons.angelica.mixins.early.angelica.fpsreducer;

import com.gtnewhorizons.angelica.rendering.FpsReducer;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.settings.GameSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GameSettings.class)
public class MixinGameSettings_FpsReducerVolume {

    @Redirect(method = "setSoundLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/audio/SoundHandler;setSoundLevel(Lnet/minecraft/client/audio/SoundCategory;F)V"))
    private void angelica$redirectSetSoundLevel(SoundHandler handler, SoundCategory category, float level) {
        handler.setSoundLevel(category, category == SoundCategory.MASTER ? FpsReducer.scaleVolume(level) : level);
    }
}
