package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.opengl.GL11;

/**
 * Command: glPushAttrib(mask)
 * Pushes the server attribute stack.
 */
@Desugar
public record PushAttribCmd(int mask) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glPushAttrib(mask);
    }

    @Override
    public boolean needsTransformSync() {
        // Only need transform sync if we're pushing transform state.
        // GL_TRANSFORM_BIT includes the matrix mode and matrix stacks.
        return (mask & GL11.GL_TRANSFORM_BIT) != 0;
    }

    @Override
    public boolean handleOptimization(OptimizationContext ctx) {
        if (needsTransformSync()) {
            ctx.emitPendingTransform();
        }
        return true;
    }

    @Override
    public @NotNull String toString() {
        return String.format("PushAttrib(0x%08X)", mask);
    }
}
