package com.gtnewhorizons.angelica.rendering.particles;

import com.gtnewhorizons.angelica.glsm.ffp.ParticleQuadMesh;

public final class ParticleQuadDecoder {

    private ParticleQuadDecoder() {}

    public static boolean decode(float[] verts, int count, float rotX, float rotXZ, float rotZ, float rotYZ, float rotXY, ParticleParams out) {
        if (count != 4) return false;

        final float sx = rotX + rotYZ;
        final float sy = rotXZ;
        final float sz = rotZ + rotXY;
        final float denom = sx * sx + sy * sy + sz * sz;
        if (denom <= 1.0e-12f) return false;

        final float cx = (verts[0] + verts[10]) * 0.5f;
        final float cy = (verts[1] + verts[11]) * 0.5f;
        final float cz = (verts[2] + verts[12]) * 0.5f;
        final float dx = verts[10] - verts[0];
        final float dy = verts[11] - verts[1];
        final float dz = verts[12] - verts[2];
        final float half = (dx * sx + dy * sy + dz * sz) / (2.0f * denom);

        final float scale = Math.max(Math.abs(cx), Math.max(Math.abs(cy), Math.abs(cz)));
        final float tol = 1.0e-5f * (1.0f + Math.abs(half) + scale);
        for (int i = 0, o = 0; i < ParticleQuadMesh.VERTEX_COUNT; i++, o += 5) {
            final float a = ParticleQuadMesh.cornerA(i) * half;
            final float b = ParticleQuadMesh.cornerB(i) * half;
            if (Math.abs(verts[o] - (cx + ParticleQuadMesh.offsetX(a, b, rotX, rotYZ))) > tol) return false;
            if (Math.abs(verts[o + 1] - (cy + ParticleQuadMesh.offsetY(b, rotXZ))) > tol) return false;
            if (Math.abs(verts[o + 2] - (cz + ParticleQuadMesh.offsetZ(a, b, rotZ, rotXY))) > tol) return false;
        }

        if (verts[3] != verts[8] || verts[9] != verts[14] || verts[18] != verts[13] || verts[19] != verts[4]) {
            return false;
        }

        out.centerX = cx;
        out.centerY = cy;
        out.centerZ = cz;
        out.half = half;
        out.u0 = verts[3];
        out.v0 = verts[4];
        out.u1 = verts[13];
        out.v1 = verts[14];
        return true;
    }
}
