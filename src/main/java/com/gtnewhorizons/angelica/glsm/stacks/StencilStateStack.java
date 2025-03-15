package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizon.gtnhlib.client.renderer.stacks.IStateStack;
import com.gtnewhorizons.angelica.glsm.managers.GLAttribManager;
import com.gtnewhorizons.angelica.glsm.states.StencilState;

/**
 * Stack for tracking stencil test state
 */
public class StencilStateStack extends StencilState implements IStateStack<StencilStateStack> {
    private final StencilState[] stack;
    private int pointer;

    public StencilStateStack() {
        super();
        stack = new StencilState[GLAttribManager.MAX_ATTRIB_STACK_DEPTH];
        for (int i = 0; i < GLAttribManager.MAX_ATTRIB_STACK_DEPTH; i++) {
            stack[i] = new StencilState();
        }
    }

    @Override
    public StencilStateStack push() {
        if (pointer == stack.length) {
            throw new IllegalStateException("Stack overflow size " + (pointer + 1) + " reached");
        }

        stack[pointer++].set(this);
        return this;
    }

    @Override
    public StencilStateStack pop() {
        if (pointer == 0) {
            throw new IllegalStateException("Stack underflow");
        }

        set(stack[--pointer]);
        return this;
    }

    @Override
    public boolean isEmpty() {
        return pointer == 0;
    }
}
