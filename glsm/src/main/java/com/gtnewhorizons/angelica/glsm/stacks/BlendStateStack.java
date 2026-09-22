package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.StateSet;
import com.gtnewhorizons.angelica.glsm.hooks.VanillaStateLayer;
import com.gtnewhorizons.angelica.glsm.states.BlendState;
import lombok.Setter;

public final class BlendStateStack extends BlendState implements CowStateStack<BlendStateStack> {

    protected final BlendState[] stack;
    private final CowDepths cow = new CowDepths(StateSet.R_BLEND);

    private boolean funcUnknown;

    @Setter private VanillaStateLayer<BlendState> vanillaLayer;

    public BlendStateStack(int id) {
        cow.id = id;
        stack = new BlendState[GLStateManager.MAX_ATTRIB_STACK_DEPTH];
        for (int i = 0; i < GLStateManager.MAX_ATTRIB_STACK_DEPTH; i++) {
            stack[i] = new BlendState();
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
        final BlendState saved = stack[s];
        if (VanillaStateLayer.restore(vanillaLayer, saved)) {
            setExceptFunc(saved);
        } else {
            set(saved);
        }
    }

    @Override
    public boolean topSlotChanged() {
        return !cow.isEmpty() && !sameAs(stack[cow.top()]);
    }

    public void setFuncUnknownState() {
        funcUnknown = true;
    }

    public boolean isFuncUnknown() {
        return funcUnknown;
    }

    public void clearFuncUnknownState() {
        funcUnknown = false;
    }

    public BlendState readEffective(BlendState out) {
        out.set(this);
        VanillaStateLayer.capture(vanillaLayer, out);
        return out;
    }

    @Override
    public int stackId() {
        return cow.id;
    }
}
