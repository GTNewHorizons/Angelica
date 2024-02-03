package com.gtnewhorizons.angelica.glsm.states;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.stacks.BooleanStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.TextureBindingStack;
import lombok.Getter;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;

public class TextureUnitArray {
    private final TextureBindingStack[] bindings;
    private final BooleanStateStack[] states;
    @Getter
    public final Matrix4fStack[] textureMatricies;

    public TextureUnitArray() {
        bindings = new TextureBindingStack[GLStateManager.MAX_TEXTURE_UNITS];
        states = new BooleanStateStack[GLStateManager.MAX_TEXTURE_UNITS];
        textureMatricies = new Matrix4fStack[GLStateManager.MAX_TEXTURE_UNITS];

        for (int i = 0; i < GLStateManager.MAX_TEXTURE_UNITS; i++) {
            bindings[i] = new TextureBindingStack();
            states[i] = new BooleanStateStack(GL11.GL_TEXTURE_2D);
            textureMatricies[i] = new Matrix4fStack(GLStateManager.MAX_TEXTURE_STACK_DEPTH);
        }
    }

    public TextureBindingStack getTextureUnitBindings(int index) {
        return bindings[index];
    }

    public BooleanStateStack getTextureUnitStates(int index) {
        return states[index];
    }

    public Matrix4fStack getTextureUnitMatrix(int index) {
        return textureMatricies[index];
    }

    @Override
    public boolean equals(Object state) {
        if (this == state) return true;
        if (!(state instanceof TextureUnitArray textureUnitArray)) return false;
        for (int i = 0; i < GLStateManager.MAX_TEXTURE_UNITS; i++) {
            if (!bindings[i].sameAs(textureUnitArray.bindings[i])) return false;
            if (!states[i].sameAs(textureUnitArray.states[i])) return false;
        }
        return true;
    }

}
