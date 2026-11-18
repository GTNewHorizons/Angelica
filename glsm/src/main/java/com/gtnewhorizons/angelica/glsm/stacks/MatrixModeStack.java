package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.MatrixMode;

public final class MatrixModeStack extends MatrixMode implements CowStateStack<MatrixModeStack> {

    protected final MatrixMode[] stack;
    private final CowDepths cow = new CowDepths();

    public MatrixModeStack(int id) {
        cow.id = id;
        stack = new MatrixMode[GLStateManager.STATE_SLOTS];
        for (int i = 0; i < GLStateManager.STATE_SLOTS; i++) {
            stack[i] = new MatrixMode();
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
