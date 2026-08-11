package jss.notfine.gui.options.control.element;

import me.jellysquid.mods.sodium.client.gui.options.Option;
import me.jellysquid.mods.sodium.client.util.Dim2i;
import net.minecraft.client.Minecraft;

public class CyclingControlElement<T extends Enum<T>> extends NotFineControlElement<T> {
    private final T[] allowedValues;
    private final String[] names;

    public CyclingControlElement(Option<T> option, Dim2i dim, T[] allowedValues, String[] names) {
        super(option, dim);
        this.allowedValues = allowedValues;
        this.names = names;
    }

    private int currentIndex() {
        for(int i = 0; i < allowedValues.length; ++i) {
            if(allowedValues[i] == option.getValue()) {
                return i;
            }
        }
        return 0;
    }

    @Override
    public String getLabel() {
        Enum<T> value = option.getValue();
        return super.getLabel() + formatValue(names[value.ordinal()]);
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        if(super.mousePressed(mc, mouseX, mouseY)) {
            option.setValue(allowedValues[(currentIndex() + 1) % allowedValues.length]);
            onOptionValueChanged();
            return true;
        } else {
            return false;
        }
    }

}
