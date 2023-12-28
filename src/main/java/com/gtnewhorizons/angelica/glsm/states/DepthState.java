package com.gtnewhorizons.angelica.glsm.states;

import com.gtnewhorizons.angelica.glsm.stacks.IStackableState;
import lombok.Getter;
import lombok.Setter;
import org.lwjgl.opengl.GL11;

@Getter @Setter
public class DepthState implements IStackableState<DepthState> {
    protected boolean mask = true;
    protected int func = GL11.GL_LESS;

    @Override
    public DepthState set(DepthState state) {
        this.mask = state.mask;
        this.func = state.func;
        return this;
    }
}
