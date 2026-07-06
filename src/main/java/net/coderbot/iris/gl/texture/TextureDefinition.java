package net.coderbot.iris.gl.texture;

import lombok.Getter;

@Getter
public abstract class TextureDefinition {
	protected String name;

    public static class PNGDefinition extends TextureDefinition {
		public PNGDefinition(String name) {
			this.name = name;
		}
	}

	@Getter
    public static class RawDefinition extends TextureDefinition {
		private final TextureType target;
		private final int sizeX;
		private final int sizeY;
		private final int sizeZ;
		private final InternalTextureFormat internalFormat;
		private final PixelFormat format;
		private final PixelType pixelType;

		public RawDefinition(String path, TextureType target, InternalTextureFormat internalFormat, int sizeX, int sizeY, int sizeZ, PixelFormat format, PixelType pixelType) {
			this.name = path;
			this.target = target;
			this.sizeX = sizeX;
			this.sizeY = sizeY;
			this.sizeZ = sizeZ;
			this.internalFormat = internalFormat;
			this.format = format;
			this.pixelType = pixelType;
		}

    }
}
