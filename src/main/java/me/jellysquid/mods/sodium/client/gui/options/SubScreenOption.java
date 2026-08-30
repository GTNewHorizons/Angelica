package me.jellysquid.mods.sodium.client.gui.options;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import me.jellysquid.mods.sodium.client.gui.options.control.Control;
import me.jellysquid.mods.sodium.client.gui.options.control.ControlElement;
import me.jellysquid.mods.sodium.client.gui.options.control.element.ControlElementFactory;
import me.jellysquid.mods.sodium.client.gui.options.control.element.SodiumControlElement;
import me.jellysquid.mods.sodium.client.gui.options.storage.OptionStorage;
import me.jellysquid.mods.sodium.client.util.Dim2i;

/** "Option" that opens a sub screen */
public class SubScreenOption implements Option<Void> {

    private static final int ARROW_HOVERED = 0xFF94E4D3;
    private static final int ARROW_IDLE = 0xFFAAAAAA;
    private static final String ARROW = ">";

    private static final OptionStorage<Void> NO_STORAGE = new OptionStorage<Void>() {

        @Override
        public Void getData() {
            return null;
        }

        @Override
        public void save() {}
    };

    private final String name;
    private final String tooltip;
    private final Function<GuiScreen, GuiScreen> destination;
    private final Control<Void> control = new SubScreenControl(this);

    public SubScreenOption(String name, String tooltip, Function<GuiScreen, GuiScreen> destination) {
        this.name = name;
        this.tooltip = tooltip;
        this.destination = destination;
    }

    private void open() {
        final var mc = Minecraft.getMinecraft();
        mc.displayGuiScreen(this.destination.apply(mc.currentScreen));
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getTooltip() {
        return this.tooltip;
    }

    @Override
    public OptionImpact getImpact() {
        return null;
    }

    @Override
    public Control<Void> getControl() {
        return this.control;
    }

    @Override
    public Void getValue() {
        return null;
    }

    @Override
    public void setValue(Void value) {}

    @Override
    public void reset() {}

    @Override
    public OptionStorage<?> getStorage() {
        return NO_STORAGE;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean hasChanged() {
        return false;
    }

    @Override
    public void applyChanges() {}

    @Override
    public Collection<OptionFlag> getFlags() {
        return Collections.emptySet();
    }

    private record SubScreenControl(SubScreenOption target) implements Control<Void> {

        @Override
        public Option<Void> getOption() {
            return this.target;
        }

        @Override
        public ControlElement<Void> createElement(Dim2i dim, ControlElementFactory factory) {
            return new SubScreenElement(this.target, dim);
        }

        @Override
        public int getMaxWidth() {
            return 30;
        }
    }

    private static class SubScreenElement extends SodiumControlElement<Void> {

        private final SubScreenOption target;

        SubScreenElement(SubScreenOption option, Dim2i dim) {
            super(option, dim);
            this.target = option;
        }

        @Override
        public void render(int mouseX, int mouseY, float delta) {
            super.render(mouseX, mouseY, delta);

            final var width = this.font.getStringWidth(ARROW);
            this.drawString(ARROW, this.dim.getLimitX() - width - 6, this.dim.getCenterY() - 4, this.hovered ? ARROW_HOVERED : ARROW_IDLE);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0 || !this.dim.containsCursor(mouseX, mouseY)) return false;

            this.playClickSound();
            this.target.open();
            return true;
        }
    }
}
