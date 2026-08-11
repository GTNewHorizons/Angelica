package com.gtnewhorizons.angelica.glsm.states;

import lombok.Getter;

public class ImageUnitBinding implements ISettableState<ImageUnitBinding> {
    @Getter protected int texture;
    @Getter protected int level;
    @Getter protected boolean layered;
    @Getter protected int layer;
    @Getter protected int access;
    @Getter protected int format;

    public void setBinding(int texture, int level, boolean layered, int layer, int access, int format) {
        this.texture = texture;
        this.level = level;
        this.layered = layered;
        this.layer = layer;
        this.access = access;
        this.format = format;
    }

    @Override
    public ImageUnitBinding set(ImageUnitBinding state) {
        this.texture = state.texture;
        this.level = state.level;
        this.layered = state.layered;
        this.layer = state.layer;
        this.access = state.access;
        this.format = state.format;
        return this;
    }

    @Override
    public boolean sameAs(Object state) {
        if (this == state) return true;
        if (!(state instanceof ImageUnitBinding other)) return false;
        return texture == other.texture && level == other.level && layered == other.layered && layer == other.layer && access == other.access && format == other.format;
    }

    @Override
    public ImageUnitBinding copy() {
        return new ImageUnitBinding().set(this);
    }
}
