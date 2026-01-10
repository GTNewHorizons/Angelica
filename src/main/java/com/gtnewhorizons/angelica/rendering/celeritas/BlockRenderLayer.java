package com.gtnewhorizons.angelica.rendering.celeritas;

public enum BlockRenderLayer {
    SOLID,       // vanilla pass 0
    TRANSLUCENT; // vanilla pass 1

    private static final BlockRenderLayer[] VALUES = values();

    public static BlockRenderLayer fromVanillaPass(int pass) {
        return VALUES[pass];
    }
}
