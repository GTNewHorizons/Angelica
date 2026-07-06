package net.coderbot.iris.gl.texture;

import com.gtnewhorizons.angelica.glsm.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL31;

import java.nio.ByteBuffer;
import java.util.Optional;

public enum TextureType {
	TEXTURE_1D(GL11.GL_TEXTURE_1D),
	TEXTURE_2D(GL11.GL_TEXTURE_2D),
	TEXTURE_3D(GL12.GL_TEXTURE_3D),
	TEXTURE_RECTANGLE(GL31.GL_TEXTURE_RECTANGLE);

	private final int glType;

	TextureType(int glType) {
		this.glType = glType;
	}

	public static Optional<TextureType> fromString(String name) {
		try {
			return Optional.of(TextureType.valueOf(name));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	public int getGlType() {
		return glType;
	}

	public void apply(int texture, int sizeX, int sizeY, int sizeZ, int internalFormat, int format, int pixelType, ByteBuffer pixels) {
		switch (this) {
			case TEXTURE_1D -> RenderSystem.texImage1D(texture, glType, 0, internalFormat, sizeX, 0, format, pixelType, pixels);
			case TEXTURE_2D, TEXTURE_RECTANGLE -> RenderSystem.texImage2D(texture, glType, 0, internalFormat, sizeX, sizeY, 0, format, pixelType, pixels);
			case TEXTURE_3D -> RenderSystem.texImage3D(texture, glType, 0, internalFormat, sizeX, sizeY, sizeZ, 0, format, pixelType, pixels);
		}
	}
}
