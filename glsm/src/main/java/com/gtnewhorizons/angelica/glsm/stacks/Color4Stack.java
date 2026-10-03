package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.Color4;

public final class Color4Stack extends Color4 implements CowStateStack<Color4Stack> {

    protected final Color4[] stack;
    private CowDepths cow = new CowDepths();

    public Color4Stack(int id) {
        cow.id = id;
        stack = new Color4[GLStateManager.STATE_SLOTS];
        for (int i = 0; i < GLStateManager.STATE_SLOTS; i++) {
            stack[i] = new Color4();
        }
    }

    public Color4Stack(int id, Color4 color4) {
        this(id);
        set(color4);
    }

    public Color4Stack restoreBit(int bit) {
        final int id = cow.id;
        cow = new CowDepths(bit);
        cow.id = id;
        return this;
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
