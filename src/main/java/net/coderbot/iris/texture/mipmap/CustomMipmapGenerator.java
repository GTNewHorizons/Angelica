package net.coderbot.iris.texture.mipmap;

import com.gtnewhorizons.angelica.compat.mojang.NativeImage;
import net.coderbot.iris.texture.pbr.loader.TextureAtlasSpriteInfo;

import javax.annotation.Nullable;

public interface CustomMipmapGenerator {
	NativeImage[] generateMipLevels(NativeImage image, int mipLevel);

	public interface Provider {
		@Nullable
		CustomMipmapGenerator getMipmapGenerator(TextureAtlasSpriteInfo info, int atlasWidth, int atlasHeight);
	}
}
