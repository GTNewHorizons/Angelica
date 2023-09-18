package jss.notfine.gui;

import jss.notfine.core.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

public class GuiCustomSettingButton extends GuiButton {

    private final Settings linkedSetting;

    public GuiCustomSettingButton(int xPosition, int yPosition, Settings setting) {
        super(setting.ordinal(), xPosition, yPosition, 150, 20, setting.getLocalization());
        linkedSetting = setting;
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        if(super.mousePressed(mc, mouseX, mouseY)) {
            linkedSetting.incrementValue();
            displayString = linkedSetting.getLocalization();
            return true;
        } else {
            return false;
        }
    }

}
