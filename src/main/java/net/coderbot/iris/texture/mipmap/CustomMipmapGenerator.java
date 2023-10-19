package net.coderbot.iris.texture.mipmap;

import net.coderbot.iris.compat.mojang.NativeImage;

public interface CustomMipmapGenerator {
	NativeImage[] generateMipLevels(NativeImage image, int mipLevel);

    // TODO: PBR
//	public interface Provider {
//		@Nullable
//		CustomMipmapGenerator getMipmapGenerator(TextureAtlasSprite.Info info, int atlasWidth, int atlasHeight);
//	}
}
