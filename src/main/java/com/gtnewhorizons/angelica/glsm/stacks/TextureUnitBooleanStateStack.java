package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * BooleanStateStack for per-texture-unit state.
 * Switches to correct unit (raw GL) before enable/disable calls.
 */
public class TextureUnitBooleanStateStack extends BooleanStateStack {
    private final int unitIndex;

    public TextureUnitBooleanStateStack(int glCap, int unitIndex) {
        super(glCap);
        this.unitIndex = unitIndex;
    }

    public TextureUnitBooleanStateStack(int glCap, int unitIndex, boolean initialState) {
        super(glCap, initialState);
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

            int currentUnit = GLStateManager.getActiveTextureUnit();
            boolean needsSwitch = currentUnit != unitIndex;

            if (needsSwitch) GL13.glActiveTexture(GL13.GL_TEXTURE0 + unitIndex);
            if (enabled) GL11.glEnable(glCap); else GL11.glDisable(glCap);
            if (needsSwitch) GL13.glActiveTexture(GL13.GL_TEXTURE0 + currentUnit);
        }
    }

    public int getUnitIndex() {
        return unitIndex;
    }
}
