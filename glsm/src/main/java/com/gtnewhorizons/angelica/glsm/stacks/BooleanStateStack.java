package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizon.gtnhlib.client.renderer.stacks.IStateStack;
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
public class BooleanStateStack extends BooleanState implements IStateStack<BooleanStateStack> {

    protected final boolean[] stack;
    @Setter private VanillaBooleanLayer vanillaLayer;

    /**
     * The depth at which state has been saved. Compared against GLStateManager.getAttribDepth()
     * to determine if we need to save before modification or restore on pop.
     */
    protected int savedDepth;

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

    // ==================== Traditional Stack Operations ====================

    @Override
    public BooleanStateStack push() {
        if (savedDepth >= stack.length) {
            throw new IllegalStateException("Stack overflow size " + (savedDepth + 1) + " reached");
        }
        stack[savedDepth++] = vanillaValue();
        return this;
    }

    @Override
    public BooleanStateStack pop() {
        if (savedDepth == 0) {
            throw new IllegalStateException("Stack underflow");
        }
        restore(stack[--savedDepth]);
        return this;
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
    public boolean isEmpty() {
        return savedDepth == 0;
    }

    // ==================== Lazy Copy-on-Write Operations ====================

    @Override
    public int pushDepth() {
        // No-op: global depth is managed by GLStateManager
        return GLStateManager.getAttribDepth();
    }

    /**
     * Restore state if it was modified at the current depth.
     * Only called by GLStateManager.popState() for states that registered as modified.
     */
    @Override
    public BooleanStateStack popDepth() {
        // We're only called if we were modified, so savedDepth should match
        if (savedDepth > 0) {
            restore(stack[--savedDepth]);
        }
        return this;
    }

    /**
     * Called before modifying state. If we haven't saved at the current depth yet,
     * save the current value and register with GLStateManager for restoration.
     */
    @Override
    public void beforeModify() {
        final int globalDepth = GLStateManager.getAttribDepth();
        if (savedDepth < globalDepth) {
            stack[savedDepth++] = vanillaValue();
            GLStateManager.registerModifiedState(this);
        }
    }

    @Override
    public int getDepth() {
        return GLStateManager.getAttribDepth();
    }

    @Override
    public void setEnabled(boolean enabled) {
        beforeModify();
        super.setEnabled(enabled);
    }

    private void setEnabledDirect(boolean enabled) {
        super.setEnabled(enabled);
    }
}
