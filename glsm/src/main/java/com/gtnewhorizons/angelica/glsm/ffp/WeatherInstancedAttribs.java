package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.lwjgl.opengl.GL11;


/**
 * One rain or snow column per instance.
 */
public final class WeatherInstancedAttribs {

    public static final int LOC_COLUMN_SPAN = ParticleInstancedAttribs.LOC_CENTER_HALF;
    public static final int LOC_JITTER = ParticleInstancedAttribs.LOC_UV;
    public static final int LOC_PARAMS = InstancedAttribs.LOC_COLOR;

    public static final int OFFSET_COLUMN_SPAN = 0;
    public static final int OFFSET_JITTER = 16;
    public static final int OFFSET_PARAMS = 32;
    public static final int STRIDE = 48;

    public static void pointInstanceAttribs(long base) {
        GLStateManager.glVertexAttribPointer(LOC_COLUMN_SPAN, 4, GL11.GL_FLOAT, false, STRIDE, base + OFFSET_COLUMN_SPAN);
        GLStateManager.glVertexAttribPointer(LOC_JITTER, 4, GL11.GL_FLOAT, false, STRIDE, base + OFFSET_JITTER);
        GLStateManager.glVertexAttribPointer(LOC_PARAMS, 4, GL11.GL_FLOAT, false, STRIDE, base + OFFSET_PARAMS);
    }

    static void enableInstanceArrays() {
        InstancedAttribs.enableInstanced(LOC_COLUMN_SPAN);
        InstancedAttribs.enableInstanced(LOC_JITTER);
        InstancedAttribs.enableInstanced(LOC_PARAMS);
    }

    private WeatherInstancedAttribs() {}
}
