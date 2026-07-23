package jss.notfine.gui.options.control.element;

import jss.notfine.gui.GuiCustomMenu;
import me.jellysquid.mods.sodium.client.gui.SodiumGameOptions;
import me.jellysquid.mods.sodium.client.gui.options.Option;
import me.jellysquid.mods.sodium.client.gui.options.OptionFlag;
import me.jellysquid.mods.sodium.client.gui.options.control.ControlElement;
import me.jellysquid.mods.sodium.client.util.Dim2i;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.EnumChatFormatting;

import java.util.Collection;

public class NotFineControlElement<T> extends GuiButton implements ControlElement<T> {
    protected final Option<T> option;
    protected final Dim2i dim;

    public NotFineControlElement(Option<T> option, Dim2i dim) {
        super(-5, dim.getOriginX(), dim.getOriginY(), dim.getWidth(), dim.getHeight(), option.getName());
        this.option = option;
        this.dim = dim;
        enabled = option.isAvailable();
    }

    private static String disabledStyle() {
        return String.valueOf(EnumChatFormatting.GRAY) + EnumChatFormatting.STRIKETHROUGH;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        displayString = getLabel();
        //TODO: tooltips
        //hovered
        super.drawButton(mc, mouseX, mouseY);
    }

    public String getLabel() {
        String name = option.getName();
        String label;
        enabled = option.isAvailable();
        if (!enabled) {
            label = disabledStyle() + name;
        } else if (option.hasChanged()) {
            label = EnumChatFormatting.ITALIC + name;
        } else {
            label = name;
        }
        label += ": ";
        return label;
    }

    protected String formatValue(String value) {
        return option.isAvailable() ? value : disabledStyle() + value;
    }

    protected void onOptionValueChanged() {
        option.applyChanges();

        Collection<OptionFlag> flags = option.getFlags();
        Minecraft mc = Minecraft.getMinecraft();
        if (flags.contains(OptionFlag.REQUIRES_ASSET_RELOAD)) {
            SodiumGameOptions.applyAtlasSettings();
            mc.refreshResources();
        } else if (flags.contains(OptionFlag.REQUIRES_RENDERER_RELOAD)) {
            mc.renderGlobal.loadRenderers();
        }
        GuiCustomMenu.dirtyStorages.add(option.getStorage());
    }

    @Override
    public boolean isHovered() {
        return false;
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
