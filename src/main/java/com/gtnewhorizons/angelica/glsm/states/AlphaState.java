package com.gtnewhorizons.angelica.glsm.states;

import lombok.Getter;
import lombok.Setter;
import org.lwjgl.opengl.GL11;

@Getter
public class AlphaState implements ISettableState<AlphaState> {
    @Setter protected int function = GL11.GL_ALWAYS;
    @Setter protected float reference = -1.0F;

    @Override
    public AlphaState set(AlphaState state) {
        this.function = state.function;
        this.reference = state.reference;
        return this;
    }

}
