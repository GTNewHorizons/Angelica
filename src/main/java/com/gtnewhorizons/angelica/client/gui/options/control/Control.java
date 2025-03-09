package com.gtnewhorizons.angelica.client.gui.options.control;

import com.gtnewhorizons.angelica.client.gui.options.Option;
import com.gtnewhorizons.angelica.client.gui.options.control.element.ControlElementFactory;
import me.jellysquid.mods.sodium.client.util.Dim2i;

public interface Control<T> {
    Option<T> getOption();

    ControlElement<T> createElement(Dim2i dim, ControlElementFactory factory);

    int getMaxWidth();
}
