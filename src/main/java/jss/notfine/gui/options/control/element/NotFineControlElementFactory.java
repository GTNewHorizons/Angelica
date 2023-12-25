package jss.notfine.gui.options.control.element;

import me.jellysquid.mods.sodium.client.gui.options.Option;
import me.jellysquid.mods.sodium.client.gui.options.control.ControlElement;
import me.jellysquid.mods.sodium.client.gui.options.control.ControlValueFormatter;
import me.jellysquid.mods.sodium.client.gui.options.control.element.ControlElementFactory;
import me.jellysquid.mods.sodium.client.util.Dim2i;

public class NotFineControlElementFactory implements ControlElementFactory {

    @Override
    public <T extends Enum<T>> ControlElement cyclingControlElement(Option<T> option, Dim2i dim, T[] allowedValues, String[] names) {
        return new CyclingControlElement(option, dim, allowedValues, names);
    }

    @Override
    public ControlElement sliderControlElement(Option<Integer> option, Dim2i dim, int min, int max, int interval, ControlValueFormatter formatter) {
        return new SliderControlElement(option, dim, min, max, interval, formatter);
    }

    @Override
    public  ControlElement tickBoxElement(Option<Boolean> option, Dim2i dim) {
        return new TickBoxControlElement(option, dim);
    }

}
