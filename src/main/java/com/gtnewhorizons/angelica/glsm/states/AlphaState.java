package com.gtnewhorizons.angelica.glsm.states;

import org.lwjgl.opengl.GL11;

public class AlphaState {
    public final BooleanState mode = new BooleanState(GL11.GL_ALPHA_TEST);
    public int function = GL11.GL_ALWAYS;
    public float reference = -1.0F;
}
