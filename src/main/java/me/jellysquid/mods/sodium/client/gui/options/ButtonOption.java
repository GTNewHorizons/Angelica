package me.jellysquid.mods.sodium.client.gui.options;

import java.util.Collection;
import java.util.Collections;

import me.jellysquid.mods.sodium.client.gui.options.control.Control;
import me.jellysquid.mods.sodium.client.gui.options.control.ControlElement;
import me.jellysquid.mods.sodium.client.gui.options.control.element.ControlElementFactory;
import me.jellysquid.mods.sodium.client.gui.options.control.element.SodiumControlElement;
import me.jellysquid.mods.sodium.client.gui.options.storage.OptionStorage;
import me.jellysquid.mods.sodium.client.util.Dim2i;

public abstract class ButtonOption implements Option<Void> {

    private static final int LABEL_HOVERED = 0xFF94E4D3;
    private static final int LABEL_IDLE = 0xFFAAAAAA;

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
    private final Control<Void> control;

    protected ButtonOption(String name, String tooltip, int maxWidth) {
        this.name = name;
        this.tooltip = tooltip;
        this.control = new ButtonControl(this, maxWidth);
    }

    protected abstract String label();

    protected abstract void invoke();

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

    private record ButtonControl(ButtonOption target, int maxWidth) implements Control<Void> {

        @Override
        public Option<Void> getOption() {
            return this.target;
        }

        @Override
        public ControlElement<Void> createElement(Dim2i dim, ControlElementFactory factory) {
            return new ButtonElement(this.target, dim);
        }

        @Override
        public int getMaxWidth() {
            return this.maxWidth;
        }
    }

    private static class ButtonElement extends SodiumControlElement<Void> {

        private final ButtonOption target;

        ButtonElement(ButtonOption target, Dim2i dim) {
            super(target, dim);
            this.target = target;
        }

        @Override
        public void render(int mouseX, int mouseY, float delta) {
            super.render(mouseX, mouseY, delta);

            final String label = this.target.label();
            final int width = this.font.getStringWidth(label);
            this.drawString(label, this.dim.getLimitX() - width - 6, this.dim.getCenterY() - 4, this.hovered ? LABEL_HOVERED : LABEL_IDLE);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!this.target.isAvailable() || button != 0 || !this.dim.containsCursor(mouseX, mouseY)) return false;

            this.playClickSound();
            this.target.invoke();
            return true;
        }
    }
}
