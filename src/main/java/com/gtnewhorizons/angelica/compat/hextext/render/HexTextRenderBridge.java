package com.gtnewhorizons.angelica.compat.hextext.render;

import com.gtnewhorizons.angelica.compat.ModStatus;
import com.gtnewhorizons.angelica.compat.hextext.HexTextCompat;
import com.gtnewhorizons.angelica.compat.hextext.HexTextServices;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kamkeel.hextext.api.rendering.RenderDirective;
import kamkeel.hextext.api.rendering.RenderPlan;
import kamkeel.hextext.api.rendering.TextRenderService;

/**
 * Bridges render preprocessing information from HexText to Angelica's colour resolver.
 */
public final class HexTextRenderBridge implements HexTextCompat.Bridge {

    @Override
    public HexTextRenderData prepare(CharSequence text, boolean rawMode) {
        String asString = text == null ? "" : text.toString();

        TextRenderService service = HexTextServices.textRenderer();
        if (service == null) {
            return new HexTextRenderData(false, asString, null);
        }

        try {
            RenderPlan plan = service.prepare(asString, rawMode);
            if (plan == null) {
                return new HexTextRenderData(false, asString, null);
            }

            String sanitized = asString;
            if (plan.shouldReplaceText() && plan.getDisplayText() != null) {
                sanitized = plan.getDisplayText();
            }

            Map<Integer, List<CompatRenderInstruction>> instructions = convert(plan.getInstructions());
            return new HexTextRenderData(plan.shouldReplaceText(), sanitized, instructions);
        } catch (Throwable t) {
            ModStatus.LOGGER.warn("Failed to query HexText render data", t);
            return new HexTextRenderData(false, asString, null);
        }
    }

    private Map<Integer, List<CompatRenderInstruction>> convert(
        Map<Integer, List<RenderDirective>> source
    ) {
        if (source == null || source.isEmpty()) {
            return new HashMap<>();
        }

        Map<Integer, List<CompatRenderInstruction>> converted = new HashMap<>(source.size());
        for (Map.Entry<Integer, List<RenderDirective>> entry : source.entrySet()) {
            List<RenderDirective> directives = entry.getValue();
            if (directives == null || directives.isEmpty()) {
                continue;
            }

            List<CompatRenderInstruction> bucket = new ArrayList<>(directives.size());
            for (RenderDirective directive : directives) {
                CompatRenderInstruction convertedDirective = RenderDirectiveAdapter.adapt(directive);
                if (convertedDirective != null) {
                    bucket.add(convertedDirective);
                }
            }

            if (!bucket.isEmpty()) {
                converted.put(entry.getKey(), bucket);
            }
        }
        return converted;
    }
}
