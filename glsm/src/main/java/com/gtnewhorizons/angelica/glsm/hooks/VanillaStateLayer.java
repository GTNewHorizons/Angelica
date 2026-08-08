package com.gtnewhorizons.angelica.glsm.hooks;

public interface VanillaStateLayer<T> {

    boolean isOverrideHeld();
    void readVanilla(T into);
    void writeVanilla(T from);
}
