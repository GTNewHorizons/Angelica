package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.glsm.ffp.CubeParams;
import com.gtnewhorizons.angelica.glsm.ffp.UnitCubeMesh;
import net.minecraft.client.model.PositionTextureVertex;
import net.minecraft.client.model.TexturedQuad;

public final class ModelBoxCapture {

    private static final float EPSILON = 1.0e-4f;

    private ModelBoxCapture() {}

    public static CubeParams capture(TexturedQuad[] quads, float texWidth, float texHeight, boolean mirror, int texU, int texV, float x, float y, float z, int sizeX, int sizeY, int sizeZ, float inflate) {
        if (quads == null || quads.length != UnitCubeMesh.FACE_COUNT) return null;
        final CubeParams params = CubeParams.of(texWidth, texHeight, mirror, texU, texV, x, y, z, sizeX, sizeY, sizeZ, inflate);
        if (params == null) return null;

        final float lowX = mirror ? params.minX() + params.spanX() : params.minX();
        final float spanX = mirror ? -params.spanX() : params.spanX();
        for (int face = 0; face < UnitCubeMesh.FACE_COUNT; face++) {
            final TexturedQuad quad = quads[face];
            if (quad == null || quad.vertexPositions == null || quad.vertexPositions.length != 4) return null;
            final int[] corners = UnitCubeMesh.FACES[face].corners();
            final UnitCubeMesh.Net net = UnitCubeMesh.FACES[face].net();
            for (int corner = 0; corner < 4; corner++) {
                final PositionTextureVertex vertex = quad.vertexPositions[mirror ? 3 - corner : corner];
                if (vertex == null || vertex.vector3D == null) return null;
                final float[] unit = UnitCubeMesh.CORNERS[corners[corner]];
                if (!near((float) vertex.vector3D.xCoord, lowX + unit[0] * spanX) || !near((float) vertex.vector3D.yCoord, params.minY() + unit[1] * params.spanY()) || !near((float) vertex.vector3D.zCoord, params.minZ() + unit[2] * params.spanZ())) {
                    return null;
                }
                final int u = texU + net.u(corner).texels(sizeX, sizeZ);
                final int v = texV + net.v(corner).texels(sizeY, sizeZ);
                if (!near(vertex.texturePositionX, u / texWidth) || !near(vertex.texturePositionY, v / texHeight)) {
                    return null;
                }
            }
        }
        return params;
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < EPSILON;
    }
}
