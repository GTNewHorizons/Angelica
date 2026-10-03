package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.MaterialState;

public final class MaterialStateStack extends MaterialState implements CowStateStack<MaterialStateStack> {

    protected final MaterialState[] stack;
    private final CowDepths cow = new CowDepths();

    public MaterialStateStack(int face, int id) {
        super(face);
        cow.id = id;
        stack = new MaterialState[GLStateManager.STATE_SLOTS];
        for (int i = 0; i < GLStateManager.STATE_SLOTS; i++) {
            stack[i] = new MaterialState(face);
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
    public int stackId() {
        return cow.id;
    }
}
