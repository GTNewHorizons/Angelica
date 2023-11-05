package com.gtnewhorizons.angelica.mixins.early.archaic.common.extrautils;

import com.rwtema.extrautils.EventHandlerSiege;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = EventHandlerSiege.class, remap = false)
public class MixinEventHandlerSiege {
    @Redirect(method = "golemDeath", at = @At(value = "INVOKE", target = "Ljava/lang/Object;equals(Ljava/lang/Object;)Z"))
    private boolean checkSubclassofBeacon(Object subject, Object teBeaconClass) {
        return ((Class<?>)teBeaconClass).isAssignableFrom((Class<?>)subject);
    }
}
