package com.gtnewhorizons.angelica.glsm.states;

import org.lwjgl.opengl.GL11;

public class TextureState {
    public final BooleanState mode = new BooleanState(GL11.GL_TEXTURE_2D);
    public int binding;
}
