package com.gtnewhorizons.angelica.glsm.states;

import com.gtnewhorizons.angelica.glsm.Dirty;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import lombok.Getter;
import org.lwjgl.opengl.GL11;

public class BooleanState {
    private final int cap;

    private final long dirtyFlag;

    @Getter
    private boolean enabled;

    public BooleanState(int cap) {
        this.cap = cap;
        this.dirtyFlag = Dirty.getFlagFromCap(this.cap);
    }

    public void disable() {
        this.setEnabled(false);
    }

    public void enable() {
        this.setEnabled(true);
    }

    public void setEnabled(boolean enabled) {
        if (GLStateManager.BYPASS_CACHE || enabled != this.enabled || GLStateManager.checkDirty(this.dirtyFlag)) {
            this.enabled = enabled;
            if (enabled) {
                GL11.glEnable(this.cap);
            } else {
                GL11.glDisable(this.cap);
            }
        }
    }
}
