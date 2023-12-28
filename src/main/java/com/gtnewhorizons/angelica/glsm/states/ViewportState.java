package com.gtnewhorizons.angelica.glsm.states;

import com.gtnewhorizons.angelica.glsm.stacks.IStackableState;

import java.nio.IntBuffer;

public class ViewportState implements IStackableState<ViewportState> {
    public int x;
    public int y;
    public int width;
    public int height;

    public void setViewPort(int x, int y, int width, int height) {
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

    @Override
    public ViewportState set(ViewportState state) {
        this.x = state.x;
        this.y = state.y;
        this.width = state.width;
        this.height = state.height;

        return this;
    }
}
