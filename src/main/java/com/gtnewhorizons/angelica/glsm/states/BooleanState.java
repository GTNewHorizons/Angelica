package com.gtnewhorizons.angelica.glsm.states;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import lombok.Getter;
import org.lwjgl.opengl.GL11;

public class BooleanState implements ISettableState<BooleanState> {
    protected final int glCap;

    @Getter protected boolean enabled;

    public BooleanState(int glCap) {
        this.glCap = glCap;
    }

    public void disable() {
        this.setEnabled(false);
    }

    public void enable() {
        this.setEnabled(true);
    }

    public void setEnabled(boolean enabled) {
        final boolean bypass = GLStateManager.shouldBypassCache();
        if (bypass || enabled != this.enabled || (this.glCap == GL11.GL_BLEND && GLStateManager.vendorIsAMD() && GLStateManager.isPoppingAttributes())) {
            if (!bypass) {
                this.enabled = enabled;
            }
            // Skip actual GL calls during display list recording (state tracking only)
            if (!GLStateManager.isRecordingDisplayList()) {
                if (enabled) {
                    GL11.glEnable(this.glCap);
                } else {
                    GL11.glDisable(this.glCap);
                }
            }
        }
    }

    @Override
    public BooleanState set(BooleanState state) {
        this.enabled = state.enabled;
        return this;
    }

    @Override
    public boolean sameAs(Object state) {
        if (this == state) return true;
        if (!(state instanceof BooleanState booleanState)) return false;
        return enabled == booleanState.enabled;
    }

    @Override
    public BooleanState copy() {
        return new BooleanState(this.glCap).set(this);
    }
}
