package com.gtnewhorizons.angelica.glsm.states;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import lombok.Getter;
import org.lwjgl.opengl.GL11;

public class BooleanState {
    private final int cap;

    @Getter
    private boolean enabled;

    public BooleanState(int cap) {
        this.cap = cap;
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
                GL11.glEnable(this.cap);
            } else {
                GL11.glDisable(this.cap);
            }
        }
    }
}
