package com.gtnewhorizons.angelica.glsm.states;

import org.lwjgl.opengl.GL11;

public class DepthState {
    public final BooleanState mode = new BooleanState(GL11.GL_DEPTH_TEST);
    public boolean mask = true;
    public int func = GL11.GL_LESS;

}
