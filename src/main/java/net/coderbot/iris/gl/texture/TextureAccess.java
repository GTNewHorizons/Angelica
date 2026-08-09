package net.coderbot.iris.gl.texture;

import com.gtnewhorizons.angelica.glsm.texture.TextureType;

import java.util.function.IntSupplier;

public interface TextureAccess {
	TextureType getType();

	IntSupplier getTextureId();
}
