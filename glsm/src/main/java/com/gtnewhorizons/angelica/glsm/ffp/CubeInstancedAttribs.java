package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.lwjgl.opengl.GL11;

public final class CubeInstancedAttribs {

    public static final int LOC_LIGHTMAP_SCALE = InstancedAttribs.LOC_LIGHTMAP;
    public static final int LOC_CUBE_TEX = 15;

    public static final int OFFSET_LIGHTMAP_SCALE = InstancedAttribs.HEAD_SIZE;
    public static final int OFFSET_CUBE_TEX = OFFSET_LIGHTMAP_SCALE + 16;
    public static final int STRIDE = OFFSET_CUBE_TEX + 16;

    public static void pointInstanceAttribs(long base) {
        InstancedAttribs.pointHead(base, STRIDE);
        GLStateManager.glVertexAttribPointer(LOC_LIGHTMAP_SCALE, 4, GL11.GL_FLOAT, false, STRIDE, base + OFFSET_LIGHTMAP_SCALE);
        GLStateManager.glVertexAttribPointer(LOC_CUBE_TEX, 4, GL11.GL_FLOAT, false, STRIDE, base + OFFSET_CUBE_TEX);
    }

    static void enableInstanceArrays() {
        InstancedAttribs.enableHeadArrays();
        InstancedAttribs.enableInstanced(LOC_CUBE_TEX);
    }

    private CubeInstancedAttribs() {}
}
