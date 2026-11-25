package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.hooks.BatchStateGuard;
import com.gtnewhorizons.angelica.proxy.ClientProxy;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/// Draws what the batchers have queued before a blended `glBegin`, display list, Tessellator or unbatched template draw
/// of a block entity, which would otherwise land under batched geometry dispatched before it. Vertex array and buffer
/// draws are not hooked.
public final class ImmediateDrawOrder {

    private static final Runnable FLUSH = ImmediateDrawOrder::flush;

    private ImmediateDrawOrder() {}

    public static void beginPass() {
        if (!ClientProxy.options().performance.translucencyMode.sorts() || TesrBatchRenderer.deferredPipeline() != null) return;
        BatchStateGuard.drawFlush = FLUSH;
    }

    public static void endPass() {
        BatchStateGuard.drawFlush = null;
    }

    static void flush() {
        if (!GLSMBlend.getBlendMode().isEnabled()) return;
        // A Tessellator draw triggers this from inside Tessellator.draw, and pending draws use the Tessellator
        if (!Tessellator.instance.isDrawing) PendingTesrDraws.flush();
        final ModelPartBatcher models = ModelPartBatcher.INSTANCE;
        final TesrBatchRenderer tesrs = TesrBatchRenderer.INSTANCE;
        if (!models.hasQueuedGeometry() && !tesrs.hasQueuedGeometry()) return;
        final int mode = GLSMMatrices.getMatrixMode().getMode();
        final int unit = GLSMTextures.getActiveTextureUnit();
        try {
            GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
            TranslucentPeel.flush();
            models.flushForStateChange();
            tesrs.flushAndContinue();
        } finally {
            GLSMMatrices.glMatrixMode(mode);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        }
    }
}
