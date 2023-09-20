package jss.notfine.gui;

import net.minecraft.client.gui.GuiButton;

public interface ISettingHandler {
    GuiButton createButton(int xPosition, int yPosition, Object setting);
}
