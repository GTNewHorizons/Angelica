package com.gtnewhorizons.angelica.compat.hextext;

import com.gtnewhorizons.angelica.compat.ModStatus;
import com.gtnewhorizons.angelica.config.FontConfig;

/**
 * Factory for creating HexText token highlighters.
 */
public final class HexTextHighlightBridgeFactory {

    private HexTextHighlightBridgeFactory() {
    }

    public static HexTextHighlighter create() {
        if (!FontConfig.enableHexTextCompat || !ModStatus.isHexTextLoaded) {
            return HexTextHighlighter.NOOP;
        }
        try {
            return new DirectHexTextHighlighter();
        } catch (Throwable t) {
            ModStatus.LOGGER.warn("Failed to initialize HexText token highlighting", t);
            return HexTextHighlighter.NOOP;
        }
    }
}
