package jss.notfine.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.settings.GameSettings;

public class GuiVanillaSettingButton extends GuiButton {

    private final GameSettings.Options linkedSetting;

    public GuiVanillaSettingButton(int xPosition, int yPosition, GameSettings.Options setting) {
        super(setting.ordinal(), xPosition, yPosition, 150, 20, Minecraft.getMinecraft().gameSettings.getKeyBinding(setting));
        linkedSetting = setting;
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        if(super.mousePressed(mc, mouseX, mouseY)) {
            mc.gameSettings.setOptionValue(linkedSetting, 1);
            displayString = mc.gameSettings.getKeyBinding(linkedSetting);
            return true;
        } else {
            return false;
        }
    }

}
