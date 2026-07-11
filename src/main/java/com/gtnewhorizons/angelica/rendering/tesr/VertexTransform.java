package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement;
import net.coderbot.iris.vertices.NormI8;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetInt;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.COLOR_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.DEFAULT_COLOR;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.DEFAULT_LIGHTMAP;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.LIGHT_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.NORMAL_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.TEX_X_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.TEX_Y_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.VERTEX_SIZE;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.X_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.Y_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.Z_INDEX;

public final class VertexTransform {

    private VertexTransform() {}

    private static final int PACKED_UP = NormI8.pack(0f, 1f, 0f, 0f);

    public static int[] decode(long srcAddr, VertexFormat srcFormat, int vertexCount, int drawMode) {
        final int[] out = decode(srcAddr, srcFormat, vertexCount);
        seedMissingNormals(out, vertexCount, drawMode);
        return out;
    }

    static void seedMissingNormals(int[] out, int vertexCount, int drawMode) {
        final int prim = switch (drawMode) {
            case GL11.GL_QUADS -> 4;
            case GL11.GL_TRIANGLES -> 3;
            default -> 0;
        };
        if (prim != 0) {
            outer:
            for (int p = 0; p + prim <= vertexCount; p += prim) {
                for (int v = p; v < p + prim; v++) {
                    if (out[v * VERTEX_SIZE | NORMAL_INDEX] != 0) continue outer;
                }
                final int packed = packedFaceNormal(out, p, prim);
                for (int v = p; v < p + prim; v++) {
                    out[v * VERTEX_SIZE | NORMAL_INDEX] = packed;
                }
            }
        }
        for (int v = 0; v < vertexCount; v++) {
            if (out[v * VERTEX_SIZE | NORMAL_INDEX] == 0) {
                out[v * VERTEX_SIZE | NORMAL_INDEX] = PACKED_UP;
            }
        }
    }

    private static int packedFaceNormal(int[] out, int firstVertex, int prim) {
        final int b0 = firstVertex * VERTEX_SIZE;
        final int b1 = (firstVertex + 1) * VERTEX_SIZE;
        final int b2 = (firstVertex + 2) * VERTEX_SIZE;
        final float e1x, e1y, e1z, e2x, e2y, e2z;
        if (prim == 4) {
            final int b3 = (firstVertex + 3) * VERTEX_SIZE;
            e1x = Float.intBitsToFloat(out[b2 | X_INDEX]) - Float.intBitsToFloat(out[b0 | X_INDEX]);
            e1y = Float.intBitsToFloat(out[b2 | Y_INDEX]) - Float.intBitsToFloat(out[b0 | Y_INDEX]);
            e1z = Float.intBitsToFloat(out[b2 | Z_INDEX]) - Float.intBitsToFloat(out[b0 | Z_INDEX]);
            e2x = Float.intBitsToFloat(out[b3 | X_INDEX]) - Float.intBitsToFloat(out[b1 | X_INDEX]);
            e2y = Float.intBitsToFloat(out[b3 | Y_INDEX]) - Float.intBitsToFloat(out[b1 | Y_INDEX]);
            e2z = Float.intBitsToFloat(out[b3 | Z_INDEX]) - Float.intBitsToFloat(out[b1 | Z_INDEX]);
        } else {
            final float x0 = Float.intBitsToFloat(out[b0 | X_INDEX]);
            final float y0 = Float.intBitsToFloat(out[b0 | Y_INDEX]);
            final float z0 = Float.intBitsToFloat(out[b0 | Z_INDEX]);
            e1x = Float.intBitsToFloat(out[b1 | X_INDEX]) - x0;
            e1y = Float.intBitsToFloat(out[b1 | Y_INDEX]) - y0;
            e1z = Float.intBitsToFloat(out[b1 | Z_INDEX]) - z0;
            e2x = Float.intBitsToFloat(out[b2 | X_INDEX]) - x0;
            e2y = Float.intBitsToFloat(out[b2 | Y_INDEX]) - y0;
            e2z = Float.intBitsToFloat(out[b2 | Z_INDEX]) - z0;
        }
        final float nx = e1y * e2z - e1z * e2y;
        final float ny = e1z * e2x - e1x * e2z;
        final float nz = e1x * e2y - e1y * e2x;
        final float lenSq = nx * nx + ny * ny + nz * nz;
        if (lenSq < 1.0e-12f) return PACKED_UP;
        final float invLen = (float) (1.0 / Math.sqrt(lenSq));
        return NormI8.pack(nx * invLen, ny * invLen, nz * invLen, 0f);
    }

    public static int[] decode(long srcAddr, VertexFormat srcFormat, int vertexCount) {
        final VertexFormatElement[] elems = srcFormat.elementsArray;
        final int n = elems.length;
        final int[] offsets = new int[n];
        int off = 0;
        for (int i = 0; i < n; i++) {
            offsets[i] = off;
            off += elems[i].getByteSize();
        }
        final int stride = srcFormat.getVertexSize();
        final int[] out = new int[vertexCount * VERTEX_SIZE];
        for (int v = 0; v < vertexCount; v++) {
            final long base = srcAddr + (long) v * stride;
            final int vbase = v * VERTEX_SIZE;
            out[vbase | COLOR_INDEX] = DEFAULT_COLOR;
            out[vbase | LIGHT_INDEX] = DEFAULT_LIGHTMAP;
            for (int i = 0; i < n; i++) {
                final long p = base + offsets[i];
                switch (elems[i].getUsage()) {
                    case POSITION -> {
                        out[vbase | X_INDEX] = memGetInt(p);
                        out[vbase | Y_INDEX] = memGetInt(p + 4);
                        out[vbase | Z_INDEX] = memGetInt(p + 8);
                    }
                    case PRIMARY_UV -> {
                        out[vbase | TEX_X_INDEX] = memGetInt(p);
                        out[vbase | TEX_Y_INDEX] = memGetInt(p + 4);
                    }
                    case NORMAL -> out[vbase | NORMAL_INDEX] = memGetInt(p);
                    case COLOR -> out[vbase | COLOR_INDEX] = memGetInt(p);
                    case SECONDARY_UV -> out[vbase | LIGHT_INDEX] = memGetInt(p);
                    default -> {}
                }
            }
        }
        return out;
    }

    public static long writeInstance(long destPtr, VertexFormat destFormat, TemplateBuffer template, Matrix4fc mv, Vector3f scratch, int colorABGR, int packedLight, Matrix4fc texMatrix) {
        final int[] src = template.data;
        final int[] work = template.work;
        final int vertexCount = template.vertexCount;
        for (int v = 0; v < vertexCount; v++) {
            final int vbase = v * VERTEX_SIZE;
            work[vbase | COLOR_INDEX] = mulColor(src[vbase | COLOR_INDEX], colorABGR);
            work[vbase | LIGHT_INDEX] = packedLight;
            if (texMatrix != null) {
                final float u = Float.intBitsToFloat(src[vbase | TEX_X_INDEX]);
                final float t = Float.intBitsToFloat(src[vbase | TEX_Y_INDEX]);
                work[vbase | TEX_X_INDEX] = Float.floatToRawIntBits(texMatrix.m00() * u + texMatrix.m10() * t + texMatrix.m30());
                work[vbase | TEX_Y_INDEX] = Float.floatToRawIntBits(texMatrix.m01() * u + texMatrix.m11() * t + texMatrix.m31());
            } else {
                work[vbase | TEX_X_INDEX] = src[vbase | TEX_X_INDEX];
                work[vbase | TEX_Y_INDEX] = src[vbase | TEX_Y_INDEX];
            }
        }
        return destFormat.writeToBuffer0(destPtr, work, vertexCount * VERTEX_SIZE, mv, scratch);
    }

    static int mulColor(int a, int b) {
        if (a == -1) return b;
        if (b == -1) return a;
        return mul8(a, b, 0) | mul8(a, b, 8) | mul8(a, b, 16) | mul8(a, b, 24);
    }

    private static int mul8(int a, int b, int shift) {
        final int t = ((a >>> shift) & 0xFF) * ((b >>> shift) & 0xFF) + 0x80;
        return (((t + (t >> 8)) >> 8) & 0xFF) << shift;
    }
}
