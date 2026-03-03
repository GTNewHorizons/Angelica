package com.gtnewhorizons.angelica.glsm.states;

import lombok.Getter;
import lombok.Setter;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;


@Getter @Setter
public class BlendState implements ISettableState<BlendState> {
    protected int srcRgb = GL11.GL_ONE;
    protected int dstRgb = GL11.GL_ZERO;
    protected int srcAlpha = GL11.GL_ONE;
    protected int dstAlpha = GL11.GL_ZERO;
    protected int equationRgb = GL14.GL_FUNC_ADD;
    protected int equationAlpha = GL14.GL_FUNC_ADD;
    protected float blendColorR = 0.0f;
    protected float blendColorG = 0.0f;
    protected float blendColorB = 0.0f;
    protected float blendColorA = 0.0f;

    public BlendState() {}

    public BlendState(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        this.srcRgb = srcRgb;
        this.dstRgb = dstRgb;
        this.srcAlpha = srcAlpha;
        this.dstAlpha = dstAlpha;
    }

    public void setAll(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        this.srcRgb = srcRgb;
        this.dstRgb = dstRgb;
        this.srcAlpha = srcAlpha;
        this.dstAlpha = dstAlpha;
    }

    public void setSrcDstRgb(int srcRgb, int dstRgb) {
        this.srcRgb = srcRgb;
        this.dstRgb = dstRgb;
    }

    @Override
    public BlendState set(BlendState state) {
        this.srcRgb = state.srcRgb;
        this.dstRgb = state.dstRgb;
        this.srcAlpha = state.srcAlpha;
        this.dstAlpha = state.dstAlpha;
        this.equationRgb = state.equationRgb;
        this.equationAlpha = state.equationAlpha;
        this.blendColorR = state.blendColorR;
        this.blendColorG = state.blendColorG;
        this.blendColorB = state.blendColorB;
        this.blendColorA = state.blendColorA;
        return this;
    }

    @Override
    public boolean sameAs(Object state) {
        if (this == state) return true;
        if (!(state instanceof BlendState blendState)) return false;
        return srcRgb == blendState.srcRgb && dstRgb == blendState.dstRgb
            && srcAlpha == blendState.srcAlpha && dstAlpha == blendState.dstAlpha
            && equationRgb == blendState.equationRgb && equationAlpha == blendState.equationAlpha
            && Float.compare(blendColorR, blendState.blendColorR) == 0
            && Float.compare(blendColorG, blendState.blendColorG) == 0
            && Float.compare(blendColorB, blendState.blendColorB) == 0
            && Float.compare(blendColorA, blendState.blendColorA) == 0;
    }

    @Override
    public BlendState copy() {
        return new BlendState().set(this);
    }
}
