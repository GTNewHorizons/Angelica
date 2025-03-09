package jss.notfine.gui.options.control.element;

import com.gtnewhorizons.angelica.client.gui.options.Option;
import com.gtnewhorizons.angelica.client.gui.options.control.ControlElement;
import com.gtnewhorizons.angelica.client.gui.options.control.ControlValueFormatter;
import com.gtnewhorizons.angelica.client.gui.options.control.element.ControlElementFactory;
import me.jellysquid.mods.sodium.client.util.Dim2i;

public class NotFineControlElementFactory implements ControlElementFactory {

    @Override
    public <T extends Enum<T>> ControlElement<T> cyclingControlElement(Option<T> option, Dim2i dim, T[] allowedValues, String[] names) {
        return new CyclingControlElement<>(option, dim, allowedValues, names);
    }

    @Override
    public ControlElement<Integer> sliderControlElement(Option<Integer> option, Dim2i dim, int min, int max, int interval, ControlValueFormatter formatter) {
        return new SliderControlElement(option, dim, min, max, interval, formatter);
    }

    @Override
    public  ControlElement<Boolean> tickBoxElement(Option<Boolean> option, Dim2i dim) {
        return new TickBoxControlElement(option, dim);
    }

}
