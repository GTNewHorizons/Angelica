package jss.notfine.gui.options.control.element;

import com.gtnewhorizons.angelica.client.gui.options.Option;
import me.jellysquid.mods.sodium.client.util.Dim2i;
import net.minecraft.client.Minecraft;

public class CyclingControlElement<T extends Enum<T>> extends NotFineControlElement<T> {
    private final T[] allowedValues;
    private final String[] names;
    private int currentIndex = 0;

    public CyclingControlElement(Option<T> option, Dim2i dim, T[] allowedValues, String[] names) {
        super(option, dim);
        this.allowedValues = allowedValues;
        this.names = names;

        for(int i = 0; i < allowedValues.length; ++i) {
            if(allowedValues[i] == option.getValue()) {
                currentIndex = i;
                break;
            }
        }
    }

    public String getLabel() {
        Enum<T> value = option.getValue();
        return super.getLabel() + names[value.ordinal()];
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        if(super.mousePressed(mc, mouseX, mouseY)) {
            currentIndex = (option.getValue().ordinal() + 1) % allowedValues.length;
            option.setValue(allowedValues[currentIndex]);
            onOptionValueChanged();
            return true;
        } else {
            return false;
        }
    }

}
