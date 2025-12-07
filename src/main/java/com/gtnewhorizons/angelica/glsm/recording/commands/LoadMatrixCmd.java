package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

/**
 * Command: glLoadMatrix(matrix)
 * Replaces the current matrix with the specified matrix for the given matrix mode.
 *
 * <p>Unlike MultMatrixCmd which can be absorbed into the accumulated transform,
 * LoadMatrixCmd must always be emitted because it sets an absolute value, not a relative one.
 * After a LoadMatrix command, the accumulated transform is reset to identity since
 * subsequent transforms are relative to the newly loaded matrix.</p>
 */
@Desugar
public record LoadMatrixCmd(Matrix4f matrix, int matrixMode, FloatBuffer buffer) implements DisplayListCommand {

    /**
     * Create a LoadMatrixCmd with pre-allocated buffer.
     */
    public static LoadMatrixCmd create(Matrix4f matrix, int matrixMode) {
        final FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
        matrix.get(buffer);
        buffer.rewind();
        return new LoadMatrixCmd(new Matrix4f(matrix), matrixMode, buffer);
    }

    /**
     * Create a LoadMatrixCmd from a FloatBuffer (copies the data).
     */
    public static LoadMatrixCmd create(FloatBuffer src, int matrixMode) {
        final Matrix4f matrix = new Matrix4f().set(src);
        src.rewind();
        return create(matrix, matrixMode);
    }

    public boolean isModelView() {
        return matrixMode == GL11.GL_MODELVIEW;
    }

    @Override
    public void execute() {
        // Buffer already populated at construction, just rewind and use
        buffer.rewind();
        GLStateManager.glLoadMatrix(buffer);
    }

    @Override
    public boolean handleOptimization(OptimizationContext ctx) {
        if (isModelView()) {
            // LoadMatrix sets an absolute value - emit any pending relative transform first
            ctx.emitPendingTransform();
            // Emit this command
            ctx.emit(this);
            // Reset accumulated to identity - subsequent transforms are relative to the loaded matrix
            ctx.loadIdentity();
            // Mark that GL is at an absolute value - LoadIdentity must emit to reset
            ctx.markAbsoluteMatrix();
            return false;  // Already emitted
        }
        return true;  // Non-MODELVIEW, emit as-is
    }

    @Override
    public @NotNull String toString() {
        return String.format("LoadMatrix(%s, %s)", GLDebug.getMatrixModeName(matrixMode), matrix);
    }
}
