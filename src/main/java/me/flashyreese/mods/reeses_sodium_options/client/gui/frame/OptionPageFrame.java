package me.flashyreese.mods.reeses_sodium_options.client.gui.frame;

import me.flashyreese.mods.reeses_sodium_options.client.gui.OptionExtended;
import com.gtnewhorizons.angelica.client.gui.options.Option;
import com.gtnewhorizons.angelica.client.gui.options.OptionGroup;
import com.gtnewhorizons.angelica.client.gui.options.OptionImpact;
import com.gtnewhorizons.angelica.client.gui.options.OptionPage;
import com.gtnewhorizons.angelica.client.gui.options.control.Control;
import com.gtnewhorizons.angelica.client.gui.options.control.ControlElement;
import com.gtnewhorizons.angelica.client.gui.options.control.element.SodiumControlElementFactory;
import com.gtnewhorizons.angelica.client.gui.widgets.AbstractWidget;
import me.jellysquid.mods.sodium.client.util.Dim2i;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;
import org.apache.commons.lang3.Validate;

import java.util.ArrayList;
import java.util.List;

public class OptionPageFrame extends AbstractFrame {
    protected static final SodiumControlElementFactory elementFactory = new SodiumControlElementFactory();
    protected final Dim2i originalDim;
    protected final OptionPage page;
    private long lastTime = 0;
    private ControlElement<?> lastHoveredElement = null;

    public OptionPageFrame(Dim2i dim, boolean renderOutline, OptionPage page) {
        super(dim, renderOutline);
        this.originalDim = new Dim2i(dim.getOriginX(), dim.getOriginY(), dim.getWidth(), dim.getHeight());
        this.page = page;
        this.setupFrame();
        this.buildFrame();
    }

    public static Builder createBuilder() {
        return new Builder();
    }

    public void setupFrame() {
        this.children.clear();
        this.drawable.clear();
        this.controlElements.clear();

        int y = 0;
        if (!this.page.getGroups().isEmpty()) {
            OptionGroup lastGroup = this.page.getGroups().get(this.page.getGroups().size() - 1);

            for (OptionGroup group : this.page.getGroups()) {
                y += group.getOptions().size() * 18;
                if (group != lastGroup) {
                    y += 4;
                }
            }
        }

        this.dim.setHeight(y);
        this.page.getGroups().forEach(group -> group.getOptions().forEach(option -> {
            if (option instanceof OptionExtended<?> optionExtended) {
                optionExtended.setParentDimension(this.dim);
            }
        }));
    }

    @Override
    public void buildFrame() {
        if (this.page == null) return;

        this.children.clear();
        this.drawable.clear();
        this.controlElements.clear();

        int y = 0;
        for (OptionGroup group : this.page.getGroups()) {
            // Add each option's control element
            for (Option<?> option : group.getOptions()) {
                final Control<?> control = option.getControl();
                final ControlElement<?> element = control.createElement(new Dim2i(this.dim.getOriginX(), this.dim.getOriginY() + y, this.dim.getWidth(), 18), elementFactory);
                // This is probably not safe
                this.children.add((AbstractWidget) element);

                // Move down to the next option
                y += 18;
            }

            // Add padding beneath each option group
            y += 4;
        }

        super.buildFrame();
    }

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        final ControlElement<?> hoveredElement = this.controlElements.stream()
                .filter(controlElement -> controlElement.getDimensions().overlapWith(this.originalDim))
                .filter(ControlElement::isHovered)
                .findFirst()
                .orElse(null);
        super.render(mouseX, mouseY, delta);
        if (hoveredElement != null && this.lastHoveredElement == hoveredElement &&
                (this.originalDim.containsCursor(mouseX, mouseY) && hoveredElement.isHovered()/* && isMouseOver(mouseX, mouseY)*/)) {
            if (this.lastTime == 0) {
                this.lastTime = System.currentTimeMillis();
            }
            this.renderOptionTooltip(hoveredElement);
        } else {
            this.lastTime = 0;
            this.lastHoveredElement = hoveredElement;
        }
    }

    private void renderOptionTooltip(ControlElement<?> element) {
        final FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;
        if (this.lastTime + 500 > System.currentTimeMillis()) return;

        final Dim2i dim = element.getDimensions();

        final int textPadding = 3;
        final int boxPadding = 3;

        final int boxWidth = dim.getWidth();

        //Offset based on mouse position, width and height of content and width and height of the window
        int boxY = dim.getLimitY();
        final int boxX = dim.getOriginX();

        final Option<?> option = element.getOption();
        final List<String> tooltip = new ArrayList<>(fontRenderer.listFormattedStringToWidth(option.getTooltip(), boxWidth - (textPadding * 2)));

        final OptionImpact impact = option.getImpact();

        if (impact != null) {
            tooltip.add(EnumChatFormatting.GRAY + I18n.format("sodium.options.performance_impact_string", impact.toDisplayString()));
        }

        final int boxHeight = (tooltip.size() * 12) + boxPadding;
        final int boxYLimit = boxY + boxHeight;
        final int boxYCutoff = this.originalDim.getLimitY();

        // If the box is going to be cutoff on the Y-axis, move it back up the difference
        if (boxYLimit > boxYCutoff) {
            boxY -= boxHeight + dim.getHeight();
        }

        if (boxY < 0) {
            boxY = dim.getLimitY();
        }

        this.drawRect(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xE0000000);
        this.drawRectOutline(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xFF94E4D3);

        for (int i = 0; i < tooltip.size(); i++) {
            fontRenderer.drawString(tooltip.get(i), boxX + textPadding, boxY + textPadding + (i * 12), 0xFFFFFFFF);
        }
    }

    public static class Builder {
        private Dim2i dim;
        private boolean renderOutline;
        private OptionPage page;

        public Builder setDimension(Dim2i dim) {
            this.dim = dim;
            return this;
        }

        public Builder shouldRenderOutline(boolean renderOutline) {
            this.renderOutline = renderOutline;
            return this;
        }

        public Builder setOptionPage(OptionPage page) {
            this.page = page;
            return this;
        }

        public OptionPageFrame build() {
            Validate.notNull(this.dim, "Dimension must be specified");
            Validate.notNull(this.page, "Option Page must be specified");

            return new OptionPageFrame(this.dim, this.renderOutline, this.page);
        }
    }
}
