package com.gtnewhorizons.angelica.mixins.early.sodium;

import com.llamalad7.mixinextras.sugar.Local;
import me.jellysquid.mods.sodium.client.gui.SodiumGameOptions;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class MixinEntity {

    @Shadow
    public abstract boolean isInRangeToRenderDist(double dist);

    @Inject(method ="isInRangeToRender3d", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;isInRangeToRenderDist(D)Z"), cancellable = true)
    private void sodium$afterDistCalc(CallbackInfoReturnable<Boolean> ci, @Local(ordinal = 6) double d6) {
        ci.setReturnValue(this.isInRangeToRenderDist(d6/(SodiumGameOptions.EntityRenderDistance.entityRenderDistanceMultiplier * SodiumGameOptions.EntityRenderDistance.entityRenderDistanceMultiplier)));
    }


}
