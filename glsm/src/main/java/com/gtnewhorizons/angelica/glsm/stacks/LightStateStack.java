package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.LightState;

public final class LightStateStack extends LightState implements CowStateStack<LightStateStack> {

    protected final LightState[] stack;
    private final CowDepths cow = new CowDepths();

    public LightStateStack(int light, int id) {
        super(light);
        cow.id = id;
        stack = new LightState[GLStateManager.MAX_ATTRIB_STACK_DEPTH];
        for (int i = 0; i < GLStateManager.MAX_ATTRIB_STACK_DEPTH; i++) {
            stack[i] = new LightState(light);
        }
    }

    @Override
    public CowDepths cowDepths() {
        return cow;
    }

    @Override
    public void captureSlot(int s) {
        stack[s].set(this);
    }

    @Override
    public void restoreSlot(int s) {
        set(stack[s]);
    }

    @Override
    public int stackId() {
        return cow.id;
    }
}
