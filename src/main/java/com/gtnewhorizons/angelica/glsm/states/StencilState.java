package com.gtnewhorizons.angelica.glsm.states;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.angelica.glsm.GLStateManager;

import lombok.Getter;

/**
 * Tracks stencil test state
 */
public class StencilState implements ISettableState<StencilState> {
    @Getter private int func;
    @Getter private int ref;
    @Getter private int mask;
    @Getter private int sfail;
    @Getter private int dpfail;
    @Getter private int dppass;
    @Getter private int stencilMask;

    public StencilState() {
        this.func = GL11.GL_ALWAYS;
        this.ref = 0;
        this.mask = 0xFFFFFFFF;
        this.sfail = GL11.GL_KEEP;
        this.dpfail = GL11.GL_KEEP;
        this.dppass = GL11.GL_KEEP;
        this.stencilMask = 0xFFFFFFFF;
    }

    public void setFunc(int func, int ref, int mask) {
        if (GLStateManager.shouldBypassCache() || this.func != func || this.ref != ref || this.mask != mask) {
            this.func = func;
            this.ref = ref;
            this.mask = mask;
            GL11.glStencilFunc(func, ref, mask);
        }
    }

    public void setOp(int sfail, int dpfail, int dppass) {
        if (GLStateManager.shouldBypassCache() || this.sfail != sfail || this.dpfail != dpfail || this.dppass != dppass) {
            this.sfail = sfail;
            this.dpfail = dpfail;
            this.dppass = dppass;
            GL11.glStencilOp(sfail, dpfail, dppass);
        }
    }

    public void setMask(int mask) {
        if (GLStateManager.shouldBypassCache() || this.stencilMask != mask) {
            this.stencilMask = mask;
            GL11.glStencilMask(mask);
        }
    }

    @Override
    public StencilState set(StencilState state) {
        this.func = state.func;
        this.ref = state.ref;
        this.mask = state.mask;
        this.sfail = state.sfail;
        this.dpfail = state.dpfail;
        this.dppass = state.dppass;
        this.stencilMask = state.stencilMask;
        return this;
    }

    @Override
    public boolean sameAs(Object state) {
        if (this == state) return true;
        if (!(state instanceof StencilState otherState)) return false;
        return this.func == otherState.func &&
               this.ref == otherState.ref &&
               this.mask == otherState.mask &&
               this.sfail == otherState.sfail &&
               this.dpfail == otherState.dpfail &&
               this.dppass == otherState.dppass &&
               this.stencilMask == otherState.stencilMask;
    }

    @Override
    public StencilState copy() {
        return new StencilState().set(this);
    }
}
