package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizon.gtnhlib.client.renderer.stacks.IStateStack;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.hooks.VanillaStateLayer;
import com.gtnewhorizons.angelica.glsm.states.ColorMask;
import lombok.Setter;

public class ColorMaskStack extends ColorMask implements IStateStack<ColorMaskStack> {

    protected final ColorMask[] stack;

    protected int pointer;

    @Setter private VanillaStateLayer<ColorMask> vanillaLayer;

    public ColorMaskStack() {
        stack = new ColorMask[GLStateManager.MAX_ATTRIB_STACK_DEPTH];
        for (int i = 0; i < GLStateManager.MAX_ATTRIB_STACK_DEPTH; i++) {
            stack[i] = new ColorMask();
        }
    }

    public ColorMaskStack push() {
        if(pointer == stack.length) {
            throw new IllegalStateException("Stack overflow size " + (pointer + 1) + " reached");
        }

        VanillaStateLayer.capture(vanillaLayer, stack[pointer++].set(this));
        return this;
    }

    public ColorMaskStack pop() {
        if(pointer == 0) {
            throw new IllegalStateException("Stack underflow");
        }

        final ColorMask saved = stack[--pointer];
        if (!VanillaStateLayer.restore(vanillaLayer, saved)) {
            set(saved);
        }
        return this;
    }

    public boolean isEmpty() {
        return pointer == 0;
    }

    public boolean topChanged() {
        return pointer > 0 && !sameAs(stack[pointer - 1]);
    }
}
