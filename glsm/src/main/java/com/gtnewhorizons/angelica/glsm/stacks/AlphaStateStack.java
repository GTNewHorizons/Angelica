package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizon.gtnhlib.client.renderer.stacks.IStateStack;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.hooks.VanillaStateLayer;
import com.gtnewhorizons.angelica.glsm.states.AlphaState;
import lombok.Setter;

public class AlphaStateStack extends AlphaState implements IStateStack<AlphaState> {

    protected final AlphaState[] stack;

    protected int pointer;

    @Setter private VanillaStateLayer<AlphaState> vanillaLayer;

    public AlphaStateStack() {
        stack = new AlphaState[GLStateManager.MAX_ATTRIB_STACK_DEPTH];
        for (int i = 0; i < GLStateManager.MAX_ATTRIB_STACK_DEPTH; i++) {
            stack[i] = new AlphaState();
        }
    }

    public AlphaStateStack push() {
        if(pointer == stack.length) {
            throw new IllegalStateException("Stack overflow size " + (pointer + 1) + " reached");
        }

        VanillaStateLayer.capture(vanillaLayer, stack[pointer++].set(this));
        return this;
    }

    public AlphaStateStack pop() {
        if(pointer == 0) {
            throw new IllegalStateException("Stack underflow");
        }

        final AlphaState saved = stack[--pointer];
        if (!VanillaStateLayer.restore(vanillaLayer, saved)) {
            set(saved);
        }
        return this;
    }

    public AlphaState readEffective(AlphaState out) {
        out.set(this);
        VanillaStateLayer.capture(vanillaLayer, out);
        return out;
    }

    public boolean isEmpty() {
        return pointer == 0;
    }
}
