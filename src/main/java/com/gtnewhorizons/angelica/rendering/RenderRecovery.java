package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.hudcaching.HUDCaching;
import com.gtnewhorizons.angelica.mixins.interfaces.FontRendererAccessor;
import com.gtnewhorizons.angelica.rendering.tesr.TesrAttribution;
import com.gtnewhorizons.angelica.rendering.tesr.TesrBlendScope;
import com.gtnewhorizons.angelica.rendering.tesr.TesrLifecycle;
import com.prupe.mcpatcher.ctm.CTMUtils;
import net.coderbot.iris.Iris;
import net.coderbot.iris.pipeline.PipelineManager;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

public final class RenderRecovery {

    private RenderRecovery() {}

    private static boolean crashTestArmed;

    public static void resetAfterCrash() {
        DisplayListManager.abortCompilation();
        final PipelineManager pipelineManager = Iris.getPipelineManagerNullable();
        if (pipelineManager != null) {
            pipelineManager.destroyPipeline();
        }
        GLStateManager.reset();
        GLDebug.resetGroupStack();
        final Minecraft mc = Minecraft.getMinecraft();
        resetFont(mc.fontRenderer);
        resetFont(mc.standardGalacticFontRenderer);
        HUDCaching.resetAfterCrash();
        mc.getFramebuffer().bindFramebuffer(false);
        TesrLifecycle.reset();
        TesrBlendScope.reset();
        TesrAttribution.currentRenderable = null;
        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(0);
        CTMUtils.clearCurrentCompact();
    }

    private static void resetFont(FontRenderer fr) {
        if (fr instanceof FontRendererAccessor a && a.angelica$getBatcher() != null) a.angelica$getBatcher().resetAfterCrash();
    }

    public static void armCrashTest() {
        crashTestArmed = true;
    }

    public static void throwIfCrashTestArmed() {
        if (crashTestArmed) {
            crashTestArmed = false;
            throw new IllegalStateException("angelica crashtest");
        }
    }
}
