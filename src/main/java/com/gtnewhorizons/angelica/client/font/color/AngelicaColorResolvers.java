package com.gtnewhorizons.angelica.client.font.color;

import com.gtnewhorizons.angelica.client.font.BatchingFontRenderer;
import com.gtnewhorizons.angelica.compat.ModStatus;
import com.gtnewhorizons.angelica.compat.hextext.HexTextBridge;
import com.gtnewhorizons.angelica.compat.hextext.HexTextBridgeFactory;
import com.gtnewhorizons.angelica.config.FontConfig;

public final class AngelicaColorResolvers {

    private AngelicaColorResolvers() {
    }

    public static AngelicaColorResolver create(BatchingFontRenderer renderer, int[] vanillaPalette) {
        if (!FontConfig.enableHexTextCompat) {
            return new DefaultColorResolver(vanillaPalette);
        }

        if (ModStatus.isHexTextLoaded) {
            HexTextBridge bridge = HexTextBridgeFactory.tryCreate();
            if (bridge != null) {
                return new HexTextColorResolver(vanillaPalette, bridge);
            }
        }

        return new DefaultColorResolver(vanillaPalette);
    }
}
