package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.opengl.GL11;

/**
 * Command: glPushMatrix()
 * Pushes the current matrix onto the matrix stack for the specified matrix mode.
 */
@Desugar
public record PushMatrixCmd(int matrixMode) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glPushMatrix();
    }

    public boolean isModelView() {
        return matrixMode == GL11.GL_MODELVIEW;
    }

    @Override
    public boolean handleOptimization(OptimizationContext ctx) {
        if (isModelView()) {
            ctx.emitPendingTransform();
            ctx.pushTransform();
        }
        return true;  // Always emit Push
    }

    @Override
    public @NotNull String toString() {
        return String.format("PushMatrix(%s)", GLDebug.getMatrixModeName(matrixMode));
    }
}
