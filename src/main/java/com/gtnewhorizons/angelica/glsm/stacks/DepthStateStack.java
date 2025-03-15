package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizon.gtnhlib.client.renderer.stacks.IStateStack;
import com.gtnewhorizons.angelica.glsm.managers.GLAttribManager;
import com.gtnewhorizons.angelica.glsm.states.DepthState;

public class DepthStateStack extends DepthState implements IStateStack<DepthStateStack> {

    protected final DepthState[] stack;

    protected int pointer;

    public DepthStateStack() {
        stack = new DepthState[GLAttribManager.MAX_ATTRIB_STACK_DEPTH];
        for (int i = 0; i < GLAttribManager.MAX_ATTRIB_STACK_DEPTH; i++) {
            stack[i] = new DepthState();
        }
    }

    public DepthStateStack push() {
        if(pointer == stack.length) {
            throw new IllegalStateException("Stack overflow size " + (pointer + 1) + " reached");
        }

        stack[pointer++].set(this);
        return this;
    }

    public DepthStateStack pop() {
        if(pointer == 0) {
            throw new IllegalStateException("Stack underflow");
        }

        set(stack[--pointer]);
        return this;
    }

    public boolean isEmpty() {
        return pointer == 0;
    }
}
