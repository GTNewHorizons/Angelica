package com.gtnewhorizons.angelica.mixins.early.sodium;

import com.gtnewhorizons.angelica.client.gui.SodiumGameOptions;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Entity.class)
public abstract class MixinEntity_RenderDist {

    @ModifyArg(method = "isInRangeToRender3d", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;isInRangeToRenderDist(D)Z"))
    private double sodium$afterDistCalc(double d6) {
        final double mult = SodiumGameOptions.EntityRenderDistance.entityRenderDistanceMultiplier;
        return d6 / (mult * mult);
    }
}
