package jss.notfine.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

public class GuiCustomMenuButton extends GuiButton {

    private final MenuButtonLists linkedList;

    public GuiCustomMenuButton(int xPosition, int yPosition, int width, int height, MenuButtonLists list) {
        super(-list.ordinal(), xPosition, yPosition, width, height, list.getButtonLabel());
        linkedList = list;
    }

    public GuiCustomMenuButton(int xPosition, int yPosition, MenuButtonLists list) {
        this(xPosition, yPosition, 150, 20, list);
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        boolean load = super.mousePressed(mc, mouseX, mouseY);
        if(load) {
            mc.displayGuiScreen(new GuiCustomMenu(mc.currentScreen, linkedList));
        }
        return load;
    }

}
