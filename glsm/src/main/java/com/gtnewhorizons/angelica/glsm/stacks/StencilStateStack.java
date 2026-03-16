package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizon.gtnhlib.client.renderer.stacks.IStateStack;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.StencilState;

/**
 * Stack for GL stencil buffer state.
 * Used by GL_STENCIL_BUFFER_BIT push/pop attrib.
 */
public class StencilStateStack extends StencilState implements IStateStack<StencilStateStack> {

    protected final StencilState[] stack;
    protected int pointer;

    public StencilStateStack() {
        stack = new StencilState[GLStateManager.MAX_ATTRIB_STACK_DEPTH];
        for (int i = 0; i < GLStateManager.MAX_ATTRIB_STACK_DEPTH; i++) {
            stack[i] = new StencilState();
        }
    }

    public StencilStateStack push() {
        if (pointer == stack.length) {
            throw new IllegalStateException("Stack overflow size " + (pointer + 1) + " reached");
        }
        stack[pointer++].set(this);
        return this;
    }

    public StencilStateStack pop() {
        if (pointer == 0) {
            throw new IllegalStateException("Stack underflow");
        }
        set(stack[--pointer]);
        return this;
    }

    public boolean isEmpty() {
        return pointer == 0;
    }
}
