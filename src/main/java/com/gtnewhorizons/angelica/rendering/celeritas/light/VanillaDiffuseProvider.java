package com.gtnewhorizons.angelica.rendering.celeritas.light;

import org.embeddedt.embeddium.api.util.ColorABGR;
import org.embeddedt.embeddium.impl.model.light.DiffuseProvider;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;

public enum VanillaDiffuseProvider implements DiffuseProvider {
    INSTANCE;

    private static final float DOWN = 0.5f;        // NEG_Y
    private static final float UP = 1.0f;          // POS_Y
    private static final float NORTH_SOUTH = 0.8f; // NEG_Z, POS_Z
    private static final float EAST_WEST = 0.6f;   // NEG_X, POS_X

    // Inverse values for stripping vanilla's baked diffuse (when oldLighting=false)
    private static final float INV_DOWN = 1.0f / DOWN;               // 2.0
    private static final float INV_UP = 1.0f / UP;                   // 1.0
    private static final float INV_NORTH_SOUTH = 1.0f / NORTH_SOUTH; // 1.25
    private static final float INV_EAST_WEST = 1.0f / EAST_WEST;     // ~1.667

    @Override
    public float getDiffuse(float normalX, float normalY, float normalZ, boolean shade) {
        if (!shade) return 1.0f;
        return Math.min(normalX * normalX * EAST_WEST + normalY * normalY * (normalY >= 0 ? UP : DOWN) + normalZ * normalZ * NORTH_SOUTH, 1.0f);
    }

    @Override
    public float getDiffuse(ModelQuadFacing lightFace, boolean shade) {
        if (!shade) return 1.0f;
        return switch (lightFace) {
            case NEG_Y -> DOWN;
            case POS_Y -> UP;
            case NEG_Z, POS_Z -> NORTH_SOUTH;
            case NEG_X, POS_X -> EAST_WEST;
            case UNASSIGNED -> 1.0f;
        };
    }

    public float getInverseDiffuse(ModelQuadFacing lightFace) {
        return switch (lightFace) {
            case NEG_Y -> INV_DOWN;
            case POS_Y -> INV_UP;
            case NEG_Z, POS_Z -> INV_NORTH_SOUTH;
            case NEG_X, POS_X -> INV_EAST_WEST;
            case UNASSIGNED -> 1.0f;
        };
    }

    public static int multiplyColor(int color, float factor) {
        if (factor == 1.0f) {
            return color;
        }
        int r = Math.min(255, (int)(ColorABGR.unpackRed(color) * factor));
        int g = Math.min(255, (int)(ColorABGR.unpackGreen(color) * factor));
        int b = Math.min(255, (int)(ColorABGR.unpackBlue(color) * factor));
        int a = ColorABGR.unpackAlpha(color);
        return ColorABGR.pack(r, g, b, a);
    }
}
