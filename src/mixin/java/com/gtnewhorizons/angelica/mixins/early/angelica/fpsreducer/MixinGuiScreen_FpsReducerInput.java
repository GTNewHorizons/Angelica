package com.gtnewhorizons.angelica.mixins.early.angelica.fpsreducer;

import com.gtnewhorizons.angelica.rendering.FpsReducer;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GuiScreen.class)
public class MixinGuiScreen_FpsReducerInput {

    @ModifyExpressionValue(method = "handleInput", at = {
        @At(value = "INVOKE", target = "Lorg/lwjgl/input/Mouse;next()Z", remap = false),
        @At(value = "INVOKE", target = "Lorg/lwjgl/input/Keyboard;next()Z", remap = false)
    })
    private boolean angelica$onInputNext(boolean original) {
        if (original) FpsReducer.onInput();
        return original;
    }
}
