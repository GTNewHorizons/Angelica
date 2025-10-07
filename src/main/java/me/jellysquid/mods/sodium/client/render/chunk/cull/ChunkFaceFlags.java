package me.jellysquid.mods.sodium.client.render.chunk.cull;

import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.properties.ModelQuadFacing;

public class ChunkFaceFlags {
    public static final int UP = of(ModelQuadFacing.POS_Y);
    public static final int DOWN = of(ModelQuadFacing.NEG_Y);
    public static final int WEST = of(ModelQuadFacing.NEG_X);
    public static final int EAST = of(ModelQuadFacing.POS_X);
    public static final int NORTH = of(ModelQuadFacing.NEG_Z);
    public static final int SOUTH = of(ModelQuadFacing.POS_Z);
    public static final int UNASSIGNED = of(ModelQuadFacing.UNASSIGNED);

    public static final int ALL = all();

    public static int of(ModelQuadFacing facing) {
        return 1 << facing.ordinal();
    }

    private static int all() {
        int v = 0;

        for (ModelQuadFacing facing : ModelQuadFacing.VALUES) {
            v |= of(facing);
        }

        return v;
    }
}
