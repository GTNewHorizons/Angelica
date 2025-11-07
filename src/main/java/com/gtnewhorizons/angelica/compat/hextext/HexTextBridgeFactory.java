package com.gtnewhorizons.angelica.compat.hextext;

import com.gtnewhorizons.angelica.compat.ModStatus;

public final class HexTextBridgeFactory {

    private HexTextBridgeFactory() {
    }

    public static HexTextBridge tryCreate() {
        try {
            return new DirectHexTextBridge();
        } catch (Throwable t) {
            ModStatus.LOGGER.warn("Failed to initialize HexText compatibility layer", t);
            return null;
        }
    }
}
