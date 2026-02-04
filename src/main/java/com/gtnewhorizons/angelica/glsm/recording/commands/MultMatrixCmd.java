package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

/**
 * Command: glMultMatrix(matrix)
 * Multiplies the current matrix by the specified matrix.
 */
@Desugar
public record MultMatrixCmd(Matrix4f matrix, FloatBuffer buffer) implements DisplayListCommand {

    /**
     * Create a MultMatrixCmd with pre-allocated buffer.
     */
    public static MultMatrixCmd create(Matrix4f matrix) {
        final FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
        matrix.get(buffer);
        buffer.rewind();
        return new MultMatrixCmd(new Matrix4f(matrix), buffer);
    }

    @Override
    public void execute() {
        buffer.rewind();
        GLStateManager.glMultMatrix(buffer);
    }

    @Override
    public @NotNull String toString() {
        return String.format("MultMatrix(%s)", matrix);
    }
}
