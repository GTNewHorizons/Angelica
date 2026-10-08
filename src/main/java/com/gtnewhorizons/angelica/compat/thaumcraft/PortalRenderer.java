package com.gtnewhorizons.angelica.compat.thaumcraft;

import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.StateSet;
import com.gtnewhorizons.angelica.glsm.hooks.BatchStateGuard;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.glsm.states.ViewportState;
import com.gtnewhorizons.angelica.rendering.tesr.PendingTesrDraws;
import com.gtnewhorizons.angelica.rendering.tesr.TesrProviderDispatch;
import net.coderbot.iris.Iris;
import net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline;
import net.coderbot.iris.pipeline.ShadowRenderer;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import thaumcraft.common.tiles.TileEldritchLock;
import thaumcraft.common.tiles.TileEldritchNothing;
import thaumcraft.common.tiles.TileHole;

import java.util.Arrays;

/**
 * Draws Thaumcraft's end-portal-style planes (eldritch obelisk, nothing, lock, portable hole, mirror) in one pass.
 */
public final class PortalRenderer implements PendingTesrDraws.Pending {
    public static final int AXIS_X = 0;
    public static final int AXIS_Y = 1;
    public static final int AXIS_Z = 2;

    private static final ResourceLocation TUNNEL = new ResourceLocation("thaumcraft", "textures/misc/tunnel.png");
    private static final ResourceLocation FIELD = new ResourceLocation("thaumcraft", "textures/misc/particlefield.png");
    private static final ResourceLocation FAR_FIELD = new ResourceLocation("thaumcraft", "textures/misc/particlefield32.png");
    private static final int FULL_BRIGHT = 240 | (240 << 16);
    private static final int TC_BRIGHTNESS = 180;
    private static final double LAYERED_RANGE_SQUARED = 512.0D;
    private static final Matrix4fc IDENTITY = new Matrix4f();
    private static final Tracy.ZoneId Z_FLUSH = Tracy.zoneId("thaumcraftPortals", Tracy.COLOR_CLIENT);

    private static final int MODE_DIRECT = 0;
    private static final int MODE_CAPTURE = 1;
    private static final int MODE_SHADOW = 2;

    private static final PortalRenderer INSTANCE = new PortalRenderer();
    private static final PortalDrawer drawer = new PortalDrawer();
    private static final PortalDrawer.Frame frame = new PortalDrawer.Frame();
    private static final Matrix4f modelViewProjection = new Matrix4f();
    private static final Matrix4f savedTextureMatrix = new Matrix4f();
    private static final Vector4f projected = new Vector4f();
    private static final float[] screenX = new float[4];
    private static final float[] screenY = new float[4];

    private static float[] faces = new float[PortalDrawer.FLOATS_PER_FACE * 64];
    private static int[] faceBlockEntity = new int[64];
    private static int faceCount;
    private static float[] farFaces = new float[PortalDrawer.FLOATS_PER_FACE * 64];
    private static float[] farGray = new float[64];
    private static int[] farBlockEntity = new int[64];
    private static int farCount;
    private static int batchMode;
    private static boolean batchFogged;
    private static int batchModelViewGeneration;
    private static int batchProjectionGeneration;
    private static boolean skippedPlaneState;
    private static int lastPlaneTexture;
    private static int tunnelTexture;
    private static int fieldTexture;
    private static int farTexture;

    private PortalRenderer() {
    }

    public static boolean replacesLayers() {
        return !DisplayListManager.isRecording();
    }

    public static boolean inLayeredRange(TileEntity te) {
        return Minecraft.getMinecraft().renderViewEntity.getDistanceSq(te.xCoord + 0.5D, te.yCoord + 0.5D, te.zCoord + 0.5D) < LAYERED_RANGE_SQUARED;
    }

    public static void addFace(int axis, boolean negative, double plane, double x0, double y0, double z0, double x1, double y1, double z1, double x3, double y3, double z3) {
        addFace(GLStateManager.getFogMode().isEnabled(), axis, negative, plane, x0, y0, z0, x1, y1, z1, x3, y3, z3);
    }

    static void addFace(boolean fogged, int axis, boolean negative, double plane, double x0, double y0, double z0, double x1, double y1, double z1, double x3, double y3, double z3) {
        prepareBatch(fogged);
        int planeIndex = 0;
        if (batchMode != MODE_SHADOW) {
            planeIndex = frame.plane(axis, negative, plane);
            if (planeIndex < 0) {
                flushFaces();
                frame.clearPlanes();
                planeIndex = frame.plane(axis, negative, plane);
            }
        }
        putLayeredFace(axis, planeIndex, x0, y0, z0, x1, y1, z1, x3, y3, z3);
        lastPlaneTexture = fieldTexture;
    }

    static void addFarFace(int axis, float gray, double x0, double y0, double z0, double x1, double y1, double z1, double x3, double y3, double z3) {
        prepareBatch(false);
        lastPlaneTexture = farTexture;
        if (batchMode == MODE_SHADOW) {
            putLayeredFace(axis, 0, x0, y0, z0, x1, y1, z1, x3, y3, z3);
            return;
        }
        if (farCount == farBlockEntity.length) {
            farFaces = Arrays.copyOf(farFaces, farFaces.length * 2);
            farGray = Arrays.copyOf(farGray, farGray.length * 2);
            farBlockEntity = Arrays.copyOf(farBlockEntity, farBlockEntity.length * 2);
        }
        PortalDrawer.putFace(farFaces, farCount, axis, 0, x0, y0, z0, x1, y1, z1, x3, y3, z3);
        farGray[farCount] = gray;
        farBlockEntity[farCount] = CapturedRenderingState.INSTANCE.getCurrentRenderedBlockEntity();
        farCount++;
        skippedPlaneState = true;
    }

    public static void drawBuilt(TileEntity te, boolean hole, int mask, double x, double y, double z) {
        PendingTesrDraws.beforeTileEntity(te);
        if (mask == 0) return;
        final CapturedRenderingState rendering = CapturedRenderingState.INSTANCE;
        rendering.pushCurrentBlockEntity();
        try {
            rendering.setCurrentBlockEntity(te.getBlockType(), TesrProviderDispatch.blockMetadata(te));
            if (hole) {
                PortalFaces.addHoleFaces(mask, inLayeredRange(te), x, y, z);
            } else {
                PortalFaces.addNothingFaces(mask, inLayeredRange(te), x, y, z);
            }
        } finally {
            rendering.popCurrentBlockEntity();
        }
        skippedPlaneState = false;
        if (PendingTesrDraws.canDefer()) {
            PendingTesrDraws.hold(INSTANCE);
        } else {
            flushFaces();
        }
    }

    public static void endSkippedRender() {
        if (!GLStateManager.glIsEnabled(GL11.GL_FOG)) GLStateManager.glEnable(GL11.GL_FOG);
    }

    private static void prepareBatch(boolean fogged) {
        if ((faceCount > 0 || farCount > 0) && fogged != batchFogged) flushFaces();
        batchFogged = fogged;
        final int modelViewGeneration = GLStateManager.getMvGeneration();
        final int projectionGeneration = GLStateManager.getProjGeneration();
        final boolean empty = faceCount == 0 && farCount == 0;
        if (empty || modelViewGeneration != batchModelViewGeneration || projectionGeneration != batchProjectionGeneration) {
            final int mode = currentMode();
            GLStateManager.getProjectionMatrix().mul(GLStateManager.getModelViewMatrix(), modelViewProjection);
            if (!empty && (mode != batchMode || !modelViewProjection.equals(frame.modelViewProjection))) flushFaces();
            if (faceCount == 0 && farCount == 0) startBatch(mode);
            batchModelViewGeneration = modelViewGeneration;
            batchProjectionGeneration = projectionGeneration;
        }
    }

    private static void putLayeredFace(int axis, int planeIndex, double x0, double y0, double z0, double x1, double y1, double z1, double x3, double y3, double z3) {
        if (faceCount == faceBlockEntity.length) {
            faces = Arrays.copyOf(faces, faces.length * 2);
            faceBlockEntity = Arrays.copyOf(faceBlockEntity, faceBlockEntity.length * 2);
        }
        PortalDrawer.putFace(faces, faceCount, axis, planeIndex, x0, y0, z0, x1, y1, z1, x3, y3, z3);
        faceBlockEntity[faceCount] = CapturedRenderingState.INSTANCE.getCurrentRenderedBlockEntity();
        faceCount++;
        skippedPlaneState = true;
    }

    private static void restoreSkippedPlaneState() {
        if (!skippedPlaneState) return;
        skippedPlaneState = false;
        if (GLStateManager.glIsEnabled(GL11.GL_BLEND)) GLStateManager.disableBlend();
        if (GLStateManager.glIsEnabled(GL11.GL_TEXTURE_GEN_S)) GLStateManager.glDisable(GL11.GL_TEXTURE_GEN_S);
        if (GLStateManager.glIsEnabled(GL11.GL_TEXTURE_GEN_T)) GLStateManager.glDisable(GL11.GL_TEXTURE_GEN_T);
        if (GLStateManager.glIsEnabled(GL11.GL_TEXTURE_GEN_R)) GLStateManager.glDisable(GL11.GL_TEXTURE_GEN_R);
        if (GLStateManager.glIsEnabled(GL11.GL_TEXTURE_GEN_Q)) GLStateManager.glDisable(GL11.GL_TEXTURE_GEN_Q);
        if (!GLStateManager.glIsEnabled(GL11.GL_LIGHTING)) GLStateManager.enableLighting();
        if (GLStateManager.getTextures().getTextureUnitBindings(GLStateManager.getActiveTextureUnit()).getBinding() != lastPlaneTexture) {
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, lastPlaneTexture);
        }
    }

    public static void drawBeforeOverlay() {
        restoreSkippedPlaneState();
        flushFaces();
    }

    public static void endTileEntity(boolean canWait) {
        restoreSkippedPlaneState();
        if (faceCount == 0 && farCount == 0) return;
        if (canWait && PendingTesrDraws.canDefer()) {
            PendingTesrDraws.hold(INSTANCE);
        } else {
            flushFaces();
        }
    }

    private static void flushFaces() {
        if (faceCount == 0 && farCount == 0) return;
        if (Tracy.ENABLED) Tracy.beginZone(Z_FLUSH);
        try {
            if (faceCount > 0) {
                switch (batchMode) {
                    case MODE_CAPTURE -> captureAndComposite(tunnelTexture, fieldTexture);
                    case MODE_SHADOW -> drawQuads(tunnelTexture, 0, faceCount, false);
                    default -> drawer.drawDirect(faces, faceCount, tunnelTexture, fieldTexture, frame, batchFogged);
                }
            }
            if (farCount > 0) drawFarFaces();
        } finally {
            faceCount = 0;
            farCount = 0;
            if (Tracy.ENABLED) Tracy.endZone();
        }
    }

    private static int currentMode() {
        if (ShadowRenderer.ACTIVE) return MODE_SHADOW;
        return capturingPipeline() != null ? MODE_CAPTURE : MODE_DIRECT;
    }

    private static DeferredWorldRenderingPipeline capturingPipeline() {
        if (!Iris.enabled) return null;
        return Iris.getPipelineManager().getPipelineNullable() instanceof DeferredWorldRenderingPipeline pipeline && pipeline.shouldOverrideShaders() ? pipeline : null;
    }

    private static void startBatch(int mode) {
        batchMode = mode;
        tunnelTexture = textureId(TUNNEL);
        fieldTexture = textureId(FIELD);
        farTexture = textureId(FAR_FIELD);
        frame.modelViewProjection.set(modelViewProjection);
        if (mode == MODE_SHADOW) return;
        frame.modelView.set(GLStateManager.getModelViewMatrix());
        frame.cameraX = (float) TileEntityRendererDispatcher.staticPlayerX;
        frame.cameraY = (float) TileEntityRendererDispatcher.staticPlayerY;
        frame.cameraZ = (float) TileEntityRendererDispatcher.staticPlayerZ;
        frame.viewX = ActiveRenderInfo.objectX;
        frame.viewY = ActiveRenderInfo.objectY;
        frame.viewZ = ActiveRenderInfo.objectZ;
        frame.time = (float) (System.currentTimeMillis() % 700000L) / 250000.0F;
        frame.prepareLayers();
        frame.readLightmap(TC_BRIGHTNESS);
    }

    private static void captureAndComposite(int tunnel, int field) {
        final DeferredWorldRenderingPipeline pipeline = capturingPipeline();
        if (pipeline == null) {
            drawer.drawDirect(faces, faceCount, tunnel, field, frame, batchFogged);
            return;
        }
        final ViewportState viewport = GLStateManager.getViewportState();
        int first = 0, x = 0, y = 0, rowHeight = 0;
        for (int face = 0; face < faceCount; face++) {
            final int size = slotSize(face, viewport.width, viewport.height);
            if (x + size > PortalDrawer.ATLAS_SIZE) {
                x = 0;
                y += rowHeight;
                rowHeight = 0;
            }
            if (y + size > PortalDrawer.ATLAS_SIZE) {
                capture(pipeline, first, face - first, tunnel, field);
                drawQuads(drawer.atlasTexture(), first, face - first, true);
                first = face;
                x = y = rowHeight = 0;
            }
            final int i = face * PortalDrawer.FLOATS_PER_FACE;
            faces[i + 7] = x;
            faces[i + 11] = y;
            faces[i + 15] = size;
            x += size;
            rowHeight = Math.max(rowHeight, size);
        }
        capture(pipeline, first, faceCount - first, tunnel, field);
        drawQuads(drawer.atlasTexture(), first, faceCount - first, true);
    }

    private static int slotSize(int face, int viewportWidth, int viewportHeight) {
        final int i = face * PortalDrawer.FLOATS_PER_FACE;
        float largest = 0.0F;
        for (int corner = 0; corner < 4; corner++) {
            final int c = i + corner * 4;
            frame.modelViewProjection.transform(projected.set(faces[c], faces[c + 1], faces[c + 2], 1.0F));
            if (projected.w <= 1.0E-4F) return PortalDrawer.MAX_SLOT;
            screenX[corner] = projected.x / projected.w * 0.5F * viewportWidth;
            screenY[corner] = projected.y / projected.w * 0.5F * viewportHeight;
        }
        for (int corner = 0; corner < 4; corner++) {
            final int next = (corner + 1) & 3;
            largest = Math.max(largest, Math.max(Math.abs(screenX[next] - screenX[corner]), Math.abs(screenY[next] - screenY[corner])));
        }
        final int pixels = (int) Math.ceil(largest);
        final int size = pixels <= 1 ? 1 : Integer.highestOneBit(pixels - 1) << 1;
        return Math.clamp(size, PortalDrawer.MIN_SLOT, PortalDrawer.MAX_SLOT);
    }

    private static void capture(DeferredWorldRenderingPipeline pipeline, int first, int count, int tunnel, int field) {
        BatchStateGuard.beforeChange();
        final int drawFramebuffer = GLStateManager.getDrawFramebuffer();
        final int readFramebuffer = GLStateManager.getReadFramebuffer();
        pipeline.setIsMainBound(false);
        final int depth = GLStateManager.pushState(StateSet.forMask(GL11.GL_VIEWPORT_BIT | GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT));
        try {
            drawer.atlasFramebuffer().bind();
            GLStateManager.glViewport(0, 0, PortalDrawer.ATLAS_SIZE, PortalDrawer.ATLAS_SIZE);
            GLStateManager.disableDepthTest();
            GLStateManager.disableCull();
            GLStateManager.glDisable(GL11.GL_SCISSOR_TEST);
            GLStateManager.glDisable(GL11.GL_STENCIL_TEST);
            GLStateManager.glColorMask(true, true, true, true);
            drawer.drawCapture(faces, first, count, tunnel, field, frame);
        } finally {
            GLStateManager.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GLStateManager.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GLStateManager.popStateTo(depth);
            pipeline.setIsMainBound(true);
            pipeline.rebindCurrentPass();
        }
    }

    private static void drawQuads(int texture, int first, int count, boolean fromAtlas) {
        final int depth = GLStateManager.pushState(StateSet.forMask(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT));
        savedTextureMatrix.set(GLStateManager.getTextures().getTextureUnitMatrix(0));
        CapturedRenderingState.INSTANCE.pushCurrentBlockEntity();
        try {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.enableTexture();
            GLStateManager.disableLighting();
            GLStateManager.disableBlend();
            if (!batchFogged) disableFogUntilPop();
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GLStateManager.setTextureMatrix(0, IDENTITY);
            final Tessellator tess = Tessellator.instance;
            final float texel = 1.0F / PortalDrawer.ATLAS_SIZE;
            int runStart = first;
            while (runStart < first + count) {
                final int blockEntity = faceBlockEntity[runStart];
                CapturedRenderingState.INSTANCE.setCurrentBlockEntity(blockEntity);
                tess.startDrawingQuads();
                tess.setColorOpaque_F(1, 1, 1);
                tess.setBrightness(FULL_BRIGHT);
                int face = runStart;
                for (; face < first + count && faceBlockEntity[face] == blockEntity; face++) {
                    if (fromAtlas) {
                        final int i = face * PortalDrawer.FLOATS_PER_FACE;
                        final float x = faces[i + 7], y = faces[i + 11], size = faces[i + 15];
                        addQuad(tess, face, (x + 0.5F) * texel, (y + 0.5F) * texel, (x + size - 0.5F) * texel, (y + size - 0.5F) * texel);
                    } else {
                        addQuad(tess, face, 0.0F, 0.0F, 1.0F, 1.0F);
                    }
                }
                tess.draw();
                runStart = face;
            }
            if (fromAtlas) BatchStateGuard.beforeChange();
        } finally {
            CapturedRenderingState.INSTANCE.popCurrentBlockEntity();
            GLStateManager.setTextureMatrix(0, savedTextureMatrix);
            popStateTo(depth);
        }
    }

    private static void drawFarFaces() {
        final int depth = GLStateManager.pushState(StateSet.forMask(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT));
        CapturedRenderingState.INSTANCE.pushCurrentBlockEntity();
        try {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.enableTexture();
            GLStateManager.disableLighting();
            GLStateManager.disableBlend();
            disableFogUntilPop();
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, farTexture);
            final Tessellator tess = Tessellator.instance;
            final float[] f = farFaces;
            int runStart = 0;
            while (runStart < farCount) {
                final int blockEntity = farBlockEntity[runStart];
                CapturedRenderingState.INSTANCE.setCurrentBlockEntity(blockEntity);
                tess.startDrawingQuads();
                tess.setBrightness(TC_BRIGHTNESS);
                int face = runStart;
                for (; face < farCount && farBlockEntity[face] == blockEntity; face++) {
                    final int i = face * PortalDrawer.FLOATS_PER_FACE;
                    tess.setColorRGBA_F(farGray[face], farGray[face], farGray[face], 1.0F);
                    tess.addVertexWithUV(f[i], f[i + 1], f[i + 2], 1.0D, 1.0D);
                    tess.addVertexWithUV(f[i + 4], f[i + 5], f[i + 6], 1.0D, 0.0D);
                    tess.addVertexWithUV(f[i + 8], f[i + 9], f[i + 10], 0.0D, 0.0D);
                    tess.addVertexWithUV(f[i + 12], f[i + 13], f[i + 14], 0.0D, 1.0D);
                }
                tess.draw();
                runStart = face;
            }
        } finally {
            CapturedRenderingState.INSTANCE.popCurrentBlockEntity();
            popStateTo(depth);
        }
    }

    private static void disableFogUntilPop() {
        BatchStateGuard.suspend();
        try {
            GLStateManager.glDisable(GL11.GL_FOG);
        } finally {
            BatchStateGuard.resume();
        }
    }

    private static void popStateTo(int depth) {
        BatchStateGuard.suspend();
        try {
            GLStateManager.popStateTo(depth);
        } finally {
            BatchStateGuard.resume();
        }
    }

    private static void addQuad(Tessellator tess, int face, float u0, float v0, float u1, float v1) {
        final float[] f = faces;
        final int i = face * PortalDrawer.FLOATS_PER_FACE;
        final float ax = f[i + 4] - f[i], ay = f[i + 5] - f[i + 1], az = f[i + 6] - f[i + 2];
        final float bx = f[i + 12] - f[i], by = f[i + 13] - f[i + 1], bz = f[i + 14] - f[i + 2];
        final float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        final float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        tess.setNormal(nx / length, ny / length, nz / length);
        tess.addVertexWithUV(f[i], f[i + 1], f[i + 2], u0, v0);
        tess.addVertexWithUV(f[i + 4], f[i + 5], f[i + 6], u1, v0);
        tess.addVertexWithUV(f[i + 8], f[i + 9], f[i + 10], u1, v1);
        tess.addVertexWithUV(f[i + 12], f[i + 13], f[i + 14], u0, v1);
    }

    private static int textureId(ResourceLocation location) {
        final TextureManager textures = Minecraft.getMinecraft().getTextureManager();
        ITextureObject texture = textures.getTexture(location);
        if (texture == null) {
            texture = new SimpleTexture(location);
            textures.loadTexture(location, texture);
        }
        return texture.getGlTextureId();
    }

    @Override
    public boolean canWaitFor(TileEntity next) {
        return next instanceof TileEldritchNothing || next instanceof TileHole || next instanceof TileEldritchLock;
    }

    @Override
    public void flush() {
        flushFaces();
    }
}
