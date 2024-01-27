package net.coderbot.iris.pipeline;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizons.angelica.compat.mojang.Camera;
import com.gtnewhorizons.angelica.compat.toremove.MatrixStack;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.rendering.RenderingState;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import net.coderbot.iris.Iris;
import net.coderbot.iris.shaderpack.OptionalBoolean;
import net.coderbot.iris.shaderpack.PackDirectives;
import net.coderbot.iris.shaderpack.PackShadowDirectives;
import net.coderbot.iris.shaderpack.ProgramSource;
import net.coderbot.iris.shadow.ShadowMatrices;
import net.coderbot.iris.shadows.CullingDataCache;
import net.coderbot.iris.shadows.ShadowRenderTargets;
import net.coderbot.iris.shadows.frustum.BoxCuller;
import net.coderbot.iris.shadows.frustum.CullEverythingFrustum;
import net.coderbot.iris.shadows.frustum.FrustumHolder;
import net.coderbot.iris.shadows.frustum.advanced.AdvancedShadowCullingFrustum;
import net.coderbot.iris.shadows.frustum.fallback.BoxCullingFrustum;
import net.coderbot.iris.shadows.frustum.fallback.NonCullingFrustum;
import net.coderbot.iris.uniforms.CameraUniforms;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.iris.uniforms.CelestialUniforms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.culling.Frustrum;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import org.joml.Matrix4f;
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

public class ShadowRenderer {
	public static final Matrix4f MODELVIEW = new Matrix4f();
    public static FloatBuffer MODELVIEW_BUFFER = BufferUtils.createFloatBuffer(16);
	public static final Matrix4f PROJECTION = new Matrix4f();
	public static List<TileEntity> visibleTileEntities;
	public static boolean ACTIVE = false;
	private final float halfPlaneLength;
	private final float renderDistanceMultiplier;
	private final float entityShadowDistanceMultiplier;
	private final int resolution;
	private final float intervalSize;
	private final Float fov;
	private final ShadowRenderTargets targets;
	private final OptionalBoolean packCullingState;
	private boolean packHasVoxelization;
	private final boolean shouldRenderTerrain;
	private final boolean shouldRenderTranslucent;
	private final boolean shouldRenderEntities;
	private final boolean shouldRenderPlayer;
	private final boolean shouldRenderBlockEntities;
	private final float sunPathRotation;
//	private final RenderBuffers buffers;
//	private final RenderBuffersExt renderBuffersExt;
	private final List<MipmapPass> mipmapPasses = new ArrayList<>();
	private final String debugStringOverall;
	private FrustumHolder terrainFrustumHolder;
	private FrustumHolder entityFrustumHolder;
	private String debugStringTerrain = "(unavailable)";
	private int renderedShadowEntities = 0;
	private int renderedShadowTileEntities = 0;
	private Profiler profiler;

	public ShadowRenderer(ProgramSource shadow, PackDirectives directives, ShadowRenderTargets shadowRenderTargets) {

		this.profiler = Minecraft.getMinecraft().mcProfiler;

		final PackShadowDirectives shadowDirectives = directives.getShadowDirectives();

		this.halfPlaneLength = shadowDirectives.getDistance();
		this.renderDistanceMultiplier = shadowDirectives.getDistanceRenderMul();
		this.entityShadowDistanceMultiplier = shadowDirectives.getEntityShadowDistanceMul();
		this.resolution = shadowDirectives.getResolution();
		this.intervalSize = shadowDirectives.getIntervalSize();
		this.shouldRenderTerrain = shadowDirectives.shouldRenderTerrain();
		this.shouldRenderTranslucent = shadowDirectives.shouldRenderTranslucent();
		this.shouldRenderEntities = shadowDirectives.shouldRenderEntities();
		this.shouldRenderPlayer = shadowDirectives.shouldRenderPlayer();
		this.shouldRenderBlockEntities = shadowDirectives.shouldRenderBlockEntities();

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
			this.packCullingState = OptionalBoolean.DEFAULT;
		}

		this.sunPathRotation = directives.getSunPathRotation();

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

	public static MatrixStack createShadowModelView(float sunPathRotation, float intervalSize) {
		// Determine the camera position
		final Vector3d cameraPos = CameraUniforms.getUnshiftedCameraPosition();

		final double cameraX = cameraPos.x;
		final double cameraY = cameraPos.y;
		final double cameraZ = cameraPos.z;

		// Set up our modelview matrix stack
		final MatrixStack modelView = new MatrixStack();
		ShadowMatrices.createModelViewMatrix(modelView, getShadowAngle(), intervalSize, sunPathRotation, cameraX, cameraY, cameraZ);

		return modelView;
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

		for (int i = 0; i < colorSamplingSettings.size(); i++) {
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
		RenderSystem.generateMipmaps(texture, GL11.GL_TEXTURE_2D);
		RenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filteringMode);
	}

	private FrustumHolder createShadowFrustum(float renderMultiplier, FrustumHolder holder) {
		// TODO: Cull entities / block entities with Advanced Frustum Culling even if voxelization is detected.
		String distanceInfo;
		String cullingInfo;
		if ((packCullingState == OptionalBoolean.FALSE || packHasVoxelization) && packCullingState != OptionalBoolean.TRUE) {
			double distance = halfPlaneLength * renderMultiplier;

			String reason;

			if (packCullingState == OptionalBoolean.FALSE) {
				reason = "(set by shader pack)";
			} else /*if (packHasVoxelization)*/ {
				reason = "(voxelization detected)";
			}

			if (distance <= 0 || distance > Minecraft.getMinecraft().gameSettings.renderDistanceChunks * 16) {
				distanceInfo = Minecraft.getMinecraft().gameSettings.renderDistanceChunks * 16
					+ " blocks (capped by normal render distance)";
				cullingInfo = "disabled " + reason;
				return holder.setInfo(new NonCullingFrustum(), distanceInfo, cullingInfo);
			} else {
				distanceInfo = distance + " blocks (set by shader pack)";
				cullingInfo = "distance only " + reason;
				BoxCuller boxCuller = new BoxCuller(distance);
				holder.setInfo(new BoxCullingFrustum(boxCuller), distanceInfo, cullingInfo);
			}
		} else {
			BoxCuller boxCuller;

			double distance = halfPlaneLength * renderMultiplier;
			String setter = "(set by shader pack)";

			if (renderMultiplier < 0) {
                // TODO: GUI
//				distance = IrisVideoSettings.shadowDistance * 16;
				distance = 32 * 16;
				setter = "(set by user)";
			}

			if (distance >= Minecraft.getMinecraft().gameSettings.renderDistanceChunks * 16) {
				distanceInfo = Minecraft.getMinecraft().gameSettings.renderDistanceChunks * 16
					+ " blocks (capped by normal render distance)";
				boxCuller = null;
			} else {
				distanceInfo = distance + " blocks " + setter;

				if (distance == 0.0) {
					cullingInfo = "no shadows rendered";
					holder.setInfo(new CullEverythingFrustum(), distanceInfo, cullingInfo);
				}

				boxCuller = new BoxCuller(distance);
			}

			cullingInfo = "Advanced Frustum Culling enabled";

			Vector4f shadowLightPosition = new CelestialUniforms(sunPathRotation).getShadowLightPositionInWorldSpace();

			Vector3f shadowLightVectorFromOrigin =
				new Vector3f(shadowLightPosition.x(), shadowLightPosition.y(), shadowLightPosition.z());

			shadowLightVectorFromOrigin.normalize();

			return holder.setInfo(new AdvancedShadowCullingFrustum(RenderingState.INSTANCE.getModelViewMatrix(), RenderingState.INSTANCE.getProjectionMatrix(),
                shadowLightVectorFromOrigin, boxCuller), distanceInfo, cullingInfo);

		}

		return holder;
	}

	private void setupGlState(Matrix4f projMatrix) {
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
	}

	private void copyPreTranslucentDepth() {
		profiler.endStartSection("translucent depth copy");

		targets.copyPreTranslucentDepth();
	}

	private void renderEntities(EntityRenderer levelRenderer, Frustrum frustum, Object bufferSource, MatrixStack modelView, double cameraX, double cameraY, double cameraZ, float tickDelta) {
        // TODO: Render
//		EntityRenderDispatcher dispatcher = levelRenderer.getEntityRenderDispatcher();

		int shadowEntities = 0;

		profiler.startSection("cull");

		List<Entity> renderedEntities = new ArrayList<>(32);

		// TODO: I'm sure that this can be improved / optimized.
        // TODO: Render
        // TODO: Entity culling
		for (Entity entity : getLevel().loadedEntityList) {
			if (false/*!dispatcher.shouldRender(entity, frustum, cameraX, cameraY, cameraZ) || entity.isSpectator()*/) {
				continue;
			}

			renderedEntities.add(entity);
		}

		profiler.endStartSection("sort");

        // TODO: Render
		// Sort the entities by type first in order to allow vanilla's entity batching system to work better.
		renderedEntities.sort(Comparator.comparingInt(entity -> entity.getClass().hashCode()));

		profiler.endStartSection("build geometry");

        // TODO: Render
        GL11.glPushMatrix();
        MODELVIEW_BUFFER.clear().rewind();
        modelView.peek().getModel().get(MODELVIEW_BUFFER);
        GL11.glLoadMatrix(MODELVIEW_BUFFER);
		for (Entity entity : renderedEntities) {
			RenderManager.instance.renderEntitySimple(entity, tickDelta);//(entity, cameraX, cameraY, cameraZ, tickDelta, modelView, bufferSource);
			shadowEntities++;
		}
        GL11.glPopMatrix();

		renderedShadowEntities = shadowEntities;

		profiler.endSection();
	}

	private void renderPlayerEntity(EntityRenderer levelRenderer, Frustrum frustum, Object bufferSource, MatrixStack modelView, double cameraX, double cameraY, double cameraZ, float tickDelta) {
        // TODO: Render
//		EntityRenderDispatcher dispatcher = levelRenderer.getEntityRenderDispatcher();

		profiler.startSection("cull");

		Entity player = Minecraft.getMinecraft().thePlayer;

//		if (!dispatcher.shouldRender(player, frustum, cameraX, cameraY, cameraZ) || player.isSpectator()) {
//			return;
//		}

		profiler.endStartSection("build geometry");

		int shadowEntities = 0;

//		if (!player.getPassengers().isEmpty()) {
//			for (int i = 0; i < player.getPassengers().size(); i++) {
//				levelRenderer.invokeRenderEntity(player.getPassengers().get(i), cameraX, cameraY, cameraZ, tickDelta, modelView, bufferSource);
//				shadowEntities++;
//			}
//		}
//
//		if (player.getVehicle() != null) {
//			levelRenderer.invokeRenderEntity(player.getVehicle(), cameraX, cameraY, cameraZ, tickDelta, modelView, bufferSource);
//			shadowEntities++;
//		}
//
//		levelRenderer.invokeRenderEntity(player, cameraX, cameraY, cameraZ, tickDelta, modelView, bufferSource);

		shadowEntities++;

		renderedShadowEntities = shadowEntities;

		profiler.endSection();
	}

	private void renderTileEntities(Object bufferSource, MatrixStack modelView, double cameraX, double cameraY, double cameraZ, float partialTicks, boolean hasEntityFrustum) {
		profiler.startSection("build blockentities");

		int shadowTileEntities = 0;
		BoxCuller culler = null;
		if (hasEntityFrustum) {
			culler = new BoxCuller(halfPlaneLength * (renderDistanceMultiplier * entityShadowDistanceMultiplier));
			culler.setPosition(cameraX, cameraY, cameraZ);
		}

		for (TileEntity tileEntity : visibleTileEntities) {
			if (hasEntityFrustum && (culler.isCulled(tileEntity.xCoord - 1, tileEntity.yCoord - 1, tileEntity.zCoord - 1, tileEntity.xCoord + 1, tileEntity.yCoord + 1, tileEntity.zCoord + 1))) {
                continue;
			}
			modelView.push();
			modelView.translate(tileEntity.xCoord - cameraX, tileEntity.yCoord - cameraY, tileEntity.zCoord - cameraZ);
            TileEntityRendererDispatcher.instance.renderTileEntity(tileEntity, partialTicks);
			modelView.pop();

			shadowTileEntities++;
		}

		renderedShadowTileEntities = shadowTileEntities;

		profiler.endSection();
	}

	public void renderShadows(EntityRenderer levelRenderer, Camera playerCamera) {
        final Minecraft mc = Minecraft.getMinecraft();
        final RenderGlobal rg = mc.renderGlobal;

        // We have to re-query this each frame since this changes based on whether the profiler is active
		// If the profiler is inactive, it will return InactiveProfiler.INSTANCE
		this.profiler = Minecraft.getMinecraft().mcProfiler;

		Minecraft client = Minecraft.getMinecraft();

		profiler.endStartSection("shadows");
		ACTIVE = true;

		// NB: We store the previous player buffers in order to be able to allow mods rendering entities in the shadow pass (Flywheel) to use the shadow buffers instead.
        // TODO: Render
//		RenderBuffers playerBuffers = levelRenderer.getRenderBuffers();
//		levelRenderer.setRenderBuffers(buffers);

		visibleTileEntities = new ArrayList<>();

		// Create our camera
		final MatrixStack modelView = createShadowModelView(this.sunPathRotation, this.intervalSize);
		MODELVIEW.set(modelView.peek().getModel());

        final Matrix4f shadowProjection;
		if (this.fov != null) {
			// If FOV is not null, the pack wants a perspective based projection matrix. (This is to support legacy packs)
            shadowProjection = ShadowMatrices.createPerspectiveMatrix(this.fov);
		} else {
            shadowProjection = ShadowMatrices.createOrthoMatrix(halfPlaneLength);
		}

		PROJECTION.set(shadowProjection);

		profiler.startSection("terrain_setup");

		if (levelRenderer instanceof CullingDataCache) {
			((CullingDataCache) levelRenderer).saveState();
		}

		profiler.startSection("initialize frustum");

		terrainFrustumHolder = createShadowFrustum(renderDistanceMultiplier, terrainFrustumHolder);

		// Determine the player camera position
		final Vector3d cameraPos = CameraUniforms.getUnshiftedCameraPosition();

		final double cameraX = cameraPos.x();
		final double cameraY = cameraPos.y();
		final double cameraZ = cameraPos.z();

		// Center the frustum on the player camera position
		terrainFrustumHolder.getFrustum().setPosition(cameraX, cameraY, cameraZ);

		profiler.endSection();

		// Always schedule a terrain update
		// TODO: Only schedule a terrain update if the sun / moon is moving, or the shadow map camera moved.
		// We have to ensure that we don't regenerate clouds every frame, since that's what needsUpdate ends up doing.
		// This took up to 10% of the frame time before we applied this fix! That's really bad!
//		boolean regenerateClouds = levelRenderer.shouldRegenerateClouds();
//		((LevelRenderer) levelRenderer).needsUpdate();
//		levelRenderer.setShouldRegenerateClouds(regenerateClouds);

		// Execute the vanilla terrain setup / culling routines using our shadow frustum.
        mc.renderGlobal.clipRenderersByFrustum(terrainFrustumHolder.getFrustum(), playerCamera.getPartialTicks());
//		levelRenderer.invokeSetupRender(playerCamera, terrainFrustumHolder.getFrustum(), false, levelRenderer.getFrameId(), false);

		// Don't forget to increment the frame counter! This variable is arbitrary and only used in terrain setup,
		// and if it's not incremented, the vanilla culling code will get confused and think that it's already seen
		// chunks during traversal, and break rendering in concerning ways.
//		levelRenderer.setFrameId(levelRenderer.getFrameId() + 1);

		profiler.endStartSection("terrain");

		setupGlState(PROJECTION);

		// Render all opaque terrain unless pack requests not to
		if (shouldRenderTerrain) {
            rg.sortAndRender(mc.thePlayer, 0, playerCamera.getPartialTicks());

//          levelRenderer.invokeRenderChunkLayer(RenderLayer.solid(), modelView, cameraX, cameraY, cameraZ);
//			levelRenderer.invokeRenderChunkLayer(RenderLayer.cutout(), modelView, cameraX, cameraY, cameraZ);
//			levelRenderer.invokeRenderChunkLayer(RenderLayer.cutoutMipped(), modelView, cameraX, cameraY, cameraZ);
		}

		profiler.endStartSection("entities");

		// Get the current tick delta. Normally this is the same as client.getTickDelta(), but when the game is paused,
		// it is set to a fixed value.
		final float tickDelta = CapturedRenderingState.INSTANCE.getTickDelta();

		// Create a constrained shadow frustum for entities to avoid rendering faraway entities in the shadow pass,
		// if the shader pack has requested it. Otherwise, use the same frustum as for terrain.
		boolean hasEntityFrustum = false;

		if (entityShadowDistanceMultiplier == 1.0F || entityShadowDistanceMultiplier < 0.0F) {
			entityFrustumHolder.setInfo(terrainFrustumHolder.getFrustum(), terrainFrustumHolder.getDistanceInfo(), terrainFrustumHolder.getCullingInfo());
		} else {
			hasEntityFrustum = true;
			entityFrustumHolder = createShadowFrustum(renderDistanceMultiplier * entityShadowDistanceMultiplier, entityFrustumHolder);
		}

		Frustrum entityShadowFrustum = entityFrustumHolder.getFrustum();
		entityShadowFrustum.setPosition(cameraX, cameraY, cameraZ);

		// Render nearby entities
		//
		// Note: We must use a separate BuilderBufferStorage object here, or else very weird things will happen during
		// rendering.
//		if (renderBuffersExt != null) {
//			renderBuffersExt.beginLevelRendering();
//		}

//		if (buffers instanceof DrawCallTrackingRenderBuffers) {
//			((DrawCallTrackingRenderBuffers) buffers).resetDrawCounts();
//		}

//		BufferSource bufferSource = buffers.bufferSource();

		if (shouldRenderEntities) {
			renderEntities(levelRenderer, entityShadowFrustum, null, modelView, cameraX, cameraY, cameraZ, tickDelta);
		} else if (shouldRenderPlayer) {
//			renderPlayerEntity(levelRenderer, entityShadowFrustum, bufferSource, modelView, cameraX, cameraY, cameraZ, tickDelta);
		}

		if (shouldRenderBlockEntities) {
//			renderTileEntities(bufferSource, modelView, cameraX, cameraY, cameraZ, tickDelta, hasEntityFrustum);
		}

		profiler.endStartSection("draw entities");

		// NB: Don't try to draw the translucent parts of entities afterwards. It'll cause problems since some
		// shader packs assume that everything drawn afterwards is actually translucent and should cast a colored
		// shadow...
//		bufferSource.endBatch();

		copyPreTranslucentDepth();

		profiler.endStartSection("translucent terrain");

		// TODO: Prevent these calls from scheduling translucent sorting...
		// It doesn't matter a ton, since this just means that they won't be sorted in the getNormal rendering pass.
		// Just something to watch out for, however...
		if (shouldRenderTranslucent) {
            // TODO: Render
            // TODO -- This makes everything look... weird
//            rg.sortAndRender(mc.thePlayer, 1, playerCamera.getPartialTicks());

//			levelRenderer.invokeRenderChunkLayer(RenderLayer.translucent(), modelView, cameraX, cameraY, cameraZ);
		}

		// Note: Apparently tripwire isn't rendered in the shadow pass.
		// worldRenderer.invokeRenderType(RenderType.getTripwire(), modelView, cameraX, cameraY, cameraZ);

//		if (renderBuffersExt != null) {
//			renderBuffersExt.endLevelRendering();
//		}

        // TODO: Render
		debugStringTerrain = SodiumWorldRenderer.getInstance().getChunksDebugString();

		profiler.endStartSection("generate mipmaps");

		generateMipmaps();

		profiler.endStartSection("restore gl state");

		restoreGlState();

		if (levelRenderer instanceof CullingDataCache) {
			((CullingDataCache) levelRenderer).restoreState();
		}

        // TODO: Render
//		levelRenderer.setRenderBuffers(playerBuffers);

		ACTIVE = false;
		profiler.endSection();
		profiler.endStartSection("updatechunks");
	}

	public void addDebugText(List<String> messages) {
		messages.add("[" + Iris.MODNAME + "] Shadow Maps: " + debugStringOverall);
		messages.add("[" + Iris.MODNAME + "] Shadow Distance Terrain: " + terrainFrustumHolder.getDistanceInfo() + " Entity: " + entityFrustumHolder.getDistanceInfo());
		messages.add("[" + Iris.MODNAME + "] Shadow Culling Terrain: " + terrainFrustumHolder.getCullingInfo() + " Entity: " + entityFrustumHolder.getCullingInfo());
		messages.add("[" + Iris.MODNAME + "] Shadow Terrain: " + debugStringTerrain + (shouldRenderTerrain ? "" : " (no terrain) ") + (shouldRenderTranslucent ? "" : "(no translucent)"));
		messages.add("[" + Iris.MODNAME + "] Shadow Entities: " + getEntitiesDebugString());
		messages.add("[" + Iris.MODNAME + "] Shadow Block Entities: " + getTileEntitiesDebugString());

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
