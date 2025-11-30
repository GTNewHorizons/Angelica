package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.opengl.GL11;

/**
 * Command: glLoadIdentity()
 * Replaces the current matrix with the identity matrix for the specified matrix mode.
 */
@Desugar
public record LoadIdentityCmd(int matrixMode) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glLoadIdentity();
    }

    public boolean isModelView() {
        return matrixMode == GL11.GL_MODELVIEW;
    }

    @Override
    public boolean handleOptimization(OptimizationContext ctx) {
        if (isModelView()) {
            ctx.loadIdentity();
            return false;  // Accumulated, don't emit
        }
        return true;  // Non-MODELVIEW, emit as-is
    }

    @Override
    public @NotNull String toString() {
        return String.format("LoadIdentity(%s)", GLDebug.getMatrixModeName(matrixMode));
    }
}
