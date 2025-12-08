package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * Command: glScalef() or glScaled()
 * Stores as doubles for precision.
 */
@Desugar
public record ScaleCmd(double x, double y, double z, int matrixMode) implements AccumulableMatrixCommand {
    @Override
    public void execute() {
        GLStateManager.glScaled(x, y, z);
    }

    @Override
    public boolean isModelView() {
        return matrixMode == GL11.GL_MODELVIEW;
    }

    @Override
    public void applyTo(Matrix4f matrix) {
        matrix.scale((float) x, (float) y, (float) z);
    }

    @Override
    public @NotNull String toString() {
        return String.format("Scale(%.2f, %.2f, %.2f, %s)", x, y, z, GLDebug.getMatrixModeName(matrixMode));
    }
}
