package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.Color4;

public class Color4Stack extends Color4 implements IStateStack<Color4Stack> {

    protected final Color4[] stack;

    protected int pointer;

    public Color4Stack() {
        stack = new Color4[GLStateManager.MAX_ATTRIB_STACK_DEPTH];
        for (int i = 0; i < GLStateManager.MAX_ATTRIB_STACK_DEPTH; i++) {
            stack[i] = new Color4();
        }
    }

    public Color4Stack(Color4 color4) {
        this();
        set(color4);
    }

    public Color4Stack push() {
        if(pointer == stack.length) {
            throw new IllegalStateException("Stack overflow size " + (pointer + 1) + " reached");
        }

        stack[pointer++].set(this);
        return this;
    }

    public Color4Stack pop() {
        if(pointer == 0) {
            throw new IllegalStateException("Stack underflow");
        }

        set(stack[--pointer]);
        return this;
    }

}
