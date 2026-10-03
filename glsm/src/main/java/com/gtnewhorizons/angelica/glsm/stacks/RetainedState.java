package com.gtnewhorizons.angelica.glsm.stacks;

public final class RetainedState {
    public int[] ids = new int[64];
    public int count;
    public int program;
    public long programGen;

    public void ensureCapacity(int capacity) {
        if (ids.length < capacity) ids = new int[capacity];
    }
}
