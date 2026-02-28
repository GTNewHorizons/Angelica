package com.gtnewhorizons.angelica.glsm.texture;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.lwjgl.opengl.GL11;

import java.util.concurrent.locks.ReentrantLock;

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

    private TextureInfoCache() {
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

    public void onTexImage2D(int texture, int target, int level, int internalformat, int width, int height) {
        if (target == GL11.GL_TEXTURE_2D && level == 0) {
            final TextureInfo info = getInfo(texture);
            if (info == null) return;
            info.internalFormat = internalformat;
            info.width = width;
            info.height = height;
        }
    }

    public void onDeleteTexture(int id) {
        if (id < 0) return;

        if (GLStateManager.isSplashComplete()) {
            cache.remove(id);
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
