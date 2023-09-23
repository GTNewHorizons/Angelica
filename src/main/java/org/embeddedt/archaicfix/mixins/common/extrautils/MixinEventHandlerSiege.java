package org.embeddedt.archaicfix.mixins.common.extrautils;

import com.rwtema.extrautils.EventHandlerSiege;
import net.minecraft.tileentity.TileEntityBeacon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = EventHandlerSiege.class, remap = false)
public class MixinEventHandlerSiege {
    @Redirect(method = "golemDeath", at = @At(value = "INVOKE", target = "Ljava/lang/Object;equals(Ljava/lang/Object;)Z"))
    private boolean checkSubclassofBeacon(Object subject, Object teBeaconClass) {
        return ((Class<TileEntityBeacon>)teBeaconClass).isAssignableFrom((Class<?>)subject);
    }
}
