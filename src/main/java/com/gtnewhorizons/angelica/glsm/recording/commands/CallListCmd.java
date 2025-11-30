package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.recording.CompiledDisplayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * Command: Call another display list (glCallList)
 * Records a reference to another display list to be executed during playback.
 * Per OpenGL spec: the referenced list is NOT executed during compilation,
 * only the reference is recorded.
 *
 */
@Desugar
public record CallListCmd(int listId) implements DisplayListCommand {
    private static final Logger LOGGER = LogManager.getLogger("CallListCmd");

    @Override
    public void execute() {
        final CompiledDisplayList compiled = GLStateManager.getDisplayList(listId);

        if (compiled != null) {
            GLDebug.pushGroup("CallList(" + listId + ")");
            try {
                compiled.render();
            } finally {
                GLDebug.popGroup();
            }
        } else {
            // Fallback if list not found (shouldn't happen in normal operation)
            LOGGER.warn("[CallListCmd] List {} not found in cache, using fallback glCallList", listId);
            GLStateManager.glCallList(listId);
        }
    }

    @Override
    public void delete() {
        // No resources to free - we just hold a reference
    }

    @Override
    public boolean needsTransformSync() {
        return true;  // Nested list uses current GL matrix state
    }

    @Override
    public boolean handleOptimization(OptimizationContext ctx) {
        // Emit transform for pending batch draws, then flush
        final org.joml.Matrix4f batchTransform = ctx.getBatchTransform();
        if (batchTransform != null) {
            ctx.emitTransformTo(batchTransform);
        }
        ctx.flushBatcher();
        // Nested list needs current GL matrix state
        ctx.emitPendingTransform();
        return true;  // Emit this command
    }

    @Override
    public @NotNull String toString() {
        return "CallList(" + listId + ")";
    }
}
