package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizon.gtnhlib.client.renderer.stacks.IStateStack;
import com.gtnewhorizons.angelica.glsm.managers.GLAttribManager;
import com.gtnewhorizons.angelica.glsm.states.ScissorState;

/**
 * Stack for tracking scissor test state
 */
public class ScissorStateStack extends ScissorState implements IStateStack<ScissorStateStack> {
    private final ScissorState[] stack;
    private int pointer;

    public ScissorStateStack() {
        super();
        stack = new ScissorState[GLAttribManager.MAX_ATTRIB_STACK_DEPTH];
        for (int i = 0; i < GLAttribManager.MAX_ATTRIB_STACK_DEPTH; i++) {
            stack[i] = new ScissorState();
        }
    }

    @Override
    public ScissorStateStack push() {
        if (pointer == stack.length) {
            throw new IllegalStateException("Stack overflow size " + (pointer + 1) + " reached");
        }

        stack[pointer++].set(this);
        return this;
    }

    @Override
    public ScissorStateStack pop() {
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
