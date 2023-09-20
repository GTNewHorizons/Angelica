package com.gtnewhorizons.angelica.client.gui;


import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

public class GuiShadersButton extends GuiButton {

    public GuiShadersButton(int xPosition, int yPosition, AngelicaVideoSettings setting) {
        super(setting.ordinal(), xPosition, yPosition, 150, 20, setting.getButtonLabel());
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        if(super.mousePressed(mc, mouseX, mouseY)) {
            mc.gameSettings.saveOptions();
            mc.displayGuiScreen(new GuiShaders(mc.currentScreen, null));
            return true;
        } else {
            return false;
        }
    }

}
