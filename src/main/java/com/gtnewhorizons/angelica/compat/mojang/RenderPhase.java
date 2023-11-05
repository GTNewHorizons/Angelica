package com.gtnewhorizons.angelica.compat.mojang;

import javax.annotation.Nullable;

public abstract class RenderPhase {
    protected final String name;
    protected Runnable setupState;
    protected final Runnable clearState;

    public RenderPhase(String name, Runnable setupState, Runnable clearState) {
        this.name = name;
        this.setupState = setupState;
        this.clearState = clearState;
    }

    public boolean equals(@Nullable Object object) {
        if (this == object) {
            return true;
        } else if (object != null && this.getClass() == object.getClass()) {
            final RenderPhase lv = (RenderPhase)object;
            return this.name.equals(lv.name);
        } else {
            return false;
        }
    }
    public int hashCode() {
        return this.name.hashCode();
    }

    public String toString() {
        return this.name;
    }

    public void setupRenderState() {
        this.setupState.run();
    }

    public void clearRenderState() {
        this.clearState.run();
    }
}
