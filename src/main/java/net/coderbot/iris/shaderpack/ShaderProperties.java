package net.coderbot.iris.shaderpack;

import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.states.BlendState;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.coderbot.iris.gl.buffer.ShaderStorageInfo;
import net.coderbot.iris.gl.framebuffer.ViewportData;
import net.coderbot.iris.gl.image.ImageInformation;
import net.coderbot.iris.gl.texture.InternalTextureFormat;
import net.coderbot.iris.gl.texture.PixelFormat;
import net.coderbot.iris.gl.texture.PixelType;
import net.coderbot.iris.gl.texture.TextureDefinition;
import net.coderbot.iris.gl.texture.TextureType;
import lombok.Getter;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.blending.AlphaTest;
import net.coderbot.iris.gl.blending.AlphaTestFunction;
import net.coderbot.iris.gl.blending.AlphaTestOverride;
import net.coderbot.iris.gl.blending.BlendModeFunction;
import net.coderbot.iris.gl.blending.BlendModeOverride;
import net.coderbot.iris.gl.blending.BufferBlendInformation;
import net.coderbot.iris.gl.texture.TextureScaleOverride;
import net.coderbot.iris.helpers.Tri;
import net.coderbot.iris.pipeline.PatchedShaderPrinter;
import net.coderbot.iris.shaderpack.option.ShaderPackOptions;
import net.coderbot.iris.shaderpack.preprocessor.PropertiesPreprocessor;
import net.coderbot.iris.shaderpack.texture.TextureStage;
import net.coderbot.iris.uniforms.custom.CustomUniforms;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The parsed representation of the shaders.properties file. This class is not meant to be stored permanently, rather
 * it merely exists as an intermediate step until we build up PackDirectives and ProgramDirectives objects from the
 * values in here & the values parsed from shader source code.
 */
public class ShaderProperties {
	@Getter private final CustomUniforms.Builder customUniforms = new CustomUniforms.Builder();
	@Getter private CloudSetting cloudSetting = CloudSetting.DEFAULT;
	@Getter private CloudSetting dhCloudSetting = CloudSetting.DEFAULT;
	@Getter private OptionalBoolean oldHandLight = OptionalBoolean.DEFAULT;
	@Getter private OptionalBoolean dynamicHandLight = OptionalBoolean.DEFAULT;
	@Getter private OptionalBoolean supportsColorCorrection = OptionalBoolean.DEFAULT;
	@Getter private OptionalBoolean oldLighting = OptionalBoolean.DEFAULT;
	@Getter private OptionalBoolean shadowTerrain = OptionalBoolean.DEFAULT;
	@Getter private OptionalBoolean shadowTranslucent = OptionalBoolean.DEFAULT;
	@Getter private OptionalBoolean shadowEntities = OptionalBoolean.DEFAULT;
	@Getter private OptionalBoolean shadowPlayer = OptionalBoolean.DEFAULT;
	@Getter private OptionalBoolean shadowBlockEntities = OptionalBoolean.DEFAULT;
	@Getter private OptionalBoolean shadowLightBlockEntities = OptionalBoolean.DEFAULT;
	@Getter private OptionalBoolean underwaterOverlay = OptionalBoolean.DEFAULT;
	@Getter private OptionalBoolean sun = OptionalBoolean.DEFAULT;
	@Getter private OptionalBoolean moon = OptionalBoolean.DEFAULT;
	@Getter private OptionalBoolean vignette = OptionalBoolean.DEFAULT;
	@Getter private OptionalBoolean backFaceSolid = OptionalBoolean.DEFAULT;
	@Getter private OptionalBoolean backFaceCutout = OptionalBoolean.DEFAULT;
	@Getter private OptionalBoolean backFaceCutoutMipped = OptionalBoolean.DEFAULT;
	@Getter private OptionalBoolean backFaceTranslucent = OptionalBoolean.DEFAULT;
	@Getter private OptionalBoolean rainDepth = OptionalBoolean.DEFAULT;
	@Getter private OptionalBoolean concurrentCompute = OptionalBoolean.DEFAULT;
	@Getter private OptionalBoolean beaconBeamDepth = OptionalBoolean.DEFAULT;
	@Getter private OptionalBoolean separateAo = OptionalBoolean.DEFAULT;
	@Getter private OptionalBoolean voxelizeLightBlocks = OptionalBoolean.DEFAULT;
	@Getter private OptionalBoolean separateEntityDraws = OptionalBoolean.DEFAULT;
	@Getter private OptionalBoolean frustumCulling = OptionalBoolean.DEFAULT;
	@Getter private OptionalBoolean occlusionCulling = OptionalBoolean.DEFAULT;
	@Getter private ShadowCullState shadowCulling = ShadowCullState.DEFAULT;
	@Getter private OptionalBoolean shadowEnabled = OptionalBoolean.DEFAULT;
	@Getter private OptionalBoolean dhShadowEnabled = OptionalBoolean.DEFAULT;
	private Optional<ParticleRenderingSettings> particleRenderingSettings = Optional.empty();
	@Getter private OptionalBoolean prepareBeforeShadow = OptionalBoolean.DEFAULT;
	@Getter private List<String> sliderOptions = new ArrayList<>();
	@Getter private final Map<String, List<String>> profiles = new LinkedHashMap<>();
	private List<String> mainScreenOptions = null;
	@Getter private final Map<String, List<String>> subScreenOptions = new HashMap<>();
	private Integer mainScreenColumnCount = null;
	@Getter private final Map<String, Integer> subScreenColumnCount = new HashMap<>();
	@Getter private int fallbackTex = 0;
	// TODO: private Map<String, String> optifineVersionRequirements;
	// TODO: Parse custom uniforms / variables
	@Getter private final Object2ObjectMap<String, AlphaTestOverride> alphaTestOverrides = new Object2ObjectOpenHashMap<>();
	@Getter private final Object2ObjectMap<String, ViewportData> viewportScaleOverrides = new Object2ObjectOpenHashMap<>();
	@Getter private final Object2ObjectMap<String, TextureScaleOverride> textureScaleOverrides = new Object2ObjectOpenHashMap<>();
	@Getter private final Object2ObjectMap<String, BlendModeOverride> blendModeOverrides = new Object2ObjectOpenHashMap<>();
	@Getter private final Object2ObjectMap<String, IndirectPointer> indirectPointers = new Object2ObjectOpenHashMap<>();
	@Getter private final Object2ObjectMap<String, ArrayList<BufferBlendInformation>> bufferBlendOverrides = new Object2ObjectOpenHashMap<>();
	@Getter private final EnumMap<TextureStage, Object2ObjectMap<String, TextureDefinition>> customTextures = new EnumMap<>(TextureStage.class);
	@Getter private final Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> customTexturePatching = new Object2ObjectOpenHashMap<>();
	@Getter private final Object2ObjectMap<String, Object2BooleanMap<String>> explicitFlips = new Object2ObjectOpenHashMap<>();
	private String noiseTexturePath = null;
	@Getter private Object2ObjectMap<String, String> conditionallyEnabledPrograms = new Object2ObjectOpenHashMap<>();
	@Getter private List<String> requiredFeatureFlags = new ArrayList<>();
	@Getter private List<String> optionalFeatureFlags = new ArrayList<>();
	@Getter private final Object2ObjectMap<String, ImageInformation> customImages = new Object2ObjectOpenHashMap<>();
	@Getter private final Int2ObjectMap<ShaderStorageInfo> bufferObjects = new Int2ObjectOpenHashMap<>();
	@Getter private final Object2ObjectMap<String, TextureDefinition> irisCustomTextures = new Object2ObjectOpenHashMap<>();
	private int customTexAmount;

	private ShaderProperties() {
		// empty
	}

	// TODO: Is there a better solution than having ShaderPack pass a root path to ShaderProperties to be able to read textures?
	public ShaderProperties(String contents, ShaderPackOptions shaderPackOptions, Iterable<StringPair> environmentDefines) {
        final String preprocessedContents = PropertiesPreprocessor.preprocessSource(contents, shaderPackOptions, environmentDefines);

		if (PatchedShaderPrinter.prettyPrintShaders) {
			try {
				Files.write(Minecraft.getMinecraft().mcDataDir.toPath().resolve("preprocessed.properties"), preprocessedContents.getBytes(StandardCharsets.UTF_8));
				Files.write(Minecraft.getMinecraft().mcDataDir.toPath().resolve("original.properties"), contents.getBytes(StandardCharsets.UTF_8));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

        final Properties preprocessed = new OrderBackedProperties();
        final Properties original = new OrderBackedProperties();
		try {
			preprocessed.load(new StringReader(preprocessedContents));
			original.load(new StringReader(contents));
		} catch (IOException e) {
			Iris.logger.error("Error loading shaders.properties!", e);
		}

		preprocessed.forEach((keyObject, valueObject) -> {
            final String key = (String) keyObject;
            final String value = (String) valueObject;

			if ("texture.noise".equals(key)) {
				noiseTexturePath = value;
				return;
			}

			if ("clouds".equals(key)) {
				if ("off".equals(value)) {
					cloudSetting = CloudSetting.OFF;
				} else if ("fast".equals(value)) {
					cloudSetting = CloudSetting.FAST;
				} else if ("fancy".equals(value)) {
					cloudSetting = CloudSetting.FANCY;
				} else {
					Iris.logger.error("Unrecognized clouds setting: " + value);
				}

				if (dhCloudSetting == CloudSetting.DEFAULT) {
					dhCloudSetting = cloudSetting;
				}
			}

			if ("dhClouds".equals(key)) {
				if ("off".equals(value)) {
					dhCloudSetting = CloudSetting.OFF;
				} else if ("on".equals(value) || "fancy".equals(value)) {
					dhCloudSetting = CloudSetting.FANCY;
				} else {
					Iris.logger.error("Unrecognized DH clouds setting (need off, on): " + value);
				}
			}

			if ("shadow.culling".equals(key)) {
				if ("false".equals(value)) {
					shadowCulling = ShadowCullState.DISTANCE;
				} else if ("true".equals(value)) {
					shadowCulling = ShadowCullState.ADVANCED;
				} else if ("reversed".equals(value)) {
					shadowCulling = ShadowCullState.REVERSED;
				} else {
					Iris.logger.error("Unrecognized shadow culling setting: " + value);
				}
			}

			handleBooleanDirective(key, value, "oldHandLight", bool -> oldHandLight = bool);
			handleBooleanDirective(key, value, "dynamicHandLight", bool -> dynamicHandLight = bool);
			handleBooleanDirective(key, value, "oldLighting", bool -> oldLighting = bool);
			handleBooleanDirective(key, value, "shadowTerrain", bool -> shadowTerrain = bool);
			handleBooleanDirective(key, value, "shadowTranslucent", bool -> shadowTranslucent = bool);
			handleBooleanDirective(key, value, "shadowEntities", bool -> shadowEntities = bool);
			handleBooleanDirective(key, value, "shadowPlayer", bool -> shadowPlayer = bool);
			handleBooleanDirective(key, value, "shadowBlockEntities", bool -> shadowBlockEntities = bool);
			handleBooleanDirective(key, value, "shadowLightBlockEntities", bool -> shadowLightBlockEntities = bool);
			handleBooleanDirective(key, value, "underwaterOverlay", bool -> underwaterOverlay = bool);
			handleBooleanDirective(key, value, "sun", bool -> sun = bool);
			handleBooleanDirective(key, value, "moon", bool -> moon = bool);
			handleBooleanDirective(key, value, "vignette", bool -> vignette = bool);
			handleBooleanDirective(key, value, "backFace.solid", bool -> backFaceSolid = bool);
			handleBooleanDirective(key, value, "backFace.cutout", bool -> backFaceCutout = bool);
			handleBooleanDirective(key, value, "backFace.cutoutMipped", bool -> backFaceCutoutMipped = bool);
			handleBooleanDirective(key, value, "backFace.translucent", bool -> backFaceTranslucent = bool);
			handleBooleanDirective(key, value, "rain.depth", bool -> rainDepth = bool);
			handleBooleanDirective(key, value, "allowConcurrentCompute", bool -> concurrentCompute = bool);
			handleBooleanDirective(key, value, "beacon.beam.depth", bool -> beaconBeamDepth = bool);
			handleBooleanDirective(key, value, "separateAo", bool -> separateAo = bool);
			handleBooleanDirective(key, value, "voxelizeLightBlocks", bool -> voxelizeLightBlocks = bool);
			handleBooleanDirective(key, value, "separateEntityDraws", bool -> separateEntityDraws = bool);
			handleBooleanDirective(key, value, "frustum.culling", bool -> frustumCulling = bool);
			handleBooleanDirective(key, value, "occlusion.culling", bool -> occlusionCulling = bool);
			handleBooleanDirective(key, value, "shadow.enabled", bool -> shadowEnabled = bool);
			handleBooleanDirective(key, value, "dhShadow.enabled", bool -> dhShadowEnabled = bool);
			handleBooleanDirective(key, value, "particles.before.deferred", bool -> {
				if (bool.orElse(false) && particleRenderingSettings.isEmpty()) {
					particleRenderingSettings = Optional.of(ParticleRenderingSettings.BEFORE);
				}
			});
			handleBooleanDirective(key, value, "prepareBeforeShadow", bool -> prepareBeforeShadow = bool);
			handleBooleanDirective(key, value, "supportsColorCorrection", bool -> supportsColorCorrection = bool);
			handleIntDirective(key, value, "fallbackTex", val -> fallbackTex = val);

			if (key.startsWith("particles.ordering")) {
				Optional<ParticleRenderingSettings> settings = ParticleRenderingSettings.fromString(value.trim().toUpperCase(Locale.ROOT));
				if (settings.isPresent()) {
					particleRenderingSettings = settings;
				} else {
					throw new RuntimeException("Failed to parse particle rendering order! " + value);
				}
			}

			// TODO: Min optifine versions, shader options layout / appearance / profiles
			// TODO: Custom uniforms

			handlePassDirective("scale.", key, value, pass -> {
				float scale, offsetX = 0.0f, offsetY = 0.0f;
				String[] parts = value.split(" ");

				try {
					scale = Float.parseFloat(parts[0]);

					if (parts.length > 1) {
						offsetX = Float.parseFloat(parts[1]);
						offsetY = Float.parseFloat(parts[2]);
					}
				} catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
					Iris.logger.error("Unable to parse scale directive for " + pass + ": " + value, e);
					return;
				}

				viewportScaleOverrides.put(pass, new ViewportData(scale, offsetX, offsetY));
			});

			handlePassDirective("size.buffer.", key, value, pass -> {
                final String[] parts = value.split(" ");

				if (parts.length != 2) {
					Iris.logger.error("Unable to parse size.buffer directive for " + pass + ": " + value);
					return;
				}

				textureScaleOverrides.put(pass, new TextureScaleOverride(parts[0], parts[1]));
			});

			handlePassDirective("alphaTest.", key, value, pass -> {
				if ("off".equals(value) || "false".equals(value)) {
					alphaTestOverrides.put(pass, AlphaTestOverride.OFF);
					return;
				}

                final String[] parts = value.split(" ");

				if (parts.length > 2) {
					Iris.logger.warn("Weird alpha test directive for " + pass + " contains more parts than we expected: " + value);
				} else if (parts.length < 2) {
					Iris.logger.error("Invalid alpha test directive for " + pass + ": " + value);
					return;
				}

                final Optional<AlphaTestFunction> function = AlphaTestFunction.fromString(parts[0]);

				if (!function.isPresent()) {
					Iris.logger.error("Unable to parse alpha test directive for " + pass + ", unknown alpha test function " + parts[0] + ": " + value);
					return;
				}

                final float reference;

				try {
					reference = Float.parseFloat(parts[1]);
				} catch (NumberFormatException e) {
					Iris.logger.error("Unable to parse alpha test directive for " + pass + ": " + value, e);
					return;
				}

				alphaTestOverrides.put(pass, new AlphaTestOverride(new AlphaTest(function.get(), reference)));
			});

			handlePassDirective("blend.", key, value, pass -> {
				if (pass.contains(".")) {

					if (!RenderSystem.supportsBufferBlending()) {
						throw new RuntimeException("Buffer blending is not supported on this platform, however it was attempted to be used!");
					}

					final String[] parts = pass.split("\\.");
					int index = PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.indexOf(parts[1]);

					if (index == -1 && parts[1].startsWith("colortex")) {
                        final String id = parts[1].substring("colortex".length());

						try {
							index = Integer.parseInt(id);
						} catch (NumberFormatException e) {
							throw new RuntimeException("Failed to parse buffer blend!", e);
						}
					}

					if (index == -1) {
						throw new RuntimeException("Failed to parse buffer blend! index = " + index);
					}

					if ("off".equals(value)) {
						bufferBlendOverrides.computeIfAbsent(parts[0], list -> new ArrayList<>()).add(new BufferBlendInformation(index, null));
						return;
					}

                    final String[] modeArray = value.split(" ");
                    final int[] modes = new int[modeArray.length];

					int i = 0;
					for (String modeName : modeArray) {
						modes[i] = BlendModeFunction.fromString(modeName).get().getGlId();
						i++;
					}

					bufferBlendOverrides.computeIfAbsent(parts[0], list -> new ArrayList<>()).add(new BufferBlendInformation(index, new BlendState(modes[0], modes[1], modes[2], modes[3])));

					return;
				}

				if ("off".equals(value)) {
					blendModeOverrides.put(pass, BlendModeOverride.OFF);
					return;
				}

                final String[] modeArray = value.split(" ");
                final int[] modes = new int[modeArray.length];

				int i = 0;
				for (String modeName : modeArray) {
					modes[i] = BlendModeFunction.fromString(modeName).get().getGlId();
					i++;
				}

				blendModeOverrides.put(pass, new BlendModeOverride(new BlendState(modes[0], modes[1], modes[2], modes[3])));
			});

			handleProgramEnabledDirective("program.", key, value, program -> {
				conditionallyEnabledPrograms.put(program, value);
			});

			handlePassDirective("indirect.", key, value, pass -> {
				try {
					String[] locations = value.split(" ");
					indirectPointers.put(pass, new IndirectPointer(Integer.parseInt(locations[0]), Long.parseLong(locations[1])));
				} catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
					Iris.logger.fatal("Failed to parse indirect command for " + pass + "! " + value);
				}
			});

			handleTwoArgDirective("texture.", key, value, (stageName, samplerName) -> {
				String[] parts = value.split(" ");
				// Handle sampler names with dots by taking only the first part
				samplerName = samplerName.split("\\.")[0];

				final Optional<TextureStage> optionalTextureStage = TextureStage.parse(stageName);

				if (!optionalTextureStage.isPresent()) {
					Iris.logger.warn("Unknown texture stage " + "\"" + stageName + "\"," + " ignoring custom texture directive for " + key);
					return;
				}

				final TextureStage stage = optionalTextureStage.get();

				if (parts.length > 1) {
					// Raw texture - create internal sampler name and store mapping
					String newSamplerName = "customtex" + customTexAmount;
					customTexAmount++;
					TextureType type = null;

					// Raw texture handling based on number of parts
					if (parts.length == 6) {
						// 1D texture handling
						type = TextureType.TEXTURE_1D;
						irisCustomTextures.put(newSamplerName, new TextureDefinition.RawDefinition(parts[0],
							TextureType.valueOf(parts[1].toUpperCase(Locale.ROOT)),
							InternalTextureFormat.fromString(parts[2]).orElseThrow(IllegalArgumentException::new),
							Integer.parseInt(parts[3]), 0, 0,
							PixelFormat.fromString(parts[4]).orElseThrow(IllegalArgumentException::new),
							PixelType.fromString(parts[5]).orElseThrow(IllegalArgumentException::new)));
					} else if (parts.length == 7) {
						// 2D texture handling
						type = TextureType.valueOf(parts[1].toUpperCase(Locale.ROOT));
						irisCustomTextures.put(newSamplerName, new TextureDefinition.RawDefinition(parts[0],
							TextureType.valueOf(parts[1].toUpperCase(Locale.ROOT)),
							InternalTextureFormat.fromString(parts[2]).orElseThrow(IllegalArgumentException::new),
							Integer.parseInt(parts[3]), Integer.parseInt(parts[4]), 0,
							PixelFormat.fromString(parts[5]).orElseThrow(IllegalArgumentException::new),
							PixelType.fromString(parts[6]).orElseThrow(IllegalArgumentException::new)));
					} else if (parts.length == 8) {
						// 3D texture handling
						type = TextureType.TEXTURE_3D;
						irisCustomTextures.put(newSamplerName, new TextureDefinition.RawDefinition(parts[0],
							TextureType.valueOf(parts[1].toUpperCase(Locale.ROOT)),
							InternalTextureFormat.fromString(parts[2]).orElseThrow(IllegalArgumentException::new),
							Integer.parseInt(parts[3]), Integer.parseInt(parts[4]), Integer.parseInt(parts[5]),
							PixelFormat.fromString(parts[6]).orElseThrow(IllegalArgumentException::new),
							PixelType.fromString(parts[7]).orElseThrow(IllegalArgumentException::new)));
					} else {
						Iris.logger.warn("Unknown texture directive for " + key + ": " + value);
					}

					if (type != null) {
						customTexturePatching.put(new Tri<>(samplerName, type, stage), newSamplerName);
					}
					return;
				}

				// PNG texture
				customTextures.computeIfAbsent(stage, _stage -> new Object2ObjectOpenHashMap<>())
						.put(samplerName, new TextureDefinition.PNGDefinition(value));
			});

			handleTwoArgDirective("flip.", key, value, (pass, buffer) -> {
				handleBooleanValue(key, value, shouldFlip -> {
					explicitFlips.computeIfAbsent(pass, _pass -> new Object2BooleanOpenHashMap<>())
							.put(buffer, shouldFlip);
				});
			});


			handleWhitespacedListDirective(key, value, "iris.features.required", options -> requiredFeatureFlags = options);
			handleWhitespacedListDirective(key, value, "iris.features.optional", options -> optionalFeatureFlags = options);

			// Parse custom images: image.<name> = <sampler> <pixelFormat> <internalFormat> <pixelType> <clear> <relative> [dimensions...]
			handlePassDirective("image.", key, value, imageName -> {
				parseCustomImage(imageName, value);
			});

			// Parse buffer objects: bufferObject.<index> = <size> [relative <scaleX> <scaleY>]
			handlePassDirective("bufferObject.", key, value, indexStr -> {
				parseBufferObject(indexStr, value);
			});

			// Parse custom textures: customTexture.<name> = <path> [type format sizeX [sizeY [sizeZ]] pixelFormat pixelType]
			handlePassDirective("customTexture.", key, value, samplerName -> {
				final TextureDefinition definition = parseTextureDefinition("customTexture." + samplerName, value);
				if (definition != null) {
					irisCustomTextures.put(samplerName, definition);
					Iris.logger.debug("[CustomTextures] Parsed customTexture.{} = {}", samplerName, value);
				}
			});

            handlePassDirective("variable.", key, value, pass -> {
                String[] parts = pass.split("\\.");
                if (parts.length != 2) {
                    Iris.logger.warn("Custom variables should take the form of `variable.<type>.<name> = <expression>. Ignoring " + key);
                    return;
                }

                customUniforms.addVariable(parts[0], parts[1], value, false);
            });

            handlePassDirective("uniform.", key, value, pass -> {
               String[] parts = pass.split("\\.");
               if (parts.length != 2) {
                   Iris.logger.warn("Custom uniforms sould take the form of `uniform.<type>.<name> = <expression>. Ignoring " + key);
                   return;
               }

               customUniforms.addVariable(parts[0], parts[1], value, true);
            });

			// TODO: Buffer size directives
			// TODO: Conditional program enabling directives
		});

		// We need to use a non-preprocessed property file here since we don't want any weird preprocessor changes to be applied to the screen/value layout.
		original.forEach((keyObject, valueObject) -> {
            final String key = (String) keyObject;
            final String value = (String) valueObject;

			// Defining "sliders" multiple times in the properties file will only result in
			// the last definition being used, should be tested if behavior matches OptiFine
			handleWhitespacedListDirective(key, value, "sliders", sliders -> sliderOptions = sliders);
			handlePrefixedWhitespacedListDirective("profile.", key, value, profiles::put);

			if (handleIntDirective(key, value, "screen.columns", columns -> mainScreenColumnCount = columns)) {
				return;
			}

			if (handleAffixedIntDirective("screen.", ".columns", key, value, subScreenColumnCount::put)) {
				return;
			}

			handleWhitespacedListDirective(key, value, "screen", options -> mainScreenOptions = options);
			handlePrefixedWhitespacedListDirective("screen.", key, value, subScreenOptions::put);
		});
	}

	private static void handleBooleanValue(String key, String value, BooleanConsumer handler) {
		if ("true".equals(value)) {
			handler.accept(true);
		} else if ("false".equals(value)) {
			handler.accept(false);
		} else {
			Iris.logger.warn("Unexpected value for boolean key " + key + " in shaders.properties: got " + value + ", but expected either true or false");
		}
	}

	private static void handleBooleanDirective(String key, String value, String expectedKey, Consumer<OptionalBoolean> handler) {
		if (!expectedKey.equals(key)) {
			return;
		}

		if ("true".equals(value)) {
			handler.accept(OptionalBoolean.TRUE);
		} else if ("false".equals(value)) {
			handler.accept(OptionalBoolean.FALSE);
		} else {
			Iris.logger.warn("Unexpected value for boolean key " + key + " in shaders.properties: got " + value + ", but expected either true or false");
		}
	}

	private static boolean handleIntDirective(String key, String value, String expectedKey, Consumer<Integer> handler) {
		if (!expectedKey.equals(key)) {
			return false;
		}

		try {
            final int result = Integer.parseInt(value);

			handler.accept(result);
		} catch (NumberFormatException nex) {
			Iris.logger.warn("Unexpected value for integer key " + key + " in shaders.properties: got " + value + ", but expected an integer");
		}

		return true;
	}

	private static boolean handleAffixedIntDirective(String prefix, String suffix, String key, String value, BiConsumer<String, Integer> handler) {
		if (key.startsWith(prefix) && key.endsWith(suffix)) {
            final int substrBegin = prefix.length();
            final int substrEnd = key.length() - suffix.length();

			if (substrEnd <= substrBegin) {
				return false;
			}

            final String affixStrippedKey = key.substring(substrBegin, substrEnd);

			try {
                final int result = Integer.parseInt(value);

				handler.accept(affixStrippedKey, result);
			} catch (NumberFormatException nex) {
				Iris.logger.warn("Unexpected value for integer key " + key + " in shaders.properties: got " + value + ", but expected an integer");
			}

			return true;
		}

		return false;
	}

	private static void handlePassDirective(String prefix, String key, String value, Consumer<String> handler) {
		if (key.startsWith(prefix)) {
            final String pass = key.substring(prefix.length());

			handler.accept(pass);
		}
	}

	private static void handleProgramEnabledDirective(String prefix, String key, String value, Consumer<String> handler) {
		if (key.startsWith(prefix)) {
            final String program = key.substring(prefix.length(), key.indexOf(".", prefix.length()));

			handler.accept(program);
		}
	}

	private static void handleWhitespacedListDirective(String key, String value, String expectedKey, Consumer<List<String>> handler) {
		if (!expectedKey.equals(key)) {
			return;
		}

        final String[] elements = value.split(" +");

		handler.accept(Arrays.asList(elements));
	}

	private static void handlePrefixedWhitespacedListDirective(String prefix, String key, String value, BiConsumer<String, List<String>> handler) {
		if (key.startsWith(prefix)) {
            final String prefixStrippedKey = key.substring(prefix.length());
            final String[] elements = value.split(" +");

			handler.accept(prefixStrippedKey, Arrays.asList(elements));
		}
	}

	private static void handleTwoArgDirective(String prefix, String key, String value, BiConsumer<String, String> handler) {
		if (key.startsWith(prefix)) {
            final int endOfPassIndex = key.indexOf(".", prefix.length());
            final String stage = key.substring(prefix.length(), endOfPassIndex);
            final String sampler = key.substring(endOfPassIndex + 1);

			handler.accept(stage, sampler);
		}
	}

	public static ShaderProperties empty() {
		return new ShaderProperties();
	}

    public Optional<String> getNoiseTexturePath() {
		return Optional.ofNullable(noiseTexturePath);
	}

    public Optional<List<String>> getMainScreenOptions() {
		return Optional.ofNullable(mainScreenOptions);
	}

    public Optional<Integer> getMainScreenColumnCount() {
		return Optional.ofNullable(mainScreenColumnCount);
	}

	public Optional<ParticleRenderingSettings> getParticleRenderingSettings() {
		// MIXED is implied if separateEntityDraws is true.
		if (separateEntityDraws == OptionalBoolean.TRUE) {
			return Optional.of(ParticleRenderingSettings.MIXED);
		}
		return particleRenderingSettings;
	}

	/**
	 * Parses a custom image directive.
	 * Format: image.<name> = <sampler_name> <pixel_format> <internal_format> <pixel_type> <clear> <relative> [width height [depth]] | [relativeX relativeY]
	 */
	private void parseCustomImage(String imageName, String value) {
		if (customImages.size() >= 16) {
			Iris.logger.error("Maximum of 16 custom images exceeded, cannot add: " + imageName);
			return;
		}

		final String[] parts = value.split("\\s+");

		if (parts.length < 6) {
			Iris.logger.error("Invalid custom image directive for " + imageName + ": expected at least 6 parts, got " + parts.length);
			return;
		}

		try {
			String samplerName = parts[0];
			if (samplerName.equals("none") || samplerName.equals("null") || samplerName.isEmpty()) {
				samplerName = null;
			}

			final Optional<PixelFormat> pixelFormatOpt = PixelFormat.fromString(parts[1].toUpperCase());
			if (!pixelFormatOpt.isPresent()) {
				Iris.logger.error("Invalid pixel format for custom image {}: {}", imageName, parts[1]);
				return;
			}
			final PixelFormat pixelFormat = pixelFormatOpt.get();

			final Optional<InternalTextureFormat> internalFormatOpt = InternalTextureFormat.fromString(parts[2].toUpperCase());
			if (!internalFormatOpt.isPresent()) {
				Iris.logger.error("Invalid internal format for custom image {}: {}", imageName, parts[2]);
				return;
			}
			final InternalTextureFormat internalFormat = internalFormatOpt.get();

			final Optional<PixelType> pixelTypeOpt = PixelType.fromString(parts[3].toUpperCase());
			if (!pixelTypeOpt.isPresent()) {
				Iris.logger.error("Invalid pixel type for custom image {}: {}", imageName, parts[3]);
				return;
			}
			final PixelType pixelType = pixelTypeOpt.get();

			final boolean clear = Boolean.parseBoolean(parts[4]);
			final boolean relative = Boolean.parseBoolean(parts[5]);

			ImageInformation imageInfo;

			if (parts.length == 7) {
				// 1D texture: width only
				int width = Integer.parseInt(parts[6]);
				imageInfo = new ImageInformation(imageName, samplerName, TextureType.TEXTURE_1D, pixelFormat, internalFormat, pixelType, width, 1, 1, clear, false, 1.0f, 1.0f);
			} else if (parts.length == 8) {
				if (relative) {
					// Relative 2D: relativeWidth relativeHeight
					float relativeWidth = Float.parseFloat(parts[6]);
					float relativeHeight = Float.parseFloat(parts[7]);
					imageInfo = new ImageInformation(imageName, samplerName, TextureType.TEXTURE_2D, pixelFormat, internalFormat, pixelType, 0, 0, 1, clear, true, relativeWidth, relativeHeight);
				} else {
					// Absolute 2D: width height
					int width = Integer.parseInt(parts[6]);
					int height = Integer.parseInt(parts[7]);
					imageInfo = new ImageInformation(imageName, samplerName, TextureType.TEXTURE_2D, pixelFormat, internalFormat, pixelType, width, height, 1, clear, false, 1.0f, 1.0f);
				}
			} else if (parts.length >= 9) {
				// 3D texture: width height depth
				int width = Integer.parseInt(parts[6]);
				int height = Integer.parseInt(parts[7]);
				int depth = Integer.parseInt(parts[8]);
				imageInfo = new ImageInformation(imageName, samplerName, TextureType.TEXTURE_3D, pixelFormat, internalFormat, pixelType, width, height, depth, clear, false, 1.0f, 1.0f);
			} else {
				Iris.logger.error("Invalid custom image directive for {}: expected dimensions", imageName);
				return;
			}

			customImages.put(imageName, imageInfo);
			Iris.logger.debug("[CustomImages] Parsed custom image: {} -> {}", imageName, imageInfo);

		} catch (NumberFormatException e) {
			Iris.logger.error("Failed to parse dimensions for custom image {}: {}", imageName, e.getMessage());
		}
	}

	/**
	 * Parses a buffer object directive.
	 * Format: bufferObject.<index> = <size> [relative <scaleX> <scaleY>]
	 */
	private void parseBufferObject(String indexStr, String value) {
		try {
			final int index = Integer.parseInt(indexStr);

			if (index > 8) {
				Iris.logger.error("SSBO index " + index + " exceeds maximum of 8, buffers 9+ are reserved");
				return;
			}

			final String[] parts = value.split("\\s+");

			if (parts.length < 1) {
				Iris.logger.error("Invalid buffer object directive for index " + index + ": expected size");
				return;
			}

			final int size = Integer.parseInt(parts[0]);

			// Size < 1 means the shader dev intended to disable the buffer
			if (size < 1) {
				return;
			}

			ShaderStorageInfo info;

			if (parts.length >= 4 && "true".equalsIgnoreCase(parts[1])) {
				// Relative: size relative scaleX scaleY
				float scaleX = Float.parseFloat(parts[2]);
				float scaleY = Float.parseFloat(parts[3]);
				info = new ShaderStorageInfo(size, true, scaleX, scaleY);
			} else {
				// Absolute: size only
				info = new ShaderStorageInfo(size, false, 1.0f, 1.0f);
			}

			bufferObjects.put(index, info);
			Iris.logger.debug("Parsed buffer object: " + index + " -> " + info);

		} catch (NumberFormatException e) {
			Iris.logger.error("Failed to parse buffer object " + indexStr + ": " + e.getMessage());
		}
	}

	/**
	 * Parses a texture definition from a shaders.properties value.
	 * Format can be:
	 * - PNG: path
	 * - Raw 1D: path type format sizeX pixelFormat pixelType
	 * - Raw 2D: path type format sizeX sizeY pixelFormat pixelType
	 * - Raw 3D: path type format sizeX sizeY sizeZ pixelFormat pixelType
	 */
	private TextureDefinition parseTextureDefinition(String key, String value) {
		final String[] parts = value.split(" ");

		if (parts.length == 1) {
			// PNG texture - just a path
			return new TextureDefinition.PNGDefinition(value);
		}

		try {
			if (parts.length == 6) {
				// 1D raw texture: path type format sizeX pixelFormat pixelType
				final Optional<TextureType> targetOpt = TextureType.fromString(parts[1].toUpperCase(Locale.ROOT));
				if (!targetOpt.isPresent()) {
					Iris.logger.error("Invalid texture type for {}: {}", key, parts[1]);
					return null;
				}

				final Optional<InternalTextureFormat> internalFormatOpt = InternalTextureFormat.fromString(parts[2].toUpperCase(Locale.ROOT));
				if (!internalFormatOpt.isPresent()) {
					Iris.logger.error("Invalid internal format for {}: {}", key, parts[2]);
					return null;
				}

				final Optional<PixelFormat> formatOpt = PixelFormat.fromString(parts[4].toUpperCase(Locale.ROOT));
				if (!formatOpt.isPresent()) {
					Iris.logger.error("Invalid pixel format for {}: {}", key, parts[4]);
					return null;
				}

				final Optional<PixelType> pixelTypeOpt = PixelType.fromString(parts[5].toUpperCase(Locale.ROOT));
				if (!pixelTypeOpt.isPresent()) {
					Iris.logger.error("Invalid pixel type for {}: {}", key, parts[5]);
					return null;
				}

				return new TextureDefinition.RawDefinition(parts[0], targetOpt.get(), internalFormatOpt.get(),
						Integer.parseInt(parts[3]), 0, 0, formatOpt.get(), pixelTypeOpt.get());

			} else if (parts.length == 7) {
				// 2D raw texture: path type format sizeX sizeY pixelFormat pixelType
				final Optional<TextureType> targetOpt = TextureType.fromString(parts[1].toUpperCase(Locale.ROOT));
				if (!targetOpt.isPresent()) {
					Iris.logger.error("Invalid texture type for {}: {}", key, parts[1]);
					return null;
				}

				final Optional<InternalTextureFormat> internalFormatOpt = InternalTextureFormat.fromString(parts[2].toUpperCase(Locale.ROOT));
				if (!internalFormatOpt.isPresent()) {
					Iris.logger.error("Invalid internal format for {}: {}", key, parts[2]);
					return null;
				}

				final Optional<PixelFormat> formatOpt = PixelFormat.fromString(parts[5].toUpperCase(Locale.ROOT));
				if (!formatOpt.isPresent()) {
					Iris.logger.error("Invalid pixel format for {}: {}", key, parts[5]);
					return null;
				}

				final Optional<PixelType> pixelTypeOpt = PixelType.fromString(parts[6].toUpperCase(Locale.ROOT));
				if (!pixelTypeOpt.isPresent()) {
					Iris.logger.error("Invalid pixel type for {}: {}", key, parts[6]);
					return null;
				}

				return new TextureDefinition.RawDefinition(parts[0], targetOpt.get(), internalFormatOpt.get(),
						Integer.parseInt(parts[3]), Integer.parseInt(parts[4]), 0, formatOpt.get(), pixelTypeOpt.get());

			} else if (parts.length == 8) {
				// 3D raw texture: path type format sizeX sizeY sizeZ pixelFormat pixelType
				final Optional<TextureType> targetOpt = TextureType.fromString(parts[1].toUpperCase(Locale.ROOT));
				if (!targetOpt.isPresent()) {
					Iris.logger.error("Invalid texture type for {}: {}", key, parts[1]);
					return null;
				}

				final Optional<InternalTextureFormat> internalFormatOpt = InternalTextureFormat.fromString(parts[2].toUpperCase(Locale.ROOT));
				if (!internalFormatOpt.isPresent()) {
					Iris.logger.error("Invalid internal format for {}: {}", key, parts[2]);
					return null;
				}

				final Optional<PixelFormat> formatOpt = PixelFormat.fromString(parts[6].toUpperCase(Locale.ROOT));
				if (!formatOpt.isPresent()) {
					Iris.logger.error("Invalid pixel format for {}: {}", key, parts[6]);
					return null;
				}

				final Optional<PixelType> pixelTypeOpt = PixelType.fromString(parts[7].toUpperCase(Locale.ROOT));
				if (!pixelTypeOpt.isPresent()) {
					Iris.logger.error("Invalid pixel type for {}: {}", key, parts[7]);
					return null;
				}

				return new TextureDefinition.RawDefinition(parts[0], targetOpt.get(), internalFormatOpt.get(),
						Integer.parseInt(parts[3]), Integer.parseInt(parts[4]), Integer.parseInt(parts[5]),
						formatOpt.get(), pixelTypeOpt.get());

			} else {
				Iris.logger.warn("Unknown texture directive format for {}: {} (expected 1, 6, 7, or 8 parts, got {})",
						key, value, parts.length);
				return null;
			}
		} catch (NumberFormatException e) {
			Iris.logger.error("Failed to parse texture dimensions for {}: {}", key, e.getMessage());
			return null;
		}
	}
}
