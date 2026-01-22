package com.gtnewhorizons.angelica.rendering.celeritas.light;

import org.embeddedt.embeddium.impl.model.light.DiffuseProvider;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;

public enum VanillaDiffuseProvider implements DiffuseProvider {
    INSTANCE;

    private static final float DOWN = 0.5f;   // NEG_Y
    private static final float UP = 1.0f;     // POS_Y
    private static final float NORTH_SOUTH = 0.8f; // NEG_Z, POS_Z
    private static final float EAST_WEST = 0.6f;   // NEG_X, POS_X

    @Override
    public float getDiffuse(float normalX, float normalY, float normalZ, boolean shade) {
        if (!shade) {
            return 1.0f;
        }
        return Math.min(normalX * normalX * EAST_WEST + normalY * normalY * (normalY >= 0 ? UP : DOWN) + normalZ * normalZ * NORTH_SOUTH, 1.0f);
    }

    @Override
    public float getDiffuse(ModelQuadFacing lightFace, boolean shade) {
        if (!shade) {
            return 1.0f;
        }
        return switch (lightFace) {
            case NEG_Y -> DOWN;
            case POS_Y -> UP;
            case NEG_Z, POS_Z -> NORTH_SOUTH;
            case NEG_X, POS_X -> EAST_WEST;
            case UNASSIGNED -> 1.0f;
        };
    }
}
