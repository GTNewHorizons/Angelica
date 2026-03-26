package com.gtnewhorizons.angelica.glsm.states;

import lombok.Getter;
import lombok.Setter;
import org.lwjgl.opengl.GL11;

/**
 * Tracks GL stencil buffer state.
 * Corresponds to GL_STENCIL_BUFFER_BIT attributes.
 * Supports separate front/back state (GL 2.0 glStencilFuncSeparate, glStencilOpSeparate, glStencilMaskSeparate).
 */
@Getter @Setter
public class StencilState implements ISettableState<StencilState> {
    // Front face state
    private int funcFront = GL11.GL_ALWAYS;
    private int refFront = 0;
    private int valueMaskFront = 0xFFFFFFFF;
    private int failOpFront = GL11.GL_KEEP;
    private int zFailOpFront = GL11.GL_KEEP;
    private int zPassOpFront = GL11.GL_KEEP;
    private int writeMaskFront = 0xFFFFFFFF;

    // Back face state (GL 2.0)
    private int funcBack = GL11.GL_ALWAYS;
    private int refBack = 0;
    private int valueMaskBack = 0xFFFFFFFF;
    private int failOpBack = GL11.GL_KEEP;
    private int zFailOpBack = GL11.GL_KEEP;
    private int zPassOpBack = GL11.GL_KEEP;
    private int writeMaskBack = 0xFFFFFFFF;

    // Clear value (shared)
    private int clearValue = 0;

    @Override
    public StencilState set(StencilState state) {
        this.funcFront = state.funcFront;
        this.refFront = state.refFront;
        this.valueMaskFront = state.valueMaskFront;
        this.failOpFront = state.failOpFront;
        this.zFailOpFront = state.zFailOpFront;
        this.zPassOpFront = state.zPassOpFront;
        this.writeMaskFront = state.writeMaskFront;

        this.funcBack = state.funcBack;
        this.refBack = state.refBack;
        this.valueMaskBack = state.valueMaskBack;
        this.failOpBack = state.failOpBack;
        this.zFailOpBack = state.zFailOpBack;
        this.zPassOpBack = state.zPassOpBack;
        this.writeMaskBack = state.writeMaskBack;

        this.clearValue = state.clearValue;
        return this;
    }

    /**
     * Set both front and back state at once (non-separate calls).
     */
    public void setFunc(int func, int ref, int mask) {
        this.funcFront = func;
        this.refFront = ref;
        this.valueMaskFront = mask;
        this.funcBack = func;
        this.refBack = ref;
        this.valueMaskBack = mask;
    }

    /**
     * Set both front and back operations at once (non-separate calls).
     */
    public void setOp(int fail, int zFail, int zPass) {
        this.failOpFront = fail;
        this.zFailOpFront = zFail;
        this.zPassOpFront = zPass;
        this.failOpBack = fail;
        this.zFailOpBack = zFail;
        this.zPassOpBack = zPass;
    }

    /**
     * Set both front and back write mask at once (non-separate calls).
     */
    public void setWriteMask(int mask) {
        this.writeMaskFront = mask;
        this.writeMaskBack = mask;
    }

    @Override
    public boolean sameAs(Object state) {
        if (this == state) return true;
        if (!(state instanceof StencilState stencilState)) return false;
        return funcFront == stencilState.funcFront
            && refFront == stencilState.refFront
            && valueMaskFront == stencilState.valueMaskFront
            && failOpFront == stencilState.failOpFront
            && zFailOpFront == stencilState.zFailOpFront
            && zPassOpFront == stencilState.zPassOpFront
            && writeMaskFront == stencilState.writeMaskFront
            && funcBack == stencilState.funcBack
            && refBack == stencilState.refBack
            && valueMaskBack == stencilState.valueMaskBack
            && failOpBack == stencilState.failOpBack
            && zFailOpBack == stencilState.zFailOpBack
            && zPassOpBack == stencilState.zPassOpBack
            && writeMaskBack == stencilState.writeMaskBack
            && clearValue == stencilState.clearValue;
    }

    @Override
    public StencilState copy() {
        return new StencilState().set(this);
    }
}
