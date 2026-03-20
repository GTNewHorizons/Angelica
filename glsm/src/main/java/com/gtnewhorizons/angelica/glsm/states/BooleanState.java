package com.gtnewhorizons.angelica.glsm.states;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import lombok.Getter;
import org.lwjgl.opengl.GL11;

public class BooleanState implements ISettableState<BooleanState> {
    protected final int glCap;
    protected final boolean ffpStateOnly;

    @Getter protected boolean enabled;
    protected boolean stateUnknown;

    public BooleanState(int glCap) {
        this(glCap, false);
    }

    public BooleanState(int glCap, boolean ffpStateOnly) {
        this.glCap = glCap;
        this.ffpStateOnly = ffpStateOnly;
    }

    public void setUnknownState() {
        stateUnknown = true;
    }

    public void disable() {
        this.setEnabled(false);
    }

    public void enable() {
        this.setEnabled(true);
    }

    public void setEnabled(boolean enabled) {
        final boolean bypass = GLStateManager.shouldBypassCache();
        if (stateUnknown || bypass || enabled != this.enabled || (this.glCap == GL11.GL_BLEND && GLStateManager.vendorIsAMD() && GLStateManager.isPoppingAttributes())) {
            stateUnknown = false;
            if (!bypass) {
                this.enabled = enabled;
            }
            if (!ffpStateOnly) {
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
