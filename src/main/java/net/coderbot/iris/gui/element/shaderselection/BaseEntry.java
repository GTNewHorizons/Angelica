package net.coderbot.iris.gui.element.shaderselection;

import net.coderbot.iris.gui.element.IrisGuiSlot;
import net.coderbot.iris.gui.screen.ShaderPackScreen;
import net.minecraft.client.renderer.Tessellator;

public abstract class BaseEntry {
    protected IrisGuiSlot list;
    protected BaseEntry(IrisGuiSlot list) {
        this.list = list;
    }

    public abstract void drawEntry(ShaderPackScreen screen, int index, int x, int y, int listWidth, Tessellator tessellator, int mouseX, int mouseY, boolean isMouseOver);
}
