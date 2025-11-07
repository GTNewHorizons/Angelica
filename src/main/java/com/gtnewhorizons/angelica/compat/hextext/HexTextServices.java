package com.gtnewhorizons.angelica.compat.hextext;

import com.gtnewhorizons.angelica.compat.ModStatus;
import kamkeel.hextext.api.HexTextApi;
import kamkeel.hextext.api.rendering.TextRenderService;
import kamkeel.hextext.api.rendering.TokenHighlightService;
import kamkeel.hextext.api.text.TextFormatter;

/**
 * Centralized accessors for the public HexText API surface.
 */
public final class HexTextServices {

    private HexTextServices() {}

    public static TextRenderService textRenderer() {
        try {
            return HexTextApi.textRenderer();
        } catch (Throwable t) {
            ModStatus.LOGGER.warn("Failed to query HexText text renderer", t);
            return null;
        }
    }

    public static TokenHighlightService tokenHighlighter() {
        try {
            return HexTextApi.tokenHighlighter();
        } catch (Throwable t) {
            ModStatus.LOGGER.warn("Failed to query HexText token highlight service", t);
            return null;
        }
    }

    public static TextFormatter textFormatter() {
        try {
            return HexTextApi.textFormatter();
        } catch (Throwable t) {
            ModStatus.LOGGER.warn("Failed to query HexText text formatter", t);
            return null;
        }
    }
}
