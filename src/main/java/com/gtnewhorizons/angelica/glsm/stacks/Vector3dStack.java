package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.joml.Vector3d;

public class Vector3dStack extends Vector3d implements IStateStack<Vector3dStack> {
    protected final Vector3d[] stack;

    protected int pointer;

    public Vector3dStack() {
        stack = new Vector3d[GLStateManager.MAX_ATTRIB_STACK_DEPTH];
        for (int i = 0; i < GLStateManager.MAX_ATTRIB_STACK_DEPTH; i++) {
            stack[i] = new Vector3d();
        }
    }

    @Override
    public Vector3dStack push() {
        if(pointer == stack.length) {
            throw new IllegalStateException("Stack overflow size " + (pointer + 1) + " reached");
        }
        stack[pointer++].set(this);
        return this;
    }

    @Override
    public Vector3dStack pop() {
        if(pointer == 0) {
            throw new IllegalStateException("Stack underflow");
        }
        set(stack[--pointer]);
        return this;
    }
}
