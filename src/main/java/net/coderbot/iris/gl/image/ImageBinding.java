package net.coderbot.iris.gl.image;

import com.gtnewhorizons.angelica.glsm.RenderSystem;
import org.lwjgl.opengl.GL15;

import java.util.function.IntSupplier;

public class ImageBinding {
	private final int imageUnit;
	private final int internalFormat;
	private final IntSupplier textureID;

	public ImageBinding(int imageUnit, int internalFormat, IntSupplier textureID) {
		this.textureID = textureID;
		this.imageUnit = imageUnit;
		this.internalFormat = internalFormat;
	}

	public void update() {
		// We can assume that image bindings are supported here as either the EXT extension or 4.2 core, as otherwise ImageLimits
		// would report that zero image units are supported.
		RenderSystem.bindImageTexture(imageUnit, textureID.getAsInt(), 0, false, 0, GL15.GL_READ_WRITE, internalFormat);
	}
}
