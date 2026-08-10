package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.QuadConverter;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.glsm.states.ColorMask;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfo;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfoCache;
import com.gtnewhorizons.angelica.mixins.interfaces.ModelBoxQuads;
import com.gtnewhorizons.angelica.rendering.tesr.PassRebindGate;
import lombok.Getter;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.blending.DepthColorStorage;
import net.coderbot.iris.gl.image.GlImage;
import net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.PositionTextureVertex;
import net.minecraft.client.model.TexturedQuad;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.player.EntityPlayer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAlloc;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memFree;

/**
 * Feeds the shaderpack's world-space player reflection, which expects the modern 12-box player model:
 * 288 vertices over a 64x64 skin layout. 1.7.10 has 7 boxes and a 64x32 layout, so both are synthesized here.
 */
public final class PlayerReflectionCapture {

    public static final int VERTEX_COUNT = 288;
    public static final int FLOATS_PER_VERTEX = 5; // x, y, z, u, v
    public static final int PART_COUNT = 12;
    public static final int VERTICES_PER_PART = VERTEX_COUNT / PART_COUNT;

    private static final int RAW_INTS_PER_VERTEX = 8;

    private static final int ATLAS_LAYOUT = 64;

    private static final int VERTEX_FLAGS = VertexFlags.convertToFlags(true, false, true, false);

    private static final Tracy.ZoneId Z_PLAYER_REFLECTION_FLUSH = Tracy.zoneId("playerReflectionFlush", Tracy.COLOR_IRIS);

    private static boolean active;
    private static boolean captured;
    @Getter
    private static Object target;

    @Getter
    private static EntityPlayer targetEntity;

    private static boolean pending;
    private static final Matrix4f pendingModelView = new Matrix4f();
    private static int pendingSkin;
    private static int pendingEntityId;

    private static int vbo;
    private static int vao;

    private static int[] rawData;
    private static float[] vertexScratch;
    private static int skin64Tex;
    private static int skin64Size;
    private static boolean skin64Valid;
    private static ByteBuffer skinPixels;
    private static TextureInfo lastCapturedInfo;
    private static int lastCapturedGeneration = -1;
    private static TextureInfo lastUploadedAtlasInfo;
    private static int lastUploadedAtlasGeneration = -1;
    private static int skinCopyWidth;
    private static int skinCopyHeight;
    private static ByteBuffer repackBuffer;
    private static long repackAddress;
    private static int repackCapacity;

    private static final ModelRenderer[] partOrder = new ModelRenderer[PART_COUNT];
    private static final Matrix4f partMatrix = new Matrix4f();
    private static final Vector3f partVertex = new Vector3f();
    private static final Matrix4f savedModelView = new Matrix4f();

    private PlayerReflectionCapture() {}

    public static void begin(EntityPlayer player) {
        active = false;
        captured = false;
        pending = false;
        target = null;
        targetEntity = null;
        if (player == null) return;

        final Render render = RenderManager.instance.getEntityRenderObject(player);
        if (render instanceof RendererLivingEntity living && living.mainModel instanceof ModelBiped model) {
            target = model;
            targetEntity = player;
            active = true;
        }
    }

    public static void end() {
        active = false;
        pending = false;
        target = null;
        targetEntity = null;
    }

    public static boolean shouldCapture() {
        return active && !captured;
    }

    public static void emitAndSubmit(ModelBiped model, float scale) {
        captured = true;

        final float[] data = vertexScratch();
        emitModel(model, scale, data);

        pendingModelView.set(GLStateManager.getModelViewMatrix());
        pendingSkin = GLStateManager.getBoundTextureForServerState(0);
        pendingEntityId = CapturedRenderingState.INSTANCE.getCurrentRenderedEntity();
        pending = true;
    }

    public static ModelRenderer[] partOrder(ModelBiped model, ModelRenderer[] out) {
        out[0] = model.bipedHead;
        out[1] = model.bipedHeadwear;
        out[2] = model.bipedRightArm;
        out[3] = model.bipedRightArm;
        out[4] = model.bipedLeftLeg;
        out[5] = model.bipedLeftLeg;
        out[6] = model.bipedLeftArm;
        out[7] = model.bipedLeftArm;
        out[8] = model.bipedRightLeg;
        out[9] = model.bipedRightLeg;
        out[10] = model.bipedBody;
        out[11] = model.bipedBody;
        return out;
    }

    private static void emitModel(ModelBiped model, float scale, float[] out) {
        final ModelRenderer[] parts = partOrder(model, partOrder);
        int o = 0;
        for (int i = 0; i < PART_COUNT; i++) {
            o = emitBox(parts[i], quadsOf(parts[i]), out, o, scale);
        }
    }

    private static TexturedQuad[] quadsOf(ModelRenderer part) {
        if (part == null || part.cubeList == null || part.cubeList.isEmpty()) return null;
        final ModelBox box = part.cubeList.getFirst();
        return ((ModelBoxQuads) box).angelica$getQuadList();
    }

    public static int emitBox(ModelRenderer part, TexturedQuad[] quads, float[] out, int o, float scale) {
        if (part == null || quads == null || part.isHidden || !part.showModel || part.textureWidth > ATLAS_LAYOUT || part.textureHeight > ATLAS_LAYOUT) {
            final int end = o + VERTICES_PER_PART * FLOATS_PER_VERTEX;
            Arrays.fill(out, o, end, 0.0f);
            return end;
        }

        final Matrix4f m = partMatrix
            .rotationZYX(part.rotateAngleZ, part.rotateAngleY, part.rotateAngleX)
            .setTranslation(
                part.offsetX + part.rotationPointX * scale,
                part.offsetY + part.rotationPointY * scale,
                part.offsetZ + part.rotationPointZ * scale);

        final float uScale = part.textureWidth / ATLAS_LAYOUT;
        final float vScale = part.textureHeight / ATLAS_LAYOUT;
        final Vector3f v = partVertex;
        for (int q = 0; q < 6; q++) {
            final PositionTextureVertex[] corners = quads[q].vertexPositions;
            for (int c = 0; c < 4; c++) {
                final PositionTextureVertex corner = corners[c];
                v.set((float) corner.vector3D.xCoord * scale,
                      (float) corner.vector3D.yCoord * scale,
                      (float) corner.vector3D.zCoord * scale);
                m.transformPosition(v);
                out[o++] = v.x;
                out[o++] = v.y;
                out[o++] = v.z;
                out[o++] = corner.texturePositionX * uScale;
                out[o++] = corner.texturePositionY * vScale;
            }
        }
        return o;
    }

    public static void flush() {
        if (!pending) return;
        pending = false;
        if (!Iris.enabled) return;

        final float[] xyzuv = vertexScratch;
        if (xyzuv == null || xyzuv.length < VERTEX_COUNT * FLOATS_PER_VERTEX) return;

        PassRebindGate.rebindIfDirty();
        if (!(Iris.getPipelineManager().getPipelineNullable() instanceof DeferredWorldRenderingPipeline pipeline)) return;
        if (GLStateManager.getActiveProgram() != pipeline.getActivePassProgramId()) return;

        if (Tracy.ENABLED) Tracy.beginZone(Z_PLAYER_REFLECTION_FLUSH);
        try {
            flushInner(xyzuv);
        } finally {
            if (Tracy.ENABLED) Tracy.endZone();
        }
    }

    private static void flushInner(float[] xyzuv) {
        if (rawData == null) {
            rawData = new int[VERTEX_COUNT * RAW_INTS_PER_VERTEX];
            for (int i = 0; i < VERTEX_COUNT; i++) {
                final int d = i * RAW_INTS_PER_VERTEX;
                rawData[d + 5] = 0xFFFFFFFF;   // color
                rawData[d + 6] = 0x00008000;   // normal
                rawData[d + 7] = 0x00F000F0;   // light
            }
        }
        final int[] raw = rawData;
        for (int i = 0; i < VERTEX_COUNT; i++) {
            final int s = i * FLOATS_PER_VERTEX;
            final int d = i * RAW_INTS_PER_VERTEX;
            raw[d]     = Float.floatToRawIntBits(xyzuv[s]);
            raw[d + 1] = Float.floatToRawIntBits(xyzuv[s + 1]);
            raw[d + 2] = Float.floatToRawIntBits(xyzuv[s + 2]);
            raw[d + 3] = Float.floatToRawIntBits(xyzuv[s + 3]);
            raw[d + 4] = Float.floatToRawIntBits(xyzuv[s + 4]);
        }

        final VertexFormat format = DefaultVertexFormat.ALL_FORMATS[VERTEX_FLAGS];
        ensureRepackCapacity(VERTEX_COUNT * format.getVertexSize());

        final long writePtr = format.writeToBuffer0(repackAddress, raw, VERTEX_COUNT * RAW_INTS_PER_VERTEX);
        repackBuffer.position(0);
        repackBuffer.limit((int) (writePtr - repackAddress));

        ensureVAO(format);
        GLStateManager.glBindVertexArray(vao);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, repackBuffer, GL15.GL_STREAM_DRAW);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

        final int prevUnit = GL13.GL_TEXTURE0 + GLStateManager.getActiveTextureUnitForServerState();
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        final int prevTexture = GLStateManager.getBoundTextureForServerState();

        if (pendingSkin > 0 && pendingSkin != skin64Tex) {
            final TextureInfo info = TextureInfoCache.INSTANCE.getInfo(pendingSkin);
            final int skinGeneration = info.getUploadGeneration();
            final boolean skinChanged = info != lastCapturedInfo || skinGeneration != lastCapturedGeneration;

            final GlImage atlas = GlImage.BY_NAME.get("playerAtlas_img");
            final int atlasId = atlas != null ? atlas.getId() : 0;
            final TextureInfo atlasInfo = atlasId > 0 ? TextureInfoCache.INSTANCE.getInfo(atlasId) : null;
            final int atlasGeneration = atlasInfo != null ? atlasInfo.getUploadGeneration() : 0;
            final boolean atlasChanged = atlasInfo != lastUploadedAtlasInfo || atlasGeneration != lastUploadedAtlasGeneration;

            if (skinChanged) {
                lastCapturedInfo = info;
                lastCapturedGeneration = skinGeneration;
                skinCopyWidth = 0;
                skinCopyHeight = 0;

                final int srcW = info.getWidth();
                final int srcH = info.getHeight();
                if (srcW > 0 && srcH > 0) {
                    final int required = srcW * srcH * 4;
                    if (skinPixels == null || skinPixels.capacity() < required) {
                        skinPixels = ByteBuffer.allocateDirect(required).order(ByteOrder.nativeOrder());
                    }
                    skinPixels.clear();
                    GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, pendingSkin);
                    GLStateManager.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, skinPixels);
                    skinCopyWidth = srcW;
                    skinCopyHeight = srcH;
                }
            }

            if (skinCopyWidth > 0 && (skinChanged || atlasChanged)) {
                ensureSkin64Texture(Math.max(skinCopyWidth, skinCopyHeight));

                final int prevRowLength = GLStateManager.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
                GLStateManager.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, skinCopyWidth);
                try {
                    skinPixels.rewind();
                    GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, skin64Tex);
                    GLStateManager.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, skinCopyWidth, skinCopyHeight, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, skinPixels);
                    skin64Valid = true;

                    if (atlasInfo != null) {
                        final int dstW = Math.min(atlasInfo.getWidth(), skinCopyWidth);
                        final int dstH = Math.min(atlasInfo.getHeight(), skinCopyHeight);
                        if (dstW > 0 && dstH > 0) {
                            skinPixels.rewind();
                            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, atlasId);
                            GLStateManager.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, dstW, dstH, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, skinPixels);
                        }
                    }
                } finally {
                    GLStateManager.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, prevRowLength);
                }
                lastUploadedAtlasInfo = atlasInfo;
                lastUploadedAtlasGeneration = atlasInfo != null ? atlasInfo.getUploadGeneration() : 0;
            }

            if (skin64Valid) GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, skin64Tex);
        }

        final boolean maskable = !DepthColorStorage.isDepthColorLocked();
        final ColorMask prevColor = GLStateManager.getColorMask();
        final boolean pr = prevColor.red, pg = prevColor.green, pb = prevColor.blue, pa = prevColor.alpha;
        final boolean prevDepthWrite = GLStateManager.getDepthState().isEnabled();

        final int prevEntityId = CapturedRenderingState.INSTANCE.getCurrentRenderedEntity();
        savedModelView.set(GLStateManager.getModelViewMatrix());

        CapturedRenderingState.INSTANCE.setCurrentEntity(pendingEntityId);
        GLStateManager.setModelViewMatrix(pendingModelView);
        if (maskable) {
            GLStateManager.glColorMask(false, false, false, false);
            GLStateManager.glDepthMask(false);
        }
        try {
            QuadConverter.drawQuadsAsTriangles(0, VERTEX_COUNT);
        } finally {
            if (maskable) {
                GLStateManager.glDepthMask(prevDepthWrite);
                GLStateManager.glColorMask(pr, pg, pb, pa);
            }
            GLStateManager.setModelViewMatrix(savedModelView);
            CapturedRenderingState.INSTANCE.setCurrentEntity(prevEntityId);

            GLStateManager.glBindVertexArray(0);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, prevTexture);
            GLStateManager.glActiveTexture(prevUnit);
        }
    }

    public static float[] vertexScratch() {
        if (vertexScratch == null) vertexScratch = new float[VERTEX_COUNT * FLOATS_PER_VERTEX];
        return vertexScratch;
    }

    private static void ensureVAO(VertexFormat format) {
        if (vao != 0) return;
        vbo = GLStateManager.glGenBuffers();
        vao = GLStateManager.glGenVertexArrays();
        GLStateManager.glBindVertexArray(vao);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        format.setupBufferState(0L);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GLStateManager.glBindVertexArray(0);
    }

    private static void ensureSkin64Texture(int size) {
        if (skin64Tex != 0 && skin64Size == size) return;
        if (skin64Tex == 0) skin64Tex = GLStateManager.glGenTextures();
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, skin64Tex);
        final ByteBuffer zero = ByteBuffer.allocateDirect(size * size * 4);
        GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, size, size, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, zero);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        skin64Size = size;
        skin64Valid = false;
    }

    private static void ensureRepackCapacity(int requiredBytes) {
        if (repackBuffer != null && requiredBytes <= repackCapacity) return;
        int newCapacity = Math.max(0x4000, repackCapacity);
        while (newCapacity < requiredBytes) newCapacity *= 2;
        if (repackBuffer != null) memFree(repackBuffer);
        repackBuffer = memAlloc(newCapacity);
        repackAddress = memAddress0(repackBuffer);
        repackCapacity = newCapacity;
    }
}
