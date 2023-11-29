package jss.notfine.gui.options.control.element;

import me.jellysquid.mods.sodium.client.gui.options.Option;
import me.jellysquid.mods.sodium.client.util.Dim2i;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;

public class TickBoxControlElement extends NotFineControlElement<Boolean> {
    public TickBoxControlElement(Option<Boolean> option, Dim2i dim) {
        super(option, dim);
    }

    public String getLabel() {
        return super.getLabel() + I18n.format(option.getValue() ? "options.on" : "options.off");
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        if(super.mousePressed(mc, mouseX, mouseY)) {
            option.setValue(!option.getValue());
            onOptionValueChanged();
            return true;
        } else {
            return false;
        }
    }

}
