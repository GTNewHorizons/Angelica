package com.gtnewhorizons.angelica.compat.chromatictooltips;

import cpw.mods.fml.common.Loader;

import java.lang.reflect.Field;

/**
 * ChromaticTooltips defers tooltip rendering: its {@code GuiContainerManager.renderToolTips}
 * mixin caches the tooltip and sets {@code TooltipHandler.renderLastTooltip = true}, then a
 * {@code DrawScreenEvent.Post} subscriber calls {@code drawLastTooltip} which renders if the
 * flag is set and clears it. Stereo posts the event twice (per eye); the LEFT post clears the
 * flag, so the RIGHT post has to re-arm to draw.
 *
 * <p>The mod is optional. The field lookup runs once at mod init and surfaces API drift loudly
 * via {@link ReflectiveOperationException}; the runtime re-arm path is exception-free after
 * {@code setAccessible(true)} succeeded at init.
 */
public final class ChromaticTooltipsCompat {

    private static final String MOD_ID = "chromatictooltips";
    private static final String TOOLTIP_HANDLER_FQCN = "com.slprime.chromatictooltips.TooltipHandler";
    private static final String FIELD_NAME = "renderLastTooltip";

    private static Field renderLastTooltipField;

    private ChromaticTooltipsCompat() {}

    /**
     * Resolves and caches the reflective handle. Throws if ChromaticTooltips is loaded but its
     * API surface has drifted — caller should fail mod init rather than silently degrade.
     */
    public static void init() throws ReflectiveOperationException {
        if (!Loader.isModLoaded(MOD_ID)) return;
        final Field f = Class.forName(TOOLTIP_HANDLER_FQCN).getDeclaredField(FIELD_NAME);
        f.setAccessible(true);
        renderLastTooltipField = f;
    }

    /** Re-arm the deferred-render flag between stereo eye Post events. No-op when not loaded. */
    public static void rearm() {
        if (renderLastTooltipField == null) return;
        try {
            renderLastTooltipField.setBoolean(null, true);
        } catch (IllegalAccessException e) {
            // setAccessible(true) was called in init(); reaching this implies a JVM/security
            // policy change at runtime, which we don't support.
            throw new AssertionError(e);
        }
    }
}
