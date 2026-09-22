package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.hooks.VanillaStateLayer;
import com.gtnewhorizons.angelica.glsm.states.AlphaState;
import lombok.Setter;

public final class AlphaStateStack extends AlphaState implements CowStateStack<AlphaStateStack> {

    protected final AlphaState[] stack;
    private final CowDepths cow = new CowDepths();

    @Setter private VanillaStateLayer<AlphaState> vanillaLayer;

    public AlphaStateStack(int id) {
        cow.id = id;
        stack = new AlphaState[GLStateManager.MAX_ATTRIB_STACK_DEPTH];
        for (int i = 0; i < GLStateManager.MAX_ATTRIB_STACK_DEPTH; i++) {
            stack[i] = new AlphaState();
        }
    }

    @Override
    public CowDepths cowDepths() {
        return cow;
    }

    @Override
    public void captureSlot(int s) {
        VanillaStateLayer.capture(vanillaLayer, stack[s].set(this));
    }

    @Override
    public void restoreSlot(int s) {
        final AlphaState saved = stack[s];
        if (!VanillaStateLayer.restore(vanillaLayer, saved)) {
            set(saved);
        }
    }

    @Override
    public boolean topSlotChanged() {
        return !cow.isEmpty() && !sameAs(stack[cow.top()]);
    }

    public AlphaState readEffective(AlphaState out) {
        out.set(this);
        VanillaStateLayer.capture(vanillaLayer, out);
        return out;
    }

    @Override
    public int stackId() {
        return cow.id;
    }
}
