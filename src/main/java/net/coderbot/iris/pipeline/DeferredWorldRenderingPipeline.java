package net.coderbot.iris.pipeline;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import com.gtnewhorizons.angelica.compat.mojang.Camera;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformMatrix3f;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformMatrix4f;
import org.joml.Matrix3f;
import org.joml.Matrix4fc;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfoCache;
import com.gtnewhorizons.angelica.rendering.RenderingState;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.coderbot.iris.Iris;
import net.coderbot.iris.block_rendering.BlockMaterialMapping;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.celeritas.CeleritasTerrainPipeline;
import net.coderbot.iris.compat.dh.DHCompat;
import net.coderbot.iris.features.FeatureFlags;
import net.coderbot.iris.gbuffer_overrides.matching.InputAvailability;
import net.coderbot.iris.gbuffer_overrides.matching.ProgramTable;
import net.coderbot.iris.gbuffer_overrides.matching.RenderCondition;
import net.coderbot.iris.gbuffer_overrides.matching.TranslucentBlendMatcher;
import net.coderbot.iris.gbuffer_overrides.matching.SpecialCondition;
import net.coderbot.iris.gbuffer_overrides.state.RenderTargetStateListener;
import net.coderbot.iris.gl.blending.AlphaTestOverride;
import net.coderbot.iris.gl.blending.BlendModeOverride;
import net.coderbot.iris.gl.blending.DepthColorStorage;
import net.coderbot.iris.gl.blending.BufferBlendOverride;
import net.coderbot.iris.gl.buffer.ShaderStorageBufferHolder;
import net.coderbot.iris.gl.buffer.ShaderStorageInfo;
import net.coderbot.iris.gl.framebuffer.GlFramebuffer;
import net.coderbot.iris.gl.image.GlImage;
import net.coderbot.iris.gl.image.ImageHolder;
import net.coderbot.iris.gl.image.ImageInformation;
import net.coderbot.iris.gl.program.ComputeProgram;
import net.coderbot.iris.gl.program.Program;
import net.coderbot.iris.gl.program.ProgramBuilder;
import net.coderbot.iris.gl.program.ProgramImages;
import net.coderbot.iris.gl.program.ProgramSamplers;
import net.coderbot.iris.gl.sampler.SamplerHolder;
import net.coderbot.iris.gl.state.FogMode;
import com.gtnewhorizons.angelica.glsm.texture.DepthBufferFormat;
import com.gtnewhorizons.angelica.glsm.texture.TextureType;
import net.coderbot.iris.helpers.Tri;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.pipeline.transform.GlintScrollInjector;
import net.coderbot.iris.pipeline.transform.PatchShaderType;
import net.coderbot.iris.pipeline.transform.TransformPatcher;
import net.coderbot.iris.postprocess.BufferFlipper;
import net.coderbot.iris.postprocess.CenterDepthSampler;
import net.coderbot.iris.postprocess.CompositeRenderer;
import net.coderbot.iris.postprocess.FinalPassRenderer;
import net.coderbot.iris.postprocess.FullScreenQuadRenderer;
import net.coderbot.iris.postprocess.ProgramBuildContext;
import net.coderbot.iris.rendertarget.IRenderTargetExt;
import net.coderbot.iris.rendertarget.ParityFlipState;
import net.coderbot.iris.rendertarget.NativeImageBackedSingleColorTexture;
import net.coderbot.iris.rendertarget.RenderTargets;
import net.coderbot.iris.samplers.IrisImages;
import net.coderbot.iris.samplers.IrisSamplers;
import net.coderbot.iris.shaderpack.CloudSetting;
import net.coderbot.iris.shaderpack.ComputeSource;
import net.coderbot.iris.shaderpack.OptionalBoolean;
import net.coderbot.iris.shaderpack.ParticleRenderingSettings;
import net.coderbot.iris.shaderpack.PackDirectives;
import net.coderbot.iris.shaderpack.PackShadowDirectives;
import net.coderbot.iris.shaderpack.ProgramDirectives;
import net.coderbot.iris.shaderpack.ProgramFallbackResolver;
import net.coderbot.iris.shaderpack.ProgramSet;
import net.coderbot.iris.shaderpack.ProgramSource;
import net.coderbot.iris.shaderpack.loading.ProgramGroup;
import net.coderbot.iris.shaderpack.loading.ProgramId;
import net.coderbot.iris.shaderpack.texture.TextureStage;
import net.coderbot.iris.shadows.ShadowCompositeRenderer;
import net.coderbot.iris.shadows.ShadowRenderTargets;
import net.coderbot.iris.texture.format.TextureFormat;
import net.coderbot.iris.texture.format.TextureFormatLoader;
import net.coderbot.iris.texture.pbr.PBRTextureHolder;
import net.coderbot.iris.texture.pbr.PBRTextureManager;
import net.coderbot.iris.texture.pbr.PBRType;
import net.coderbot.iris.uniforms.CommonUniforms;
import net.coderbot.iris.uniforms.FrameUpdateNotifier;
import net.coderbot.iris.uniforms.ItemMaterialHelper;
import net.coderbot.iris.uniforms.custom.CustomUniforms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.profiler.Profiler;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;
import org.taumc.glsl.ShaderParser;
import org.taumc.glsl.Transformer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * Encapsulates the compiled shader program objects for the currently loaded shaderpack.
 */
public class DeferredWorldRenderingPipeline implements WorldRenderingPipeline, RenderTargetStateListener  {
	private final RenderTargets renderTargets;

	@Nullable
	private ShadowRenderTargets shadowRenderTargets;
	@Nullable
	private ComputeProgram[] shadowComputes;
	@Nullable private ComputeProgram shadowVoxelizationCompute;
	private final Supplier<ShadowRenderTargets> shadowTargetsSupplier;

	private final ProgramTable<Pass> table;

	private ImmutableList<ClearPass> clearPassesFull;
	private ImmutableList<ClearPass> clearPasses;
	private ImmutableList<ClearPass> shadowClearPasses;
	private ImmutableList<ClearPass> shadowClearPassesFull;

	private final ComputeProgram[] setup;
	private final CompositeRenderer beginRenderer;
	private final CompositeRenderer prepareRenderer;

	@Nullable
	private final ShadowRenderer shadowRenderer;
	@Nullable
	private final ShadowCompositeRenderer shadowCompositeRenderer;

    private final CustomUniforms customUniforms;

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

	private final CeleritasTerrainPipeline celeritasTerrainPipeline;
	private final DHCompat dhCompat;

	// Custom images and SSBOs
	private final Set<GlImage> customImages;
	private final GlImage[] imagesToClear;
	@Nullable
	private final ShaderStorageBufferHolder ssboHolder;

	private final Map<Pair<String, InputAvailability>, Map<PatchShaderType, String>> attributeTransforms;
	private final Map<Pair<String, InputAvailability>, Map<PatchShaderType, String>> instancedAttributeTransforms;
	private boolean supportsTesrInstancing = true;
	private final ParityFlipState parityState = new ParityFlipState(AngelicaConfig.shaderParityFlip);
	private final Supplier<ImmutableSet<Integer>> flippedGbuffers;
	private final Supplier<ImmutableSet<Integer>> flippedShadowGbuffers;
	private final Supplier<ImmutableSet<Integer>> flippedAfterPrepareResolved;
	private final Supplier<ImmutableSet<Integer>> flippedBeforeShadowResolved;

	private final HorizonRenderer horizonRenderer = new HorizonRenderer();

	private final float sunPathRotation;
	private final CloudSetting cloudSetting;
	private final boolean shouldRenderUnderwaterOverlay;
	private final boolean shouldRenderVignette;
	private final boolean supportsEndFlash;
	private final boolean shouldRenderSun;
	private final boolean shouldRenderMoon;
	private final boolean shouldRenderStars;
	private final boolean shouldRenderSkyDisc;
	private final boolean shouldRenderWeather;
	private final boolean shouldRenderWeatherParticles;
	private final boolean shouldWriteRainAndSnowToDepthBuffer;
	private final boolean shouldRenderParticlesBeforeDeferred;
	private final boolean shouldRenderPrepareBeforeShadow;
	private final boolean oldLighting;
	private final boolean allowConcurrentCompute;
	private final OptionalInt forcedShadowRenderDistanceChunks;
	private final CloudSetting dhCloudSetting;
	private Pass current = null;
	private RenderCondition currentCondition = null;
	private WorldRenderingPhase overridePhase = null;
	private WorldRenderingPhase phase = WorldRenderingPhase.NONE;
	private WorldRenderingPhase pushedPhase = WorldRenderingPhase.NONE;
	private boolean worldGroupActive = false;
	private boolean isBeforeTranslucent;
	private boolean isRenderingShadow = false;
	private InputAvailability inputs = new InputAvailability(false, false);
	private SpecialCondition special = null;

	private boolean shouldBindPBR;
	private int currentNormalTexture;
	private int currentSpecularTexture;
	private PackDirectives packDirectives;
	private final Set<FeatureFlags> activeFeatures;
	private final ProgramFallbackResolver resolver;

	private int packSamplerUsage;
	private final Profiler profiler = Minecraft.getMinecraft().mcProfiler;

	private static final Vector4f EMPTY_CLEAR_COLOR = new Vector4f(1.0F);

	public DeferredWorldRenderingPipeline(ProgramSet programs) {
		Objects.requireNonNull(programs);

		final long _t0 = System.nanoTime();
		long _tLast = _t0;

		final Map<Integer, CompletableFuture<Map<PatchShaderType, String>>> prepareTransformFutures =
			submitCompositeTransforms(programs.getPrepare(), TextureStage.PREPARE, programs.getPackDirectives().getTextureMap());
		final Map<Integer, CompletableFuture<Map<PatchShaderType, String>>> deferredTransformFutures =
			submitCompositeTransforms(programs.getDeferred(), TextureStage.DEFERRED, programs.getPackDirectives().getTextureMap());
		final Map<Integer, CompletableFuture<Map<PatchShaderType, String>>> compositeTransformFutures =
			submitCompositeTransforms(programs.getComposite(), TextureStage.COMPOSITE_AND_FINAL, programs.getPackDirectives().getTextureMap());

		final CompletableFuture<Map<PatchShaderType, String>> finalTransformFuture =
			programs.getCompositeFinal()
				.filter(ProgramSource::isValid)
				.map(source -> submitCompositeTransform(source, TextureStage.COMPOSITE_AND_FINAL, programs.getPackDirectives().getTextureMap()))
				.orElse(null);

		resolver = new ProgramFallbackResolver(programs);
		final Map<Pair<String, InputAvailability>, CompletableFuture<Map<PatchShaderType, String>>> attributeTransformFutures = submitAttributeTransforms(resolver, ProgramId.values(), false);
		final Map<Pair<String, InputAvailability>, CompletableFuture<Map<PatchShaderType, String>>> instancedTransformFutures = submitAttributeTransforms(resolver, INSTANCED_PROGRAM_IDS, true);

		final Optional<ProgramSource> terrainSource = first(programs.getGbuffersTerrain(), programs.getGbuffersTexturedLit(), programs.getGbuffersTextured(), programs.getGbuffersBasic());
		final Optional<ProgramSource> terrainSolidOverride = programs.getGbuffersTerrainSolid();
		final Optional<ProgramSource> terrainCutoutOverride = programs.getGbuffersTerrainCutout();
		final Optional<ProgramSource> terrainSolidSource = first(terrainSolidOverride, terrainSource);
		final Optional<ProgramSource> terrainCutoutSource = first(terrainCutoutOverride, terrainSource);

		final Optional<ProgramSource> translucentSource = first(programs.getGbuffersWater(), terrainSource);
		final Optional<ProgramSource> shadowSource = programs.getShadow();
		final Optional<ProgramSource> shadowSolidOverride = programs.getShadowSolid();
		final Optional<ProgramSource> shadowCutoutOverride = programs.getShadowCutout();
		final Optional<ProgramSource> shadowSolidSource = first(shadowSolidOverride, shadowSource);
		final Optional<ProgramSource> shadowCutoutSource = first(shadowCutoutOverride, shadowSource);
		final Optional<ProgramSource> shadowTranslucentSource = first(programs.getShadowWater(), shadowSource);

		// Celeritas terrain transform futures
		final CompletableFuture<Map<PatchShaderType, String>> celeritasTerrainFuture = terrainSource.map(DeferredWorldRenderingPipeline::submitCeleritasTerrainTransform).orElse(null);
		final CompletableFuture<Map<PatchShaderType, String>> celeritasTerrainSolidFuture = terrainSolidOverride
			.map(DeferredWorldRenderingPipeline::submitCeleritasTerrainTransform).orElse(celeritasTerrainFuture);
		final CompletableFuture<Map<PatchShaderType, String>> celeritasTerrainCutoutFuture = terrainCutoutOverride
			.map(DeferredWorldRenderingPipeline::submitCeleritasTerrainTransform).orElse(celeritasTerrainFuture);
		final CompletableFuture<Map<PatchShaderType, String>> celeritasTranslucentFuture = translucentSource.map(DeferredWorldRenderingPipeline::submitCeleritasTerrainTransform).orElse(null);
		final CompletableFuture<Map<PatchShaderType, String>> celeritasShadowFuture = shadowSource.map(DeferredWorldRenderingPipeline::submitCeleritasTerrainTransform).orElse(null);
		final CompletableFuture<Map<PatchShaderType, String>> celeritasShadowSolidFuture = shadowSolidOverride
			.map(DeferredWorldRenderingPipeline::submitCeleritasTerrainTransform).orElse(celeritasShadowFuture);
		final CompletableFuture<Map<PatchShaderType, String>> celeritasShadowCutoutFuture = shadowCutoutOverride
			.map(DeferredWorldRenderingPipeline::submitCeleritasTerrainTransform).orElse(celeritasShadowFuture);
		final CompletableFuture<Map<PatchShaderType, String>> celeritasShadowTranslucentFuture = shadowTranslucentSource.map(DeferredWorldRenderingPipeline::submitCeleritasTerrainTransform).orElse(null);

		final Int2ObjectArrayMap<CompletableFuture<String>> setupComputeFutures = submitSetupComputeTransforms(programs.getSetup(), programs.getPackDirectives().getTextureMap());

		this.cloudSetting = programs.getPackDirectives().getCloudSetting();
		this.shouldRenderUnderwaterOverlay = programs.getPackDirectives().underwaterOverlay();
		this.shouldRenderVignette = programs.getPackDirectives().vignette();
		this.supportsEndFlash = programs.getPackDirectives().isSupportsEndFlash();
		this.shouldRenderSun = programs.getPackDirectives().shouldRenderSun();
		this.shouldRenderMoon = programs.getPackDirectives().shouldRenderMoon();
		this.shouldRenderStars = programs.getPackDirectives().shouldRenderStars();
		this.shouldRenderSkyDisc = programs.getPackDirectives().shouldRenderSkyDisc();
		this.shouldRenderWeather = programs.getPackDirectives().shouldRenderWeather();
		this.shouldRenderWeatherParticles = programs.getPackDirectives().shouldRenderWeatherParticles();
		this.shouldWriteRainAndSnowToDepthBuffer = programs.getPackDirectives().rainDepth();
		this.dhCloudSetting = programs.getPackDirectives().getDHCloudSetting();
		this.shouldRenderParticlesBeforeDeferred = programs.getPackDirectives().getParticleRenderingSettings()
			.map(s -> s == ParticleRenderingSettings.BEFORE || s == ParticleRenderingSettings.MIXED)
			.orElse(false);
		this.allowConcurrentCompute = programs.getPackDirectives().getConcurrentCompute();
		this.shouldRenderPrepareBeforeShadow = programs.getPackDirectives().isPrepareBeforeShadow();
		this.oldLighting = programs.getPackDirectives().isOldLighting();
		this.updateNotifier = new FrameUpdateNotifier();

		this.packDirectives = programs.getPackDirectives();
		this.activeFeatures = programs.getPack().getActiveFeatures();

        final Framebuffer main = Minecraft.getMinecraft().getFramebuffer();

        final int depthTextureId = ((IRenderTargetExt)main).iris$getDepthTextureId();
		final int internalFormat = TextureInfoCache.INSTANCE.getInfo(depthTextureId).getInternalFormat();
		final DepthBufferFormat depthBufferFormat = DepthBufferFormat.fromGlEnumOrDefault(internalFormat);

		this.renderTargets = new RenderTargets(main.framebufferWidth, main.framebufferHeight, depthTextureId,
            ((IRenderTargetExt)main).iris$getDepthBufferVersion(), depthBufferFormat,
			programs.getPackDirectives().getRenderTargetDirectives().getRenderTargetSettings(), programs.getPackDirectives());
		this.renderTargets.setParityState(parityState);

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

        this.customUniforms = programs.getPack().customUniforms.build(
            holder -> CommonUniforms.addNonDynamicUniforms(holder, programs.getPack().getIdMap(), programs.getPackDirectives(), this.updateNotifier)
        );

		final var blockIdMaps = BlockMaterialMapping.createBlockIdMaps(
			programs.getPack().getIdMap().getBlockProperties(),
			programs.getPack().getIdMap().hasLegacySection());
		BlockRenderingSettings.INSTANCE.setBlockMetaMatches(blockIdMaps.blockMetaMap());
		BlockRenderingSettings.INSTANCE.setBlockNbtMap(blockIdMaps.tileEntityMap());
		BlockRenderingSettings.INSTANCE.setBlockTypeIds(BlockMaterialMapping.createBlockTypeMap(programs.getPack().getIdMap().getBlockRenderTypeMap()));

		BlockRenderingSettings.INSTANCE.setEntityIds(programs.getPack().getIdMap().getEntityIdMap());
		BlockRenderingSettings.INSTANCE.setEntityNbtMap(BlockMaterialMapping.createNamespacedNbtMap(programs.getPack().getIdMap().getEntityNbtEntries()));

		ItemMaterialHelper.clearCache();
		BlockRenderingSettings.INSTANCE.setItemIds(programs.getPack().getIdMap().getItemIdMap());
		BlockRenderingSettings.INSTANCE.setItemNbtMap(BlockMaterialMapping.createNamespacedNbtMap(programs.getPack().getIdMap().getItemNbtEntries()));
		BlockRenderingSettings.INSTANCE.setAmbientOcclusionLevel(programs.getPackDirectives().getAmbientOcclusionLevel());
		BlockRenderingSettings.INSTANCE.setDisableDirectionalShading(shouldDisableDirectionalShading());
		BlockRenderingSettings.INSTANCE.setUseSeparateAo(programs.getPackDirectives().shouldUseSeparateAo());
		BlockRenderingSettings.INSTANCE.setUseExtendedVertexFormat(true);

		// Don't clobber anything in texture unit 0. It probably won't cause issues, but we're just being cautious here.
		GLStateManager.glActiveTexture(GL13.GL_TEXTURE2);

		customTextureManager = new CustomTextureManager(programs.getPackDirectives(), programs.getPack().getCustomTextureDataMap(), programs.getPack().getIrisCustomTextureDataMap(), programs.getPack().getCustomNoiseTexture());

		whitePixel = new NativeImageBackedSingleColorTexture(255, 255, 255, 255);

		// Initialize custom images
		this.customImages = new HashSet<>();
		final var customImageInfos = programs.getPack().getCustomImages();
		final List<GlImage> clearList = new ArrayList<>();
		for (var entry : customImageInfos.object2ObjectEntrySet()) {
			final ImageInformation info = entry.getValue();
			final GlImage image;
			if (info.isRelative()) {
				image = new GlImage.Relative(info.name(), info.samplerName(), info.format(), info.internalTextureFormat(),
					info.type(), info.clear(), info.relativeWidth(), info.relativeHeight(),
					main.framebufferWidth, main.framebufferHeight);
			} else {
				image = new GlImage(info.name(), info.samplerName(), info.target(), info.format(), info.internalTextureFormat(),
					info.type(), info.clear(), info.width(), info.height(), info.depth());
			}
			customImages.add(image);
			if (image.shouldClear()) {
				clearList.add(image);
			}
		}
		this.imagesToClear = clearList.toArray(new GlImage[0]);

		// Initialize SSBOs
		final var bufferObjectInfos = programs.getPack().getBufferObjects();
		if (!bufferObjectInfos.isEmpty() && RenderSystem.supportsSSBO()) {
			Int2ObjectArrayMap<ShaderStorageInfo> ssboOverrides = new Int2ObjectArrayMap<>();
			bufferObjectInfos.forEach(ssboOverrides::put);
			this.ssboHolder = new ShaderStorageBufferHolder(ssboOverrides, main.framebufferWidth, main.framebufferHeight);
		} else {
			this.ssboHolder = null;
		}

		GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);

		BufferFlipper flipper = new BufferFlipper();

		this.centerDepthSampler = new CenterDepthSampler(() -> getRenderTargets().getDepthTexture(), programs.getPackDirectives().getCenterDepthHalfLife());

		this.shadowMapResolution = programs.getPackDirectives().getShadowDirectives().getResolution();

		this.shadowTargetsSupplier = () -> {
			if (shadowRenderTargets == null) {
				this.shadowRenderTargets = new ShadowRenderTargets(this, shadowMapResolution, shadowDirectives);
			}

			return shadowRenderTargets;
		};

		PatchedShaderPrinter.resetPrintState();

		final ProgramBuildContext beginBuildContext = new ProgramBuildContext(renderTargets, customTextureManager.getNoiseTexture(), updateNotifier, centerDepthSampler, shadowTargetsSupplier, customTextureManager.getCustomTextureIdMap(TextureStage.BEGIN), customUniforms, customImages, customTextureManager.getIrisCustomTextures(), this);
		final ProgramBuildContext prepareBuildContext = new ProgramBuildContext(renderTargets, customTextureManager.getNoiseTexture(), updateNotifier, centerDepthSampler, shadowTargetsSupplier, customTextureManager.getCustomTextureIdMap(TextureStage.PREPARE), customUniforms, customImages, customTextureManager.getIrisCustomTextures(), this);
		final ProgramBuildContext deferredBuildContext = new ProgramBuildContext(renderTargets, customTextureManager.getNoiseTexture(), updateNotifier, centerDepthSampler, shadowTargetsSupplier, customTextureManager.getCustomTextureIdMap(TextureStage.DEFERRED), customUniforms, customImages, customTextureManager.getIrisCustomTextures(), this);
		final ProgramBuildContext compositeBuildContext = new ProgramBuildContext(renderTargets, customTextureManager.getNoiseTexture(), updateNotifier, centerDepthSampler, shadowTargetsSupplier, customTextureManager.getCustomTextureIdMap(TextureStage.COMPOSITE_AND_FINAL), customUniforms, customImages, customTextureManager.getIrisCustomTextures(), this);

		this.setup = createSetupComputes(programs.getSetup(), setupComputeFutures);

		Iris.logger.info("[Load #{}] DWRP phase=pre-renderers elapsed_ms={}", Iris.getShaderPackLoadId(), String.format("%.1f", (System.nanoTime() - _tLast) / 1_000_000.0));
		_tLast = System.nanoTime();
		this.beginRenderer = new CompositeRenderer(programs.getBegin(), programs.getBeginCompute(), flipper, beginBuildContext, programs.getPackDirectives().getExplicitFlips("begin_pre"), null, "begin", TextureStage.BEGIN);
		recordSamplerUsage(beginRenderer.getSamplerUsage());
		Iris.logger.info("[Load #{}] DWRP renderer=begin elapsed_ms={}", Iris.getShaderPackLoadId(), String.format("%.1f", (System.nanoTime() - _tLast) / 1_000_000.0));
		_tLast = System.nanoTime();

		this.flippedBeforeShadow = flipper.snapshot();

		this.prepareRenderer = new CompositeRenderer(programs.getPrepare(), programs.getPrepareCompute(), flipper, prepareBuildContext, programs.getPackDirectives().getExplicitFlips("prepare_pre"), prepareTransformFutures, "prepare", TextureStage.PREPARE);
		recordSamplerUsage(prepareRenderer.getSamplerUsage());
		Iris.logger.info("[Load #{}] DWRP renderer=prepare elapsed_ms={}", Iris.getShaderPackLoadId(), String.format("%.1f", (System.nanoTime() - _tLast) / 1_000_000.0));
		_tLast = System.nanoTime();

		flippedAfterPrepare = flipper.snapshot();

		this.deferredRenderer = new CompositeRenderer(programs.getDeferred(), programs.getDeferredCompute(), flipper, deferredBuildContext, programs.getPackDirectives().getExplicitFlips("deferred_pre"), deferredTransformFutures, "deferred", TextureStage.DEFERRED);
		recordSamplerUsage(deferredRenderer.getSamplerUsage());
		Iris.logger.info("[Load #{}] DWRP renderer=deferred elapsed_ms={}", Iris.getShaderPackLoadId(), String.format("%.1f", (System.nanoTime() - _tLast) / 1_000_000.0));
		_tLast = System.nanoTime();

		flippedAfterTranslucent = flipper.snapshot();

		this.compositeRenderer = new CompositeRenderer(programs.getComposite(), programs.getCompositeCompute(), flipper, compositeBuildContext, programs.getPackDirectives().getExplicitFlips("composite_pre"), compositeTransformFutures, "composite", TextureStage.COMPOSITE_AND_FINAL);
		recordSamplerUsage(compositeRenderer.getSamplerUsage());
		Iris.logger.info("[Load #{}] DWRP renderer=composite elapsed_ms={}", Iris.getShaderPackLoadId(), String.format("%.1f", (System.nanoTime() - _tLast) / 1_000_000.0));
		_tLast = System.nanoTime();
		this.finalPassRenderer = new FinalPassRenderer(programs, compositeBuildContext, flipper.snapshot(), this.compositeRenderer.getFlippedAtLeastOnceFinal(), finalTransformFuture, "final");
		recordSamplerUsage(finalPassRenderer.getSamplerUsage());
		Iris.logger.info("[Load #{}] DWRP phase=final-renderer elapsed_ms={}", Iris.getShaderPackLoadId(), String.format("%.1f", (System.nanoTime() - _tLast) / 1_000_000.0));
		_tLast = System.nanoTime();

		parityState.finalizeParitySet(flipper.snapshot(), programs.getPackDirectives().getRenderTargetDirectives().getBuffersToBeCleared());
		renderTargets.finalizeParity();
		if (parityState.isEnabled()) {
			Iris.logger.info("Parity flip active for buffers {}", parityState.parityBuffers());
		}

		this.flippedGbuffers = () -> parityState.resolve(isBeforeTranslucent ? flippedAfterPrepare : flippedAfterTranslucent);
		this.flippedShadowGbuffers = () -> parityState.resolve(shouldRenderPrepareBeforeShadow ? flippedAfterPrepare : flippedBeforeShadow);
		this.flippedAfterPrepareResolved = () -> parityState.resolve(flippedAfterPrepare);
		this.flippedBeforeShadowResolved = () -> parityState.resolve(flippedBeforeShadow);

		// [(textured=false,lightmap=false), (textured=true,lightmap=false), (textured=true,lightmap=true)]
		final ProgramId[] ids = new ProgramId[] {
				ProgramId.Basic, ProgramId.Textured, ProgramId.TexturedLit,
				ProgramId.SkyBasic, ProgramId.SkyTextured, ProgramId.SkyTextured,
				null, null, ProgramId.Terrain,
				null, null, ProgramId.Water,
				null, ProgramId.Clouds, ProgramId.Clouds,
				null, ProgramId.DamagedBlock, ProgramId.DamagedBlock,
				ProgramId.Block, ProgramId.Block, ProgramId.Block,
				ProgramId.BlockTrans, ProgramId.BlockTrans, ProgramId.BlockTrans,
				ProgramId.BeaconBeam, ProgramId.BeaconBeam, ProgramId.BeaconBeam,
				ProgramId.Entities, ProgramId.Entities, ProgramId.Entities,
				ProgramId.EntitiesTrans, ProgramId.EntitiesTrans, ProgramId.EntitiesTrans,
				ProgramId.Particles, ProgramId.Particles, ProgramId.Particles,
				ProgramId.ParticlesTrans, ProgramId.ParticlesTrans, ProgramId.ParticlesTrans,
				null, ProgramId.ArmorGlint, ProgramId.ArmorGlint,
				null, ProgramId.SpiderEyes, ProgramId.SpiderEyes,
				ProgramId.Hand, ProgramId.Hand, ProgramId.Hand,
				ProgramId.HandWater, ProgramId.HandWater, ProgramId.HandWater,
				null, null, ProgramId.Weather,
				// world border uses textured_lit even though it has no lightmap :/
				null, ProgramId.TexturedLit, ProgramId.TexturedLit,
				ProgramId.Lightning, ProgramId.Lightning, ProgramId.Lightning,
				ProgramId.ShadowWater, ProgramId.ShadowWater, ProgramId.ShadowWater,
				ProgramId.Shadow, ProgramId.Shadow, ProgramId.Shadow
		};

		if (ids.length != RenderCondition.values().length * 3) {
			throw new IllegalStateException("Program ID table length mismatch");
		}

		this.attributeTransforms = new HashMap<>();
		for (Map.Entry<Pair<String, InputAvailability>, CompletableFuture<Map<PatchShaderType, String>>> entry : attributeTransformFutures.entrySet()) {
			try {
				this.attributeTransforms.put(entry.getKey(), entry.getValue().join());
			} catch (Exception e) {
				Iris.logger.error("Failed to transform shader: {}", entry.getKey().getLeft(), e);
				throw new RuntimeException("Shader transformation failed for " + entry.getKey().getLeft(), e);
			}
		}
		Iris.logger.info("[Load #{}] DWRP phase=attribute-join elapsed_ms={}", Iris.getShaderPackLoadId(), String.format("%.1f", (System.nanoTime() - _tLast) / 1_000_000.0));
		_tLast = System.nanoTime();

		this.instancedAttributeTransforms = new HashMap<>();
		for (Map.Entry<Pair<String, InputAvailability>, CompletableFuture<Map<PatchShaderType, String>>> entry : instancedTransformFutures.entrySet()) {
			try {
				this.instancedAttributeTransforms.put(entry.getKey(), entry.getValue().join());
			} catch (Exception e) {
				Iris.logger.warn("Instanced transform failed for {}; disabling TESR instancing", entry.getKey().getLeft(), e);
				supportsTesrInstancing = false;
				instancedAttributeTransforms.clear();
				break;
			}
		}

		final Map<Pair<ProgramId, InputAvailability>, Pass> cachedPasses = new HashMap<>();

		this.shadowComputes = createShadowComputes(programs.getShadowCompute());

		this.table = new ProgramTable<>((condition, availability) -> {
			final int idx;

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

			final ProgramId finalId = id;

			return cachedPasses.computeIfAbsent(Pair.of(id, availability), p -> {
				final ProgramSource source = resolver.resolveNullable(p.getLeft());

				if (condition == RenderCondition.SHADOW || condition == RenderCondition.SHADOW_TRANSLUCENT) {
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
					return createPass(source, availability,
						condition == RenderCondition.SHADOW || condition == RenderCondition.SHADOW_TRANSLUCENT, finalId);
				} catch (Exception e) {
					throw new RuntimeException("Failed to create pass for " + source.getName() + " for rendering condition "
						+ condition + " specialized to input availability " + availability, e);
				}
			});
		});
		Iris.logger.info("[Load #{}] DWRP phase=gbuffer-table elapsed_ms={}", Iris.getShaderPackLoadId(), String.format("%.1f", (System.nanoTime() - _tLast) / 1_000_000.0));
		_tLast = System.nanoTime();
		if (shadowRenderTargets == null && shadowDirectives.isShadowEnabled() == OptionalBoolean.TRUE) {
			shadowRenderTargets = new ShadowRenderTargets(this, shadowMapResolution, shadowDirectives);
		}

		if (shadowRenderTargets != null) {
			this.shadowClearPasses = ClearPassCreator.createShadowClearPasses(shadowRenderTargets, false, shadowDirectives);
			this.shadowClearPassesFull = ClearPassCreator.createShadowClearPasses(shadowRenderTargets, true, shadowDirectives);

			this.shadowCompositeRenderer = new ShadowCompositeRenderer(this, programs.getPackDirectives(),
				programs.getShadowComposite(), programs.getShadowCompCompute(), this.shadowRenderTargets,
				ssboHolder,
				customTextureManager.getNoiseTexture(), updateNotifier,
				customTextureManager.getCustomTextureIdMap(TextureStage.SHADOWCOMP),
				customImages, programs.getPackDirectives().getExplicitFlips("shadowcomp_pre"),
				customTextureManager.getIrisCustomTextures(), customUniforms);
			recordSamplerUsage(shadowCompositeRenderer.getSamplerUsage());

			if (programs.getPackDirectives().getShadowDirectives().isShadowEnabled().orElse(true)) {
				this.shadowRenderer = new ShadowRenderer(programs.getShadow().orElse(null),
					programs.getPackDirectives(), shadowRenderTargets, shadowCompositeRenderer,
					() -> usesSampler(IrisSamplers.USAGE_SHADOWTEX1));
				Program shadowProgram = table.match(RenderCondition.SHADOW, new InputAvailability(true, true)).getProgram();
				Program shadowWaterProgram = table.match(RenderCondition.SHADOW_TRANSLUCENT, new InputAvailability(true, true)).getProgram();
				shadowRenderer.setUsesImages((shadowProgram != null && shadowProgram.getActiveImages() > 0)
					|| (shadowWaterProgram != null && shadowWaterProgram.getActiveImages() > 0));
				shadowRenderer.setPlayerReflectionCaptureEnabled((shadowProgram != null && GLStateManager.glGetUniformLocation(shadowProgram.getProgramId(), "playerAtlas_img") != -1) || (shadowWaterProgram != null && GLStateManager.glGetUniformLocation(shadowWaterProgram.getProgramId(), "playerAtlas_img") != -1));
			} else {
				shadowRenderer = null;
			}
		} else {
			this.shadowClearPasses = ImmutableList.of();
			this.shadowClearPassesFull = ImmutableList.of();
			this.shadowCompositeRenderer = null;
			this.shadowRenderer = null;
		}
		Iris.logger.info("[Load #{}] DWRP phase=shadow elapsed_ms={}", Iris.getShaderPackLoadId(), String.format("%.1f", (System.nanoTime() - _tLast) / 1_000_000.0));
		_tLast = System.nanoTime();

        this.customUniforms.optimise();

		this.clearPassesFull = ClearPassCreator.createClearPasses(renderTargets, true, programs.getPackDirectives().getRenderTargetDirectives());
		this.clearPasses = ClearPassCreator.createClearPasses(renderTargets, false, programs.getPackDirectives().getRenderTargetDirectives());

		boolean hasSetup = false;
		for (ComputeProgram program : setup) {
			if (program != null) {
				if (!hasSetup) {
					hasSetup = true;
					renderTargets.onFullClear();
					final Vector4f fogColor = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
					clearPassesFull.forEach(clearPass -> clearPass.execute(fogColor));
				}
				program.use();
				customUniforms.push(program);
				program.dispatch(1, 1);
			}
		}
		if (hasSetup) {
			ComputeProgram.unbind();
			RenderSystem.memoryBarrier(GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL42.GL_TEXTURE_FETCH_BARRIER_BIT | GL43.GL_SHADER_STORAGE_BARRIER_BIT);
		}

		// Terrain pipeline sampler/image factory setup follows.

		Supplier<ImmutableSet<Integer>> flipped = flippedGbuffers;

		IntFunction<ProgramSamplers> createTerrainSamplers = (programId) -> {
			ProgramSamplers.Builder builder = ProgramSamplers.builder(programId, IrisSamplers.WORLD_RESERVED_TEXTURE_UNITS);
			ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor = ProgramSamplers.customTextureSamplerInterceptor(builder, customTextureManager.getCustomTextureIdMap(TextureStage.GBUFFERS_AND_SHADOW));

			IrisSamplers.addRenderTargetSamplers(customTextureSamplerInterceptor, flipped, renderTargets, false, this);
			IrisSamplers.addLevelSamplers(customTextureSamplerInterceptor, this, whitePixel, new InputAvailability(true, true));
			recordSamplerUsage(IrisSamplers.addWorldDepthSamplers(customTextureSamplerInterceptor, renderTargets));
			IrisSamplers.addNoiseSampler(customTextureSamplerInterceptor, customTextureManager.getNoiseTexture());

			// Bind custom images as samplers (for texture() access to voxel_sampler, floodfill_sampler, etc.)
			IrisSamplers.addCustomImages(customTextureSamplerInterceptor, customImages);
			// Bind custom textures (PNG files from shader pack)
			IrisSamplers.addCustomTextures(customTextureSamplerInterceptor, customTextureManager.getIrisCustomTextures());

			if (IrisSamplers.hasShadowSamplers(customTextureSamplerInterceptor)) {
				recordSamplerUsage(IrisSamplers.addShadowSamplers(customTextureSamplerInterceptor, Objects.requireNonNull(shadowRenderTargets), null, true));
			}

			return builder.build();
		};

		IntFunction<ProgramImages> createTerrainImages = (programId) -> {
			ProgramImages.Builder builder = ProgramImages.builder(programId);

			IrisImages.addRenderTargetImages(builder, flipped, renderTargets);
			// Bind custom images as image units (for imageLoad/imageStore)
			IrisImages.addCustomImages(builder, customImages);

			if (IrisImages.hasShadowImages(builder)) {
				IrisImages.addShadowColorImages(builder, Objects.requireNonNull(shadowRenderTargets), null);
			}

			return builder.build();
		};

		IntFunction<ProgramSamplers> createShadowTerrainSamplers = (programId) -> {
			ProgramSamplers.Builder builder = ProgramSamplers.builder(programId, IrisSamplers.WORLD_RESERVED_TEXTURE_UNITS);
			ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor = ProgramSamplers.customTextureSamplerInterceptor(builder, customTextureManager.getCustomTextureIdMap(TextureStage.GBUFFERS_AND_SHADOW));

			IrisSamplers.addRenderTargetSamplers(customTextureSamplerInterceptor, flippedAfterPrepareResolved, renderTargets, false, this);
			IrisSamplers.addLevelSamplers(customTextureSamplerInterceptor, this, whitePixel, new InputAvailability(true, true));
			IrisSamplers.addNoiseSampler(customTextureSamplerInterceptor, customTextureManager.getNoiseTexture());

			// Bind custom images as samplers (for texture() access to voxel_sampler, floodfill_sampler, etc.)
			IrisSamplers.addCustomImages(customTextureSamplerInterceptor, customImages);
			// Bind custom textures (PNG files from shader pack)
			IrisSamplers.addCustomTextures(customTextureSamplerInterceptor, customTextureManager.getIrisCustomTextures());

			// Only initialize these samplers if the shadow map renderer exists. Otherwise, this program shouldn't be used at all?
			if (IrisSamplers.hasShadowSamplers(customTextureSamplerInterceptor)) {
				recordSamplerUsage(IrisSamplers.addShadowSamplers(customTextureSamplerInterceptor, Objects.requireNonNull(shadowRenderTargets), null, true));
			}

			return builder.build();
		};
        IntFunction<ProgramImages> createShadowTerrainImages = (programId) -> {
			ProgramImages.Builder builder = ProgramImages.builder(programId);

			IrisImages.addRenderTargetImages(builder, flippedAfterPrepareResolved, renderTargets);
			// Bind custom images as image units (for imageLoad/imageStore - voxelization uses this in shadow pass)
			IrisImages.addCustomImages(builder, customImages);

			if (IrisImages.hasShadowImages(builder)) {
				IrisImages.addShadowColorImages(builder, Objects.requireNonNull(shadowRenderTargets), null);
			}

			return builder.build();
		};
		final GlFramebuffer celeritasShadowFb = (shadowRenderTargets != null && shadowRenderer != null)
			? shadowRenderTargets.createShadowFramebuffer(shadowRenderTargets.snapshot(), new int[] {0, 1})
			: null;

		this.celeritasTerrainPipeline = new CeleritasTerrainPipeline(createTerrainSamplers,
			shadowRenderer == null ? null : createShadowTerrainSamplers, createTerrainImages,
			shadowRenderer == null ? null : createShadowTerrainImages, this.customUniforms,
			terrainSolidSource,
			terrainCutoutSource,
			translucentSource,
			shadowSolidSource,
			shadowCutoutSource,
			shadowTranslucentSource,
			celeritasTerrainSolidFuture, celeritasTerrainCutoutFuture, celeritasTranslucentFuture,
			celeritasShadowSolidFuture, celeritasShadowCutoutFuture, celeritasShadowTranslucentFuture,
			renderTargets, flippedAfterPrepare, flippedAfterTranslucent,
			celeritasShadowFb);

		this.dhCompat = new DHCompat(this, shadowDirectives.isDhShadowEnabled().orElse(true));

		this.shadowVoxelizationCompute = createShadowVoxelizationCompute();
		bindVoxelizationMatrixUniforms();
	}

	private RenderTargets getRenderTargets() {
		return renderTargets;
	}

	private void recordSamplerUsage(int mask) {
		packSamplerUsage |= mask;
	}

	private boolean usesSampler(int bit) {
		return (packSamplerUsage & bit) != 0;
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
	public boolean supportsEndFlash() {
		return supportsEndFlash;
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
	public boolean shouldRenderStars() {
		return shouldRenderStars;
	}

	@Override
	public boolean shouldRenderSkyDisc() {
		return shouldRenderSkyDisc;
	}

	@Override
	public boolean shouldRenderWeather() {
		return shouldRenderWeather;
	}

	@Override
	public boolean shouldRenderWeatherParticles() {
		return shouldRenderWeatherParticles;
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

	@Override
	public boolean hasFeature(FeatureFlags flag) {
		return activeFeatures.contains(flag);
	}

	@Override
	public Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> getTextureMap() {
		return packDirectives.getTextureMap();
	}

	private RenderCondition getCondition(WorldRenderingPhase phase) {
		if (isRenderingShadow) {
			return switch (phase) {
				case TERRAIN_TRANSLUCENT, TRIPWIRE -> RenderCondition.SHADOW_TRANSLUCENT;
				default -> RenderCondition.SHADOW;
			};
		}

		if (special != null) {
			if (special == SpecialCondition.BEACON_BEAM) {
				return RenderCondition.BEACON_BEAM;
			} else if (special == SpecialCondition.ENTITY_EYES) {
				return RenderCondition.ENTITY_EYES;
			} else if (special == SpecialCondition.GLINT) {
				return RenderCondition.GLINT;
			} else if (special == SpecialCondition.LIGHTNING) {
				return RenderCondition.LIGHTNING;
			}
		}

		switch (phase) {
			case NONE, OUTLINE, DEBUG:
				return RenderCondition.DEFAULT;
			case PARTICLES:
				return isTranslucentDraw() ? RenderCondition.PARTICLES_TRANSLUCENT : RenderCondition.PARTICLES;
			case SKY, SUNSET, CUSTOM_SKY, SUN, MOON, STARS, VOID:
				return RenderCondition.SKY;
			case TERRAIN_SOLID, TERRAIN_CUTOUT, TERRAIN_CUTOUT_MIPPED:
				return RenderCondition.TERRAIN_OPAQUE;
			case ENTITIES:
				return isTranslucentDraw() ? RenderCondition.ENTITIES_TRANSLUCENT : RenderCondition.ENTITIES;
			case BLOCK_ENTITIES:
				return isTranslucentDraw() ? RenderCondition.BLOCK_ENTITIES_TRANSLUCENT : RenderCondition.BLOCK_ENTITIES;
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

	private boolean isTranslucentDraw() {
		return declaredTranslucent != null ? declaredTranslucent : TranslucentBlendMatcher.matchesCurrentState();
	}

	public boolean shouldOverrideShaders() {
		return isRenderingLevelGeometry() && isMainBound;
	}

	public boolean isRenderingLevelGeometry() {
		return isRenderingWorld && !isRenderingFullScreenPass && !isPostChain;
	}

	public Pass getActivePassProgram() {
		return current;
	}

	public int getActivePassProgramId() {
		if (current == null) return -1;
		final Program p = current.getProgram();
		return p != null ? p.getProgramId() : -1;
	}

	/**
	 * Called when a mod overrides the GL program away from the active Iris pass
	 */
	public void onModProgramOverride() {
		if (current != null) {
			modProgramOverrode = true;
			programBeforeModOverride = getActivePassProgramId();
		}
		current = null;
		currentCondition = null;
	}

	public void restorePassAfterModProgram(int newProgram) {
		if (refreshingPass || drivingProgram || !modProgramOverrode) {
			return;
		}
		if (newProgram != 0 && newProgram != programBeforeModOverride) {
			return;
		}
		refreshingPass = true;
		try {
			current = null;
			matchPass();
		} finally {
			modProgramOverrode = false;
			programBeforeModOverride = -1;
			refreshingPass = false;
		}
	}

	private boolean refreshingPass;
	private boolean modProgramOverrode;
	private int programBeforeModOverride = -1;

	public void onVanillaBlendChanged() {
		if (matchingBlend || drivingProgram || refreshingPass) {
			return;
		}

		if (declaredTranslucent != null) {
			return;
		}

		final WorldRenderingPhase phase = getPhase();
		if (phase != WorldRenderingPhase.ENTITIES && phase != WorldRenderingPhase.BLOCK_ENTITIES) {
			return;
		}

		matchingBlend = true;
		try {
			matchPass();
		} finally {
			matchingBlend = false;
		}
	}

	private boolean matchingBlend;

	private void matchPass() {
		if (!shouldOverrideShaders()) {
			return;
		}

		matchPass(getCondition(getPhase()));
	}

	private void matchPass(RenderCondition condition) {
		currentCondition = condition;
		beginPass(table.match(condition, inputs));
	}

	@Override
	public void onEntityRenderBoundary() {
		if (!shouldOverrideShaders()) {
			return;
		}

		final RenderCondition condition = getCondition(getPhase());
		if (condition != currentCondition) {
			matchPass(condition);
		}
	}

	public void beginPass(Pass pass) {
		if (current == pass) {
			return;
		}

		if (current != null) {
			current.stopUsing();
		}

		current = pass;

		drivingProgram = true;
		try {
			if (pass != null) {
				pass.use();
			} else {
				Program.unbind();
			}
		} finally {
			drivingProgram = false;
		}
	}

	private boolean drivingProgram;

	@Override
	public void rebindCurrentPass() {
		final Pass pass = this.current;
		if (pass == null) {
			if (GLStateManager.getActiveProgram() == 0) {
				matchPass();
			}
			return;
		}
		final Program program = pass.getProgram();
		final GlFramebuffer expected = isBeforeTranslucent ? pass.framebufferBeforeTranslucents : pass.framebufferAfterTranslucents;
		if (program != null && GLStateManager.getActiveProgram() == program.getProgramId() && GLStateManager.getDrawFramebuffer() == expected.getId()) {
			return;
		}
		pass.use();
	}

	private Pass createDefaultPass() {
		final GlFramebuffer framebufferBeforeTranslucents = renderTargets.createGbufferFramebuffer(flippedAfterPrepare, new int[] {0});
		final GlFramebuffer framebufferAfterTranslucents = renderTargets.createGbufferFramebuffer(flippedAfterTranslucent, new int[] {0});


		return new Pass(null, framebufferBeforeTranslucents, framebufferAfterTranslucents, null,
			null, Collections.emptyList(), false);
	}

	private static ProgramBuilder beginBuilder(String name, Map<PatchShaderType, String> transformed) {
		final String vertex = transformed.get(PatchShaderType.VERTEX);
		final String geometry = transformed.get(PatchShaderType.GEOMETRY);
		final String tessControl = transformed.get(PatchShaderType.TESS_CONTROL);
		final String tessEval = transformed.get(PatchShaderType.TESS_EVAL);
		final String fragment = transformed.get(PatchShaderType.FRAGMENT);

		PatchedShaderPrinter.debugPatchedShaders(name, vertex, geometry, tessControl, tessEval, fragment);

		return ProgramBuilder.begin(name, vertex, geometry, tessControl, tessEval, fragment, IrisSamplers.WORLD_RESERVED_TEXTURE_UNITS);
	}

	private Pass createPass(ProgramSource source, InputAvailability availability, boolean shadow, ProgramId id) {
		// Use pre-computed transform if available, otherwise transform synchronously
		Pair<String, InputAvailability> key = Pair.of(source.getName(), availability);
		Map<PatchShaderType, String> transformed = attributeTransforms.get(key);

		if (transformed == null) {
			// Fallback to synchronous transform if not pre-computed
			String vertex = source.getVertexSource().orElseThrow(NullPointerException::new);
			final boolean scrollGlint = GlintScrollInjector.shouldInject(id, source);
			transformed = TransformPatcher.patchAttributes(
				vertex,
				source.getGeometrySource().orElse(null),
				source.getTessControlSource().orElse(null),
				source.getTessEvalSource().orElse(null),
				source.getFragmentSource().orElseThrow(NullPointerException::new),
				availability,
				scrollGlint);
		}

		ProgramBuilder builder = beginBuilder(source.getName(), transformed);

		final Pass pass = createPassInner(builder, source.getDirectives(), availability, shadow, id);
		pass.setInstancingKey(source.getName(), availability, shadow);
		return pass;
	}

	private void wireGbufferProgram(ProgramBuilder builder, InputAvailability availability, boolean shadow) {
		CommonUniforms.addDynamicUniforms(builder, FogMode.PER_VERTEX);
        this.customUniforms.assignTo(builder);

		final Supplier<ImmutableSet<Integer>> flipped = shadow ? flippedShadowGbuffers : flippedGbuffers;

		TextureStage textureStage = TextureStage.GBUFFERS_AND_SHADOW;

		ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor =
			ProgramSamplers.customTextureSamplerInterceptor(builder,
				customTextureManager.getCustomTextureIdMap(textureStage));

		IrisSamplers.addRenderTargetSamplers(customTextureSamplerInterceptor, flipped, renderTargets, false, this);
		IrisImages.addRenderTargetImages(builder, flipped, renderTargets);

		if (!shouldBindPBR) {
			shouldBindPBR = IrisSamplers.hasPBRSamplers(customTextureSamplerInterceptor);
		}

		IrisSamplers.addLevelSamplers(customTextureSamplerInterceptor, this, whitePixel, availability);

		if (!shadow) {
			recordSamplerUsage(IrisSamplers.addWorldDepthSamplers(customTextureSamplerInterceptor, renderTargets));
		}

		IrisSamplers.addNoiseSampler(customTextureSamplerInterceptor, customTextureManager.getNoiseTexture());

		IrisSamplers.addCustomImages(customTextureSamplerInterceptor, customImages);
		IrisSamplers.addCustomTextures(customTextureSamplerInterceptor, customTextureManager.getIrisCustomTextures());
		IrisImages.addCustomImages(builder, customImages);

		if (IrisSamplers.hasShadowSamplers(customTextureSamplerInterceptor)) {
			if (!shadow) {
				shadowTargetsSupplier.get();
			}

			if (shadowRenderTargets != null) {
				recordSamplerUsage(IrisSamplers.addShadowSamplers(customTextureSamplerInterceptor, shadowRenderTargets, null, true));
				IrisImages.addShadowColorImages(builder, shadowRenderTargets, null);
			}
		}
	}

	private Pass createPassInner(ProgramBuilder builder, ProgramDirectives programDirectives, InputAvailability availability, boolean shadow, ProgramId id) {

		wireGbufferProgram(builder, availability, shadow);

		GlFramebuffer framebufferBeforeTranslucents;
		GlFramebuffer framebufferAfterTranslucents;

		if (shadow) {
			final int[] shadowDrawBuffers = programDirectives.hasUnknownDrawBuffers() ? new int[] { 0, 1 } : programDirectives.getDrawBuffers();
			framebufferBeforeTranslucents = shadowTargetsSupplier.get().createShadowFramebuffer(shadowRenderTargets.snapshot(), shadowDrawBuffers);
			framebufferAfterTranslucents = framebufferBeforeTranslucents;
		} else {
			framebufferBeforeTranslucents = renderTargets.createGbufferFramebuffer(flippedAfterPrepare, programDirectives.getDrawBuffers());
			framebufferAfterTranslucents = renderTargets.createGbufferFramebuffer(flippedAfterTranslucent, programDirectives.getDrawBuffers());
		}

		builder.bindAttributeLocation(11, "mc_Entity");
		builder.bindAttributeLocation(12, "mc_midTexCoord");
		builder.bindAttributeLocation(13, "at_tangent");
		builder.bindAttributeLocation(14, "at_midBlock");

		AlphaTestOverride alphaTestOverride = programDirectives.getAlphaTestOverride()
			.orElse(id.getDefaultAlphaTestOverride());

		List<BufferBlendOverride> bufferOverrides = new ArrayList<>();

		programDirectives.getBufferBlendOverrides().forEach(information -> {
			int index = Ints.indexOf(programDirectives.getDrawBuffers(), information.getIndex());
			if (index > -1) {
				bufferOverrides.add(new BufferBlendOverride(index, information.getBlendMode()));
			}
		});

        Pass pass = new Pass(builder.build(), framebufferBeforeTranslucents, framebufferAfterTranslucents, alphaTestOverride,
            programDirectives.getBlendModeOverride().orElse(id.getBlendModeOverride()), bufferOverrides, shadow);

        this.customUniforms.mapholderToPass(builder, pass);

		return pass;
	}

	public void addGbufferOrShadowSamplers(SamplerHolder samplers, ImageHolder images, Supplier<ImmutableSet<Integer>> flipped,
										   boolean isShadowPass, boolean hasTexture, boolean hasLightmap, boolean hasOverlay) {
		TextureStage textureStage = TextureStage.GBUFFERS_AND_SHADOW;

		ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor =
			ProgramSamplers.customTextureSamplerInterceptor(samplers, customTextureManager.getCustomTextureIdMap(textureStage));

		IrisSamplers.addRenderTargetSamplers(customTextureSamplerInterceptor, flipped, renderTargets, false, this);
		IrisImages.addRenderTargetImages(images, flipped, renderTargets);

		if (!shouldBindPBR) {
			shouldBindPBR = IrisSamplers.hasPBRSamplers(customTextureSamplerInterceptor);
		}

		IrisSamplers.addLevelSamplers(customTextureSamplerInterceptor, this, whitePixel, new InputAvailability(hasTexture, hasLightmap));
		if (!isShadowPass) {
			recordSamplerUsage(IrisSamplers.addWorldDepthSamplers(customTextureSamplerInterceptor, renderTargets));
		}
		IrisSamplers.addNoiseSampler(customTextureSamplerInterceptor, customTextureManager.getNoiseTexture());
		IrisSamplers.addCustomImages(customTextureSamplerInterceptor, customImages);
		IrisSamplers.addCustomTextures(customTextureSamplerInterceptor, customTextureManager.getIrisCustomTextures());
		IrisImages.addCustomImages(images, customImages);

		if (IrisSamplers.hasShadowSamplers(customTextureSamplerInterceptor)) {
			recordSamplerUsage(IrisSamplers.addShadowSamplers(customTextureSamplerInterceptor, shadowTargetsSupplier.get(), null, true));
		}

		if (isShadowPass || IrisImages.hasShadowImages(images)) {
			IrisImages.addShadowColorImages(images, shadowTargetsSupplier.get(), null);
		}
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

	public CloudSetting getDHCloudSetting() {
		return dhCloudSetting;
	}

	public Optional<ProgramSource> getDHTerrainShader() {
		return resolver.resolve(ProgramId.DhTerrain);
	}

	public Optional<ProgramSource> getDHGenericShader() {
		return resolver.resolve(ProgramId.DhGeneric);
	}

	public Optional<ProgramSource> getDHWaterShader() {
		return resolver.resolve(ProgramId.DhWater);
	}

	public Optional<ProgramSource> getDHShadowShader() {
		return resolver.resolve(ProgramId.DhShadow);
	}

	public CustomUniforms getCustomUniforms() {
		return customUniforms;
	}

	public GlFramebuffer createDHFramebuffer(ProgramSource sources, boolean trans) {
		return renderTargets.createDHFramebuffer(trans ? flippedAfterTranslucent : flippedAfterPrepare,
			sources.getDirectives().getDrawBuffers());
	}

	public ImmutableSet<Integer> getFlippedBeforeShadow() {
		return parityState.resolve(flippedBeforeShadow);
	}

	public ImmutableSet<Integer> getFlippedAfterPrepare() {
		return parityState.resolve(flippedAfterPrepare);
	}

	public ImmutableSet<Integer> getFlippedAfterTranslucent() {
		return parityState.resolve(flippedAfterTranslucent);
	}

	public GlFramebuffer createDHFramebufferShadow(ProgramSource sources) {

		return shadowRenderTargets.createDHFramebuffer(ImmutableSet.of(), new int[]{0, 1});
	}

	public boolean hasShadowRenderTargets() {
		return shadowRenderTargets != null;
	}

	public final class Pass {
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

		private String instancingSourceName;
		private InputAvailability instancingAvailability;
		private boolean instancingShadow;
		@Nullable private Program instancedVariant;
		private boolean instancedVariantAttempted;

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
			DepthColorStorage.unlockDepthColor();

			if (isBeforeTranslucent) {
				framebufferBeforeTranslucents.bind();
			} else {
				framebufferAfterTranslucents.bind();
			}

			if (shadowViewport) {
				GLStateManager.glViewport(0, 0, shadowMapResolution, shadowMapResolution);
			} else {
                final Framebuffer main = Minecraft.getMinecraft().getFramebuffer();
				GLStateManager.glViewport(0, 0, main.framebufferWidth, main.framebufferHeight);
			}

			// Apply state overrides before program.use() so that uniforms (e.g. iris_currentAlphaTest)
			// read the correct GLSM state during upload, not the stale vanilla state.
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

			if (program != null) {
				program.use();
			}

			DeferredWorldRenderingPipeline.this.customUniforms.push(this);
		}

		public void stopUsing() {
			DepthColorStorage.unlockDepthColor();

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

		void setInstancingKey(String sourceName, InputAvailability availability, boolean shadow) {
			this.instancingSourceName = sourceName;
			this.instancingAvailability = availability;
			this.instancingShadow = shadow;
		}

		@Nullable
		Program getInstancedVariant() {
			if (!instancedVariantAttempted) {
				instancedVariantAttempted = true;
				if (instancingSourceName != null && supportsTesrInstancing) {
					instancedVariant = buildInstancedVariant(instancingSourceName, instancingAvailability, instancingShadow);
				}
			}
			return instancedVariant;
		}

		public void destroy() {
			if (this.program != null) {
				this.program.destroy();
			}
			if (this.instancedVariant != null) {
				this.instancedVariant.destroy();
				this.instancedVariant = null;
			}
		}
	}

	@Nullable
	private Program buildInstancedVariant(String sourceName, InputAvailability availability, boolean shadow) {
		final Map<PatchShaderType, String> transformed = instancedAttributeTransforms.get(Pair.of(sourceName, availability));
		if (transformed == null) {
			return null;
		}
		try {
			final ProgramBuilder builder = beginBuilder(sourceName + "_instanced", transformed);
			wireGbufferProgram(builder, availability, shadow);
			final Program variant = builder.build();
			this.customUniforms.mapholderToPass(builder, variant);
			return variant;
		} catch (Exception e) {
			Iris.logger.warn("TESR instanced variant link failed for {}; disabling TESR instancing", sourceName, e);
			supportsTesrInstancing = false;
			return null;
		}
	}

	public boolean supportsTesrInstancing() {
		return supportsTesrInstancing;
	}

	public boolean hasTesrInstancedVariant() {
		final Pass pass = current;
		return pass != null && pass.getInstancedVariant() != null;
	}

	public void bindTesrInstancedVariant() {
		final Pass pass = current;
		final Program variant = pass.getInstancedVariant();
		variant.use();
		this.customUniforms.push(variant);
	}

	@Override
	public void destroy() {
		DepthColorStorage.unlockDepthColor();
		BlendModeOverride.restore();
		AlphaTestOverride.restore();
		GLStateManager.enableAlphaTest();
		GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.1f);
		GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
		GLStateManager.incrementFragmentGeneration();

		FullScreenQuadRenderer.clearLocCache();
		destroyPasses(table);

		// Destroy the composite rendering pipeline
		//
		// This destroys all the loaded composite programs as well.
		beginRenderer.destroy();
		prepareRenderer.destroy();
		compositeRenderer.destroy();
		deferredRenderer.destroy();
		finalPassRenderer.destroy();
		centerDepthSampler.destroy();

		// Destroy setup compute programs
		if (setup != null) {
			for (ComputeProgram compute : setup) {
				if (compute != null) {
					compute.destroy();
				}
			}
		}

		// Destroy shadow compute programs
		if (shadowComputes != null) {
			for (ComputeProgram compute : shadowComputes) {
				if (compute != null) {
					compute.destroy();
				}
			}
		}
		if (shadowVoxelizationCompute != null) {
			shadowVoxelizationCompute.destroy();
			shadowVoxelizationCompute = null;
		}

		// Destroy shadow composite renderer
		if (shadowCompositeRenderer != null) {
			shadowCompositeRenderer.destroy();
		}

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
		dhCompat.clearPipeline();

		// destroy the shadow render targets
		if (shadowRenderTargets != null) {
			shadowRenderTargets.destroy();
		}

		// Destroy custom textures and the static samplers (normals, specular, and noise)
		customTextureManager.destroy();
		whitePixel.deleteGlTexture();

		// Destroy custom images
		for (GlImage image : customImages) {
			image.destroy();
		}
		customImages.clear();

		// Destroy SSBOs
		if (ssboHolder != null) {
			ssboHolder.destroyBuffers();
		}
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
		parityState.onFrameStart();
		// Make sure we're using texture unit 0 for this.
		GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);

		if (shadowRenderTargets != null) {
			profiler.startSection("iris_shadow_clear");
			if (packDirectives.getShadowDirectives().isShadowEnabled() == OptionalBoolean.FALSE) {
				if (shadowRenderTargets.isFullClearRequired()) {
					shadowRenderTargets.onFullClear();
					for (ClearPass clearPass : shadowClearPassesFull) {
						clearPass.execute(EMPTY_CLEAR_COLOR);
					}
				}
			} else {
				// Clear depth first, regardless of any color clearing.
				shadowRenderTargets.getDepthSourceFb().bind();
                GLStateManager.glClear(GL11.GL_DEPTH_BUFFER_BIT);

				ImmutableList<ClearPass> passes;

				for (ComputeProgram computeProgram : shadowComputes) {
					if (computeProgram != null) {
                        computeProgram.use();
                        this.customUniforms.push(computeProgram);
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
					clearPass.execute(EMPTY_CLEAR_COLOR);
				}
			}
			profiler.endSection();
		}

		profiler.startSection("iris_clear");
        final Framebuffer main = Minecraft.getMinecraft().getFramebuffer();

        final int depthTextureId = ((IRenderTargetExt)main).iris$getDepthTextureId();
		final int internalFormat = TextureInfoCache.INSTANCE.getInfo(depthTextureId).getInternalFormat();
		final DepthBufferFormat depthBufferFormat = DepthBufferFormat.fromGlEnumOrDefault(internalFormat);

		final boolean changed = renderTargets.resizeIfNeeded(((IRenderTargetExt)main).iris$getDepthBufferVersion(), depthTextureId, main.framebufferWidth,
            main.framebufferHeight, depthBufferFormat, packDirectives);

		if (changed) {
			profiler.startSection("iris_clear_resize");
			beginRenderer.recalculateSizes();
			prepareRenderer.recalculateSizes();
			deferredRenderer.recalculateSizes();
			compositeRenderer.recalculateSizes();
			finalPassRenderer.recalculateSwapPassSize();

			this.clearPassesFull.forEach(clearPass -> renderTargets.destroyFramebuffer(clearPass.getFramebuffer()));
			this.clearPasses.forEach(clearPass -> renderTargets.destroyFramebuffer(clearPass.getFramebuffer()));

			this.clearPassesFull = ClearPassCreator.createClearPasses(renderTargets, true, packDirectives.getRenderTargetDirectives());
			this.clearPasses = ClearPassCreator.createClearPasses(renderTargets, false, packDirectives.getRenderTargetDirectives());

			// Resize custom images if needed
			for (GlImage image : customImages) {
				image.updateNewSize(main.framebufferWidth, main.framebufferHeight);
			}

			// Resize SSBOs if needed
			if (ssboHolder != null) {
				ssboHolder.hasResizedScreen(main.framebufferWidth, main.framebufferHeight);
			}

			// Re-run setup computes after resize (SETUP dispatches with 1,1 workgroups)
			boolean ranSetup = false;
			for (ComputeProgram program : setup) {
				if (program != null) {
					ranSetup = true;
					program.use();
					customUniforms.push(program);
					program.dispatch(1, 1);
				}
			}
			if (ranSetup) {
				ComputeProgram.unbind();
				RenderSystem.memoryBarrier(GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL42.GL_TEXTURE_FETCH_BARRIER_BIT | GL43.GL_SHADER_STORAGE_BARRIER_BIT);
			}
			profiler.endSection();
		}

		final ImmutableList<ClearPass> passes;

		if (renderTargets.isFullClearRequired()) {
			renderTargets.onFullClear();
			parityState.reset();
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
		profiler.endSection();
	}

	@Nullable
	private ComputeProgram createShadowVoxelizationCompute() {
		if (celeritasTerrainPipeline == null) return null;
		final Optional<String> sourceOpt = celeritasTerrainPipeline.getShadowVoxelizationComputeSource();
		if (sourceOpt.isEmpty()) return null;
		final String src = sourceOpt.get();
		final ProgramBuilder builder;
		try {
			PatchedShaderPrinter.debugPatchedShaders("shadow_voxelization_compute", null, null, null, src);
			builder = ProgramBuilder.beginCompute("shadow_voxelization", src, IrisSamplers.WORLD_RESERVED_TEXTURE_UNITS);
		} catch (RuntimeException e) {
			Iris.logger.error("Voxelization compute shader compilation failed; colored lighting will not render", e);
			return null;
		}
		CommonUniforms.addDynamicUniforms(builder, FogMode.PER_VERTEX);
		this.customUniforms.assignTo(builder);
		final Supplier<ImmutableSet<Integer>> flipped = () -> flippedBeforeShadow;
		final TextureStage textureStage = TextureStage.GBUFFERS_AND_SHADOW;
		final ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor =
			ProgramSamplers.customTextureSamplerInterceptor(builder, customTextureManager.getCustomTextureIdMap(textureStage));
		IrisSamplers.addRenderTargetSamplers(customTextureSamplerInterceptor, flipped, renderTargets, false, this);
		IrisImages.addRenderTargetImages(builder, flipped, renderTargets);
		IrisSamplers.addLevelSamplers(customTextureSamplerInterceptor, this, whitePixel, new InputAvailability(true, true));
		IrisSamplers.addNoiseSampler(customTextureSamplerInterceptor, customTextureManager.getNoiseTexture());
		IrisSamplers.addCustomImages(customTextureSamplerInterceptor, customImages);
		IrisSamplers.addCustomTextures(customTextureSamplerInterceptor, customTextureManager.getIrisCustomTextures());
		if (IrisSamplers.hasShadowSamplers(customTextureSamplerInterceptor) && shadowRenderTargets != null) {
			IrisSamplers.addShadowSamplers(customTextureSamplerInterceptor, shadowRenderTargets, null, true);
			IrisImages.addShadowColorImages(builder, shadowRenderTargets, null);
		}
		customImages.stream()
			.sorted(Comparator.comparing(GlImage::getName))
			.forEach(image -> builder.addTextureImage(image::getId, image.getInternalFormat(), image.getName()));
		final ComputeProgram program = builder.buildCompute();
		this.customUniforms.mapholderToPass(builder, program);
		return program;
	}

	@Nullable
	public ComputeProgram getShadowVoxelizationCompute() { return shadowVoxelizationCompute; }

	@Nullable
	public ShadowRenderer getShadowRenderer() { return shadowRenderer; }

	private GlUniformMatrix4f voxelizationModelView;
	private GlUniformMatrix3f voxelizationNormalMatrix;
	private final Matrix3f voxelizationNormalScratch = new Matrix3f();

	private void bindVoxelizationMatrixUniforms() {
		if (shadowVoxelizationCompute == null) return;
		final int id = shadowVoxelizationCompute.getProgramId();
		final int modelView = GLStateManager.glGetUniformLocation(id, "iris_ModelViewMatrix");
		final int normal = GLStateManager.glGetUniformLocation(id, "iris_NormalMatrix");
		this.voxelizationModelView = modelView >= 0 ? new GlUniformMatrix4f(modelView) : null;
		this.voxelizationNormalMatrix = normal >= 0 ? new GlUniformMatrix3f(normal) : null;
	}

	public void prepareShadowVoxelizationCompute(Matrix4fc modelView) {
		if (shadowVoxelizationCompute == null) return;
		if (voxelizationModelView != null) {
			voxelizationModelView.set(modelView);
		}
		if (voxelizationNormalMatrix != null) {
			voxelizationNormalScratch.set(modelView).invert().transpose();
			voxelizationNormalMatrix.set(voxelizationNormalScratch);
		}
		customUniforms.push(shadowVoxelizationCompute);
	}

	private ComputeProgram[] createShadowComputes(ComputeSource[] compute) {
		ComputeProgram[] programs = new ComputeProgram[compute.length];
		for (int i = 0; i < programs.length; i++) {
			ComputeSource source = compute[i];
			if (source == null || !source.getSource().isPresent()) {
				continue;
			} else {
				ProgramBuilder builder;

				try {
					String transformed = TransformPatcher.patchCompute(source.getName(), source.getSource().orElse(null), TextureStage.GBUFFERS_AND_SHADOW, getTextureMap());
					PatchedShaderPrinter.debugPatchedShaders(source.getName() + "_compute", null, null, null, transformed);
					builder = ProgramBuilder.beginCompute(source.getName(), transformed, IrisSamplers.WORLD_RESERVED_TEXTURE_UNITS);
				} catch (RuntimeException e) {
					// TODO: Better error handling
					throw new RuntimeException("Shader compilation failed!", e);
				}

				CommonUniforms.addDynamicUniforms(builder, FogMode.PER_VERTEX);
                this.customUniforms.assignTo(builder);

				final Supplier<ImmutableSet<Integer>> flipped = flippedBeforeShadowResolved;

				TextureStage textureStage = TextureStage.GBUFFERS_AND_SHADOW;

				ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor =
					ProgramSamplers.customTextureSamplerInterceptor(builder, customTextureManager.getCustomTextureIdMap(textureStage));

				IrisSamplers.addRenderTargetSamplers(customTextureSamplerInterceptor, flipped, renderTargets, false, this);
				IrisImages.addRenderTargetImages(builder, flipped, renderTargets);

				IrisSamplers.addLevelSamplers(customTextureSamplerInterceptor, this, whitePixel, new InputAvailability(true, true));

				IrisSamplers.addNoiseSampler(customTextureSamplerInterceptor, customTextureManager.getNoiseTexture());

				IrisSamplers.addCustomImages(customTextureSamplerInterceptor, customImages);
				IrisSamplers.addCustomTextures(customTextureSamplerInterceptor, customTextureManager.getIrisCustomTextures());

				if (IrisSamplers.hasShadowSamplers(customTextureSamplerInterceptor)) {
					if (shadowRenderTargets != null) {
						recordSamplerUsage(IrisSamplers.addShadowSamplers(customTextureSamplerInterceptor, shadowRenderTargets, null, true));
						IrisImages.addShadowColorImages(builder, shadowRenderTargets, null);
					}
				}

				IrisImages.addCustomImages(builder, customImages);

				programs[i] = builder.buildCompute();

                this.customUniforms.mapholderToPass(builder, programs[i]);

				programs[i].setWorkGroupInfo(source.getWorkGroupRelative(), source.getWorkGroups(), null);
			}
		}


		return programs;
	}

	private ComputeProgram[] createSetupComputes(ComputeSource[] compute, Int2ObjectArrayMap<CompletableFuture<String>> futures) {
		ComputeProgram[] programs = new ComputeProgram[compute.length];
		for (int i = 0; i < programs.length; i++) {
			ComputeSource source = compute[i];
			if (source == null || !source.getSource().isPresent()) {
				continue;
			} else {
				ProgramBuilder builder;

				try {
					final CompletableFuture<String> future = futures.get(i);
					final String transformed = future != null ? future.join() : TransformPatcher.patchCompute(source.getName(), source.getSource().orElse(null), TextureStage.SETUP, getTextureMap());
					PatchedShaderPrinter.debugPatchedShaders(source.getName() + "_compute", null, null, null, transformed);
					builder = ProgramBuilder.beginCompute(source.getName(), transformed, IrisSamplers.COMPOSITE_RESERVED_TEXTURE_UNITS);
				} catch (RuntimeException e) {
					throw new RuntimeException("Shader compilation failed for setup compute " + source.getName() + "!", e);
				}

				CommonUniforms.addDynamicUniforms(builder, FogMode.OFF);
				this.customUniforms.assignTo(builder);

				ImmutableSet<Integer> empty = ImmutableSet.of();
				Supplier<ImmutableSet<Integer>> flipped = () -> empty;

				ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor =
					ProgramSamplers.customTextureSamplerInterceptor(builder, customTextureManager.getCustomTextureIdMap(TextureStage.SETUP));

				IrisSamplers.addRenderTargetSamplers(customTextureSamplerInterceptor, flipped, renderTargets, true, this);
				IrisSamplers.addCustomImages(customTextureSamplerInterceptor, customImages);
				IrisSamplers.addCustomTextures(customTextureSamplerInterceptor, customTextureManager.getIrisCustomTextures());

				IrisImages.addRenderTargetImages(builder, flipped, renderTargets);
				IrisImages.addCustomImages(builder, customImages);

				IrisSamplers.addNoiseSampler(customTextureSamplerInterceptor, customTextureManager.getNoiseTexture());
				recordSamplerUsage(IrisSamplers.addCompositeSamplers(customTextureSamplerInterceptor, renderTargets));

				if (IrisSamplers.hasShadowSamplers(customTextureSamplerInterceptor)) {
					if (shadowRenderTargets != null) {
						recordSamplerUsage(IrisSamplers.addShadowSamplers(customTextureSamplerInterceptor, shadowRenderTargets, null, true));
						IrisImages.addShadowColorImages(builder, shadowRenderTargets, null);
					}
				}

				programs[i] = builder.buildCompute();

				this.customUniforms.mapholderToPass(builder, programs[i]);

				programs[i].setWorkGroupInfo(source.getWorkGroupRelative(), source.getWorkGroups(), null);
			}
		}

		return programs;
	}

	@Override
	public void beginHand() {
		isRenderingFullScreenPass = true;
		beginPass(null);
		currentCondition = null;
		profiler.startSection("iris_center_depth");
		centerDepthSampler.sampleCenterDepth();
		profiler.endSection();
		isRenderingFullScreenPass = false;

		if (!usesSampler(IrisSamplers.USAGE_DEPTHTEX2)) {
			return;
		}

		// We need to copy the current depth texture so that depthtex2 can contain the depth values for
		// all non-translucent content without the hand, as required.
		profiler.startSection("iris_hand_depth_copy");
		renderTargets.copyPreHandDepth();
		profiler.endSection();
	}

	@Override
	public void beginTranslucents() {
		isBeforeTranslucent = false;

		// We need to copy the current depth texture so that depthtex1 can contain the depth values for
		// all non-translucent content, as required.
		if (usesSampler(IrisSamplers.USAGE_DEPTHTEX1)) {
			profiler.startSection("iris_translucent_depth_copy");
			renderTargets.copyPreTranslucentDepth();
			profiler.endSection();
		}


		// needed to remove blend mode overrides and similar
		beginPass(null);

		isRenderingFullScreenPass = true;

		profiler.startSection("iris_deferred");
		GLDebug.pushGroup("deferred");
		try {
			deferredRenderer.renderAll();
		} finally {
			GLDebug.popGroup();
			profiler.endSection();
		}

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
	public void preSubmitShadowGraph(int frame, boolean spectator) {
		if (shadowRenderer != null) shadowRenderer.preSubmitGraphUpdate(frame, spectator);
	}

	@Override
	public void renderShadows(EntityRenderer levelRenderer, Camera playerCamera) {
		if (shouldRenderPrepareBeforeShadow) {
			isRenderingFullScreenPass = true;

			profiler.startSection("iris_prepare_passes");
			GLDebug.pushGroup("prepare");
			try {
				prepareRenderer.renderAll();
			} finally {
				GLDebug.popGroup();
				profiler.endSection();
			}

			isRenderingFullScreenPass = false;
		}

		if (shadowRenderer != null) {
			isRenderingShadow = true;
			matchPass();  // Ensure shadow shader is bound for entity rendering

			GLDebug.pushGroup("shadow");
			try {
				shadowRenderer.renderShadows(levelRenderer, playerCamera);
			} finally {
				GLDebug.popGroup();
			}

			// needed to remove blend mode overrides and similar
			beginPass(null);
			isRenderingShadow = false;
		}

		if (!shouldRenderPrepareBeforeShadow) {
			isRenderingFullScreenPass = true;

			profiler.startSection("iris_prepare_passes");
			GLDebug.pushGroup("prepare");
			try {
				prepareRenderer.renderAll();
			} finally {
				GLDebug.popGroup();
				profiler.endSection();
			}

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
		final Framebuffer mainFb = Minecraft.getMinecraft().getFramebuffer();
		if (mainFb == null || mainFb.framebufferWidth < 16 || mainFb.framebufferHeight < 16) {
			return;
		}

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

		profiler.startSection("iris_uniforms");
		updateNotifier.onNewFrame();

        this.customUniforms.update();

		// Make sure SSBO is bound
		if (ssboHolder != null) {
			ssboHolder.setupBuffers();
		}
		profiler.endSection();

		// Get ready for world rendering
		prepareRenderTargets();

		// Clear custom images that need clearing each frame
		if (imagesToClear.length > 0) {
			profiler.startSection("iris_clear_images");
			for (GlImage image : imagesToClear) {
				image.clear();
			}
			profiler.endSection();
		}

		GLDebug.pushGroup("world");
		worldGroupActive = true;

		isRenderingFullScreenPass = true;
		profiler.startSection("iris_begin_passes");
		GLDebug.pushGroup("begin");
		try {
			beginRenderer.renderAll();
		} finally {
			GLDebug.popGroup();
			profiler.endSection();
		}
		isRenderingFullScreenPass = false;

		setPhase(WorldRenderingPhase.SKY);

		// Render our horizon box before actual sky rendering to avoid being broken by mods that do weird things
		// while rendering the sky.
		//
		// A lot of dimension mods touch sky rendering, FabricSkyboxes injects at HEAD and cancels, etc.
//		DimensionSpecialEffects.SkyType skyType = Minecraft.getMinecraft().theWorld.effects().skyType();

		if (true/*skyType == DimensionSpecialEffects.SkyType.NORMAL*/) {
			profiler.startSection("iris_horizon");
            GLStateManager.glDisable(GL11.GL_TEXTURE_2D);
			GLStateManager.glDepthMask(false);

			final Vector3d fogColor = GLStateManager.getFogColor();
            GLStateManager.glColor4f((float) fogColor.x, (float) fogColor.y, (float) fogColor.z, 1.0F);

			horizonRenderer.renderHorizon(RenderingState.INSTANCE.getModelViewBuffer());

			GLStateManager.glDepthMask(true);
            GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
			profiler.endSection();
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
		syncPhaseDebugGroup();

		isRenderingFullScreenPass = true;

		profiler.startSection("iris_composites");
		GLDebug.pushGroup("composite");
		try {
			compositeRenderer.renderAll();
		} finally {
			GLDebug.popGroup();
			profiler.endSection();
		}

		profiler.startSection("iris_final_pass");
		GLDebug.pushGroup("final");
		try {
			finalPassRenderer.renderFinalPass();
		} finally {
			GLDebug.popGroup();
			profiler.endSection();
		}

		isRenderingFullScreenPass = false;

		if (worldGroupActive) {
			GLDebug.popGroup();
			worldGroupActive = false;
		}
	}

	@Override
	public CeleritasTerrainPipeline getCeleritasTerrainPipeline() {
		return celeritasTerrainPipeline;
	}

	@Override
	public FrameUpdateNotifier getFrameUpdateNotifier() {
		return updateNotifier;
	}

	@Override
	public DHCompat getDHCompat() {
		return dhCompat;
	}

	@Override
	public WorldRenderingPhase getPhase() {
		if (overridePhase != null) {
			return overridePhase;
		}

		return phase;
	}

	@Override
	public void setOverridePhase(WorldRenderingPhase phase) {
		this.overridePhase = phase;
		syncPhaseDebugGroup();
		matchPass();
		GbufferPrograms.runPhaseChangeNotifier();
	}

	@Override
	public void setPhase(WorldRenderingPhase phase) {
		this.phase = phase;
		syncPhaseDebugGroup();
		matchPass();
		GbufferPrograms.runPhaseChangeNotifier();
	}

	private static final String[] PHASE_GROUP_NAMES = buildPhaseGroupNames();

	private static String[] buildPhaseGroupNames() {
		final WorldRenderingPhase[] phases = WorldRenderingPhase.values();
		final String[] names = new String[phases.length];
		for (int i = 0; i < phases.length; i++) {
			names[i] = "phase:" + phases[i].name();
		}
		return names;
	}

	private void syncPhaseDebugGroup() {
		final WorldRenderingPhase target = overridePhase != null ? overridePhase : phase;
		if (target == pushedPhase) return;
		if (pushedPhase != WorldRenderingPhase.NONE) {
			GLDebug.popGroup();
		}
		if (target != WorldRenderingPhase.NONE) {
			GLDebug.pushGroup(PHASE_GROUP_NAMES[target.ordinal()]);
		}
		pushedPhase = target;
	}

	@Override
	public void setInputs(InputAvailability availability) {
		this.inputs = availability;
		matchPass();
	}

	@Override
	public void setSpecialCondition(SpecialCondition special) {
		this.special = special;
		matchPass();
	}

	@Override
	public void setDeclaredTranslucency(@Nullable Boolean translucent) {
		this.declaredTranslucent = translucent;
		matchPass();
	}

	@Nullable
	private Boolean declaredTranslucent;

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
			final PBRTextureHolder pbrHolder = PBRTextureManager.INSTANCE.getOrLoadHolder(id);
			currentNormalTexture = pbrHolder.getNormalTexture().getGlTextureId();
			currentSpecularTexture = pbrHolder.getSpecularTexture().getGlTextureId();

            final TextureFormat textureFormat = TextureFormatLoader.getFormat();
			if (textureFormat != null) {
				textureFormat.setupTextureParameters(PBRType.NORMAL, pbrHolder.getNormalTexture());
				textureFormat.setupTextureParameters(PBRType.SPECULAR, pbrHolder.getSpecularTexture());
			}

			PBRTextureManager.notifyPBRTexturesChanged();
		}
	}

	private static final InputAvailability INPUT_NONE = new InputAvailability(false, false);
	private static final InputAvailability INPUT_LIGHTMAP_ONLY = new InputAvailability(false, true);
	private static final InputAvailability INPUT_TEXTURE = new InputAvailability(true, false);
	private static final InputAvailability INPUT_TEXTURE_LIGHTMAP = new InputAvailability(true, true);
	private static final InputAvailability[] INPUT_AVAILABILITIES = { INPUT_NONE, INPUT_LIGHTMAP_ONLY, INPUT_TEXTURE, INPUT_TEXTURE_LIGHTMAP };

	private static CompletableFuture<Map<PatchShaderType, String>> submitCompositeTransform(ProgramSource source, TextureStage stage,
		Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap) {
		return Iris.ShaderTransformExecutor.submitTracked(() -> TransformPatcher.patchComposite(
			source.getVertexSource().orElse(null),
			source.getGeometrySource().orElse(null),
			source.getTessControlSource().orElse(null),
			source.getTessEvalSource().orElse(null),
			source.getFragmentSource().orElse(null),
			stage,
			textureMap));
	}

	private static Map<Integer, CompletableFuture<Map<PatchShaderType, String>>> submitCompositeTransforms(ProgramSource[] sources,
		TextureStage stage, Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap) {
		// Count valid sources for initial capacity
		int count = 0;
		for (ProgramSource source : sources) {
			if (source != null && source.isValid()) count++;
		}
		final Map<Integer, CompletableFuture<Map<PatchShaderType, String>>> futures = new HashMap<>(count);
		for (int i = 0; i < sources.length; i++) {
			if (sources[i] != null && sources[i].isValid()) {
				futures.put(i, submitCompositeTransform(sources[i], stage, textureMap));
			}
		}
		return futures;
	}

	private static Map<Pair<String, InputAvailability>, CompletableFuture<Map<PatchShaderType, String>>>
			submitAttributeTransforms(ProgramFallbackResolver resolver, ProgramId[] ids, boolean instanced) {
		final Map<Pair<String, InputAvailability>, CompletableFuture<Map<PatchShaderType, String>>> futures = new HashMap<>();
		final Set<String> processedSourceNames = new HashSet<>();
		for (ProgramId id : ids) {
			if (id.getGroup() == ProgramGroup.Dh) continue;
			final ProgramSource source = resolver.resolveNullable(id);
			if (source == null || !processedSourceNames.add(source.getName())) {
				continue;
			}
			if (instanced && referencesMvBuiltinsOutsideVertex(source)) {
				Iris.logger.info("TESR instancing: {} references MV builtins outside the vertex stage, keeping CPU path", source.getName());
				continue;
			}
			final String vertexSource = source.getVertexSource().orElse(null);
			final boolean scrollGlint = GlintScrollInjector.shouldInject(id, source);
			for (InputAvailability avail : INPUT_AVAILABILITIES) {
				futures.put(Pair.of(source.getName(), avail), Iris.ShaderTransformExecutor.submitTracked(() -> {
					final String geometry = source.getGeometrySource().orElse(null);
					final String tessControl = source.getTessControlSource().orElse(null);
					final String tessEval = source.getTessEvalSource().orElse(null);
					final String fragment = source.getFragmentSource().orElse(null);
					return instanced ? TransformPatcher.patchAttributesInstanced(vertexSource, geometry, tessControl, tessEval, fragment, avail, scrollGlint)
						: TransformPatcher.patchAttributes(vertexSource, geometry, tessControl, tessEval, fragment, avail, scrollGlint);
				}));
			}
		}
		return futures;
	}

	private static final ProgramId[] INSTANCED_PROGRAM_IDS = {
		ProgramId.Block, ProgramId.BlockTrans, ProgramId.Entities, ProgramId.EntitiesTrans,
		ProgramId.Shadow, ProgramId.ShadowWater
	};

	private static final String[] MV_BUILTINS = {
		"gl_ModelViewMatrix", "gl_ModelViewMatrixInverse",
		"gl_ModelViewProjectionMatrix", "gl_ModelViewProjectionMatrixInverse",
		"gl_NormalMatrix", "ftransform"
	};

	static boolean referencesMvBuiltins(String source) {
		if (source == null) {
			return false;
		}
		try {
			final Transformer transformer = new Transformer(ShaderParser.parseShader(source).full());
			for (String builtin : MV_BUILTINS) {
				if (transformer.containsCall(builtin)) {
					return true;
				}
			}
			return false;
		} catch (Exception e) {
			return true;
		}
	}

	private static boolean referencesMvBuiltinsOutsideVertex(ProgramSource source) {
		return referencesMvBuiltins(source.getGeometrySource().orElse(null))
			|| referencesMvBuiltins(source.getTessControlSource().orElse(null))
			|| referencesMvBuiltins(source.getTessEvalSource().orElse(null))
			|| referencesMvBuiltins(source.getFragmentSource().orElse(null));
	}

	private static CompletableFuture<Map<PatchShaderType, String>> submitCeleritasTerrainTransform(ProgramSource source) {
		return Iris.ShaderTransformExecutor.submitTracked(() -> TransformPatcher.patchCeleritasTerrain(source.getVertexSource().orElse(null), source.getGeometrySource().orElse(null), source.getFragmentSource().orElse(null)));
	}

	private static Int2ObjectArrayMap<CompletableFuture<String>> submitSetupComputeTransforms(ComputeSource[] compute,
		Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap) {
		if (compute == null) return new Int2ObjectArrayMap<>(0);
		final Int2ObjectArrayMap<CompletableFuture<String>> futures = new Int2ObjectArrayMap<>(compute.length);
		for (int i = 0; i < compute.length; i++) {
			final ComputeSource source = compute[i];
			if (source == null || !source.getSource().isPresent()) continue;
			final String name = source.getName();
			final String src = source.getSource().get();
			futures.put(i, Iris.ShaderTransformExecutor.submitTracked(() -> TransformPatcher.patchCompute(name, src, TextureStage.SETUP, textureMap)));
		}
		return futures;
	}

	@SafeVarargs
	private static <T> Optional<T> first(Optional<T>... candidates) {
		for (Optional<T> candidate : candidates) {
			if (candidate.isPresent()) {
				return candidate;
			}
		}
		return Optional.empty();
	}

}
