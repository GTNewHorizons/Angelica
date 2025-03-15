package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizon.gtnhlib.client.renderer.stacks.IStateStack;
import com.gtnewhorizons.angelica.glsm.managers.GLAttribManager;
import com.gtnewhorizons.angelica.glsm.states.IntegerState;

public class IntegerStateStack extends IntegerState implements IStateStack<IntegerStateStack> {

    protected final IntegerState[] stack;

    protected int pointer;

    public IntegerStateStack(int val) {
        setValue(val);
        stack = new IntegerState[GLAttribManager.MAX_ATTRIB_STACK_DEPTH];
        for (int i = 0; i < GLAttribManager.MAX_ATTRIB_STACK_DEPTH; i++) {
            stack[i] = new IntegerState();
            stack[i].setValue(val);
        }
    }

    public IntegerStateStack push() {
        if(pointer == stack.length) {
            throw new IllegalStateException("Stack overflow size " + (pointer + 1) + " reached");
        }

        stack[pointer++].set(this);
        return this;
    }

    public IntegerStateStack push(int value) {
        push().setValue(value);
        return this;
    }

    public IntegerStateStack pop() {
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
