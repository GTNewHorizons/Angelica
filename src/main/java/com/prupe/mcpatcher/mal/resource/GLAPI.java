package com.prupe.mcpatcher.mal.resource;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.managers.GLTextureManager;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

public class GLAPI {

    private static final boolean useGlBlendFuncSeparate = GL.getCapabilities().OpenGL14;

    public static void glBindTexture(int texture) {
        if (texture >= 0) {
            GLTextureManager.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        }
    }

    public static void glBlendFuncSeparate(int src, int dst, int srcAlpha, int dstAlpha) {
        if (useGlBlendFuncSeparate) {
            GL14.glBlendFuncSeparate(src, dst, srcAlpha, dstAlpha);
        } else {
            GLStateManager.glBlendFunc(src, dst);
        }
    }
}
