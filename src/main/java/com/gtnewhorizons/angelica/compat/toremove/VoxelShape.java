package com.gtnewhorizons.angelica.compat.toremove;

@Deprecated
public enum VoxelShape {

    FULL_CUBE(false),
    EMPTY(true);

    private final boolean empty;

    VoxelShape(boolean empty) {
        this.empty = empty;
    }

    public boolean isEmpty() {
        return empty;
    }
}