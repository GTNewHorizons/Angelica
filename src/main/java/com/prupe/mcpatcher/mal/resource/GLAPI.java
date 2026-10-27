package com.prupe.mcpatcher.mal.resource;

import org.lwjgl.opengl.GL11;
import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.GLStateManager;

public class GLAPI {

    private static final boolean useGlBlendFuncSeparate = GLStateManager.capabilities.OpenGL14;

    public static void glBindTexture(int texture) {
        if (texture >= 0) {
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        }
    }

    public static void glBlendFuncSeparate(int src, int dst, int srcAlpha, int dstAlpha) {
        if (useGlBlendFuncSeparate) {
            GLSMBlend.glBlendFuncSeparate(src, dst, srcAlpha, dstAlpha);
        } else {
            GLSMBlend.glBlendFunc(src, dst);
        }
    }
}
