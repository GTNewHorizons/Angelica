package net.coderbot.iris.gui.element.shaderoptions;

import net.coderbot.iris.gui.NavigationController;
import net.coderbot.iris.gui.element.widget.AbstractElementWidget;
import net.coderbot.iris.gui.screen.ShaderPackScreen;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.MathHelper;

import java.util.List;

public class ElementRowEntry extends BaseEntry {

    private final List<AbstractElementWidget<?>> widgets;

    private int cachedWidth;
    private int cachedPosX;
    private float cachedSingleWidth;

    public ElementRowEntry(NavigationController navigation, List<AbstractElementWidget<?>> widgets) {
        super(navigation);
        this.widgets = widgets;
    }

    @Override
    public void drawEntry(ShaderPackScreen screen, int index, int x, int y, int slotWidth, int slotHeight, Tessellator tessellator, int mouseX, int mouseY, boolean isMouseOver) {
        this.cachedWidth = slotWidth;
        this.cachedPosX = x;

        // The amount of space widgets will occupy, excluding margins. Will be divided up between widgets.
        int totalWidthWithoutMargins = slotWidth - (2 * (widgets.size() - 1));

        totalWidthWithoutMargins -= 3; // Centers it for some reason

        // Width of a single widget
        final float singleWidgetWidth = (float) totalWidthWithoutMargins / widgets.size();
        this.cachedSingleWidth = singleWidgetWidth;

        for (int i = 0; i < widgets.size(); i++) {
            final AbstractElementWidget<?> widget = widgets.get(i);
            final boolean widgetHovered = isMouseOver && (getHoveredWidget(mouseX) == i);
            widget.drawScreen(x + (int) ((singleWidgetWidth + 2) * i), y, (int) singleWidgetWidth, slotHeight - 2, mouseX, mouseY, 0, widgetHovered);

            screen.setElementHoveredStatus(widget, widgetHovered);
        }
    }

    public int getHoveredWidget(int mouseX) {

        final float CONTENT_ANCHOR_OFFSET = 2f;
        final float rel = (mouseX - cachedPosX) + CONTENT_ANCHOR_OFFSET;
        final float clamped = Math.clamp(rel, 0f, cachedWidth);
        final float positionAcrossWidget = clamped / cachedWidth;

        return MathHelper.clamp_int((int) Math.floor(widgets.size() * positionAcrossWidget), 0, widgets.size() - 1);
    }

    private int widgetAt(int mouseX) {
        final int i = getHoveredWidget(mouseX);
        final int widgetX = this.cachedPosX + (int) ((this.cachedSingleWidth + 2) * i);
        if (mouseX < widgetX || mouseX >= widgetX + (int) this.cachedSingleWidth) {
            return -1;
        }
        return i;
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        final int i = widgetAt(mouseX);
        if (i < 0) {
            return false;
        }
        return this.widgets.get(i).mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int button) {
        return this.widgets.get(getHoveredWidget(mouseX)).mouseReleased(mouseX, mouseY, button);
    }

}
