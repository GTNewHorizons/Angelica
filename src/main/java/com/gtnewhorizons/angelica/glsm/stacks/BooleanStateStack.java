package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizon.gtnhlib.client.renderer.stacks.IStateStack;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.BooleanState;

/**
 * A stack for boolean GL state with lazy copy-on-write optimization.
 * <p>
 * When using the global attrib depth tracking (from glPushAttrib/glPopAttrib), state is only
 * saved when actually modified, dramatically reducing overhead when most states
 * don't change (e.g., GL_ENABLE_BIT saves ~270 states but typically only 3-4 change).
 */
public class BooleanStateStack extends BooleanState implements IStateStack<BooleanStateStack> {

    protected final boolean[] stack;

    /**
     * The depth at which state has been saved. Compared against GLStateManager.getAttribDepth()
     * to determine if we need to save before modification or restore on pop.
     */
    protected int savedDepth;

    public BooleanStateStack(int glCap) {
        // Most booleans default to false
        this(glCap, false);
    }

    /**
     * Create a BooleanStateStack with a custom initial state.
     * Useful for GL states that default to true (e.g., GL_DITHER, GL_MULTISAMPLE).
     *
     * @param glCap GL capability constant
     * @param initialState initial enabled state
     */
    public BooleanStateStack(int glCap, boolean initialState) {
        super(glCap);
        this.enabled = initialState;
        stack = new boolean[GLStateManager.MAX_ATTRIB_STACK_DEPTH];
    }

    // ==================== Traditional Stack Operations ====================

    @Override
    public BooleanStateStack push() {
        if (savedDepth >= stack.length) {
            throw new IllegalStateException("Stack overflow size " + (savedDepth + 1) + " reached");
        }
        stack[savedDepth++] = enabled;
        return this;
    }

    @Override
    public BooleanStateStack pop() {
        if (savedDepth == 0) {
            throw new IllegalStateException("Stack underflow");
        }
        final boolean oldValue = stack[--savedDepth];
        // Call setEnabledDirect to avoid triggering beforeModify during restore
        setEnabledDirect(oldValue);
        return this;
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
            final boolean oldValue = stack[--savedDepth];
            setEnabledDirect(oldValue);
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
            stack[savedDepth++] = enabled;
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
