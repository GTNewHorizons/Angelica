package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * Command: glTranslatef() or glTranslated()
 * Stores as doubles for precision.
 */
@Desugar
public record TranslateCmd(double x, double y, double z, int matrixMode) implements AccumulableMatrixCommand {
    @Override
    public void execute() {
        // Save current mode
        final int savedMode = GLStateManager.getMatrixMode().getMode();

        // Switch to the mode this was recorded with
        GLStateManager.glMatrixMode(matrixMode);
        GLStateManager.glTranslated(x, y, z);

        // Restore mode
        GLStateManager.glMatrixMode(savedMode);
    }

    @Override
    public boolean isModelView() {
        return matrixMode == GL11.GL_MODELVIEW;
    }

    @Override
    public void applyTo(Matrix4f matrix) {
        matrix.translate((float) x, (float) y, (float) z);
    }

    @Override
    public @NotNull String toString() {
        return String.format("Translate(%.2f, %.2f, %.2f, %s)", x, y, z, GLDebug.getMatrixModeName(matrixMode));
    }
}
