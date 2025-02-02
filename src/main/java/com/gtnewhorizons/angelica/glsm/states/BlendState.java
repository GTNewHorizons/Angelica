package com.gtnewhorizons.angelica.glsm.states;

import lombok.Getter;
import lombok.Setter;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import org.lwjgl.opengl.GL11;


@Getter @Setter
@Lwjgl3Aware
public class BlendState implements ISettableState<BlendState> {
    protected int srcRgb = GL11.GL_ONE;
    protected int dstRgb = GL11.GL_ZERO;
    protected int srcAlpha = GL11.GL_ONE;
    protected int dstAlpha = GL11.GL_ZERO;

    public BlendState() {}

    public BlendState(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        this.srcRgb = srcRgb;
        this.dstRgb = dstRgb;
        this.srcAlpha = srcAlpha;
        this.dstAlpha = dstAlpha;
    }

    @Override
    public BlendState set(BlendState state) {
        this.srcRgb = state.srcRgb;
        this.dstRgb = state.dstRgb;
        this.srcAlpha = state.srcAlpha;
        this.dstAlpha = state.dstAlpha;
        return this;
    }

    @Override
    public boolean sameAs(Object state) {
        if (this == state) return true;
        if (!(state instanceof BlendState blendState)) return false;
        return srcRgb == blendState.srcRgb && dstRgb == blendState.dstRgb && srcAlpha == blendState.srcAlpha && dstAlpha == blendState.dstAlpha;
    }

    @Override
    public BlendState copy() {
        return new BlendState().set(this);
    }
}
