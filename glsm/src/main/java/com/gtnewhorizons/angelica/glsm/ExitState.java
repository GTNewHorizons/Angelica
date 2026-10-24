package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.states.AlphaState;
import com.gtnewhorizons.angelica.glsm.states.BlendState;
import com.gtnewhorizons.angelica.glsm.states.Color4;
import com.gtnewhorizons.angelica.glsm.states.ColorMask;
import com.gtnewhorizons.angelica.glsm.states.MaterialState;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

public final class ExitState {
    public static final int POLYGON_OFFSET_ENABLE_WRITTEN = 1;
    public static final int POLYGON_OFFSET_VALUES_WRITTEN = 2;
    final Values entry = new Values();
    final Values value = new Values();
    final MaterialState frontMaterial = new MaterialState(GL11.GL_FRONT);
    final MaterialState backMaterial = new MaterialState(GL11.GL_BACK);
    long writes, dependencies;
    boolean valid;
    int textureDeleteCount;

    public void clear() {
        valid = false;
        writes = dependencies = 0;
    }

    static final class Values {
        final Color4 color = new Color4();
        final Vector3f normal = new Vector3f();
        final Vector4f texCoord = new Vector4f();
        final Vector4f shaderColor = new Vector4f();
        final Vector4f overlayColor = new Vector4f();
        final BlendState blend = new BlendState();
        final AlphaState alpha = new AlphaState();
        final ColorMask colorMask = new ColorMask();
        final Matrix4f textureMatrix = new Matrix4f();
        long enabled;
        int activeUnit, clientActiveUnit, matrixMode, texture, depthFunction, cullFace, frontFace, shadeModel;
        int materialFace, materialParameter;
        boolean depthMask;
        float lightmapX, lightmapY, offsetFactor, offsetUnits, offsetClamp;
    }
}
