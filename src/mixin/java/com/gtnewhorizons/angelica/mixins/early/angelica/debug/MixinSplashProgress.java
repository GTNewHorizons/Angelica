package com.gtnewhorizons.angelica.mixins.early.angelica.debug;

import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import cpw.mods.fml.client.SplashProgress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Debug mixin to disable splash screen when LWJGL debug mode is active.
 *
 * NOTE: This is only needed because LWJGL's debug callback conflicts with the splash thread's
 * GL context switching. The splash screen itself works correctly with GLSM caching
 *
 */
@SuppressWarnings("deprecation")
@Mixin(value = SplashProgress.class, remap = false)
public class MixinSplashProgress {
    @WrapOperation(method="Lcpw/mods/fml/client/SplashProgress;start()V", at=@At(value="INVOKE", target="Lcpw/mods/fml/client/SplashProgress;getBool(Ljava/lang/String;Z)Z"))
    private static boolean angelica$disableSplashProgress(String name, boolean def, Operation<Boolean> original) {
        if(name.equals("enabled")) {
            AngelicaTweaker.LOGGER.info("Disabling splash screen due to LWJGL debug mode");
            return false;
        }
        return original.call(name, def);
    }
}
