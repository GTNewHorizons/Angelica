package com.gtnewhorizons.angelica.mixins.early.sodium.startup;

import net.coderbot.iris.Iris;
import net.minecraft.client.renderer.OpenGlHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OpenGlHelper.class)
public class MixinInitDebug {
    @Inject(method = "initializeTextures", at = @At("RETURN"))
    private static void sodium$initIrisDebug(CallbackInfo ci) {
        // Temp -- move this into common debug code
        Iris.identifyCapabilities();
        Iris.setDebug(true);
    }

}
