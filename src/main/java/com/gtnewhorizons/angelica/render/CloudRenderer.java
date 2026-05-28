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

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.IVertexArrayObject;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.VertexBufferType;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import jss.notfine.core.Settings;
import jss.notfine.gui.options.named.GraphicsQualityOff;
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
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class CloudRenderer implements IResourceManagerReloadListener {

    private static final byte F_TOP = 0, F_BOTTOM = 1, F_X = 2, F_Z = 3;
    private static final float[] FACTOR_VALUES = {0.7f, 1.0f, 0.9f, 0.8f}; // {top, bottom, X, Z}

    private static final int VERTEX_STRIDE = 24;
    private static final int NORMAL_OFFSET = 20;
    private static final int QUAD_BYTES = 4 * VERTEX_STRIDE;

    private static final int CELLS_PER_CHUNK = 8;

    private static final float SCROLL_SPEED = 1.0f / 256.0f;
    private static final float EDGE_OVERLAP = 0.0001f;

    private static final boolean BIG_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;

    private static final int N_POS_X = packNormalInt((byte) 127, (byte) 0, (byte) 0);
    private static final int N_NEG_X = packNormalInt((byte) -127, (byte) 0, (byte) 0);
    private static final int N_POS_Y = packNormalInt((byte) 0, (byte) 127, (byte) 0);
    private static final int N_NEG_Y = packNormalInt((byte) 0, (byte) -127, (byte) 0);
    private static final int N_POS_Z = packNormalInt((byte) 0, (byte) 0, (byte) 127);
    private static final int N_NEG_Z = packNormalInt((byte) 0, (byte) 0, (byte) -127);

    private final Minecraft mc = Minecraft.getMinecraft();
    private final ResourceLocation texture = new ResourceLocation("textures/environment/clouds.png");

    private CloudEdges edges;
    private int edgesGeneration;
    private int texW, texH;
    private int mipmapTexId = -1;
    private int cloudTexId = -1;

    private IVertexArrayObject vao;
    private int cachedVertexCount;
    private int eboId = -1;
    private int eboQuadCapacity;

    private int anchorCellX = Integer.MIN_VALUE, anchorCellZ = Integer.MIN_VALUE;
    private int anchorMarginCells = 0;
    private int cachedEdgesGeneration = Integer.MIN_VALUE;
    private boolean cachedEmitTopFace, cachedEmitBottomFace, cachedCameraInsideCloud;
    private int cachedRenderRadius = -1;
    private float cachedCloudInteriorHeight = Float.NaN;

    private int cloudMode = -1, renderDistance = -1, cloudElevation = -1, scaleMult = -1;

    private ByteBuffer emitBuffer;
    private long emitBufferAddr;
    private int emitBufferCapacity;
    private long writeAddr, writeEnd;

    private ByteBuffer sortedBuffer;
    private long sortedBufferAddr;
    private int sortedBufferCapacity;

    private ByteBuffer edgeStagingBuffer;
    private int edgeStagingCapacity;

    private byte[] quadFactor = new byte[2048];
    private int quadCount;

    private final int[] factorQuadCount = new int[4];
    private final int[] factorVertOffset = new int[4];
    private final long[] factorWriteAddr = new long[4];

    private final int[] chunkOpaqueRows = new int[10];
    private long[] globalEmit;
    private long[] globalEmitTodo;
    private int gridRows, gridCols, gridLongsPerRow, gridMinX, gridMinZ;
    private long[] runMaskScratch = new long[8];

    private GlProgram<CloudUniforms> program;
    private final Matrix4f mvpScratch = new Matrix4f();

    public CloudRenderer() {
        ((IReloadableResourceManager) mc.getResourceManager()).registerReloadListener(this);
    }

    private static CloudRenderer instance;

    public static CloudRenderer getCloudRenderer() {
        if (instance == null) instance = new CloudRenderer();
        return instance;
    }

    public static int getCloudTextureWidth() {
        return instance != null && instance.texW > 0 ? instance.texW : 256;
    }

    public static int getScaleMult() {
        return instance != null && instance.scaleMult > 0 ? instance.scaleMult : 1;
    }

    public void checkSettings() {
        final GraphicsQualityOff cloudGraphicsQuality = (GraphicsQualityOff) Settings.MODE_CLOUDS.option.getStore();
        final int cloudQualitySetting = cloudGraphicsQuality == GraphicsQualityOff.FANCY
            || cloudGraphicsQuality == GraphicsQualityOff.DEFAULT && mc.gameSettings.fancyGraphics ? 2 : 1;
        final boolean newEnabled = cloudGraphicsQuality != GraphicsQualityOff.OFF
            && mc.gameSettings.shouldRenderClouds()
            && mc.theWorld != null
            && mc.theWorld.provider.isSurfaceWorld();
        final int targetDistance = Math.max(mc.gameSettings.renderDistanceChunks, (int) Settings.RENDER_DISTANCE_CLOUDS.option.getStore());
        final int cloudScaleMult = (int) Settings.CLOUD_SCALE.option.getStore();

        if (!newEnabled
            || cloudQualitySetting != cloudMode
            || targetDistance != renderDistance
            || cloudScaleMult != scaleMult) {
            invalidateGeometry();
        }

        cloudMode = cloudQualitySetting;
        renderDistance = targetDistance;
        scaleMult = cloudScaleMult;

        cloudElevation = mc.theWorld == null ? 128 : (int) mc.theWorld.provider.getCloudHeight();
        // Allows the setting to work with RFG and similar without hardcoding.
        // The minimum height check is so stuff like Aether cloud height doesn't get messed up.
        if (cloudElevation >= 96) {
            cloudElevation = (int) Settings.CLOUD_HEIGHT.option.getStore();
        }
    }

    private void invalidateGeometry() {
        anchorCellX = Integer.MIN_VALUE;
        anchorCellZ = Integer.MIN_VALUE;
        cachedRenderRadius = -1;
        cachedEdgesGeneration = Integer.MIN_VALUE;
        cachedCloudInteriorHeight = Float.NaN;
        cachedVertexCount = 0;
    }

    private void initProgram() {
        if (program != null) return;

        final GlShader vert = ShaderLoader.loadShader(ShaderType.VERTEX, "angelica:cloud.vert", ShaderConstants.EMPTY);
        final GlShader frag = ShaderLoader.loadShader(ShaderType.FRAGMENT, "angelica:cloud.frag", ShaderConstants.EMPTY);
        try {
            program = GlProgram.builder("angelica:cloud")
                .attachShader(vert)
                .attachShader(frag)
                .link(CloudUniforms::new);
        } finally {
            vert.delete();
            frag.delete();
        }

        program.bind();
        program.getInterface().tex.setInt(0);
        program.unbind();
    }

    private void uploadFogUniforms(float partialTicks, int renderRadius, float cloudInteriorWidth) {
        final CloudUniforms u = program.getInterface();
        final boolean fogEnabled = GLStateManager.getFogMode().isEnabled();
        u.setFogEnabled(fogEnabled);
        if (fogEnabled) {
            final float start = renderDistance * 16.0f;
            final float end = renderRadius * CELLS_PER_CHUNK * cloudInteriorWidth;
            final float range = end - start;
            u.setFogParams(range != 0.0f ? -1.0f / range : 0.0f, range != 0.0f ? end / range : 1.0f);
            final Vec3 sky = mc.theWorld.getSkyColor(mc.renderViewEntity, partialTicks);
            u.setFogColor((float) sky.xCoord, (float) sky.yCoord, (float) sky.zCoord);
        }
    }

    public boolean render(int cloudTicks, float partialTicks) {
        if (mc.theWorld == null || mc.renderViewEntity == null) return false;
        if (cloudMode != 2 || scaleMult <= 0) return false;

        boolean textureSetupDone = false;
        if (edges == null) {
            if (mc.renderEngine == null) return false;
            mc.renderEngine.bindTexture(texture);
            setupCloudTexture();
            textureSetupDone = true;
            if (edges == null) return false;
        }
        if (program == null) {
            initProgram();
            if (program == null) return false;
        }

        final Entity entity = mc.renderViewEntity;
        final float cloudInteriorWidth = 12.0f * scaleMult;
        final float cloudInteriorHeight = 4.0f * scaleMult;
        final double cloudTick = cloudTicks + (double) partialTicks;

        final float cameraOffsetY = (float) (entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks);
        double cameraOffsetX = (entity.prevPosX + (entity.posX - entity.prevPosX) * partialTicks + cloudTick * 0.03D) / cloudInteriorWidth;
        double cameraOffsetZ = (entity.prevPosZ + (entity.posZ - entity.prevPosZ) * partialTicks) / cloudInteriorWidth;
        cameraOffsetX -= MathHelper.floor_double(cameraOffsetX / 2048.0D) * 2048;
        cameraOffsetZ -= MathHelper.floor_double(cameraOffsetZ / 2048.0D) * 2048;

        final float cameraRelativeY = cloudElevation - cameraOffsetY + 0.33F;
        final float cameraRelativeX = (float) (cameraOffsetX - MathHelper.floor_double(cameraOffsetX));
        final float cameraRelativeZ = (float) (cameraOffsetZ - MathHelper.floor_double(cameraOffsetZ));

        final int anchorX = MathHelper.floor_double(cameraOffsetX);
        final int anchorZ = MathHelper.floor_double(cameraOffsetZ);

        final boolean emitTopFace = cameraRelativeY + cloudInteriorHeight >= 0.0F;
        final boolean emitBottomFace = cameraRelativeY <= 0.0F;
        final boolean cameraInsideY = cameraRelativeY <= 0.0F && cameraRelativeY + cloudInteriorHeight >= 0.0F;
        final boolean cameraInsideCloud = cameraInsideY && edges.cellOpaque(anchorX, anchorZ);

        final int renderRadius = (int) Math.ceil(renderDistance * 64.0f / (CELLS_PER_CHUNK * cloudInteriorWidth));

        final boolean anchorInRange = vao != null
            && Math.abs(anchorX - anchorCellX) <= anchorMarginCells
            && Math.abs(anchorZ - anchorCellZ) <= anchorMarginCells;
        final boolean geomCacheValid = anchorInRange
            && cachedEdgesGeneration == edgesGeneration
            && cachedEmitTopFace == emitTopFace
            && cachedEmitBottomFace == emitBottomFace
            && cachedCameraInsideCloud == cameraInsideCloud
            && cachedRenderRadius == renderRadius
            && cachedCloudInteriorHeight == cloudInteriorHeight;

        if (!geomCacheValid) {
            final int marginCells = cameraInsideCloud ? 0 : Math.max(CELLS_PER_CHUNK, renderRadius * CELLS_PER_CHUNK / 4);
            rebuildGeometry(anchorX, anchorZ, renderRadius, cloudInteriorHeight, emitTopFace, emitBottomFace, cameraInsideCloud, marginCells);
            anchorCellX = anchorX;
            anchorCellZ = anchorZ;
            anchorMarginCells = marginCells;
            cachedEdgesGeneration = edgesGeneration;
            cachedEmitTopFace = emitTopFace;
            cachedEmitBottomFace = emitBottomFace;
            cachedCameraInsideCloud = cameraInsideCloud;
            cachedRenderRadius = renderRadius;
            cachedCloudInteriorHeight = cloudInteriorHeight;
        }

        if (vao == null || cachedVertexCount == 0) return true;

        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        mc.renderEngine.bindTexture(texture);
        if (!textureSetupDone) setupCloudTexture();

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

        final boolean shadersActive = IrisApi.getInstance().isShaderPackInUse();

        final float origFogStart = GLStateManager.getFogState().getStart();
        final float origFogEnd = GLStateManager.getFogState().getEnd();
        final float cloudExtentBlocks = renderRadius * CELLS_PER_CHUNK * cloudInteriorWidth;
        final boolean pushFog = GLStateManager.getFogMode().isEnabled() && GLStateManager.getFogState().getFogMode() == GL11.GL_LINEAR && origFogEnd < cloudExtentBlocks;
        if (pushFog) {
            final float ratio = cloudExtentBlocks / origFogEnd;
            GLStateManager.glFogf(GL11.GL_FOG_START, origFogStart * ratio);
            GLStateManager.glFogf(GL11.GL_FOG_END, cloudExtentBlocks);
        }

        final float anchorDriftX = (float) (anchorX - anchorCellX);
        final float anchorDriftZ = (float) (anchorZ - anchorCellZ);

        if (!shadersActive) {
            program.bind();
            final CloudUniforms u = program.getInterface();
            final Matrix4fStack mv = GLStateManager.getModelViewMatrix();
            mv.pushMatrix();
            mv.scale(cloudInteriorWidth, 1.0f, cloudInteriorWidth);
            mv.translate(-(cameraRelativeX + anchorDriftX), cameraRelativeY, -(cameraRelativeZ + anchorDriftZ));
            GLStateManager.getProjectionMatrix().mul(mv, mvpScratch);
            mv.popMatrix();
            u.mvp.set(mvpScratch);
            uploadFogUniforms(partialTicks, renderRadius, cloudInteriorWidth);

            drawAllFactors(r, g, b, false);
            program.unbind();
        } else {
            GLStateManager.glPushMatrix();
            GLStateManager.glScalef(cloudInteriorWidth, 1.0f, cloudInteriorWidth);
            GLStateManager.glTranslatef(-(cameraRelativeX + anchorDriftX), cameraRelativeY, -(cameraRelativeZ + anchorDriftZ));
            drawAllFactors(r, g, b, true);
            GLStateManager.glPopMatrix();
        }

        if (pushFog) {
            GLStateManager.glFogf(GL11.GL_FOG_START, origFogStart);
            GLStateManager.glFogf(GL11.GL_FOG_END, origFogEnd);
        }
        return true;
    }

    private void drawAllFactors(float r, float g, float b, boolean shadersActive) {
        vao.bind();
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, eboId);

        GLStateManager.disableBlend();
        GLStateManager.glDepthMask(true);
        GLStateManager.glColorMask(false, false, false, false);
        // Depth prepass: alpha must match color pass so discard threshold writes same depth coverage.
        if (shadersActive) {
            GLStateManager.glColor4f(1.0f, 1.0f, 1.0f, CloudUniforms.ALPHA);
        } else {
            program.getInterface().setColorMult(1.0f, 1.0f, 1.0f);
        }
        final int totalQuads = cachedVertexCount / 4;
        GLStateManager.glDrawElements(GL11.GL_TRIANGLES, totalQuads * 6, GL11.GL_UNSIGNED_INT, 0L);

        if (!mc.gameSettings.anaglyph) {
            GLStateManager.glColorMask(true, true, true, true);
        } else {
            switch (EntityRenderer.anaglyphField) {
                case 0 -> GLStateManager.glColorMask(false, true, true, true);
                case 1 -> GLStateManager.glColorMask(true, false, false, true);
                default -> GLStateManager.glColorMask(true, true, true, true);
            }
        }
        GLStateManager.enableBlend();
        GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GLStateManager.glDepthMask(false);

        for (int f = 0; f < 4; f++) {
            final int qc = factorQuadCount[f];
            if (qc == 0) continue;
            final float v = FACTOR_VALUES[f];
            if (shadersActive) {
                GLStateManager.glColor4f(v * r, v * g, v * b, CloudUniforms.ALPHA);
            } else {
                program.getInterface().setColorMult(v * r, v * g, v * b);
            }
            final long byteOffset = (long) (factorVertOffset[f] / 4) * 6L * 4L;
            GLStateManager.glDrawElements(GL11.GL_TRIANGLES, qc * 6, GL11.GL_UNSIGNED_INT, byteOffset);
        }

        vao.unbind();
        GLStateManager.glDepthMask(true);
        GLStateManager.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GLStateManager.disableBlend();
    }

    private void rebuildGeometry(int anchorX, int anchorZ, int renderRadius, float cloudInteriorHeight, boolean emitTopFace, boolean emitBottomFace, boolean cameraInsideCloud, int marginCells) {
        prepareEmitBuffer();

        final int marginChunks = (marginCells + CELLS_PER_CHUNK - 1) / CELLS_PER_CHUNK;
        final int chunkRange = cameraInsideCloud ? 0 : (renderRadius + marginChunks);
        final int outerLo = cameraInsideCloud ? -1 : -chunkRange;
        final int outerHi = cameraInsideCloud ? 0 : chunkRange;

        final int radiusCellsWithMargin = cameraInsideCloud ? CELLS_PER_CHUNK : (chunkRange * CELLS_PER_CHUNK);
        final int radiusCellsSq = radiusCellsWithMargin * radiusCellsWithMargin;

        final float scrollX = anchorX * SCROLL_SPEED;
        final float scrollZ = anchorZ * SCROLL_SPEED;

        final int rows = (outerHi - outerLo + 1) * CELLS_PER_CHUNK;
        final int lpr = (rows + 63) >>> 6;
        final int minX = outerLo * CELLS_PER_CHUNK;
        final int minZ = outerLo * CELLS_PER_CHUNK;
        final int gridSize = rows * lpr;
        if (emitTopFace || emitBottomFace) {
            if (globalEmit == null || globalEmit.length < gridSize) {
                globalEmit = new long[gridSize];
                globalEmitTodo = new long[gridSize];
            } else {
                java.util.Arrays.fill(globalEmit, 0, gridSize, 0L);
            }
            this.gridRows = rows;
            this.gridCols = rows;
            this.gridLongsPerRow = lpr;
            this.gridMinX = minX;
            this.gridMinZ = minZ;
        }

        final double y1 = cloudInteriorHeight;
        final double ye = y1 - EDGE_OVERLAP;

        for (int chunkX = outerLo; chunkX <= outerHi; chunkX++) {
            for (int chunkZ = outerLo; chunkZ <= outerHi; chunkZ++) {
                emitChunk(chunkX, chunkZ, anchorX, anchorZ, radiusCellsSq, cameraInsideCloud, emitTopFace, emitBottomFace, y1, ye, scrollX, scrollZ);
            }
        }

        if (emitTopFace || emitBottomFace) {
            emitTopBottomGlobalGreedy(emitTopFace, emitBottomFace, ye, scrollX, scrollZ);
        }

        final int totalBytes = (int) (writeAddr - emitBufferAddr);
        final int vertexCount = totalBytes / VERTEX_STRIDE;
        if (vertexCount == 0) {
            if (vao != null) {
                vao.delete();
                vao = null;
            }
            cachedVertexCount = 0;
            return;
        }

        sortQuadsByFactor();

        if (vao == null) {
            vao = VertexBufferType.MUTABLE_RESIZABLE.allocate(
                DefaultVertexFormat.POSITION_TEXTURE_NORMAL, GL11.GL_TRIANGLES,
                sortedBuffer, vertexCount);
        } else {
            vao.getVBO().allocate(sortedBuffer, vertexCount);
        }
        cachedVertexCount = vertexCount;
        ensureEbo(vertexCount / 4);
    }

    private void ensureEbo(int quadCount) {
        if (eboId == -1) {
            eboId = GLStateManager.glGenBuffers();
        }
        if (quadCount <= eboQuadCapacity) return;
        final java.nio.IntBuffer ib = BufferUtils.createIntBuffer(quadCount * 6);
        for (int q = 0; q < quadCount; q++) {
            final int v = q * 4;
            ib.put(v).put(v + 1).put(v + 2);
            ib.put(v).put(v + 2).put(v + 3);
        }
        ib.flip();
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, eboId);
        GLStateManager.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, ib, GL15.GL_STATIC_DRAW);
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        eboQuadCapacity = quadCount;
    }

    private void deleteEbo() {
        if (eboId != -1) {
            GLStateManager.glDeleteBuffers(eboId);
            eboId = -1;
            eboQuadCapacity = 0;
        }
    }

    private void emitChunk(int chunkX, int chunkZ, int anchorX, int anchorZ, int radiusCellsSq, boolean cameraInsideCloud, boolean emitTopFace, boolean emitBottomFace, double y1, double ye, float scrollX, float scrollZ) {
        final float chunkOffsetX = chunkX * (float) CELLS_PER_CHUNK;
        final float chunkOffsetZ = chunkZ * (float) CELLS_PER_CHUNK;
        final int baseU = chunkX * CELLS_PER_CHUNK + anchorX;
        final int baseV = chunkZ * CELLS_PER_CHUNK + anchorZ;

        final int[] opaqueRows = this.chunkOpaqueRows;
        for (int row = 0; row < 10; row++) {
            opaqueRows[row] = (int) edges.readOpaqueRowBits(baseU - 1, baseV + row - 1);
        }

        long emittableBits = 0L;
        for (int j = 0; j < CELLS_PER_CHUNK; j++) {
            int rowOpaque = (opaqueRows[j + 1] >>> 1) & 0xFF;
            while (rowOpaque != 0) {
                final int k = Integer.numberOfTrailingZeros(rowOpaque);
                rowOpaque &= rowOpaque - 1;
                final int relX = chunkX * CELLS_PER_CHUNK + k;
                final int relZ = chunkZ * CELLS_PER_CHUNK + j;
                if (relX * relX + relZ * relZ > radiusCellsSq) continue;
                if (cameraInsideCloud && (Math.abs(relX) > 1 || Math.abs(relZ) > 1)) continue;
                emittableBits |= 1L << (j * CELLS_PER_CHUNK + k);
            }
        }
        if (emittableBits == 0L) return;

        if (cameraInsideCloud) {
            long bits = emittableBits;
            while (bits != 0L) {
                final int bit = Long.numberOfTrailingZeros(bits);
                bits &= bits - 1;
                final int k = bit & 7;
                final int j = bit >>> 3;
                final double x0 = chunkOffsetX + k;
                final double x1 = chunkOffsetX + k + 1;
                final double z0 = chunkOffsetZ + j;
                final double z1 = chunkOffsetZ + j + 1;
                final double xe = x1 - EDGE_OVERLAP;
                final double zs = z1 - EDGE_OVERLAP;
                final float uC = (chunkOffsetX + k + 0.5F) * SCROLL_SPEED + scrollX;
                final float vC = (chunkOffsetZ + j + 0.5F) * SCROLL_SPEED + scrollZ;
                final float uL = (chunkOffsetX + k) * SCROLL_SPEED + scrollX;
                final float uR = (chunkOffsetX + k + 1) * SCROLL_SPEED + scrollX;
                final float vN = (chunkOffsetZ + j) * SCROLL_SPEED + scrollZ;
                final float vS = (chunkOffsetZ + j + 1) * SCROLL_SPEED + scrollZ;

                ensureEmitCapacity(6 * QUAD_BYTES);
                emitCloudQuad(x0, 0.0, z1, uL, vS, x1, 0.0, z1, uR, vS, x1, 0.0, z0, uR, vN, x0, 0.0, z0, uL, vN, F_TOP, N_POS_Y);
                emitCloudQuad(x0, ye, z0, uL, vN, x1, ye, z0, uR, vN, x1, ye, z1, uR, vS, x0, ye, z1, uL, vS, F_BOTTOM, N_NEG_Y);
                emitCloudQuad(x0, 0.0, z0, uC, vN, x0, y1, z0, uC, vN, x0, y1, z1, uC, vS, x0, 0.0, z1, uC, vS, F_X, N_POS_X);
                emitCloudQuad(xe, 0.0, z1, uC, vS, xe, y1, z1, uC, vS, xe, y1, z0, uC, vN, xe, 0.0, z0, uC, vN, F_X, N_NEG_X);
                emitCloudQuad(x0, 0.0, z0, uL, vC, x1, 0.0, z0, uR, vC, x1, y1, z0, uR, vC, x0, y1, z0, uL, vC, F_Z, N_POS_Z);
                emitCloudQuad(x0, y1, zs, uL, vC, x1, y1, zs, uR, vC, x1, 0.0, zs, uR, vC, x0, 0.0, zs, uL, vC, F_Z, N_NEG_Z);
            }
            return;
        }

        if (emitTopFace || emitBottomFace) {
            final int globalColStart = chunkX * CELLS_PER_CHUNK - gridMinX;
            final int globalRowStart = chunkZ * CELLS_PER_CHUNK - gridMinZ;
            final int wordIdx = globalColStart >>> 6;
            final int bitOff = globalColStart & 63;
            final long[] gE = this.globalEmit;
            final int lpr = this.gridLongsPerRow;
            if (bitOff <= 56) {
                for (int j = 0; j < CELLS_PER_CHUNK; j++) {
                    final long row8 = (emittableBits >>> (j * CELLS_PER_CHUNK)) & 0xFFL;
                    if (row8 == 0L) continue;
                    gE[(globalRowStart + j) * lpr + wordIdx] |= row8 << bitOff;
                }
            } else {
                final int hiShift = 64 - bitOff;
                for (int j = 0; j < CELLS_PER_CHUNK; j++) {
                    final long row8 = (emittableBits >>> (j * CELLS_PER_CHUNK)) & 0xFFL;
                    if (row8 == 0L) continue;
                    final int gRowOff = (globalRowStart + j) * lpr;
                    gE[gRowOff + wordIdx] |= row8 << bitOff;
                    gE[gRowOff + wordIdx + 1] |= row8 >>> hiShift;
                }
            }
        }

        int rowsAnd = 0x3FF;
        for (int r = 0; r < 10 && rowsAnd == 0x3FF; r++) rowsAnd &= opaqueRows[r];
        final boolean hasBoundary = rowsAnd != 0x3FF;

        long westFace = 0L, eastFace = 0L, northFace = 0L, southFace = 0L;
        if (hasBoundary) {
            long westOpaque = 0L, eastOpaque = 0L, northOpaque = 0L, southOpaque = 0L;
            for (int j = 0; j < CELLS_PER_CHUNK; j++) {
                final int rowShift = j * CELLS_PER_CHUNK;
                westOpaque |= ((long) (opaqueRows[j + 1] & 0xFF)) << rowShift;
                eastOpaque |= ((long) ((opaqueRows[j + 1] >>> 2) & 0xFF)) << rowShift;
                northOpaque |= ((long) ((opaqueRows[j] >>> 1) & 0xFF)) << rowShift;
                southOpaque |= ((long) ((opaqueRows[j + 2] >>> 1) & 0xFF)) << rowShift;
            }
            westFace = emittableBits & ~westOpaque;
            eastFace = emittableBits & ~eastOpaque;
            northFace = emittableBits & ~northOpaque;
            southFace = emittableBits & ~southOpaque;
        }

        emitZRun(westFace, chunkOffsetX, chunkOffsetZ, y1, scrollX, scrollZ, 0.0, N_NEG_X, false);
        emitZRun(eastFace, chunkOffsetX, chunkOffsetZ, y1, scrollX, scrollZ, 1.0 - EDGE_OVERLAP, N_POS_X, true);
        emitXRun(northFace, chunkOffsetX, chunkOffsetZ, y1, scrollX, scrollZ, 0.0, N_NEG_Z, false);
        emitXRun(southFace, chunkOffsetX, chunkOffsetZ, y1, scrollX, scrollZ, 1.0 - EDGE_OVERLAP, N_POS_Z, true);
    }

    private void emitZRun(long faceBits, float chunkOffsetX, float chunkOffsetZ, double y1, float scrollX, float scrollZ, double xOffset, int normalInt, boolean flip) {
        while (faceBits != 0L) {
            final int bit = Long.numberOfTrailingZeros(faceBits);
            final int k = bit & 7;
            final int j0 = bit >>> 3;
            int h = 1;
            while (j0 + h < CELLS_PER_CHUNK && ((faceBits >>> ((j0 + h) * CELLS_PER_CHUNK + k)) & 1L) != 0L) h++;
            long mask = 0L;
            for (int dh = 0; dh < h; dh++) mask |= 1L << ((j0 + dh) * CELLS_PER_CHUNK + k);
            faceBits &= ~mask;

            final double x = chunkOffsetX + k + xOffset;
            final double z0 = chunkOffsetZ + j0;
            final double z1 = chunkOffsetZ + j0 + h;
            final float uC = (chunkOffsetX + k + 0.5F) * SCROLL_SPEED + scrollX;
            final float vN = (chunkOffsetZ + j0) * SCROLL_SPEED + scrollZ;
            final float vS = (chunkOffsetZ + j0 + h) * SCROLL_SPEED + scrollZ;
            final double za = flip ? z0 : z1;
            final double zb = flip ? z1 : z0;
            final float va = flip ? vN : vS;
            final float vb = flip ? vS : vN;

            ensureEmitCapacity(QUAD_BYTES);
            emitCloudQuad(x, 0.0, za, uC, va, x, y1, za, uC, va, x, y1, zb, uC, vb, x, 0.0, zb, uC, vb, F_X, normalInt);
        }
    }

    private void emitXRun(long faceBits, float chunkOffsetX, float chunkOffsetZ, double y1, float scrollX, float scrollZ, double zOffset, int normalInt, boolean flip) {
        while (faceBits != 0L) {
            final int bit = Long.numberOfTrailingZeros(faceBits);
            final int k0 = bit & 7;
            final int j = bit >>> 3;
            int w = 1;
            while (k0 + w < CELLS_PER_CHUNK && ((faceBits >>> (j * CELLS_PER_CHUNK + k0 + w)) & 1L) != 0L) w++;
            faceBits &= ~(((1L << w) - 1L) << (j * CELLS_PER_CHUNK + k0));

            final double x0 = chunkOffsetX + k0;
            final double x1 = chunkOffsetX + k0 + w;
            final double z = chunkOffsetZ + j + zOffset;
            final float uL = (chunkOffsetX + k0) * SCROLL_SPEED + scrollX;
            final float uR = (chunkOffsetX + k0 + w) * SCROLL_SPEED + scrollX;
            final float vC = (chunkOffsetZ + j + 0.5F) * SCROLL_SPEED + scrollZ;
            final double ya = flip ? 0.0 : y1;
            final double yb = flip ? y1 : 0.0;

            ensureEmitCapacity(QUAD_BYTES);
            emitCloudQuad(x0, ya, z, uL, vC, x1, ya, z, uR, vC, x1, yb, z, uR, vC, x0, yb, z, uL, vC, F_Z, normalInt);
        }
    }

    private void emitTopBottomGlobalGreedy(boolean emitTopFace, boolean emitBottomFace, double ye, float scrollX, float scrollZ) {
        final int rows = this.gridRows;
        final int cols = this.gridCols;
        final int lpr = this.gridLongsPerRow;
        final int minX = this.gridMinX;
        final int minZ = this.gridMinZ;
        final long[] todo = this.globalEmitTodo;
        System.arraycopy(this.globalEmit, 0, todo, 0, rows * lpr);

        final int bytesPerCell = (emitTopFace ? QUAD_BYTES : 0) + (emitBottomFace ? QUAD_BYTES : 0);

        for (int row = 0; row < rows; row++) {
            final int rowOff = row * lpr;
            for (int wi = 0; wi < lpr; wi++) {
                long word = todo[rowOff + wi];
                while (word != 0L) {
                    final int bit = Long.numberOfTrailingZeros(word);
                    final int col0 = wi * 64 + bit;
                    if (col0 >= cols) break;

                    final int w = measureRowRun(todo, rowOff, lpr, cols, col0);
                    final int wiStart = col0 >>> 6;
                    final int wiLast = (col0 + w - 1) >>> 6;
                    final int spanWords = wiLast - wiStart + 1;
                    if (runMaskScratch.length < spanWords) {
                        runMaskScratch = new long[Math.max(spanWords, runMaskScratch.length * 2)];
                    }
                    final long[] mask = runMaskScratch;
                    for (int s = 0; s < spanWords; s++) {
                        final int wIdx = wiStart + s;
                        final int wordColStart = wIdx * 64;
                        final int loBit = Math.max(0, col0 - wordColStart);
                        final int hiBit = Math.min(63, col0 + w - 1 - wordColStart);
                        final int bitsInWord = hiBit - loBit + 1;
                        mask[s] = (bitsInWord == 64) ? -1L : (((1L << bitsInWord) - 1L) << loBit);
                    }

                    int h = 1;
                    extendRow:
                    while (row + h < rows) {
                        final int rOff = (row + h) * lpr;
                        for (int s = 0; s < spanWords; s++) {
                            final long m = mask[s];
                            if ((todo[rOff + wiStart + s] & m) != m) break extendRow;
                        }
                        h++;
                    }
                    for (int rr = 0; rr < h; rr++) {
                        final int rOff = (row + rr) * lpr;
                        for (int s = 0; s < spanWords; s++) {
                            todo[rOff + wiStart + s] &= ~mask[s];
                        }
                    }

                    final int relX0 = col0 + minX;
                    final int relX1 = relX0 + w;
                    final int relZ0 = row + minZ;
                    final int relZ1 = relZ0 + h;
                    final double x0 = relX0, x1 = relX1, z0 = relZ0, z1 = relZ1;
                    final float uL = relX0 * SCROLL_SPEED + scrollX;
                    final float uR = relX1 * SCROLL_SPEED + scrollX;
                    final float vN = relZ0 * SCROLL_SPEED + scrollZ;
                    final float vS = relZ1 * SCROLL_SPEED + scrollZ;

                    if (bytesPerCell > 0) ensureEmitCapacity(bytesPerCell);

                    if (emitTopFace) {
                        emitCloudQuad(x0, 0.0, z0, uL, vN, x1, 0.0, z0, uR, vN, x1, 0.0, z1, uR, vS, x0, 0.0, z1, uL, vS, F_TOP, N_NEG_Y);
                    }
                    if (emitBottomFace) {
                        emitCloudQuad(x0, ye, z1, uL, vS, x1, ye, z1, uR, vS, x1, ye, z0, uR, vN, x0, ye, z0, uL, vN, F_BOTTOM, N_POS_Y);
                    }

                    word = todo[rowOff + wi];
                }
            }
        }
    }

    private static int measureRowRun(long[] arr, int rowOff, int lpr, int stride, int col0) {
        int wi = col0 >>> 6;
        final int bit = col0 & 63;
        final long highMask = -1L << bit;
        final long inv = ~arr[rowOff + wi] & highMask;
        if (inv != 0L) {
            final int zeroBit = Long.numberOfTrailingZeros(inv);
            return Math.min(zeroBit - bit, stride - col0);
        }
        int run = 64 - bit;
        wi++;
        while (wi < lpr) {
            final long w = arr[rowOff + wi];
            if (w != -1L) {
                run += Long.numberOfTrailingZeros(~w);
                return Math.min(run, stride - col0);
            }
            run += 64;
            wi++;
        }
        return Math.min(run, stride - col0);
    }

    private void prepareEmitBuffer() {
        if (emitBuffer == null) {
            emitBufferCapacity = 256 * 1024;
            emitBuffer = MemoryUtilities.memRealloc((ByteBuffer) null, emitBufferCapacity);
            emitBufferAddr = MemoryUtilities.memAddress(emitBuffer, 0);
        }
        writeAddr = emitBufferAddr;
        writeEnd = emitBufferAddr + emitBufferCapacity;
        quadCount = 0;
    }

    private void growEmitBuffer(int neededBytes) {
        final long oldBytes = writeAddr - emitBufferAddr;
        int newCap = emitBufferCapacity;
        while (newCap < neededBytes) newCap *= 2;
        emitBuffer = MemoryUtilities.memRealloc(emitBuffer, newCap);
        emitBufferAddr = MemoryUtilities.memAddress(emitBuffer, 0);
        emitBufferCapacity = newCap;
        writeAddr = emitBufferAddr + oldBytes;
        writeEnd = emitBufferAddr + newCap;
    }

    private void ensureEmitCapacity(int bytes) {
        if (writeAddr + bytes > writeEnd) {
            growEmitBuffer((int) (writeAddr - emitBufferAddr) + bytes);
        }
    }

    private void emitCloudQuad(double x0, double y0, double z0, float u0, float v0, double x1, double y1, double z1, float u1, float v1, double x2, double y2, double z2, float u2, float v2, double x3, double y3, double z3, float u3, float v3, byte factorIdx, int normalInt) {
        if (quadCount == quadFactor.length) {
            quadFactor = java.util.Arrays.copyOf(quadFactor, quadFactor.length * 2);
        }
        quadFactor[quadCount++] = factorIdx;

        long a = writeAddr;
        a = writeCloudVertex(a, x0, y0, z0, u0, v0, normalInt);
        a = writeCloudVertex(a, x1, y1, z1, u1, v1, normalInt);
        a = writeCloudVertex(a, x2, y2, z2, u2, v2, normalInt);
        a = writeCloudVertex(a, x3, y3, z3, u3, v3, normalInt);
        writeAddr = a;
    }

    private static long writeCloudVertex(long addr, double x, double y, double z, float u, float v, int normalInt) {
        MemoryUtilities.memPutFloat(addr, (float) x);
        MemoryUtilities.memPutFloat(addr + 4, (float) y);
        MemoryUtilities.memPutFloat(addr + 8, (float) z);
        MemoryUtilities.memPutFloat(addr + 12, u);
        MemoryUtilities.memPutFloat(addr + 16, v);
        MemoryUtilities.memPutInt(addr + NORMAL_OFFSET, normalInt);
        return addr + VERTEX_STRIDE;
    }

    private static int packNormalInt(byte nx, byte ny, byte nz) {
        final int x = nx & 0xFF, y = ny & 0xFF, z = nz & 0xFF;
        return BIG_ENDIAN ? (x << 24) | (y << 16) | (z << 8) : x | (y << 8) | (z << 16);
    }

    private void sortQuadsByFactor() {
        final int qc = quadCount;
        final int[] cnt = factorQuadCount;
        cnt[0] = 0;
        cnt[1] = 0;
        cnt[2] = 0;
        cnt[3] = 0;
        final byte[] qf = quadFactor;
        for (int q = 0; q < qc; q++) cnt[qf[q]]++;

        final int totalBytes = qc * QUAD_BYTES;
        if (sortedBuffer == null || sortedBufferCapacity < totalBytes) {
            int rounded = Math.max(totalBytes, 64 * 1024);
            int pow2 = Integer.highestOneBit(rounded - 1) << 1;
            if (pow2 <= 0) pow2 = rounded;
            sortedBuffer = MemoryUtilities.memRealloc(sortedBuffer, pow2);
            sortedBufferAddr = MemoryUtilities.memAddress(sortedBuffer, 0);
            sortedBufferCapacity = pow2;
        }

        final int[] off = factorVertOffset;
        off[0] = 0;
        off[1] = cnt[0] * 4;
        off[2] = (cnt[0] + cnt[1]) * 4;
        off[3] = (cnt[0] + cnt[1] + cnt[2]) * 4;

        final long[] dst = factorWriteAddr;
        dst[0] = sortedBufferAddr;
        dst[1] = dst[0] + (long) cnt[0] * QUAD_BYTES;
        dst[2] = dst[1] + (long) cnt[1] * QUAD_BYTES;
        dst[3] = dst[2] + (long) cnt[2] * QUAD_BYTES;

        final long src0 = emitBufferAddr;
        int q = 0;
        while (q < qc) {
            final int f = qf[q];
            int runEnd = q + 1;
            while (runEnd < qc && qf[runEnd] == f) runEnd++;
            final int runLen = runEnd - q;
            final long runBytes = (long) runLen * QUAD_BYTES;
            MemoryUtilities.memCopy(src0 + (long) q * QUAD_BYTES, dst[f], runBytes);
            dst[f] += runBytes;
            q = runEnd;
        }

        sortedBuffer.position(0).limit(totalBytes);
    }

    private void setupCloudTexture() {
        final int bound = GLStateManager.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        if (bound != mipmapTexId) {
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 4);
            GLStateManager.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST_MIPMAP_LINEAR);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
            mipmapTexId = bound;
        }
        if (bound != cloudTexId || edges == null) {
            extractCloudEdges();
            cloudTexId = bound;
        }
    }

    private void extractCloudEdges() {
        final int w = GLStateManager.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        final int h = GLStateManager.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        if (w <= 0 || h <= 0) return;
        final int needed = w * h * 4;
        if (edgeStagingBuffer == null || edgeStagingCapacity < needed) {
            edgeStagingBuffer = BufferUtils.createByteBuffer(needed);
            edgeStagingCapacity = needed;
        } else {
            edgeStagingBuffer.clear();
        }
        final ByteBuffer buf = edgeStagingBuffer;
        GLStateManager.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
        texW = w;
        texH = h;
        edges = new CloudEdges(w, h, buf);
        edgesGeneration++;
        invalidateGeometry();
    }

    private void reloadTextures() {
        if (mc.renderEngine == null) return;
        mc.renderEngine.bindTexture(texture);
        extractCloudEdges();
        if (vao != null) {
            vao.delete();
            vao = null;
        }
        deleteEbo();
        mipmapTexId = -1;
        cloudTexId = -1;
    }

    @Override
    public void onResourceManagerReload(IResourceManager resourceManager) {
        reloadTextures();
        if (program != null) {
            program.delete();
            program = null;
        }
    }
}
