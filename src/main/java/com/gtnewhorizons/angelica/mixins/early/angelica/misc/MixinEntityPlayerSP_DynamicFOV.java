package com.gtnewhorizons.angelica.mixins.early.angelica.misc;


import jss.notfine.core.Settings;
import net.minecraft.client.entity.EntityPlayerSP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityPlayerSP.class)
public class MixinEntityPlayerSP_DynamicFOV {

    @Inject(method = "getFOVMultiplier", at = @At("RETURN"), cancellable = true)
    void angelica$getFOVMultiplier_return(CallbackInfoReturnable<Float> cir) {
        if (!(boolean)Settings.DYNAMIC_FOV.option.getStore())
            cir.setReturnValue(1.0f);
    }

}
