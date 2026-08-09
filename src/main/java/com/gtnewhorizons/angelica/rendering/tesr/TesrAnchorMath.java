package com.gtnewhorizons.angelica.rendering.tesr;

import org.joml.Matrix4f;

public final class TesrAnchorMath {

    public static final int ANCHOR_GRID = 16;
    public static final double REANCHOR_DISTANCE = 512.0;
    private static final float HASH_QUANT = 4096f;

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
        long h = 0x2545F4914F6CDD1DL;
        h = mix(h, quantize(t.m00()));
        h = mix(h, quantize(t.m01()));
        h = mix(h, quantize(t.m10()));
        h = mix(h, quantize(t.m11()));
        h = mix(h, quantize(t.m30()));
        h = mix(h, quantize(t.m31()));
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
