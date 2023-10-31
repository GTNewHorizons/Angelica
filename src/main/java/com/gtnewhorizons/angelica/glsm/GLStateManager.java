package com.gtnewhorizons.angelica.glsm;

import lombok.Getter;
import net.coderbot.iris.gl.blending.BlendModeStorage;
import net.coderbot.iris.gl.state.StateUpdateNotifiers;
import net.coderbot.iris.texture.TextureInfoCache;
import net.coderbot.iris.texture.TextureTracker;
import net.coderbot.iris.texture.pbr.PBRTextureManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

@SuppressWarnings("unused") // Used in ASM
public class GLStateManager {
    // GLStateManager State Trackers
    @Getter
    private static final BlendState Blend = new BlendState();

    // Iris Listeners
    private static Runnable blendFuncListener;

    static {
        StateUpdateNotifiers.blendFuncNotifier = listener -> blendFuncListener = listener;
    }


    // LWJGL Overrides

    public static void glBindTexture(int target, int texture) {
        // Iris
        TextureTracker.INSTANCE.onBindTexture(texture);
        GL11.glBindTexture(target, texture);
    }

    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, IntBuffer pixels) {
        // Iris
        TextureInfoCache.INSTANCE.onTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
        GL11.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
    }
    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, ByteBuffer pixels) {
        // Iris
        TextureInfoCache.INSTANCE.onTexImage2D(
            target, level, internalformat, width, height, border, format, type,
            pixels != null ? pixels.asIntBuffer() : (IntBuffer) null
        );
        GL11.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
    }

    public static void glDeleteTextures(int id) {
        // Iris
        iris$onDeleteTexture(id);
        GL11.glDeleteTextures(id);
    }
    public static void glDeleteTextures(IntBuffer ids) {
        // Iris
        for(int id: ids.array()) {
            iris$onDeleteTexture(id);
        }
        GL11.glDeleteTextures(ids);
    }

    public static void glEnable(int cap) {
        switch(cap) {
            case GL11.GL_BLEND -> enableBlend();
            default -> GL11.glEnable(cap);
        }
    }


    public static void glDisable(int cap) {
        switch (cap) {
            case GL11.GL_BLEND -> disableBlend();
            default -> GL11.glDisable(cap);
        }
    }

    // GLStateManager Functions

    private static void enableBlend() {
        // Iris
        if(BlendModeStorage.isBlendLocked()) {
            BlendModeStorage.deferBlendModeToggle(true);
            return;
        }
        Blend.mode.enable();
    }

    public static void disableBlend() {
        // Iris
        if (BlendModeStorage.isBlendLocked()) {
            BlendModeStorage.deferBlendModeToggle(false);
            return;
        }
        Blend.mode.disable();
    }

    public static void glBlendFunc(int srcFactor, int dstFactor) {
        // Iris
        if(BlendModeStorage.isBlendLocked()) {
            BlendModeStorage.deferBlendFunc(srcFactor, dstFactor, srcFactor, dstFactor);
            return;
        }
        Blend.srcRgb = srcFactor;
        Blend.dstRgb = dstFactor;
        GL11.glBlendFunc(srcFactor, dstFactor);

        // Iris
        if (blendFuncListener != null) blendFuncListener.run();
    }

    public static void glBlendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        // Iris
        if(BlendModeStorage.isBlendLocked()) {
            BlendModeStorage.deferBlendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha);
            return;
        }
        Blend.srcRgb = srcRgb;
        Blend.dstRgb = dstRgb;
        Blend.srcAlpha = srcAlpha;
        Blend.dstAlpha = dstAlpha;
        GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);

        // Iris
        if (blendFuncListener != null) blendFuncListener.run();
    }

    // Iris Functions

    private static void iris$onDeleteTexture(int id) {
        TextureTracker.INSTANCE.onDeleteTexture(id);
        TextureInfoCache.INSTANCE.onDeleteTexture(id);
        PBRTextureManager.INSTANCE.onDeleteTexture(id);
    }

}
