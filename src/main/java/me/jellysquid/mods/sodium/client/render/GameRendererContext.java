package me.jellysquid.mods.sodium.client.render;

import com.gtnewhorizons.angelica.compat.mojang.MatrixStack;
import com.gtnewhorizons.angelica.rendering.RenderingState;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

public class GameRendererContext {
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
        final FloatBuffer bufModelViewProjection = BufferUtils.createFloatBuffer(16);
        final Matrix4f matrix = new Matrix4f(RenderingState.INSTANCE.getProjectionMatrix());
        matrix.mul(matrices.getModel());

        matrix.get(bufModelViewProjection);

        return bufModelViewProjection;
    }
}
