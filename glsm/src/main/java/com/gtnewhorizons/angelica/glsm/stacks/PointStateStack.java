package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizon.gtnhlib.client.renderer.stacks.IStateStack;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.PointState;

/**
 * Stack for GL point state (size).
 * Used by GL_POINT_BIT push/pop attrib.
 */
public class PointStateStack extends PointState implements IStateStack<PointStateStack> {

    protected final PointState[] stack;
    protected int pointer;

    public PointStateStack() {
        stack = new PointState[GLStateManager.MAX_ATTRIB_STACK_DEPTH];
        for (int i = 0; i < GLStateManager.MAX_ATTRIB_STACK_DEPTH; i++) {
            stack[i] = new PointState();
        }
    }

    public PointStateStack push() {
        if (pointer == stack.length) {
            throw new IllegalStateException("Stack overflow size " + (pointer + 1) + " reached");
        }
        stack[pointer++].set(this);
        return this;
    }

    public PointStateStack pop() {
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
