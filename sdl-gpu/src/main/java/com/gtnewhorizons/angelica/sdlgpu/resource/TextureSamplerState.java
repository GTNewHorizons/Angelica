package com.gtnewhorizons.angelica.sdlgpu.resource;

import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;

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

    public boolean invalidate(boolean changed) {
        if (!changed && sdlSampler != 0) return false;
        sdlSampler = 0;
        return true;
    }

    public boolean seti(int pname, int param) {
        switch (pname) {
            case GL11.GL_TEXTURE_MIN_FILTER -> { if (minFilter == param) return false; minFilter = param; }
            case GL11.GL_TEXTURE_MAG_FILTER -> { if (magFilter == param) return false; magFilter = param; }
            case GL11.GL_TEXTURE_WRAP_S -> { if (wrapS == param) return false; wrapS = param; }
            case GL11.GL_TEXTURE_WRAP_T -> { if (wrapT == param) return false; wrapT = param; }
            case GL12.GL_TEXTURE_WRAP_R -> { if (wrapR == param) return false; wrapR = param; }
            case GL12.GL_TEXTURE_MAX_LEVEL -> { maxLevel = param; return false; }
            case GL14.GL_TEXTURE_COMPARE_MODE -> { if (compareMode == param) return false; compareMode = param; }
            case GL14.GL_TEXTURE_COMPARE_FUNC -> { if (compareFunc == param) return false; compareFunc = param; }
            case GL12.GL_TEXTURE_MIN_LOD, GL12.GL_TEXTURE_MAX_LOD, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT -> { return setf(pname, (float) param); }
            default -> { return false; }
        }
        return true;
    }

    public boolean setf(int pname, float param) {
        switch (pname) {
            case GL12.GL_TEXTURE_MIN_LOD -> { if (minLod == param) return false; minLod = param; }
            case GL12.GL_TEXTURE_MAX_LOD -> { if (maxLod == param) return false; maxLod = param; }
            case GL14.GL_TEXTURE_LOD_BIAS -> { if (lodBias == param) return false; lodBias = param; }
            case EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT -> { if (maxAnisotropy == param) return false; maxAnisotropy = param; }
            default -> { return seti(pname, (int) param); }
        }
        return true;
    }
}
