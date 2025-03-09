package com.gtnewhorizons.angelica.client.gui.options;

import com.gtnewhorizons.angelica.client.gui.options.control.Control;
import com.gtnewhorizons.angelica.client.gui.options.storage.OptionStorage;

import java.util.Collection;

public interface Option<T> {
    String getName();

    String getTooltip();

    OptionImpact getImpact();

    Control<T> getControl();

    T getValue();

    void setValue(T value);

    void reset();

    OptionStorage<?> getStorage();

    boolean isAvailable();

    boolean hasChanged();

    void applyChanges();

    Collection<OptionFlag> getFlags();

}
