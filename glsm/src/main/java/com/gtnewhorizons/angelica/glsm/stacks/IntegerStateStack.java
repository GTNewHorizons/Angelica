package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.IntegerState;

public final class IntegerStateStack extends IntegerState implements CowStateStack<IntegerStateStack> {

    protected final IntegerState[] stack;
    private CowDepths cow = new CowDepths();

    public IntegerStateStack(int val, int id) {
        cow.id = id;
        setValue(val);
        stack = new IntegerState[GLStateManager.MAX_ATTRIB_STACK_DEPTH];
        for (int i = 0; i < GLStateManager.MAX_ATTRIB_STACK_DEPTH; i++) {
            stack[i] = new IntegerState();
            stack[i].setValue(val);
        }
    }

    public IntegerStateStack restoreBit(int bit) {
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

    public IntegerStateStack push(int value) {
        push().setValue(value);
        return this;
    }

    @Override
    public int stackId() {
        return cow.id;
    }
}
