package net.coderbot.iris.pipeline;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import com.gtnewhorizons.angelica.compat.mojang.Camera;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.rendering.RenderingState;
import net.coderbot.iris.Iris;
import net.coderbot.iris.block_rendering.BlockMaterialMapping;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.gbuffer_overrides.matching.InputAvailability;
import net.coderbot.iris.gbuffer_overrides.matching.ProgramTable;
import net.coderbot.iris.gbuffer_overrides.matching.RenderCondition;
import net.coderbot.iris.gbuffer_overrides.matching.SpecialCondition;
import net.coderbot.iris.gbuffer_overrides.state.RenderTargetStateListener;
import net.coderbot.iris.gl.blending.AlphaTestOverride;
import net.coderbot.iris.gl.blending.BlendModeOverride;
import net.coderbot.iris.gl.blending.BufferBlendOverride;
import net.coderbot.iris.gl.framebuffer.GlFramebuffer;
import net.coderbot.iris.gl.program.ComputeProgram;
import net.coderbot.iris.gl.program.Program;
import net.coderbot.iris.gl.program.ProgramBuilder;
import net.coderbot.iris.gl.program.ProgramImages;
import net.coderbot.iris.gl.program.ProgramSamplers;
import net.coderbot.iris.gl.texture.DepthBufferFormat;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.pipeline.transform.PatchShaderType;
import net.coderbot.iris.pipeline.transform.TransformPatcher;
import net.coderbot.iris.postprocess.BufferFlipper;
import net.coderbot.iris.postprocess.CenterDepthSampler;
import net.coderbot.iris.postprocess.CompositeRenderer;
import net.coderbot.iris.postprocess.FinalPassRenderer;
import net.coderbot.iris.rendertarget.IRenderTargetExt;
import net.coderbot.iris.rendertarget.NativeImageBackedSingleColorTexture;
import net.coderbot.iris.rendertarget.RenderTargets;
import net.coderbot.iris.samplers.IrisImages;
import net.coderbot.iris.samplers.IrisSamplers;
import net.coderbot.iris.shaderpack.CloudSetting;
import net.coderbot.iris.shaderpack.ComputeSource;
import net.coderbot.iris.shaderpack.IdMap;
import net.coderbot.iris.shaderpack.OptionalBoolean;
import net.coderbot.iris.shaderpack.PackDirectives;
import net.coderbot.iris.shaderpack.PackShadowDirectives;
import net.coderbot.iris.shaderpack.ProgramDirectives;
import net.coderbot.iris.shaderpack.ProgramFallbackResolver;
import net.coderbot.iris.shaderpack.ProgramSet;
import net.coderbot.iris.shaderpack.ProgramSource;
import net.coderbot.iris.shaderpack.loading.ProgramId;
import net.coderbot.iris.shaderpack.texture.TextureStage;
import net.coderbot.iris.shadows.ShadowRenderTargets;
import net.coderbot.iris.texture.TextureInfoCache;
import net.coderbot.iris.texture.format.TextureFormat;
import net.coderbot.iris.texture.format.TextureFormatLoader;
import net.coderbot.iris.texture.pbr.PBRTextureHolder;
import net.coderbot.iris.texture.pbr.PBRTextureManager;
import net.coderbot.iris.texture.pbr.PBRType;
import net.coderbot.iris.uniforms.CommonUniforms;
import net.coderbot.iris.uniforms.FrameUpdateNotifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.shader.Framebuffer;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * Encapsulates the compiled shader program objects for the currently loaded shaderpack.
 */
public class DeferredWorldRenderingPipeline implements WorldRenderingPipeline, RenderTargetStateListener  {
    private final static int SRC_ALPHA = 770;
    private final static int ONE_MINUS_SRC_ALPHA = 771;
    private final static int ONE = 1;
	private final RenderTargets renderTargets;

	@Nullable
	private ShadowRenderTargets shadowRenderTargets;
	@Nullable
	private ComputeProgram[] shadowComputes;
	private final Supplier<ShadowRenderTargets> shadowTargetsSupplier;

	private final ProgramTable<Pass> table;

	private ImmutableList<ClearPass> clearPassesFull;
	private ImmutableList<ClearPass> clearPasses;
	private ImmutableList<ClearPass> shadowClearPasses;
	private ImmutableList<ClearPass> shadowClearPassesFull;

	private final CompositeRenderer prepareRenderer;

	@Nullable
	private final ShadowRenderer shadowRenderer;

	private final int shadowMapResolution;
	private final CompositeRenderer deferredRenderer;
	private final CompositeRenderer compositeRenderer;
	private final FinalPassRenderer finalPassRenderer;
	private final CustomTextureManager customTextureManager;
	private final AbstractTexture whitePixel;
	private final FrameUpdateNotifier updateNotifier;
	private final CenterDepthSampler centerDepthSampler;

	private final ImmutableSet<Integer> flippedBeforeShadow;
	private final ImmutableSet<Integer> flippedAfterPrepare;
	private final ImmutableSet<Integer> flippedAfterTranslucent;

	private final SodiumTerrainPipeline sodiumTerrainPipeline;

	private final HorizonRenderer horizonRenderer = new HorizonRenderer();

	private final float sunPathRotation;
	private final CloudSetting cloudSetting;
	private final boolean shouldRenderUnderwaterOverlay;
	private final boolean shouldRenderVignette;
	private final boolean shouldRenderSun;
	private final boolean shouldRenderMoon;
	private final boolean shouldWriteRainAndSnowToDepthBuffer;
	private final boolean shouldRenderParticlesBeforeDeferred;
	private final boolean shouldRenderPrepareBeforeShadow;
	private final boolean oldLighting;
	private final boolean allowConcurrentCompute;
	private final OptionalInt forcedShadowRenderDistanceChunks;

	private Pass current = null;

	private WorldRenderingPhase overridePhase = null;
	private WorldRenderingPhase phase = WorldRenderingPhase.NONE;
	private boolean isBeforeTranslucent;
	private boolean isRenderingShadow = false;
	private InputAvailability inputs = new InputAvailability(false, false, false);
	private SpecialCondition special = null;

	private boolean shouldBindPBR;
	private int currentNormalTexture;
	private int currentSpecularTexture;
	private PackDirectives packDirectives;

	public DeferredWorldRenderingPipeline(ProgramSet programs) {
		Objects.requireNonNull(programs);

		this.cloudSetting = programs.getPackDirectives().getCloudSetting();
		this.shouldRenderUnderwaterOverlay = programs.getPackDirectives().underwaterOverlay();
		this.shouldRenderVignette = programs.getPackDirectives().vignette();
		this.shouldRenderSun = programs.getPackDirectives().shouldRenderSun();
		this.shouldRenderMoon = programs.getPackDirectives().shouldRenderMoon();
		this.shouldWriteRainAndSnowToDepthBuffer = programs.getPackDirectives().rainDepth();
		this.shouldRenderParticlesBeforeDeferred = programs.getPackDirectives().areParticlesBeforeDeferred();
		this.allowConcurrentCompute = programs.getPackDirectives().getConcurrentCompute();
		this.shouldRenderPrepareBeforeShadow = programs.getPackDirectives().isPrepareBeforeShadow();
		this.oldLighting = programs.getPackDirectives().isOldLighting();
		this.updateNotifier = new FrameUpdateNotifier();

		this.packDirectives = programs.getPackDirectives();

        final Framebuffer main = Minecraft.getMinecraft().getFramebuffer();

        final int depthTextureId = ((IRenderTargetExt)main).getIris$depthTextureId();
		final int internalFormat = TextureInfoCache.INSTANCE.getInfo(depthTextureId).getInternalFormat();
		final DepthBufferFormat depthBufferFormat = DepthBufferFormat.fromGlEnumOrDefault(internalFormat);

		this.renderTargets = new RenderTargets(main.framebufferWidth, main.framebufferHeight, depthTextureId,
            ((IRenderTargetExt)main).iris$getDepthBufferVersion(), depthBufferFormat,
			programs.getPackDirectives().getRenderTargetDirectives().getRenderTargetSettings(), programs.getPackDirectives());

		this.sunPathRotation = programs.getPackDirectives().getSunPathRotation();

		PackShadowDirectives shadowDirectives = programs.getPackDirectives().getShadowDirectives();

		if (shadowDirectives.isDistanceRenderMulExplicit()) {
			if (shadowDirectives.getDistanceRenderMul() >= 0.0) {
				// add 15 and then divide by 16 to ensure we're rounding up
				forcedShadowRenderDistanceChunks = OptionalInt.of(((int) (shadowDirectives.getDistance() * shadowDirectives.getDistanceRenderMul()) + 15) / 16);
			} else {
				forcedShadowRenderDistanceChunks = OptionalInt.of(-1);
			}
		} else {
			forcedShadowRenderDistanceChunks = OptionalInt.empty();
		}

        // TODO: BlockStateIdMap
		BlockRenderingSettings.INSTANCE.setBlockMatches(BlockMaterialMapping.createBlockStateIdMap(programs.getPack().getIdMap().getBlockProperties()));
		BlockRenderingSettings.INSTANCE.setBlockTypeIds(BlockMaterialMapping.createBlockTypeMap(programs.getPack().getIdMap().getBlockRenderTypeMap()));

		BlockRenderingSettings.INSTANCE.setEntityIds(programs.getPack().getIdMap().getEntityIdMap());
		BlockRenderingSettings.INSTANCE.setAmbientOcclusionLevel(programs.getPackDirectives().getAmbientOcclusionLevel());
		BlockRenderingSettings.INSTANCE.setDisableDirectionalShading(shouldDisableDirectionalShading());
		BlockRenderingSettings.INSTANCE.setUseSeparateAo(programs.getPackDirectives().shouldUseSeparateAo());
		BlockRenderingSettings.INSTANCE.setUseExtendedVertexFormat(true);

		// Don't clobber anything in texture unit 0. It probably won't cause issues, but we're just being cautious here.
		GLStateManager.glActiveTexture(GL13.GL_TEXTURE2);

		customTextureManager = new CustomTextureManager(programs.getPackDirectives(), programs.getPack().getCustomTextureDataMap(), programs.getPack().getCustomNoiseTexture());

		whitePixel = new NativeImageBackedSingleColorTexture(255, 255, 255, 255);

		GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);

		this.flippedBeforeShadow = ImmutableSet.of();

		BufferFlipper flipper = new BufferFlipper();

		this.centerDepthSampler = new CenterDepthSampler(() -> getRenderTargets().getDepthTexture(), programs.getPackDirectives().getCenterDepthHalfLife());

		this.shadowMapResolution = programs.getPackDirectives().getShadowDirectives().getResolution();

		this.shadowTargetsSupplier = () -> {
			if (shadowRenderTargets == null) {
				this.shadowRenderTargets = new ShadowRenderTargets(shadowMapResolution, shadowDirectives);
			}

			return shadowRenderTargets;
		};

		PatchedShaderPrinter.resetPrintState();

		this.prepareRenderer = new CompositeRenderer(programs.getPackDirectives(), programs.getPrepare(), programs.getPrepareCompute(), renderTargets,
				customTextureManager.getNoiseTexture(), updateNotifier, centerDepthSampler, flipper, shadowTargetsSupplier,
				customTextureManager.getCustomTextureIdMap(TextureStage.PREPARE),
				programs.getPackDirectives().getExplicitFlips("prepare_pre"));

		flippedAfterPrepare = flipper.snapshot();

		this.deferredRenderer = new CompositeRenderer(programs.getPackDirectives(), programs.getDeferred(), programs.getDeferredCompute(), renderTargets,
				customTextureManager.getNoiseTexture(), updateNotifier, centerDepthSampler, flipper, shadowTargetsSupplier,
				customTextureManager.getCustomTextureIdMap(TextureStage.DEFERRED),
				programs.getPackDirectives().getExplicitFlips("deferred_pre"));

		flippedAfterTranslucent = flipper.snapshot();

		this.compositeRenderer = new CompositeRenderer(programs.getPackDirectives(), programs.getComposite(), programs.getCompositeCompute(), renderTargets,
				customTextureManager.getNoiseTexture(), updateNotifier, centerDepthSampler, flipper, shadowTargetsSupplier,
				customTextureManager.getCustomTextureIdMap(TextureStage.COMPOSITE_AND_FINAL),
				programs.getPackDirectives().getExplicitFlips("composite_pre"));
		this.finalPassRenderer = new FinalPassRenderer(programs, renderTargets, customTextureManager.getNoiseTexture(), updateNotifier, flipper.snapshot(),
				centerDepthSampler, shadowTargetsSupplier,
				customTextureManager.getCustomTextureIdMap(TextureStage.COMPOSITE_AND_FINAL),
				this.compositeRenderer.getFlippedAtLeastOnceFinal());

		// [(textured=false,lightmap=false), (textured=true,lightmap=false), (textured=true,lightmap=true)]
		ProgramId[] ids = new ProgramId[] {
				ProgramId.Basic, ProgramId.Textured, ProgramId.TexturedLit,
				ProgramId.SkyBasic, ProgramId.SkyTextured, ProgramId.SkyTextured,
				null, null, ProgramId.Terrain,
				null, null, ProgramId.Water,
				null, ProgramId.Clouds, ProgramId.Clouds,
				null, ProgramId.DamagedBlock, ProgramId.DamagedBlock,
				ProgramId.Block, ProgramId.Block, ProgramId.Block,
				ProgramId.BeaconBeam, ProgramId.BeaconBeam, ProgramId.BeaconBeam,
				ProgramId.Entities, ProgramId.Entities, ProgramId.Entities,
				ProgramId.EntitiesTrans, ProgramId.EntitiesTrans, ProgramId.EntitiesTrans,
				null, ProgramId.ArmorGlint, ProgramId.ArmorGlint,
				null, ProgramId.SpiderEyes, ProgramId.SpiderEyes,
				ProgramId.Hand, ProgramId.Hand, ProgramId.Hand,
				ProgramId.HandWater, ProgramId.HandWater, ProgramId.HandWater,
				null, null, ProgramId.Weather,
				// world border uses textured_lit even though it has no lightmap :/
				null, ProgramId.TexturedLit, ProgramId.TexturedLit,
				ProgramId.Shadow, ProgramId.Shadow, ProgramId.Shadow
		};

		if (ids.length != RenderCondition.values().length * 3) {
			throw new IllegalStateException("Program ID table length mismatch");
		}

		ProgramFallbackResolver resolver = new ProgramFallbackResolver(programs);

		Map<Pair<ProgramId, InputAvailability>, Pass> cachedPasses = new HashMap<>();

		this.shadowComputes = createShadowComputes(programs.getShadowCompute(), programs);

		this.table = new ProgramTable<>((condition, availability) -> {
			int idx;

			if (availability.texture && availability.lightmap) {
				idx = 2;
			} else if (availability.texture) {
				idx = 1;
			} else {
				idx = 0;
			}

			ProgramId id = ids[condition.ordinal() * 3 + idx];

			if (id == null) {
				id = ids[idx];
			}

			ProgramId finalId = id;

			return cachedPasses.computeIfAbsent(Pair.of(id, availability), p -> {
				ProgramSource source = resolver.resolveNullable(p.getLeft());

				if (condition == RenderCondition.SHADOW) {
					if (!shadowDirectives.isShadowEnabled().orElse(shadowRenderTargets != null)) {
						// shadow is not used
						return null;
					} else if (source == null) {
						// still need the custom framebuffer, viewport, and blend mode behavior
						GlFramebuffer shadowFb = shadowTargetsSupplier.get().createShadowFramebuffer(shadowRenderTargets.snapshot(), new int[] {0});
						return new Pass(null, shadowFb, shadowFb, null,
							BlendModeOverride.OFF, Collections.emptyList(), true);
					}
				}

				if (source == null) {
					return createDefaultPass();
				}

				try {
					return createPass(source, availability, condition == RenderCondition.SHADOW, finalId);
				} catch (Exception e) {
					throw new RuntimeException("Failed to create pass for " + source.getName() + " for rendering condition "
						+ condition + " specialized to input availability " + availability, e);
				}
			});
		});
		if (shadowRenderTargets == null && shadowDirectives.isShadowEnabled() == OptionalBoolean.TRUE) {
			shadowRenderTargets = new ShadowRenderTargets(shadowMapResolution, shadowDirectives);
		}

		if (shadowRenderTargets != null) {
			this.shadowClearPasses = ClearPassCreator.createShadowClearPasses(shadowRenderTargets, false, shadowDirectives);
			this.shadowClearPassesFull = ClearPassCreator.createShadowClearPasses(shadowRenderTargets, true, shadowDirectives);

			if (programs.getPackDirectives().getShadowDirectives().isShadowEnabled().orElse(true)) {
				this.shadowRenderer = new ShadowRenderer(programs.getShadow().orElse(null),
					programs.getPackDirectives(), shadowRenderTargets);
				Program shadowProgram = table.match(RenderCondition.SHADOW, new InputAvailability(true, true, true)).getProgram();
				shadowRenderer.setUsesImages(shadowProgram != null && shadowProgram.getActiveImages() > 0);
			} else {
				shadowRenderer = null;
			}
		} else {
			this.shadowClearPasses = ImmutableList.of();
			this.shadowClearPassesFull = ImmutableList.of();
			this.shadowRenderer = null;
		}

		this.clearPassesFull = ClearPassCreator.createClearPasses(renderTargets, true, programs.getPackDirectives().getRenderTargetDirectives());
		this.clearPasses = ClearPassCreator.createClearPasses(renderTargets, false, programs.getPackDirectives().getRenderTargetDirectives());

		// SodiumTerrainPipeline setup follows.

		Supplier<ImmutableSet<Integer>> flipped = () -> isBeforeTranslucent ? flippedAfterPrepare : flippedAfterTranslucent;

		IntFunction<ProgramSamplers> createTerrainSamplers = (programId) -> {
			ProgramSamplers.Builder builder = ProgramSamplers.builder(programId, IrisSamplers.WORLD_RESERVED_TEXTURE_UNITS);
			ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor = ProgramSamplers.customTextureSamplerInterceptor(builder, customTextureManager.getCustomTextureIdMap(TextureStage.GBUFFERS_AND_SHADOW));

			IrisSamplers.addRenderTargetSamplers(customTextureSamplerInterceptor, flipped, renderTargets, false);
			IrisSamplers.addLevelSamplers(customTextureSamplerInterceptor, this, whitePixel, new InputAvailability(true, true, false));
			IrisSamplers.addWorldDepthSamplers(customTextureSamplerInterceptor, renderTargets);
			IrisSamplers.addNoiseSampler(customTextureSamplerInterceptor, customTextureManager.getNoiseTexture());

			if (IrisSamplers.hasShadowSamplers(customTextureSamplerInterceptor)) {
				IrisSamplers.addShadowSamplers(customTextureSamplerInterceptor, Objects.requireNonNull(shadowRenderTargets));
			}

			return builder.build();
		};

		IntFunction<ProgramImages> createTerrainImages = (programId) -> {
			ProgramImages.Builder builder = ProgramImages.builder(programId);

			IrisImages.addRenderTargetImages(builder, flipped, renderTargets);

			if (IrisImages.hasShadowImages(builder)) {
				IrisImages.addShadowColorImages(builder, Objects.requireNonNull(shadowRenderTargets));
			}

			return builder.build();
		};

		IntFunction<ProgramSamplers> createShadowTerrainSamplers = (programId) -> {
			ProgramSamplers.Builder builder = ProgramSamplers.builder(programId, IrisSamplers.WORLD_RESERVED_TEXTURE_UNITS);
			ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor = ProgramSamplers.customTextureSamplerInterceptor(builder, customTextureManager.getCustomTextureIdMap(TextureStage.GBUFFERS_AND_SHADOW));

			IrisSamplers.addRenderTargetSamplers(customTextureSamplerInterceptor, () -> flippedAfterPrepare, renderTargets, false);
			IrisSamplers.addLevelSamplers(customTextureSamplerInterceptor, this, whitePixel, new InputAvailability(true, true, false));
			IrisSamplers.addNoiseSampler(customTextureSamplerInterceptor, customTextureManager.getNoiseTexture());

			// Only initialize these samplers if the shadow map renderer exists. Otherwise, this program shouldn't be used at all?
			if (IrisSamplers.hasShadowSamplers(customTextureSamplerInterceptor)) {
				IrisSamplers.addShadowSamplers(customTextureSamplerInterceptor, Objects.requireNonNull(shadowRenderTargets));
			}

			return builder.build();
		};
        IntFunction<ProgramImages> createShadowTerrainImages = (programId) -> {
			ProgramImages.Builder builder = ProgramImages.builder(programId);

			IrisImages.addRenderTargetImages(builder, () -> flippedAfterPrepare, renderTargets);

			if (IrisImages.hasShadowImages(builder)) {
				IrisImages.addShadowColorImages(builder, Objects.requireNonNull(shadowRenderTargets));
			}

			return builder.build();
		};
        this.sodiumTerrainPipeline = new SodiumTerrainPipeline(this, programs, createTerrainSamplers,
			shadowRenderer == null ? null : createShadowTerrainSamplers, createTerrainImages,
			shadowRenderer == null ? null : createShadowTerrainImages);
	}

	private RenderTargets getRenderTargets() {
		return renderTargets;
	}

	private void checkWorld() {
		// If we're not in a world, then obviously we cannot possibly be rendering a world.
		if (Minecraft.getMinecraft().theWorld == null) {
			isRenderingWorld = false;
			current = null;
		}
	}

	@Override
	public boolean shouldDisableVanillaEntityShadows() {
		// OptiFine seems to disable vanilla shadows when the shaderpack uses shadow mapping?
		return shadowRenderer != null;
	}

	@Override
	public boolean shouldDisableDirectionalShading() {
		return !oldLighting;
	}

	@Override
	public CloudSetting getCloudSetting() {
		return cloudSetting;
	}

	@Override
	public boolean shouldRenderUnderwaterOverlay() {
		return shouldRenderUnderwaterOverlay;
	}

	@Override
	public boolean shouldRenderVignette() {
		return shouldRenderVignette;
	}

	@Override
	public boolean shouldRenderSun() {
		return shouldRenderSun;
	}

	@Override
	public boolean shouldRenderMoon() {
		return shouldRenderMoon;
	}

	@Override
	public boolean shouldWriteRainAndSnowToDepthBuffer() {
		return shouldWriteRainAndSnowToDepthBuffer;
	}

	@Override
	public boolean shouldRenderParticlesBeforeDeferred() {
		return shouldRenderParticlesBeforeDeferred;
	}

	@Override
	public boolean allowConcurrentCompute() {
		return allowConcurrentCompute;
	}

	@Override
	public float getSunPathRotation() {
		return sunPathRotation;
	}

	private RenderCondition getCondition(WorldRenderingPhase phase) {
		if (isRenderingShadow) {
			return RenderCondition.SHADOW;
		}

		if (special != null) {
			if (special == SpecialCondition.BEACON_BEAM) {
				return RenderCondition.BEACON_BEAM;
			} else if (special == SpecialCondition.ENTITY_EYES) {
				return RenderCondition.ENTITY_EYES;
			} else if (special == SpecialCondition.GLINT) {
				return RenderCondition.GLINT;
			}
		}

		switch (phase) {
			case NONE, OUTLINE, DEBUG, PARTICLES:
				return RenderCondition.DEFAULT;
			case SKY, SUNSET, CUSTOM_SKY, SUN, MOON, STARS, VOID:
				return RenderCondition.SKY;
			case TERRAIN_SOLID, TERRAIN_CUTOUT, TERRAIN_CUTOUT_MIPPED:
				return RenderCondition.TERRAIN_OPAQUE;
			case ENTITIES:
                if (GLStateManager.getBlendState().getSrcRgb() == SRC_ALPHA &&
                    GLStateManager.getBlendState().getSrcAlpha() == ONE_MINUS_SRC_ALPHA &&
                    GLStateManager.getBlendState().getDstRgb() == ONE &&
                    GLStateManager.getBlendState().getDstAlpha() == ONE_MINUS_SRC_ALPHA)
                {
					return RenderCondition.ENTITIES_TRANSLUCENT;
				} else {
					return RenderCondition.ENTITIES;
				}
			case BLOCK_ENTITIES:
				return RenderCondition.BLOCK_ENTITIES;
			case DESTROY:
				return RenderCondition.DESTROY;
			case HAND_SOLID:
				return RenderCondition.HAND_OPAQUE;
            case TERRAIN_TRANSLUCENT, TRIPWIRE:
				return RenderCondition.TERRAIN_TRANSLUCENT;
			case CLOUDS:
				return RenderCondition.CLOUDS;
			case RAIN_SNOW:
				return RenderCondition.RAIN_SNOW;
			case HAND_TRANSLUCENT:
				return RenderCondition.HAND_TRANSLUCENT;
			case WORLD_BORDER:
				return RenderCondition.WORLD_BORDER;
			default:
				throw new IllegalStateException("Unknown render phase " + phase);
		}
	}

	private void matchPass() {
		if (!isRenderingWorld || isRenderingFullScreenPass || isPostChain || !isMainBound) {
			return;
		}

		if (sodiumTerrainRendering) {
			beginPass(table.match(getCondition(getPhase()), new InputAvailability(true, true, false)));
			return;
		}

		beginPass(table.match(getCondition(getPhase()), inputs));
	}

	public void beginPass(Pass pass) {
		if (current == pass) {
			return;
		}

		if (current != null) {
			current.stopUsing();
		}

		current = pass;

		if (pass != null) {
			pass.use();
		} else {
			Program.unbind();
		}
	}

	private Pass createDefaultPass() {
		final GlFramebuffer framebufferBeforeTranslucents = renderTargets.createGbufferFramebuffer(flippedAfterPrepare, new int[] {0});
		final GlFramebuffer framebufferAfterTranslucents = renderTargets.createGbufferFramebuffer(flippedAfterTranslucent, new int[] {0});


		return new Pass(null, framebufferBeforeTranslucents, framebufferAfterTranslucents, null,
			null, Collections.emptyList(), false);
	}

	private Pass createPass(ProgramSource source, InputAvailability availability, boolean shadow, ProgramId id) {
		// TODO: Properly handle empty shaders?
		Map<PatchShaderType, String> transformed = TransformPatcher.patchAttributes(
			source.getVertexSource().orElseThrow(NullPointerException::new),
			source.getGeometrySource().orElse(null),
			source.getFragmentSource().orElseThrow(NullPointerException::new),
			availability);
		String vertex = transformed.get(PatchShaderType.VERTEX);
		String geometry = transformed.get(PatchShaderType.GEOMETRY);
		String fragment = transformed.get(PatchShaderType.FRAGMENT);

		PatchedShaderPrinter.debugPatchedShaders(source.getName(), vertex, geometry, fragment);

		ProgramBuilder builder = ProgramBuilder.begin(source.getName(), vertex, geometry, fragment,
			IrisSamplers.WORLD_RESERVED_TEXTURE_UNITS);

		return createPassInner(builder, source.getParent().getPack().getIdMap(), source.getDirectives(), source.getParent().getPackDirectives(), availability, shadow, id);
	}

	private Pass createPassInner(ProgramBuilder builder, IdMap map, ProgramDirectives programDirectives,
								 PackDirectives packDirectives, InputAvailability availability, boolean shadow, ProgramId id) {

		CommonUniforms.addCommonUniforms(builder, map, packDirectives, updateNotifier);

		Supplier<ImmutableSet<Integer>> flipped;

		if (shadow) {
			flipped = () -> (shouldRenderPrepareBeforeShadow ? flippedAfterPrepare : flippedBeforeShadow);
		} else {
			flipped = () -> isBeforeTranslucent ? flippedAfterPrepare : flippedAfterTranslucent;
		}

		TextureStage textureStage = TextureStage.GBUFFERS_AND_SHADOW;

		ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor =
			ProgramSamplers.customTextureSamplerInterceptor(builder,
				customTextureManager.getCustomTextureIdMap(textureStage));

		IrisSamplers.addRenderTargetSamplers(customTextureSamplerInterceptor, flipped, renderTargets, false);
		IrisImages.addRenderTargetImages(builder, flipped, renderTargets);

		if (!shouldBindPBR) {
			shouldBindPBR = IrisSamplers.hasPBRSamplers(customTextureSamplerInterceptor);
		}

		IrisSamplers.addLevelSamplers(customTextureSamplerInterceptor, this, whitePixel, availability);

		if (!shadow) {
			IrisSamplers.addWorldDepthSamplers(customTextureSamplerInterceptor, renderTargets);
		}

		IrisSamplers.addNoiseSampler(customTextureSamplerInterceptor, customTextureManager.getNoiseTexture());

		if (IrisSamplers.hasShadowSamplers(customTextureSamplerInterceptor)) {
			if (!shadow) {
				shadowTargetsSupplier.get();
			}

			if (shadowRenderTargets != null) {
				IrisSamplers.addShadowSamplers(customTextureSamplerInterceptor, shadowRenderTargets);
				IrisImages.addShadowColorImages(builder, shadowRenderTargets);
			}
		}

		GlFramebuffer framebufferBeforeTranslucents;
		GlFramebuffer framebufferAfterTranslucents;

		if (shadow) {
			// Always add both draw buffers on the shadow pass.
			framebufferBeforeTranslucents = shadowTargetsSupplier.get().createShadowFramebuffer(shadowRenderTargets.snapshot(), new int[] { 0, 1 });
			framebufferAfterTranslucents = framebufferBeforeTranslucents;
		} else {
			framebufferBeforeTranslucents = renderTargets.createGbufferFramebuffer(flippedAfterPrepare, programDirectives.getDrawBuffers());
			framebufferAfterTranslucents = renderTargets.createGbufferFramebuffer(flippedAfterTranslucent, programDirectives.getDrawBuffers());
		}

		builder.bindAttributeLocation(11, "mc_Entity");
		builder.bindAttributeLocation(12, "mc_midTexCoord");
		builder.bindAttributeLocation(13, "at_tangent");
		builder.bindAttributeLocation(14, "at_midBlock");

		AlphaTestOverride alphaTestOverride = programDirectives.getAlphaTestOverride().orElse(null);

		List<BufferBlendOverride> bufferOverrides = new ArrayList<>();

		programDirectives.getBufferBlendOverrides().forEach(information -> {
			int index = Ints.indexOf(programDirectives.getDrawBuffers(), information.getIndex());
			if (index > -1) {
				bufferOverrides.add(new BufferBlendOverride(index, information.getBlendMode()));
			}
		});

		return new Pass(builder.build(), framebufferBeforeTranslucents, framebufferAfterTranslucents, alphaTestOverride,
				programDirectives.getBlendModeOverride().orElse(id.getBlendModeOverride()), bufferOverrides, shadow);
	}

	private boolean isPostChain;
	private boolean isMainBound = true;

	@Override
	public void beginPostChain() {
		isPostChain = true;

		beginPass(null);
	}

	@Override
	public void endPostChain() {
		isPostChain = false;
	}

	@Override
	public void setIsMainBound(boolean bound) {
		isMainBound = bound;

		if (!isRenderingWorld || isRenderingFullScreenPass || isPostChain) {
			return;
		}

		if (bound) {
			// force refresh
			current = null;
		} else {
			beginPass(null);
		}
	}

	private final class Pass {
		@Nullable
		private final Program program;
		private final GlFramebuffer framebufferBeforeTranslucents;
		private final GlFramebuffer framebufferAfterTranslucents;
		@Nullable
		private final AlphaTestOverride alphaTestOverride;
		@Nullable
		private final BlendModeOverride blendModeOverride;
		@Nullable
		private final List<BufferBlendOverride> bufferBlendOverrides;
		private final boolean shadowViewport;

		private Pass(@Nullable Program program, GlFramebuffer framebufferBeforeTranslucents, GlFramebuffer framebufferAfterTranslucents,
					 @Nullable AlphaTestOverride alphaTestOverride, @Nullable BlendModeOverride blendModeOverride, @Nullable List<BufferBlendOverride> bufferBlendOverrides, boolean shadowViewport) {
			this.program = program;
			this.framebufferBeforeTranslucents = framebufferBeforeTranslucents;
			this.framebufferAfterTranslucents = framebufferAfterTranslucents;
			this.alphaTestOverride = alphaTestOverride;
			this.blendModeOverride = blendModeOverride;
			this.bufferBlendOverrides = bufferBlendOverrides;
			this.shadowViewport = shadowViewport;
		}

		public void use() {
			if (isBeforeTranslucent) {
				framebufferBeforeTranslucents.bind();
			} else {
				framebufferAfterTranslucents.bind();
			}

			if (shadowViewport) {
				GL11.glViewport(0, 0, shadowMapResolution, shadowMapResolution);
			} else {
                final Framebuffer main = Minecraft.getMinecraft().getFramebuffer();
				GL11.glViewport(0, 0, main.framebufferWidth, main.framebufferHeight);
			}

			if (program != null && !sodiumTerrainRendering) {
				program.use();
			}

			if (alphaTestOverride != null) {
				alphaTestOverride.apply();
			} else {
				// Previous program on the stack might have applied an override
				AlphaTestOverride.restore();
			}

			if (blendModeOverride != null) {
				blendModeOverride.apply();
			} else {
				// Previous program on the stack might have applied an override
				BlendModeOverride.restore();
			}

			if (bufferBlendOverrides != null && !bufferBlendOverrides.isEmpty()) {
				bufferBlendOverrides.forEach(BufferBlendOverride::apply);
			}
		}

		public void stopUsing() {
			if (alphaTestOverride != null) {
				AlphaTestOverride.restore();
			}

			if (blendModeOverride != null || (bufferBlendOverrides != null && !bufferBlendOverrides.isEmpty())) {
				BlendModeOverride.restore();
			}
		}

		@Nullable
		public Program getProgram() {
			return program;
		}

		public void destroy() {
			if (this.program != null) {
				this.program.destroy();
			}
		}
	}

	@Override
	public void destroy() {
		BlendModeOverride.restore();
		AlphaTestOverride.restore();

		destroyPasses(table);

		// Destroy the composite rendering pipeline
		//
		// This destroys all the loaded composite programs as well.
		compositeRenderer.destroy();
		deferredRenderer.destroy();
		finalPassRenderer.destroy();
		centerDepthSampler.destroy();

		horizonRenderer.destroy();

		// Make sure that any custom framebuffers are not bound before destroying render targets
		OpenGlHelper.func_153171_g/*glBindFramebuffer*/(GL30.GL_READ_FRAMEBUFFER, 0);
		OpenGlHelper.func_153171_g/*glBindFramebuffer*/(GL30.GL_DRAW_FRAMEBUFFER, 0);
		OpenGlHelper.func_153171_g/*glBindFramebuffer*/(GL30.GL_FRAMEBUFFER, 0);

        Minecraft.getMinecraft().getFramebuffer().bindFramebuffer(false);

		// Destroy our render targets
		//
		// While it's possible to just clear them instead and reuse them, we'd need to investigate whether or not this
		// would help performance.
		renderTargets.destroy();

		// destroy the shadow render targets
		if (shadowRenderTargets != null) {
			shadowRenderTargets.destroy();
		}

		// Destroy custom textures and the static samplers (normals, specular, and noise)
		customTextureManager.destroy();
//		whitePixel.releaseId();
	}

	private static void destroyPasses(ProgramTable<Pass> table) {
		Set<Pass> destroyed = new HashSet<>();

		table.forEach(pass -> {
			if (pass == null) {
				return;
			}

			if (destroyed.contains(pass)) {
				return;
			}

			pass.destroy();
			destroyed.add(pass);
		});
	}

	private void prepareRenderTargets() {
		// Make sure we're using texture unit 0 for this.
		GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
		final Vector4f emptyClearColor = new Vector4f(1.0F);

		if (shadowRenderTargets != null) {
			if (packDirectives.getShadowDirectives().isShadowEnabled() == OptionalBoolean.FALSE) {
				if (shadowRenderTargets.isFullClearRequired()) {
					shadowRenderTargets.onFullClear();
					for (ClearPass clearPass : shadowClearPassesFull) {
						clearPass.execute(emptyClearColor);
					}
				}
			} else {
				// Clear depth first, regardless of any color clearing.
				shadowRenderTargets.getDepthSourceFb().bind();
                GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
                if (Minecraft.isRunningOnMac) {
                    GL11.glGetError();
                }


				ImmutableList<ClearPass> passes;

				for (ComputeProgram computeProgram : shadowComputes) {
					if (computeProgram != null) {
						computeProgram.dispatch(shadowMapResolution, shadowMapResolution);
					}
				}

				if (shadowRenderTargets.isFullClearRequired()) {
					passes = shadowClearPassesFull;
					shadowRenderTargets.onFullClear();
				} else {
					passes = shadowClearPasses;
				}

				for (ClearPass clearPass : passes) {
					clearPass.execute(emptyClearColor);
				}
			}
		}

        final Framebuffer main = Minecraft.getMinecraft().getFramebuffer();

        final int depthTextureId = ((IRenderTargetExt)main).getIris$depthTextureId();
		final int internalFormat = TextureInfoCache.INSTANCE.getInfo(depthTextureId).getInternalFormat();
		final DepthBufferFormat depthBufferFormat = DepthBufferFormat.fromGlEnumOrDefault(internalFormat);

		final boolean changed = renderTargets.resizeIfNeeded(((IRenderTargetExt)main).iris$getDepthBufferVersion(), depthTextureId, main.framebufferWidth,
            main.framebufferHeight, depthBufferFormat, packDirectives);

		if (changed) {
			prepareRenderer.recalculateSizes();
			deferredRenderer.recalculateSizes();
			compositeRenderer.recalculateSizes();
			finalPassRenderer.recalculateSwapPassSize();

			this.clearPassesFull.forEach(clearPass -> renderTargets.destroyFramebuffer(clearPass.getFramebuffer()));
			this.clearPasses.forEach(clearPass -> renderTargets.destroyFramebuffer(clearPass.getFramebuffer()));

			this.clearPassesFull = ClearPassCreator.createClearPasses(renderTargets, true, packDirectives.getRenderTargetDirectives());
			this.clearPasses = ClearPassCreator.createClearPasses(renderTargets, false, packDirectives.getRenderTargetDirectives());
		}

		final ImmutableList<ClearPass> passes;

		if (renderTargets.isFullClearRequired()) {
			renderTargets.onFullClear();
			passes = clearPassesFull;
		} else {
			passes = clearPasses;
		}

		final Vector3d fogColor3 = GLStateManager.getFogColor();

		// NB: The alpha value must be 1.0 here, or else you will get a bunch of bugs. Sildur's Vibrant Shaders
		//     will give you pink reflections and other weirdness if this is zero.
        final Vector4f fogColor = new Vector4f((float) fogColor3.x, (float) fogColor3.y, (float) fogColor3.z, 1.0F);

		for (ClearPass clearPass : passes) {
			clearPass.execute(fogColor);
		}

		// Reset framebuffer and viewport
        Minecraft.getMinecraft().getFramebuffer().bindFramebuffer(true);
	}

	private ComputeProgram[] createShadowComputes(ComputeSource[] compute, ProgramSet programSet) {
		ComputeProgram[] programs = new ComputeProgram[compute.length];
		for (int i = 0; i < programs.length; i++) {
			ComputeSource source = compute[i];
			if (source == null || !source.getSource().isPresent()) {
				continue;
			} else {
				ProgramBuilder builder;

				try {
					builder = ProgramBuilder.beginCompute(source.getName(), source.getSource().orElse(null), IrisSamplers.WORLD_RESERVED_TEXTURE_UNITS);
				} catch (RuntimeException e) {
					// TODO: Better error handling
					throw new RuntimeException("Shader compilation failed!", e);
				}

				CommonUniforms.addCommonUniforms(builder, programSet.getPack().getIdMap(), programSet.getPackDirectives(), updateNotifier);

				Supplier<ImmutableSet<Integer>> flipped;

				flipped = () -> flippedBeforeShadow;

				TextureStage textureStage = TextureStage.GBUFFERS_AND_SHADOW;

				ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor =
					ProgramSamplers.customTextureSamplerInterceptor(builder, customTextureManager.getCustomTextureIdMap(textureStage));

				IrisSamplers.addRenderTargetSamplers(customTextureSamplerInterceptor, flipped, renderTargets, false);
				IrisImages.addRenderTargetImages(builder, flipped, renderTargets);

				IrisSamplers.addLevelSamplers(customTextureSamplerInterceptor, this, whitePixel, new InputAvailability(true, true, false));

				IrisSamplers.addNoiseSampler(customTextureSamplerInterceptor, customTextureManager.getNoiseTexture());

				if (IrisSamplers.hasShadowSamplers(customTextureSamplerInterceptor)) {
					if (shadowRenderTargets != null) {
						IrisSamplers.addShadowSamplers(customTextureSamplerInterceptor, shadowRenderTargets);
						IrisImages.addShadowColorImages(builder, shadowRenderTargets);
					}
				}

				programs[i] = builder.buildCompute();

				programs[i].setWorkGroupInfo(source.getWorkGroupRelative(), source.getWorkGroups());
			}
		}


		return programs;
	}

	@Override
	public void beginHand() {
		// We need to copy the current depth texture so that depthtex2 can contain the depth values for
		// all non-translucent content without the hand, as required.
		renderTargets.copyPreHandDepth();
	}

	@Override
	public void beginTranslucents() {
		isBeforeTranslucent = false;

		// We need to copy the current depth texture so that depthtex1 can contain the depth values for
		// all non-translucent content, as required.
		renderTargets.copyPreTranslucentDepth();


		// needed to remove blend mode overrides and similar
		beginPass(null);

		isRenderingFullScreenPass = true;

		deferredRenderer.renderAll();

		GLStateManager.enableBlend();
		GLStateManager.enableAlphaTest();

		// note: we are careful not to touch the lightmap texture unit or overlay color texture unit here,
		// so we don't need to do anything to restore them if needed.
		//
		// Previous versions of the code tried to "restore" things by enabling the lightmap & overlay color
		// but that actually broke rendering of clouds and rain by making them appear red in the case of
		// a pack not overriding those shader programs.
		//
		// Not good!

		isRenderingFullScreenPass = false;
	}

	@Override
	public void renderShadows(EntityRenderer levelRenderer, Camera playerCamera) {
		if (shouldRenderPrepareBeforeShadow) {
			isRenderingFullScreenPass = true;

			prepareRenderer.renderAll();

			isRenderingFullScreenPass = false;
		}

		if (shadowRenderer != null) {
			isRenderingShadow = true;

			shadowRenderer.renderShadows(levelRenderer, playerCamera);

			// needed to remove blend mode overrides and similar
			beginPass(null);
			isRenderingShadow = false;
		}

		if (!shouldRenderPrepareBeforeShadow) {
			isRenderingFullScreenPass = true;

			prepareRenderer.renderAll();

			isRenderingFullScreenPass = false;
		}
	}

	@Override
	public void addDebugText(List<String> messages) {
		messages.add("");

		if (shadowRenderer != null) {
			shadowRenderer.addDebugText(messages);
		} else {
			messages.add("[" + Iris.MODNAME + "] Shadow Maps: not used by shader pack");
		}
	}

	@Override
	public OptionalInt getForcedShadowRenderDistanceChunksForDisplay() {
		return forcedShadowRenderDistanceChunks;
	}

	// TODO: better way to avoid this global state?
	private boolean isRenderingWorld = false;
	private boolean isRenderingFullScreenPass = false;

	@Override
	public void beginLevelRendering() {
		isRenderingFullScreenPass = false;
		isRenderingWorld = true;
		isBeforeTranslucent = true;
		isMainBound = true;
		isPostChain = false;
		phase = WorldRenderingPhase.NONE;
		overridePhase = null;
//		HandRenderer.INSTANCE.getBufferSource().resetDrawCalls();

		checkWorld();

		if (!isRenderingWorld) {
			Iris.logger.warn("beginWorldRender was called but we are not currently rendering a world?");
			return;
		}

		if (current != null) {
			throw new IllegalStateException("Called beginLevelRendering but level rendering appears to still be in progress?");
		}

		updateNotifier.onNewFrame();

		// Get ready for world rendering
		prepareRenderTargets();

		setPhase(WorldRenderingPhase.SKY);

		// Render our horizon box before actual sky rendering to avoid being broken by mods that do weird things
		// while rendering the sky.
		//
		// A lot of dimension mods touch sky rendering, FabricSkyboxes injects at HEAD and cancels, etc.
//		DimensionSpecialEffects.SkyType skyType = Minecraft.getMinecraft().theWorld.effects().skyType();

		if (true/*skyType == DimensionSpecialEffects.SkyType.NORMAL*/) {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
			GL11.glDepthMask(false);

			final Vector3d fogColor = GLStateManager.getFogColor();
            GL11.glColor4f((float) fogColor.x, (float) fogColor.y, (float) fogColor.z, 1.0F);

			horizonRenderer.renderHorizon(RenderingState.INSTANCE.getModelViewBuffer());

			GL11.glDepthMask(true);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
		}
	}

	@Override
	public void finalizeLevelRendering() {
		checkWorld();

		if (!isRenderingWorld) {
			Iris.logger.warn("finalizeWorldRendering was called but we are not currently rendering a world?");
			return;
		}

		beginPass(null);

		isRenderingWorld = false;
		phase = WorldRenderingPhase.NONE;
		overridePhase = null;

		isRenderingFullScreenPass = true;

		centerDepthSampler.sampleCenterDepth();

		compositeRenderer.renderAll();
		finalPassRenderer.renderFinalPass();

		isRenderingFullScreenPass = false;
	}

	@Override
	public SodiumTerrainPipeline getSodiumTerrainPipeline() {
		return sodiumTerrainPipeline;
	}

	@Override
	public FrameUpdateNotifier getFrameUpdateNotifier() {
		return updateNotifier;
	}

	@Override
	public WorldRenderingPhase getPhase() {
		if (overridePhase != null) {
			return overridePhase;
		}

		return phase;
	}

	boolean sodiumTerrainRendering = false;

	@Override
	public void syncProgram() {
		matchPass();
	}

	@Override
	public void beginSodiumTerrainRendering() {
		sodiumTerrainRendering = true;
		syncProgram();

	}

	@Override
	public void endSodiumTerrainRendering() {
		sodiumTerrainRendering = false;
		current = null;
		syncProgram();
	}

	@Override
	public void setOverridePhase(WorldRenderingPhase phase) {
		this.overridePhase = phase;

		GbufferPrograms.runPhaseChangeNotifier();
	}

	@Override
	public void setPhase(WorldRenderingPhase phase) {
		this.phase = phase;

		GbufferPrograms.runPhaseChangeNotifier();
	}

	@Override
	public void setInputs(InputAvailability availability) {
		this.inputs = availability;
	}

	@Override
	public void setSpecialCondition(SpecialCondition special) {
		this.special = special;
	}

	@Override
	public RenderTargetStateListener getRenderTargetStateListener() {
		return this;
	}

	@Override
	public int getCurrentNormalTexture() {
		return currentNormalTexture;
	}

	@Override
	public int getCurrentSpecularTexture() {
		return currentSpecularTexture;
	}

	@Override
	public void onBindTexture(int id) {
		if (shouldBindPBR && isRenderingWorld) {
			PBRTextureHolder pbrHolder = PBRTextureManager.INSTANCE.getOrLoadHolder(id);
			currentNormalTexture = pbrHolder.getNormalTexture().getGlTextureId();
			currentSpecularTexture = pbrHolder.getSpecularTexture().getGlTextureId();

			TextureFormat textureFormat = TextureFormatLoader.getFormat();
			if (textureFormat != null) {
				textureFormat.setupTextureParameters(PBRType.NORMAL, pbrHolder.getNormalTexture());
				textureFormat.setupTextureParameters(PBRType.SPECULAR, pbrHolder.getSpecularTexture());
			}

			PBRTextureManager.notifyPBRTexturesChanged();
		}
	}
}
