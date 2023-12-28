package com.gtnewhorizons.angelica.glsm.states;

import com.gtnewhorizons.angelica.glsm.Dirty;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import lombok.Getter;
import org.lwjgl.opengl.GL11;

public class BooleanState implements ISettableState<BooleanState> {
    private final int glCap;

    private final long dirtyFlag;

    @Getter
    private boolean enabled;

    public BooleanState(int glCap) {
        this.glCap = glCap;
        this.dirtyFlag = Dirty.getFlagFromCap(this.glCap);
    }

    public void disable() {
        this.setEnabled(false);
    }

    public void enable() {
        this.setEnabled(true);
    }

    public void setEnabled(boolean enabled) {
        if (GLStateManager.BYPASS_CACHE || enabled != this.enabled) {
            this.enabled = enabled;
            if (enabled) {
                GL11.glEnable(this.glCap);
            } else {
                GL11.glDisable(this.glCap);
            }
        }
    }

    @Override
    public BooleanState set(BooleanState state) {
        this.enabled = state.enabled;
        return this;
    }
}
