package net.coderbot.iris.texture.mipmap;

import com.gtnewhorizons.angelica.compat.NativeImage;
import net.coderbot.iris.texture.pbr.loader.TextureAtlasSpriteInfo;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import javax.annotation.Nullable;

public interface CustomMipmapGenerator {
	NativeImage[] generateMipLevels(NativeImage image, int mipLevel);

    // TODO: PBR
	public interface Provider {
		@Nullable
		CustomMipmapGenerator getMipmapGenerator(TextureAtlasSpriteInfo info, int atlasWidth, int atlasHeight);
	}
}
