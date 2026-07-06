package com.gtnewhorizons.angelica.client.gui;

import com.gtnewhorizons.angelica.compat.mojang.Element;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;

import java.util.List;

public abstract class ScrollableGuiScreen extends GuiScreen {
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        handleMouseScroll(mouseX, mouseY, partialTicks);
    }

    public abstract List<? extends Element> children();

    protected void handleMouseScroll(int mouseX, int mouseY, float partialTicks) {
        for (; !this.mc.gameSettings.touchscreen && Mouse.next(); this.mc.currentScreen.handleMouseInput()) {
            int dWheel = Mouse.getEventDWheel();

            if (dWheel != 0) {
                for(Element child : children()) {
                    child.mouseScrolled(mouseX, mouseY, dWheel);
                }
            }
        }
    }
}
