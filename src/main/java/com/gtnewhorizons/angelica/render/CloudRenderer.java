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

import static com.gtnewhorizons.angelica.render.CloudDisc.CELLS_PER_CHUNK;
import static com.gtnewhorizons.angelica.render.CloudDisc.MARGIN_CELLS;
import static com.gtnewhorizons.angelica.render.CloudDisc.SCROLL_SPEED;
import static com.gtnewhorizons.angelica.render.CloudDisc.WEDGE_COUNT;
import static com.gtnewhorizons.angelica.render.CloudDisc.WEDGES_PER_RADIAN;

/**
 * Draws the cloud layer.
 *
 * <h2>Per frame, in {@link #render}</h2>
 * <ol>
 * <li>Reduce the texture to cells, plate rectangles and wall runs into {@link CloudShape}.
 * <li>Convert the camera position to cell space: the anchor cell, the fraction across it, and how far the
 *     cloud base sits above the camera.
 * <li>Figure out what plates a player can see.
 * <li>Get the two detail distances from {@link CloudDisc}: where walls stop being built, and where
 *     {@link CloudPlateCover} switches to coarse plate rectangles.
 * <li>Choose the mesher: {@link CloudFaceMesh} inside the deck, {@link CloudVertexMesh} elsewhere and
 *     always for shader packs.
 * <li>Rebuild through {@link #rebuildGeometry} when the cache is stale.
 * <li>Reorder near-to-far with {@link CloudVertexMesh#reorder} on every cell crossed.
 * <li>Upload matrices and fog into {@link CloudUniforms}, then {@link #drawVisibleWedges}.
 * </ol>
 */
public class CloudRenderer implements IResourceManagerReloadListener {

    private static final int MODE_FAST = 1;
    private static final int MODE_FANCY = 2;
    private static final float MAX_FAR_PLANE_DISTANCE = 65536.0f;

    private static final Tracy.ZoneId Z_DRAW = Tracy.zoneId("cloudDraw", Tracy.COLOR_CLIENT);
    private static CloudRenderer instance;
    private final Minecraft mc = Minecraft.getMinecraft();
    private final ResourceLocation texture = new ResourceLocation("textures/environment/clouds.png");
    private final CloudFaceMesh faceMesh = new CloudFaceMesh();
    private final CloudVertexMesh vertexMesh = new CloudVertexMesh();
    private final Matrix4f mvpScratch = new Matrix4f();
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
    private int cloudMode = -1, renderDistance = -1, cloudElevation = -1, scaleMult = -1;
    private boolean enabled;
    private double driftCells;
    private int arcFrom, arcTo;
    private int drawnQuads, drawCalls;
    private boolean programUntextured;
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

    private static boolean withinMargin(int driftX, int driftZ) {
        return driftX * driftX + driftZ * driftZ <= MARGIN_CELLS * MARGIN_CELLS;
    }

    private void invalidateGeometry() {
        anchorCellX = Integer.MIN_VALUE;
        anchorCellZ = Integer.MIN_VALUE;
        cachedRenderRadiusChunks = -1;
        cachedShapeGeneration = Integer.MIN_VALUE;
        cachedCellHeightBlocks = Float.NaN;
        vertexMesh.clear();
        faceMesh.clear();
        geometryBuilt = false;
    }

    private void initProgram(boolean useFaceMesh) {
        if (programFaces == null) {
            programFaces = buildProgram(true, true);
        }

        final boolean untextured = untextured();
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

    private void uploadFogUniforms(float fogStart, float fogEnd) {
        final CloudUniforms uniforms = program.getInterface();
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
        double cameraCellX = (viewEntity.prevPosX + (viewEntity.posX - viewEntity.prevPosX) * partialTicks + cloudTick * 0.03D) / cellWidthBlocks;
        double cameraCellZ = (viewEntity.prevPosZ + (viewEntity.posZ - viewEntity.prevPosZ) * partialTicks) / cellWidthBlocks;
        cameraCellX -= MathHelper.floor_double(cameraCellX / 2048.0D) * 2048;
        cameraCellZ -= MathHelper.floor_double(cameraCellZ / 2048.0D) * 2048;

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

        final double pixelsPerRadian = CloudDisc.pixelsPerRadian(
            mc.displayHeight, mc.gameSettings == null ? 70.0f : mc.gameSettings.fovSetting);
        wallCutCells = CloudDisc.wallCutCells(pixelsPerRadian, !(backFaceCulling && insideDeck));
        plateLodCells = CloudDisc.plateLodCells(pixelsPerRadian, cloudBaseRelativeY, cellWidthBlocks,
            shape.coarsePlateRects != null && shape.opaqueTexelsAllWhite);

        final int marginChunks = (MARGIN_CELLS + CELLS_PER_CHUNK - 1) / CELLS_PER_CHUNK;
        final int radiusChunks = renderRadiusChunks + marginChunks;
        final int radiusCells = radiusChunks * CELLS_PER_CHUNK;
        faceMeshActive = !shadersActive && untextured() && insideDeck && radiusCells <= CloudFaceMesh.CELL_LIMIT;

        initProgram(faceMeshActive);
        if (program == null) return false;

        final boolean anchorInRange = geometryBuilt
            && (insideDeck
            ? anchorX == anchorCellX && anchorZ == anchorCellZ
            : withinMargin(anchorX - anchorCellX, anchorZ - anchorCellZ));
        final boolean geomCacheValid = anchorInRange
            && cachedShapeGeneration == shapeGeneration
            && cachedEmitUnderside == emitUnderside
            && cachedEmitTopSurface == emitTopSurface
            && cachedRenderRadiusChunks == renderRadiusChunks
            && cachedCellHeightBlocks == cellHeightBlocks
            && cachedShadersActive == shadersActive
            && cachedWallCutCells == wallCutCells
            && (faceMeshActive || cachedPlateLodCells == plateLodCells);

        if (!geomCacheValid) {
            rebuildGeometry(anchorX, anchorZ, radiusCells, cellHeightBlocks, emitUnderside, emitTopSurface, shadersActive, backFaceCulling);
            anchorCellX = anchorX;
            anchorCellZ = anchorZ;
            cachedShapeGeneration = shapeGeneration;
            cachedEmitUnderside = emitUnderside;
            cachedEmitTopSurface = emitTopSurface;
            cachedRenderRadiusChunks = renderRadiusChunks;
            cachedCellHeightBlocks = cellHeightBlocks;
            cachedShadersActive = shadersActive;
            cachedWallCutCells = wallCutCells;
            cachedPlateLodCells = plateLodCells;
            geometryBuilt = true;
        }

        if (faceMeshActive) {
            if (faceMesh.uploadedCount() == 0) return true;
        } else if (!vertexMesh.built()) {
            return true;
        }

        if (!faceMeshActive && !shadersActive && cloudMode == MODE_FANCY && vertexMesh.orderStale(anchorX, anchorZ)) {
            vertexMesh.reorder(anchorX, anchorZ);
        }

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

        if (!shadersActive) {
            program.bind();
            final CloudUniforms uniforms = program.getInterface();
            final Matrix4fStack modelView = GLStateManager.getModelViewMatrix();
            modelView.pushMatrix();
            modelView.scale(cellWidthBlocks, 1.0f, cellWidthBlocks);
            modelView.translate(-(cellFractionX + anchorDriftX), cloudBaseRelativeY, -(cellFractionZ + anchorDriftZ));
            modelViewScratch.set(modelView);
            GLStateManager.getProjectionMatrix().mul(modelView, mvpScratch);
            modelView.popMatrix();
            uniforms.mvp.set(mvpScratch);
            uniforms.modelView.set(modelViewScratch);
            if (faceMeshActive) uniforms.setCellHeight(cellHeightBlocks);
            uploadFogUniforms(fogStart, fogEnd);
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
        return true;
    }

    private void rebuildGeometry(int anchorX, int anchorZ, int radiusCells, float cellHeightBlocks,
                                 boolean emitUnderside, boolean emitTopSurface, boolean shadersActive, boolean backFaceCulling) {
        final float scrollX = anchorX * SCROLL_SPEED;
        final float scrollZ = anchorZ * SCROLL_SPEED;

        if (cloudMode != MODE_FANCY) {
            vertexMesh.buildFast(radiusCells, scrollX, scrollZ, false, shadersActive);
            return;
        }

        final boolean emitPlates = !(backFaceCulling && emitUnderside && emitTopSurface);
        final int radiusCellsSq = radiusCells * radiusCells;

        if (faceMeshActive) {
            faceMesh.build(shape, anchorX, anchorZ, radiusCells, radiusCellsSq, wallCutCells);
            return;
        }

        vertexMesh.build(shape, anchorX, anchorZ, radiusCells, radiusCellsSq, cellHeightBlocks,
            scrollX, scrollZ, emitUnderside, emitTopSurface, emitPlates,
            wallCutCells, plateLodCells, untextured() && !shadersActive, shadersActive);
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
        drawnQuads = 0;
        drawCalls = 0;

        if (faceMeshActive) {
            drawFaces(r, g, b);
        } else {
            drawVertices(r, g, b, shadersActive);
        }

        if (Tracy.ENABLED) {
            Tracy.plotInt("clouds.drawnQuads", drawnQuads);
            Tracy.plotInt("clouds.drawCalls", drawCalls);
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
            drawnQuads += totalQuads;
            drawCalls++;
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
                drawnQuads += vertexMesh.drawFace(face);
                drawCalls++;
            }
        } else {
            program.getInterface().setColorMult(r, g, b);
            if (cloudMode == MODE_FANCY) {
                drawVisibleWedges(vertexMesh.plateStarts(), vertexMesh.wallStarts(), totalQuads, false);
            } else {
                drawRange(0, totalQuads, false);
            }
        }

        vertexMesh.unbind();
        GLStateManager.enableCull();
        GLStateManager.glDepthMask(true);
        GLStateManager.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GLStateManager.disableBlend();
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
    }

    private void drawRange(int start, int end, boolean fromFaceMesh) {
        final int count = end - start;
        if (count <= 0) return;
        drawnQuads += count;
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
        if (halfSpan * 2.0 >= WEDGE_COUNT) return false;

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
        vertexMesh.deleteVao();
        vertexMesh.deleteEbo();
        invalidateGeometry();
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
