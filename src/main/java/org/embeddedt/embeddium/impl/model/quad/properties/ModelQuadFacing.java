package org.embeddedt.embeddium.impl.model.quad.properties;

import lombok.Getter;
import org.embeddedt.embeddium.api.util.NormI8;

import java.util.Arrays;

public enum ModelQuadFacing {
    POS_X(1, 0, 0),
    POS_Y(0, 1, 0),
    POS_Z(0, 0, 1),
    NEG_X(-1, 0, 0),
    NEG_Y(0, -1, 0),
    NEG_Z(0, 0, -1),
    UNASSIGNED(0, 0, 0);

    public static final ModelQuadFacing[] VALUES = ModelQuadFacing.values();
    public static final ModelQuadFacing[] DIRECTIONS = Arrays.stream(VALUES).filter(facing -> facing != UNASSIGNED).toArray(ModelQuadFacing[]::new);

    public static final int COUNT = VALUES.length;

    public static final int NONE = 0;
    public static final int ALL = (1 << COUNT) - 1;

    @Getter
    private final int packedNormal;

    @Getter
    private final int stepX, stepY, stepZ;

    ModelQuadFacing(int stepX, int stepY, int stepZ) {
        this.stepX = stepX;
        this.stepY = stepY;
        this.stepZ = stepZ;
        this.packedNormal = NormI8.pack(stepX, stepY, stepZ);
    }

    public ModelQuadFacing getOpposite() {
        return switch (this) {
            case POS_Y -> NEG_Y;
            case NEG_Y -> POS_Y;
            case POS_X -> NEG_X;
            case NEG_X -> POS_X;
            case POS_Z -> NEG_Z;
            case NEG_Z -> POS_Z;
            default -> UNASSIGNED;
        };
    }
}
