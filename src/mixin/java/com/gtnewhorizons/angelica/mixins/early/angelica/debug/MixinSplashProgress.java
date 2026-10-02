package com.gtnewhorizons.angelica.mixins.early.angelica.debug;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.loading.AngelicaClientTweaker;
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
    @Surround(method="Lcpw/mods/fml/client/SplashProgress;start()V", at=@At(value="INVOKE", target="Lcpw/mods/fml/client/SplashProgress;getBool(Ljava/lang/String;Z)Z"))
    private static void angelica$disableSplashProgress(String name) {
        @Surround.Skip
        boolean skip = name.equals("enabled");
        if (skip) {
            AngelicaClientTweaker.LOGGER.info("Disabling splash screen due to LWJGL debug mode");
        }
    }

    @Surround.Skipped
    private static boolean angelica$disableSplashProgressSkipped() {
        return false;
    }
}
