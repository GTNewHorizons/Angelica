package net.coderbot.iris.gui.element;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.renderer.Tessellator;

// TODO: look into GuiListExtended & GuiSelectStringEntries
public abstract class IrisGuiSlot extends GuiSlot {
    @Setter @Getter protected boolean renderBackground = true;

    protected IrisGuiSlot(Minecraft mc, int width, int height, int top, int bottom, int slotHeight) {
        super(mc, width, height, top, bottom, slotHeight);
    }

    @Override
    protected void drawContainerBackground(Tessellator tessellator) {
        if(this.renderBackground) {
            super.drawContainerBackground(tessellator);
        }
    }

    @Override
    protected int getScrollBarX() {
        // Position the scrollbar at the rightmost edge of the screen.
        // By default, the scrollbar is positioned moderately offset from the center.
        return this.width - 6;
    }

    @Override
    protected void drawSelectionBox(int x, int y, int mouseX, int mouseY) {
        final int oldPadding = this.headerPadding;
        this.headerPadding = 2;
        super.drawSelectionBox(x, y, mouseX, mouseY);
        this.headerPadding = oldPadding;
    }

}
