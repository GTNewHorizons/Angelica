package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizon.gtnhlib.client.renderer.stacks.IStateStack;
import com.gtnewhorizons.angelica.glsm.managers.GLAttribManager;
import com.gtnewhorizons.angelica.glsm.states.LightModelState;

public class LightModelStateStack extends LightModelState implements IStateStack<LightModelStateStack> {

    protected final LightModelState[] stack;

    protected int pointer;

    public LightModelStateStack() {
        super();
        stack = new LightModelState[GLAttribManager.MAX_ATTRIB_STACK_DEPTH];
        for (int i = 0; i < GLAttribManager.MAX_ATTRIB_STACK_DEPTH; i++) {
            stack[i] = new LightModelState();
        }
    }

    public LightModelStateStack push() {
        if (pointer == stack.length) {
            throw new IllegalStateException("Stack overflow size " + (pointer + 1) + " reached");
        }

        stack[pointer++].set(this);
        return this;
    }

    public LightModelStateStack pop() {
        if (pointer == 0) {
            throw new IllegalStateException("Stack underflow");
        }

        set(stack[--pointer]);
        return this;
    }

    public boolean isEmpty() { return pointer == 0; }
}
