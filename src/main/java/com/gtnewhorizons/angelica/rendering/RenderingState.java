package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import lombok.Getter;
import lombok.Setter;
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
    @Getter
    @Setter
    private float fov;


    public void setCameraPosition(double x, double y, double z) {
        cameraPosition.set(x, y, z);
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
