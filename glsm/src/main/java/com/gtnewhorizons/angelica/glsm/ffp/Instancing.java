package com.gtnewhorizons.angelica.glsm.ffp;

public enum Instancing {
    NONE(""),
    TEMPLATE("_instanced"),
    CUBE("_cubeinstanced"),
    PARTICLE("_particleinstanced");

    public static final Instancing[] VALUES = values();

    public final String variantSuffix;

    Instancing(String variantSuffix) {
        this.variantSuffix = variantSuffix;
    }

    public boolean hasInstanceHead() {
        return this == TEMPLATE || this == CUBE;
    }
}
