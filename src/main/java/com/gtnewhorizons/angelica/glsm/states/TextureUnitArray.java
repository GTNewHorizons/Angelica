package com.gtnewhorizons.angelica.glsm.states;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.stacks.BooleanStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.TextureBindingStack;
import org.lwjgl.opengl.GL11;

public class TextureUnitArray {
    private TextureBindingStack[] bindings;
    private BooleanStateStack[] states;

    public TextureUnitArray() {
        bindings = new TextureBindingStack[GLStateManager.MAX_TEXTURE_UNITS];
        states = new BooleanStateStack[GLStateManager.MAX_TEXTURE_UNITS];
        for (int i = 0; i < GLStateManager.MAX_TEXTURE_UNITS; i++) {
            bindings[i] = new TextureBindingStack();
            states[i] = new BooleanStateStack(GL11.GL_TEXTURE_2D);
        }
    }

    public TextureBindingStack getTextureUnitBindings(int index) {
        return bindings[index];
    }

    public BooleanStateStack getTextureUnitStates(int index) {
        return states[index];
    }
}
