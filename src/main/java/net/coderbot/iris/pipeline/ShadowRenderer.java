package net.coderbot.iris.pipeline;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizons.angelica.compat.mojang.Camera;
import com.gtnewhorizons.angelica.compat.mojang.GameModeUtil;
import com.gtnewhorizons.angelica.compat.toremove.MatrixStack;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.profiling.RenderClassTimings;
import com.gtnewhorizons.angelica.rendering.tesr.ModelPartBatcher;
import com.gtnewhorizons.angelica.rendering.tesr.TesrBatchRenderer;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.rendering.PlayerReflectionCapture;
import com.gtnewhorizons.angelica.rendering.RenderingState;
import com.gtnewhorizons.angelica.rendering.celeritas.AngelicaRenderSectionManager;
import com.gtnewhorizons.angelica.rendering.celeritas.CeleritasWorldRenderer;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.coderbot.iris.Iris;
import net.coderbot.iris.compat.dh.DHCompat;
import net.coderbot.iris.gui.option.IrisVideoSettings;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.shaderpack.PackDirectives;
import net.coderbot.iris.shaderpack.PackShadowDirectives;
import net.coderbot.iris.shaderpack.ProgramSource;
import net.coderbot.iris.shaderpack.ShadowCullState;
import net.coderbot.iris.shadow.ShadowMatrices;
import net.coderbot.iris.shadows.CullingDataCache;
import net.coderbot.iris.shadows.ShadowCompositeRenderer;
import net.coderbot.iris.shadows.ShadowGraphGate;
import net.coderbot.iris.shadows.ShadowRenderTargets;
import net.coderbot.iris.shadows.frustum.BoxCuller;
import net.coderbot.iris.shadows.frustum.CullEverythingFrustum;
import net.coderbot.iris.shadows.frustum.FrustumHolder;
import net.coderbot.iris.shadows.frustum.advanced.AdvancedShadowCullingFrustum;
import net.coderbot.iris.shadows.frustum.advanced.SafeZoneCullingFrustum;
import net.coderbot.iris.shadows.frustum.fallback.BoxCullingFrustum;
import net.coderbot.iris.shadows.frustum.fallback.NonCullingFrustum;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.iris.uniforms.CelestialUniforms;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.culling.Frustrum;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.embeddedt.embeddium.impl.render.viewport.ViewportProvider;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBTextureSwizzle;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

public class ShadowRenderer {
	private static final long P_SHADOW_ENTITIES_RENDERED = Tracy.plotHandle("shadow.entitiesRendered");
	private static final Tracy.ZoneId Z_GEN_MIPMAP = Tracy.zoneId("genMipmap", Tracy.COLOR_IRIS);
	private static final Tracy.ZoneId Z_SHADOW_FRUSTUM = Tracy.zoneId("shadowFrustum", Tracy.COLOR_IRIS);
	private static final Tracy.ZoneId Z_SHADOW_VIEWPORT = Tracy.zoneId("shadowViewport", Tracy.COLOR_IRIS);
	private static final Tracy.ZoneId Z_SHADOW_MODEL_PARTS = Tracy.zoneId("shadowModelParts", Tracy.COLOR_IRIS);

	public static final Matrix4f MODELVIEW = new Matrix4f();
    public static final FloatBuffer MODELVIEW_BUFFER = BufferUtils.createFloatBuffer(16);
	public static final Matrix4f PROJECTION = new Matrix4f();
	public static final List<List<TileEntity>> visibleTileEntities = new ArrayList<>();
	public static final List<List<TileEntity>> globalTileEntities = new ArrayList<>();
	public static boolean ACTIVE = false;

	public static Frustrum FRUSTUM;

	private static final Comparator<Entity> ENTITY_CLASS_COMPARATOR = Comparator.comparingInt(a -> System.identityHashCode(a.getClass()));
	private static final NonCullingFrustum NON_CULLING_FRUSTUM = new NonCullingFrustum();
	private static final CullEverythingFrustum CULL_EVERYTHING_FRUSTUM = new CullEverythingFrustum();

	public static ShadowRenderTargets CURRENT_TARGETS = null;
	private final float halfPlaneLength;
	private final float voxelDistance;
	private final float renderDistanceMultiplier;
	private final float entityShadowDistanceMultiplier;
	private final int resolution;
	private final float intervalSize;
	private final Float fov;
	private final ShadowRenderTargets targets;
	private final ShadowCullState packCullingState;
	private final ShadowCompositeRenderer compositeRenderer;
	private boolean packHasVoxelization;
	private boolean playerReflectionCaptureEnabled = true;
	private final boolean shouldRenderTerrain;
	private final boolean shouldRenderTranslucent;
	private final boolean shouldRenderEntities;
	private final boolean shouldRenderPlayer;
	private final boolean shouldRenderBlockEntities;
	private final float sunPathRotation;
	private final List<MipmapPass> mipmapPasses = new ArrayList<>();
	private final String debugStringOverall;
	private FrustumHolder terrainFrustumHolder;
	private FrustumHolder entityFrustumHolder;
	private int renderedShadowEntities = 0;
	private int renderedShadowTileEntities = 0;
	private Profiler profiler;
	private final ObjectArrayList<Entity> renderedEntitiesList = new ObjectArrayList<>(64);
	private final MatrixStack shadowModelView = new MatrixStack();
	private final CelestialUniforms celestialUniforms;


	private static final class FrustumCaches {
		final AdvancedShadowCullingFrustum advancedFrustum = new AdvancedShadowCullingFrustum();
		BoxCuller boxCuller;
		BoxCullingFrustum boxCullingFrustum;
		BoxCuller advancedBoxCuller;
		double lastBoxCullerDistance = -1;
		double lastAdvancedBoxCullerDistance = -1;
	}

	private static final long SHADOW_RELAY_MAX_AGE_NANOS = 250_000_000L;

	public static boolean SHADOW_TERRAIN_RELAID = true;
	private static float activeShadowAngle = Float.NaN;

	private final FrustumCaches terrainFrustumCaches = new FrustumCaches();
	private final FrustumCaches entityFrustumCaches = new FrustumCaches();
	private float lastGraphShadowAngle = Float.NaN;
	private float relayShadowAngle = Float.NaN;
	private FrustumHolder preSubmitFrustumHolder = new FrustumHolder();
	private final FrustumCaches preSubmitFrustumCaches = new FrustumCaches();
	private boolean preSubmitActive;
	private float preSubmittedShadowAngle = Float.NaN;

	public void preSubmitGraphUpdate(int frame, boolean spectator) {
		final AngelicaRenderSectionManager rsm = CeleritasWorldRenderer.getInstance().getRenderSectionManager();
		final float currentShadowAngle = getShadowAngle();
		if (ShadowGraphGate.shouldMarkDirty(lastGraphShadowAngle, currentShadowAngle)) {
			rsm.markShadowGraphDirty();
			lastGraphShadowAngle = currentShadowAngle;
		}
		if (!rsm.isShadowGraphDirty()) return;

		preSubmitFrustumHolder = createShadowFrustum(renderDistanceMultiplier, preSubmitFrustumHolder, preSubmitFrustumCaches);
		if (!(preSubmitFrustumHolder.getFrustum() instanceof ViewportProvider provider)) return;
		final Vector3d entityPos = Camera.INSTANCE.getEntityPos();
		preSubmitFrustumHolder.getFrustum().setPosition(entityPos.x, entityPos.y, entityPos.z);

		if (rsm.preSubmitShadowGraphUpdate(provider.sodium$createViewport(), frame, spectator)) {
			preSubmittedShadowAngle = lastGraphShadowAngle;
			preSubmitActive = true;
		}
	}
	private long lastRelayNanos;
	private final Vector3f shadowLightVectorCache = new Vector3f();
	private BoxCuller cachedTileEntityCuller;
	private double lastTileEntityCullerDistance = -1;
	private final boolean shouldRenderDH;
	private final float nearPlane, farPlane;
	private final BooleanSupplier packUsesShadowtex1;

	public ShadowRenderer(ProgramSource shadow, PackDirectives directives, ShadowRenderTargets shadowRenderTargets, ShadowCompositeRenderer compositeRenderer, BooleanSupplier packUsesShadowtex1) {

		this.profiler = Minecraft.getMinecraft().mcProfiler;
		SHADOW_TERRAIN_RELAID = true;
		activeShadowAngle = Float.NaN;

		final PackShadowDirectives shadowDirectives = directives.getShadowDirectives();
		this.nearPlane = shadowDirectives.getNearPlane();
		this.farPlane = shadowDirectives.getFarPlane();

		this.halfPlaneLength = shadowDirectives.getDistance();
		this.voxelDistance = shadowDirectives.getVoxelDistance();
		this.renderDistanceMultiplier = shadowDirectives.getDistanceRenderMul();
		this.entityShadowDistanceMultiplier = shadowDirectives.getEntityShadowDistanceMul();
		this.resolution = shadowDirectives.getResolution();
		this.intervalSize = shadowDirectives.getIntervalSize();
		this.shouldRenderTerrain = shadowDirectives.shouldRenderTerrain();
		this.shouldRenderTranslucent = shadowDirectives.shouldRenderTranslucent();
		this.shouldRenderEntities = shadowDirectives.shouldRenderEntities();
		this.shouldRenderPlayer = shadowDirectives.shouldRenderPlayer();
		this.shouldRenderBlockEntities = shadowDirectives.shouldRenderBlockEntities();
		this.shouldRenderDH = shadowDirectives.isDhShadowEnabled().orElse(false);
		this.packUsesShadowtex1 = packUsesShadowtex1;

		this.compositeRenderer = compositeRenderer;

		debugStringOverall = "half plane = " + halfPlaneLength + " meters @ " + resolution + "x" + resolution;

		this.terrainFrustumHolder = new FrustumHolder();
		this.entityFrustumHolder = new FrustumHolder();

		this.fov = shadowDirectives.getFov();
		this.targets = shadowRenderTargets;

		if (shadow != null) {
			// Assume that the shader pack is doing voxelization if a geometry shader is detected.
			// Also assume voxelization if image load / store is detected.
			this.packHasVoxelization = shadow.getGeometrySource().isPresent();
			this.packCullingState = shadowDirectives.getCullingState();
		} else {
			this.packHasVoxelization = false;
			this.packCullingState = ShadowCullState.DEFAULT;
		}

		this.sunPathRotation = directives.getSunPathRotation();
		this.celestialUniforms = new CelestialUniforms(this.sunPathRotation);

//		this.buffers = new RenderBuffers();
//
//		if (this.buffers instanceof RenderBuffersExt) {
//			this.renderBuffersExt = (RenderBuffersExt) buffers;
//		} else {
//			this.renderBuffersExt = null;
//		}

		configureSamplingSettings(shadowDirectives);
	}

	public void setUsesImages(boolean usesImages) {
		this.packHasVoxelization = packHasVoxelization || usesImages;
	}

	public void setPlayerReflectionCaptureEnabled(boolean enabled) {
		this.playerReflectionCaptureEnabled = enabled;
	}

	public static MatrixStack createShadowModelView(float sunPathRotation, float intervalSize) {
		// Use entity position for shadow matrix
		final Vector3d entityPos = Camera.INSTANCE.getEntityPos();

		final float angle = Float.isNaN(activeShadowAngle) ? getShadowAngle() : activeShadowAngle;

		// Set up our modelview matrix stack
		final MatrixStack modelView = new MatrixStack();
		ShadowMatrices.createModelViewMatrix(modelView, angle, intervalSize, sunPathRotation, entityPos.x, entityPos.y, entityPos.z);

		return modelView;
	}

	private MatrixStack getShadowModelView(float shadowAngle) {
		final Vector3d entityPos = Camera.INSTANCE.getEntityPos();

		shadowModelView.reset();
		ShadowMatrices.createModelViewMatrix(shadowModelView, shadowAngle, this.intervalSize, this.sunPathRotation, entityPos.x, entityPos.y, entityPos.z);
		return shadowModelView;
	}

	private static WorldClient getLevel() {
		return Objects.requireNonNull(Minecraft.getMinecraft().theWorld);
	}

	private static float getSkyAngle() {
        return Minecraft.getMinecraft().theWorld.getCelestialAngle(CapturedRenderingState.INSTANCE.getTickDelta());
	}

	private static float getSunAngle() {
		final float skyAngle = getSkyAngle();

		if (skyAngle < 0.75F) {
			return skyAngle + 0.25F;
		} else {
			return skyAngle - 0.75F;
		}
	}

	private static float getShadowAngle() {
		float shadowAngle = getSunAngle();

		if (!CelestialUniforms.isDay()) {
			shadowAngle -= 0.5F;
		}

		return shadowAngle;
	}

	private void configureSamplingSettings(PackShadowDirectives shadowDirectives) {
		final ImmutableList<PackShadowDirectives.DepthSamplingSettings> depthSamplingSettings =
			shadowDirectives.getDepthSamplingSettings();

		final ImmutableList<PackShadowDirectives.SamplingSettings> colorSamplingSettings =
			shadowDirectives.getColorSamplingSettings();

		GLStateManager.glActiveTexture(GL13.GL_TEXTURE4);

		configureDepthSampler(targets.getDepthTexture().getTextureId(), depthSamplingSettings.get(0));

		configureDepthSampler(targets.getDepthTextureNoTranslucents().getTextureId(), depthSamplingSettings.get(1));

		for (int i = 0; i < Math.min(colorSamplingSettings.size(), targets.getNumColorTextures()); i++) {
			if (targets.get(i) == null) continue;
			int glTextureId = targets.get(i).getMainTexture();

			configureSampler(glTextureId, colorSamplingSettings.get(i));
		}

		GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
	}

    private final IntBuffer swizzleBuf = BufferUtils.createIntBuffer(4);
	private void configureDepthSampler(int glTextureId, PackShadowDirectives.DepthSamplingSettings settings) {
		if (settings.getHardwareFiltering()) {
			// We have to do this or else shadow hardware filtering breaks entirely!
			RenderSystem.texParameteri(glTextureId, GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL30.GL_COMPARE_REF_TO_TEXTURE);
		}

		// Workaround for issues with old shader packs like Chocapic v4.
		// They expected the driver to put the depth value in z, but it's supposed to only
		// be available in r. So we set up the swizzle to fix that.
        swizzleBuf.rewind();
        swizzleBuf.put(new int[] { GL11.GL_RED, GL11.GL_RED, GL11.GL_RED, GL11.GL_ONE }).rewind();
		RenderSystem.texParameteriv(glTextureId, GL11.GL_TEXTURE_2D, ARBTextureSwizzle.GL_TEXTURE_SWIZZLE_RGBA, swizzleBuf);

		configureSampler(glTextureId, settings);
	}

	private void configureSampler(int glTextureId, PackShadowDirectives.SamplingSettings settings) {
		if (settings.getMipmap()) {
			final int filteringMode = settings.getNearest() ? GL11.GL_NEAREST_MIPMAP_NEAREST : GL11.GL_LINEAR_MIPMAP_LINEAR;
			mipmapPasses.add(new MipmapPass(glTextureId, filteringMode));
		}

		if (!settings.getNearest()) {
			// Make sure that things are smoothed
			RenderSystem.texParameteri(glTextureId, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
			RenderSystem.texParameteri(glTextureId, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		} else {
			RenderSystem.texParameteri(glTextureId, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
			RenderSystem.texParameteri(glTextureId, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
		}
	}

	private void generateMipmaps() {
		GLStateManager.glActiveTexture(GL13.GL_TEXTURE4);

		for (MipmapPass mipmapPass : mipmapPasses) {
			setupMipmappingForTexture(mipmapPass.getTexture(), mipmapPass.getTargetFilteringMode());
		}

		GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
	}

	private void setupMipmappingForTexture(int texture, int filteringMode) {
		if (Tracy.ENABLED) {
			Tracy.beginZone(Z_GEN_MIPMAP);
			Tracy.zoneValue(texture);
		}
		try {
			RenderSystem.generateMipmaps(texture, GL11.GL_TEXTURE_2D);
		} finally {
			if (Tracy.ENABLED) Tracy.endZone();
		}
		RenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filteringMode);
	}

	private FrustumHolder createShadowFrustum(float renderMultiplier, FrustumHolder holder, FrustumCaches caches) {
		// TODO: Cull entities / block entities with Advanced Frustum Culling even if voxelization is detected.
		String distanceInfo;
		String cullingInfo;
		if ((packCullingState == ShadowCullState.DISTANCE || packHasVoxelization) && packCullingState != ShadowCullState.ADVANCED && packCullingState != ShadowCullState.SAFE_ZONE) {
			double distance = halfPlaneLength * renderMultiplier;

			String reason;

			if (packCullingState == ShadowCullState.DISTANCE) {
				reason = "(set by shader pack)";
			} else /*if (packHasVoxelization)*/ {
				reason = "(voxelization detected)";
			}

			if (distance <= 0 || distance > Minecraft.getMinecraft().gameSettings.renderDistanceChunks * 16) {
				distanceInfo = Minecraft.getMinecraft().gameSettings.renderDistanceChunks * 16
					+ " blocks (capped by normal render distance)";
				cullingInfo = "disabled " + reason;
				return holder.setInfo(NON_CULLING_FRUSTUM, distanceInfo, cullingInfo);
			} else {
				distanceInfo = distance + " blocks (set by shader pack)";
				cullingInfo = "distance only " + reason;
				holder.setInfo(getOrCreateBoxCullingFrustum(distance, caches), distanceInfo, cullingInfo);
			}
		} else {
			BoxCuller boxCuller;

			boolean hasSafeZone = packCullingState == ShadowCullState.SAFE_ZONE;

			if (hasSafeZone && renderMultiplier < 0) renderMultiplier = 1.0f;

			double distance = (hasSafeZone ? voxelDistance : halfPlaneLength) * renderMultiplier;
			String setter = "(set by shader pack)";

			if (renderMultiplier < 0) {
                // TODO: GUI
				distance = IrisVideoSettings.shadowDistance * 16; // can be zero :(
				setter = "(set by user)";
			}

			if (distance >= Minecraft.getMinecraft().gameSettings.renderDistanceChunks * 16 && !hasSafeZone) {
				distanceInfo = Minecraft.getMinecraft().gameSettings.renderDistanceChunks * 16
					+ " blocks (capped by normal render distance)";
				boxCuller = null;
			} else {
				distanceInfo = distance + " blocks " + setter;

				if (distance == 0.0 && !hasSafeZone) {
					cullingInfo = "no shadows rendered";
					return holder.setInfo(CULL_EVERYTHING_FRUSTUM, distanceInfo, cullingInfo);
				}

				boxCuller = getOrCreateAdvancedBoxCuller(distance, caches);
			}

			cullingInfo = (hasSafeZone ? "Safe Zone" : "Advanced") + " Frustum Culling enabled";

			final Vector4f shadowLightPosition = celestialUniforms.getShadowLightPositionInWorldSpace();
			shadowLightVectorCache.set(shadowLightPosition.x(), shadowLightPosition.y(), shadowLightPosition.z());
			shadowLightVectorCache.normalize();

			Matrix4fc projView = ((shouldRenderDH && DHCompat.hasRenderingEnabled()) ? DHCompat.getProjection() : RenderingState.INSTANCE.getProjectionMatrix());

			if (hasSafeZone) {
				BoxCuller distanceCuller = new BoxCuller(halfPlaneLength * renderMultiplier);
				SafeZoneCullingFrustum safeZoneFrustum = new SafeZoneCullingFrustum(
					RenderingState.INSTANCE.getModelViewMatrix(), projView,
					shadowLightVectorCache, boxCuller, distanceCuller);
				return holder.setInfo(safeZoneFrustum, distanceInfo, cullingInfo);
			} else {
				caches.advancedFrustum.init(RenderingState.INSTANCE.getModelViewMatrix(), projView, shadowLightVectorCache, boxCuller);
				return holder.setInfo(caches.advancedFrustum, distanceInfo, cullingInfo);
			}
		}

		return holder;
	}

	private static BoxCullingFrustum getOrCreateBoxCullingFrustum(double distance, FrustumCaches caches) {
		if (caches.boxCuller == null) {
			caches.boxCuller = new BoxCuller(distance);
			caches.boxCullingFrustum = new BoxCullingFrustum(caches.boxCuller);
			caches.lastBoxCullerDistance = distance;
		} else if (caches.lastBoxCullerDistance != distance) {
			caches.boxCuller.setMaxDistance(distance);
			caches.lastBoxCullerDistance = distance;
		}
		return caches.boxCullingFrustum;
	}

	private static BoxCuller getOrCreateAdvancedBoxCuller(double distance, FrustumCaches caches) {
		if (caches.advancedBoxCuller == null) {
			caches.advancedBoxCuller = new BoxCuller(distance);
			caches.lastAdvancedBoxCullerDistance = distance;
		} else if (caches.lastAdvancedBoxCullerDistance != distance) {
			caches.advancedBoxCuller.setMaxDistance(distance);
			caches.lastAdvancedBoxCullerDistance = distance;
		}
		return caches.advancedBoxCuller;
	}

	private BoxCuller getOrCreateTileEntityCuller(double distance) {
		if (cachedTileEntityCuller == null) {
			cachedTileEntityCuller = new BoxCuller(distance);
			lastTileEntityCullerDistance = distance;
		} else if (lastTileEntityCullerDistance != distance) {
			cachedTileEntityCuller.setMaxDistance(distance);
			lastTileEntityCullerDistance = distance;
		}
		return cachedTileEntityCuller;
	}

	private void setupGlState(Matrix4f projMatrix) {
		GLStateManager.glViewport(0, 0, resolution, resolution);

		// Set up our projection matrix and load it into the legacy matrix stack
		RenderSystem.setupProjectionMatrix(projMatrix);

		// Disable backface culling
		// This partially works around an issue where if the front face of a mountain isn't visible, it casts no
		// shadow.
		//
		// However, it only partially resolves issues of light leaking into caves.
		//
		// TODO: Better way of preventing light from leaking into places where it shouldn't
		GLStateManager.disableCull();
	}

	private void restoreGlState() {
		// Restore backface culling
        GLStateManager.enableCull();

		// Make sure to unload the projection matrix
		RenderSystem.restoreProjectionMatrix();

		GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
		GLStateManager.glClearDepth(1.0);

		// Restore main framebuffer and viewport
		Minecraft mc = Minecraft.getMinecraft();
		mc.getFramebuffer().bindFramebuffer(false);
		GLStateManager.glViewport(0, 0, mc.displayWidth, mc.displayHeight);
	}

	private void copyPreTranslucentDepth() {
		if (!packUsesShadowtex1.getAsBoolean()) {
			return;
		}

		profiler.endStartSection("iris_shadow_translucent_depth_copy");

		targets.copyPreTranslucentDepth();
	}

	private void renderEntities(EntityRenderer levelRenderer, Frustrum frustum, Object bufferSource, MatrixStack modelView, double cameraX, double cameraY, double cameraZ, float tickDelta) {
		profiler.startSection("iris_shadow_cull");

		renderedEntitiesList.clear();

		final boolean playerIsSpectator = GameModeUtil.isSpectator();
		final EntityPlayer player = Minecraft.getMinecraft().thePlayer;

		for (Entity entity : getLevel().loadedEntityList) {
			if (playerIsSpectator && entity == player) continue;

			if (!entity.ignoreFrustumCheck && !frustum.isBoundingBoxInFrustum(entity.boundingBox)) continue;

			renderedEntitiesList.add(entity);
		}

		profiler.endStartSection("iris_shadow_sort");

		renderedEntitiesList.unstableSort(ENTITY_CLASS_COMPARATOR);

		profiler.endStartSection("iris_shadow_build_geometry");

		setupEntityShadowState(modelView, cameraX, cameraY, cameraZ);
		if (playerReflectionCaptureEnabled) {
			PlayerReflectionCapture.begin(player);
		}
		ModelPartBatcher.INSTANCE.begin(ModelPartBatcher.Mode.ENTITIES, true);
		try {
			for (Entity entity : renderedEntitiesList) {
				final long start = Tracy.ENABLED ? System.nanoTime() : 0L;
				try {
					RenderManager.instance.renderEntitySimple(entity, tickDelta);
				} finally {
					if (Tracy.ENABLED) RenderClassTimings.SHADOW_ENTITY.add(entity.getClass(), System.nanoTime() - start);
				}
			}
		} finally {
			flushShadowModelParts();
			PlayerReflectionCapture.end();
			teardownEntityShadowState();
		}

		renderedShadowEntities = renderedEntitiesList.size();
		if (Tracy.ENABLED) Tracy.plotInt(P_SHADOW_ENTITIES_RENDERED, renderedShadowEntities);

		profiler.endSection();
	}

	private boolean entityFrustumConstrained;

	private Frustrum createEntityShadowFrustum(double entityX, double entityY, double entityZ) {
        // Shader packs can shrink the entity shadow distance so faraway entities skip the shadow pass
        entityFrustumConstrained = false;
		if (Tracy.ENABLED) Tracy.beginZone(Z_SHADOW_FRUSTUM);
		try {
			if (entityShadowDistanceMultiplier == 1.0F || entityShadowDistanceMultiplier < 0.0F) {
				entityFrustumHolder.setInfo(terrainFrustumHolder.getFrustum(), terrainFrustumHolder.getDistanceInfo(), terrainFrustumHolder.getCullingInfo());
				return entityFrustumHolder.getFrustum();
			}

			entityFrustumConstrained = true;
			entityFrustumHolder = createShadowFrustum(renderDistanceMultiplier * entityShadowDistanceMultiplier, entityFrustumHolder, entityFrustumCaches);
			final Frustrum frustum = entityFrustumHolder.getFrustum();
			frustum.setPosition(entityX, entityY, entityZ);
			return frustum;
		} finally {
			if (Tracy.ENABLED) Tracy.endZone();
		}
	}

	private void applyEntityShadowViewport(Frustrum entityShadowFrustum) {
		if (Tracy.ENABLED) Tracy.beginZone(Z_SHADOW_VIEWPORT);
		try {
			CeleritasWorldRenderer.getInstance().setCurrentViewport(((ViewportProvider) entityShadowFrustum).sodium$createViewport());
		} finally {
			if (Tracy.ENABLED) Tracy.endZone();
		}
	}

	private void renderShadowEntitiesAndPlayer(EntityRenderer levelRenderer, Frustrum entityShadowFrustum, MatrixStack modelView, double entityX, double entityY, double entityZ, float tickDelta) {
		if (shouldRenderEntities) {
			renderEntities(levelRenderer, entityShadowFrustum, null, modelView, entityX, entityY, entityZ, tickDelta);
		} else if (shouldRenderPlayer) {
			renderPlayerEntity(levelRenderer, entityShadowFrustum, null, modelView, entityX, entityY, entityZ, tickDelta);
		}
	}

	private static boolean voxelizationActive() {
		return Iris.getPipelineManager().getPipelineNullable() instanceof DeferredWorldRenderingPipeline deferred && deferred.getShadowVoxelizationCompute() != null;
	}

	private void renderSolidShadowTerrain(Minecraft mc, RenderGlobal rg, Camera playerCamera) {
		// Render all opaque terrain unless pack requests not to
		if (shouldRenderTerrain) {
			mc.renderEngine.bindTexture(TextureMap.locationBlocksTexture);
			rg.sortAndRender(mc.renderViewEntity, 0, playerCamera.getPartialTicks());
		}

		// Reset viewport in case terrain rendering changed it
		GLStateManager.glViewport(0, 0, resolution, resolution);
	}

	// Saved RenderManager position for shadow pass
    private double savedRenderPosX, savedRenderPosY, savedRenderPosZ;

    /** Flushes shadow-pass batched model parts while GL_POLYGON_OFFSET_FILL is still enabled. */
    private static void flushShadowModelParts() {
        if (Tracy.ENABLED) Tracy.beginZone(Z_SHADOW_MODEL_PARTS);
        final boolean alphaEnabled = GLStateManager.getAlphaTest().isEnabled();
        final int alphaFunc = GLStateManager.getAlphaState().getFunction();
        final float alphaRef = GLStateManager.getAlphaState().getReference();
        try {
            ModelPartBatcher.INSTANCE.flush();
        } finally {
            if (alphaEnabled) GLStateManager.enableAlphaTest();
            else GLStateManager.disableAlphaTest();
            GLStateManager.glAlphaFunc(alphaFunc, alphaRef);
            if (Tracy.ENABLED) Tracy.endZone();
        }
    }

    private void setupEntityShadowState(MatrixStack modelView, double cameraX, double cameraY, double cameraZ) {
        savedRenderPosX = RenderManager.renderPosX;
        savedRenderPosY = RenderManager.renderPosY;
        savedRenderPosZ = RenderManager.renderPosZ;

        RenderManager.renderPosX = cameraX;
        RenderManager.renderPosY = cameraY;
        RenderManager.renderPosZ = cameraZ;

        GLStateManager.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GLStateManager.glPolygonOffset(1.0f, 1.0f);

        GLStateManager.glPushMatrix();
        MODELVIEW_BUFFER.clear().rewind();
        modelView.peek().getModel().get(MODELVIEW_BUFFER);
        GLStateManager.glLoadMatrix(MODELVIEW_BUFFER);
    }

    private void teardownEntityShadowState() {
        GLStateManager.glPopMatrix();

        GLStateManager.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GLStateManager.glPolygonOffset(0.0f, 0.0f);

        RenderManager.renderPosX = savedRenderPosX;
        RenderManager.renderPosY = savedRenderPosY;
        RenderManager.renderPosZ = savedRenderPosZ;
    }

	private void renderPlayerEntity(EntityRenderer levelRenderer, Frustrum frustum, Object bufferSource, MatrixStack modelView, double cameraX, double cameraY, double cameraZ, float tickDelta) {
		profiler.startSection("iris_shadow_cull");

		EntityPlayer player = Minecraft.getMinecraft().thePlayer;

		// Skip if spectating or outside frustum
		if (GameModeUtil.isSpectator()) {
			profiler.endSection();
			renderedShadowEntities = 0;
			return;
		}

		if (!player.ignoreFrustumCheck && !frustum.isBoundingBoxInFrustum(player.boundingBox)) {
			profiler.endSection();
			renderedShadowEntities = 0;
			return;
		}

		profiler.endStartSection("iris_shadow_build_geometry");

		int shadowEntities = 0;

		setupEntityShadowState(modelView, cameraX, cameraY, cameraZ);
		ModelPartBatcher.INSTANCE.begin(ModelPartBatcher.Mode.ENTITIES, true);
		try {
			if (player.riddenByEntity != null) {
				RenderManager.instance.renderEntitySimple(player.riddenByEntity, tickDelta);
				shadowEntities++;
			}

			if (player.ridingEntity != null) {
				RenderManager.instance.renderEntitySimple(player.ridingEntity, tickDelta);
				shadowEntities++;
			}

			if (playerReflectionCaptureEnabled) {
				PlayerReflectionCapture.begin(player);
			}
			try {
				RenderManager.instance.renderEntitySimple(player, tickDelta);
			} finally {
				PlayerReflectionCapture.end();
			}
			shadowEntities++;
		} finally {
			flushShadowModelParts();
			teardownEntityShadowState();
		}

		renderedShadowEntities = shadowEntities;

		profiler.endSection();
	}

    private void renderTileEntity(TileEntity tile, double cameraX, double cameraY, double cameraZ, float partialTicks) {
        final double distSq = tile.getDistanceFrom(cameraX, cameraY, cameraZ);
        if (distSq >= tile.getMaxRenderDistanceSquared()) {
            return;
        }
        if (AngelicaConfig.cullShadowTileEntities) {
            final int maxD = AngelicaConfig.shadowTileEntityMaxDistance;
            if (distSq >= (double) maxD * maxD) {
                return;
            }
            if (AngelicaConfig.shadowSkipInMeshTileEntities) {
                final Block block = tile.getBlockType();
                if (block != null && block.getRenderType() != -1) {
                    return;
                }
            }
        }
        int brightness = tile.getWorldObj().getLightBrightnessForSkyBlocks(tile.xCoord, tile.yCoord, tile.zCoord, 0);
        GLStateManager.setLightmapTextureCoords(GL13.GL_TEXTURE1, (float) brightness % 65536, (float) brightness / 65536);
        GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        TileEntityRendererDispatcher.instance.renderTileEntityAt(tile,
            (double)tile.xCoord - cameraX,
            (double)tile.yCoord - cameraY,
            (double)tile.zCoord - cameraZ,
            partialTicks
        );
    }

	private void renderTileEntities(Object bufferSource, MatrixStack modelView, double cameraX, double cameraY, double cameraZ, float partialTicks, boolean hasEntityFrustum) {
		profiler.startSection("iris_shadow_build_blockentities");

		int shadowTileEntities = 0;
		BoxCuller culler = null;
		if (hasEntityFrustum) {
			double distance = halfPlaneLength * (renderDistanceMultiplier * entityShadowDistanceMultiplier);
			culler = getOrCreateTileEntityCuller(distance);
			culler.setPosition(cameraX, cameraY, cameraZ);
		}

        GLStateManager.glPushMatrix();
        MODELVIEW_BUFFER.clear().rewind();
        modelView.peek().getModel().get(MODELVIEW_BUFFER);
        GLStateManager.glLoadMatrix(MODELVIEW_BUFFER);

        TesrBatchRenderer.INSTANCE.beginPass(TesrBatchRenderer.PASS_SHADOW, modelView.peek().getModel(), cameraX, cameraY, cameraZ);

        GbufferPrograms.beginBlockEntities();
        GbufferPrograms.setBlockEntityDefaults();
        ModelPartBatcher.INSTANCE.begin(ModelPartBatcher.Mode.BLOCK_ENTITIES, true);

		for (int b = 0, bn = visibleTileEntities.size(); b < bn; b++) {
			final List<TileEntity> bucket = visibleTileEntities.get(b);
			for (int i = 0, n = bucket.size(); i < n; i++) {
				final TileEntity tileEntity = bucket.get(i);
				if (hasEntityFrustum && (culler.isCulled(tileEntity.xCoord - 1, tileEntity.yCoord - 1, tileEntity.zCoord - 1, tileEntity.xCoord + 1, tileEntity.yCoord + 1, tileEntity.zCoord + 1))) {
					continue;
				}
				renderTileEntity(tileEntity, cameraX, cameraY, cameraZ, partialTicks);

				shadowTileEntities++;
			}
		}
		for (int b = 0, bn = globalTileEntities.size(); b < bn; b++) {
			final List<TileEntity> bucket = globalTileEntities.get(b);
			for (int i = 0, n = bucket.size(); i < n; i++) {
				final TileEntity tileEntity = bucket.get(i);
				if (hasEntityFrustum && (culler.isCulled(tileEntity.xCoord - 1, tileEntity.yCoord - 1, tileEntity.zCoord - 1, tileEntity.xCoord + 1, tileEntity.yCoord + 1, tileEntity.zCoord + 1))) {
					continue;
				}
				renderTileEntity(tileEntity, cameraX, cameraY, cameraZ, partialTicks);

				shadowTileEntities++;
			}
		}

        flushShadowModelParts();
        TesrBatchRenderer.INSTANCE.flush();

        GbufferPrograms.endBlockEntities();
        GLStateManager.glPopMatrix();

		renderedShadowTileEntities = shadowTileEntities;

		profiler.endSection();
	}

	public void renderShadows(EntityRenderer levelRenderer, Camera playerCamera) {
        final Minecraft mc = Minecraft.getMinecraft();
        final RenderGlobal rg = mc.renderGlobal;

        // We have to re-query this each frame since this changes based on whether the profiler is active
		// If the profiler is inactive, it will return InactiveProfiler.INSTANCE
		this.profiler = Minecraft.getMinecraft().mcProfiler;

		profiler.endStartSection("iris_shadows");
		ACTIVE = true;
		CURRENT_TARGETS = this.targets;

		// NB: We store the previous player buffers in order to be able to allow mods rendering entities in the shadow pass (Flywheel) to use the shadow buffers instead.
        // TODO: Render
//		RenderBuffers playerBuffers = levelRenderer.getRenderBuffers();
//		levelRenderer.setRenderBuffers(buffers);

		visibleTileEntities.clear();
		globalTileEntities.clear();

		final float currentShadowAngle = getShadowAngle();
		if (ShadowGraphGate.shouldMarkDirty(lastGraphShadowAngle, currentShadowAngle)) {
			CeleritasWorldRenderer.getInstance().getRenderSectionManager().markShadowGraphDirty();
			lastGraphShadowAngle = currentShadowAngle;
		}

		final boolean deferActive = shouldRenderTerrain;
		final long frameNanos = System.nanoTime();
		final boolean consumePreSubmit = preSubmitActive;
		preSubmitActive = false;

		final boolean relayTerrain = !deferActive
			|| voxelizationActive()
			|| !targets.isTerrainSnapshotValid()
			|| consumePreSubmit
			|| CeleritasWorldRenderer.getInstance().getRenderSectionManager().isShadowGraphDirty()
			|| frameNanos - lastRelayNanos >= SHADOW_RELAY_MAX_AGE_NANOS;
		if (relayTerrain) {

			relayShadowAngle = consumePreSubmit && !CeleritasWorldRenderer.getInstance().getRenderSectionManager().isShadowGraphDirty() ? preSubmittedShadowAngle : currentShadowAngle;
		}
		activeShadowAngle = relayShadowAngle;
		SHADOW_TERRAIN_RELAID = relayTerrain;

		// Create our camera
		final MatrixStack modelView = getShadowModelView(relayShadowAngle);
		MODELVIEW.set(modelView.peek().getModel());

		final Matrix4f shadowProjection;
		if (this.fov != null) {
			// If FOV is not null, the pack wants a perspective based projection matrix. (This is to support legacy packs)
			shadowProjection = ShadowMatrices.createPerspectiveMatrix(this.fov);
		} else {
			shadowProjection = ShadowMatrices.createOrthoMatrix(halfPlaneLength, nearPlane < 0 ? -DHCompat.getRenderDistance() : nearPlane, farPlane < 0 ? DHCompat.getRenderDistance() : farPlane);
		}

		PROJECTION.set(shadowProjection);

		profiler.startSection("iris_shadow_terrain_setup");

		if (levelRenderer instanceof CullingDataCache) {
			((CullingDataCache) levelRenderer).saveState();
		}

		profiler.startSection("iris_shadow_initialize_frustum");

		terrainFrustumHolder = createShadowFrustum(renderDistanceMultiplier, terrainFrustumHolder, terrainFrustumCaches);
		FRUSTUM = terrainFrustumHolder.getFrustum();

		// Use the player/entity position for shadow rendering
		final Vector3d entityPos = playerCamera.getEntityPos();
		final double entityX = entityPos.x;
		final double entityY = entityPos.y;
		final double entityZ = entityPos.z;

		// Center the frustum on the player position
		terrainFrustumHolder.getFrustum().setPosition(entityX, entityY, entityZ);

		profiler.endSection();

		// Save the main camera viewport before shadow pass overwrites it.
		// clipRenderersByFrustum -> setupTerrain sets currentViewport to the shadow frustum.
		// If the shadow pass throws or the main setupTerrain doesn't run after us, the shadow
		// viewport would persist and corrupt entity culling in the main pass.
		final Viewport savedViewport = CeleritasWorldRenderer.getInstance().getCurrentViewport();

		// Pair setupGlState / restoreGlState with try-finally so any throw between them
		// (translucent terrain, entity rendering, mipmaps, etc.) still unwinds the
		// projection push and the shadow framebuffer binding. Without this, a single
		// mid-pass throw strands shadow GL state into the next frame's vanilla rendering.
		boolean setupGlStateRan = false;
		try {
		// Execute the vanilla terrain setup / culling routines using our shadow frustum.
        mc.renderGlobal.clipRenderersByFrustum(terrainFrustumHolder.getFrustum(), playerCamera.getPartialTicks());

		// Don't forget to increment the frame counter! This variable is arbitrary and only used in terrain setup,
		// and if it's not incremented, the vanilla culling code will get confused and think that it's already seen
		// chunks during traversal, and break rendering in concerning ways.
//		levelRenderer.setFrameId(levelRenderer.getFrameId() + 1);

		setupGlState(PROJECTION);
		setupGlStateRan = true;

		// Get the current tick delta. Normally this is the same as client.getTickDelta(), but when the game is paused,
		// it is set to a fixed value.
		final float tickDelta = CapturedRenderingState.INSTANCE.getTickDelta();

		profiler.endStartSection("iris_shadow_terrain");
		if (relayTerrain) {
			try (GLDebug.Scope s = GLDebug.scope("shadow:terrain")) {
				renderSolidShadowTerrain(mc, rg, playerCamera);
			}
			if (deferActive) {
				targets.captureTerrainSnapshot();
				lastRelayNanos = frameNanos;
			}
		} else {
			try (GLDebug.Scope s = GLDebug.scope("shadow:terrain_restore")) {
				targets.restoreTerrainSnapshot();
			}
		}

		profiler.endStartSection("iris_shadow_entities");
		final Frustrum entityShadowFrustum = createEntityShadowFrustum(entityX, entityY, entityZ);
		applyEntityShadowViewport(entityShadowFrustum);
		try (GLDebug.Scope s = GLDebug.scope("shadow:entities")) {
			renderShadowEntitiesAndPlayer(levelRenderer, entityShadowFrustum, modelView, entityX, entityY, entityZ, tickDelta);
		}

		if (shouldRenderBlockEntities) {
			try (GLDebug.Scope s = GLDebug.scope("shadow:block_entities")) {
				renderTileEntities(null, modelView, entityX, entityY, entityZ, tickDelta, entityFrustumConstrained);
			}
		}

		profiler.endStartSection("iris_shadow_draw_entities");

		// NB: Don't try to draw the translucent parts of entities afterwards. It'll cause problems since some
		// shader packs assume that everything drawn afterwards is actually translucent and should cast a colored
		// shadow...

		copyPreTranslucentDepth();

		profiler.endStartSection("iris_shadow_translucent_terrain");

		// TODO (Iris): Prevent these calls from scheduling translucent sorting...
		// It doesn't matter a ton, since this just means that they won't be sorted in the getNormal rendering pass.
		// Just something to watch out for, however...
		if (shouldRenderTranslucent) {
			try (GLDebug.Scope s = GLDebug.scope("shadow:translucent")) {
				rg.sortAndRender(mc.renderViewEntity, 1, playerCamera.getPartialTicks());
			}
		}

		// Note: Apparently tripwire isn't rendered in the shadow pass.
		// worldRenderer.invokeRenderType(RenderType.getTripwire(), modelView, cameraX, cameraY, cameraZ);

//		if (renderBuffersExt != null) {
//			renderBuffersExt.endLevelRendering();
//		}

		profiler.endStartSection("iris_shadow_generate_mipmaps");

		generateMipmaps();

		profiler.endStartSection("iris_shadow_restore_gl_state");
		} finally {
			if (setupGlStateRan) {
				restoreGlState();
			}
			if (savedViewport != null) {
				CeleritasWorldRenderer.getInstance().setCurrentViewport(savedViewport);
			}
		}

		if (levelRenderer instanceof CullingDataCache) {
			((CullingDataCache) levelRenderer).restoreState();
		}

		profiler.endStartSection("iris_shadowcomp");

		if (compositeRenderer != null) compositeRenderer.renderAll();

		ACTIVE = false;
		CURRENT_TARGETS = null;
		profiler.endSection();
		profiler.endStartSection("culling");
	}

	public void addDebugText(List<String> messages) {
		messages.add("[" + Iris.MODNAME + " - Shadow Pass]");
		messages.add("  Shadow Maps: " + debugStringOverall);
		messages.add("  Shadow Distance Terrain: " + terrainFrustumHolder.getDistanceInfo() + " Entity: " + entityFrustumHolder.getDistanceInfo());
		messages.add("  Shadow Culling Terrain: " + terrainFrustumHolder.getCullingInfo() + " Entity: " + entityFrustumHolder.getCullingInfo());
		messages.add("  Shadow Terrain: " + CeleritasWorldRenderer.getInstance().getChunksDebugString() + (shouldRenderTerrain ? "" : " (no terrain) ") + (shouldRenderTranslucent ? "" : "(no translucent)"));
		messages.add("  Shadow Entities: " + getEntitiesDebugString());
		messages.add("  Shadow Block Entities: " + getTileEntitiesDebugString());

//		if (buffers instanceof DrawCallTrackingRenderBuffers drawCallTracker && (shouldRenderEntities || shouldRenderPlayer)) {
//            messages.add("[" + Iris.MODNAME + "] Shadow Entity Batching: " + BatchingDebugMessageHelper.getDebugMessage(drawCallTracker));
//		}
	}

	private String getEntitiesDebugString() {
		return (shouldRenderEntities || shouldRenderPlayer) ? (renderedShadowEntities + "/" + Minecraft.getMinecraft().theWorld.loadedEntityList.size()) : "disabled by pack";
	}

	private String getTileEntitiesDebugString() {
		return shouldRenderBlockEntities ? (renderedShadowTileEntities + "/" + Minecraft.getMinecraft().theWorld.loadedTileEntityList.size()) : "disabled by pack";
	}

	private static class MipmapPass {
		private final int texture;
		private final int targetFilteringMode;

		public MipmapPass(int texture, int targetFilteringMode) {
			this.texture = texture;
			this.targetFilteringMode = targetFilteringMode;
		}

		public int getTexture() {
			return texture;
		}

		public int getTargetFilteringMode() {
			return targetFilteringMode;
		}
	}
}
