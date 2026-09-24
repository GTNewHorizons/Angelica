package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.StateSet;
import com.gtnewhorizons.angelica.glsm.states.ViewportState;

public final class ViewPortStateStack extends ViewportState implements CowStateStack<ViewPortStateStack> {

    protected final ViewportState[] stack;
    private final CowDepths cow = new CowDepths(StateSet.R_VIEWPORT);

    public ViewPortStateStack(int id) {
        cow.id = id;
        stack = new ViewportState[GLStateManager.MAX_ATTRIB_STACK_DEPTH];
        for (int i = 0; i < GLStateManager.MAX_ATTRIB_STACK_DEPTH; i++) {
            stack[i] = new ViewportState();
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
    public boolean topSlotChanged() {
        return !cow.isEmpty() && !sameAs(stack[cow.top()]);
    }

    @Override
    public int stackId() {
        return cow.id;
    }
}
