package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

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
