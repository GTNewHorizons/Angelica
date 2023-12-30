package com.gtnewhorizons.angelica.glsm.states;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class Color4 implements ISettableState<Color4> {
    protected float red = 1.0F;
    protected float green = 1.0F;
    protected float blue = 1.0F;
    protected float alpha = 1.0F;

    public Color4() {
    }

    public Color4(float red, float green, float blue, float alpha) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
    }

    @Override
    public Color4 set(Color4 state) {
        this.red = state.red;
        this.green = state.green;
        this.blue = state.blue;
        this.alpha = state.alpha;

        return this;
    }
}
