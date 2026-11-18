package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.glsm.GLSMFramebuffer;
import com.gtnewhorizons.angelica.glsm.GLSMPrograms;
import net.coderbot.iris.Iris;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;

public final class PassRebindGate {

    private static int lastProgramGen = -1;
    private static int lastFbGen = -1;

    private PassRebindGate() {}

    public static void rebindIfDirty() {
        if (!Iris.enabled) return;
        if (GLSMPrograms.getProgramGeneration() == lastProgramGen
            && GLSMFramebuffer.getDrawFramebufferGeneration() == lastFbGen) {
            return;
        }
        final WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline != null) {
            pipeline.rebindCurrentPass();
        }
        lastProgramGen = GLSMPrograms.getProgramGeneration();
        lastFbGen = GLSMFramebuffer.getDrawFramebufferGeneration();
    }
}
