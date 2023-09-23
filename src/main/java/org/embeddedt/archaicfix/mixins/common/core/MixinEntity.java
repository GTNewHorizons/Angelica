package org.embeddedt.archaicfix.mixins.common.core;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class MixinEntity {
    @Shadow public float fallDistance;

    /**
     * @reason Fixes a vanilla bug where the entity's fall distance is not updated before invoking the
     * block's onFallenUpon when it falls on the ground, meaning that the last fall state update won't
     * be included in the fall distance.
     */
    @Inject(method = "updateFallState", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;fall(F)V"))
    private void beforeOnFallenUpon(double distanceFallenThisTick, boolean isOnGround, CallbackInfo ci) {
        if (distanceFallenThisTick < 0) fallDistance -= distanceFallenThisTick;
    }
}