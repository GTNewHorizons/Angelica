package com.gtnewhorizons.angelica.mixins.early.sodium.startup;

import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
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
        if (Thread.currentThread() != GLStateManager.getMainThread()) {
            AngelicaTweaker.LOGGER.warn("Renderer initialization called from non-main thread!");
            return;
        }
        // Temp -- move this into common debug code
        Iris.identifyCapabilities();
        Iris.setDebug(AngelicaMod.lwjglDebug);
    }

}
