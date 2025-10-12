package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizon.gtnhlib.client.renderer.stacks.IStateStack;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.BooleanState;
import org.lwjgl.opengl.GL11;

public class BooleanStateStack extends BooleanState implements IStateStack<BooleanStateStack> {

    protected final BooleanState[] stack;

    protected int pointer;

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
        stack = new BooleanState[GLStateManager.MAX_ATTRIB_STACK_DEPTH];
        for (int i = 0; i < GLStateManager.MAX_ATTRIB_STACK_DEPTH; i++) {
            stack[i] = new BooleanState(glCap);
        }
    }

    public BooleanStateStack push() {
        if(pointer == stack.length) {
            throw new IllegalStateException("Stack overflow size " + (pointer + 1) + " reached");
        }

        stack[pointer++].set(this);
        return this;
    }

    public BooleanStateStack pop() {
        if(pointer == 0) {
            throw new IllegalStateException("Stack underflow");
        }

        set(stack[--pointer]);
        if (GLStateManager.isAMD() && glCap == GL11.GL_BLEND) {
            setEnabled(enabled);
        }
        return this;
    }

    public boolean isEmpty() {
        return pointer == 0;
    }
}
