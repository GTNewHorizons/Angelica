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

        final BlendState slot = stack[pointer++].set(this);
        if (vanillaLayer != null && vanillaLayer.isOverrideHeld()) {
            vanillaLayer.readVanilla(slot);
        }
        return this;
    }

    public BlendStateStack pop() {
        if(pointer == 0) {
            throw new IllegalStateException("Stack underflow");
        }

        final BlendState saved = stack[--pointer];
        if (vanillaLayer != null && vanillaLayer.isOverrideHeld()) {
            vanillaLayer.writeVanilla(saved);
            this.equationRgb = saved.getEquationRgb();
            this.equationAlpha = saved.getEquationAlpha();
            this.blendColorR = saved.getBlendColorR();
            this.blendColorG = saved.getBlendColorG();
            this.blendColorB = saved.getBlendColorB();
            this.blendColorA = saved.getBlendColorA();
        } else {
            set(saved);
        }
        return this;
    }

    public BlendState readEffective(BlendState out) {
        out.set(this);
        if (vanillaLayer != null && vanillaLayer.isOverrideHeld()) {
            vanillaLayer.readVanilla(out);
        }
        return out;
    }

    public boolean isEmpty() {
        return pointer == 0;
    }
}
