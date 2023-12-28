package com.gtnewhorizons.angelica.glsm.states;

import com.gtnewhorizons.angelica.glsm.stacks.IStackableState;
import lombok.Getter;
import lombok.Setter;
import org.lwjgl.opengl.GL11;

@Getter
public class AlphaState implements IStackableState<AlphaState> {
    @Setter protected int function = GL11.GL_ALWAYS;
    @Setter protected float reference = -1.0F;

    @Override
    public AlphaState set(AlphaState state) {
        this.function = state.function;
        this.reference = state.reference;
        return this;
    }

}
