package com.gtnewhorizons.angelica.mixins.early.shaders.startup;

import net.coderbot.iris.Iris;
import net.minecraft.client.settings.GameSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameSettings.class)
public abstract class MixinGameSettings {
    @Unique
    private static boolean iris$shadersInitialized;

    @Inject(method="Lnet/minecraft/client/settings/GameSettings;loadOptions()V", at=@At("HEAD"))
    private void angelica$InitializeShaders(CallbackInfo ci) {
        if (iris$shadersInitialized) {
            return;
        }

        iris$shadersInitialized = true;
        Iris.INSTANCE.onEarlyInitialize();
    }

}
