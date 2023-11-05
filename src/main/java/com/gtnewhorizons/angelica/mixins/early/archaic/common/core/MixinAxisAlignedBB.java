package com.gtnewhorizons.angelica.mixins.early.archaic.common.core;

import net.minecraft.util.AxisAlignedBB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(AxisAlignedBB.class)
public class MixinAxisAlignedBB {
    private final double XZ_MARGIN = 1E-6;

    @ModifyVariable(method = "calculateXOffset", at = @At(value = "STORE", ordinal = 0), index = 2, argsOnly = true)
    private double subXMargin(double old) {
        return old - XZ_MARGIN;
    }

    @ModifyVariable(method = "calculateXOffset", at = @At(value = "STORE", ordinal = 1), index = 2, argsOnly = true)
    private double addXMargin(double old) {
        return old + XZ_MARGIN;
    }

    @ModifyVariable(method = "calculateZOffset", at = @At(value = "STORE", ordinal = 0), index = 2, argsOnly = true)
    private double subZMargin(double old) {
        return old - XZ_MARGIN;
    }

    @ModifyVariable(method = "calculateZOffset", at = @At(value = "STORE", ordinal = 1), index = 2, argsOnly = true)
    private double addZMargin(double old) {
        return old + XZ_MARGIN;
    }
}
