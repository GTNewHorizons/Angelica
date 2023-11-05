package com.gtnewhorizons.angelica.glsm.states;

import org.lwjgl.opengl.GL11;

public class FogState {
    public BooleanState mode = new BooleanState(GL11.GL_FOG);
    public int fogMode = GL11.GL_EXP;
    public float density = 1.0F;
    public float start;
    public float end = 1.0F;
}
