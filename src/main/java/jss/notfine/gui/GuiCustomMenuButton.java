package jss.notfine.gui;

import me.jellysquid.mods.sodium.client.gui.options.OptionPage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

public class GuiCustomMenuButton extends GuiButton {

    private final OptionPage optionPage;
    private final OptionPage[] subPages;

    public GuiCustomMenuButton(int xPosition, int yPosition, int width, int height, OptionPage optionPage, OptionPage... subPages) {
        super(-10, xPosition, yPosition, width, height, optionPage.getName());
        this.optionPage = optionPage;
        this.subPages = subPages;
    }

    public GuiCustomMenuButton(int xPosition, int yPosition, OptionPage optionPage, OptionPage... subPages) {
        this(xPosition, yPosition, 150, 20, optionPage, subPages);
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        boolean load = super.mousePressed(mc, mouseX, mouseY);
        if(load) {
            mc.displayGuiScreen(new GuiCustomMenu(mc.currentScreen, optionPage, subPages));
        }
        return load;
    }

}
