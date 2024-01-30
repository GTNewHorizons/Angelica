package net.coderbot.iris.texture.pbr.loader;

import com.google.common.collect.Lists;
import com.gtnewhorizons.angelica.compat.mojang.NativeImage;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfo;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfoCache;
import com.gtnewhorizons.angelica.mixins.early.shaders.accessors.AnimationMetadataSectionAccessor;
import com.gtnewhorizons.angelica.mixins.early.shaders.accessors.TextureAtlasSpriteAccessor;
import com.gtnewhorizons.angelica.mixins.early.shaders.accessors.TextureMapAccessor;
import com.gtnewhorizons.angelica.mixins.interfaces.ISpriteExt;
import net.coderbot.iris.Iris;
import net.coderbot.iris.texture.format.TextureFormat;
import net.coderbot.iris.texture.format.TextureFormatLoader;
import net.coderbot.iris.texture.mipmap.ChannelMipmapGenerator;
import net.coderbot.iris.texture.mipmap.CustomMipmapGenerator;
import net.coderbot.iris.texture.mipmap.LinearBlendFunction;
import net.coderbot.iris.texture.pbr.PBRAtlasTexture;
import net.coderbot.iris.texture.pbr.PBRSpriteHolder;
import net.coderbot.iris.texture.pbr.PBRType;
import net.coderbot.iris.texture.pbr.TextureAtlasSpriteExtension;
import net.coderbot.iris.texture.util.ImageManipulationUtil;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;


public class AtlasPBRLoader implements PBRTextureLoader<TextureMap> {
    public static final ChannelMipmapGenerator LINEAR_MIPMAP_GENERATOR = new ChannelMipmapGenerator(
        LinearBlendFunction.INSTANCE,
        LinearBlendFunction.INSTANCE,
        LinearBlendFunction.INSTANCE,
        LinearBlendFunction.INSTANCE
    );

    @Override
    public void load(TextureMap texMap, IResourceManager resourceManager, PBRTextureConsumer pbrTextureConsumer) {
        final TextureInfo textureInfo = TextureInfoCache.INSTANCE.getInfo(texMap.getGlTextureId());
        final int atlasWidth = textureInfo.getWidth();
        final int atlasHeight = textureInfo.getHeight();
        final int mipLevel = fetchAtlasMipLevel(texMap);

        PBRAtlasTexture normalAtlas = null;
        PBRAtlasTexture specularAtlas = null;
        for (TextureAtlasSprite sprite : (Collection<TextureAtlasSprite>) ((TextureMapAccessor) texMap).getMapUploadedSprites().values()) {
            if (!(sprite.getIconName().equals("missingno"))) {
                TextureAtlasSprite normalSprite = createPBRSprite(sprite, resourceManager, texMap, atlasWidth, atlasHeight, mipLevel, PBRType.NORMAL);
                TextureAtlasSprite specularSprite = createPBRSprite(sprite, resourceManager, texMap, atlasWidth, atlasHeight, mipLevel, PBRType.SPECULAR);
                if (normalSprite != null) {
                    if (normalAtlas == null) {
                        normalAtlas = new PBRAtlasTexture(texMap, PBRType.NORMAL);
                    }
                    normalAtlas.addSprite(normalSprite);
                    final PBRSpriteHolder pbrSpriteHolder = ((TextureAtlasSpriteExtension) sprite).getOrCreatePBRHolder();
                    pbrSpriteHolder.setNormalSprite(normalSprite);
                }
                if (specularSprite != null) {
                    if (specularAtlas == null) {
                        specularAtlas = new PBRAtlasTexture(texMap, PBRType.SPECULAR);
                    }
                    specularAtlas.addSprite(specularSprite);
                    final PBRSpriteHolder pbrSpriteHolder = ((TextureAtlasSpriteExtension) sprite).getOrCreatePBRHolder();
                    pbrSpriteHolder.setSpecularSprite(specularSprite);
                }
            }
        }

        if (normalAtlas != null) {
            if (normalAtlas.tryUpload(atlasWidth,
                atlasHeight,
                mipLevel,
                ((TextureMapAccessor) texMap).getAnisotropicFiltering())) {
                pbrTextureConsumer.acceptNormalTexture(normalAtlas);
            }
        }
        if (specularAtlas != null) {
            if (specularAtlas.tryUpload(atlasWidth, atlasHeight, mipLevel, ((TextureMapAccessor) texMap).getAnisotropicFiltering())) {
                pbrTextureConsumer.acceptSpecularTexture(specularAtlas);
            }
        }
    }

    protected static int fetchAtlasMipLevel(TextureMap texMap) {
        return ((TextureMapAccessor) texMap).getMipmapLevels();
    }

    @Nullable
    protected TextureAtlasSprite createPBRSprite(TextureAtlasSprite sprite, IResourceManager resourceManager, TextureMap texMap, int atlasWidth, int atlasHeight, int mipLevel, PBRType pbrType) {
        final ResourceLocation spriteName = new ResourceLocation(sprite.getIconName());
        final ResourceLocation imageLocation = texMap.completeResourceLocation(spriteName, 0);
        final ResourceLocation pbrImageLocation = pbrType.appendToFileLocation(imageLocation);

        TextureAtlasSprite pbrSprite = null;

        try  {
            // This is no longer closable. Not sure about this.
            final IResource resource = resourceManager.getResource(pbrImageLocation);
            NativeImage nativeImage = NativeImage.read(resource.getInputStream());
            AnimationMetadataSection animationMetadata = (AnimationMetadataSection) resource.getMetadata("animation");
            if (animationMetadata == null) {
                animationMetadata = new AnimationMetadataSection(Lists.newArrayList(), -1, -1, -1);
            }

            final Pair<Integer, Integer> frameSize = this.getFrameSize(nativeImage.getWidth(), nativeImage.getHeight(), animationMetadata);
            int frameWidth = frameSize.getLeft();
            int frameHeight = frameSize.getRight();
            final int targetFrameWidth = sprite.getIconWidth();
            final int targetFrameHeight = sprite.getIconHeight();
            if (frameWidth != targetFrameWidth || frameHeight != targetFrameHeight) {
                final int imageWidth = nativeImage.getWidth();
                final int imageHeight = nativeImage.getHeight();

                // We can assume the following is always true as a result of getFrameSize's check:
                // imageWidth % frameWidth == 0 && imageHeight % frameHeight == 0
                final int targetImageWidth = imageWidth / frameWidth * targetFrameWidth;
                final int targetImageHeight = imageHeight / frameHeight * targetFrameHeight;

                final NativeImage scaledImage;
                if (targetImageWidth % imageWidth == 0 && targetImageHeight % imageHeight == 0) {
                    scaledImage = ImageManipulationUtil.scaleNearestNeighbor(nativeImage, targetImageWidth, targetImageHeight);
                } else {
                    scaledImage = ImageManipulationUtil.scaleBilinear(nativeImage, targetImageWidth, targetImageHeight);
                }

                // This is no longer closeable either
//                nativeImage.close();
                nativeImage = scaledImage;

                frameWidth = targetFrameWidth;
                frameHeight = targetFrameHeight;

                if (!animationMetadata.equals(new AnimationMetadataSection(Lists.newArrayList(), -1, -1, -1))) {
                    final AnimationMetadataSectionAccessor animationAccessor = (AnimationMetadataSectionAccessor) animationMetadata;
                    final int internalFrameWidth = animationAccessor.getFrameHeight();
                    final int internalFrameHeight = animationAccessor.getFrameHeight();
                    if (internalFrameWidth != -1) {
                        animationAccessor.setFrameWidth(frameWidth);
                    }
                    if (internalFrameHeight != -1) {
                        animationAccessor.setFrameHeight(frameHeight);
                    }
                }
            }

            final ResourceLocation pbrSpriteName = new ResourceLocation(spriteName.getResourceDomain(), spriteName.getResourcePath() + pbrType.getSuffix());
            final TextureAtlasSpriteInfo pbrSpriteInfo = new PBRTextureAtlasSpriteInfo(pbrSpriteName, frameWidth, frameHeight, animationMetadata, pbrType);

            final int x = sprite.getOriginX();
            final int y = sprite.getOriginY();
            pbrSprite = new PBRTextureAtlasSprite(pbrSpriteInfo, animationMetadata, atlasWidth, atlasHeight, x, y, nativeImage, texMap, mipLevel);
            syncAnimation(sprite, pbrSprite);
        } catch (FileNotFoundException e) {
            //
        } catch (RuntimeException e) {
            Iris.logger.error("Unable to parse metadata from {} : {}", pbrImageLocation, e);
        } catch (IOException e) {
            Iris.logger.error("Unable to load {} : {}", pbrImageLocation, e);
        }

        return pbrSprite;
    }


	protected void syncAnimation(TextureAtlasSprite source, TextureAtlasSprite target) {
        if (!((ISpriteExt)source).isAnimation() || !((ISpriteExt)target).isAnimation()) {
			return;
		}

        final TextureAtlasSpriteAccessor sourceAccessor = ((TextureAtlasSpriteAccessor) source);
        final AnimationMetadataSection sourceMetadata = sourceAccessor.getMetadata();

		int ticks = 0;
		for (int f = 0; f < sourceAccessor.getFrame(); f++) {
			ticks += sourceMetadata.getFrameTimeSingle(f);
		}

        final TextureAtlasSpriteAccessor targetAccessor = ((TextureAtlasSpriteAccessor) target);
        final AnimationMetadataSection targetMetadata = targetAccessor.getMetadata();

		int cycleTime = 0;
        final int frameCount = targetMetadata.getFrameCount();
		for (int f = 0; f < frameCount; f++) {
			cycleTime += targetMetadata.getFrameTimeSingle(f);
		}
		ticks %= cycleTime;

		int targetFrame = 0;
		while (true) {
            final int time = targetMetadata.getFrameTimeSingle(targetFrame);
			if (ticks >= time) {
				targetFrame++;
				ticks -= time;
			} else {
				break;
			}
		}

		targetAccessor.setFrame(targetFrame);
		targetAccessor.setSubFrame(ticks + sourceAccessor.getSubFrame());
	}

	protected static class PBRTextureAtlasSpriteInfo extends TextureAtlasSpriteInfo {
		protected final PBRType pbrType;

		public PBRTextureAtlasSpriteInfo(ResourceLocation name, int width, int height, AnimationMetadataSection metadata, PBRType pbrType) {
			super(name, width, height, metadata);
			this.pbrType = pbrType;
		}
	}

    public static class PBRTextureAtlasSprite extends TextureAtlasSprite implements CustomMipmapGenerator.Provider {
        // This feels super janky
        protected PBRTextureAtlasSprite(TextureAtlasSpriteInfo info, AnimationMetadataSection animationMetaDataSection, int atlasWidth, int atlasHeight, int x, int y, NativeImage nativeImage, TextureMap texMap, int miplevel) {
            super(info.name().toString());
            super.initSprite(atlasWidth, atlasHeight, x, y, false);
            super.loadSprite(getMipmapGenerator(info, atlasWidth, atlasHeight).generateMipLevels(nativeImage, miplevel), animationMetaDataSection, (float)((TextureMapAccessor) texMap).getAnisotropicFiltering() > 1.0F);
        }

        @Override
        public CustomMipmapGenerator getMipmapGenerator(TextureAtlasSpriteInfo info, int atlasWidth, int atlasHeight) {
            if (info instanceof PBRTextureAtlasSpriteInfo pbrInfo) {
                final PBRType pbrType = pbrInfo.pbrType;
                final TextureFormat format = TextureFormatLoader.getFormat();
                if (format != null) {
                    final CustomMipmapGenerator generator = format.getMipmapGenerator(pbrType);
                    if (generator != null) {
                        return generator;
                    }
                }
            }
            return LINEAR_MIPMAP_GENERATOR;
        }
    }

    private Pair<Integer, Integer> getFrameSize(int i, int j, AnimationMetadataSection animationMetadataSection) {
        final Pair<Integer, Integer> pair = this.calculateFrameSize(i, j, animationMetadataSection);
        final int k = pair.getLeft();
        final int l = pair.getRight();
        if (isDivisionInteger(i, k) && isDivisionInteger(j, l)) {
            return pair;
        } else {
            throw new IllegalArgumentException(String.format("Image size %s,%s is not multiply of frame size %s,%s", i, j, k, l));
        }
    }

    private Pair<Integer, Integer> calculateFrameSize(int i, int j, AnimationMetadataSection animationMetadataSection) {
        if (animationMetadataSection.getFrameWidth() != -1) {
            return animationMetadataSection.getFrameHeight() != -1 ? Pair.of(animationMetadataSection.getFrameWidth(), animationMetadataSection.getFrameHeight()) : Pair.of(animationMetadataSection.getFrameWidth(), j);
        } else if (animationMetadataSection.getFrameHeight() != -1) {
            return Pair.of(i, animationMetadataSection.getFrameHeight());
        } else {
            int k = Math.min(i, j);
            return Pair.of(k, k);
        }
    }

    private static boolean isDivisionInteger(int i, int j) {
        return i / j * j == i;
    }

}
