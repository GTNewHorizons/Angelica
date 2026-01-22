package net.coderbot.iris.pipeline;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.mixins.interfaces.EntityRendererAccessor;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import lombok.Getter;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.texture.GlTexture;
import net.coderbot.iris.gl.texture.TextureAccess;
import net.coderbot.iris.gl.texture.TextureType;
import net.coderbot.iris.gl.texture.TextureWrapper;
import net.coderbot.iris.rendertarget.NativeImageBackedCustomTexture;
import net.coderbot.iris.rendertarget.NativeImageBackedNoiseTexture;
import net.coderbot.iris.shaderpack.PackDirectives;
import net.coderbot.iris.shaderpack.texture.CustomTextureData;
import net.coderbot.iris.shaderpack.texture.TextureStage;
import net.coderbot.iris.texture.format.TextureFormat;
import net.coderbot.iris.texture.format.TextureFormatLoader;
import net.coderbot.iris.texture.pbr.PBRTextureHolder;
import net.coderbot.iris.texture.pbr.PBRTextureManager;
import net.coderbot.iris.texture.pbr.PBRType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.io.FilenameUtils;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;

public class CustomTextureManager {
	@Getter private final EnumMap<TextureStage, Object2ObjectMap<String, TextureAccess>> customTextureIdMap = new EnumMap<>(TextureStage.class);
	@Getter private final Object2ObjectMap<String, TextureAccess> irisCustomTextures = new Object2ObjectOpenHashMap<>();
	private final TextureAccess noise;

	/**
	 * List of all OpenGL texture objects owned by this CustomTextureManager that need to be deleted in order to avoid leaks.
	 * Make sure any textures added to this list call releaseId from the close method.
	 */
	private final List<AbstractTexture> ownedTextures = new ArrayList<>();
	private final List<GlTexture> ownedRawTextures = new ArrayList<>();

	public CustomTextureManager(PackDirectives packDirectives,
								EnumMap<TextureStage, Object2ObjectMap<String, CustomTextureData>> customTextureDataMap,
								Object2ObjectMap<String, CustomTextureData> irisCustomTextureDataMap,
								Optional<CustomTextureData> customNoiseTextureData) {
		customTextureDataMap.forEach((textureStage, customTextureStageDataMap) -> {
			final Object2ObjectMap<String, TextureAccess> customTextureIds = new Object2ObjectOpenHashMap<>();

			customTextureStageDataMap.forEach((samplerName, textureData) -> {
				try {
					customTextureIds.put(samplerName, createCustomTexture(textureData));
				} catch (IOException e) {
					Iris.logger.error("Unable to parse the image data for the custom texture on stage "
							+ textureStage + ", sampler " + samplerName, e);
				}
			});

			customTextureIdMap.put(textureStage, customTextureIds);
		});

		irisCustomTextureDataMap.forEach((name, texture) -> {
			try {
				irisCustomTextures.put(name, createCustomTexture(texture));
			} catch (IOException e) {
				Iris.logger.error("Unable to parse the image data for the custom texture on sampler " + name, e);
			}
		});

		noise = customNoiseTextureData.flatMap(textureData -> {
			try {
				return Optional.of(createCustomTexture(textureData));
			} catch (IOException e) {
				Iris.logger.error("Unable to parse the image data for the custom noise texture", e);

				return Optional.empty();
			}
		}).orElseGet(() -> {
			final int noiseTextureResolution = packDirectives.getNoiseTextureResolution();

			final AbstractTexture texture = new NativeImageBackedNoiseTexture(noiseTextureResolution);
			ownedTextures.add(texture);

			return new TextureWrapper(texture::getGlTextureId, TextureType.TEXTURE_2D);
		});
	}

	private TextureAccess createCustomTexture(CustomTextureData textureData) throws IOException {
		if (textureData instanceof CustomTextureData.PngData) {
			final AbstractTexture texture = new NativeImageBackedCustomTexture((CustomTextureData.PngData) textureData);
			ownedTextures.add(texture);

			return new TextureWrapper(texture::getGlTextureId, TextureType.TEXTURE_2D);
		} else if (textureData instanceof CustomTextureData.LightmapMarker) {
			// Special code path for the light texture. While shader packs hardcode the primary light texture, it's possible that a mod will
			// create a different light texture, so this code path is robust to that.
			return new TextureWrapper(() -> ((EntityRendererAccessor) Minecraft.getMinecraft().entityRenderer).getLightmapTexture().getGlTextureId(), TextureType.TEXTURE_2D);
		} else if (textureData instanceof CustomTextureData.RawData1D rawData) {
			final GlTexture texture = new GlTexture(TextureType.TEXTURE_1D, rawData.getSizeX(), 0, 0, rawData.getInternalFormat().getGlFormat(), rawData.getPixelFormat().getGlFormat(), rawData.getPixelType().getGlFormat(), rawData.getContent(), rawData.getFilteringData());
			ownedRawTextures.add(texture);
			return texture;
		} else if (textureData instanceof CustomTextureData.RawDataRect rawData) {
			final GlTexture texture = new GlTexture(TextureType.TEXTURE_RECTANGLE, rawData.getSizeX(), rawData.getSizeY(), 0, rawData.getInternalFormat().getGlFormat(), rawData.getPixelFormat().getGlFormat(), rawData.getPixelType().getGlFormat(), rawData.getContent(), rawData.getFilteringData());
			ownedRawTextures.add(texture);
			return texture;
		} else if (textureData instanceof CustomTextureData.RawData2D rawData) {
			final GlTexture texture = new GlTexture(TextureType.TEXTURE_2D, rawData.getSizeX(), rawData.getSizeY(), 0, rawData.getInternalFormat().getGlFormat(), rawData.getPixelFormat().getGlFormat(), rawData.getPixelType().getGlFormat(), rawData.getContent(), rawData.getFilteringData());
			ownedRawTextures.add(texture);
			return texture;
		} else if (textureData instanceof CustomTextureData.RawData3D rawData) {
			final GlTexture texture = new GlTexture(TextureType.TEXTURE_3D, rawData.getSizeX(), rawData.getSizeY(), rawData.getSizeZ(), rawData.getInternalFormat().getGlFormat(), rawData.getPixelFormat().getGlFormat(), rawData.getPixelType().getGlFormat(), rawData.getContent(), rawData.getFilteringData());
			ownedRawTextures.add(texture);
			return texture;
		} else if (textureData instanceof CustomTextureData.ResourceData resourceData) {
            final String namespace = resourceData.getNamespace();
			String location = resourceData.getLocation();

			final String withoutExtension;
			final int extensionIndex = FilenameUtils.indexOfExtension(location);
			if (extensionIndex != -1) {
				withoutExtension = location.substring(0, extensionIndex);
			} else {
				withoutExtension = location;
			}
			final PBRType pbrType = PBRType.fromFileLocation(withoutExtension);

			final TextureManager textureManager = Minecraft.getMinecraft().getTextureManager();

			if (pbrType == null) {
				final ResourceLocation textureLocation = new ResourceLocation(namespace, location);

				// NB: We have to re-query the TextureManager for the texture object every time. This is because the
				//     AbstractTexture object could be removed / deleted from the TextureManager on resource reloads,
				//     and we could end up holding on to a deleted texture unless we added special code to handle resource
				//     reloads. Re-fetching the texture from the TextureManager every time is the most robust approach for
				//     now.
				return new TextureWrapper(() -> {
					final ITextureObject texture = textureManager.getTexture(textureLocation);
					return texture != null ? texture.getGlTextureId() : TextureUtil.missingTexture.getGlTextureId();
				}, TextureType.TEXTURE_2D);
			} else {
				location = location.substring(0, extensionIndex - pbrType.getSuffix().length()) + location.substring(extensionIndex);
				final ResourceLocation textureLocation = new ResourceLocation(namespace, location);

				return new TextureWrapper(() -> {
					final ITextureObject texture = textureManager.getTexture(textureLocation);

					if (texture != null) {
						final int id = texture.getGlTextureId();
						final PBRTextureHolder pbrHolder = PBRTextureManager.INSTANCE.getOrLoadHolder(id);
						final AbstractTexture pbrTexture = switch (pbrType) {
                            case NORMAL -> pbrHolder.getNormalTexture();
                            case SPECULAR -> pbrHolder.getSpecularTexture();
                            default -> throw new IllegalArgumentException("Unknown PBRType '" + pbrType + "'");
                        };

						final TextureFormat textureFormat = TextureFormatLoader.getFormat();
						if (textureFormat != null) {
							final int previousBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
							GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, pbrTexture.getGlTextureId());
							textureFormat.setupTextureParameters(pbrType, pbrTexture);
							GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, previousBinding);
						}

						return pbrTexture.getGlTextureId();
					}

                    return TextureUtil.missingTexture.getGlTextureId();
				}, TextureType.TEXTURE_2D);
			}
		}
		throw new IllegalArgumentException("Unable to handle custom texture data " + textureData);
	}

    public Object2ObjectMap<String, TextureAccess> getCustomTextureIdMap(TextureStage stage) {
		return customTextureIdMap.getOrDefault(stage, Object2ObjectMaps.emptyMap());
	}

	public TextureAccess getNoiseTexture() {
		return noise;
	}

    public void destroy() {
		ownedTextures.forEach(AbstractTexture::deleteGlTexture);
		ownedTextures.clear();

		ownedRawTextures.forEach(GlTexture::destroy);
		ownedRawTextures.clear();
	}
}
