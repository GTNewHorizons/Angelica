package net.coderbot.iris.shaderpack.texture;

import lombok.Getter;
import net.coderbot.iris.gl.texture.InternalTextureFormat;
import net.coderbot.iris.gl.texture.PixelFormat;
import net.coderbot.iris.gl.texture.PixelType;

public abstract class CustomTextureData {
	CustomTextureData() {
	}

	@Getter
    public static final class PngData extends CustomTextureData {
		private final TextureFilteringData filteringData;
		private final byte[] content;

		public PngData(TextureFilteringData filteringData, byte[] content) {
			this.filteringData = filteringData;
			this.content = content;
		}

    }

	public static final class LightmapMarker extends CustomTextureData {
		@Override
		public boolean equals(Object obj) {
			return obj.getClass() == this.getClass();
		}

		@Override
		public int hashCode() {
			return 33;
		}
	}

	@Getter
    public static final class ResourceData extends CustomTextureData {
        private final String namespace;
        private final String location;

		public ResourceData(String namespace, String location) {
			this.namespace = namespace;
			this.location = location;
		}

    }

	@Getter
	public abstract static class RawData extends CustomTextureData {
		private final byte[] content;
		private final TextureFilteringData filteringData;
		private final InternalTextureFormat internalFormat;
		private final PixelFormat pixelFormat;
		private final PixelType pixelType;

		protected RawData(byte[] content, TextureFilteringData filteringData, InternalTextureFormat internalFormat, PixelFormat pixelFormat, PixelType pixelType) {
			this.content = content;
			this.filteringData = filteringData;
			this.internalFormat = internalFormat;
			this.pixelFormat = pixelFormat;
			this.pixelType = pixelType;
		}
	}

	@Getter
    public static final class RawData1D extends RawData {
		private final int sizeX;

		public RawData1D(byte[] content, TextureFilteringData filteringData, InternalTextureFormat internalFormat, PixelFormat pixelFormat, PixelType pixelType, int sizeX) {
			super(content, filteringData, internalFormat, pixelFormat, pixelType);

			this.sizeX = sizeX;
		}

    }

	@Getter
    public static class RawData2D extends RawData {
		private final int sizeX;
		private final int sizeY;

		public RawData2D(byte[] content, TextureFilteringData filteringData, InternalTextureFormat internalFormat, PixelFormat pixelFormat, PixelType pixelType, int sizeX, int sizeY) {
			super(content, filteringData, internalFormat, pixelFormat, pixelType);

			this.sizeX = sizeX;
			this.sizeY = sizeY;
		}

    }

	@Getter
    public static final class RawData3D extends RawData {
		private final int sizeX;
		private final int sizeY;
		private final int sizeZ;

		public RawData3D(byte[] content, TextureFilteringData filteringData, InternalTextureFormat internalFormat, PixelFormat pixelFormat, PixelType pixelType, int sizeX, int sizeY, int sizeZ) {
			super(content, filteringData, internalFormat, pixelFormat, pixelType);

			this.sizeX = sizeX;
			this.sizeY = sizeY;
			this.sizeZ = sizeZ;
		}

    }

	public static final class RawDataRect extends RawData2D {
		public RawDataRect(byte[] content, TextureFilteringData filteringData, InternalTextureFormat internalFormat, PixelFormat pixelFormat, PixelType pixelType, int sizeX, int sizeY) {
			super(content, filteringData, internalFormat, pixelFormat, pixelType, sizeX, sizeY);
		}
	}
}
