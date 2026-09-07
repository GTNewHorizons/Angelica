package com.gtnewhorizons.angelica.glsm.texture;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL40;

public final class TextureTargets {

    private TextureTargets() {}

    public static boolean isProxyTarget(int target) {
        return switch (target) {
            case GL11.GL_PROXY_TEXTURE_1D,
                 GL11.GL_PROXY_TEXTURE_2D,
                 GL12.GL_PROXY_TEXTURE_3D,
                 GL13.GL_PROXY_TEXTURE_CUBE_MAP,
                 GL30.GL_PROXY_TEXTURE_1D_ARRAY,
                 GL30.GL_PROXY_TEXTURE_2D_ARRAY,
                 GL31.GL_PROXY_TEXTURE_RECTANGLE,
                 GL32.GL_PROXY_TEXTURE_2D_MULTISAMPLE,
                 GL32.GL_PROXY_TEXTURE_2D_MULTISAMPLE_ARRAY,
                 GL40.GL_PROXY_TEXTURE_CUBE_MAP_ARRAY -> true;
            default -> false;
        };
    }
}
