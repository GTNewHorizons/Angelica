package net.coderbot.iris.compat.mojang;

import net.minecraft.client.renderer.OpenGlHelper;

public class GLHelper {

    public static void glDeleteFramebuffers(int framebuffer) {
        OpenGlHelper.func_153174_h(framebuffer);
    }

    public static int glCheckFramebufferStatus(int target) {
        return OpenGlHelper.func_153167_i(target);
    }

    public static int glGenFramebuffers() {
        return OpenGlHelper.func_153165_e/*glGenFramebuffers*/();
    }
}
