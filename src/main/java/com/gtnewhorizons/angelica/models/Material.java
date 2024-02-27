package com.gtnewhorizons.angelica.models;

import org.antlr.v4.codegen.model.TestSetInline;

import java.util.BitSet;

public class Material {

    /**
     * The following masks control whether the property is enabled.
     */

    // If true, renders face as fullbright
    private static final int EMISSIVE_MASK = 1;
    private static final int COLOR_MASK = 1 << 1;
    // If true, uses diffuse shading/shadows
    private static final int DIFFUSE_MASK = 1 << 2;
    private static final int AO_MASK = 1 << 3;

    // By default, quads use diffuse shading and AO
    private static final int DEFAULTS = DIFFUSE_MASK | AO_MASK;

    private int flags = DEFAULTS;

    public void reset() {
        this.flags = DEFAULTS;
    }

    private void setMask(int mask, boolean val) {
        if (val)
            this.flags |= mask;
        else
            this.flags &= ~mask;
    }

    private boolean getMask(int mask) {
        return (this.flags & mask) == mask;
    }

    public void setAO(boolean val) {
        this.setMask(AO_MASK, val);
    }

    public boolean getAO() {
        return getMask(AO_MASK);
    }

    public void setDiffuse(boolean val) {
        this.setMask(DIFFUSE_MASK, val);
    }

    public boolean getDiffuse() {
        return getMask(DIFFUSE_MASK);
    }
}
