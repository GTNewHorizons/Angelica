package com.gtnewhorizons.angelica.client.gui.options.control.element;

import com.gtnewhorizons.angelica.client.gui.options.Option;
import com.gtnewhorizons.angelica.client.gui.options.control.ControlElement;
import com.gtnewhorizons.angelica.client.gui.options.control.ControlValueFormatter;
import me.jellysquid.mods.sodium.client.util.Dim2i;

public interface ControlElementFactory {

    <T extends Enum<T>> ControlElement cyclingControlElement(Option<T> option, Dim2i dim, T[] allowedValues, String[] names);

    ControlElement sliderControlElement(Option<Integer> option, Dim2i dim, int min, int max, int interval, ControlValueFormatter formatter);

    ControlElement tickBoxElement(Option<Boolean> option, Dim2i dim);

}
