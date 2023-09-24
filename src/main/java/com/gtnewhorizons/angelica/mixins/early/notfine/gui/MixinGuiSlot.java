package com.gtnewhorizons.angelica.mixins.early.notfine.gui;

import jss.notfine.core.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiSlot.class)
public abstract class MixinGuiSlot {

    @Inject(method = "drawContainerBackground", at = @At("HEAD"), cancellable = true, remap = false)
    private void notFine$toggleContainerBackground(CallbackInfo ci) {
        if(!Settings.MODE_GUI_BACKGROUND.isValueBase() && Minecraft.getMinecraft().theWorld != null) {
            ci.cancel();
        }
    }

}
