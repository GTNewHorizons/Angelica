package com.gtnewhorizons.angelica.iris;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.hooks.ImmediateExtendedAttribHandler;
import com.gtnewhorizons.angelica.rendering.items.ItemRenderListManager;
import it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap;
import net.coderbot.iris.Iris;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureMap;
import net.coderbot.iris.vertices.IrisQuadView;
import net.coderbot.iris.vertices.NormI8;
import net.coderbot.iris.vertices.NormalHelper;
import org.joml.Vector3f;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetFloat;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetInt;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutFloat;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutInt;

/**
 * Supplies per-quad {@code mc_midTexCoord} + {@code at_tangent} for immediate-mode draws so parallax/POM shader packs
 * reconstruct sprite bounds correctly.
 */
public final class ImmediateExtendedAttribs implements ImmediateExtendedAttribHandler {

    private static final int VERTEX_SIZE = 8;
    private static final int X = 0, Y = 1, Z = 2, U = 3, V = 4;

    private static final float UV_EPS = 1.0e-5f;
    private static final float POM_SUPPRESS_EPS = 1.0e-4f;

    private static final float POM_MAX_TILE_EXTENT = 0.125f;

    private static volatile boolean currentProgramWantsExt = false;

    private static final Int2BooleanOpenHashMap programWantsExtCache = new Int2BooleanOpenHashMap();

    public static void onProgramBound(int program) {
        if (program == 0) {
            currentProgramWantsExt = false;
            return;
        }
        boolean wants;
        if (programWantsExtCache.containsKey(program)) {
            wants = programWantsExtCache.get(program);
        } else {
            final int midLoc = GLStateManager.glGetAttribLocation(program, "mc_midTexCoord");
            final int tanLoc = GLStateManager.glGetAttribLocation(program, "at_tangent");
            wants = midLoc >= 0 || tanLoc >= 0;
            programWantsExtCache.put(program, wants);
        }
        currentProgramWantsExt = wants;
    }

    public static void onProgramDeleted(int program) {
        programWantsExtCache.remove(program);
    }

    public static void onShaderPackChanged() {
        currentProgramWantsExt = false;
        programWantsExtCache.clear();
        ItemRenderListManager.clearCache();
    }

    private final Vector3f normal = new Vector3f();
    private final QuadView quad = new QuadView();
    private final PackedQuadView packedQuad = new PackedQuadView();

    private boolean limitTileExtent = true;

    @Override
    public boolean wantsExtended() {
        return Iris.enabled && currentProgramWantsExt && GLStateManager.getActiveProgram() != 0;
    }

    @Override
    public boolean wantsExtendedCapture() {
        return Iris.enabled && Iris.getCurrentPack().isPresent();
    }

    @Override
    public void build(int[] rawBuffer, int vertexCount, int vertsPerPrim, int normalIntIndex, long dstAddr, int dstStride) {
        final boolean smooth = normalIntIndex >= 0;
        limitTileExtent = isAtlasNotBound();
        long ptr = dstAddr;
        for (int v = 0; v < vertexCount; v += vertsPerPrim) {
            quad.setup(rawBuffer, v, normalIntIndex);
            if (vertsPerPrim == 4) {
                writeQuadExt(quad, ptr, dstStride);
            } else {
                writeTriExt(quad, smooth, ptr, dstStride);
            }
            ptr += (long) dstStride * vertsPerPrim;
        }
    }

    @Override
    public void buildPacked(long srcBase, int stride, int posOffset, int texOffset, int normalOffset,
                            int vertexCount, int vertsPerPrim, long dstAddr, int dstStride) {
        final boolean smooth = normalOffset >= 0;
        limitTileExtent = isAtlasNotBound();
        long ptr = dstAddr;
        for (int v = 0; v < vertexCount; v += vertsPerPrim) {
            packedQuad.setup(srcBase + (long) v * stride, stride, posOffset, texOffset, normalOffset);
            if (vertsPerPrim == 4) {
                writeQuadExt(packedQuad, ptr, dstStride);
            } else {
                writeTriExt(packedQuad, smooth, ptr, dstStride);
            }
            ptr += (long) dstStride * vertsPerPrim;
        }
    }

    private void writeQuadExt(IrisQuadView quad, long dstAddr, int dstStride) {
        final float u0 = quad.u(0), u1 = quad.u(1), u2 = quad.u(2), u3 = quad.u(3);
        final float v0 = quad.v(0), v1 = quad.v(1), v2 = quad.v(2), v3 = quad.v(3);

        final float umin = min4(u0, u1, u2, u3), umax = max4(u0, u1, u2, u3);
        final float vmin = min4(v0, v1, v2, v3), vmax = max4(v0, v1, v2, v3);

        final boolean tileable = formsRectAxisQuad(u0, u1, u2, u3) && formsRectAxisQuad(v0, v1, v2, v3)
            && (!limitTileExtent || ((umax - umin) <= POM_MAX_TILE_EXTENT && (vmax - vmin) <= POM_MAX_TILE_EXTENT));

        NormalHelper.computeFaceNormal(normal, quad);
        final int tangent = NormalHelper.computeTangent(normal.x, normal.y, normal.z, quad);

        long ptr = dstAddr;
        if (!tileable) {
            for (int i = 0; i < 4; i++) {
                memPutFloat(ptr, quad.u(i) + POM_SUPPRESS_EPS);
                memPutFloat(ptr + 4, quad.v(i) + POM_SUPPRESS_EPS);
                memPutInt(ptr + 8, tangent);
                ptr += dstStride;
            }
            return;
        }

        final float midU = (umin + umax) * 0.5f;
        final float midV = (vmin + vmax) * 0.5f;
        for (int i = 0; i < 4; i++) {
            memPutFloat(ptr, midU);
            memPutFloat(ptr + 4, midV);
            memPutInt(ptr + 8, tangent);
            ptr += dstStride;
        }
    }

    private void writeTriExt(NormalSource view, boolean smooth, long dstAddr, int dstStride) {
        final float u0 = view.u(0), u1 = view.u(1), u2 = view.u(2);
        final float v0 = view.v(0), v1 = view.v(1), v2 = view.v(2);

        final float umin = Math.min(u0, Math.min(u1, u2)), umax = Math.max(u0, Math.max(u1, u2));
        final float vmin = Math.min(v0, Math.min(v1, v2)), vmax = Math.max(v0, Math.max(v1, v2));

        final boolean tileable = formsRectAxis(u0, u1, u2) && formsRectAxis(v0, v1, v2)
            && (!limitTileExtent || ((umax - umin) <= POM_MAX_TILE_EXTENT && (vmax - vmin) <= POM_MAX_TILE_EXTENT));
        if (!tileable) {
            writeTriSuppressed(view, dstAddr, dstStride);
            return;
        }

        final float midU = (umin + umax) * 0.5f;
        final float midV = (vmin + vmax) * 0.5f;

        long ptr = dstAddr;
        if (smooth) {
            for (int i = 0; i < 3; i++) {
                final int n = view.packedNormal(i);
                final int tangent = NormalHelper.computeTangentSmooth(NormI8.unpackX(n), NormI8.unpackY(n), NormI8.unpackZ(n), view);
                memPutFloat(ptr, midU);
                memPutFloat(ptr + 4, midV);
                memPutInt(ptr + 8, tangent);
                ptr += dstStride;
            }
        } else {
            NormalHelper.computeFaceNormalTri(normal, view);
            final int tangent = NormalHelper.computeTangent(normal.x, normal.y, normal.z, view);
            for (int i = 0; i < 3; i++) {
                memPutFloat(ptr, midU);
                memPutFloat(ptr + 4, midV);
                memPutInt(ptr + 8, tangent);
                ptr += dstStride;
            }
        }
    }

    private void writeTriSuppressed(NormalSource view, long dstAddr, int dstStride) {
        NormalHelper.computeFaceNormalTri(normal, view);
        final int tangent = NormalHelper.computeTangent(normal.x, normal.y, normal.z, view);
        long ptr = dstAddr;
        for (int i = 0; i < 3; i++) {
            memPutFloat(ptr, view.u(i) + POM_SUPPRESS_EPS);
            memPutFloat(ptr + 4, view.v(i) + POM_SUPPRESS_EPS);
            memPutInt(ptr + 8, tangent);
            ptr += dstStride;
        }
    }

    private static boolean formsRectAxis(float a, float b, float c) {
        final boolean ab = Math.abs(a - b) <= UV_EPS;
        final boolean ac = Math.abs(a - c) <= UV_EPS;
        final boolean bc = Math.abs(b - c) <= UV_EPS;
        return (ab || ac || bc) && !(ab && ac && bc);
    }

    private static boolean formsRectAxisQuad(float a, float b, float c, float d) {
        final float lo = min4(a, b, c, d), hi = max4(a, b, c, d);
        if (hi - lo <= UV_EPS) return false;
        return atEdge(a, lo, hi) && atEdge(b, lo, hi) && atEdge(c, lo, hi) && atEdge(d, lo, hi);
    }

    private static boolean atEdge(float x, float lo, float hi) {
        return Math.abs(x - lo) <= UV_EPS || Math.abs(x - hi) <= UV_EPS;
    }

    private static boolean isAtlasNotBound() {
        final int tex = GLStateManager.getBoundTextureForServerState(0);
        if (tex <= 0) return true;
        final Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.getTextureManager() == null) return true;
        final ITextureObject blocks = mc.getTextureManager().getTexture(TextureMap.locationBlocksTexture);
        if (blocks != null && blocks.getGlTextureId() == tex) return false;
        final ITextureObject items = mc.getTextureManager().getTexture(TextureMap.locationItemsTexture);
        return items == null || items.getGlTextureId() != tex;
    }

    private static float min4(float a, float b, float c, float d) {
        return Math.min(Math.min(a, b), Math.min(c, d));
    }

    private static float max4(float a, float b, float c, float d) {
        return Math.max(Math.max(a, b), Math.max(c, d));
    }

    private interface NormalSource extends IrisQuadView {
        int packedNormal(int i);
    }

    private static final class QuadView implements NormalSource {
        private int[] raw;
        private int base;
        private int normalIndex;

        void setup(int[] raw, int firstVertex, int normalIndex) {
            this.raw = raw;
            this.base = firstVertex;
            this.normalIndex = normalIndex;
        }

        @Override public float x(int i) { return Float.intBitsToFloat(raw[(base + i) * VERTEX_SIZE + X]); }
        @Override public float y(int i) { return Float.intBitsToFloat(raw[(base + i) * VERTEX_SIZE + Y]); }
        @Override public float z(int i) { return Float.intBitsToFloat(raw[(base + i) * VERTEX_SIZE + Z]); }
        @Override public float u(int i) { return Float.intBitsToFloat(raw[(base + i) * VERTEX_SIZE + U]); }
        @Override public float v(int i) { return Float.intBitsToFloat(raw[(base + i) * VERTEX_SIZE + V]); }
        @Override public int packedNormal(int i) { return raw[(base + i) * VERTEX_SIZE + normalIndex]; }
    }

    private static final class PackedQuadView implements NormalSource {
        private long base;
        private int stride;
        private int posOffset;
        private int texOffset;
        private int normalOffset;

        void setup(long base, int stride, int posOffset, int texOffset, int normalOffset) {
            this.base = base;
            this.stride = stride;
            this.posOffset = posOffset;
            this.texOffset = texOffset;
            this.normalOffset = normalOffset;
        }

        @Override public float x(int i) { return memGetFloat(base + (long) i * stride + posOffset); }
        @Override public float y(int i) { return memGetFloat(base + (long) i * stride + posOffset + 4); }
        @Override public float z(int i) { return memGetFloat(base + (long) i * stride + posOffset + 8); }
        @Override public float u(int i) { return memGetFloat(base + (long) i * stride + texOffset); }
        @Override public float v(int i) { return memGetFloat(base + (long) i * stride + texOffset + 4); }
        @Override public int packedNormal(int i) { return memGetInt(base + (long) i * stride + normalOffset); }
    }
}
