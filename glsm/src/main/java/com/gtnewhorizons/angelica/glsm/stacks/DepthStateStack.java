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

        final DepthState slot = stack[pointer++].set(this);
        if (vanillaLayer != null && vanillaLayer.isOverrideHeld()) {
            vanillaLayer.readVanilla(slot);
        }
        return this;
    }

    public DepthStateStack pop() {
        if(pointer == 0) {
            throw new IllegalStateException("Stack underflow");
        }

        final DepthState saved = stack[--pointer];
        if (vanillaLayer != null && vanillaLayer.isOverrideHeld()) {
            vanillaLayer.writeVanilla(saved);
            this.func = saved.getFunc();
            this.clearValue = saved.getClearValue();
        } else {
            set(saved);
        }
        return this;
    }

    public boolean isEmpty() {
        return pointer == 0;
    }
}
