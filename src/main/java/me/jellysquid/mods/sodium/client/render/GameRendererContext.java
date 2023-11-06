package me.jellysquid.mods.sodium.client.render;

import com.gtnewhorizons.angelica.compat.mojang.MatrixStack;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

public class GameRendererContext {
    private static Matrix4f PROJECTION_MATRIX = new Matrix4f().identity();

    // TODO: Sodium - projection matrix
    // Sodium uses a mixin in `loadProjectionMatrix` to capture the matrix, unclear if this is available in 1.7.10
    public static void captureProjectionMatrix(Matrix4f matrix) {
        PROJECTION_MATRIX.set(matrix);
    }

    /**
     * TODO: Not accurate
     * Obtains a model-view-projection matrix by multiplying the projection matrix with the model-view matrix
     * from {@param matrices}.
     *
     * The returned buffer is only valid for the lifetime of {@param stack}.
     *
     * @return A float-buffer on the stack containing the model-view-projection matrix in a format suitable for
     * uploading as uniform state
     */
    public static FloatBuffer getModelViewProjectionMatrix(MatrixStack.Entry matrices) {
        FloatBuffer bufModelViewProjection = BufferUtils.createFloatBuffer(16);
        // TODO: Sodium - projection matrix
        Matrix4f matrix = new Matrix4f(PROJECTION_MATRIX);
        matrix.mul(matrices.getModel());

        matrix.get(bufModelViewProjection);

        return bufModelViewProjection;
    }
}
