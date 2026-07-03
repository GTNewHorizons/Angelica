package com.gtnewhorizons.angelica.iris;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.hooks.ImmediateExtendedAttribHandler;
import com.gtnewhorizons.angelica.rendering.items.ItemRenderListManager;
import it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap;
import net.coderbot.iris.Iris;
import net.coderbot.iris.vertices.IrisQuadView;
import net.coderbot.iris.vertices.NormalHelper;
import org.joml.Vector3f;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetFloat;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutFloat;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutInt;

/**
 * Supplies per-quad {@code mc_midTexCoord} + {@code at_tangent} for immediate-mode draws so parallax/POM shader packs
 * reconstruct sprite bounds correctly.
 */
public final class ImmediateExtendedAttribs implements ImmediateExtendedAttribHandler {

    private static final int VERTEX_SIZE = 8;
    private static final int X = 0, Y = 1, Z = 2, U = 3, V = 4;

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
            wants = GLStateManager.glGetAttribLocation(program, "mc_midTexCoord") >= 0
                || GLStateManager.glGetAttribLocation(program, "at_tangent") >= 0;
            programWantsExtCache.put(program, wants);
        }
        currentProgramWantsExt = wants;
    }
    public static void onShaderPackChanged() {
        currentProgramWantsExt = false;
        programWantsExtCache.clear();
        ItemRenderListManager.clearCache();
    }

    private final Vector3f normal = new Vector3f();
    private final QuadView quad = new QuadView();
    private final PackedQuadView packedQuad = new PackedQuadView();

    @Override
    public boolean wantsExtended() {
        return Iris.enabled && currentProgramWantsExt && GLStateManager.getActiveProgram() != 0;
    }

    @Override
    public boolean wantsExtendedCapture() {
        return Iris.enabled && Iris.getCurrentPack().isPresent();
    }

    @Override
    public void build(int[] rawBuffer, int vertexCount, long dstAddr, int dstStride) {
        long ptr = dstAddr;
        for (int v = 0; v < vertexCount; v += 4) {
            quad.setup(rawBuffer, v);
            writeQuadExt(quad, ptr, dstStride);
            ptr += (long) dstStride * 4;
        }
    }

    @Override
    public void buildPacked(long srcBase, int stride, int posOffset, int texOffset, int vertexCount, long dstAddr, int dstStride) {
        long ptr = dstAddr;
        for (int v = 0; v < vertexCount; v += 4) {
            packedQuad.setup(srcBase + (long) v * stride, stride, posOffset, texOffset);
            writeQuadExt(packedQuad, ptr, dstStride);
            ptr += (long) dstStride * 4;
        }
    }

    private void writeQuadExt(IrisQuadView quad, long dstAddr, int dstStride) {
        final float midU = (quad.u(0) + quad.u(1) + quad.u(2) + quad.u(3)) * 0.25f;
        final float midV = (quad.v(0) + quad.v(1) + quad.v(2) + quad.v(3)) * 0.25f;

        NormalHelper.computeFaceNormal(normal, quad);
        final int tangent = NormalHelper.computeTangent(normal.x, normal.y, normal.z, quad);

        long ptr = dstAddr;
        for (int i = 0; i < 4; i++) {
            memPutFloat(ptr, midU);
            memPutFloat(ptr + 4, midV);
            memPutInt(ptr + 8, tangent);
            ptr += dstStride;
        }
    }

    private static final class QuadView implements IrisQuadView {
        private int[] raw;
        private int base;

        void setup(int[] raw, int firstVertex) {
            this.raw = raw;
            this.base = firstVertex;
        }

        @Override public float x(int i) { return Float.intBitsToFloat(raw[(base + i) * VERTEX_SIZE + X]); }
        @Override public float y(int i) { return Float.intBitsToFloat(raw[(base + i) * VERTEX_SIZE + Y]); }
        @Override public float z(int i) { return Float.intBitsToFloat(raw[(base + i) * VERTEX_SIZE + Z]); }
        @Override public float u(int i) { return Float.intBitsToFloat(raw[(base + i) * VERTEX_SIZE + U]); }
        @Override public float v(int i) { return Float.intBitsToFloat(raw[(base + i) * VERTEX_SIZE + V]); }
    }

    private static final class PackedQuadView implements IrisQuadView {
        private long base;
        private int stride;
        private int posOffset;
        private int texOffset;

        void setup(long base, int stride, int posOffset, int texOffset) {
            this.base = base;
            this.stride = stride;
            this.posOffset = posOffset;
            this.texOffset = texOffset;
        }

        @Override public float x(int i) { return memGetFloat(base + (long) i * stride + posOffset); }
        @Override public float y(int i) { return memGetFloat(base + (long) i * stride + posOffset + 4); }
        @Override public float z(int i) { return memGetFloat(base + (long) i * stride + posOffset + 8); }
        @Override public float u(int i) { return memGetFloat(base + (long) i * stride + texOffset); }
        @Override public float v(int i) { return memGetFloat(base + (long) i * stride + texOffset + 4); }
    }
}
