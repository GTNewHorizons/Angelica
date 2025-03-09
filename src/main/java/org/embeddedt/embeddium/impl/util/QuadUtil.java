package org.embeddedt.embeddium.impl.util;

import org.embeddedt.embeddium.api.util.NormI8;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import org.joml.Vector3f;

public class QuadUtil {
    public static ModelQuadFacing findNormalFace(float x, float y, float z) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
            return ModelQuadFacing.UNASSIGNED;
        }

        float maxDot = 0;
        ModelQuadFacing closestFace = null;

        for (ModelQuadFacing face : ModelQuadFacing.DIRECTIONS) {
            float dot = (x * face.getStepX()) + (y * face.getStepY()) + (z * face.getStepZ());

            if (dot > maxDot) {
                maxDot = dot;
                closestFace = face;
            }
        }

        if (closestFace != null && Math.abs(maxDot - 1.0f) < 1.0E-5F) {
            return closestFace;
        }

        return ModelQuadFacing.UNASSIGNED;
    }

    public static ModelQuadFacing findNormalFace(int normal) {
        return findNormalFace(NormI8.unpackX(normal), NormI8.unpackY(normal), NormI8.unpackZ(normal));
    }

    public static void calculateNormal(ChunkVertexEncoder.Vertex[] quad, Vector3f result) {
        ChunkVertexEncoder.Vertex q0 = quad[0], q1 = quad[1], q2 = quad[2], q3 = quad[3];

        final float x0 = q0.x;
        final float y0 = q0.y;
        final float z0 = q0.z;

        final float x1 = q1.x;
        final float y1 = q1.y;
        final float z1 = q1.z;

        final float x2 = q2.x;
        final float y2 = q2.y;
        final float z2 = q2.z;

        final float x3 = q3.x;
        final float y3 = q3.y;
        final float z3 = q3.z;

        final float dx0 = x2 - x0;
        final float dy0 = y2 - y0;
        final float dz0 = z2 - z0;
        final float dx1 = x3 - x1;
        final float dy1 = y3 - y1;
        final float dz1 = z3 - z1;

        float normX = dy0 * dz1 - dz0 * dy1;
        float normY = dz0 * dx1 - dx0 * dz1;
        float normZ = dx0 * dy1 - dy0 * dx1;

        float l = (float) Math.sqrt(normX * normX + normY * normY + normZ * normZ);

        if (l != 0) {
            normX /= l;
            normY /= l;
            normZ /= l;
        }

        result.set(normX, normY, normZ);
    }

    public static int calculateNormal(ChunkVertexEncoder.Vertex[] quad) {
        ChunkVertexEncoder.Vertex q0 = quad[0], q1 = quad[1], q2 = quad[2], q3 = quad[3];

        final float x0 = q0.x;
        final float y0 = q0.y;
        final float z0 = q0.z;

        final float x1 = q1.x;
        final float y1 = q1.y;
        final float z1 = q1.z;

        final float x2 = q2.x;
        final float y2 = q2.y;
        final float z2 = q2.z;

        final float x3 = q3.x;
        final float y3 = q3.y;
        final float z3 = q3.z;

        final float dx0 = x2 - x0;
        final float dy0 = y2 - y0;
        final float dz0 = z2 - z0;
        final float dx1 = x3 - x1;
        final float dy1 = y3 - y1;
        final float dz1 = z3 - z1;

        float normX = dy0 * dz1 - dz0 * dy1;
        float normY = dz0 * dx1 - dx0 * dz1;
        float normZ = dx0 * dy1 - dy0 * dx1;

        float l = (float) Math.sqrt(normX * normX + normY * normY + normZ * normZ);

        if (l != 0) {
            normX /= l;
            normY /= l;
            normZ /= l;
        }

        return NormI8.pack(normX, normY, normZ);
    }
}
