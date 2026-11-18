package com.gtnewhorizons.angelica.debug.screen;

import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.api.debug.DebugContext;
import com.gtnewhorizons.angelica.api.debug.DebugScreenDisplayer;
import com.gtnewhorizons.angelica.compat.debug.DebugCompat;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.debug.screen.renderers.DebugRenderers;
import cpw.mods.fml.common.Loader;
import net.minecraft.client.Minecraft;

import java.io.File;

public final class DebugScreen {

    public static DebugScreenEntryList ENTRIES;

    private static final DebugContextImpl CONTEXT = new DebugContextImpl();

    private static boolean overlayVisible;
    private static int clientTick;
    private static boolean groupedFailed;
    private static boolean freezeAttempted;

    private DebugScreen() {}

    public static void bootstrap() {
        DebugGroup.bootstrap();
        DebugScreenEntries.bootstrap();
    }

    private static void freeze(File configDir) {
        bootstrap();
        DebugCompat.registerMissing();
        DebugRegistry.MAIN.freeze();
        final DebugScreenEntryList entries = new DebugScreenEntryList(DebugRegistry.MAIN);
        entries.setFile(new File(configDir, "angelica-debug-profile.json"));
        entries.setListener(DebugRenderers::onEnabledChanged);
        ENTRIES = entries;
        entries.load();
        entries.setOverlayVisible(overlayVisible);
    }

    public static void syncOverlayVisible() {
        final boolean visible = Minecraft.getMinecraft().gameSettings.showDebugInfo;
        overlayVisible = visible;
        final DebugScreenEntryList entries = ENTRIES;
        if (entries != null && entries.isOverlayVisible() != visible) entries.setOverlayVisible(visible);
    }

    public static void onClientTickStart() {
        if (!freezeAttempted) {
            freezeAttempted = true;
            try {
                freeze(Loader.instance().getConfigDir());
            } catch (RuntimeException | LinkageError e) {
                ENTRIES = null;
                DebugRegistry.MAIN.failAll();
                AngelicaMod.LOGGER.error("Failed to set up the debug screen; it stays disabled for this session", e);
            }
        }
        syncOverlayVisible();
        final DebugScreenEntryList entries = ENTRIES;
        if (entries != null) entries.refresh();
    }

    public static void onClientTickEnd() {
        clientTick++;
    }

    public static boolean isOverlayVisible() {
        return overlayVisible;
    }

    public static int clientTick() {
        return clientTick;
    }

    public static boolean isRendererVerbose() {
        return (SystemProperties.DEBUG_F3_DETAIL && DebugScreenEntries.ANGELICA_RENDERER.isEnabled()) || DebugScreenEntries.ANGELICA_RENDERER_VERBOSE.isEnabled();
    }

    public static boolean isOverlayIdle() {
        final DebugScreenEntryList entries = ENTRIES;
        return !overlayVisible && (entries == null || !entries.anyTextEnabled());
    }

    public static boolean isGroupedActive() {
        return AngelicaConfig.modernizeF3Screen && !groupedFailed && AngelicaConfig.enableFontRenderer;
    }

    public static void disableGrouped() {
        groupedFailed = true;
    }

    public static boolean isAlwaysOnTextAtTopLeft() {
        final DebugScreenEntryList entries = ENTRIES;
        return entries != null && isGroupedActive() && entries.anyTextAlwaysOn() && Minecraft.getMinecraft().theWorld != null;
    }

    public static void fail(DebugEntryDef def, Throwable cause) {
        def.registry.fail(def, cause);
    }

    static void display(DebugEntryDef def, DebugScreenDisplayer displayer, DebugContext context) {
        if (context.world() == null || context.player() == null) return;
        try {
            def.entryOrCreate().display(displayer, context);
        } catch (RuntimeException | LinkageError e) {
            fail(def, e);
        }
    }

    static void runEntries(DebugScreenDisplayer displayer, Minecraft mc, long version, DebugRegistry flatFrom, boolean alwaysOnOnly) {
        final DebugContextImpl context = CONTEXT;
        context.prepare(mc, System.nanoTime(), version);
        try {
            if (flatFrom != null) {
                for (int i = 0, n = flatFrom.flatCount(); i < n; i++) {
                    final DebugEntryDef def = flatFrom.flatAt(i);
                    if (!def.isEnabled()) continue;
                    context.beginEntry(def);
                    display(def, displayer, context);
                }
                return;
            }
            final DebugScreenEntryList entries = ENTRIES;
            for (int i = 0, n = entries.enabledCount(); i < n; i++) {
                final DebugEntryDef def = entries.enabledAt(i);
                if (!def.isScreenText()) continue;
                if (alwaysOnOnly && !def.isAlwaysOn()) continue;
                context.beginEntry(def);
                display(def, displayer, context);
            }
        } finally {
            context.release();
        }
    }
}
