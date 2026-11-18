package net.coderbot.iris.texture.pbr;

import com.gtnewhorizons.angelica.compat.mojang.AutoClosableAbstractTexture;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.profiling.DebugCounters;
import com.gtnewhorizons.angelica.mixins.interfaces.IPatchedTextureAtlasSprite;
import com.gtnewhorizons.angelica.mixins.interfaces.ISpriteExt;
import com.gtnewhorizons.angelica.proxy.ClientProxy;
import com.gtnewhorizons.angelica.rendering.celeritas.SpriteExtension;
import com.gtnewhorizons.angelica.utils.SpritePadding;
import lombok.Getter;
import net.coderbot.iris.Iris;
import net.coderbot.iris.texture.pbr.loader.AtlasPBRLoader;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PBRAtlasTexture extends AutoClosableAbstractTexture {
	protected final TextureMap texMap;
	@Getter
    protected final PBRType type;
	protected final ResourceLocation id;
	protected final Map<ResourceLocation, TextureAtlasSprite> sprites = new HashMap<>();
	protected final List<IPatchedTextureAtlasSprite> animatedSprites = new ArrayList<>();
	protected final List<TextureAtlasSprite> alwaysAnimatedSprites = new ArrayList<>();

	public PBRAtlasTexture(TextureMap textureMap, PBRType type) {
		this.texMap = textureMap;
		this.type = type;
		id = type.appendToFileLocation(TextureMap.locationBlocksTexture);

	}

    public ResourceLocation getAtlasId() {
		return id;
	}

	public void addSprite(AtlasPBRLoader.PBRTextureAtlasSprite sprite) {
		sprites.put(texMap.completeResourceLocation(new ResourceLocation(sprite.getIconName()), 0), sprite);
		if (((ISpriteExt) sprite).isAnimation()) {
			// Without the animation mixins, or for a base outside listAnimatedSprites, nothing consumes base visibility
			if (sprite instanceof IPatchedTextureAtlasSprite patched && sprite.getBaseSprite().hasAnimationMetadata()) {
				animatedSprites.add(patched);
			} else {
				alwaysAnimatedSprites.add(sprite);
			}
		}
	}

	@Nullable
	public TextureAtlasSprite getSprite(ResourceLocation id) {
		return sprites.get(id);
	}

	public void clear() {
		sprites.clear();
		animatedSprites.clear();
		alwaysAnimatedSprites.clear();
	}

	public void upload(int atlasWidth, int atlasHeight, int mipLevel, float anisotropicFiltering) {
		final int glId = getGlTextureId();
		TextureUtil.allocateTextureImpl(glId, mipLevel, atlasWidth, atlasHeight, anisotropicFiltering);
		TextureManipulationUtil.fillWithColor(glId, mipLevel, type.getDefaultValue());

		for (TextureAtlasSprite sprite : sprites.values()) {
			try {
				uploadSprite(sprite);
			} catch (Exception e) {
				CrashReport crashReport = CrashReport.makeCrashReport(e, "Stitching texture atlas");
				CrashReportCategory crashReportCategory = crashReport.makeCategory("Texture being stitched together");
				crashReportCategory.addCrashSection("Atlas path", id);
				crashReportCategory.addCrashSection("Sprite", sprite);
				throw new ReportedException(crashReport);
			}
		}

		if (!animatedSprites.isEmpty() || !alwaysAnimatedSprites.isEmpty()) {
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
			TextureExporter.exportTextures("pbr_debug/atlas", id.getResourceDomain() + "_" + id.getResourcePath().replace("/", "_") + ".png", glId, mipLevel, atlasWidth, atlasHeight);
		}
	}

	public boolean tryUpload(int atlasWidth, int atlasHeight, int mipLevel, float anisotropicFiltering) {
		try {
			upload(atlasWidth, atlasHeight, mipLevel, anisotropicFiltering);
			return true;
		} catch (Exception t) {
            Iris.logger.error("Could not upload PBR texture", t);
			return false;
		}
	}

    @Override
    public void loadTexture(IResourceManager manager) throws IOException {
        // todo
    }

    protected void uploadSprite(TextureAtlasSprite sprite) {
        final int gutter = ((SpriteExtension) sprite).angelica$getGutterWidth();

		if (sprite.animationMetadata.getFrameCount() > 0) {
			final AnimationMetadataSection metadata = sprite.animationMetadata;
			final int frameCount = sprite.getFrameCount();
			for (int frame = sprite.frameCounter; frame >= 0; frame--) {
				final int frameIndex = metadata.getFrameIndex(frame);
				if (frameIndex >= 0 && frameIndex < frameCount) {
                    SpritePadding.uploadPadded(sprite.getFrameTextureData(frameIndex), sprite.getIconWidth(), sprite.getIconHeight(), sprite.getOriginX(), sprite.getOriginY(), gutter, false, false);
					return;
				}
			}
		}
		SpritePadding.uploadPadded(sprite.getFrameTextureData(0), sprite.getIconWidth(), sprite.getIconHeight(), sprite.getOriginX(), sprite.getOriginY(), gutter, false, false);
	}

	public void cycleAnimationFrames() {
		final boolean onlyVisible = ClientProxy.options().performance.animateOnlyVisibleTextures;
		bind();
		int updated = alwaysAnimatedSprites.size();
		for (int i = 0; i < animatedSprites.size(); i++) {
			if (animatedSprites.get(i).angelica$tickAnimation(onlyVisible)) updated++;
		}
		for (int i = 0; i < alwaysAnimatedSprites.size(); i++) {
			alwaysAnimatedSprites.get(i).updateAnimation();
		}
		DebugCounters.PBR_ANIM_UPDATES.add(updated);
		DebugCounters.PBR_ANIM_DRY_RUNS.add(animatedSprites.size() + alwaysAnimatedSprites.size() - updated);
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

        clear();
        super.close();
	}
}
