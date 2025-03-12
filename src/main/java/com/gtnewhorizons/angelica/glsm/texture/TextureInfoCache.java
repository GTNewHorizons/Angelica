package com.gtnewhorizons.angelica.glsm.texture;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.managers.GLTextureManager;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL33C;

import java.nio.IntBuffer;

public class TextureInfoCache {

    private final Int2ObjectMap<TextureInfo> cache = new Int2ObjectOpenHashMap<>();

	public TextureInfoCache() {
	}

	public TextureInfo getInfo(int id) {
        if(id < 0 || !GLStateManager.isMainThread()) return null;
		return cache.computeIfAbsent(id, TextureInfo::new);
	}

	public void onTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, @Nullable IntBuffer pixels) {
		if (target == GL33C.GL_TEXTURE_2D && level == 0) {
            final TextureInfo info = getInfo(GLTextureManager.getBoundTexture());
            if(info == null) return;
			info.internalFormat = internalformat;
			info.width = width;
			info.height = height;
		}
	}
    public void onTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, long pixels_buffer_offset) {
		if (target == GL33C.GL_TEXTURE_2D && level == 0) {
            final TextureInfo info = getInfo(GLTextureManager.getBoundTexture());
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
