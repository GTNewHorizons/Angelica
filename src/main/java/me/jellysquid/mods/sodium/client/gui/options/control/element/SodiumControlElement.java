package me.jellysquid.mods.sodium.client.gui.options.control.element;

import me.jellysquid.mods.sodium.client.gui.options.Option;
import me.jellysquid.mods.sodium.client.gui.options.control.ControlElement;
import me.jellysquid.mods.sodium.client.gui.widgets.AbstractWidget;
import me.jellysquid.mods.sodium.client.util.Dim2i;
import net.minecraft.util.EnumChatFormatting;

public class SodiumControlElement<T> extends AbstractWidget implements ControlElement<T> {
    protected final Option<T> option;
    protected final Dim2i dim;
    protected boolean hovered;

    public SodiumControlElement(Option<T> option, Dim2i dim) {
        this.option = option;
        this.dim = dim;
    }

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        String name = this.option.getName();
        String label;

        if (this.hovered && this.font.getStringWidth(name) > (this.dim.getWidth() - this.option.getControl().getMaxWidth())) {
            name = name.substring(0, Math.min(name.length(), 10)) + "...";
        }

        if (this.option.isAvailable()) {
            if (this.option.hasChanged()) {
                label = EnumChatFormatting.ITALIC + name + " *";
            } else {
                label = EnumChatFormatting.WHITE + name;
            }
        } else {
            label = String.valueOf(EnumChatFormatting.GRAY) + EnumChatFormatting.STRIKETHROUGH + name;
        }

        this.hovered = this.dim.containsCursor(mouseX, mouseY);


        this.drawRect(this.dim.getOriginX(), this.dim.getOriginY(), this.dim.getLimitX(), this.dim.getLimitY(), this.hovered ? 0xE0000000 : 0x90000000);
        this.drawString(label, this.dim.getOriginX() + 6, this.dim.getCenterY() - 4, 0xFFFFFFFF);
    }

    @Override
    public boolean isHovered() {
        return this.hovered;
    }

    @Override
    public Option<T> getOption() {
        return this.option;
    }

    @Override
    public Dim2i getDimensions() {
        return this.dim;
    }

}
