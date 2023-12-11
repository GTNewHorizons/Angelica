package net.coderbot.iris.texture.pbr;

import com.gtnewhorizons.angelica.mixins.early.angelica.textures.MixinTextureAtlasSprite;
import com.gtnewhorizons.angelica.compat.mojang.AutoClosableAbstractTexture;
import com.gtnewhorizons.angelica.compat.mojang.TextureAtlas;
import net.coderbot.iris.texture.util.TextureExporter;
import net.coderbot.iris.texture.util.TextureManipulationUtil;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
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
	protected final PBRType type;
	protected final ResourceLocation id;
	protected final Map<ResourceLocation, TextureAtlasSprite> sprites = new HashMap<>();
	protected final Set<TextureAtlasSprite> animatedSprites = new HashSet<>();

	public PBRAtlasTexture(TextureMap textureMap, PBRType type) {
		this.texMap = textureMap;
		this.type = type;
//		id = type.appendToFileLocation(atlasTexture.location());

        id = type.appendToFileLocation(new ResourceLocation("stuff", getType().name()));
	}

	public PBRType getType() {
		return type;
	}

	public ResourceLocation getAtlasId() {
		return id;
	}

	public void addSprite(TextureAtlasSprite sprite) {
        // TODO: PBR - Wants location
//		sprites.put(sprite.getName(), sprite);
		sprites.put(new ResourceLocation("stuff", sprite.getIconName()), sprite);
		if (((MixinTextureAtlasSprite) (Object)sprite).isAnimation()) {
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

	public void upload(int atlasWidth, int atlasHeight, int mipLevel) {
		int glId = getGlTextureId();
//		TextureUtil.prepareImage(glId, mipLevel, atlasWidth, atlasHeight);
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
			PBRAtlasHolder pbrHolder = ((TextureAtlasExtension) texMap).getOrCreatePBRHolder();
			switch (type) {
			case NORMAL:
				pbrHolder.setNormalAtlas(this);
				break;
			case SPECULAR:
				pbrHolder.setSpecularAtlas(this);
				break;
			}
		}

		if (PBRTextureManager.DEBUG) {
			TextureExporter.exportTextures("pbr_debug/atlas", id.getResourceDomain() + "_" + id.getResourcePath().replaceAll("/", "_"), glId, mipLevel, atlasWidth, atlasHeight);
		}
	}

	public boolean tryUpload(int atlasWidth, int atlasHeight, int mipLevel) {
		try {
			upload(atlasWidth, atlasHeight, mipLevel);
			return true;
		} catch (Throwable t) {
			return false;
		}
	}

    @Override
    public void loadTexture(IResourceManager p_110551_1_) throws IOException {
        // todo
    }

    protected void uploadSprite(TextureAtlasSprite sprite) {
		if (((MixinTextureAtlasSprite) (Object)sprite).isAnimation()) {
            MixinTextureAtlasSprite mixinSprite = ((MixinTextureAtlasSprite) (Object) sprite);
			AnimationMetadataSection metadata = mixinSprite.getMetadata();

			int frameCount = sprite.getFrameCount();
			for (int frame = mixinSprite.getFrame(); frame >= 0; frame--) {
				int frameIndex = metadata.getFrameIndex(frame);
				if (frameIndex >= 0 && frameIndex < frameCount) {
					mixinSprite.callUpload(frameIndex);
					return;
				}
			}
		}

//		sprite.uploadFirstFrame();
	}

	public void cycleAnimationFrames() {
		bind();
		for (TextureAtlasSprite sprite : animatedSprites) {
//			sprite.cycleFrames();
            sprite.updateAnimation();
		}
	}

	@Override
	public void close() {
		PBRAtlasHolder pbrHolder = ((TextureAtlasExtension) texMap).getPBRHolder();
		if (pbrHolder != null) {
			switch (type) {
			case NORMAL:
				pbrHolder.setNormalAtlas(null);
				break;
			case SPECULAR:
				pbrHolder.setSpecularAtlas(null);
				break;
			}
		}
	}

	@Override
	public void load(IResourceManager manager) {
	}
}
