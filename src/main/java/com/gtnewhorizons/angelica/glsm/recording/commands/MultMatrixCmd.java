package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

/**
 * Command: glMultMatrix(matrix)
 * Multiplies the specified matrix mode by the specified matrix.
 */
@Desugar
public record MultMatrixCmd(Matrix4f matrix, int matrixMode, FloatBuffer buffer) implements AccumulableMatrixCommand {

    /**
     * Create a MultMatrixCmd with pre-allocated buffer.
     */
    public static MultMatrixCmd create(Matrix4f matrix, int matrixMode) {
        final FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
        matrix.get(buffer);
        buffer.rewind();
        return new MultMatrixCmd(new Matrix4f(matrix), matrixMode, buffer);
    }

    @Override
    public boolean isModelView() {
        return matrixMode == GL11.GL_MODELVIEW;
    }

    @Override
    public void applyTo(Matrix4f target) {
        target.mul(matrix);
    }

    @Override
    public void execute() {
        // Buffer already populated at construction, just rewind and use
        buffer.rewind();
        GLStateManager.glMultMatrix(buffer);
    }

    @Override
    public @NotNull String toString() {
        return String.format("MultMatrix(%s)", matrix);
    }
}
