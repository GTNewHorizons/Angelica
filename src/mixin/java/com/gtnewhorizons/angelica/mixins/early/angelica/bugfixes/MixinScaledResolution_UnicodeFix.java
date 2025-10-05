package com.gtnewhorizons.angelica.mixins.early.angelica.bugfixes;

import net.minecraft.client.gui.ScaledResolution;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(ScaledResolution.class)
public class MixinScaledResolution_UnicodeFix {


    // Force unicode languages to use the calculated scaleFactor and not adjust it.
    // Can lead to blurry text
    @ModifyConstant(method = "<init>", constant = @Constant(intValue = 1, ordinal = 5))
    int angelica$ScaledResolutionInit(int constant){
        return 0;
    }

}
