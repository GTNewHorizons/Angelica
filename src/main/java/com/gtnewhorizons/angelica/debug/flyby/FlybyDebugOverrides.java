package com.gtnewhorizons.angelica.debug.flyby;

import com.gtnewhorizons.angelica.api.debug.DebugScreenEntryStatus;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.debug.screen.DebugEntryDef;
import com.gtnewhorizons.angelica.debug.screen.DebugRegistry;
import com.gtnewhorizons.angelica.debug.screen.DebugScreen;
import com.gtnewhorizons.angelica.debug.screen.DebugScreenEntryList;
import com.gtnewhorizons.angelica.debug.screen.DebugScreenProfile;
import com.gtnewhorizons.angelica.mixins.interfaces.IGameSettingsExt;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

import static com.gtnewhorizons.angelica.config.SystemProperties.FLYBY_DEBUG_ENTRIES;
import static com.gtnewhorizons.angelica.config.SystemProperties.FLYBY_DEBUG_FLAT;
import static com.gtnewhorizons.angelica.config.SystemProperties.FLYBY_DEBUG_HUD;
import static com.gtnewhorizons.angelica.config.SystemProperties.FLYBY_DEBUG_PROFILE;
import static com.gtnewhorizons.angelica.config.SystemProperties.FLYBY_FPS_GRAPH;
import static com.gtnewhorizons.angelica.config.SystemProperties.FLYBY_HUD_CACHING;

final class FlybyDebugOverrides {
    private static final Logger LOGGER = LogManager.getLogger("Angelica/Flyby");

    record Pair(String id, DebugScreenEntryStatus status) {}

    private Boolean hudSaved;
    private Boolean flatSaved;
    private Boolean cachingSaved;
    private Boolean fpsGraphSaved;
    private boolean sessionOverridden;

    static List<Pair> parseEntries(String raw) {
        final List<Pair> pairs = new ArrayList<>();
        if (raw == null) return pairs;
        for (final String part : raw.split(",")) {
            final String pair = part.trim();
            if (pair.isEmpty()) continue;
            final int eq = pair.lastIndexOf('=');
            if (eq <= 0 || eq == pair.length() - 1) {
                LOGGER.error("Flyby debugEntries: malformed pair '{}', expected id=status", pair);
                continue;
            }
            final String id = pair.substring(0, eq).trim();
            final String name = pair.substring(eq + 1).trim();
            final DebugScreenEntryStatus status = DebugScreenEntryStatus.bySerializedName(name);
            if (id.isEmpty() || status == null) {
                LOGGER.error("Flyby debugEntries: bad {} '{}' in '{}'", id.isEmpty() ? "id" : "status", id.isEmpty() ? id : name, pair);
                continue;
            }
            pairs.add(new Pair(id, status));
        }
        return pairs;
    }

    void apply(Minecraft mc) {
        if (FLYBY_DEBUG_HUD != null && this.hudSaved == null) {
            this.hudSaved = mc.gameSettings.showDebugInfo;
            mc.gameSettings.showDebugInfo = FLYBY_DEBUG_HUD;
        }
        if (FLYBY_DEBUG_FLAT != null && this.flatSaved == null) {
            this.flatSaved = AngelicaConfig.modernizeF3Screen;
            AngelicaConfig.modernizeF3Screen = !FLYBY_DEBUG_FLAT;
        }
        if (FLYBY_HUD_CACHING != null && this.cachingSaved == null) {
            this.cachingSaved = AngelicaConfig.hudCachingActive;
            AngelicaConfig.hudCachingActive = FLYBY_HUD_CACHING;
        }
        if (FLYBY_FPS_GRAPH != null && this.fpsGraphSaved == null) {
            final IGameSettingsExt settings = (IGameSettingsExt) mc.gameSettings;
            this.fpsGraphSaved = settings.angelica$showFpsGraph();
            settings.angelica$setShowFpsGraph(FLYBY_FPS_GRAPH);
        }
        this.applySession();
    }

    private void applySession() {
        final boolean wantProfile = !FLYBY_DEBUG_PROFILE.isEmpty();
        final boolean wantEntries = !FLYBY_DEBUG_ENTRIES.isEmpty();
        if (!wantProfile && !wantEntries) return;

        final DebugScreenEntryList entries = DebugScreen.ENTRIES;
        if (entries == null) {
            LOGGER.error("Flyby: debug entry list is not available, ignoring debugProfile and debugEntries");
            return;
        }
        final DebugRegistry registry = DebugRegistry.MAIN;
        this.sessionOverridden = true;

        if (wantProfile) {
            final DebugScreenProfile profile = DebugScreenProfile.bySerializedName(FLYBY_DEBUG_PROFILE);
            if (profile == null) {
                LOGGER.error("Flyby: unknown debugProfile '{}', expected default or performance", FLYBY_DEBUG_PROFILE);
            } else {
                for (int i = 0; i < registry.entryCount(); i++) {
                    final DebugEntryDef def = registry.entryAt(i);
                    entries.overrideForSession(def, def.profileStatus(profile));
                }
            }
        }

        for (final Pair pair : parseEntries(FLYBY_DEBUG_ENTRIES)) {
            final DebugEntryDef def = registry.entryById(pair.id());
            if (def == null) {
                LOGGER.error("Flyby debugEntries: unknown entry id '{}'", pair.id());
                continue;
            }
            entries.overrideForSession(def, pair.status());
        }
    }

    void restore(Minecraft mc) {
        if (this.hudSaved != null) {
            mc.gameSettings.showDebugInfo = this.hudSaved;
            this.hudSaved = null;
        }
        if (this.flatSaved != null) {
            AngelicaConfig.modernizeF3Screen = this.flatSaved;
            this.flatSaved = null;
        }
        if (this.cachingSaved != null) {
            AngelicaConfig.hudCachingActive = this.cachingSaved;
            this.cachingSaved = null;
        }
        if (this.fpsGraphSaved != null) {
            ((IGameSettingsExt) mc.gameSettings).angelica$setShowFpsGraph(this.fpsGraphSaved);
            this.fpsGraphSaved = null;
        }
        if (this.sessionOverridden) {
            final DebugScreenEntryList entries = DebugScreen.ENTRIES;
            if (entries != null) entries.clearSessionOverrides();
            this.sessionOverridden = false;
        }
    }
}
