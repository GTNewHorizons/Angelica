package net.coderbot.iris.gui.element.shaderoptions;

import net.coderbot.iris.gui.NavigationController;
import net.coderbot.iris.gui.screen.ShaderPackScreen;
import net.minecraft.client.renderer.Tessellator;

public abstract class BaseEntry {
    protected final NavigationController navigation;

    protected BaseEntry(NavigationController navigation) {
        this.navigation = navigation;
    }

    public abstract boolean mouseClicked(double mouseX, double mouseY, int button);


    public abstract void drawEntry(ShaderPackScreen screen, int index, int x, int y, int slotWidth, int slotHeight, Tessellator tessellator, int mouseX, int mouseY, boolean isMouseOver);
}
