package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.StateSet;
import com.gtnewhorizons.angelica.glsm.states.PolygonState;

public final class PolygonStateStack extends PolygonState implements CowStateStack<PolygonStateStack> {

    protected final PolygonState[] stack;
    private final CowDepths cow = new CowDepths(StateSet.R_POLYGON);

    public PolygonStateStack(int id) {
        cow.id = id;
        stack = new PolygonState[GLStateManager.STATE_SLOTS];
        for (int i = 0; i < GLStateManager.STATE_SLOTS; i++) {
            stack[i] = new PolygonState();
        }
    }

    @Override
    public CowDepths cowDepths() {
        return cow;
    }

    @Override
    public boolean slotChanged(int slot) {
        return !sameAs(stack[slot]);
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
