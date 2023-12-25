package com.gtnewhorizons.angelica.glsm.states;

import org.joml.Vector3d;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

public class FogState {
    public BooleanState mode = new BooleanState(GL11.GL_FOG);
    public int fogMode = GL11.GL_EXP;
    public final Vector3d fogColor = new Vector3d(0.0F, 0.0F, 0.0F);
    public float fogAlpha = 1.0F;
    public final FloatBuffer fogColorBuffer = FloatBuffer.allocate(4);
    public float density = 1.0F;
    public float start;
    public float end = 1.0F;
}
