package net.coderbot.iris.gui.element.shaderoptions;

import buildcraft.core.lib.utils.MathUtils;
import net.coderbot.iris.gui.NavigationController;
import net.coderbot.iris.gui.element.widget.AbstractElementWidget;
import net.coderbot.iris.gui.screen.ShaderPackScreen;
import net.minecraft.client.renderer.Tessellator;

import java.util.List;

public class ElementRowEntry extends BaseEntry {

    private final List<AbstractElementWidget<?>> widgets;
    private final ShaderPackScreen screen;

    private int cachedWidth;
    private int cachedPosX;

    public ElementRowEntry(ShaderPackScreen screen, NavigationController navigation, List<AbstractElementWidget<?>> widgets) {
        super(navigation);

        this.screen = screen;
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

        for (int i = 0; i < widgets.size(); i++) {
            final AbstractElementWidget<?> widget = widgets.get(i);
            final boolean widgetHovered = isMouseOver && (getHoveredWidget(mouseX) == i);
            widget.drawScreen(x + (int) ((singleWidgetWidth + 2) * i), y, (int) singleWidgetWidth, slotHeight + 2, mouseX, mouseY, 0, widgetHovered);

            screen.setElementHoveredStatus(widget, widgetHovered);
        }
    }

    public int getHoveredWidget(int mouseX) {
        final float positionAcrossWidget = ((float) MathUtils.clamp(mouseX - cachedPosX, 0, cachedWidth)) / cachedWidth;

        return MathUtils.clamp((int) Math.floor(widgets.size() * positionAcrossWidget), 0, widgets.size() - 1);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return this.widgets.get(getHoveredWidget((int) mouseX)).mouseClicked(mouseX, mouseY, button);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return this.widgets.get(getHoveredWidget((int) mouseX)).mouseReleased(mouseX, mouseY, button);
    }

}
