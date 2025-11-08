package com.gtnewhorizons.angelica.compat.hextext.render;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Sanitized text and directive buckets returned by HexText's preprocessing pipeline.
 */
public final class HexTextRenderData {

    private static final HexTextRenderData UNCHANGED = new HexTextRenderData(false, "", Collections.emptyMap());

    private final boolean shouldReplaceText;
    private final String displayText;
    private final Map<Integer, List<CompatRenderInstruction>> instructions;

    public HexTextRenderData(boolean shouldReplaceText, String displayText,
                             Map<Integer, List<CompatRenderInstruction>> instructions) {
        this.shouldReplaceText = shouldReplaceText;
        this.displayText = displayText == null ? "" : displayText;
        this.instructions = instructions == null ? Collections.emptyMap() : instructions;
    }

    public boolean shouldReplaceText() {
        return shouldReplaceText;
    }

    public String getDisplayText() {
        return displayText;
    }

    public Map<Integer, List<CompatRenderInstruction>> getInstructions() {
        return instructions;
    }

    public boolean hasInstructions() {
        return !instructions.isEmpty();
    }

    public static HexTextRenderData unchanged() {
        return UNCHANGED;
    }
}
