package com.gtnewhorizons.angelica.glsm.states;

import com.gtnewhorizon.gtnhlib.client.renderer.stacks.IStateStack;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.stacks.StackIdAllocator;
import com.gtnewhorizons.angelica.glsm.stacks.TextureBindingStack;
import com.gtnewhorizons.angelica.glsm.stacks.TextureUnitBooleanStateStack;
import lombok.Getter;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.util.ArrayList;
import java.util.List;

public class TextureUnitArray {
    private final List<IStateStack<?>> idStacks = new ArrayList<>();
    private final TextureBindingStack[] bindings;
    private final TextureUnitBooleanStateStack[] states;
    private final TextureUnitBooleanStateStack[] texture1DStates;
    private final TextureUnitBooleanStateStack[] texture3DStates;
    private final TextureUnitBooleanStateStack[] texGenSStates;
    private final TextureUnitBooleanStateStack[] texGenTStates;
    private final TextureUnitBooleanStateStack[] texGenRStates;
    private final TextureUnitBooleanStateStack[] texGenQStates;
    private final TexGenState[] texGenStates;
    private final TexEnvState[] texEnvStates;
    @Getter
    public final Matrix4fStack[] textureMatricies;

    public int texMatIdentityMask;
    public int texMatIdentityGen;

    public TextureUnitArray() {
        bindings = new TextureBindingStack[GLStateManager.MAX_TEXTURE_UNITS];
        states = new TextureUnitBooleanStateStack[GLStateManager.MAX_TEXTURE_UNITS];
        texture1DStates = new TextureUnitBooleanStateStack[GLStateManager.MAX_TEXTURE_UNITS];
        texture3DStates = new TextureUnitBooleanStateStack[GLStateManager.MAX_TEXTURE_UNITS];
        texGenSStates = new TextureUnitBooleanStateStack[GLStateManager.MAX_TEXTURE_UNITS];
        texGenTStates = new TextureUnitBooleanStateStack[GLStateManager.MAX_TEXTURE_UNITS];
        texGenRStates = new TextureUnitBooleanStateStack[GLStateManager.MAX_TEXTURE_UNITS];
        texGenQStates = new TextureUnitBooleanStateStack[GLStateManager.MAX_TEXTURE_UNITS];
        texGenStates = new TexGenState[GLStateManager.MAX_TEXTURE_UNITS];
        texEnvStates = new TexEnvState[GLStateManager.MAX_TEXTURE_UNITS];
        textureMatricies = new Matrix4fStack[GLStateManager.MAX_TEXTURE_UNITS];

        for (int i = 0; i < GLStateManager.MAX_TEXTURE_UNITS; i++) {
            bindings[i] = member(new TextureBindingStack(StackIdAllocator.nextId(), i));
            states[i] = track(new TextureUnitBooleanStateStack(GL11.GL_TEXTURE_2D, i));
            texture1DStates[i] = track(new TextureUnitBooleanStateStack(GL11.GL_TEXTURE_1D, i));
            texture3DStates[i] = track(new TextureUnitBooleanStateStack(GL12.GL_TEXTURE_3D, i));
            texGenSStates[i] = track(new TextureUnitBooleanStateStack(GL11.GL_TEXTURE_GEN_S, i));
            texGenTStates[i] = track(new TextureUnitBooleanStateStack(GL11.GL_TEXTURE_GEN_T, i));
            texGenRStates[i] = track(new TextureUnitBooleanStateStack(GL11.GL_TEXTURE_GEN_R, i));
            texGenQStates[i] = track(new TextureUnitBooleanStateStack(GL11.GL_TEXTURE_GEN_Q, i));
            texGenStates[i] = new TexGenState();
            texEnvStates[i] = member(new TexEnvState(StackIdAllocator.nextId()));
            textureMatricies[i] = new Matrix4fStack(GLStateManager.MAX_TEXTURE_STACK_DEPTH);
        }
    }

    private <T extends TextureUnitBooleanStateStack> T track(T s) {
        s.setStackId(StackIdAllocator.nextId());
        idStacks.add(s);
        return s;
    }

    private <T extends IStateStack<?>> T member(T s) {
        idStacks.add(s);
        return s;
    }

    public List<IStateStack<?>> idStacks() {
        return idStacks;
    }

    public TextureBindingStack getTextureUnitBindings(int index) {
        return bindings[index];
    }

    public TextureUnitBooleanStateStack getTextureUnitStates(int index) {
        return states[index];
    }

    public TextureUnitBooleanStateStack getTexture1DStates(int index) {
        return texture1DStates[index];
    }

    public TextureUnitBooleanStateStack getTexture3DStates(int index) {
        return texture3DStates[index];
    }

    public TextureUnitBooleanStateStack getTexGenSStates(int index) {
        return texGenSStates[index];
    }

    public TextureUnitBooleanStateStack getTexGenTStates(int index) {
        return texGenTStates[index];
    }

    public TextureUnitBooleanStateStack getTexGenRStates(int index) {
        return texGenRStates[index];
    }

    public TextureUnitBooleanStateStack getTexGenQStates(int index) {
        return texGenQStates[index];
    }

    public TexGenState getTexGenState(int index) {
        return texGenStates[index];
    }

    public TexEnvState getTexEnvState(int index) {
        return texEnvStates[index];
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
