package com.gtnewhorizons.angelica.glsm.texture;

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
		return cache.computeIfAbsent(id, TextureInfo::new);
	}

	public void onTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, @Nullable IntBuffer pixels) {
		if (level == 0) {
			final int id = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            final TextureInfo info = getInfo(id);
			info.internalFormat = internalformat;
			info.width = width;
			info.height = height;
		}
	}
    public void onTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, long pixels_buffer_offset) {
		if (level == 0) {
            final int id = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            final TextureInfo info = getInfo(id);
			info.internalFormat = internalformat;
			info.width = width;
			info.height = height;
		}
	}

	public void onDeleteTexture(int id) {
		cache.remove(id);
	}

}
