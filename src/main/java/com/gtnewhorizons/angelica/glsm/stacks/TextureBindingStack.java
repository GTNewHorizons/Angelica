package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.TextureBinding;

public class TextureBindingStack extends TextureBinding implements IStateStack<TextureBindingStack> {

    protected final TextureBinding[] stack;

    protected int pointer;

    public TextureBindingStack() {
        stack = new TextureBinding[GLStateManager.MAX_ATTRIB_STACK_DEPTH];
        for (int i = 0; i < GLStateManager.MAX_ATTRIB_STACK_DEPTH; i++) {
            stack[i] = new TextureBinding();
        }
    }

    public TextureBindingStack push() {
        if(pointer == stack.length) {
            throw new IllegalStateException("Stack overflow size " + (pointer + 1) + " reached");
        }

        stack[pointer++].set(this);
        return this;
    }

    public TextureBindingStack pop() {
        if(pointer == 0) {
            throw new IllegalStateException("Stack underflow");
        }

        set(stack[--pointer]);
        return this;
    }

    public TextureBinding peek() {
        return stack[pointer];
    }
}
