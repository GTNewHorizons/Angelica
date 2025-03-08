package com.gtnewhorizons.angelica.glsm.states;

import lombok.Getter;
import lombok.Setter;
import org.lwjgl.opengl.GL11;

@Getter @Setter
public class DepthState implements ISettableState<DepthState> {
    protected boolean enabled = true;
    protected int func = GL11.GL_LESS;

    @Override
    public DepthState set(DepthState state) {
        this.enabled = state.enabled;
        this.func = state.func;
        return this;
    }

    @Override
    public boolean sameAs(Object state) {
        if (this == state) return true;
        if (!(state instanceof DepthState depthState)) return false;
        return enabled == depthState.enabled && func == depthState.func;
    }
    @Override
    public DepthState copy() {
        return new DepthState().set(this);
    }

}
