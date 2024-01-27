package com.gtnewhorizons.angelica.glsm.states;

import lombok.Getter;
import lombok.Setter;

import java.nio.FloatBuffer;

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

    public void get(FloatBuffer params) {
        params.put(0, red);
        params.put(1, green);
        params.put(2, blue);
        params.put(3, alpha);
    }

    @Override
    public boolean sameAs(Object state) {
        if (this == state) return true;
        if (!(state instanceof Color4 color4)) return false;
        return Float.compare(color4.red, red) == 0 && Float.compare(color4.green, green) == 0 && Float.compare(color4.blue, blue) == 0 && Float.compare(color4.alpha, alpha) == 0;
    }
    @Override
    public Color4 copy() {
        return new Color4().set(this);
    }
}
