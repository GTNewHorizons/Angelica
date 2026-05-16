package com.gtnewhorizons.angelica.mixins.early.notfine.clouds;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.IVertexArrayObject;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.VertexBufferType;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.rendering.RenderingState;
import jss.notfine.core.Settings;
import jss.notfine.gui.options.named.GraphicsQualityOff;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.IRenderHandler;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.nio.ByteBuffer;

/*
 * Vanilla MC walks every cell of the cloud texture every frame, builds quads
 * through Tessellator, uploads them, and draws. At Angelica's render
 * distances that's tens of thousands of quads per frame for something that
 * almost never visibly changes. This mixin replaces that with:
 *
 *   1. A cell-opacity bitmap, extracted once from the clouds.png alpha
 *      channel. Used for neighbor lookups + face culling.
 *
 *   2. A cached VBO with an anchor and chunk margin. Geometry is emitted in
 *      anchor-relative cell coords covering the visible radius plus a
 *      margin in every direction. Each frame the geometry is translated
 *      by (cameraCell - anchor) at draw time, so camera motion within
 *      the chunk margin doesn't dirty the VBO. Rebuild only fires when the
 *      camera leaves the margin, render distance changes, the
 *      inside-cloud state flips, or a few other params change.
 *
 *   3. Greedy meshing of top + bottom faces across the entire visible grid.
 *      Side faces use per-chunk 1-D greedy runs.
 *
 *   4. Tint applied per-draw via glColor4f to the generic COLOR vertex
 *      attribute. No tint baked into the VBO, so weather/day-night
 *      transitions don't re-upload anything.
 *
 *   5. Quads are bucket-sorted into 4 contiguous regions (one per
 *      darkening factor: top, bottom, X-side, Z-side) so each region draws
 *      in a single glDrawArrays with its own tinted glColor4f, instead of
 *      baking RGBA into every vertex.
 */
@Mixin(value = RenderGlobal.class)
public abstract class MixinRenderGlobal {
    // Cell-opacity bitmap: one bit per pixel of the cloud texture,
    // set if the pixel's alpha >= the empty threshold.
    @Unique private static int          angelica$cloudMipmapTexId = -1;
    @Unique private static long[]       angelica$cellOpaqueBits;
    @Unique private static int          angelica$cellW;
    @Unique private static int          angelica$cellH;
    @Unique private static int          angelica$cellWMask;
    @Unique private static int          angelica$cellHMask;
    @Unique private static boolean      angelica$cellWHPow2;
    @Unique private static int          angelica$cellTexId            = -1;
    @Unique private static final int    ANGELICA_CELL_EMPTY_THRESHOLD = 10;
    @Unique private static IVertexArrayObject angelica$cloudVao;
    @Unique private static int      angelica$cachedCellTexId            = Integer.MIN_VALUE;
    @Unique private static int      angelica$anchorOffsetX              = Integer.MIN_VALUE;
    @Unique private static int      angelica$anchorOffsetZ              = Integer.MIN_VALUE;
    @Unique private static int      angelica$anchorMarginCells          = 0;
    @Unique private static boolean  angelica$cachedEmitTopFace;
    @Unique private static boolean  angelica$cachedEmitBottomFace;
    @Unique private static boolean  angelica$cachedCameraInsideCloud;
    @Unique private static int      angelica$cachedRenderRadius         = -1;
    @Unique private static float    angelica$cachedCloudInteriorHeight  = -1f;
    @Unique private static int      angelica$cachedVertexCount;

    // The factor index that picks its darkening factor.
    // After emit, the buffer is bucket-sorted by this so each
    // factor's quads end up contiguous in the VBO and the draw
    // loop can issue one glDrawArrays per factor.
    @Unique private static byte[] angelica$quadFactor = new byte[2048];
    @Unique private static int    angelica$quadCount;

    // Scratch for the cross-chunk top/bottom greedy mesh. Holds the
    // per-64-bit-word mask of a run while we extend it downward. Reused
    // across runs; grown if a single run spans more longs than fit.

    @Unique private static long[] angelica$runMaskScratch = new long[8];
    // Vertex format:
    //
    // POSITION_TEXTURE_NORMAL: pos(12 bytes) + uv(8 bytes) + normal(4 bytes).
    // Vanilla MC bakes RGBA per vertex; we don't, because tint changes
    // used to dominate the profile by re-uploading the whole VBO.
    //
    // Instead, the tint is set per draw via glColor4f which GLStateManager
    // forwards to the COLOR generic vertex attribute in flushDeferredVertexAttribs.
    // With the color array disabled on this VAO, the generic
    // attribute feeds gl_Color / iris_Color in the shader.
    //
    // angelica$writeCloudVertex writes bytes directly at these offsets;
    // verifyCloudVertexFormat asserts the GTNHLib format still matches
    // before the first emit so a layout change blows up loudly instead
    // of silently corrupting geometry.
    @Unique private static final int ANGELICA_CLOUD_VERTEX_STRIDE = 24;
    @Unique private static final int ANGELICA_CLOUD_NORMAL_OFFSET = 20;

    // The four darkening factors vanilla MC uses for clouds. Indices
    // are what we store in quadFactor and what the sort + draw loops
    // iterate over.
    @Unique private static final byte       ANGELICA_FACTOR_TOP_IDX         = 0;
    @Unique private static final byte       ANGELICA_FACTOR_BOTTOM_IDX      = 1;
    @Unique private static final byte       ANGELICA_FACTOR_X_IDX           = 2;
    @Unique private static final byte       ANGELICA_FACTOR_Z_IDX           = 3;
    @Unique private static final float[]    ANGELICA_FACTOR_VALUES          = { 0.7f, 1.0f, 0.9f, 0.8f };
    @Unique private static final float      ANGELICA_CLOUD_ALPHA            = 0.8f;

    // Bucket-sort outputs, set by sortQuadsByFactor and consumed by
    // drawAllFactorGroups: count of quads per factor, first vertex of
    // each factor's region (prefix sum × 4 verts per quad).
    @Unique private static final int[] angelica$factorQuadCount  = new int[4];
    @Unique private static final int[] angelica$factorVertOffset = new int[4];
    // Scratch write pointers used during the sort copy itself.
    @Unique private static final long[] angelica$factorWriteAddr = new long[4];

    // The normal int is 4 bytes packed in a specific position-order,
    // and which int encoding matches that byte order depends on the machine.
    @Unique private static final boolean ANGELICA_BIG_ENDIAN =
        java.nio.ByteOrder.nativeOrder() == java.nio.ByteOrder.BIG_ENDIAN;

    // Emit + sort buffers.
    //
    // Going around DirectTessellator and writing bytes directly into a
    // reusable native buffer made this path noticeably cheaper than
    // going through Tessellator's per-attribute dispatch.
    //
    // emitBuffer: where the per-cell emit code writes its quads in
    // natural traversal order, with writeAddr / writeEnd tracking the
    // current cursor and capacity.
    //
    // sortedBuffer: the bucket sort copies whole quads from emitBuffer
    // into here, grouped by factor. This is what gets uploaded to the
    // VBO. Two buffers because doing the sort in place would need an
    // extra index array of the same total size; the dedicated buffer
    // is simpler and the memcpy is cheap.
    @Unique private static ByteBuffer angelica$emitBuffer;
    @Unique private static long       angelica$emitBufferAddr;
    @Unique private static int        angelica$emitBufferCapacity;
    @Unique private static long       angelica$writeAddr;
    @Unique private static long       angelica$writeEnd;
    @Unique private static boolean    angelica$formatVerified;
    @Unique private static ByteBuffer angelica$sortedBuffer;
    @Unique private static long       angelica$sortedBufferAddr;
    @Unique private static int        angelica$sortedBufferCapacity;

    // Per-chunk opacity.
    //
    // One 10-bit row per chunk-Z, holding the 10-cell window of opacity
    // for that row (8 cells of the chunk + a 1-cell border each side).
    // Built per (chunkX, chunkZ); the side/top/bottom face logic
    // shifts and ANDs out of this instead of hitting the global
    // cellOpaqueBits array 4× per cell.
    @Unique private static final int[] angelica$chunkOpaqueRows = new int[10];

    // Global top/bottom emit grid for cross-chunk greedy meshing.
    //
    // Each chunk OR's its emittable-cells bitmap into here during the
    // chunk loop; after the loop, emitTopBottomGlobalGreedy walks the
    // whole grid in one pass so a cloud run that crosses an 8-cell
    // chunk boundary collapses into a single quad instead of one per
    // chunk. Side faces don't share this...cross-chunk side meshing
    // was tried and didn't help, or I simply suck at programming.
    @Unique private static long[]   angelica$globalEmit;
    @Unique private static long[]   angelica$globalEmitTodo;
    @Unique private static int      angelica$gridStride;
    @Unique private static int      angelica$gridRows;
    @Unique private static int      angelica$gridLongsPerRow;
    @Unique private static int      angelica$gridMinX;
    @Unique private static int      angelica$gridMinZ;

    @Unique
    private static void angelica$setupCloudTexture() {
        final int bound = GLStateManager.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        if (bound != angelica$cloudMipmapTexId) {
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 4);
            GLStateManager.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST_MIPMAP_LINEAR);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            angelica$cloudMipmapTexId = bound;
        }

        if (bound != angelica$cellTexId || angelica$cellOpaqueBits == null) {
            final int w = GLStateManager.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            final int h = GLStateManager.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            if (w > 0 && h > 0) {
                final ByteBuffer buf = BufferUtils.createByteBuffer(w * h * 4);
                GLStateManager.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
                final long[] bits = new long[(w * h + 63) >>> 6];
                for (int i = 0; i < w * h; i++) {
                    if ((buf.get(i * 4 + 3) & 0xFF) >= ANGELICA_CELL_EMPTY_THRESHOLD) {
                        bits[i >>> 6] |= 1L << (i & 63);
                    }
                }
                angelica$cellOpaqueBits = bits;
                angelica$cellW = w;
                angelica$cellH = h;
                angelica$cellWMask = w - 1;
                angelica$cellHMask = h - 1;
                // Pow2 dimensions let us replace the floorMod with a single
                // AND in the hot per-cell paths.
                angelica$cellWHPow2 = (w & (w - 1)) == 0 && (h & (h - 1)) == 0;
                angelica$cellTexId = bound;
            }
        }
    }

   /**
   * Cell opacity at world cell (x, z), with toroidal wrap on both axes.
   * Returns true (opaque) if the bitmap isn't loaded yet.
   */
    @Unique
    private static boolean angelica$cellOpaque(int x, int z) {
        final long[] bits = angelica$cellOpaqueBits;
        if (bits == null) return true;
        final int w = angelica$cellW;
        final int xw, zh;
        if (angelica$cellWHPow2) {
            xw = x & angelica$cellWMask;
            zh = z & angelica$cellHMask;
        } else {
            xw = Math.floorMod(x, w);
            zh = Math.floorMod(z, angelica$cellH);
        }
        final int idx = xw + zh * w;
        return ((bits[idx >>> 6] >>> (idx & 63)) & 1L) != 0L;
    }

    /**
     * Read {@code count} consecutive opacity bits along the X axis at
     * row z, starting at world cell x0. Returns bit c = opacity at
     * column x0 + c.
     * <p>
     * The emit loop reads opacity in 10-cell rows (one chunk + border);
     * pulling them all at once as a single long is much cheaper than 10
     * separate cellOpaque calls. Falls through to at most two
     * readContigBits calls.
     */
    @Unique
    private static long angelica$readOpaqueRowBits(int x0, int z) {
        final long[] bits = angelica$cellOpaqueBits;
        if (bits == null) return (1L << 10) - 1L;
        final int cellW = angelica$cellW;
        final int zMod;
        final int x0Mod;
        if (angelica$cellWHPow2) {
            zMod  = z  & angelica$cellHMask;
            x0Mod = x0 & angelica$cellWMask;
        } else {
            zMod  = Math.floorMod(z,  angelica$cellH);
            x0Mod = Math.floorMod(x0, cellW);
        }
        if (x0Mod + 10 <= cellW) {
            return angelica$readContigBits(bits, (long) zMod * cellW + x0Mod, 10);
        }
        // Bits span the row's right edge and wrap back to column 0.
        final int colsBeforeWrap = cellW - x0Mod;
        final long firstPart  = angelica$readContigBits(bits, (long) zMod * cellW + x0Mod, colsBeforeWrap);
        final long secondPart = angelica$readContigBits(bits, (long) zMod * cellW,         10 - colsBeforeWrap);
        return firstPart | (secondPart << colsBeforeWrap);
    }

    /**
     * Read up to 64 bits from {@code bits}, starting at the absolute bit
     * index {@code startBitIdx}. May straddle two longs; we always read
     * the first one and only touch the second if we have to.
     */
    @Unique
    private static long angelica$readContigBits(long[] bits, long startBitIdx, int count) {
        final int wordIdx = (int) (startBitIdx >>> 6);
        final int bitOff  = (int) (startBitIdx & 63);
        final long mask = count == 64 ? -1L : (1L << count) - 1L;
        final long w0 = bits[wordIdx];
        if (bitOff + count <= 64) {
            return (w0 >>> bitOff) & mask;
        }
        final long w1 = bits[wordIdx + 1];
        return ((w0 >>> bitOff) | (w1 << (64 - bitOff))) & mask;
    }

    /**
     * Sanity check that GTNHLib's POSITION_TEXTURE_NORMAL still has the
     * stride we hardcoded in writeCloudVertex. If they ever add a field
     * to that format, we want a loud failure on first emit rather than
     * silently writing corrupt geometry. Runs once per session.
     */
    @Unique
    private static void angelica$verifyCloudVertexFormat() {
        if (angelica$formatVerified) return;
        final VertexFormat fmt = DefaultVertexFormat.POSITION_TEXTURE_NORMAL;
        if (fmt.getVertexSize() != ANGELICA_CLOUD_VERTEX_STRIDE) {
            throw new IllegalStateException(
                "Cloud vertex format stride mismatch: expected "
                    + ANGELICA_CLOUD_VERTEX_STRIDE + ", got " + fmt.getVertexSize()
                    + ". DefaultVertexFormat.POSITION_TEXTURE_NORMAL layout has"
                    + " changed; the hand-written vertex writer in MixinRenderGlobal"
                    + "  no longer works, let Eclipse know.");
        }
        angelica$formatVerified = true;
    }

    @Unique
    private static void angelica$prepareEmitBuffer() {
        if (angelica$emitBuffer == null) {
            angelica$emitBufferCapacity = 256 * 1024;
            angelica$emitBuffer = BufferUtils.createByteBuffer(angelica$emitBufferCapacity);
            angelica$emitBufferAddr = MemoryUtilities.memAddress(angelica$emitBuffer, 0);
        }
        angelica$writeAddr = angelica$emitBufferAddr;
        angelica$writeEnd  = angelica$emitBufferAddr + angelica$emitBufferCapacity;
        angelica$quadCount = 0;
    }

    @Unique
    private static void angelica$growEmitBuffer(int neededBytes) {
        final long oldBytes = angelica$writeAddr - angelica$emitBufferAddr;
        int newCap = angelica$emitBufferCapacity;
        while (newCap < neededBytes) newCap *= 2;
        final ByteBuffer newBuf = BufferUtils.createByteBuffer(newCap);
        final long newAddr = MemoryUtilities.memAddress(newBuf, 0);
        if (oldBytes > 0) {
            MemoryUtilities.memCopy(angelica$emitBufferAddr, newAddr, oldBytes);
        }
        angelica$emitBuffer         = newBuf;
        angelica$emitBufferAddr     = newAddr;
        angelica$emitBufferCapacity = newCap;
        angelica$writeAddr          = newAddr + oldBytes;
        angelica$writeEnd           = newAddr + newCap;
    }

    @Unique
    private static void angelica$ensureEmitCapacity(int bytes) {
        if (angelica$writeAddr + bytes > angelica$writeEnd) {
            angelica$growEmitBuffer((int) (angelica$writeAddr - angelica$emitBufferAddr) + bytes);
        }
    }

    /**
     * Write a single vertex (pos + uv + packed normal) at {@code addr}
     * and return the address of the next vertex. Direct native byte
     * writes; this lets us skip Tessellator's per-attribute dispatch.
     */
    @Unique
    private static long angelica$writeCloudVertex(
        long addr, double x, double y, double z, float u, float v, int normalInt
    ) {
        MemoryUtilities.memPutFloat(addr,      (float) x);
        MemoryUtilities.memPutFloat(addr + 4,  (float) y);
        MemoryUtilities.memPutFloat(addr + 8,  (float) z);
        MemoryUtilities.memPutFloat(addr + 12, u);
        MemoryUtilities.memPutFloat(addr + 16, v);
        MemoryUtilities.memPutInt  (addr + ANGELICA_CLOUD_NORMAL_OFFSET, normalInt);
        return addr + ANGELICA_CLOUD_VERTEX_STRIDE;
    }

    /**
     * Pack the three signed-byte normal components into a single int
     * matching the byte layout at the normal offset. The endian split
     * is so the four bytes land in (nx, ny, nz, 0) order on disk
     * regardless of host byte order.
     */
    @Unique
    private static int angelica$packNormalInt(byte nx, byte ny, byte nz) {
        final int x = nx & 0xFF;
        final int y = ny & 0xFF;
        final int z = nz & 0xFF;
        return ANGELICA_BIG_ENDIAN
            ? (x << 24) | (y << 16) | (z << 8)
            : (z << 16) | (y << 8) | x;
    }

    /**
     * Emit one quad, record its factor, then write 4 vertices sharing
     * the same packed normal.
     */
    @Unique
    private static void angelica$emitCloudQuad(
        double x0, double y0, double z0, float u0, float v0,
        double x1, double y1, double z1, float u1, float v1,
        double x2, double y2, double z2, float u2, float v2,
        double x3, double y3, double z3, float u3, float v3,
        byte factorIdx, byte nx, byte ny, byte nz
    ) {
        final int q = angelica$quadCount;
        if (q == angelica$quadFactor.length) {
            angelica$quadFactor = java.util.Arrays.copyOf(angelica$quadFactor, angelica$quadFactor.length * 2);
        }
        angelica$quadFactor[q] = factorIdx;
        angelica$quadCount = q + 1;

        final int normalInt = angelica$packNormalInt(nx, ny, nz);
        long a = angelica$writeAddr;
        a = angelica$writeCloudVertex(a, x0, y0, z0, u0, v0, normalInt);
        a = angelica$writeCloudVertex(a, x1, y1, z1, u1, v1, normalInt);
        a = angelica$writeCloudVertex(a, x2, y2, z2, u2, v2, normalInt);
        a = angelica$writeCloudVertex(a, x3, y3, z3, u3, v3, normalInt);
        angelica$writeAddr = a;
    }

    /**
     * @author jss2a98aj
     * @reason Adjust how cloud render mode is selected.
     */
    @Overwrite
    public void renderClouds(float partialTicks) {
        IRenderHandler renderer;
        if((renderer = theWorld.provider.getCloudRenderer()) != null) {
            renderer.render(partialTicks, theWorld, mc);
            return;
        }
        if(mc.theWorld.provider.isSurfaceWorld()) {
            GraphicsQualityOff cloudMode = (GraphicsQualityOff)Settings.MODE_CLOUDS.option.getStore();
            if(cloudMode == GraphicsQualityOff.FANCY || cloudMode == GraphicsQualityOff.DEFAULT && mc.gameSettings.fancyGraphics) {
                renderCloudsFancy(partialTicks);
            } else {
                renderCloudsFast(partialTicks);
            }
        }
    }

    /**
     * @author jss2a98aj
     * @reason Adjust fancy cloud render.
     * </p>
     * Flow:
     * </p>
     *   1. Set up the GL state, texture bind, and cell bitmap refresh.
     *   2. Compute cloud color, scale, scroll, camera-relative offsets
     *      and the per-frame "inside cloud" flag.
     *   3. If the cache key matches the last build, skip emit entirely
     *      and draw the existing VBO.
     *   4. Otherwise, rebuild, do emit, sort by factor, then upload.
     *   5. Draw a depth prepass followed by a color pass that issues
     *      one draw per factor with its own tinted glColor4f.
     */
    @Overwrite
    public void renderCloudsFancy(float partialTicks) {
        GLStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        renderEngine.bindTexture(locationCloudsPng);
        angelica$setupCloudTexture();

        Vec3 color = theWorld.getCloudColour(partialTicks);
        float red   = (float)color.xCoord;
        float green = (float)color.yCoord;
        float blue  = (float)color.zCoord;
        if(mc.gameSettings.anaglyph) {
            float altRed    = (red * 30.0F + green * 59.0F + blue * 11.0F) / 100.0F;
            float altGreen  = (red * 30.0F + green * 70.0F)                / 100.0F;
            float altBlue   = (red * 30.0F + blue  * 70.0F)                / 100.0F;
            red = altRed;
            green = altGreen;
            blue = altBlue;
        }
        double cloudTick = ((float)cloudTickCounter + partialTicks);

        float cloudScale = (int)Settings.CLOUD_SCALE.option.getStore();
        float cloudInteriorWidth  = 12.0F * cloudScale;
        float cloudInteriorHeight = 4.0F * cloudScale;
        float cameraOffsetY = (float)(mc.renderViewEntity.lastTickPosY + (mc.renderViewEntity.posY - mc.renderViewEntity.lastTickPosY) * (double)partialTicks);
        double cameraOffsetX = (mc.renderViewEntity.prevPosX + (mc.renderViewEntity.posX - mc.renderViewEntity.prevPosX) * (double)partialTicks + cloudTick * 0.03D) / (double)cloudInteriorWidth;
        double cameraOffsetZ = (mc.renderViewEntity.prevPosZ + (mc.renderViewEntity.posZ - mc.renderViewEntity.prevPosZ) * (double)partialTicks) / (double)cloudInteriorWidth + 0.33D;
        cameraOffsetX -= MathHelper.floor_double(cameraOffsetX / 2048.0D) * 2048;
        cameraOffsetZ -= MathHelper.floor_double(cameraOffsetZ / 2048.0D) * 2048;

        int cloudElevation = (int)theWorld.provider.getCloudHeight();
        if (cloudElevation >= 96) {
            cloudElevation = (int)Settings.CLOUD_HEIGHT.option.getStore();
        }
        float cameraRelativeY = cloudElevation - cameraOffsetY + 0.33F;
        float cameraRelativeX = (float)(cameraOffsetX - (double)MathHelper.floor_double(cameraOffsetX));
        float cameraRelativeZ = (float)(cameraOffsetZ - (double)MathHelper.floor_double(cameraOffsetZ));

        final float scrollSpeed = 0.00390625F;

        float cloudWidth = 8f;
        final int cloudTargetDistance = Math.max(mc.gameSettings.renderDistanceChunks,
            (int)Settings.RENDER_DISTANCE_CLOUDS.option.getStore());
        int renderRadius = (int)Math.ceil(cloudTargetDistance * 64.0f / (cloudWidth * cloudInteriorWidth));
        float edgeOverlap = 0.0001f;

        final boolean emitTopFace       = cameraRelativeY + cloudInteriorHeight >= 0.0F;
        final boolean emitBottomFace    = cameraRelativeY                       <= 0.0F;
        final int cellsPerChunk         = (int)cloudWidth;

        final int anchorX = MathHelper.floor_double(cameraOffsetX);
        final int anchorZ = MathHelper.floor_double(cameraOffsetZ);

        final boolean cameraInsideY = cameraRelativeY <= 0.0F
            && cameraRelativeY + cloudInteriorHeight >= 0.0F;
        final boolean cameraInsideCloud = cameraInsideY
            && angelica$cellOpaque(anchorX, anchorZ);

        final boolean anchorInRange = angelica$cloudVao != null
            && Math.abs(anchorX - angelica$anchorOffsetX) <= angelica$anchorMarginCells
            && Math.abs(anchorZ - angelica$anchorOffsetZ) <= angelica$anchorMarginCells;

        final boolean geomCacheValid = anchorInRange
            && angelica$cachedCellTexId             == angelica$cellTexId
            && angelica$cachedEmitTopFace           == emitTopFace
            && angelica$cachedEmitBottomFace        == emitBottomFace
            && angelica$cachedCameraInsideCloud     == cameraInsideCloud
            && angelica$cachedRenderRadius          == renderRadius
            && angelica$cachedCloudInteriorHeight   == cloudInteriorHeight;

        if (!geomCacheValid) {
            // Pick new anchor because camera left the current margin
            final int marginCells = cameraInsideCloud
                ? 0
                : Math.max(cellsPerChunk, renderRadius * cellsPerChunk / 4);
            final int marginChunks = (marginCells + cellsPerChunk - 1) / cellsPerChunk;
            final int chunkRange = renderRadius + marginChunks;
            final int radiusCellsWithMargin = chunkRange * cellsPerChunk;
            final int radiusCellsSq = radiusCellsWithMargin * radiusCellsWithMargin;

            final float anchorScrollingX = anchorX * scrollSpeed;
            final float anchorScrollingZ = anchorZ * scrollSpeed;

            angelica$verifyCloudVertexFormat();
            angelica$prepareEmitBuffer();
            angelica$emitCloudGeometry(chunkRange, cloudWidth, cellsPerChunk,
                radiusCellsSq, anchorX, anchorZ,
                cloudInteriorHeight,
                scrollSpeed, anchorScrollingX, anchorScrollingZ, edgeOverlap,
                emitTopFace, emitBottomFace, cameraInsideCloud);
            final int totalBytes  = (int) (angelica$writeAddr - angelica$emitBufferAddr);
            final int vertexCount = totalBytes / ANGELICA_CLOUD_VERTEX_STRIDE;
            if (vertexCount == 0) {
                if (angelica$cloudVao != null) {
                    angelica$cloudVao.delete();
                    angelica$cloudVao = null;
                }
                angelica$cachedVertexCount = 0;
            } else {
                angelica$sortQuadsByFactor();

                // MUTABLE_RESIZABLE keeps the VAO/VBO id stable across
                // re-uploads. Otherwise, driver seems to stall?
                if (angelica$cloudVao == null) {
                    angelica$cloudVao = VertexBufferType.MUTABLE_RESIZABLE.allocate(
                        DefaultVertexFormat.POSITION_TEXTURE_NORMAL,
                        GL11.GL_QUADS, angelica$sortedBuffer, vertexCount);
                } else {
                    angelica$cloudVao.getVBO().allocate(angelica$sortedBuffer, vertexCount);
                }
                angelica$cachedVertexCount = vertexCount;
            }

            angelica$cachedCellTexId            = angelica$cellTexId;
            angelica$anchorOffsetX              = anchorX;
            angelica$anchorOffsetZ              = anchorZ;
            angelica$anchorMarginCells          = marginCells;
            angelica$cachedEmitTopFace          = emitTopFace;
            angelica$cachedEmitBottomFace       = emitBottomFace;
            angelica$cachedCameraInsideCloud    = cameraInsideCloud;
            angelica$cachedRenderRadius         = renderRadius;
            angelica$cachedCloudInteriorHeight  = cloudInteriorHeight;
        }

        if (angelica$cloudVao == null) {
            return;
        }

        angelica$cloudVao.bind();

        final float anchorDriftX = (float)(anchorX - angelica$anchorOffsetX);
        final float anchorDriftZ = (float)(anchorZ - angelica$anchorOffsetZ);

        // Not sure if this works but this should make it so that clouds
        // aren't incorrectly obscured by vanilla fog if a shaderpack wants to use it
        final float origFogStart = GLStateManager.getFogState().getStart();
        final float origFogEnd   = GLStateManager.getFogState().getEnd();
        final float cloudExtentBlocks = renderRadius * cloudWidth * cloudInteriorWidth;
        final boolean pushFog = GLStateManager.getFogMode().isEnabled()
            && GLStateManager.getFogState().getFogMode() == GL11.GL_LINEAR
            && origFogEnd < cloudExtentBlocks;
        if (pushFog) {
            final float ratio = cloudExtentBlocks / origFogEnd;
            GLStateManager.glFogf(GL11.GL_FOG_START, origFogStart * ratio);
            GLStateManager.glFogf(GL11.GL_FOG_END,   cloudExtentBlocks);
        }

        GLStateManager.glPushMatrix();
        GLStateManager.glScalef(cloudInteriorWidth, 1.0F, cloudInteriorWidth);
        GLStateManager.glTranslatef(
            -(cameraRelativeX + anchorDriftX),
            cameraRelativeY,
            -(cameraRelativeZ + anchorDriftZ));

        GLStateManager.glDisable(GL11.GL_BLEND);
        GLStateManager.glColorMask(false, false, false, false);
        GLStateManager.glDepthMask(true);
        // RGB doesn't matter here because color writes are off, but alpha feeds
        // the cloud shader's discard / alpha-test path. Set it to the
        // same value the color pass will use so depth coverage matches
        // what eventually gets shaded.
        GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, ANGELICA_CLOUD_ALPHA);
        angelica$cloudVao.draw(0, angelica$cachedVertexCount);

        if (mc.gameSettings.anaglyph) {
            if (EntityRenderer.anaglyphField == 0) {
                GLStateManager.glColorMask(false, true, true, true);
            } else {
                GLStateManager.glColorMask(true, false, false, true);
            }
        } else {
            GLStateManager.glColorMask(true, true, true, true);
        }
        GLStateManager.glEnable(GL11.GL_BLEND);
        GLStateManager.glDepthMask(false);
        // Each call updates the generic COLOR vertex attribute, so the
        // same VBO renders with different tints across the four sub-ranges.
        angelica$drawAllFactorGroups(red, green, blue);
        GLStateManager.glPopMatrix();

        angelica$cloudVao.unbind();

        if (pushFog) {
            GLStateManager.glFogf(GL11.GL_FOG_START, origFogStart);
            GLStateManager.glFogf(GL11.GL_FOG_END,   origFogEnd);
        }

        GLStateManager.glDepthMask(true);
        GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GLStateManager.glDisable(GL11.GL_BLEND);
    }

    /**
     * Bucket sort the natural-order emit into 4 contiguous per-factor
     * regions inside sortedBuffer.
     * <p>
     * Pass 1: Count how many quads landed in each bucket.
     * Pass 2: For each quad, memcpy its 96-byte block into the next slot
     * of its bucket. Per-bucket write pointers (factorWriteAddr)
     * let us advance independently per bucket without a per-quad
     * prefix sum.
     * <p>
     * Done this way rather than emitting straight into 4 buffers because
     * the emit code already streams sequentially through one buffer and
     * benefits from the cache locality. One memcpy after emit ends up
     * cheaper than dirtying 4 cachelines per quad during emit.
     */
    @Unique
    private static void angelica$sortQuadsByFactor() {
        final int quadCount = angelica$quadCount;
        final int[] cnt = angelica$factorQuadCount;
        cnt[0] = 0; cnt[1] = 0; cnt[2] = 0; cnt[3] = 0;
        final byte[] qf = angelica$quadFactor;
        for (int q = 0; q < quadCount; q++) cnt[qf[q]]++;

        final int quadBytes = ANGELICA_CLOUD_VERTEX_STRIDE * 4;
        final int totalBytes = quadCount * quadBytes;

        if (angelica$sortedBuffer == null || angelica$sortedBufferCapacity < totalBytes) {
            int newCap = Math.max(totalBytes, 64 * 1024);
            int rounded = Integer.highestOneBit(newCap - 1) << 1;
            if (rounded <= 0) rounded = newCap;
            angelica$sortedBuffer = BufferUtils.createByteBuffer(rounded);
            angelica$sortedBufferAddr = MemoryUtilities.memAddress(angelica$sortedBuffer, 0);
            angelica$sortedBufferCapacity = rounded;
        }

        // Prefix-sum the counts to first-vertex index of each bucket in
        // the uploaded VBO.
        final int[] off = angelica$factorVertOffset;
        off[0] = 0;
        off[1] =  cnt[0] * 4;
        off[2] = (cnt[0] + cnt[1]) * 4;
        off[3] = (cnt[0] + cnt[1] + cnt[2]) * 4;

        final long[] dst = angelica$factorWriteAddr;
        dst[0] = angelica$sortedBufferAddr;
        dst[1] = dst[0] + (long) cnt[0] * quadBytes;
        dst[2] = dst[1] + (long) cnt[1] * quadBytes;
        dst[3] = dst[2] + (long) cnt[2] * quadBytes;

        final long src0 = angelica$emitBufferAddr;
        for (int q = 0; q < quadCount; q++) {
            final int f = qf[q];
            MemoryUtilities.memCopy(src0 + (long) q * quadBytes, dst[f], quadBytes);
            dst[f] += quadBytes;
        }

        angelica$sortedBuffer.position(0).limit(totalBytes);
    }

    /**
     * Issue the four per-factor draw calls during the color pass. Each
     * call sets the tinted color and draws the matching vertex range.
     * </p>
     * glColor4f goes through Angelica's GLStateManager and lands as a
     * generic vertex attribute write on the next draw (via
     * flushDeferredVertexAttribs). That feeds gl_Color / iris_Color in
     * the shader, so we get per-bucket color uniformity without baking
     * any color bytes into the VBO.
     */
    @Unique
    private static void angelica$drawAllFactorGroups(float tintR, float tintG, float tintB) {
        for (int f = 0; f < 4; f++) {
            final int qc = angelica$factorQuadCount[f];
            if (qc == 0) continue;
            final float v = ANGELICA_FACTOR_VALUES[f];
            GLStateManager.glColor4f(v * tintR, v * tintG, v * tintB, ANGELICA_CLOUD_ALPHA);
            angelica$cloudVao.draw(angelica$factorVertOffset[f], qc * 4);
        }
    }

    /**
     * Walk every chunk in render distance, decide which faces each cell
     * needs, and emit them into emitBuffer.
     * </p>
     * Two output paths:
     *  1. Side faces and interior faces for the inside-cloud
     *     case go straight out per-chunk with 1-D greedy runs along the
     *     run axis.
     *  2. Top + bottom faces are deferred: each chunk OR's its emittable
     *     cells into a global grid, then a single greedy pass after the
     *     chunk loop meshes the whole grid so cloud strips spanning many
     *     chunks emit as a single big quad each
     */
    @Unique
    private static void angelica$emitCloudGeometry(
        int     chunkRange,
        float   cloudWidth,
        int     cellsPerChunk,
        int     radiusCellsSq,
        int     anchorOffsetX,
        int     anchorOffsetZ,
        float   cloudInteriorHeight,
        float   scrollSpeed,
        float   cloudScrollingX,
        float   cloudScrollingZ,
        float   edgeOverlap,
        boolean emitTopFace,
        boolean emitBottomFace,
        boolean cameraInsideCloud
    ) {
        // y1 = top face Y, ye = bottom face Y.
        final double y1 = cloudInteriorHeight;
        final double ye = y1 - edgeOverlap;
        final int[] opaqueRows = angelica$chunkOpaqueRows;
        // When the camera is inside a cloud cell, all we need is a 3×3
        // cluster of cells centered on the camera.
        final int chunkLo = cameraInsideCloud ? -1 : -chunkRange;
        final int chunkHi = cameraInsideCloud ?  0 :  chunkRange;

        // Allocate / clear the global top/bottom emit grid. It holds one
        // bit per cell in the visible window, OR-accumulated by each
        // chunk that contributes to it.
        final int gridRows = (chunkHi - chunkLo + 1) * cellsPerChunk;
        final int lpr        = (gridRows + 63) >>> 6; // longs per row
        final int gridMinX   = chunkLo * cellsPerChunk;
        final int gridMinZ   = chunkLo * cellsPerChunk;
        final int gridSize   = gridRows * lpr;
        if (emitTopFace || emitBottomFace) {
            if (angelica$globalEmit    == null || angelica$globalEmit.length < gridSize) {
                angelica$globalEmit     = new long[gridSize];
                angelica$globalEmitTodo = new long[gridSize];
            } else {
                java.util.Arrays.fill(angelica$globalEmit, 0, gridSize, 0L);
            }
            angelica$gridStride       = gridRows;
            angelica$gridRows         = gridRows;
            angelica$gridLongsPerRow  = lpr;
            angelica$gridMinX         = gridMinX;
            angelica$gridMinZ         = gridMinZ;
        }

        for (int chunkX = chunkLo; chunkX <= chunkHi; ++chunkX) {
            for (int chunkZ = chunkLo; chunkZ <= chunkHi; ++chunkZ) {
                final float chunkOffsetX = (chunkX * cloudWidth);
                final float chunkOffsetZ = (chunkZ * cloudWidth);
                final int baseU = chunkX * cellsPerChunk + anchorOffsetX;
                final int baseV = chunkZ * cellsPerChunk + anchorOffsetZ;

                // Build the local 10×10 opacity grid where this chunk's 8×8
                // cells plus a 1-cell border on every side so we can
                // check neighbor opacity without spilling out into the
                // global bitset on every cell.
                //
                // opaqueRows[r] holds 10 bits where bit c = opaque at
                // chunk-local (c-1, r-1).
                for (int row = 0; row < 10; row++) {
                    opaqueRows[row] = (int) angelica$readOpaqueRowBits(baseU - 1, baseV + row - 1);
                }

                // Mask of cells in this chunk able to emit (opaque + within
                // radius circle + within 3×3 cluster when cameraInsideCloud).
                // Bit (j*8 + k) is set if cell (k, j) emits. Used for both the
                // greedy top/bottom mesh and the per-cell side/interior pass.
                long emittableBits = 0L;
                for (int j = 0; j < cellsPerChunk; j++) {
                    int rowOpaque = (opaqueRows[j + 1] >>> 1) & 0xFF;
                    while (rowOpaque != 0) {
                        final int k = Integer.numberOfTrailingZeros(rowOpaque);
                        rowOpaque &= rowOpaque - 1;
                        final int relX = chunkX * cellsPerChunk + k;
                        final int relZ = chunkZ * cellsPerChunk + j;
                        if (relX * relX + relZ * relZ > radiusCellsSq) continue;
                        if (cameraInsideCloud && (Math.abs(relX) > 1 || Math.abs(relZ) > 1)) continue;
                        emittableBits |= 1L << (j * cellsPerChunk + k);
                    }
                }

                if (emittableBits == 0L) continue;

                // Place this chunk's emittable cells into the global
                // grid. The grid is laid out as a row of 64-bit longs;
                // each 8-bit chunk row either fits inside one long
                // or straddles two longs.
                if (emitTopFace || emitBottomFace) {
                    final int globalColStart = chunkX * cellsPerChunk - gridMinX;
                    final int globalRowStart = chunkZ * cellsPerChunk - gridMinZ;
                    final int wordIdx = globalColStart >>> 6;
                    final int bitOff  = globalColStart & 63;
                    final long[] gE = angelica$globalEmit;
                    if (bitOff <= 56) {
                        for (int j = 0; j < cellsPerChunk; j++) {
                            final long row8 = (emittableBits >>> (j * cellsPerChunk)) & 0xFFL;
                            if (row8 == 0L) continue;
                            gE[(globalRowStart + j) * lpr + wordIdx] |= row8 << bitOff;
                        }
                    } else {
                        final int hiShift = 64 - bitOff;
                        for (int j = 0; j < cellsPerChunk; j++) {
                            final long row8 = (emittableBits >>> (j * cellsPerChunk)) & 0xFFL;
                            if (row8 == 0L) continue;
                            final int gRowOff = (globalRowStart + j) * lpr;
                            gE[gRowOff + wordIdx]     |= row8 << bitOff;
                            gE[gRowOff + wordIdx + 1] |= row8 >>> hiShift;
                        }
                    }
                }

                // If every cell in chunk + border is opaque,
                // skip the mask build.
                int rowsAnd = 0x3FF;
                for (int r = 0; r < 10 && rowsAnd == 0x3FF; r++) rowsAnd &= opaqueRows[r];
                final boolean hasBoundary = rowsAnd != 0x3FF;

                // Build the four side-face bitmasks.
                // A set bit at position (j*8 + k) means cell (k, j) in
                // this chunk should emit a face in that direction.
                //
                // Here lies what used to be very clever bitmasks. If someone could figure out
                // how to enable and disable specific quads rendering without dirtying the cache,
                // refer to PR #1685's previous iterations.
                long westFace = 0L, eastFace = 0L, northFace = 0L, southFace = 0L;
                if (hasBoundary) {
                    // Pack each direction's neighbor-opacity bitmap into
                    // an 8×8 long.
                    long westOpaque = 0L, eastOpaque = 0L, northOpaque = 0L, southOpaque = 0L;
                    for (int j = 0; j < cellsPerChunk; j++) {
                        final int rowShift = j * cellsPerChunk;
                        westOpaque  |= ((long) ( opaqueRows[j + 1]        & 0xFF)) << rowShift;
                        eastOpaque  |= ((long) ((opaqueRows[j + 1] >>> 2) & 0xFF)) << rowShift;
                        northOpaque |= ((long) ((opaqueRows[j]     >>> 1) & 0xFF)) << rowShift;
                        southOpaque |= ((long) ((opaqueRows[j + 2] >>> 1) & 0xFF)) << rowShift;
                    }

                    westFace  = emittableBits & ~westOpaque;
                    eastFace  = emittableBits & ~eastOpaque;
                    northFace = emittableBits & ~northOpaque;
                    southFace = emittableBits & ~southOpaque;
                }

                // Per-chunk greedy meshing of side faces.

                // -X (west) face
                while (westFace != 0L) {
                    final int bit = Long.numberOfTrailingZeros(westFace);
                    final int k = bit & 7;
                    final int j0 = bit >>> 3;
                    int h = 1;
                    while (j0 + h < cellsPerChunk
                        && ((westFace >>> ((j0 + h) * cellsPerChunk + k)) & 1L) != 0L) {
                        h++;
                    }
                    long mask = 0L;
                    for (int dh = 0; dh < h; dh++) mask |= 1L << ((j0 + dh) * cellsPerChunk + k);
                    westFace &= ~mask;

                    final double x0 = chunkOffsetX + k;
                    final double z0 = chunkOffsetZ + j0;
                    final double z1 = chunkOffsetZ + j0 + h;
                    final float cellUF = (chunkOffsetX + k + 0.5F) * scrollSpeed + cloudScrollingX;
                    final float vN = (chunkOffsetZ + j0)     * scrollSpeed + cloudScrollingZ;
                    final float vS = (chunkOffsetZ + j0 + h) * scrollSpeed + cloudScrollingZ;

                    angelica$ensureEmitCapacity(96);
                    angelica$emitCloudQuad(
                        x0, 0.0, z1, cellUF, vS,
                        x0, y1,  z1, cellUF, vS,
                        x0, y1,  z0, cellUF, vN,
                        x0, 0.0, z0, cellUF, vN,
                        ANGELICA_FACTOR_X_IDX, (byte) -127, (byte) 0, (byte) 0);
                }

                // +X (east) face
                while (eastFace != 0L) {
                    final int bit = Long.numberOfTrailingZeros(eastFace);
                    final int k = bit & 7;
                    final int j0 = bit >>> 3;
                    int h = 1;
                    while (j0 + h < cellsPerChunk
                        && ((eastFace >>> ((j0 + h) * cellsPerChunk + k)) & 1L) != 0L) {
                        h++;
                    }
                    long mask = 0L;
                    for (int dh = 0; dh < h; dh++) mask |= 1L << ((j0 + dh) * cellsPerChunk + k);
                    eastFace &= ~mask;

                    final double xe = chunkOffsetX + k + 1 - edgeOverlap;
                    final double z0 = chunkOffsetZ + j0;
                    final double z1 = chunkOffsetZ + j0 + h;
                    final float cellUF = (chunkOffsetX + k + 0.5F) * scrollSpeed + cloudScrollingX;
                    final float vN = (chunkOffsetZ + j0)     * scrollSpeed + cloudScrollingZ;
                    final float vS = (chunkOffsetZ + j0 + h) * scrollSpeed + cloudScrollingZ;

                    angelica$ensureEmitCapacity(96);
                    angelica$emitCloudQuad(
                        xe, 0.0, z0, cellUF, vN,
                        xe, y1,  z0, cellUF, vN,
                        xe, y1,  z1, cellUF, vS,
                        xe, 0.0, z1, cellUF, vS,
                        ANGELICA_FACTOR_X_IDX, (byte) 127, (byte) 0, (byte) 0);
                }

                // -Z (north) face
                while (northFace != 0L) {
                    final int bit = Long.numberOfTrailingZeros(northFace);
                    final int k0 = bit & 7;
                    final int j = bit >>> 3;
                    int w = 1;
                    while (k0 + w < cellsPerChunk
                        && ((northFace >>> (j * cellsPerChunk + k0 + w)) & 1L) != 0L) {
                        w++;
                    }
                    northFace &= ~(((1L << w) - 1L) << (j * cellsPerChunk + k0));

                    final double x0 = chunkOffsetX + k0;
                    final double x1 = chunkOffsetX + k0 + w;
                    final double z0 = chunkOffsetZ + j;
                    final float uL = (chunkOffsetX + k0)     * scrollSpeed + cloudScrollingX;
                    final float uR = (chunkOffsetX + k0 + w) * scrollSpeed + cloudScrollingX;
                    final float cellVF = (chunkOffsetZ + j + 0.5F) * scrollSpeed + cloudScrollingZ;

                    angelica$ensureEmitCapacity(96);
                    angelica$emitCloudQuad(
                        x0, y1,  z0, uL, cellVF,
                        x1, y1,  z0, uR, cellVF,
                        x1, 0.0, z0, uR, cellVF,
                        x0, 0.0, z0, uL, cellVF,
                        ANGELICA_FACTOR_Z_IDX, (byte) 0, (byte) 0, (byte) -127);
                }

                // +Z (south) face
                while (southFace != 0L) {
                    final int bit = Long.numberOfTrailingZeros(southFace);
                    final int k0 = bit & 7;
                    final int j = bit >>> 3;
                    int w = 1;
                    while (k0 + w < cellsPerChunk
                        && ((southFace >>> (j * cellsPerChunk + k0 + w)) & 1L) != 0L) {
                        w++;
                    }
                    southFace &= ~(((1L << w) - 1L) << (j * cellsPerChunk + k0));

                    final double x0 = chunkOffsetX + k0;
                    final double x1 = chunkOffsetX + k0 + w;
                    final double ze = chunkOffsetZ + j + 1 - edgeOverlap;
                    final float uL = (chunkOffsetX + k0)     * scrollSpeed + cloudScrollingX;
                    final float uR = (chunkOffsetX + k0 + w) * scrollSpeed + cloudScrollingX;
                    final float cellVF = (chunkOffsetZ + j + 0.5F) * scrollSpeed + cloudScrollingZ;

                    angelica$ensureEmitCapacity(96);
                    angelica$emitCloudQuad(
                        x0, 0.0, ze, uL, cellVF,
                        x1, 0.0, ze, uR, cellVF,
                        x1, y1,  ze, uR, cellVF,
                        x0, y1,  ze, uL, cellVF,
                        ANGELICA_FACTOR_Z_IDX, (byte) 0, (byte) 0, (byte) 127);
                }

                // Modern MC's FLAG_INSIDE_FACE....kinda. emit for 3x3 center cluster,
                // with reversed vertex order so the rasterizer's back-face culling makes
                // these visible only when the camera is inside the cell's cuboid.
                if (cameraInsideCloud) {
                    long bits = emittableBits;
                    while (bits != 0L) {
                        final int bit = Long.numberOfTrailingZeros(bits);
                        bits &= bits - 1;
                        final int k         = bit & 7;
                        final int j         = bit >>> 3;
                        final double x0     = chunkOffsetX + k;
                        final double x1     = chunkOffsetX + k + 1;
                        final double z0     = chunkOffsetZ + j;
                        final double z1     = chunkOffsetZ + j + 1;
                        final double xe     = x1 - edgeOverlap;
                        final double ze     = z1 - edgeOverlap;
                        final float cellUF  = (chunkOffsetX + k + 0.5F) * scrollSpeed + cloudScrollingX;
                        final float cellVF  = (chunkOffsetZ + j + 0.5F) * scrollSpeed + cloudScrollingZ;
                        final float uL      = (chunkOffsetX + k)        * scrollSpeed + cloudScrollingX;
                        final float uR      = (chunkOffsetX + k + 1)    * scrollSpeed + cloudScrollingX;
                        final float vN      = (chunkOffsetZ + j)        * scrollSpeed + cloudScrollingZ;
                        final float vS      = (chunkOffsetZ + j + 1)    * scrollSpeed + cloudScrollingZ;
                        angelica$ensureEmitCapacity(6 * 96); // 6 quads
                        // Interior top
                        angelica$emitCloudQuad(
                            x0, 0.0, z1, uL, vS,
                            x1, 0.0, z1, uR, vS,
                            x1, 0.0, z0, uR, vN,
                            x0, 0.0, z0, uL, vN,
                            ANGELICA_FACTOR_TOP_IDX, (byte) 0, (byte) 127, (byte) 0);
                        // Interior bottom
                        angelica$emitCloudQuad(
                            x0, ye, z0, uL, vN,
                            x1, ye, z0, uR, vN,
                            x1, ye, z1, uR, vS,
                            x0, ye, z1, uL, vS,
                            ANGELICA_FACTOR_BOTTOM_IDX, (byte) 0, (byte) -127, (byte) 0);
                        // Interior west
                        angelica$emitCloudQuad(
                            x0, 0.0, z0, cellUF, vN,
                            x0, y1,  z0, cellUF, vN,
                            x0, y1,  z1, cellUF, vS,
                            x0, 0.0, z1, cellUF, vS,
                            ANGELICA_FACTOR_X_IDX, (byte) 127, (byte) 0, (byte) 0);
                        // Interior east
                        angelica$emitCloudQuad(
                            xe, 0.0, z1, cellUF, vS,
                            xe, y1,  z1, cellUF, vS,
                            xe, y1,  z0, cellUF, vN,
                            xe, 0.0, z0, cellUF, vN,
                            ANGELICA_FACTOR_X_IDX, (byte) -127, (byte) 0, (byte) 0);
                        // Interior north
                        angelica$emitCloudQuad(
                            x0, 0.0, z0, uL, cellVF,
                            x1, 0.0, z0, uR, cellVF,
                            x1, y1,  z0, uR, cellVF,
                            x0, y1,  z0, uL, cellVF,
                            ANGELICA_FACTOR_Z_IDX, (byte) 0, (byte) 0, (byte) 127);
                        // Interior south
                        angelica$emitCloudQuad(
                            x0, y1,  ze, uL, cellVF,
                            x1, y1,  ze, uR, cellVF,
                            x1, 0.0, ze, uR, cellVF,
                            x0, 0.0, ze, uL, cellVF,
                            ANGELICA_FACTOR_Z_IDX, (byte) 0, (byte) 0, (byte) -127);
                    }
                }
            }
        }

        if (emitTopFace || emitBottomFace) {
            angelica$emitTopBottomGlobalGreedy(emitTopFace, emitBottomFace,
                ye, scrollSpeed, cloudScrollingX, cloudScrollingZ);
        }
    }

    @Unique
    private static void angelica$emitTopBottomGlobalGreedy(
        boolean emitTopFace,
        boolean emitBottomFace,
        double  ye,
        float   scrollSpeed,
        float   scrollX,
        float   scrollZ
    ) {
        final int stride  = angelica$gridStride;
        final int rows    = angelica$gridRows;
        final int lpr     = angelica$gridLongsPerRow;
        final int minX    = angelica$gridMinX;
        final int minZ    = angelica$gridMinZ;
        final long[] todo = angelica$globalEmitTodo;
        System.arraycopy(angelica$globalEmit, 0, todo, 0, rows * lpr);

        final int bytesPerQuad = 96;
        final int bytesPerCell = (emitTopFace ? bytesPerQuad : 0)
                               + (emitBottomFace ? bytesPerQuad : 0);

        for (int row = 0; row < rows; row++) {
            final int rowOff = row * lpr;
            for (int wi = 0; wi < lpr; wi++) {
                long word = todo[rowOff + wi];
                while (word != 0L) {
                    final int bit  = Long.numberOfTrailingZeros(word);
                    final int col0 = wi * 64 + bit;
                    if (col0 >= stride) break;

                    final int w = angelica$measureRowRun(todo, rowOff, lpr, stride, col0);

                    final int wiStart = col0 >>> 6;
                    final int wiLast  = (col0 + w - 1) >>> 6;
                    final int spanWords = wiLast - wiStart + 1;
                    if (angelica$runMaskScratch.length < spanWords) {
                        angelica$runMaskScratch = new long[Math.max(spanWords, angelica$runMaskScratch.length * 2)];
                    }
                    final long[] mask = angelica$runMaskScratch;
                    for (int s = 0; s < spanWords; s++) {
                        final int wIdx = wiStart + s;
                        final int wordColStart = wIdx * 64;
                        final int loBit = Math.max(0, col0 - wordColStart);
                        final int hiBit = Math.min(63, col0 + w - 1 - wordColStart);
                        final int bits = hiBit - loBit + 1;
                        mask[s] = (bits == 64) ? -1L : (((1L << bits) - 1L) << loBit);
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
                    // Claim all cells in the rectangle so they don't
                    // get emitted again by a later row's pass.
                    for (int rr = 0; rr < h; rr++) {
                        final int rOff = (row + rr) * lpr;
                        for (int s = 0; s < spanWords; s++) {
                            todo[rOff + wiStart + s] &= ~mask[s];
                        }
                    }

                    final int       relX0 = col0 + minX;
                    final int       relX1 = relX0 + w;
                    final int       relZ0 = row  + minZ;
                    final int       relZ1 = relZ0 + h;
                    final double    x0    = relX0;
                    final double    x1    = relX1;
                    final double    z0    = relZ0;
                    final double    z1    = relZ1;
                    final float     uL    = relX0 * scrollSpeed + scrollX;
                    final float     uR    = relX1 * scrollSpeed + scrollX;
                    final float     vN    = relZ0 * scrollSpeed + scrollZ;
                    final float     vS    = relZ1 * scrollSpeed + scrollZ;

                    if (bytesPerCell > 0) angelica$ensureEmitCapacity(bytesPerCell);

                    if (emitTopFace) {
                        angelica$emitCloudQuad(
                            x0, 0.0, z0, uL, vN,
                            x1, 0.0, z0, uR, vN,
                            x1, 0.0, z1, uR, vS,
                            x0, 0.0, z1, uL, vS,
                            ANGELICA_FACTOR_TOP_IDX, (byte) 0, (byte) -127, (byte) 0);
                    }
                    if (emitBottomFace) {
                        angelica$emitCloudQuad(
                            x0, ye, z1, uL, vS,
                            x1, ye, z1, uR, vS,
                            x1, ye, z0, uR, vN,
                            x0, ye, z0, uL, vN,
                            ANGELICA_FACTOR_BOTTOM_IDX, (byte) 0, (byte) 127, (byte) 0);
                    }

                    word = todo[rowOff + wi];
                }
            }
        }
    }

    @Unique
    private static int angelica$measureRowRun(long[] arr, int rowOff, int lpr, int stride, int col0) {
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

    public void renderCloudsFast(float partialTicks) {
        final float cameraOffsetY = (float)(mc.renderViewEntity.lastTickPosY + (mc.renderViewEntity.posY - mc.renderViewEntity.lastTickPosY) * (double)partialTicks);
        int fastCloudElevation = (int)theWorld.provider.getCloudHeight();
        if (fastCloudElevation >= 96) {
            fastCloudElevation = (int)Settings.CLOUD_HEIGHT.option.getStore();
        }
        final double cameraRelativeY = fastCloudElevation - cameraOffsetY + 0.33F;
        final int fastTargetDistance = Math.max(mc.gameSettings.renderDistanceChunks,
            (int)Settings.RENDER_DISTANCE_CLOUDS.option.getStore());
        final float renderRadius = fastTargetDistance * 64.0f;

        final float fastPitchRad = (mc.renderViewEntity.prevRotationPitch
            + (mc.renderViewEntity.rotationPitch - mc.renderViewEntity.prevRotationPitch) * partialTicks)
            * (float)(Math.PI / 180.0);
        final float fastHalfVFovRad = RenderingState.INSTANCE.getFov() * 0.5f * (float)(Math.PI / 180.0);
        final float fastPlaneElev = (float)Math.atan(cameraRelativeY / renderRadius);
        if ((cameraRelativeY > 0.0 && -fastPitchRad + fastHalfVFovRad < fastPlaneElev)
         || (cameraRelativeY < 0.0 && -fastPitchRad - fastHalfVFovRad > fastPlaneElev)) {
            return;
        }

        Tessellator tessellator = Tessellator.instance;
        GLStateManager.glDisable(GL11.GL_CULL_FACE);
        GLStateManager.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        renderEngine.bindTexture(locationCloudsPng);
        angelica$setupCloudTexture();

        Vec3 color = theWorld.getCloudColour(partialTicks);
        float red = (float)color.xCoord;
        float green = (float)color.yCoord;
        float blue = (float)color.zCoord;
        if(mc.gameSettings.anaglyph) {
            float altRed = (red * 30.0F + green * 59.0F + blue * 11.0F) / 100.0F;
            float altGreen = (red * 30.0F + green * 70.0F) / 100.0F;
            float altBlue = (red * 30.0F + blue * 70.0F) / 100.0F;
            red = altRed;
            green = altGreen;
            blue = altBlue;
        }
        double cloudTick = ((float)cloudTickCounter + partialTicks);
        double cameraOffsetX = mc.renderViewEntity.prevPosX + (mc.renderViewEntity.posX - mc.renderViewEntity.prevPosX) * (double)partialTicks + cloudTick * 0.03D;
        double cameraOffsetZ = mc.renderViewEntity.prevPosZ + (mc.renderViewEntity.posZ - mc.renderViewEntity.prevPosZ) * (double)partialTicks;

        final int cloudSettingScale = (int)Settings.CLOUD_SCALE.option.getStore();
        final int fastScale = 8 * cloudSettingScale;
        double uvScale = (1.0 / 256.0) / fastScale;

        final double fastTextureCycleWorld = 256.0 * fastScale;
        cameraOffsetX -= MathHelper.floor_double(cameraOffsetX / fastTextureCycleWorld) * fastTextureCycleWorld;
        cameraOffsetZ -= MathHelper.floor_double(cameraOffsetZ / fastTextureCycleWorld) * fastTextureCycleWorld;

        float uvShiftX = (float)(cameraOffsetX * uvScale);
        float uvShiftZ = (float)(cameraOffsetZ * uvScale);

        double neg = -renderRadius;

        double startXUv = neg * uvScale + uvShiftX;
        double startZUv = neg * uvScale + uvShiftZ;
        double movedXUv = (double) renderRadius * uvScale + uvShiftX;
        double movedZUv = (double) renderRadius * uvScale + uvShiftZ;

        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_F(red, green, blue, 0.8F);
        tessellator.addVertexWithUV(neg, cameraRelativeY, renderRadius, startXUv, movedZUv);
        tessellator.addVertexWithUV(renderRadius, cameraRelativeY, renderRadius, movedXUv, movedZUv);
        tessellator.addVertexWithUV(renderRadius, cameraRelativeY, neg, movedXUv, startZUv);
        tessellator.addVertexWithUV(neg, cameraRelativeY, neg, startXUv, startZUv);
        tessellator.draw();

        GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GLStateManager.glDisable(GL11.GL_BLEND);
        GLStateManager.glEnable(GL11.GL_CULL_FACE);
    }

    @Shadow @Final
    private static ResourceLocation locationCloudsPng;
    @Shadow @Final
    private TextureManager renderEngine;

    @Shadow private WorldClient theWorld;
    @Shadow private Minecraft mc;
    @Shadow private int cloudTickCounter;

}
