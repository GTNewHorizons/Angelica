package com.gtnewhorizons.angelica.glsm.states;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

/**
 * Per-texture-unit storage for texgen mode and plane coefficients. Based on Mesa's texgen.c storage model.
 */
public class TexGenState {
    private int modeS, modeT, modeR, modeQ;

    private final float[] objectPlaneS = {1, 0, 0, 0};
    private final float[] objectPlaneT = {0, 1, 0, 0};
    private final float[] objectPlaneR = {0, 0, 0, 0};
    private final float[] objectPlaneQ = {0, 0, 0, 0};

    private final float[] eyePlaneS = {1, 0, 0, 0};
    private final float[] eyePlaneT = {0, 1, 0, 0};
    private final float[] eyePlaneR = {0, 0, 0, 0};
    private final float[] eyePlaneQ = {0, 0, 0, 0};

    private static final Vector4f tempPlane = new Vector4f();
    private static final Matrix4f tempInverse = new Matrix4f();

    public int getMode(int coord) {
        return switch (coord) {
            case GL11.GL_S -> modeS;
            case GL11.GL_T -> modeT;
            case GL11.GL_R -> modeR;
            case GL11.GL_Q -> modeQ;
            default -> 0;
        };
    }

    public void setMode(int coord, int mode) {
        switch (coord) {
            case GL11.GL_S -> modeS = mode;
            case GL11.GL_T -> modeT = mode;
            case GL11.GL_R -> modeR = mode;
            case GL11.GL_Q -> modeQ = mode;
        }
    }

    public float[] getObjectPlane(int coord) {
        return switch (coord) {
            case GL11.GL_S -> objectPlaneS;
            case GL11.GL_T -> objectPlaneT;
            case GL11.GL_R -> objectPlaneR;
            case GL11.GL_Q -> objectPlaneQ;
            default -> objectPlaneS;
        };
    }

    public float[] getEyePlane(int coord) {
        return switch (coord) {
            case GL11.GL_S -> eyePlaneS;
            case GL11.GL_T -> eyePlaneT;
            case GL11.GL_R -> eyePlaneR;
            case GL11.GL_Q -> eyePlaneQ;
            default -> eyePlaneS;
        };
    }

    public void setObjectPlane(int coord, float[] plane) {
        final float[] dest = getObjectPlane(coord);
        System.arraycopy(plane, 0, dest, 0, 4);
    }

    /**
     * Set eye plane coefficients. Per GL spec (Mesa texgen.c:154), the plane is transformed by the inverse of the current modelview matrix at the time of the call.
     */
    public void setEyePlane(int coord, float[] plane, Matrix4f modelView) {
        final float[] dest = getEyePlane(coord);
        modelView.invert(tempInverse);
        tempPlane.set(plane[0], plane[1], plane[2], plane[3]);
        tempInverse.transformTranspose(tempPlane);
        dest[0] = tempPlane.x;
        dest[1] = tempPlane.y;
        dest[2] = tempPlane.z;
        dest[3] = tempPlane.w;
    }
}
