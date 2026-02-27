package com.gtnewhorizons.angelica.glsm.states;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.nio.FloatBuffer;

/**
 * Stores eye-space clip plane equations for core profile emulation.
 */
public class ClipPlaneState {
    private final float[][] eyePlanes = new float[GLStateManager.MAX_CLIP_PLANES][4];

    private static final Vector4f tempPlane = new Vector4f();
    private static final Matrix4f tempInverse = new Matrix4f();

    public void setPlane(int index, double a, double b, double c, double d, Matrix4f modelView) {
        final float[] dest = eyePlanes[index];
        modelView.invert(tempInverse);
        tempPlane.set((float) a, (float) b, (float) c, (float) d);
        tempInverse.transformTranspose(tempPlane);
        dest[0] = tempPlane.x;
        dest[1] = tempPlane.y;
        dest[2] = tempPlane.z;
        dest[3] = tempPlane.w;
    }

    /** Writes the 4-component eye-space plane equation into {@code buf} at its current position. */
    public void putEyePlane(int index, FloatBuffer buf) {
        final float[] p = eyePlanes[index];
        buf.put(p[0]).put(p[1]).put(p[2]).put(p[3]);
    }
}
