package com.gtnewhorizons.angelica.glsm.states;

import org.lwjgl.opengl.GL11;

public class BlendState {

    public final BooleanState mode = new BooleanState(GL11.GL_BLEND);
    public int srcRgb = GL11.GL_ONE;
    public int dstRgb = GL11.GL_ZERO;
    public int srcAlpha = GL11.GL_ONE;
    public int dstAlpha = GL11.GL_ZERO;

}
