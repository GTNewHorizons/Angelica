package com.gtnewhorizons.angelica.glsm.states;

public class ColorMask implements ISettableState<ColorMask> {
    public boolean red = true;
    public boolean green = true;
    public boolean blue = true;
    public boolean alpha = true;

    @Override
    public ColorMask set(ColorMask state) {
        this.red = state.red;
        this.green = state.green;
        this.blue = state.blue;
        this.alpha = state.alpha;

        return this;
    }
}
