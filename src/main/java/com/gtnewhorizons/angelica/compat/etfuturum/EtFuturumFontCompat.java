package com.gtnewhorizons.angelica.compat.etfuturum;

import ganymedes01.etfuturum.configuration.configs.ConfigMixins;

public final class EtFuturumFontCompat {

    public static final boolean MODERN_FONT_ENABLED = detect();

    private EtFuturumFontCompat() {
    }

    private static boolean detect() {
        try {
            return ConfigMixins.unicodeFontPages;
        } catch (LinkageError e) {
            return false;
        }
    }
}
