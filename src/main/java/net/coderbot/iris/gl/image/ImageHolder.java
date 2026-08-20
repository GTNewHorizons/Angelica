package net.coderbot.iris.gl.image;

import com.gtnewhorizons.angelica.glsm.texture.InternalTextureFormat;

import java.util.function.IntSupplier;

public interface ImageHolder {
	boolean hasImage(String name);
	void addTextureImage(IntSupplier textureID, InternalTextureFormat internalFormat, String name);
}
