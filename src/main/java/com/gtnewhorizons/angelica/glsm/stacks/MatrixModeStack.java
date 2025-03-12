package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizon.gtnhlib.client.renderer.stacks.IStateStack;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.MatrixMode;

public class MatrixModeStack extends MatrixMode implements IStateStack<MatrixModeStack> {

    protected final MatrixMode[] stack;

    protected int pointer;

    public MatrixModeStack() {
        stack = new MatrixMode[GLStateManager.MAX_ATTRIB_STACK_DEPTH];
        for (int i = 0; i < GLStateManager.MAX_ATTRIB_STACK_DEPTH; i++) {
            stack[i] = new MatrixMode();
        }
    }

    public MatrixModeStack push() {
        if(pointer == stack.length) {
            throw new IllegalStateException("Stack overflow size " + (pointer + 1) + " reached");
        }

        stack[pointer++].set(this);
        return this;
    }

    public MatrixModeStack pop() {
        if(pointer == 0) {
            throw new IllegalStateException("Stack underflow");
        }

        set(stack[--pointer]);
        return this;
    }

    public MatrixModeStack clear() {
        pointer = 0;
        return this;
    }

    public boolean isEmpty() {
        return pointer == 0;
    }
}
