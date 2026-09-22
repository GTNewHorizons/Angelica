package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.joml.Vector3f;

/**
 * Stack for a Vector3f value (e.g. current normal).
 */
public final class Vec3fStack implements CowStateStack<Vec3fStack> {

    private final Vector3f value;
    private final float[][] stack;
    private final CowDepths cow = new CowDepths();

    public Vec3fStack(Vector3f value, int id) {
        this.value = value;
        cow.id = id;
        stack = new float[GLStateManager.MAX_ATTRIB_STACK_DEPTH][3];
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
    }

    @Override
    public void restoreSlot(int s) {
        value.set(stack[s][0], stack[s][1], stack[s][2]);
    }

    @Override
    public int stackId() {
        return cow.id;
    }
}
