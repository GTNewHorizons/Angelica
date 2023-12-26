package com.gtnewhorizons.angelica.rendering;

import lombok.Getter;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

public class RenderingState {
    public static final RenderingState INSTANCE = new RenderingState();
    @Getter
    private final Vector3d cameraPosition = new Vector3d();
    @Getter
    private final FloatBuffer projectionBuffer = BufferUtils.createFloatBuffer(16);
    @Getter
    private final FloatBuffer modelViewBuffer = BufferUtils.createFloatBuffer(16);
    @Getter
    private final Matrix4f projectionMatrix = new Matrix4f().identity();
    @Getter
    private final Matrix4f modelViewMatrix = new Matrix4f().identity();


    public void setCameraPosition(double x, double y, double z) {
        cameraPosition.set(x, y, z);
    }

    public void captureProjectionMatrix() {
        // Not very fast, but given we're not on 3.2+ core and are still using the opengl matrix stack, we don't have much alternative
        projectionBuffer.position(0);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projectionBuffer);
        projectionMatrix.set(projectionBuffer);
    }

    public void captureModelViewMatrix() {
        modelViewBuffer.position(0);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelViewBuffer);
        modelViewMatrix.set(modelViewBuffer);
    }

    public void setProjectionMatrix(FloatBuffer projection) {
        projectionMatrix.set(projection);
        projectionMatrix.get(0, projectionBuffer);
    }

    public void setModelViewMatrix(FloatBuffer modelview) {
        modelViewMatrix.set(modelview);
        modelViewMatrix.get(0, modelViewBuffer);
    }
}
