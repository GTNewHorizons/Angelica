package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizon.gtnhlib.client.renderer.stacks.IStateStack;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.LineState;

/**
 * Stack for GL line state (width, stipple factor, stipple pattern).
 * Used by GL_LINE_BIT push/pop attrib.
 */
public class LineStateStack extends LineState implements IStateStack<LineStateStack> {

    protected final LineState[] stack;
    protected int pointer;

    public LineStateStack() {
        stack = new LineState[GLStateManager.MAX_ATTRIB_STACK_DEPTH];
        for (int i = 0; i < GLStateManager.MAX_ATTRIB_STACK_DEPTH; i++) {
            stack[i] = new LineState();
        }
    }

    public LineStateStack push() {
        if (pointer == stack.length) {
            throw new IllegalStateException("Stack overflow size " + (pointer + 1) + " reached");
        }
        stack[pointer++].set(this);
        return this;
    }

    public LineStateStack pop() {
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
