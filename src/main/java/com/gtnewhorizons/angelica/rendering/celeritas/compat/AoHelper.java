package com.gtnewhorizons.angelica.rendering.celeritas.compat;

import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;

/**
 * Inlined data from package-private {@code AoNeighborInfo} -- edge face directions,
 * bilinear corner weight formulas, and depth formulas per face direction.
 */
public final class AoHelper {

    private static final ModelQuadFacing[][] FACES = {
        { ModelQuadFacing.NEG_Y, ModelQuadFacing.POS_Y, ModelQuadFacing.NEG_Z, ModelQuadFacing.POS_Z }, // POS_X
        { ModelQuadFacing.POS_X, ModelQuadFacing.NEG_X, ModelQuadFacing.NEG_Z, ModelQuadFacing.POS_Z }, // POS_Y
        { ModelQuadFacing.NEG_X, ModelQuadFacing.POS_X, ModelQuadFacing.NEG_Y, ModelQuadFacing.POS_Y }, // POS_Z
        { ModelQuadFacing.POS_Y, ModelQuadFacing.NEG_Y, ModelQuadFacing.NEG_Z, ModelQuadFacing.POS_Z }, // NEG_X
        { ModelQuadFacing.NEG_X, ModelQuadFacing.POS_X, ModelQuadFacing.NEG_Z, ModelQuadFacing.POS_Z }, // NEG_Y
        { ModelQuadFacing.POS_Y, ModelQuadFacing.NEG_Y, ModelQuadFacing.POS_X, ModelQuadFacing.NEG_X }, // NEG_Z
    };

    /**
     * Corner remap table derived from each AoNeighborInfo.mapCorners.
     * remap[outIdx] = inIdx.
     */
    public static final int[][] CORNER_REMAP = {
        { 3, 0, 1, 2 }, // POS_X
        { 2, 3, 0, 1 }, // POS_Y
        { 0, 1, 2, 3 }, // POS_Z
        { 1, 2, 3, 0 }, // NEG_X
        { 0, 1, 2, 3 }, // NEG_Y
        { 1, 2, 3, 0 }, // NEG_Z
    };

    private AoHelper() {}

    public static ModelQuadFacing[] getFaces(ModelQuadFacing direction) {
        return FACES[direction.ordinal()];
    }

    public static void calculateCornerWeights(ModelQuadFacing direction, float x, float y, float z, float[] out) {
        final float u, v;
        switch (direction) {
            case POS_X -> {
                u = z;
                v = 1.0f - y;
            }
            case POS_Y -> {
                u = z;
                v = x;
            }
            case POS_Z -> {
                u = y;
                v = 1.0f - x;
            }
            case NEG_X -> {
                u = z;
                v = y;
            }
            case NEG_Y -> {
                u = z;
                v = 1.0f - x;
            }
            case NEG_Z -> {
                u = 1.0f - x;
                v = y;
            }
            default -> throw new IllegalArgumentException();
        }
        out[0] = v * u;
        out[1] = v * (1.0f - u);
        out[2] = (1.0f - v) * (1.0f - u);
        out[3] = (1.0f - v) * u;
    }

    public static float getDepth(ModelQuadFacing direction, float x, float y, float z) {
        return switch (direction) {
            case POS_X -> 1.0f - x;
            case POS_Y -> 1.0f - y;
            case POS_Z -> 1.0f - z;
            case NEG_X -> x;
            case NEG_Y -> y;
            case NEG_Z -> z;
            default -> throw new IllegalArgumentException();
        };
    }
}
