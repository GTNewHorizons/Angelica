package com.gtnewhorizons.angelica.glsm.states;

import java.nio.IntBuffer;

public class ViewportState {
    public int x;
    public int y;
    public int width;
    public int height;

    public void set(int x, int y, int width, int height) {
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
}
