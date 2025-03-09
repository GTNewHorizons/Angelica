package com.gtnewhorizons.angelica.client.gui.options.storage;

public interface OptionStorage<T> {
    T getData();

    void save();
}
