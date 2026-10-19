package com.gtnewhorizons.angelica.glsm.texture;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL40;

import java.nio.Buffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntConsumer;

public class TextureInfoCache {
    /**
     * Adapted from Iris for use in GLSM.
     *
     * This cache stores server-side texture state (parameters, dimensions) that is shared across GL contexts.
     * Locking is only used during splash when multiple contexts exist.
     */

    public static final TextureInfoCache INSTANCE = new TextureInfoCache();

    private final Int2ObjectMap<TextureInfo> cache = new Int2ObjectOpenHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final List<IntConsumer> deleteListeners = new ArrayList<>();
    private int deleteCount;

    private TextureInfoCache() {
    }

    public int deleteCount() {
        return deleteCount;
    }

    public void addDeleteListener(IntConsumer listener) {
        deleteListeners.add(listener);
    }

    public void onContentsChanged(int texture) {
        if (texture <= 0) return;
        final TextureInfo info = getInfo(texture);
        if (info != null) info.bumpUploadGeneration();
    }

    public void onWrittenOutsideUploads(int texture) {
        if (texture <= 0) return;
        final TextureInfo info = getInfo(texture);
        if (info == null || info.writtenOutsideUploads) return;
        info.writtenOutsideUploads = true;
        info.changeStamp++;
    }

    private void defineLevelZero(int texture, int target, int internalformat, int width, int height) {
        final TextureInfo info = getInfo(texture);
        if (info == null) return;
        info.setTarget(target);
        info.internalFormat = internalformat;
        info.resolvedInternalFormat = isGenericCompressedInternalFormat(internalformat) ? -1 : internalformat;
        info.width = width;
        info.height = height;
        info.bumpUploadGeneration();
    }

    public TextureInfo getInfo(int id) {
        if (id < 0) return null;

        if (GLStateManager.isSplashComplete()) {
            return cache.computeIfAbsent(id, TextureInfo::new);
        }

        lock.lock();
        try {
            return cache.computeIfAbsent(id, TextureInfo::new);
        } finally {
            lock.unlock();
        }
    }

    public void onTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, @Nullable Buffer pixels) {
        if (isTrackedTarget(target) && level == 0) defineLevelZero(GLStateManager.getBoundTextureForServerState(), target, internalformat, width, height);
    }

    public void onTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, long pixels_buffer_offset) {
        if (isTrackedTarget(target) && level == 0) defineLevelZero(GLStateManager.getBoundTextureForServerState(), target, internalformat, width, height);
    }

    public void onCopyTexImage2D(int target, int level, int internalformat, int width, int height) {
        if (isTrackedTarget(target) && level == 0) defineLevelZero(GLStateManager.getBoundTextureForServerState(), target, internalformat, width, height);
    }

    public void onTextureImage2D(int texture, int target, int level, int internalformat, int width, int height) {
        if (isTrackedTarget(target) && level == 0) defineLevelZero(texture, target, internalformat, width, height);
    }

    public void onTexStorage2D(int target, int internalformat, int width, int height) {
        if (isTrackedTarget(target)) defineLevelZero(GLStateManager.getBoundTextureForServerState(), target, internalformat, width, height);
    }

    public void onTexSubImage2D(int target, int level) {
        if (isTrackedTarget(target) && level == 0) onContentsChanged(GLStateManager.getBoundTextureForServerState());
    }

    public static boolean isGenericCompressedInternalFormat(int internalFormat) {
        return switch (internalFormat) {
            case GL30.GL_COMPRESSED_RED,
                 GL30.GL_COMPRESSED_RG,
                 GL13.GL_COMPRESSED_RGB,
                 GL13.GL_COMPRESSED_RGBA,
                 GL21.GL_COMPRESSED_SRGB,
                 GL21.GL_COMPRESSED_SRGB_ALPHA -> true;
            default -> false;
        };
    }

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

    private static boolean isTrackedTarget(int target) {
        return target == GL11.GL_TEXTURE_2D
            || target == GL11.GL_TEXTURE_1D
            || target == GL12.GL_TEXTURE_3D
            || target == GL13.GL_TEXTURE_CUBE_MAP
            || target == GL30.GL_TEXTURE_2D_ARRAY
            || target == GL30.GL_TEXTURE_1D_ARRAY;
    }

    public void onDeleteTexture(int id) {
        if (id < 0) return;
        deleteCount++;

        if (GLStateManager.isSplashComplete()) {
            cache.remove(id);
            for (IntConsumer listener : deleteListeners) listener.accept(id);
            return;
        }

        // During splash: lock for thread safety
        lock.lock();
        try {
            cache.remove(id);
        } finally {
            lock.unlock();
        }
    }
}
