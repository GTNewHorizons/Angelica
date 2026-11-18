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

    public static int bindingQuery(int target) {
        return switch (target) {
            case GL11.GL_TEXTURE_1D -> GL11.GL_TEXTURE_BINDING_1D;
            case GL12.GL_TEXTURE_3D -> GL12.GL_TEXTURE_BINDING_3D;
            case GL13.GL_TEXTURE_CUBE_MAP -> GL13.GL_TEXTURE_BINDING_CUBE_MAP;
            case GL30.GL_TEXTURE_1D_ARRAY -> GL30.GL_TEXTURE_BINDING_1D_ARRAY;
            case GL30.GL_TEXTURE_2D_ARRAY -> GL30.GL_TEXTURE_BINDING_2D_ARRAY;
            case GL31.GL_TEXTURE_RECTANGLE -> GL31.GL_TEXTURE_BINDING_RECTANGLE;
            case GL31.GL_TEXTURE_BUFFER -> GL31.GL_TEXTURE_BINDING_BUFFER;
            case GL32.GL_TEXTURE_2D_MULTISAMPLE -> GL32.GL_TEXTURE_BINDING_2D_MULTISAMPLE;
            case GL32.GL_TEXTURE_2D_MULTISAMPLE_ARRAY -> GL32.GL_TEXTURE_BINDING_2D_MULTISAMPLE_ARRAY;
            case GL40.GL_TEXTURE_CUBE_MAP_ARRAY -> GL40.GL_TEXTURE_BINDING_CUBE_MAP_ARRAY;
            default -> GL11.GL_TEXTURE_BINDING_2D;
        };
    }
}
