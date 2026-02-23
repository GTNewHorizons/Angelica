package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizon.gtnhlib.client.renderer.stacks.IStateStack;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.joml.Vector3f;

/**
 * Stack for a Vector3f value (e.g. current normal).
 */
public class Vec3fStack implements IStateStack<Vec3fStack> {

    private final Vector3f value;
    private final float[][] stack;
    private int pointer;

    public Vec3fStack(Vector3f value) {
        this.value = value;
        stack = new float[GLStateManager.MAX_ATTRIB_STACK_DEPTH][3];
    }

    @Override
    public Vec3fStack push() {
        if (pointer == stack.length) {
            throw new IllegalStateException("Stack overflow size " + (pointer + 1) + " reached");
        }
        stack[pointer][0] = value.x;
        stack[pointer][1] = value.y;
        stack[pointer][2] = value.z;
        pointer++;
        return this;
    }

    @Override
    public Vec3fStack pop() {
        if (pointer == 0) {
            throw new IllegalStateException("Stack underflow");
        }
        pointer--;
        value.set(stack[pointer][0], stack[pointer][1], stack[pointer][2]);
        return this;
    }

    @Override
    public boolean isEmpty() {
        return pointer == 0;
    }
}
