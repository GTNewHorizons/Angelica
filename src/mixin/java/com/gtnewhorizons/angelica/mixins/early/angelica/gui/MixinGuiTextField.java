package com.gtnewhorizons.angelica.mixins.early.angelica.gui;

import com.gtnewhorizons.angelica.mixins.hooks.TextHighlightHooks;
import net.minecraft.client.gui.GuiTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiTextField.class)
public abstract class MixinGuiTextField {

    @Shadow public int xPosition;
    @Shadow public int width;

    @Inject(method = "drawCursorVertical", at = @At("HEAD"), cancellable = true)
    private void angelica$drawCursorVertical(int x1, int y1, int x2, int y2, CallbackInfo ci) {
        int tmp;

        if (x1 < x2) {
            tmp = x1;
            x1 = x2;
            x2 = tmp;
        }

        if (y1 < y2) {
            tmp = y1;
            y1 = y2;
            y2 = tmp;
        }

        if (x2 > this.xPosition + this.width) {
            x2 = this.xPosition + this.width;
        }

        if (x1 > this.xPosition + this.width) {
            x1 = this.xPosition + this.width;
        }

        TextHighlightHooks.draw(x1, y1, x2, y2);
        ci.cancel();
    }
}
