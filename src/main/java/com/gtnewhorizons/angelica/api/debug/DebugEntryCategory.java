package com.gtnewhorizons.angelica.api.debug;

public enum DebugEntryCategory {

    SCREEN_TEXT("angelica.debug.options.category.text"),
    RENDERER("angelica.debug.options.category.renderer");

    public final String translationKey;

    DebugEntryCategory(String translationKey) {
        this.translationKey = translationKey;
    }
}
