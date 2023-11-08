package com.gtnewhorizons.angelica.compat.mojang;

import javax.annotation.Nullable;

public abstract class RenderPhase {
    protected final String name;
    protected Runnable beingAction;
    protected final Runnable endAction;

    public RenderPhase(String name, Runnable beingAction, Runnable clearState) {
        this.name = name;
        this.beingAction = beingAction;
        this.endAction = clearState;
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

    public void startDrawing() {
        this.beingAction.run();
    }

    public void endDrawing() {
        this.endAction.run();
    }

}
