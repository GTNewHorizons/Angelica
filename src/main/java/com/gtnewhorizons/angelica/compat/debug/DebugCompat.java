package com.gtnewhorizons.angelica.compat.debug;

import com.gtnewhorizons.angelica.api.debug.DebugEntryCategory;
import com.gtnewhorizons.angelica.api.debug.DebugEntryHandle;
import com.gtnewhorizons.angelica.api.debug.DebugEntrySpec;
import com.gtnewhorizons.angelica.api.debug.DebugEntrySpec.DebugEntryFactory;
import com.gtnewhorizons.angelica.api.debug.DebugGroupHandle;
import com.gtnewhorizons.angelica.api.debug.DebugGroupSpec;
import com.gtnewhorizons.angelica.api.debug.DebugScreenApi;
import com.gtnewhorizons.angelica.api.debug.DebugScreenEntryStatus;
import com.gtnewhorizons.angelica.debug.screen.DebugRegistry;
import cpw.mods.fml.common.Loader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class DebugCompat {

    private static final Logger LOGGER = LogManager.getLogger("Angelica");

    private DebugCompat() {}

    public static void registerMissing() {
        if (Loader.isModLoaded("Botania")) registerMissing("botania", BotaniaDebugEntry::register);
        if (Loader.isModLoaded("journeymap")) registerMissing("journeymap", JourneyMapDebugEntry::register);
        if (Loader.isModLoaded("hodgepodge")) registerMissing("hodgepodge", HodgepodgeDebugEntry::register);
    }

    static void registerMissing(String namespace, Runnable register) {
        if (DebugRegistry.MAIN.hasNamespace(namespace)) return;
        try {
            register.run();
        } catch (RuntimeException | LinkageError e) {
            LOGGER.error("Debug screen compat entry for {} failed to register", namespace, e);
        }
    }

    static boolean active(DebugEntryHandle handle) {
        return handle != null && !handle.isFailed();
    }

    static DebugEntryHandle register(String groupId, String entryId, String name, int accent, DebugEntryFactory factory) {
        DebugScreenApi.registerGroup(new DebugGroupSpec(groupId).title(name).accent(accent).side(DebugGroupHandle.LEFT));
        return DebugScreenApi.registerEntry(new DebugEntrySpec(entryId, DebugEntryCategory.SCREEN_TEXT)
            .name(name)
            .defaults(DebugScreenEntryStatus.IN_OVERLAY, DebugScreenEntryStatus.NEVER)
            .text(factory));
    }
}
