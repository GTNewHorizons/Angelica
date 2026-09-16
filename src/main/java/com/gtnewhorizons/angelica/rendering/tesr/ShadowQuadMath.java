package com.gtnewhorizons.angelica.rendering.tesr;

import net.minecraft.util.MathHelper;

import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.COLOR_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.LIGHT_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.NORMAL_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.TEX_X_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.TEX_Y_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.VERTEX_SIZE;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.X_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.Y_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.Z_INDEX;

public final class ShadowQuadMath {

    public static final int RECORD_SIZE = 11;

    public static final int MIN_X = 0, Y = 1, MIN_Z = 2, MAX_X = 3, MAX_Z = 4, U_MIN_X = 5, V_MIN_Z = 6, U_MAX_X = 7, V_MAX_Z = 8, COLOR = 9, LIGHT = 10;

    private ShadowQuadMath() {}

    public static int box(double ex, double ey, double ez, float size, int[] outIJK) {
        final int i = MathHelper.floor_double(ex - size);
        final int j = MathHelper.floor_double(ex + size);
        final int k = MathHelper.floor_double(ey - size);
        final int l = MathHelper.floor_double(ey);
        final int i1 = MathHelper.floor_double(ez - size);
        final int j1 = MathHelper.floor_double(ez + size);
        outIJK[0] = i;
        outIJK[1] = j;
        outIJK[2] = k;
        outIJK[3] = l;
        outIJK[4] = i1;
        outIJK[5] = j1;
        return (j - i + 1) * (l - k + 1) * (j1 - i1 + 1);
    }

    public static boolean project(int[] out, int off, double x, double y, double z, int bx, int by, int bz, float shadowAlpha, float size, double ox, double oy, double oz, double minX, double maxX, double minY, double minZ, double maxZ, float brightness, int packedLight) {
        double alpha = ((double) shadowAlpha - (y - ((double) by + oy)) / 2.0D) * 0.5D * (double) brightness;
        if (alpha < 0.0D) return false;
        if (alpha > 1.0D) alpha = 1.0D;

        final double qMinX = (double) bx + minX + ox;
        final double qMaxX = (double) bx + maxX + ox;
        final double qY = (double) by + minY + oy + 0.015625D;
        final double qMinZ = (double) bz + minZ + oz;
        final double qMaxZ = (double) bz + maxZ + oz;
        final float uMinX = (float) ((x - qMinX) / 2.0D / (double) size + 0.5D);
        final float uMaxX = (float) ((x - qMaxX) / 2.0D / (double) size + 0.5D);
        final float vMinZ = (float) ((z - qMinZ) / 2.0D / (double) size + 0.5D);
        final float vMaxZ = (float) ((z - qMaxZ) / 2.0D / (double) size + 0.5D);

        out[off + MIN_X] = Float.floatToRawIntBits((float) qMinX);
        out[off + Y] = Float.floatToRawIntBits((float) qY);
        out[off + MIN_Z] = Float.floatToRawIntBits((float) qMinZ);
        out[off + MAX_X] = Float.floatToRawIntBits((float) qMaxX);
        out[off + MAX_Z] = Float.floatToRawIntBits((float) qMaxZ);
        out[off + U_MIN_X] = Float.floatToRawIntBits(uMinX);
        out[off + V_MIN_Z] = Float.floatToRawIntBits(vMinZ);
        out[off + U_MAX_X] = Float.floatToRawIntBits(uMaxX);
        out[off + V_MAX_Z] = Float.floatToRawIntBits(vMaxZ);
        out[off + COLOR] = 0xFFFFFF | ((int) ((float) alpha * 255.0F)) << 24;
        out[off + LIGHT] = packedLight;
        return true;
    }

    public static void expand(int[] packed32, int[] records, int off, int normalPacked) {
        final int minX = records[off + MIN_X];
        final int y = records[off + Y];
        final int minZ = records[off + MIN_Z];
        final int maxX = records[off + MAX_X];
        final int maxZ = records[off + MAX_Z];
        final int uMinX = records[off + U_MIN_X];
        final int vMinZ = records[off + V_MIN_Z];
        final int uMaxX = records[off + U_MAX_X];
        final int vMaxZ = records[off + V_MAX_Z];
        final int color = records[off + COLOR];
        final int light = records[off + LIGHT];

        write(packed32, 0, minX, y, minZ, uMinX, vMinZ, color, light, normalPacked);
        write(packed32, 1, minX, y, maxZ, uMinX, vMaxZ, color, light, normalPacked);
        write(packed32, 2, maxX, y, maxZ, uMaxX, vMaxZ, color, light, normalPacked);
        write(packed32, 3, maxX, y, minZ, uMaxX, vMinZ, color, light, normalPacked);
    }

    private static void write(int[] packed32, int vertex, int x, int y, int z, int u, int v, int color, int light, int normal) {
        final int base = vertex * VERTEX_SIZE;
        packed32[base | X_INDEX] = x;
        packed32[base | Y_INDEX] = y;
        packed32[base | Z_INDEX] = z;
        packed32[base | TEX_X_INDEX] = u;
        packed32[base | TEX_Y_INDEX] = v;
        packed32[base | COLOR_INDEX] = color;
        packed32[base | LIGHT_INDEX] = light;
        packed32[base | NORMAL_INDEX] = normal;
    }
}
