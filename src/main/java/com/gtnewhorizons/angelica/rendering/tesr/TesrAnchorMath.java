package com.gtnewhorizons.angelica.rendering.tesr;

import org.joml.Matrix4f;

public final class TesrAnchorMath {

    public static final int ANCHOR_GRID = 16;
    public static final double REANCHOR_DISTANCE = 512.0;
    public static final float TRANSFORM_TOLERANCE = 1f / 2048;
    public static final int TEX_MATRIX_TERMS = 7;
    private static final int TEX_PRESENT = TEX_MATRIX_TERMS - 1;
    private static final float HASH_QUANT = 1f / TRANSFORM_TOLERANCE;

    private TesrAnchorMath() {}

    public static long anchorCoord(double cam) {
        return (long) Math.floor(cam / ANCHOR_GRID) * ANCHOR_GRID;
    }

    public static boolean shouldReanchor(double camX, double camY, double camZ, long anchorX, long anchorY, long anchorZ) {
        final double dx = camX - anchorX;
        final double dy = camY - anchorY;
        final double dz = camZ - anchorZ;
        return dx * dx + dy * dy + dz * dz > REANCHOR_DISTANCE * REANCHOR_DISTANCE;
    }

    public static void toAnchorRelative(Matrix4f m, double camX, double camY, double camZ, long anchorX, long anchorY, long anchorZ) {
        m.m30(m.m30() + (float) (camX - anchorX));
        m.m31(m.m31() + (float) (camY - anchorY));
        m.m32(m.m32() + (float) (camZ - anchorZ));
    }

    public static boolean close(float a, float b) {
        return Math.abs(a - b) <= TRANSFORM_TOLERANCE || Float.floatToRawIntBits(a) == Float.floatToRawIntBits(b);
    }

    public static boolean sameTransform(float[] a, int aOffset, float[] b, int bOffset) {
        for (int column = 0; column < 16; column += 4) {
            for (int row = 0; row < 3; row++) {
                if (!close(a[aOffset + column + row], b[bOffset + column + row])) return false;
            }
        }
        return true;
    }

    public static void storeTexMatrix(float[] dst, int offset, Matrix4f t) {
        if (t == null) {
            for (int i = 0; i < TEX_MATRIX_TERMS; i++) dst[offset + i] = 0f;
            return;
        }
        for (int i = 0; i < TEX_PRESENT; i++) dst[offset + i] = texTerm(t, i);
        dst[offset + TEX_PRESENT] = 1f;
    }

    public static boolean sameTexMatrix(float[] stored, int offset, Matrix4f t) {
        final boolean storedPresent = stored[offset + TEX_PRESENT] != 0f;
        if (t == null || !storedPresent) return t == null && !storedPresent;
        for (int i = 0; i < TEX_PRESENT; i++) {
            if (!close(stored[offset + i], texTerm(t, i))) return false;
        }
        return true;
    }

    private static float texTerm(Matrix4f t, int i) {
        return switch (i) {
            case 0 -> t.m00();
            case 1 -> t.m01();
            case 2 -> t.m10();
            case 3 -> t.m11();
            case 4 -> t.m30();
            default -> t.m31();
        };
    }

    public static long instanceHash(int templateIdentity, Matrix4f mA, int packedLight, int colorABGR) {
        long h = templateIdentity * 0x9E3779B97F4A7C15L;
        h = mix(h, quantize(mA.m00()));
        h = mix(h, quantize(mA.m01()));
        h = mix(h, quantize(mA.m02()));
        h = mix(h, quantize(mA.m10()));
        h = mix(h, quantize(mA.m11()));
        h = mix(h, quantize(mA.m12()));
        h = mix(h, quantize(mA.m20()));
        h = mix(h, quantize(mA.m21()));
        h = mix(h, quantize(mA.m22()));
        h = mix(h, quantize(mA.m30()));
        h = mix(h, quantize(mA.m31()));
        h = mix(h, quantize(mA.m32()));
        h = mix(h, packedLight);
        h = mix(h, colorABGR);
        return avalanche(h);
    }

    public static long texMatrixHash(Matrix4f t) {
        if (t == null) return 0L;
        long h = 0x9E3779B97F4A7C15L;
        for (int i = 0; i < TEX_PRESENT; i++) h = mix(h, quantize(texTerm(t, i)));
        return avalanche(h);
    }

    private static int quantize(float v) {
        return Math.round(v * HASH_QUANT);
    }

    private static long mix(long h, int v) {
        h ^= v;
        h *= 0xFF51AFD7ED558CCDL;
        return Long.rotateLeft(h, 31);
    }

    private static long avalanche(long h) {
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 29;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 32;
        return h;
    }
}
