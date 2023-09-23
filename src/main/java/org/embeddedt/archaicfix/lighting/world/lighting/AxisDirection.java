package org.embeddedt.archaicfix.lighting.world.lighting;

public enum AxisDirection {
    POSITIVE(1),
    NEGATIVE(-1);

    private final int off;
    AxisDirection(int off) {
        this.off = off;
    }

    public int getOffset() {
        return off;
    }

}
