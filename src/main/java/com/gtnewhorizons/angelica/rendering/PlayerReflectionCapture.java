package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.QuadConverter;
import com.gtnewhorizons.angelica.glsm.states.ColorMask;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfo;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfoCache;
import lombok.Getter;
import net.coderbot.iris.gl.image.GlImage;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAlloc;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memFree;

/**
 * Skins in 1.7.10 are 64x32 while modern has 64x64 textures. We have to address it here.
 */
public final class PlayerReflectionCapture {

    public static final int VERTEX_COUNT = 288;
    public static final int FLOATS_PER_VERTEX = 5; // x, y, z, u, v

    private static final int RAW_INTS_PER_VERTEX = 8;

    private static final int VERTEX_FLAGS = VertexFlags.convertToFlags(true, false, true, false);

    private static boolean active;
    private static boolean drawnThisCapture;
    @Getter
    private static Object target;

    @Getter
    private static EntityPlayer targetEntity;

    private static int vbo;
    private static int vao;

    private static int[] rawData;
    private static float[] vertexScratch;
    private static int skin64Tex;
    private static ByteBuffer skinPixels;
    private static int lastCapturedSkin;
    private static int lastUploadedAtlas;
    private static int skinCopyWidth = 64;
    private static int skinCopyHeight = 32;
    private static ByteBuffer repackBuffer;
    private static long repackAddress;
    private static int repackCapacity;

    private PlayerReflectionCapture() {}

    public static void begin(EntityPlayer player) {
        active = false;
        drawnThisCapture = false;
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
        target = null;
        targetEntity = null;
    }

    public static boolean shouldCapture() {
        return active && !drawnThisCapture;
    }

    /**
     * Emit the 288-vertex player batch VBO.
     */
    public static void drawPlayerCapture(float[] xyzuv) {
        if (drawnThisCapture) return;
        drawnThisCapture = true;
        if (xyzuv == null || xyzuv.length < VERTEX_COUNT * FLOATS_PER_VERTEX) return;

        // Build the Tessellator layout
        if (rawData == null) rawData = new int[VERTEX_COUNT * RAW_INTS_PER_VERTEX];
        final int[] raw = rawData;
        for (int i = 0; i < VERTEX_COUNT; i++) {
            final int s = i * FLOATS_PER_VERTEX;
            final int d = i * RAW_INTS_PER_VERTEX;
            raw[d]     = Float.floatToRawIntBits(xyzuv[s]);
            raw[d + 1] = Float.floatToRawIntBits(xyzuv[s + 1]);
            raw[d + 2] = Float.floatToRawIntBits(xyzuv[s + 2]);
            raw[d + 3] = Float.floatToRawIntBits(xyzuv[s + 3]);
            raw[d + 4] = Float.floatToRawIntBits(xyzuv[s + 4]);
            raw[d + 5] = 0xFFFFFFFF;   // color
            raw[d + 6] = 0x00008000;   // normal
            raw[d + 7] = 0x00F000F0;   // light
        }

        // Pack into our own native buffer using the player model's vertex format
        final VertexFormat format = DefaultVertexFormat.ALL_FORMATS[VERTEX_FLAGS];
        ensureRepackCapacity(VERTEX_COUNT * format.getVertexSize());

        final long writePtr = format.writeToBuffer0(repackAddress, raw, VERTEX_COUNT * RAW_INTS_PER_VERTEX);
        repackBuffer.position(0);
        repackBuffer.limit((int) (writePtr - repackAddress));

        // Upload to our dedicated VBO at offset 0
        ensureVAO(format);
        GLStateManager.glBindVertexArray(vao);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, repackBuffer, GL15.GL_STREAM_DRAW);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

        // Bind a 64x64 copy of the skin as `tex`
        final int activeSkin = GLStateManager.getBoundTextureForServerState();
        final boolean haveSkin = activeSkin != 0 && activeSkin != skin64Tex;
        if (haveSkin) {
            ensureSkin64Texture();

            final GlImage atlas = GlImage.BY_NAME.get("playerAtlas_img");
            final int atlasId = atlas != null ? atlas.getId() : 0;
            final boolean skinChanged = activeSkin != lastCapturedSkin;

            if (skinChanged) {
                final TextureInfo info = TextureInfoCache.INSTANCE.getInfo(activeSkin);
                final int srcW = info.getWidth();
                final int srcH = info.getHeight();
                if (srcW > 0 && srcH > 0) {
                    final int required = srcW * srcH * 4;
                    if (skinPixels == null || skinPixels.capacity() < required) {
                        skinPixels = ByteBuffer.allocateDirect(required).order(ByteOrder.nativeOrder());
                    }
                    // The 1.7.10 model UVs only address the top 64x32 region.
                    skinCopyWidth = Math.min(64, srcW);
                    skinCopyHeight = Math.min(32, srcH);

                    skinPixels.clear();
                    GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, activeSkin);
                    GLStateManager.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, skinPixels);
                    lastCapturedSkin = activeSkin;
                }
            }

            if (skinPixels != null && (skinChanged || atlasId != lastUploadedAtlas)) {
                skinPixels.rewind();
                GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, skin64Tex);
                GLStateManager.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, skinCopyWidth, skinCopyHeight, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, skinPixels);

                if (atlas != null) {
                    skinPixels.rewind();
                    GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, atlasId);
                    GLStateManager.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, skinCopyWidth, skinCopyHeight, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, skinPixels);
                }
                lastUploadedAtlas = atlasId;
            }

            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, skin64Tex);
        }

        final ColorMask prevColor = GLStateManager.getColorMask();
        final boolean pr = prevColor.red, pg = prevColor.green, pb = prevColor.blue, pa = prevColor.alpha;
        final boolean prevDepthWrite = GLStateManager.getDepthState().isEnabled();

        GLStateManager.glColorMask(false, false, false, false);
        GLStateManager.glDepthMask(false);
        QuadConverter.drawQuadsAsTriangles(0, VERTEX_COUNT);
        GLStateManager.glDepthMask(prevDepthWrite);
        GLStateManager.glColorMask(pr, pg, pb, pa);

        if (haveSkin) {
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, activeSkin);
        }

        GLStateManager.glBindVertexArray(0);
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

    private static void ensureSkin64Texture() {
        if (skin64Tex != 0) return;
        final int prev = GLStateManager.getBoundTextureForServerState();
        skin64Tex = GLStateManager.glGenTextures();
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, skin64Tex);
        final ByteBuffer zero = ByteBuffer.allocateDirect(64 * 64 * 4);
        GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 64, 64, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, zero);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, prev);
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
