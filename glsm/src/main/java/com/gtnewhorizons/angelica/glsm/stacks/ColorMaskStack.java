package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.StateSet;
import com.gtnewhorizons.angelica.glsm.hooks.VanillaStateLayer;
import com.gtnewhorizons.angelica.glsm.states.ColorMask;
import lombok.Setter;

public final class ColorMaskStack extends ColorMask implements CowStateStack<ColorMaskStack> {

    protected final ColorMask[] stack;
    private final CowDepths cow = new CowDepths(StateSet.R_COLOR_MASK);

    @Setter private VanillaStateLayer<ColorMask> vanillaLayer;

    public ColorMaskStack(int id) {
        cow.id = id;
        stack = new ColorMask[GLStateManager.STATE_SLOTS];
        for (int i = 0; i < GLStateManager.STATE_SLOTS; i++) {
            stack[i] = new ColorMask();
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
        final ColorMask saved = stack[s];
        if (!VanillaStateLayer.restore(vanillaLayer, saved)) {
            set(saved);
        }
    }

    public ColorMask readEffective(ColorMask out) {
        out.set(this);
        VanillaStateLayer.capture(vanillaLayer, out);
        return out;
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
