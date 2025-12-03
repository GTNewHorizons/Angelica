package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.opengl.GL11;

/**
 * Command: glPopMatrix()
 * Pops the current matrix from the matrix stack for the specified matrix mode.
 */
@Desugar
public record PopMatrixCmd(int matrixMode) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glPopMatrix();
    }

    public boolean isModelView() {
        return matrixMode == GL11.GL_MODELVIEW;
    }

    @Override
    public boolean handleOptimization(OptimizationContext ctx) {
        if (isModelView()) {
            ctx.popTransform();
        }
        return true;  // Always emit Pop
    }

    @Override
    public @NotNull String toString() {
        return String.format("PopMatrix(%s)", GLDebug.getMatrixModeName(matrixMode));
    }
}
