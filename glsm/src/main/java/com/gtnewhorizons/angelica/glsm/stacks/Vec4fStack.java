package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizon.gtnhlib.client.renderer.stacks.IStateStack;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.joml.Vector4f;

/**
 * Stack for a Vector4f value (e.g. current texture coordinates).
 */
public class Vec4fStack implements IStateStack<Vec4fStack> {

    private final Vector4f value;
    private final float[][] stack;
    private int pointer;

    public Vec4fStack(Vector4f value) {
        this.value = value;
        stack = new float[GLStateManager.MAX_ATTRIB_STACK_DEPTH][4];
    }

    @Override
    public Vec4fStack push() {
        if (pointer == stack.length) {
            throw new IllegalStateException("Stack overflow size " + (pointer + 1) + " reached");
        }
        stack[pointer][0] = value.x;
        stack[pointer][1] = value.y;
        stack[pointer][2] = value.z;
        stack[pointer][3] = value.w;
        pointer++;
        return this;
    }

    @Override
    public Vec4fStack pop() {
        if (pointer == 0) {
            throw new IllegalStateException("Stack underflow");
        }
        pointer--;
        value.set(stack[pointer][0], stack[pointer][1], stack[pointer][2], stack[pointer][3]);
        return this;
    }

    @Override
    public boolean isEmpty() {
        return pointer == 0;
    }
}
