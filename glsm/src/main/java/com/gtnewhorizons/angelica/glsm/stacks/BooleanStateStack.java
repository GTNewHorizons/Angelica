package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.hooks.VanillaBooleanLayer;
import com.gtnewhorizons.angelica.glsm.states.BooleanState;
import lombok.Setter;

/**
 * A stack for boolean GL state with lazy copy-on-write optimization.
 * <p>
 * When using the global attrib depth tracking (from glPushAttrib/glPopAttrib), state is only
 * saved when actually modified, dramatically reducing overhead when most states
 * don't change (e.g., GL_ENABLE_BIT saves ~270 states but typically only 3-4 change).
 */
public class BooleanStateStack extends BooleanState implements CowStateStack<BooleanStateStack> {

    protected final boolean[] stack;
    private final CowDepths cow = new CowDepths();
    @Setter private VanillaBooleanLayer vanillaLayer;

    public BooleanStateStack(int glCap) {
        this(glCap, false, false);
    }

    /**
     * Create a BooleanStateStack with a custom initial state.
     * Useful for GL states that default to true (e.g., GL_DITHER, GL_MULTISAMPLE).
     *
     * @param glCap GL capability constant
     * @param initialState initial enabled state
     */
    public BooleanStateStack(int glCap, boolean initialState) {
        this(glCap, initialState, false);
    }

    /**
     * Create a BooleanStateStack with custom initial state and FFP flag.
     *
     * @param glCap GL capability constant
     * @param initialState initial enabled state
     * @param ffpStateOnly when true, GL calls are skipped when FFP emulation is active
     */
    public BooleanStateStack(int glCap, boolean initialState, boolean ffpStateOnly) {
        super(glCap, ffpStateOnly);
        this.enabled = initialState;
        stack = new boolean[GLStateManager.MAX_ATTRIB_STACK_DEPTH];
    }

    @Override
    public CowDepths cowDepths() {
        return cow;
    }

    @Override
    public void captureSlot(int slot) {
        stack[slot] = vanillaValue();
    }

    @Override
    public void restoreSlot(int slot) {
        restore(stack[slot]);
    }

    @Override
    public int stackId() {
        return cow.id;
    }

    public boolean isEffectivelyEnabled() {
        return (vanillaLayer != null && vanillaLayer.isOverrideHeld()) ? vanillaLayer.getVanilla() : enabled;
    }

    private boolean vanillaValue() {
        return isEffectivelyEnabled();
    }

    private void restore(boolean value) {
        if (vanillaLayer != null && vanillaLayer.isOverrideHeld()) {
            vanillaLayer.setVanilla(value);
        } else {
            setEnabledDirect(value);
        }
    }

    @Override
    public int getDepth() {
        return GLStateManager.getAttribDepth();
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (GLStateManager.getAttribDepth() > 0
            && (stateUnknown || !GLStateManager.isCachingEnabled() || enabled != vanillaValue())) {
            beforeModify();
        }
        super.setEnabled(enabled);
    }

    private void setEnabledDirect(boolean enabled) {
        super.setEnabled(enabled);
    }
}
