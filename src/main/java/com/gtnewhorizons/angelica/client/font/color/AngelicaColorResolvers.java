package com.gtnewhorizons.angelica.client.font.color;

import com.gtnewhorizons.angelica.client.font.BatchingFontRenderer;
import com.gtnewhorizons.angelica.compat.hextext.HexTextCompat;
import com.gtnewhorizons.angelica.compat.hextext.HexTextCompat.Bridge;
import com.gtnewhorizons.angelica.compat.hextext.HexTextServices;
import com.gtnewhorizons.angelica.config.FontConfig;

public final class AngelicaColorResolvers {

    private AngelicaColorResolvers() {
    }

    public static AngelicaColorResolver create(BatchingFontRenderer renderer, int[] vanillaPalette) {
        if (!FontConfig.enableHexTextCompat) {
            return new DefaultColorResolver(vanillaPalette);
        }

        if (HexTextServices.isSupported()) {
            Bridge bridge = HexTextCompat.tryCreateBridge();
            if (bridge != null) {
                return new HexTextColorResolver(vanillaPalette, bridge);
            }
        }

        return new DefaultColorResolver(vanillaPalette);
    }
}
