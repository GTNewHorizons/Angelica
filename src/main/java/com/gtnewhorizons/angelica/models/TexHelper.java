package com.gtnewhorizons.angelica.models;

import net.minecraft.util.IIcon;
import net.minecraftforge.common.util.ForgeDirection;

public class TexHelper {

    private static final float NORMALIZER = 1f / 16f;
    private static final VertexModifier[] UVLOCKERS = new VertexModifier[6];

    static {
        UVLOCKERS[ForgeDirection.EAST.ordinal()] = (q, i) -> q.uv(i, 1 - q.z(i), 1 - q.y(i));
        UVLOCKERS[ForgeDirection.WEST.ordinal()] = (q, i) -> q.uv(i, q.z(i), 1 - q.y(i));
        UVLOCKERS[ForgeDirection.NORTH.ordinal()] = (q, i) -> q.uv(i, 1 - q.x(i), 1 - q.y(i));
        UVLOCKERS[ForgeDirection.SOUTH.ordinal()] = (q, i) -> q.uv(i, q.x(i), 1 - q.y(i));
        UVLOCKERS[ForgeDirection.DOWN.ordinal()] = (q, i) -> q.uv(i, q.x(i), 1 - q.z(i));
        UVLOCKERS[ForgeDirection.UP.ordinal()] = (q, i) -> q.uv(i, q.x(i), q.z(i));
    }

    private static final VertexModifier[] ROTATIONS = new VertexModifier[] {
        null,
        (q, i) -> q.uv(i, q.v(i), 1 - q.u(i)), //90
        (q, i) -> q.uv(i, 1 - q.u(i), 1 - q.v(i)), //180
        (q, i) -> q.uv(i, 1 - q.v(i), q.u(i)) // 270
    };

    private static void applyModifier(NdQuadBuilder quad, VertexModifier modifier) {
        for (int i = 0; i < 4; i++) {
            modifier.apply(quad, i);
        }
    }

    /**
     * Bakes textures in the provided vertex data, handling UV locking,
     * rotation, interpolation, etc. Textures must not be already baked.
     */
    public static void bakeSprite(NdQuadBuilder quad, IIcon sprite, int bakeFlags) {
        if (quad.nominalFace() != ForgeDirection.UNKNOWN && (NdQuadBuilder.BAKE_LOCK_UV & bakeFlags) != 0) {
            // Assigns normalized UV coordinates based on vertex positions
            applyModifier(quad, UVLOCKERS[quad.nominalFace().ordinal()]);
        } else if ((NdQuadBuilder.BAKE_NORMALIZED & bakeFlags) == 0) { // flag is NOT set, UVs are assumed to not be normalized yet as is the default, normalize through dividing by 16
            // Scales from 0-16 to 0-1
            applyModifier(quad, (q, i) -> q.uv(i, q.u(i) * NORMALIZER, q.v(i) * NORMALIZER));
        }

        final int rotation = bakeFlags & 3;

        if (rotation != 0) {
            // Rotates texture around the center of sprite.
            // Assumes normalized coordinates.
            applyModifier(quad, ROTATIONS[rotation]);
        }

        if ((NdQuadBuilder.BAKE_FLIP_U & bakeFlags) != 0) {
            // Inverts U coordinates.  Assumes normalized (0-1) values.
            applyModifier(quad, (q, i) -> q.uv(i, 1 - q.u(i), q.v(i)));
        }

        if ((NdQuadBuilder.BAKE_FLIP_V & bakeFlags) != 0) {
            // Inverts V coordinates.  Assumes normalized (0-1) values.
            applyModifier(quad, (q, i) -> q.uv(i, q.u(i), 1 - q.v(i)));
        }

        interpolate(quad, sprite);
    }

    /**
     * Faster than sprite method. Sprite computes span and normalizes inputs each call,
     * so we'd have to denormalize before we called, only to have the sprite renormalize immediately.
     */
    private static void interpolate(NdQuadBuilder q, IIcon sprite) {
        final float uMin = sprite.getMinU();
        final float uSpan = sprite.getMaxU() - uMin;
        final float vMin = sprite.getMinV();
        final float vSpan = sprite.getMaxV() - vMin;

        for (int i = 0; i < 4; i++) {
            q.uv(i, uMin + q.u(i) * uSpan, vMin + q.v(i) * vSpan);
        }
    }

    @FunctionalInterface
    private interface VertexModifier {
        void apply(NdQuadBuilder quad, int vertexIndex);
    }
}
