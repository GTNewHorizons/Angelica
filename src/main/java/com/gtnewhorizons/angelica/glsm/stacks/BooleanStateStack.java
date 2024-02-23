package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.BooleanState;

public class BooleanStateStack extends BooleanState implements IStateStack<BooleanStateStack> {

    protected final BooleanState[] stack;

    protected int pointer;

    public BooleanStateStack(int glCap) {
        super(glCap);
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
        return this;
    }

}
