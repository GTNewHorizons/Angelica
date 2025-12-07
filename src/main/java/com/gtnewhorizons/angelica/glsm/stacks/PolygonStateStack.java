package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizon.gtnhlib.client.renderer.stacks.IStateStack;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.PolygonState;

/**
 * Stack for GL polygon state (mode, offset, cull face mode, front face).
 * Used by GL_POLYGON_BIT push/pop attrib.
 */
public class PolygonStateStack extends PolygonState implements IStateStack<PolygonStateStack> {

    protected final PolygonState[] stack;
    protected int pointer;

    public PolygonStateStack() {
        stack = new PolygonState[GLStateManager.MAX_ATTRIB_STACK_DEPTH];
        for (int i = 0; i < GLStateManager.MAX_ATTRIB_STACK_DEPTH; i++) {
            stack[i] = new PolygonState();
        }
    }

    public PolygonStateStack push() {
        if (pointer == stack.length) {
            throw new IllegalStateException("Stack overflow size " + (pointer + 1) + " reached");
        }
        stack[pointer++].set(this);
        return this;
    }

    public PolygonStateStack pop() {
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
