package com.gtnewhorizons.angelica.mixins.early.angelica.gui;

import com.gtnewhorizons.angelica.client.font.AngelicaFontRenderContext;
import net.minecraft.client.gui.GuiTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiTextField.class)
public abstract class MixinGuiTextField {

    @Unique
    private boolean angelica$rawPushed;

    @Inject(method = "drawTextBox", at = @At("HEAD"))
    private void angelica$beginRawMode(CallbackInfo ci) {
        AngelicaFontRenderContext.pushRawTextRendering();
        angelica$rawPushed = true;
    }

    @Inject(method = "drawTextBox", at = @At("RETURN"))
    private void angelica$endRawMode(CallbackInfo ci) {
        if (angelica$rawPushed) {
            AngelicaFontRenderContext.popRawTextRendering();
            angelica$rawPushed = false;
        }
    }
}
