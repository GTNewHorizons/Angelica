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
        AngelicaColorResolver fallback = new DefaultColorResolver(vanillaPalette);
        if (!FontConfig.enableHexTextCompat) {
            return fallback;
        }

        return new SwitchingColorResolver(vanillaPalette, fallback);
    }

    private static final class SwitchingColorResolver implements AngelicaColorResolver {

        private final int[] vanillaPalette;
        private final AngelicaColorResolver fallback;

        private volatile HexTextColorResolver hexResolver;

        private SwitchingColorResolver(int[] vanillaPalette, AngelicaColorResolver fallback) {
            this.vanillaPalette = vanillaPalette;
            this.fallback = fallback;
        }

        @Override
        public ResolvedText resolve(CharSequence text, int start, int end, int baseColor, int baseShadowColor) {
            if (!FontConfig.enableHexTextCompat) {
                hexResolver = null;
                return fallback.resolve(text, start, end, baseColor, baseShadowColor);
            }

            if (HexTextServices.isSupported()) {
                HexTextColorResolver resolver = hexResolver;
                if (resolver == null) {
                    Bridge bridge = HexTextCompat.tryCreateBridge();
                    if (bridge != null) {
                        resolver = new HexTextColorResolver(vanillaPalette, bridge);
                        hexResolver = resolver;
                    } else {
                        return fallback.resolve(text, start, end, baseColor, baseShadowColor);
                    }
                }
                return resolver.resolve(text, start, end, baseColor, baseShadowColor);
            }

            hexResolver = null;
            return fallback.resolve(text, start, end, baseColor, baseShadowColor);
        }
    }
}
