package com.gtnewhorizons.angelica.glsm.ffp;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutFloat;

public record CubeParams(float minX, float minY, float minZ, float spanX, float spanY, float spanZ, float u0, float v0, float uScaleX, float vScaleY, float uScaleZ, float vScaleZ) {

    private static final float MIN_SPAN = 1.0e-4f;

    public CubeParams {
        if (!(spanX > MIN_SPAN && spanY > MIN_SPAN && spanZ > MIN_SPAN)) throw new IllegalArgumentException("degenerate cube span");
    }

    public static CubeParams of(float texWidth, float texHeight, boolean mirror, int texU, int texV, float x, float y, float z, int sizeX, int sizeY, int sizeZ, float inflate) {
        if (texWidth <= 0.0f || texHeight <= 0.0f) return null;
        if (sizeX < 0 || sizeY < 0 || sizeZ < 0) return null;
        if (mirror && sizeX == 0) return null;
        final float minX = x - inflate;
        final float minY = y - inflate;
        final float minZ = z - inflate;
        final float spanX = ((x + sizeX) + inflate) - minX;
        final float spanY = ((y + sizeY) + inflate) - minY;
        final float spanZ = ((z + sizeZ) + inflate) - minZ;
        if (spanX <= MIN_SPAN || spanY <= MIN_SPAN || spanZ <= MIN_SPAN) return null;
        return new CubeParams(minX, minY, minZ, spanX, spanY, spanZ, texU / texWidth, texV / texHeight, (mirror ? -sizeX : sizeX) / texWidth, sizeY / texHeight, sizeZ / texWidth, sizeZ / texHeight);
    }

    public void writeRows(long ptr, float[] mv, int off, float scale) {
        final float tx = minX * scale;
        final float ty = minY * scale;
        final float tz = minZ * scale;
        final float sx = spanX * scale;
        final float sy = spanY * scale;
        final float sz = spanZ * scale;
        for (int r = 0; r < 3; r++) {
            final float c0 = mv[off + r];
            final float c1 = mv[off + 4 + r];
            final float c2 = mv[off + 8 + r];
            final long row = ptr + InstancedAttribs.OFFSET_ROWS + r * 16L;
            memPutFloat(row, c0 * sx);
            memPutFloat(row + 4, c1 * sy);
            memPutFloat(row + 8, c2 * sz);
            memPutFloat(row + 12, mv[off + 12 + r] + c0 * tx + c1 * ty + c2 * tz);
        }
    }

    public void writeTexture(long ptr) {
        memPutFloat(ptr + CubeInstancedAttribs.OFFSET_LIGHTMAP_SCALE + 8, uScaleZ);
        memPutFloat(ptr + CubeInstancedAttribs.OFFSET_LIGHTMAP_SCALE + 12, vScaleZ);
        memPutFloat(ptr + CubeInstancedAttribs.OFFSET_CUBE_TEX, u0);
        memPutFloat(ptr + CubeInstancedAttribs.OFFSET_CUBE_TEX + 4, v0);
        memPutFloat(ptr + CubeInstancedAttribs.OFFSET_CUBE_TEX + 8, uScaleX);
        memPutFloat(ptr + CubeInstancedAttribs.OFFSET_CUBE_TEX + 12, vScaleY);
    }
}
