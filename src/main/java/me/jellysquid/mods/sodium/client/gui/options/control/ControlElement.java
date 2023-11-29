package me.jellysquid.mods.sodium.client.gui.options.control;

import me.jellysquid.mods.sodium.client.gui.options.Option;
import me.jellysquid.mods.sodium.client.util.Dim2i;

public interface ControlElement<T> {
    public boolean isHovered();
    public Option<T> getOption();
    public Dim2i getDimensions();

}
