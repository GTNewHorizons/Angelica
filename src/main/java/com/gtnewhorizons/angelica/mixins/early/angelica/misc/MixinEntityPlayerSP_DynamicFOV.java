package com.gtnewhorizons.angelica.mixins.early.angelica.misc;


import jss.notfine.core.Settings;
import net.minecraft.client.entity.EntityPlayerSP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(EntityPlayerSP.class)
public class MixinEntityPlayerSP_DynamicFOV {

    @ModifyVariable(method = "getFOVMultiplier", at = @At(value = "STORE", ordinal = 2))
    float angelica$getFOVMultiplier(float f){
        if ((boolean)Settings.DYNAMIC_FOV.option.getStore())
            return f;

        return 1.0f;
    }

}
