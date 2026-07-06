package com.gtnewhorizons.angelica.glsm.states;

import java.nio.IntBuffer;

public class ViewportState implements ISettableState<ViewportState> {
    public int x;
    public int y;
    public int width;
    public int height;
    public double depthRangeNear = 0.0;
    public double depthRangeFar = 1.0;

    public void setViewPort(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setDepthRange(double near, double far) {
        this.depthRangeNear = near;
        this.depthRangeFar = far;
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
        this.depthRangeNear = state.depthRangeNear;
        this.depthRangeFar = state.depthRangeFar;

        return this;
    }

    @Override
    public boolean sameAs(Object state) {
        if (this == state) return true;
        if (!(state instanceof ViewportState viewportState)) return false;
        return x == viewportState.x && y == viewportState.y && width == viewportState.width && height == viewportState.height
            && Double.compare(depthRangeNear, viewportState.depthRangeNear) == 0
            && Double.compare(depthRangeFar, viewportState.depthRangeFar) == 0;
    }

    @Override
    public ViewportState copy() {
        return new ViewportState().set(this);
    }

}
