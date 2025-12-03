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
    public boolean needsTransformSync() {
        return true;
    }

    @Override
    public boolean handleOptimization(OptimizationContext ctx) {
        ctx.emitPendingTransform();
        return true;
    }

    @Override
    public @NotNull String toString() {
        return "PopAttrib()";
    }
}
