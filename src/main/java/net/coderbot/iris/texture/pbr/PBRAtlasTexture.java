package net.coderbot.iris.texture.pbr;

import com.gtnewhorizons.angelica.compat.mojang.AutoClosableAbstractTexture;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.mixins.interfaces.TextureAtlasSpriteAccessor;
import com.gtnewhorizons.angelica.mixins.interfaces.TextureMapAccessor;
import lombok.Getter;
import net.coderbot.iris.Iris;
import net.coderbot.iris.texture.util.TextureExporter;
import net.coderbot.iris.texture.util.TextureManipulationUtil;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.util.ReportedException;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PBRAtlasTexture extends AutoClosableAbstractTexture {
	protected final TextureMap texMap;
	@Getter
    protected final PBRType type;
	protected final ResourceLocation id;
	protected final Map<ResourceLocation, TextureAtlasSprite> sprites = new HashMap<>();
	protected final Set<TextureAtlasSprite> animatedSprites = new HashSet<>();

	public PBRAtlasTexture(TextureMap textureMap, PBRType type) {
		this.texMap = textureMap;
		this.type = type;
		id = type.appendToFileLocation(((TextureMapAccessor)textureMap).getLocationBlocksTexture());

	}

    public ResourceLocation getAtlasId() {
		return id;
	}

	public void addSprite(TextureAtlasSprite sprite) {
		sprites.put(texMap.completeResourceLocation(new ResourceLocation(sprite.getIconName()), 0), sprite);
		if (sprite.hasAnimationMetadata()) {
			animatedSprites.add(sprite);
		}
	}

	@Nullable
	public TextureAtlasSprite getSprite(ResourceLocation id) {
		return sprites.get(id);
	}

	public void clear() {
		sprites.clear();
		animatedSprites.clear();
	}

	public void upload(int atlasWidth, int atlasHeight, int mipLevel, float anisotropicFiltering) {
		final int glId = getGlTextureId();
		TextureUtil.allocateTextureImpl(glId, mipLevel, atlasWidth, atlasHeight, anisotropicFiltering);
		TextureManipulationUtil.fillWithColor(glId, mipLevel, type.getDefaultValue());

		for (TextureAtlasSprite sprite : sprites.values()) {
			try {
				uploadSprite(sprite);
			} catch (Throwable throwable) {
				CrashReport crashReport = CrashReport.makeCrashReport(throwable, "Stitching texture atlas");
				CrashReportCategory crashReportCategory = crashReport.makeCategory("Texture being stitched together");
				crashReportCategory.addCrashSection("Atlas path", id);
				crashReportCategory.addCrashSection("Sprite", sprite);
				throw new ReportedException(crashReport);
			}
		}

		if (!animatedSprites.isEmpty()) {
			final PBRAtlasHolder pbrHolder = ((TextureAtlasExtension) texMap).getOrCreatePBRHolder();
			switch (type) {
			case NORMAL:
				pbrHolder.setNormalAtlas(this);
				break;
			case SPECULAR:
				pbrHolder.setSpecularAtlas(this);
				break;
			}
		}

		if (AngelicaConfig.enablePBRDebug) {
			TextureExporter.exportTextures("pbr_debug/atlas", id.getResourceDomain() + "_" + id.getResourcePath().replaceAll("/", "_"), glId, mipLevel, atlasWidth, atlasHeight);
		}
	}

	public boolean tryUpload(int atlasWidth, int atlasHeight, int mipLevel, float anisotropicFiltering) {
		try {
			upload(atlasWidth, atlasHeight, mipLevel, anisotropicFiltering);
			return true;
		} catch (Throwable t) {
            Iris.logger.error("Could not upload PBR texture", t);
			return false;
		}
	}

    @Override
    public void loadTexture(IResourceManager manager) throws IOException {
        // todo
    }

    protected void uploadSprite(TextureAtlasSprite sprite) {

        final TextureAtlasSpriteAccessor accessor = (TextureAtlasSpriteAccessor) sprite;
		if (accessor.getMetadata().getFrameCount() > 1) {
			final AnimationMetadataSection metadata = accessor.getMetadata();
			final int frameCount = sprite.getFrameCount();
			for (int frame = accessor.getFrame(); frame >= 0; frame--) {
				final int frameIndex = metadata.getFrameIndex(frame);
				if (frameIndex >= 0 && frameIndex < frameCount) {
                    TextureUtil.uploadTextureMipmap(sprite.getFrameTextureData(frameIndex), sprite.getIconWidth(), sprite.getIconHeight(), sprite.getOriginX(), sprite.getOriginY(), false, false);
					return;
				}
			}
		}
		TextureUtil.uploadTextureMipmap(sprite.getFrameTextureData(0), sprite.getIconWidth(), sprite.getIconHeight(), sprite.getOriginX(), sprite.getOriginY(), false, false);
	}

	public void cycleAnimationFrames() {
		bind();
		for (TextureAtlasSprite sprite : animatedSprites) {
            sprite.updateAnimation();
		}
	}

	@Override
	public void close() {
		final PBRAtlasHolder pbrHolder = ((TextureAtlasExtension) texMap).getPBRHolder();
		if (pbrHolder != null) {
            switch (type) {
                case NORMAL -> pbrHolder.setNormalAtlas(null);
                case SPECULAR -> pbrHolder.setSpecularAtlas(null);
            }
		}
	}
}
