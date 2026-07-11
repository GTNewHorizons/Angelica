package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.coderbot.iris.Iris;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;

public final class PassRebindGate {

    private static int lastProgramGen = -1;
    private static int lastFbGen = -1;

    private PassRebindGate() {}

    public static void rebindIfDirty() {
        if (!Iris.enabled) return;
        if (GLStateManager.programGeneration == lastProgramGen
            && GLStateManager.drawFramebufferGeneration == lastFbGen) {
            return;
        }
        final WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline != null) {
            pipeline.rebindCurrentPass();
        }
        lastProgramGen = GLStateManager.programGeneration;
        lastFbGen = GLStateManager.drawFramebufferGeneration;
    }
}
