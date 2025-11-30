package com.gtnewhorizons.angelica.glsm.recording.commands;

import org.joml.Matrix4f;

/**
 * Interface for matrix commands that can be accumulated during transform optimization.
 * Implemented by TranslateCmd, RotateCmd, ScaleCmd, and MultMatrixCmd.
 */
public interface AccumulableMatrixCommand extends DisplayListCommand {
    boolean isModelView();
    void applyTo(Matrix4f matrix);

    @Override
    default boolean handleOptimization(OptimizationContext ctx) {
        if (isModelView()) {
            applyTo(ctx.getAccumulatedTransform());
            return false;  // Accumulated, don't emit
        }
        return true;  // Non-MODELVIEW, emit as-is
    }
}