package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.hooks.BatchStateGuard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/** Rare, uncaptured state changes end the current batch before the mutation takes effect. */
final class BatchStateFallback {
    private static final Runnable FLUSH = BatchStateFallback::flush;

    static void install() { BatchStateGuard.flush = FLUSH; }

    private static void flush() {
        final ModelPartBatcher models = ModelPartBatcher.INSTANCE;
        final TesrBatchRenderer tesrs = TesrBatchRenderer.INSTANCE;
        if (!models.isActive() && !tesrs.hasPendingGeometry()) return;
        // Pass setup may establish defaults after beginPass, before any renderer or queued draw.
        if (!models.hasQueuedGeometry() && !tesrs.hasQueuedGeometry() && !BatchEligibility.insideRenderer()) return;
        final int mode = GLStateManager.getMatrixMode().getMode();
        final int unit = GLStateManager.getActiveTextureUnit();
        BatchEligibility.onUncapturedState();
        final long before = GLStateManager.drawCalls;
        try {
            GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
            models.flush();
            tesrs.flushForStateChange();
        } finally {
            GLStateManager.glMatrixMode(mode);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            BatchEligibility.onBatchFlushed(GLStateManager.drawCalls - before);
        }
    }
}
