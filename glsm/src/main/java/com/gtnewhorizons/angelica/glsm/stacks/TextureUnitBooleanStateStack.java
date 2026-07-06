package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import org.lwjgl.opengl.GL13;

/**
 * BooleanStateStack for per-texture-unit state.
 * Switches to correct unit (raw GL) before enable/disable calls.
 */
public class TextureUnitBooleanStateStack extends BooleanStateStack {
    private final int unitIndex;

    public TextureUnitBooleanStateStack(int glCap, int unitIndex) {
        super(glCap, false, true); // All texture enable/disable states are FFP-only
        this.unitIndex = unitIndex;
    }

    public TextureUnitBooleanStateStack(int glCap, int unitIndex, boolean initialState) {
        super(glCap, initialState, true);
        this.unitIndex = unitIndex;
    }

    @Override
    public void setEnabled(boolean enabled) {
        beforeModify();
        setEnabledWithUnitSwitch(enabled);
    }

    @Override
    public TextureUnitBooleanStateStack popDepth() {
        if (savedDepth > 0) {
            final boolean oldValue = stack[--savedDepth];
            setEnabledWithUnitSwitch(oldValue);
        }
        return this;
    }

    private void setEnabledWithUnitSwitch(boolean enabled) {
        final boolean bypass = GLStateManager.shouldBypassCache();
        if (bypass || enabled != this.enabled) {
            if (!bypass) this.enabled = enabled;

            if (!ffpStateOnly) {
                final int currentUnit = GLStateManager.getActiveTextureUnit();
                final boolean needsSwitch = currentUnit != unitIndex;

                if (needsSwitch) BackendManager.RENDER_BACKEND.activeTexture(GL13.GL_TEXTURE0 + unitIndex);
                if (enabled) BackendManager.RENDER_BACKEND.enable(glCap); else BackendManager.RENDER_BACKEND.disable(glCap);
                if (needsSwitch) BackendManager.RENDER_BACKEND.activeTexture(GL13.GL_TEXTURE0 + currentUnit);
            }
        }
    }

    public int getUnitIndex() {
        return unitIndex;
    }
}
