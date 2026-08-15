package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizon.gtnhlib.client.renderer.stacks.IStateStack;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.hooks.VanillaStateLayer;
import com.gtnewhorizons.angelica.glsm.states.BlendState;
import lombok.Setter;

public class BlendStateStack extends BlendState implements IStateStack<BlendStateStack> {

    protected final BlendState[] stack;

    protected int pointer;

    @Setter private VanillaStateLayer<BlendState> vanillaLayer;

    public BlendStateStack() {
        stack = new BlendState[GLStateManager.MAX_ATTRIB_STACK_DEPTH];
        for (int i = 0; i < GLStateManager.MAX_ATTRIB_STACK_DEPTH; i++) {
            stack[i] = new BlendState();
        }
    }

    public BlendStateStack push() {
        if(pointer == stack.length) {
            throw new IllegalStateException("Stack overflow size " + (pointer + 1) + " reached");
        }

        VanillaStateLayer.capture(vanillaLayer, stack[pointer++].set(this));
        return this;
    }

    public BlendStateStack pop() {
        if(pointer == 0) {
            throw new IllegalStateException("Stack underflow");
        }

        final BlendState saved = stack[--pointer];
        if (VanillaStateLayer.restore(vanillaLayer, saved)) {
            setExceptFunc(saved);
        } else {
            set(saved);
        }
        return this;
    }

    public BlendState readEffective(BlendState out) {
        out.set(this);
        VanillaStateLayer.capture(vanillaLayer, out);
        return out;
    }

    public boolean isEmpty() {
        return pointer == 0;
    }

    public boolean topChanged() {
        return pointer > 0 && !sameAs(stack[pointer - 1]);
    }
}
