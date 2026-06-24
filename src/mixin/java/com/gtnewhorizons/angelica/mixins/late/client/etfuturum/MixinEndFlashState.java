package com.gtnewhorizons.angelica.mixins.late.client.etfuturum;

import net.coderbot.iris.Iris;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Keeps the Et Futurum Requiem End flash above the horizon while a shader pack is loaded.
 * Apparently Iris does this too.
 */
@Mixin(targets = "ganymedes01.etfuturum.client.EndFlashState", remap = false)
public class MixinEndFlashState {

    @ModifyConstant(method = "calculateFlashParameters", constant = @Constant(floatValue = 10.0F), remap = false)
    private float iris$keepFlashAboveHorizon(float original) {
        return Iris.getCurrentPack().isPresent() ? -1.0F : original;
    }
}
