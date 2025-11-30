package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glPopAttrib()
 * Pops the server attribute stack.
 */
@Desugar
public record PopAttribCmd() implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glPopAttrib();
    }

    @Override
    public boolean isBarrier() {
        return true;  // Attribute stack operations require full state sync
    }

    @Override
    public boolean needsTransformSync() {
        return true;
    }

    @Override
    public boolean handleOptimization(OptimizationContext ctx) {
        final org.joml.Matrix4f batchTransform = ctx.getBatchTransform();
        if (batchTransform != null) {
            ctx.emitTransformTo(batchTransform);
        }
        ctx.flushBatcher();
        ctx.emitPendingTransform();
        return true;
    }

    @Override
    public @NotNull String toString() {
        return "PopAttrib()";
    }
}
