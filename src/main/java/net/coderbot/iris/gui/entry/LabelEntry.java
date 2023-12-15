package net.coderbot.iris.gui.entry;

import net.coderbot.iris.gui.element.IrisGuiSlot;
import net.coderbot.iris.gui.screen.ShaderPackScreen;
import net.minecraft.client.renderer.Tessellator;

public class LabelEntry extends BaseEntry {
    private final String label;

    public LabelEntry(IrisGuiSlot list, String label) {
        super(list);
        this.label = label;
    }

    @Override
    public void drawEntry(ShaderPackScreen screen, int index, int x, int y, int listWidth, Tessellator tessellator, int mouseX, int mouseY, boolean isMouseOver) {
        screen.drawCenteredString(label, x, y, 0xFFFFFF);
    }
}
