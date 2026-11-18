package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.ffp.VertexKey;
import com.gtnewhorizons.angelica.glsm.states.LightModelState;
import com.gtnewhorizons.angelica.glsm.states.LightState;
import com.gtnewhorizons.angelica.glsm.states.MaterialState;
import org.lwjgl.opengl.GL11;

public final class LightingSnapshot {
    final boolean[] lightEnabled = new boolean[VertexKey.FFP_LIGHT_COUNT];
    final LightState[] lights = new LightState[VertexKey.FFP_LIGHT_COUNT];
    final LightModelState lightModel = new LightModelState();
    final MaterialState frontMaterial = new MaterialState(GL11.GL_FRONT);
    final MaterialState backMaterial = new MaterialState(GL11.GL_BACK);
    boolean colorMaterial;
    int colorMaterialFace;
    int colorMaterialParameter;
    boolean normalize;
    boolean rescaleNormal;

    public LightingSnapshot() {
        for (int i = 0; i < lights.length; i++) {
            lights[i] = new LightState(GL11.GL_LIGHT0 + i);
        }
    }
}
