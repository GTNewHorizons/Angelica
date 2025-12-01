package com.gtnewhorizons.angelica.glsm.recording.commands;

import org.joml.Matrix4f;

/**
 * Context provided to commands during display list optimization.
 * Allows commands to interact with the transform optimizer and draw batcher.
 */
public interface OptimizationContext {
    /** Get the accumulated MODELVIEW transform matrix. */
    Matrix4f getAccumulatedTransform();

    /** Reset accumulated transform to identity. */
    void loadIdentity();

    /** Push current accumulated transform onto stack. */
    void pushTransform();

    /** Pop transform from stack, restoring previous accumulated value. */
    void popTransform();

    /** Emit a MultMatrix command if accumulated differs from last emitted. */
    void emitPendingTransform();

    /** Emit a MultMatrix command to reach the target transform. */
    void emitTransformTo(Matrix4f target);

    /** Add a command to the output list. */
    void emit(DisplayListCommand cmd);
}
