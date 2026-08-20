package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizon.gtnhlib.client.renderer.stacks.IStateStack;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.hooks.VanillaStateLayer;
import com.gtnewhorizons.angelica.glsm.states.DepthState;
import lombok.Setter;

public class DepthStateStack extends DepthState implements IStateStack<DepthStateStack> {

    protected final DepthState[] stack;

    protected int pointer;

    @Setter private VanillaStateLayer<DepthState> vanillaLayer;

    private final DepthState effective = new DepthState();

    public DepthStateStack() {
        stack = new DepthState[GLStateManager.MAX_ATTRIB_STACK_DEPTH];
        for (int i = 0; i < GLStateManager.MAX_ATTRIB_STACK_DEPTH; i++) {
            stack[i] = new DepthState();
        }
    }

    public DepthStateStack push() {
        if(pointer == stack.length) {
            throw new IllegalStateException("Stack overflow size " + (pointer + 1) + " reached");
        }

        VanillaStateLayer.capture(vanillaLayer, stack[pointer++].set(this));
        return this;
    }

    public DepthStateStack pop() {
        if(pointer == 0) {
            throw new IllegalStateException("Stack underflow");
        }

        final DepthState saved = stack[--pointer];
        if (VanillaStateLayer.restore(vanillaLayer, saved)) {
            setExceptMask(saved);
        } else {
            set(saved);
        }
        return this;
    }

    public boolean isEffectiveMaskEnabled() {
        if (!VanillaStateLayer.isHeld(vanillaLayer)) return isEnabled();
        vanillaLayer.readVanilla(effective);
        return effective.isEnabled();
    }

    public boolean isEmpty() {
        return pointer == 0;
    }

    public boolean topChanged() {
        return pointer > 0 && !sameAs(stack[pointer - 1]);
    }
}
