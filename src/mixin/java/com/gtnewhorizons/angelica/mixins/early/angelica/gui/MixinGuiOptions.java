package com.gtnewhorizons.angelica.mixins.early.angelica.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiOptions.class)
public abstract class MixinGuiOptions extends GuiScreen {

    @Unique
    private static final int SUPER_SECRET_SETTINGS_BUTTON_ID = 8675309;

    @Inject(method = "initGui", at = @At("RETURN"))
    private void angelica$disableSuperSecretSettings(CallbackInfo ci) {
        this.buttonList.removeIf(b -> ((GuiButton) b).id == SUPER_SECRET_SETTINGS_BUTTON_ID);
    }
}
