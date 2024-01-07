package com.gtnewhorizons.angelica.glsm.states;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

public class MatrixState {

    public MatrixMode mode = MatrixMode.NONE;
    public final Matrix4fStack projectionMatrix;
    public final Matrix4fStack modelViewMatrix;

    public MatrixState() {
        projectionMatrix = new Matrix4fStack(GL11.glGetInteger(GL11.GL_MAX_PROJECTION_STACK_DEPTH));
        modelViewMatrix = new Matrix4fStack(GL11.glGetInteger(GL11.GL_MAX_MODELVIEW_STACK_DEPTH));
    }

    public void identity() {
        switch (mode) {
            case MODELVIEW -> modelViewMatrix.identity();
            case PROJECTION -> projectionMatrix.identity();
            default -> {
            }
        }
    }

    public void setMode(int mode) {
        switch (mode) {
            case GL11.GL_MODELVIEW -> this.mode = MatrixMode.MODELVIEW;
            case GL11.GL_PROJECTION -> this.mode = MatrixMode.PROJECTION;
            case GL11.GL_TEXTURE -> this.mode = MatrixMode.TEXTURE;
            default -> throw new RuntimeException("Unsupported matrix mode: " + mode);
        }
    }

    public void translate(float x, float y, float z) {
        switch (mode) {
            case MODELVIEW -> modelViewMatrix.translate(x, y, z);
            case PROJECTION -> projectionMatrix.translate(x, y, z);
            default -> {
            }
        }
    }

    public void scale(float x, float y, float z) {
        switch (mode) {
            case MODELVIEW -> modelViewMatrix.scale(x, y, z);
            case PROJECTION -> projectionMatrix.scale(x, y, z);
            default -> {
            }
        }
    }

    private final Matrix4f tempMatrix = new Matrix4f();

    public void multiply(FloatBuffer matrix) {
        tempMatrix.set(matrix);
        switch (mode) {
            case MODELVIEW -> modelViewMatrix.mul(tempMatrix);
            case PROJECTION -> projectionMatrix.mul(tempMatrix);
            default -> {
            }
        }
    }

    public void multiply(Matrix4f matrix) {
        switch (mode) {
            case MODELVIEW -> modelViewMatrix.mul(matrix);
            case PROJECTION -> projectionMatrix.mul(matrix);
            default -> {
            }
        }
    }

    public void rotate(float angle, float x, float y, float z) {
        switch (mode) {
            case MODELVIEW -> modelViewMatrix.rotate(angle, x, y, z);
            case PROJECTION -> projectionMatrix.rotate(angle, x, y, z);
            default -> {
            }
        }
    }

    public void ortho(double left, double right, double bottom, double top, double zNear, double zFar) {
        switch (mode) {
            case MODELVIEW -> modelViewMatrix.ortho((float) left, (float) right, (float) bottom, (float) top, (float) zNear, (float) zFar);
            case PROJECTION -> projectionMatrix.ortho((float) left, (float) right, (float) bottom, (float) top, (float) zNear, (float) zFar);
            default -> {
            }
        }
    }

    public void frustum(double left, double right, double bottom, double top, double zNear, double zFar) {
        switch (mode) {
            case MODELVIEW -> modelViewMatrix.frustum((float) left, (float) right, (float) bottom, (float) top, (float) zNear, (float) zFar);
            case PROJECTION -> projectionMatrix.frustum((float) left, (float) right, (float) bottom, (float) top, (float) zNear, (float) zFar);
            default -> {
            }
        }
    }
    public void push() {
        switch (mode) {
            case MODELVIEW -> modelViewMatrix.pushMatrix();
            case PROJECTION -> projectionMatrix.pushMatrix();
            default -> {
            }
        }
    }

    public void pop() {
        switch (mode) {
            case MODELVIEW -> modelViewMatrix.popMatrix();
            case PROJECTION -> projectionMatrix.popMatrix();
            default -> {
            }
        }
    }

    public void perspective(float fovy, float aspect, float zNear, float zFar) {
        switch (mode) {
            case MODELVIEW -> modelViewMatrix.perspective(fovy, aspect, zNear, zFar);
            case PROJECTION -> projectionMatrix.perspective(fovy, aspect, zNear, zFar);
            default -> {
            }
        }
    }

    enum MatrixMode {
        NONE,
        MODELVIEW,
        PROJECTION,
        TEXTURE
    }

    @Override
    public boolean equals(Object state) {
        if (this == state) return true;
        if (!(state instanceof MatrixState matrixState)) return false;
        return mode == matrixState.mode && projectionMatrix.equals(matrixState.projectionMatrix) && modelViewMatrix.equals(matrixState.modelViewMatrix);
    }

}
