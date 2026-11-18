package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.StateSet;
import com.gtnewhorizons.angelica.glsm.hooks.VanillaStateLayer;
import com.gtnewhorizons.angelica.glsm.states.DepthState;
import lombok.Setter;

public final class DepthStateStack extends DepthState implements CowStateStack<DepthStateStack> {

    protected final DepthState[] stack;
    private final CowDepths cow = new CowDepths(StateSet.R_DEPTH);

    @Setter private VanillaStateLayer<DepthState> vanillaLayer;

    private final DepthState effective = new DepthState();

    public DepthStateStack(int id) {
        cow.id = id;
        stack = new DepthState[GLStateManager.STATE_SLOTS];
        for (int i = 0; i < GLStateManager.STATE_SLOTS; i++) {
            stack[i] = new DepthState();
        }
    }

    @Override
    public CowDepths cowDepths() {
        return cow;
    }

    @Override
    public boolean slotChanged(int slot) {
        return VanillaStateLayer.isHeld(vanillaLayer) || !sameAs(stack[slot]);
    }

    @Override
    public void captureSlot(int s) {
        VanillaStateLayer.capture(vanillaLayer, stack[s].set(this));
    }

    @Override
    public void restoreSlot(int s) {
        final DepthState saved = stack[s];
        if (VanillaStateLayer.restore(vanillaLayer, saved)) {
            setExceptMask(saved);
        } else {
            set(saved);
        }
    }

    @Override
    public boolean topSlotChanged() {
        return !cow.isEmpty() && !sameAs(stack[cow.top()]);
    }

    public boolean isEffectiveMaskEnabled() {
        if (!VanillaStateLayer.isHeld(vanillaLayer)) return isEnabled();
        vanillaLayer.readVanilla(effective);
        return effective.isEnabled();
    }

    @Override
    public int stackId() {
        return cow.id;
    }
}
