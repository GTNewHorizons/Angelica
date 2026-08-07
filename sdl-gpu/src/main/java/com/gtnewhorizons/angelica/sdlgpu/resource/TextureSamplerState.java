package com.gtnewhorizons.angelica.sdlgpu.resource;

import org.lwjgl.opengl.GL11;

public final class TextureSamplerState {
    public int minFilter = GL11.GL_NEAREST_MIPMAP_LINEAR;
    public int magFilter = GL11.GL_LINEAR;
    public int wrapS = GL11.GL_REPEAT;
    public int wrapT = GL11.GL_REPEAT;
    public int wrapR = GL11.GL_REPEAT;
    public int maxLevel = -1;
    public float minLod = -1000.0f;
    public float maxLod = 1000.0f;
    public float lodBias = 0.0f;
    public float maxAnisotropy = 1.0f;
    public int compareMode = 0;
    public int compareFunc = GL11.GL_LEQUAL;
    public long sdlSampler;
}
