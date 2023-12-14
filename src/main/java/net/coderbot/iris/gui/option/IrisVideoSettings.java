package net.coderbot.iris.gui.option;

import java.io.IOException;

import net.coderbot.iris.Iris;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;

public class IrisVideoSettings {
    public static int shadowDistance = 32;

    // TODO: Tell the user to check in the shader options once that's supported.
    private static final String DISABLED_TOOLTIP = I18n.format("options.iris.shadowDistance.disabled");
    private static final String ENABLED_TOOLTIP = I18n.format("options.iris.shadowDistance.enabled");

    public static int getOverriddenShadowDistance(int base) {
        return Iris.getPipelineManager().getPipeline()
            .map(pipeline -> pipeline.getForcedShadowRenderDistanceChunksForDisplay().orElse(base))
            .orElse(base);
    }

    public static boolean isShadowDistanceSliderEnabled() {
        return Iris.getPipelineManager().getPipeline()
            .map(pipeline -> !pipeline.getForcedShadowRenderDistanceChunksForDisplay().isPresent())
            .orElse(true);
    }

}
