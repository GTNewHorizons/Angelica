package com.gtnewhorizons.angelica.client;

import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

@SuppressWarnings("unused") // Used in ASM
public class GLStateManager {
    public static void glBindTexture(int target, int texture) {
        net.coderbot.iris.texture.TextureTracker.INSTANCE.onBindTexture(texture);
        GL11.glBindTexture(target, texture);
    }

    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, IntBuffer pixels) {
        net.coderbot.iris.texture.TextureInfoCache.INSTANCE.onTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
        GL11.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
    }
    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, ByteBuffer pixels) {
        net.coderbot.iris.texture.TextureInfoCache.INSTANCE.onTexImage2D(
            target, level, internalformat, width, height, border, format, type,
            pixels != null ? pixels.asIntBuffer() : (IntBuffer) null
        );
        GL11.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
    }

    public static void glDeleteTextures(int id) {
        iris$onDeleteTexture(id);
        GL11.glDeleteTextures(id);
    }
    public static void glDeleteTextures(IntBuffer ids) {
        for(int id: ids.array()) {
            iris$onDeleteTexture(id);
        }
        GL11.glDeleteTextures(ids);
    }

    private static void iris$onDeleteTexture(int id) {
        net.coderbot.iris.texture.TextureTracker.INSTANCE.onDeleteTexture(id);
        net.coderbot.iris.texture.TextureInfoCache.INSTANCE.onDeleteTexture(id);
        net.coderbot.iris.texture.pbr.PBRTextureManager.INSTANCE.onDeleteTexture(id);
    }
}
