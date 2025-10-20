package com.gtnewhorizons.angelica.glsm.states;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.stacks.BooleanStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.TextureBindingStack;
import lombok.Getter;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public class TextureUnitArray {
    private final TextureBindingStack[] bindings;
    private final BooleanStateStack[] states;
    private final BooleanStateStack[] texture1DStates;
    private final BooleanStateStack[] texture3DStates;
    private final BooleanStateStack[] texGenSStates;
    private final BooleanStateStack[] texGenTStates;
    private final BooleanStateStack[] texGenRStates;
    private final BooleanStateStack[] texGenQStates;
    @Getter
    public final Matrix4fStack[] textureMatricies;

    public TextureUnitArray() {
        bindings = new TextureBindingStack[GLStateManager.MAX_TEXTURE_UNITS];
        states = new BooleanStateStack[GLStateManager.MAX_TEXTURE_UNITS];
        texture1DStates = new BooleanStateStack[GLStateManager.MAX_TEXTURE_UNITS];
        texture3DStates = new BooleanStateStack[GLStateManager.MAX_TEXTURE_UNITS];
        texGenSStates = new BooleanStateStack[GLStateManager.MAX_TEXTURE_UNITS];
        texGenTStates = new BooleanStateStack[GLStateManager.MAX_TEXTURE_UNITS];
        texGenRStates = new BooleanStateStack[GLStateManager.MAX_TEXTURE_UNITS];
        texGenQStates = new BooleanStateStack[GLStateManager.MAX_TEXTURE_UNITS];
        textureMatricies = new Matrix4fStack[GLStateManager.MAX_TEXTURE_UNITS];

        for (int i = 0; i < GLStateManager.MAX_TEXTURE_UNITS; i++) {
            bindings[i] = new TextureBindingStack();
            states[i] = new BooleanStateStack(GL11.GL_TEXTURE_2D);
            texture1DStates[i] = new BooleanStateStack(GL11.GL_TEXTURE_1D);
            texture3DStates[i] = new BooleanStateStack(GL12.GL_TEXTURE_3D);
            texGenSStates[i] = new BooleanStateStack(GL11.GL_TEXTURE_GEN_S);
            texGenTStates[i] = new BooleanStateStack(GL11.GL_TEXTURE_GEN_T);
            texGenRStates[i] = new BooleanStateStack(GL11.GL_TEXTURE_GEN_R);
            texGenQStates[i] = new BooleanStateStack(GL11.GL_TEXTURE_GEN_Q);
            textureMatricies[i] = new Matrix4fStack(GLStateManager.MAX_TEXTURE_STACK_DEPTH);
        }
    }

    public TextureBindingStack getTextureUnitBindings(int index) {
        return bindings[index];
    }

    public BooleanStateStack getTextureUnitStates(int index) {
        return states[index];
    }

    public BooleanStateStack getTexture1DStates(int index) {
        return texture1DStates[index];
    }

    public BooleanStateStack getTexture3DStates(int index) {
        return texture3DStates[index];
    }

    public BooleanStateStack getTexGenSStates(int index) {
        return texGenSStates[index];
    }

    public BooleanStateStack getTexGenTStates(int index) {
        return texGenTStates[index];
    }

    public BooleanStateStack getTexGenRStates(int index) {
        return texGenRStates[index];
    }

    public BooleanStateStack getTexGenQStates(int index) {
        return texGenQStates[index];
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
            if (!texture1DStates[i].sameAs(textureUnitArray.texture1DStates[i])) return false;
            if (!texture3DStates[i].sameAs(textureUnitArray.texture3DStates[i])) return false;
            if (!texGenSStates[i].sameAs(textureUnitArray.texGenSStates[i])) return false;
            if (!texGenTStates[i].sameAs(textureUnitArray.texGenTStates[i])) return false;
            if (!texGenRStates[i].sameAs(textureUnitArray.texGenRStates[i])) return false;
            if (!texGenQStates[i].sameAs(textureUnitArray.texGenQStates[i])) return false;
        }
        return true;
    }

}
