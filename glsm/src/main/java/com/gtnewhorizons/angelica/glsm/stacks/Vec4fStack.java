package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.joml.Vector4f;

/**
 * Stack for a Vector4f value (e.g. current texture coordinates).
 */
public final class Vec4fStack implements CowStateStack<Vec4fStack> {

    private final Vector4f value;
    private final float[][] stack;
    private final CowDepths cow = new CowDepths();

    public Vec4fStack(Vector4f value, int id) {
        this.value = value;
        cow.id = id;
        stack = new float[GLStateManager.MAX_ATTRIB_STACK_DEPTH][4];
    }

    @Override
    public CowDepths cowDepths() {
        return cow;
    }

    @Override
    public void captureSlot(int s) {
        stack[s][0] = value.x;
        stack[s][1] = value.y;
        stack[s][2] = value.z;
        stack[s][3] = value.w;
    }

    @Override
    public void restoreSlot(int s) {
        value.set(stack[s][0], stack[s][1], stack[s][2], stack[s][3]);
    }

    @Override
    public int stackId() {
        return cow.id;
    }
}
