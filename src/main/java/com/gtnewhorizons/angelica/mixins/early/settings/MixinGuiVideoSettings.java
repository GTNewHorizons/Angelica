package com.gtnewhorizons.angelica.mixins.early.settings;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiVideoSettings;
import net.minecraft.client.settings.GameSettings;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizons.angelica.client.GuiShaders;

@Mixin(GuiVideoSettings.class)
public abstract class MixinGuiVideoSettings extends GuiScreen {

    private static final int SHADER_BUTTON_ID = 190;

    @Shadow
    private GameSettings guiGameSettings;

    @Inject(method = "initGui()V", at = @At(value = "TAIL"))
    private void angelica$addShadersButton(CallbackInfo ci) {
        // Add the Shaders Button to the bottom of Video Options
        final GuiButton shaderButton = new GuiButton(
                SHADER_BUTTON_ID,
                this.width / 2 - 190,
                this.height - 27,
                70,
                20,
                "Shaders...");
        this.buttonList.add(shaderButton);
    }

    @Inject(method = "actionPerformed(Lnet/minecraft/client/gui/GuiButton;)V", at = @At(value = "HEAD"))
    private void angelica$actionPerformed(GuiButton button, CallbackInfo ci) {
        if (button.id == SHADER_BUTTON_ID) {
            this.mc.gameSettings.saveOptions();
            this.mc.displayGuiScreen(new GuiShaders(this, this.guiGameSettings));
        }
    }
}
