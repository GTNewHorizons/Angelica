package com.gtnewhorizons.angelica.glsm.texture;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import java.nio.IntBuffer;

public class TextureInfoCache {
    /**
     * Adapted from Iris for use in GLSM
     */

	public static final TextureInfoCache INSTANCE = new TextureInfoCache();

	private final Int2ObjectMap<TextureInfo> cache = new Int2ObjectOpenHashMap<>();

	private TextureInfoCache() {
	}

	public TextureInfo getInfo(int id) {
        if(id < 0 || !GLStateManager.isMainThread()) return null;
		return cache.computeIfAbsent(id, TextureInfo::new);
	}

	public void onTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, @Nullable IntBuffer pixels) {
		if (target == GL11.GL_TEXTURE_2D && level == 0) {
            final TextureInfo info = getInfo(GLStateManager.getBoundTexture());
            if(info == null) return;
			info.internalFormat = internalformat;
			info.width = width;
			info.height = height;
		}
	}
    public void onTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, long pixels_buffer_offset) {
		if (target == GL11.GL_TEXTURE_2D && level == 0) {
            final TextureInfo info = getInfo(GLStateManager.getBoundTexture());
            if(info == null) return;
			info.internalFormat = internalformat;
			info.width = width;
			info.height = height;
		}
	}

	public void onDeleteTexture(int id) {
		if(id >= 0 && GLStateManager.isMainThread()) cache.remove(id);
	}

}
