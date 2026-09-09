package com.gtnewhorizons.angelica.mixins.early.angelica.debug;

import com.gtnewhorizons.angelica.debug.flyby.FlybyRunner;
import net.minecraft.util.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.entity.EntityLivingBase;

/**
 * Make mobs invulnerable when frozen. They die if in a wall or due to entity cramming.
 */
@Mixin(EntityLivingBase.class)
public abstract class MixinEntityLivingBase_FlybyFreeze {

    @Inject(method = "attackEntityFrom", at = @At("HEAD"), cancellable = true)
    private void angelica$flybyInvulnerable(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (FlybyRunner.entitiesFrozen()) {
            cir.setReturnValue(false);
        }
    }
}
