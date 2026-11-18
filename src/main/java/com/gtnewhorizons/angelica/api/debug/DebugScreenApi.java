package com.gtnewhorizons.angelica.api.debug;

import com.gtnewhorizons.angelica.debug.screen.DebugRegistry;
import com.gtnewhorizons.angelica.debug.screen.DebugScreen;

public final class DebugScreenApi {

    private DebugScreenApi() {}

    public static DebugGroupHandle registerGroup(DebugGroupSpec spec) {
        DebugScreen.bootstrap();
        return DebugRegistry.MAIN.registerGroup(spec);
    }

    public static DebugEntryHandle registerEntry(DebugEntrySpec spec) {
        DebugScreen.bootstrap();
        return DebugRegistry.MAIN.registerEntry(spec);
    }

    public static DebugGroupHandle group(String id) {
        DebugScreen.bootstrap();
        return DebugRegistry.MAIN.groupById(id);
    }

    public static DebugEntryHandle entry(String id) {
        DebugScreen.bootstrap();
        return DebugRegistry.MAIN.entryById(id);
    }
}
