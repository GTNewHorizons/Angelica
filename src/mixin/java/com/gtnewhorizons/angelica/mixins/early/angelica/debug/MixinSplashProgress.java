package com.gtnewhorizons.angelica.mixins.early.angelica.debug;

import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import cpw.mods.fml.client.SplashProgress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@SuppressWarnings("deprecation")
@Mixin(value = SplashProgress.class, remap = false)
public class MixinSplashProgress {
    @WrapOperation(method="Lcpw/mods/fml/client/SplashProgress;start()V", at=@At(value="INVOKE", target="Lcpw/mods/fml/client/SplashProgress;getBool(Ljava/lang/String;Z)Z"))
    private static boolean angelica$disableSplashProgress(String name, boolean def, Operation<Boolean> original) {
        AngelicaTweaker.LOGGER.info("Forcibly disabling splash progress because LWJGL debug has been set");
        // Forcibly disable splash progress until we can figure out why it's not working with our debug callback
        if(name.equals("enabled"))
            return false;
        else
            return original.call(name, def);
    }

}
