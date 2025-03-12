package net.coderbot.iris.pipeline;

import com.gtnewhorizons.angelica.glsm.managers.GLTextureManager;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.coderbot.iris.Iris;
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
import java.util.function.IntSupplier;

public class CustomTextureManager {
	private final EnumMap<TextureStage, Object2ObjectMap<String, IntSupplier>> customTextureIdMap = new EnumMap<>(TextureStage.class);
	private final IntSupplier noise;

	/**
	 * List of all OpenGL texture objects owned by this CustomTextureManager that need to be deleted in order to avoid
	 * leaks.
	 * Make sure any textures added to this list call releaseId from the close method.
	 */
	private final List<AbstractTexture> ownedTextures = new ArrayList<>();

	public CustomTextureManager(PackDirectives packDirectives,
								EnumMap<TextureStage, Object2ObjectMap<String, CustomTextureData>> customTextureDataMap,
								Optional<CustomTextureData> customNoiseTextureData) {
		customTextureDataMap.forEach((textureStage, customTextureStageDataMap) -> {
			Object2ObjectMap<String, IntSupplier> customTextureIds = new Object2ObjectOpenHashMap<>();

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

		noise = customNoiseTextureData.flatMap(textureData -> {
			try {
				return Optional.of(createCustomTexture(textureData));
			} catch (IOException e) {
				Iris.logger.error("Unable to parse the image data for the custom noise texture", e);

				return Optional.empty();
			}
		}).orElseGet(() -> {
			final int noiseTextureResolution = packDirectives.getNoiseTextureResolution();

			AbstractTexture texture = new NativeImageBackedNoiseTexture(noiseTextureResolution);
			ownedTextures.add(texture);

			return texture::getGlTextureId;
		});
	}

	private IntSupplier createCustomTexture(CustomTextureData textureData) throws IOException {
		if (textureData instanceof CustomTextureData.PngData) {
			AbstractTexture texture = new NativeImageBackedCustomTexture((CustomTextureData.PngData) textureData);
			ownedTextures.add(texture);

			return texture::getGlTextureId;
		}
        // TODO: Iris
//        else if (textureData instanceof CustomTextureData.LightmapMarker) {
//			// Special code path for the light texture. While shader packs hardcode the primary light texture, it's
//			// possible that a mod will create a different light texture, so this code path is robust to that.
//			return () -> ((LightTextureAccessor) Minecraft.getMinecraft().gameRenderer.lightTexture()).getLightTexture().getId();
//		}
        else if (textureData instanceof CustomTextureData.ResourceData) {
			CustomTextureData.ResourceData resourceData = (CustomTextureData.ResourceData) textureData;
			String namespace = resourceData.getNamespace();
			String location = resourceData.getLocation();

			String withoutExtension;
			int extensionIndex = FilenameUtils.indexOfExtension(location);
			if (extensionIndex != -1) {
				withoutExtension = location.substring(0, extensionIndex);
			} else {
				withoutExtension = location;
			}
			PBRType pbrType = PBRType.fromFileLocation(withoutExtension);

			TextureManager textureManager = Minecraft.getMinecraft().getTextureManager();

			if (pbrType == null) {
				ResourceLocation textureLocation = new ResourceLocation(namespace, location);

				// NB: We have to re-query the TextureManager for the texture object every time. This is because the
				//     AbstractTexture object could be removed / deleted from the TextureManager on resource reloads,
				//     and we could end up holding on to a deleted texture unless we added special code to handle resource
				//     reloads. Re-fetching the texture from the TextureManager every time is the most robust approach for
				//     now.
				return () -> {
					ITextureObject texture = textureManager.getTexture(textureLocation);

					// TODO: Should we give something else if the texture isn't there? This will need some thought
					return texture != null ? texture.getGlTextureId() : TextureUtil.missingTexture.getGlTextureId();
				};
			} else {
				location = location.substring(0, extensionIndex - pbrType.getSuffix().length()) + location.substring(extensionIndex);
				ResourceLocation textureLocation = new ResourceLocation(namespace, location);

				return () -> {
                    ITextureObject texture = textureManager.getTexture(textureLocation);

					if (texture != null) {
						int id = texture.getGlTextureId();
						PBRTextureHolder pbrHolder = PBRTextureManager.INSTANCE.getOrLoadHolder(id);
						AbstractTexture pbrTexture = switch (pbrType) {
                            case NORMAL -> pbrHolder.getNormalTexture();
                            case SPECULAR -> pbrHolder.getSpecularTexture();
                            default -> throw new Error("Unknown PBRType '" + pbrType + "'");
                        };

                        TextureFormat textureFormat = TextureFormatLoader.getFormat();
						if (textureFormat != null) {
							int previousBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
							GLTextureManager.glBindTexture(GL11.GL_TEXTURE_2D, pbrTexture.getGlTextureId());
							textureFormat.setupTextureParameters(pbrType, pbrTexture);
							GLTextureManager.glBindTexture(GL11.GL_TEXTURE_2D, previousBinding);
						}

						return pbrTexture.getGlTextureId();
					}

//					return MissingTextureAtlasSprite.getTexture().getId();
                    return TextureUtil.missingTexture.getGlTextureId();
				};
			}
		} else {
			throw new IllegalArgumentException("Unable to handle custom texture data " + textureData);
		}
	}

	public EnumMap<TextureStage, Object2ObjectMap<String, IntSupplier>> getCustomTextureIdMap() {
		return customTextureIdMap;
	}

	public Object2ObjectMap<String, IntSupplier> getCustomTextureIdMap(TextureStage stage) {
		return customTextureIdMap.getOrDefault(stage, Object2ObjectMaps.emptyMap());
	}

	public IntSupplier getNoiseTexture() {
		return noise;
	}

	public void destroy() {
        // TODO :Iris
//		ownedTextures.forEach(AbstractTexture::close);
	}
}
