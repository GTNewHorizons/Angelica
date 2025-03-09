package com.gtnewhorizons.angelica.client.gui.options.control;

import com.gtnewhorizons.angelica.client.gui.options.Option;
import me.jellysquid.mods.sodium.client.util.Dim2i;

public interface ControlElement<T> {
    boolean isHovered();
    Option<T> getOption();
    Dim2i getDimensions();

}
