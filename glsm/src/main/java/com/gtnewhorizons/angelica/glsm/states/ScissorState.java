package com.gtnewhorizons.angelica.glsm.states;

import java.nio.IntBuffer;

public class ScissorState implements ISettableState<ScissorState> {
    public int x;
    public int y;
    public int width;
    public int height;

    public void setScissor(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void get(IntBuffer params) {
        params.put(0, x);
        params.put(1, y);
        params.put(2, width);
        params.put(3, height);
    }

    public void get(int[] params) {
        params[0] = x;
        params[1] = y;
        params[2] = width;
        params[3] = height;
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
        if (!(state instanceof ScissorState scissorState)) return false;
        return x == scissorState.x && y == scissorState.y && width == scissorState.width && height == scissorState.height;
    }

    @Override
    public ScissorState copy() {
        return new ScissorState().set(this);
    }

}
