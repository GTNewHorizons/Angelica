package com.seibel.distanthorizons.common.wrappers.gui;

import com.seibel.distanthorizons.core.wrapperInterfaces.config.IConfigGui;

public class ClassicConfigGUI implements IConfigGui {
    public static ClassicConfigGUI INSTANCE = new ClassicConfigGUI();

    @Override
    public void addOnScreenChangeListener(Runnable newListener) {

    }

    @Override
    public void removeOnScreenChangeListener(Runnable oldListener) {

    }
}
