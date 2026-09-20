/*
 * Backported from Minecraft Forge 1.12 under the LGPL 2.1
 *
 * Minecraft Forge
 * Copyright (c) 2016.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package com.gtnewhorizons.angelica.render;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.glsm.states.FogState;
import jss.notfine.core.Settings;
import jss.notfine.gui.options.named.GraphicsQualityOff;
import net.coderbot.iris.Iris;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.shaderpack.CloudSetting;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.gl.shader.GlShader;
import org.embeddedt.embeddium.impl.gl.shader.ShaderConstants;
import org.embeddedt.embeddium.impl.gl.shader.ShaderType;
import org.embeddedt.embeddium.impl.render.shader.ShaderLoader;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3d;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.gtnewhorizons.angelica.render.CloudDisc.CELLS_PER_CHUNK;
import static com.gtnewhorizons.angelica.render.CloudDisc.MARGIN_CELLS;
import static com.gtnewhorizons.angelica.render.CloudDisc.WEDGE_COUNT;
import static com.gtnewhorizons.angelica.render.CloudDisc.WEDGES_PER_RADIAN;
import static com.gtnewhorizons.angelica.render.CloudDisc.textureOffset;
import static com.gtnewhorizons.angelica.render.CloudDisc.withinMargin;

/**
 * Draws the cloud layer.
 *
 * <h2>Per frame, in {@link #render}</h2>
 * <ol>
 * <li>Reduce the texture to cells, plate rectangles and wall runs into {@link CloudShape}.
 * <li>Convert the camera position to cell space: the anchor cell, the fraction across it, and how far the
 *     cloud base sits above the camera.
 * <li>Work out which faces the view needs: the underside, the top surface, or both when the camera is
 *     inside the deck.
 * <li>Get the two detail distances from {@link CloudDisc}: where walls stop being built, and where
 *     {@link CloudPlateCover} switches to coarse plate rectangles.
 * <li>Choose the mesher: {@link CloudFaceMesh} inside the deck, {@link CloudVertexMesh} elsewhere and
 *     always for shader packs.
 * <li>Pick the matching program depending on whether every solid texel is plain white.
 * <li>Rebuild when the cache is stale. {@link CloudVertexMesh} builds on a worker thread.
 * <li>Reorder near-to-far on every cell crossed, and keep the cell the camera sits in up to date.
 * <li>Upload matrices, fog and the texture scroll into {@link CloudUniforms}, then
 *     {@link #drawVisibleWedges}.
 * </ol>
 */
public class CloudRenderer implements IResourceManagerReloadListener {

    private static final int MODE_FAST = 1;
    private static final int MODE_FANCY = 2;
    private static final float MAX_FAR_PLANE_DISTANCE = 65536.0f;
    private static final int UPLOAD_SLICE_BYTES = 256 * 1024;

    private static final Tracy.ZoneId Z_RENDER = Tracy.zoneId("cloudRender", Tracy.COLOR_CLIENT);
    private static final Tracy.ZoneId Z_SUBMIT = Tracy.zoneId("cloudSubmit", Tracy.COLOR_CLIENT);
    private static final Tracy.ZoneId Z_DRAW = Tracy.zoneId("cloudDraw", Tracy.COLOR_CLIENT);
    private static final Tracy.ZoneId Z_ASYNC_BUILD = Tracy.zoneId("cloudAsyncBuild", Tracy.COLOR_CLIENT);
    private static final Executor BUILD_THREAD = Executors.newSingleThreadExecutor(task -> {
        final Thread thread = new Thread(task, "Angelica Cloud Builder");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    private static CloudRenderer instance;
    private final Minecraft mc = Minecraft.getMinecraft();
    private final ResourceLocation texture = new ResourceLocation("textures/environment/clouds.png");
    private final CloudFaceMesh faceMesh = new CloudFaceMesh();
    private CloudVertexMesh vertexMesh = new CloudVertexMesh();
    private CloudVertexMesh spareMesh = new CloudVertexMesh();
    private final CloudVertexMesh interiorMesh = new CloudVertexMesh();
    private int interiorAnchorX = Integer.MIN_VALUE, interiorAnchorZ = Integer.MIN_VALUE;
    private int interiorShapeGeneration = Integer.MIN_VALUE;
    private float interiorCellHeight = Float.NaN;
    private boolean interiorShadersActive, interiorActive;
    private float interiorDriftX, interiorDriftZ;
    private CompletableFuture<Void> buildInFlight;
    private CompletableFuture<Void> reorderInFlight;
    private CloudVertexMesh reorderMesh;
    private BuildParams buildParams;
    private BuildParams uploadParams;
    private final Matrix4f mvpScratch = new Matrix4f();
    private final Matrix4f mvpInteriorScratch = new Matrix4f();
    private final Matrix4f modelViewScratch = new Matrix4f();
    private CloudShape shape;
    private int shapeGeneration;
    private int cloudTextureWidth;
    private int mipmappedTexId = -1;
    private int cloudTexId = -1;
    private ByteBuffer texelStagingBuffer;
    private boolean faceMeshActive;
    private int anchorCellX = Integer.MIN_VALUE, anchorCellZ = Integer.MIN_VALUE;
    private int cachedShapeGeneration = Integer.MIN_VALUE;
    private boolean cachedEmitUnderside, cachedEmitTopSurface;
    private int cachedRenderRadiusChunks = -1;
    private float cachedCellHeightBlocks = Float.NaN;
    private boolean cachedShadersActive;
    private int cachedWallCutCells = -1;
    private int cachedPlateLodCells = -1;
    private boolean geometryBuilt;
    private int wallCutCells = Integer.MAX_VALUE;
    private int plateLodCells = Integer.MAX_VALUE;
    private int cachedFovDisplayHeight = -1;
    private float cachedFovDegrees = Float.NaN;
    private double cachedPixelsPerRadian;
    private int cloudMode = -1, renderDistance = -1, cloudElevation = -1, scaleMult = -1;
    private boolean enabled;
    private double driftCells;
    private int arcFrom, arcTo;
    private int drawCalls;
    private boolean programUntextured, programFacesUntextured;
    private GlProgram<CloudUniforms> program;
    private GlProgram<CloudUniforms> programVertex, programFaces;

    public CloudRenderer() {
        ((IReloadableResourceManager) mc.getResourceManager()).registerReloadListener(this);
    }

    public static CloudRenderer getCloudRenderer() {
        if (instance == null) instance = new CloudRenderer();
        return instance;
    }

    public static int getCloudTextureWidth() {
        return instance != null && instance.cloudTextureWidth > 0 ? instance.cloudTextureWidth : 256;
    }

    public static int getScaleMult() {
        return instance != null && instance.scaleMult > 0 ? instance.scaleMult : 1;
    }

    public void checkSettings() {
        final GraphicsQualityOff cloudGraphicsQuality = (GraphicsQualityOff) Settings.MODE_CLOUDS.option.getStore();
        final int newCloudMode = cloudGraphicsQuality == GraphicsQualityOff.FANCY
            || cloudGraphicsQuality == GraphicsQualityOff.DEFAULT && mc.gameSettings.fancyGraphics ? MODE_FANCY : MODE_FAST;
        final float dimCloudHeight = mc.theWorld == null ? 128.0f : mc.theWorld.provider.getCloudHeight();
        final boolean finiteCloudHeight = Float.isFinite(dimCloudHeight);
        final boolean newEnabled = cloudGraphicsQuality != GraphicsQualityOff.OFF
            && mc.gameSettings.shouldRenderClouds()
            && mc.theWorld != null
            && mc.theWorld.provider.isSurfaceWorld()
            && finiteCloudHeight;
        final int newRenderDistance = Math.max(mc.gameSettings.renderDistanceChunks, (int) Settings.RENDER_DISTANCE_CLOUDS.option.getStore());
        final int newScaleMult = (int) Settings.CLOUD_SCALE.option.getStore();

        if (!newEnabled
            || newCloudMode != cloudMode
            || newRenderDistance != renderDistance
            || newScaleMult != scaleMult) {
            invalidateGeometry();
        }

        enabled = newEnabled;
        cloudMode = newCloudMode;
        renderDistance = newRenderDistance;
        scaleMult = newScaleMult;

        cloudElevation = finiteCloudHeight ? (int) dimCloudHeight : 128;
    }

    /**
     * Far-plane distance needed so none of the cloud geometry gets clipped.
     */
    public float getRequiredFarPlaneDistance() {
        if (!enabled || scaleMult <= 0 || renderDistance <= 0) return 0.0f;
        if (mc.theWorld == null) return 0.0f;
        final float cellWidthBlocks = 12.0f * scaleMult;
        final int renderRadiusChunks = (int) Math.ceil(renderDistance * 64.0f / (CELLS_PER_CHUNK * cellWidthBlocks));
        final int marginChunks = (MARGIN_CELLS + CELLS_PER_CHUNK - 1) / CELLS_PER_CHUNK;
        float radiusBlocks = (renderRadiusChunks + marginChunks) * CELLS_PER_CHUNK * cellWidthBlocks;
        if (cloudMode != MODE_FANCY) radiusBlocks *= (float) Math.sqrt(2);
        radiusBlocks += MARGIN_CELLS * cellWidthBlocks;
        float verticalSlack = cloudElevation;
        final Entity view = mc.renderViewEntity;
        if (view != null) {
            verticalSlack = Math.abs(cloudElevation + 0.33f - (float) view.posY) + 4.0f * scaleMult;
        }
        return Math.min(radiusBlocks + verticalSlack, MAX_FAR_PLANE_DISTANCE);
    }

    private void invalidateGeometry() {
        anchorCellX = Integer.MIN_VALUE;
        anchorCellZ = Integer.MIN_VALUE;
        cachedRenderRadiusChunks = -1;
        cachedShapeGeneration = Integer.MIN_VALUE;
        cachedCellHeightBlocks = Float.NaN;
        cancelAsyncBuild();
        vertexMesh.clear();
        spareMesh.clear();
        interiorMesh.clear();
        interiorAnchorX = Integer.MIN_VALUE;
        interiorAnchorZ = Integer.MIN_VALUE;
        interiorActive = false;
        faceMesh.clear();
        geometryBuilt = false;
    }

    private void initProgram(boolean useFaceMesh) {
        final boolean untextured = untextured();
        if (programFaces != null && programFacesUntextured != untextured) {
            programFaces.delete();
            programFaces = null;
        }
        if (programFaces == null) {
            programFaces = buildProgram(true, untextured);
            programFacesUntextured = untextured;
        }

        if (programVertex != null && programUntextured != untextured) {
            programVertex.delete();
            programVertex = null;
        }
        if (programVertex == null) {
            programVertex = buildProgram(false, untextured);
            programUntextured = untextured;
        }

        program = useFaceMesh ? programFaces : programVertex;
    }

    private boolean untextured() {
        return cloudMode == MODE_FANCY && shape != null && shape.opaqueTexelsAllWhite;
    }

    private GlProgram<CloudUniforms> buildProgram(boolean forFaceMesh, boolean untextured) {
        final ShaderConstants.Builder builder = ShaderConstants.builder();
        if (untextured) builder.add("UNTEXTURED");
        final ShaderConstants constants = builder.build();
        final String vertName = forFaceMesh ? "angelica:cloud_faces.vert" : "angelica:cloud.vert";
        final String fragName = forFaceMesh ? "angelica:cloud_faces.frag" : "angelica:cloud.frag";
        final GlShader vert = ShaderLoader.loadShader(ShaderType.VERTEX, vertName, constants);
        final GlShader frag = ShaderLoader.loadShader(ShaderType.FRAGMENT, fragName, constants);
        final GlProgram<CloudUniforms> built;
        try {
            built = GlProgram.builder("angelica:cloud")
                .attachShader(vert)
                .attachShader(frag)
                .link(CloudUniforms::new);
        } finally {
            vert.delete();
            frag.delete();
        }

        built.bind();
        if (!untextured) built.getInterface().textureUnit.setInt(0);
        built.unbind();
        return built;
    }

    private void uploadFogUniforms(CloudUniforms uniforms, float fogStart, float fogEnd) {
        final boolean fogEnabled = GLStateManager.getFogMode().isEnabled();
        uniforms.setFogEnabled(fogEnabled);
        if (!fogEnabled) return;
        final FogState fog = GLStateManager.getFogState();
        final Vector3d color = fog.getFogColor();
        uniforms.setFogColor((float) color.x, (float) color.y, (float) color.z);
        switch (fog.getFogMode()) {
            case GL11.GL_LINEAR -> {
                final float range = fogEnd - fogStart;
                uniforms.setFogParams(range != 0.0f ? -1.0f / range : 0.0f, range != 0.0f ? fogEnd / range : 1.0f, 0.0f, 0.0f);
            }
            case GL11.GL_EXP -> uniforms.setFogParams(0.0f, 0.0f, fog.getDensity(), 1.0f);
            default -> uniforms.setFogParams(0.0f, 0.0f, fog.getDensity(), 2.0f);
        }
    }

    public boolean render(int cloudTicks, float partialTicks) {
        if (!Tracy.ENABLED) return renderInner(cloudTicks, partialTicks);
        Tracy.beginZone(Z_RENDER);
        try {
            return renderInner(cloudTicks, partialTicks);
        } finally {
            Tracy.endZone();
        }
    }

    private boolean renderInner(int cloudTicks, float partialTicks) {
        if (mc.theWorld == null || mc.renderViewEntity == null) return false;
        if (!enabled || scaleMult <= 0) return false;

        final WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline != null && pipeline.getCloudSetting() == CloudSetting.OFF) return false;

        boolean textureSetupDone = false;
        if (shape == null) {
            if (mc.renderEngine == null) return false;
            mc.renderEngine.bindTexture(texture);
            setupCloudTexture();
            textureSetupDone = true;
            if (shape == null) return false;
        }

        final Entity viewEntity = mc.renderViewEntity;
        final float cellWidthBlocks = 12.0f * scaleMult;
        final float cellHeightBlocks = 4.0f * scaleMult;
        final double cloudTick = cloudTicks + (double) partialTicks;

        final float cameraY = (float) (viewEntity.lastTickPosY + (viewEntity.posY - viewEntity.lastTickPosY) * partialTicks);
        final double cameraCellX = CloudDisc.cellCoordinate(viewEntity.prevPosX + (viewEntity.posX - viewEntity.prevPosX) * partialTicks + cloudTick * 0.03D, cellWidthBlocks, 0);
        final double cameraCellZ = CloudDisc.cellCoordinate(viewEntity.prevPosZ + (viewEntity.posZ - viewEntity.prevPosZ) * partialTicks, cellWidthBlocks, 0.33000001311302185D);

        final float cloudBaseRelativeY = cloudElevation - cameraY + 0.33F;
        final float cellFractionX = (float) (cameraCellX - MathHelper.floor_double(cameraCellX));
        final float cellFractionZ = (float) (cameraCellZ - MathHelper.floor_double(cameraCellZ));

        final int anchorX = MathHelper.floor_double(cameraCellX);
        final int anchorZ = MathHelper.floor_double(cameraCellZ);

        final boolean emitUnderside = cloudBaseRelativeY + cellHeightBlocks >= 0.0F;
        final boolean emitTopSurface = cloudBaseRelativeY <= 0.0F;
        final boolean insideDeck = emitUnderside && emitTopSurface;

        final int renderRadiusChunks = (int) Math.ceil(renderDistance * 64.0f / (CELLS_PER_CHUNK * cellWidthBlocks));

        final boolean shadersActive = IrisApi.getInstance().isShaderPackInUse();
        final boolean backFaceCulling = cullBackFaces();

        final float fovDegrees = mc.gameSettings == null ? 70.0f : mc.gameSettings.fovSetting;
        if (mc.displayHeight != cachedFovDisplayHeight || fovDegrees != cachedFovDegrees) {
            cachedPixelsPerRadian = CloudDisc.pixelsPerRadian(mc.displayHeight, fovDegrees);
            cachedFovDisplayHeight = mc.displayHeight;
            cachedFovDegrees = fovDegrees;
        }
        final double pixelsPerRadian = cachedPixelsPerRadian;
        wallCutCells = CloudDisc.wallCutCells(pixelsPerRadian, !(backFaceCulling && insideDeck));
        plateLodCells = CloudDisc.plateLodCells(pixelsPerRadian, cloudBaseRelativeY, cellWidthBlocks,
            shape.coarsePlateRects != null && shape.opaqueTexelsAllWhite);

        final int marginChunks = (MARGIN_CELLS + CELLS_PER_CHUNK - 1) / CELLS_PER_CHUNK;
        final int radiusChunks = renderRadiusChunks + marginChunks;
        final int radiusCells = radiusChunks * CELLS_PER_CHUNK;
        faceMeshActive = !shadersActive && insideDeck && radiusCells <= CloudFaceMesh.CELL_LIMIT;

        initProgram(faceMeshActive);
        if (program == null) return false;

        final boolean anchorInRange = geometryBuilt
            && withinMargin(anchorX - anchorCellX, anchorZ - anchorCellZ);
        final boolean settingsUnchanged = geometryBuilt
            && cachedShapeGeneration == shapeGeneration
            && cachedEmitUnderside == emitUnderside
            && cachedEmitTopSurface == emitTopSurface
            && cachedRenderRadiusChunks == renderRadiusChunks
            && cachedCellHeightBlocks == cellHeightBlocks
            && cachedShadersActive == shadersActive
            && cachedWallCutCells == wallCutCells
            && (faceMeshActive || cachedPlateLodCells == plateLodCells);
        final boolean geomCacheValid = anchorInRange && settingsUnchanged;

        final boolean canBuildAsync = settingsUnchanged && !faceMeshActive && vertexMesh.built() && CloudDisc.withinDisc((long) anchorX - anchorCellX, (long) anchorZ - anchorCellZ, radiusCells);

        final boolean builderBusy = buildInFlight != null || uploadParams != null;
        if (!geomCacheValid && !(canBuildAsync && builderBusy)) {
            final BuildParams params = captureBuildParams(anchorX, anchorZ, radiusCells, renderRadiusChunks,
                cellHeightBlocks, emitUnderside, emitTopSurface, shadersActive, backFaceCulling);
            if (canBuildAsync) {
                startAsyncBuild(params);
            } else {
                cancelAsyncBuild();
                if (faceMeshActive) {
                    faceMesh.build(shape, anchorX, anchorZ, radiusCells, radiusCells * radiusCells, wallCutCells);
                } else {
                    buildVertexMesh(vertexMesh, params);
                    vertexMesh.uploadBuilt();
                }
                markGeometryCached(params);
            }
        }

        collectFinishedBuild();

        final boolean wantInterior = insideDeck && cloudMode == MODE_FANCY && !faceMeshActive;
        if (wantInterior && (interiorAnchorX != anchorX || interiorAnchorZ != anchorZ
            || interiorShapeGeneration != shapeGeneration || interiorCellHeight != cellHeightBlocks
            || interiorShadersActive != shadersActive)) {
            interiorMesh.buildInterior(shape, anchorX, anchorZ, cellHeightBlocks, textureOffset(anchorX, shape.width), textureOffset(anchorZ, shape.height), untextured() && !shadersActive, shadersActive);
            interiorMesh.uploadBuilt();
            interiorAnchorX = anchorX;
            interiorAnchorZ = anchorZ;
            interiorShapeGeneration = shapeGeneration;
            interiorCellHeight = cellHeightBlocks;
            interiorShadersActive = shadersActive;
        }
        interiorActive = wantInterior && interiorMesh.built();

        if (faceMeshActive) {
            if (faceMesh.orderStale(anchorX, anchorZ)) faceMesh.reorder(anchorX, anchorZ);
            if (faceMesh.uploadedCount() == 0 && faceMesh.interiorCount() == 0) return true;
        } else if (!vertexMesh.built()) {
            return true;
        }

        if (!faceMeshActive && !shadersActive && cloudMode == MODE_FANCY) {
            if (reorderInFlight != null && reorderInFlight.isDone()) {
                final boolean usable = !reorderInFlight.isCompletedExceptionally() && reorderMesh == vertexMesh;
                reorderInFlight = null;
                reorderMesh = null;
                if (usable) vertexMesh.reorderUpload();
                else vertexMesh.discardPendingReorder();
            }
            if (reorderInFlight == null && vertexMesh.orderStale(anchorX, anchorZ)) {
                final CloudVertexMesh target = vertexMesh;
                reorderMesh = target;
                reorderInFlight = CompletableFuture.runAsync(() -> target.reorder(anchorX, anchorZ), BUILD_THREAD);
            }
        }

        if (Tracy.FINE_ZONES) Tracy.beginZone(Z_SUBMIT);
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        if (cloudTexId != -1 && !textureSetupDone) {
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, cloudTexId);
        } else {
            mc.renderEngine.bindTexture(texture);
            if (!textureSetupDone) setupCloudTexture();
        }

        final Vec3 color = mc.theWorld.getCloudColour(partialTicks);
        float r = (float) color.xCoord;
        float g = (float) color.yCoord;
        float b = (float) color.zCoord;
        if (mc.gameSettings.anaglyph) {
            final float tempR = (r * 30.0F + g * 59.0F + b * 11.0F) / 100.0F;
            final float tempG = (r * 30.0F + g * 70.0F) / 100.0F;
            final float tempB = (r * 30.0F + b * 70.0F) / 100.0F;
            r = tempR;
            g = tempG;
            b = tempB;
        }

        final float origFogStart = GLStateManager.getFogState().getStart();
        final float origFogEnd = GLStateManager.getFogState().getEnd();
        final float cloudExtentBlocks = renderRadiusChunks * CELLS_PER_CHUNK * cellWidthBlocks;
        final boolean stretchFog = GLStateManager.getFogMode().isEnabled()
            && GLStateManager.getFogState().getFogMode() == GL11.GL_LINEAR
            && origFogEnd > 0.0f
            && origFogEnd < cloudExtentBlocks;
        final float fogStart = stretchFog ? origFogStart * (cloudExtentBlocks / origFogEnd) : origFogStart;
        final float fogEnd = stretchFog ? cloudExtentBlocks : origFogEnd;

        final boolean pushFog = stretchFog && shadersActive;
        if (pushFog) {
            GLStateManager.glFogf(GL11.GL_FOG_START, fogStart);
            GLStateManager.glFogf(GL11.GL_FOG_END, fogEnd);
        }

        final float anchorDriftX = (float) (anchorX - anchorCellX);
        final float anchorDriftZ = (float) (anchorZ - anchorCellZ);
        driftCells = Math.sqrt(anchorDriftX * anchorDriftX + anchorDriftZ * anchorDriftZ);

        interiorDriftX = anchorDriftX;
        interiorDriftZ = anchorDriftZ;

        if (!shadersActive) {
            program.bind();
            final CloudUniforms uniforms = program.getInterface();
            final Matrix4fStack modelView = GLStateManager.getModelViewMatrix();
            modelView.pushMatrix();
            modelView.scale(cellWidthBlocks, 1.0f, cellWidthBlocks);
            if (interiorActive) {
                modelView.pushMatrix();
                modelView.translate(-cellFractionX, cloudBaseRelativeY, -cellFractionZ);
                GLStateManager.getProjectionMatrix().mul(modelView, mvpInteriorScratch);
                modelView.popMatrix();
            }
            modelView.translate(-(cellFractionX + anchorDriftX), cloudBaseRelativeY, -(cellFractionZ + anchorDriftZ));
            modelViewScratch.set(modelView);
            GLStateManager.getProjectionMatrix().mul(modelView, mvpScratch);
            modelView.popMatrix();
            uniforms.mvp.set(mvpScratch);
            uniforms.modelView.set(modelViewScratch);
            if (faceMeshActive) {
                uniforms.setCellHeight(cellHeightBlocks);
                uniforms.setScroll(textureOffset(faceMesh.buildAnchorX(), shape.width), textureOffset(faceMesh.buildAnchorZ(), shape.height), 1.0f / shape.width, 1.0f / shape.height);
            }
            uploadFogUniforms(uniforms, fogStart, fogEnd);
            drawClouds(r, g, b, false);
            program.unbind();
        } else {
            GLStateManager.glPushMatrix();
            GLStateManager.glScalef(cellWidthBlocks, 1.0f, cellWidthBlocks);
            GLStateManager.glTranslatef(-(cellFractionX + anchorDriftX), cloudBaseRelativeY, -(cellFractionZ + anchorDriftZ));
            drawClouds(r, g, b, true);
            GLStateManager.glPopMatrix();
        }

        if (pushFog) {
            GLStateManager.glFogf(GL11.GL_FOG_START, origFogStart);
            GLStateManager.glFogf(GL11.GL_FOG_END, origFogEnd);
        }
        if (Tracy.FINE_ZONES) Tracy.endZone();
        return true;
    }

    private record BuildParams(CloudShape shape, boolean fancy, int anchorX, int anchorZ, int radiusCells,
                               float cellHeightBlocks, boolean emitUnderside, boolean emitTopSurface, boolean emitPlates,
                               boolean shadersActive, int wallCutCells, int plateLodCells, boolean untextured,
                               int shapeGeneration, int renderRadiusChunks) {}

    private BuildParams captureBuildParams(int anchorX, int anchorZ, int radiusCells, int renderRadiusChunks,
                                           float cellHeightBlocks, boolean emitUnderside, boolean emitTopSurface,
                                           boolean shadersActive, boolean backFaceCulling) {
        return new BuildParams(shape, cloudMode == MODE_FANCY, anchorX, anchorZ, radiusCells, cellHeightBlocks,
            emitUnderside, emitTopSurface, !(backFaceCulling && emitUnderside && emitTopSurface), shadersActive,
            wallCutCells, plateLodCells, untextured() && !shadersActive, shapeGeneration, renderRadiusChunks);
    }

    private static void buildVertexMesh(CloudVertexMesh mesh, BuildParams p) {
        final float scrollX = textureOffset(p.anchorX(), p.shape().width);
        final float scrollZ = textureOffset(p.anchorZ(), p.shape().height);

        if (!p.fancy()) {
            mesh.buildFast(p.shape(), p.radiusCells(), scrollX, scrollZ, p.shadersActive());
            return;
        }

        mesh.build(p.shape(), p.anchorX(), p.anchorZ(), p.radiusCells(), p.radiusCells() * p.radiusCells(),
            p.cellHeightBlocks(), scrollX, scrollZ, p.emitUnderside(), p.emitTopSurface(), p.emitPlates(),
            p.wallCutCells(), p.plateLodCells(), p.untextured(), p.shadersActive());
    }

    private void markGeometryCached(BuildParams p) {
        anchorCellX = p.anchorX();
        anchorCellZ = p.anchorZ();
        cachedShapeGeneration = p.shapeGeneration();
        cachedEmitUnderside = p.emitUnderside();
        cachedEmitTopSurface = p.emitTopSurface();
        cachedRenderRadiusChunks = p.renderRadiusChunks();
        cachedCellHeightBlocks = p.cellHeightBlocks();
        cachedShadersActive = p.shadersActive();
        cachedWallCutCells = p.wallCutCells();
        cachedPlateLodCells = p.plateLodCells();
        geometryBuilt = true;
    }

    private void startAsyncBuild(BuildParams p) {
        if (buildInFlight != null || uploadParams != null) return;
        final CloudVertexMesh target = spareMesh;
        buildParams = p;
        buildInFlight = CompletableFuture.runAsync(() -> {
            if (Tracy.ENABLED) Tracy.beginZone(Z_ASYNC_BUILD);
            try {
                buildVertexMesh(target, p);
            } finally {
                if (Tracy.ENABLED) Tracy.endZone();
            }
        }, BUILD_THREAD);
    }

    private void collectFinishedBuild() {
        if (buildInFlight != null) {
            if (!buildInFlight.isDone()) return;

            final boolean usable = !buildInFlight.isCompletedExceptionally()
                && buildParams.shapeGeneration() == shapeGeneration;
            buildInFlight = null;
            if (!usable) {
                spareMesh.discardPendingUpload();
                buildParams = null;
                return;
            }
            uploadParams = buildParams;
            buildParams = null;
            if (!spareMesh.beginUpload()) {
                swapMeshes();
                return;
            }
        }

        if (uploadParams == null) return;
        if (!spareMesh.uploadSlice(UPLOAD_SLICE_BYTES)) return;
        swapMeshes();
    }

    private void swapMeshes() {
        discardReorder();
        final CloudVertexMesh finished = spareMesh;
        spareMesh = vertexMesh;
        vertexMesh = finished;
        markGeometryCached(uploadParams);
        uploadParams = null;
    }

    private void discardReorder() {
        if (reorderInFlight != null) {
            reorderInFlight.join();
            reorderInFlight = null;
        }
        if (reorderMesh != null) {
            reorderMesh.discardPendingReorder();
            reorderMesh = null;
        }
    }

    private void cancelAsyncBuild() {
        discardReorder();
        if (buildInFlight != null) {
            buildInFlight.join();
            buildInFlight = null;
        }
        buildParams = null;
        uploadParams = null;
        spareMesh.discardPendingUpload();
    }

    private void drawClouds(float r, float g, float b, boolean shadersActive) {
        if (Tracy.ENABLED) Tracy.beginZone(Z_DRAW);
        try {
            drawCloudsInner(r, g, b, shadersActive);
        } finally {
            if (Tracy.ENABLED) Tracy.endZone();
        }
    }

    private void drawCloudsInner(float r, float g, float b, boolean shadersActive) {
        drawCalls = 0;

        if (faceMeshActive) {
            drawFaces(r, g, b);
        } else {
            drawVertices(r, g, b, shadersActive);
        }
    }

    private void drawFaces(float r, float g, float b) {
        GLStateManager.enableCull();
        applyAnaglyphColorMask();
        GLStateManager.enableBlend();
        GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GLStateManager.glDepthMask(true);

        program.getInterface().setColorMult(r, g, b);

        faceMesh.bind();
        final int interiorFaces = faceMesh.interiorCount();
        if (interiorFaces > 0) {
            faceMesh.drawInterior();
            drawCalls++;
        }
        drawVisibleWedges(faceMesh.plateStarts(), faceMesh.wallStarts(), faceMesh.uploadedCount(), true);
        faceMesh.unbind();

        GLStateManager.glDepthMask(true);
        GLStateManager.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GLStateManager.disableBlend();
    }

    private void drawVertices(float r, float g, float b, boolean depthPrepass) {
        vertexMesh.bind();
        final int totalQuads = vertexMesh.quadCount();

        if (cullBackFaces()) {
            GLStateManager.enableCull();
        } else {
            GLStateManager.disableCull();
        }

        if (depthPrepass) {
            GLStateManager.disableBlend();
            GLStateManager.glDepthMask(true);
            GLStateManager.glColorMask(false, false, false, false);
            GLStateManager.glColor4f(1.0f, 1.0f, 1.0f, CloudUniforms.ALPHA);
            vertexMesh.drawAll();
            drawCalls++;
            if (interiorActive) {
                vertexMesh.unbind();
                pushInteriorTransform(true);
                interiorMesh.bind();
                interiorMesh.drawAll();
                interiorMesh.unbind();
                popInteriorTransform(true);
                vertexMesh.bind();
                drawCalls++;
            }
        }

        applyAnaglyphColorMask();
        GLStateManager.enableBlend();
        GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GLStateManager.glDepthMask(!depthPrepass);

        if (depthPrepass) {
            for (int face = 0; face < 4; face++) {
                if (vertexMesh.faceEmpty(face)) continue;
                final float shade = CloudVertexMesh.FACE_SHADE[face];
                GLStateManager.glColor4f(shade * r, shade * g, shade * b, CloudUniforms.ALPHA);
                vertexMesh.drawFace(face);
                drawCalls++;
            }
        } else {
            program.getInterface().setColorMult(r, g, b);
            if (cloudMode == MODE_FANCY && vertexMesh.orderPublished()) {
                drawVisibleWedges(vertexMesh.plateStarts(), vertexMesh.wallStarts(), totalQuads, false);
            } else {
                drawRange(0, totalQuads, false);
            }
        }

        vertexMesh.unbind();

        if (interiorActive) {
            pushInteriorTransform(depthPrepass);
            interiorMesh.bind();
            if (depthPrepass) {
                for (int face = 0; face < 4; face++) {
                    if (interiorMesh.faceEmpty(face)) continue;
                    final float shade = CloudVertexMesh.FACE_SHADE[face];
                    GLStateManager.glColor4f(shade * r, shade * g, shade * b, CloudUniforms.ALPHA);
                    interiorMesh.drawFace(face);
                    drawCalls++;
                }
            } else {
                drawCalls++;
                interiorMesh.drawRange(0, interiorMesh.quadCount());
            }
            interiorMesh.unbind();
            popInteriorTransform(depthPrepass);
        }

        GLStateManager.enableCull();
        GLStateManager.glDepthMask(true);
        GLStateManager.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GLStateManager.disableBlend();
    }

    private void pushInteriorTransform(boolean shadersActive) {
        if (shadersActive) {
            GLStateManager.glPushMatrix();
            GLStateManager.glTranslatef(interiorDriftX, 0.0f, interiorDriftZ);
        } else {
            program.getInterface().mvp.set(mvpInteriorScratch);
        }
    }

    private void popInteriorTransform(boolean shadersActive) {
        if (shadersActive) {
            GLStateManager.glPopMatrix();
        } else {
            program.getInterface().mvp.set(mvpScratch);
        }
    }

    private boolean cullBackFaces() {
        return cloudMode == MODE_FANCY;
    }

    private void drawVisibleWedges(int[] plateStarts, int[] wallStarts, int total, boolean fromFaceMesh) {
        final Entity view = mc.renderViewEntity;
        if (view == null) {
            drawRange(0, total, fromFaceMesh);
            return;
        }
        final int callsBefore = drawCalls;

        drawRange(0, plateStarts[0], fromFaceMesh);

        if (!computeVisibleArc(view)) {
            drawRange(plateStarts[0], total, fromFaceMesh);
            return;
        }

        if (arcFrom <= arcTo) {
            drawRange(plateStarts[arcFrom], plateStarts[arcTo + 1], fromFaceMesh);
            drawRange(plateStarts[WEDGE_COUNT], wallStarts[0], fromFaceMesh);
            drawRange(wallStarts[arcFrom], wallStarts[arcTo + 1], fromFaceMesh);
        } else {
            drawRange(plateStarts[arcFrom], plateStarts[WEDGE_COUNT], fromFaceMesh);
            drawRange(plateStarts[0], plateStarts[arcTo + 1], fromFaceMesh);
            drawRange(plateStarts[WEDGE_COUNT], wallStarts[0], fromFaceMesh);
            drawRange(wallStarts[arcFrom], wallStarts[WEDGE_COUNT], fromFaceMesh);
            drawRange(wallStarts[0], wallStarts[arcTo + 1], fromFaceMesh);
        }

        if (drawCalls == callsBefore && total > 0) {
            drawRange(0, total, fromFaceMesh);
        }
    }

    private void drawRange(int start, int end, boolean fromFaceMesh) {
        final int count = end - start;
        if (count <= 0) return;
        drawCalls++;
        if (fromFaceMesh) {
            faceMesh.drawRange(start, end);
        } else {
            vertexMesh.drawRange(start, end);
        }
    }

    private boolean computeVisibleArc(Entity view) {
        final double yaw = Math.toRadians(view.rotationYaw);
        final double facing = Math.atan2(Math.cos(yaw), -Math.sin(yaw));
        final float m00 = GLStateManager.getProjectionMatrix().m00();
        final double halfFov = m00 > 0.0f ? Math.atan(1.0 / m00) : Math.PI;

        final double halfSpan = (halfFov + CloudDisc.driftAngle(driftCells)) * WEDGES_PER_RADIAN + 0.25;
        if (halfSpan * 2.0 + 1.0 >= WEDGE_COUNT) return false;

        final double centre = (facing + Math.PI) * WEDGES_PER_RADIAN;
        arcFrom = Math.floorMod((int) Math.floor(centre - halfSpan), WEDGE_COUNT);
        arcTo = Math.floorMod((int) Math.ceil(centre + halfSpan), WEDGE_COUNT);
        return true;
    }

    private void applyAnaglyphColorMask() {
        if (!mc.gameSettings.anaglyph) {
            GLStateManager.glColorMask(true, true, true, true);
            return;
        }
        switch (EntityRenderer.anaglyphField) {
            case 0 -> GLStateManager.glColorMask(false, true, true, true);
            case 1 -> GLStateManager.glColorMask(true, false, false, true);
            default -> GLStateManager.glColorMask(true, true, true, true);
        }
    }

    private void setupCloudTexture() {
        final int boundTexId = GLStateManager.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        if (boundTexId != mipmappedTexId) {
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 4);
            GLStateManager.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST_MIPMAP_LINEAR);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
            mipmappedTexId = boundTexId;
        }
        if (boundTexId != cloudTexId || shape == null) {
            extractCloudShape();
            cloudTexId = boundTexId;
        }
    }

    private void extractCloudShape() {
        final int texWidth = GLStateManager.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        final int texHeight = GLStateManager.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        if (texWidth <= 0 || texHeight <= 0) return;
        final int neededBytes = texWidth * texHeight * 4;
        if (texelStagingBuffer == null || texelStagingBuffer.capacity() < neededBytes) {
            texelStagingBuffer = BufferUtils.createByteBuffer(neededBytes);
        } else {
            texelStagingBuffer.clear();
        }
        final ByteBuffer pixels = texelStagingBuffer;
        GLStateManager.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        cloudTextureWidth = texWidth;
        shape = new CloudShape(texWidth, texHeight, pixels);
        shapeGeneration++;
        invalidateGeometry();
    }

    private void reloadTextures() {
        if (mc.renderEngine == null) return;
        mc.renderEngine.bindTexture(texture);
        extractCloudShape();
        invalidateGeometry();
        vertexMesh.deleteVao();
        vertexMesh.deleteEbo();
        spareMesh.deleteVao();
        spareMesh.deleteEbo();
        interiorMesh.deleteVao();
        interiorMesh.deleteEbo();
        mipmappedTexId = -1;
        cloudTexId = -1;
    }

    @Override
    public void onResourceManagerReload(IResourceManager resourceManager) {
        reloadTextures();
        if (programVertex != null) {
            programVertex.delete();
            programVertex = null;
        }
        if (programFaces != null) {
            programFaces.delete();
            programFaces = null;
        }
        program = null;
    }
}
