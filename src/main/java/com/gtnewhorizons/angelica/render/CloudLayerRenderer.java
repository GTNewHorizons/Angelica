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

import com.gtnewhorizons.angelica.api.clouds.CloudLayer;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.glsm.states.FogState;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3d;
import org.lwjgl.opengl.GL11;

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
 * Draws a cloud layer.
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
final class CloudLayerRenderer {

    static final int MODE_FAST = 1;
    static final int MODE_FANCY = 2;
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
    private final Minecraft mc = Minecraft.getMinecraft();
    private final CloudRenderResources resources;
    final CloudRenderResources.Texture texture;
    CloudLayer layer;
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
    private CloudView cloudView;
    private int cloudMode = -1, renderDistance = -1, scaleMult = -1;
    private float alpha;
    private double driftCells;
    private double cullingHeightCells;
    private int arcFrom, arcTo;
    private int drawCalls;
    private GlProgram<CloudUniforms> program;

    CloudLayerRenderer(CloudRenderResources resources, CloudRenderResources.Texture texture) {
        this.resources = resources;
        this.texture = texture;
    }

    void configure(CloudLayer layer, int mode, int distance, int scale) {
        this.layer = layer;
        if (cloudMode != mode || renderDistance != distance || scaleMult != scale) {
            invalidateGeometry();
        }
        cloudMode = mode;
        renderDistance = distance;
        scaleMult = scale;
        alpha = layer.alpha();
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

    private boolean untextured() {
        return cloudMode == MODE_FANCY && shape != null && shape.opaqueTexelsAllWhite;
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

    boolean render(float partialTicks, CloudView cloudView) {
        this.cloudView = cloudView;
        if (!Tracy.ENABLED) return renderInner(partialTicks);
        Tracy.beginZone(Z_RENDER);
        try {
            return renderInner(partialTicks);
        } finally {
            Tracy.endZone();
        }
    }

    private boolean renderInner(float partialTicks) {
        texture.bind();
        shape = texture.shape;
        shapeGeneration = texture.generation;
        if (shape == null) return false;

        final Entity viewEntity = mc.renderViewEntity;
        final float cellWidthBlocks = layer.cellWidth() * scaleMult;
        final float cellHeightBlocks = layer.cellHeight() * scaleMult;
        final float coordinateWidth = layer.coordinateWidth() * scaleMult;

        final float cameraY = (float) (viewEntity.lastTickPosY + (viewEntity.posY - viewEntity.lastTickPosY) * partialTicks);
        final double cameraCellX = CloudDisc.cellCoordinate(viewEntity.prevPosX + (viewEntity.posX - viewEntity.prevPosX) * partialTicks, coordinateWidth, layer.offsetX());
        final double cameraCellZ = CloudDisc.cellCoordinate(viewEntity.prevPosZ + (viewEntity.posZ - viewEntity.prevPosZ) * partialTicks, coordinateWidth, layer.offsetZ());

        final float cloudBaseRelativeY = layer.height() - cameraY + 0.33F;
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

        final double pixelsPerRadian = cloudView.pixelsPerRadian;
        wallCutCells = CloudDisc.wallCutCells(pixelsPerRadian, !(backFaceCulling && insideDeck));
        plateLodCells = CloudDisc.plateLodCells(pixelsPerRadian, cloudBaseRelativeY, cellWidthBlocks,
            shape.coarsePlateRects != null && shape.opaqueTexelsAllWhite);

        final int marginChunks = (MARGIN_CELLS + CELLS_PER_CHUNK - 1) / CELLS_PER_CHUNK;
        final int radiusChunks = renderRadiusChunks + marginChunks;
        final int radiusCells = radiusChunks * CELLS_PER_CHUNK;
        faceMeshActive = cloudMode == MODE_FANCY && !shadersActive && insideDeck && radiusCells <= CloudFaceMesh.CELL_LIMIT;

        if (!shadersActive) program = resources.program(faceMeshActive, untextured());

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
        float r = layer.red();
        float g = layer.green();
        float b = layer.blue();
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
        // Face wedges are re-bucketed around the current cell; vertex wedges keep their build anchor.
        final double cameraDriftX = (faceMeshActive ? 0 : anchorDriftX) + cellFractionX + cloudView.offsetX / cellWidthBlocks;
        final double cameraDriftZ = (faceMeshActive ? 0 : anchorDriftZ) + cellFractionZ + cloudView.offsetZ / cellWidthBlocks;
        driftCells = Math.hypot(cameraDriftX, cameraDriftZ);
        final double baseFromEye = cloudBaseRelativeY - cloudView.offsetY;
        cullingHeightCells = Math.max(Math.abs(baseFromEye), Math.abs(baseFromEye + cellHeightBlocks)) / cellWidthBlocks;

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

        program.getInterface().setColorMult(r, g, b, alpha);

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
            GLStateManager.glColor4f(1.0f, 1.0f, 1.0f, alpha);
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
                GLStateManager.glColor4f(shade * r, shade * g, shade * b, alpha);
                vertexMesh.drawFace(face);
                drawCalls++;
            }
        } else {
            program.getInterface().setColorMult(r, g, b, alpha);
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
                    GLStateManager.glColor4f(shade * r, shade * g, shade * b, alpha);
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
        if (!computeVisibleArc()) {
            drawRange(0, total, fromFaceMesh);
            return;
        }

        drawRange(0, plateStarts[0], fromFaceMesh);

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
        drawCalls++;
        if (fromFaceMesh) {
            faceMesh.drawRange(start, end);
        } else {
            vertexMesh.drawRange(start, end);
        }
    }

    private boolean computeVisibleArc() {
        final double halfSpan = cloudView.halfSpan(cullingHeightCells, driftCells) * WEDGES_PER_RADIAN + 0.25;
        if (halfSpan * 2.0 + 1.0 >= WEDGE_COUNT) return false;

        final double centre = (cloudView.facing + Math.PI) * WEDGES_PER_RADIAN;
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

    void delete() {
        invalidateGeometry();
        vertexMesh.deleteVao();
        vertexMesh.deleteEbo();
        spareMesh.deleteVao();
        spareMesh.deleteEbo();
        interiorMesh.deleteVao();
        interiorMesh.deleteEbo();
        faceMesh.delete();
        program = null;
    }
}
