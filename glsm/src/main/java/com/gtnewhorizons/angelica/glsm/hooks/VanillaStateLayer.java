package com.gtnewhorizons.angelica.glsm.hooks;

public interface VanillaStateLayer<T> {

    boolean isOverrideHeld();
    void readVanilla(T into);
    void writeVanilla(T from);

    static boolean isHeld(VanillaStateLayer<?> layer) {
        return layer != null && layer.isOverrideHeld();
    }

    static <T> void capture(VanillaStateLayer<T> layer, T slot) {
        if (isHeld(layer)) layer.readVanilla(slot);
    }

    static <T> boolean restore(VanillaStateLayer<T> layer, T saved) {
        if (!isHeld(layer)) return false;
        layer.writeVanilla(saved);
        return true;
    }
}
