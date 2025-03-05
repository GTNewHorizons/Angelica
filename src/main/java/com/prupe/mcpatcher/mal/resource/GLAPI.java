package com.prupe.mcpatcher.mal.resource;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GLContext;

@Lwjgl3Aware
public class GLAPI {

    private static final boolean useGlBlendFuncSeparate = GLContext.getCapabilities().OpenGL14;

    public static void glBindTexture(int texture) {
        if (texture >= 0) {
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture);
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
