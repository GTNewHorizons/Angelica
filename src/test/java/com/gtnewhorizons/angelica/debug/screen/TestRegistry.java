package com.gtnewhorizons.angelica.debug.screen;

import com.gtnewhorizons.angelica.api.debug.DebugEntryCategory;
import com.gtnewhorizons.angelica.api.debug.DebugEntrySpec;
import com.gtnewhorizons.angelica.api.debug.DebugGroupSpec;
import com.gtnewhorizons.angelica.api.debug.DebugScreenEntryStatus;

final class TestRegistry {

    private TestRegistry() {}

    static synchronized DebugRegistry main() {
        DebugScreen.bootstrap();
        DebugRegistry.MAIN.freeze();
        return DebugRegistry.MAIN;
    }

    static DebugEntrySpec text(String id) {
        return new DebugEntrySpec(id, DebugEntryCategory.SCREEN_TEXT).defaults(DebugScreenEntryStatus.IN_OVERLAY, DebugScreenEntryStatus.NEVER);
    }

    static DebugEntrySpec renderer(String id) {
        return new DebugEntrySpec(id, DebugEntryCategory.RENDERER).defaults(DebugScreenEntryStatus.NEVER, DebugScreenEntryStatus.NEVER);
    }

    static DebugGroup group(DebugRegistry registry, String id, int side, int order) {
        return registry.registerGroup(new DebugGroupSpec(id).side(side).order(order));
    }
}
