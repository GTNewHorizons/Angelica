package com.gtnewhorizons.angelica.glsm.states;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.angelica.glsm.GLStateManager;

import lombok.Getter;

/**
 * Tracks scissor test state
 */
public class ScissorState implements ISettableState<ScissorState> {
    @Getter private int x;
    @Getter private int y;
    @Getter private int width;
    @Getter private int height;

    public ScissorState() {
        this.x = 0;
        this.y = 0;
        this.width = 0;
        this.height = 0;
    }

    public void setScissor(int x, int y, int width, int height) {
        if (GLStateManager.shouldBypassCache() || this.x != x || this.y != y || this.width != width || this.height != height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            GL11.glScissor(x, y, width, height);
        }
    }

    @Override
    public ScissorState set(ScissorState state) {
        this.x = state.x;
        this.y = state.y;
        this.width = state.width;
        this.height = state.height;
        return this;
    }

    @Override
    public boolean sameAs(Object state) {
        if (this == state) return true;
        if (!(state instanceof ScissorState otherState)) return false;
        return this.x == otherState.x &&
               this.y == otherState.y &&
               this.width == otherState.width &&
               this.height == otherState.height;
    }

    @Override
    public ScissorState copy() {
        return new ScissorState().set(this);
    }
}
