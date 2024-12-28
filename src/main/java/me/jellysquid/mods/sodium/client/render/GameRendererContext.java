package me.jellysquid.mods.sodium.client.render;

import com.gtnewhorizon.gtnhlib.client.lwjgl3.MemoryStack;
import com.gtnewhorizons.angelica.compat.toremove.MatrixStack;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.rendering.RenderingState;
import java.nio.FloatBuffer;
import net.coderbot.iris.shadows.ShadowRenderingState;
import org.joml.Matrix4f;

public class GameRendererContext {
    /**
     * Obtains a model-view-projection matrix by multiplying the projection matrix with the model-view matrix
     * from {@param matrices}.
     *
     * @return A float-buffer on the stack containing the model-view-projection matrix in a format suitable for
     * uploading as uniform state
     */
    public static FloatBuffer getModelViewProjectionMatrix(MatrixStack.Entry matrices, MemoryStack stack) {
        final FloatBuffer bufModelViewProjection = stack.mallocFloat(16);
        final Matrix4f projectionMatrix = (AngelicaConfig.enableIris && ShadowRenderingState.areShadowsCurrentlyBeingRendered()) ? ShadowRenderingState.getShadowOrthoMatrix() : RenderingState.INSTANCE.getProjectionMatrix();
        final Matrix4f matrix = new Matrix4f(projectionMatrix);
        matrix.mul(matrices.getModel());

        matrix.get(bufModelViewProjection);

        return bufModelViewProjection;
    }
}
