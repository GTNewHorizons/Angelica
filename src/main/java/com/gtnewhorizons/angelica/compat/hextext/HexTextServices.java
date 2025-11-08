package com.gtnewhorizons.angelica.compat.hextext;

import com.gtnewhorizons.angelica.compat.ModStatus;
import cpw.mods.fml.common.Loader;
import kamkeel.hextext.api.HexTextApi;
import kamkeel.hextext.api.rendering.ColorService;
import kamkeel.hextext.api.rendering.DynamicEffectService;
import kamkeel.hextext.api.rendering.RenderPlan;
import kamkeel.hextext.api.rendering.RenderingEnvironmentService;
import kamkeel.hextext.api.rendering.TextRenderService;
import kamkeel.hextext.api.rendering.TokenHighlightService;
import kamkeel.hextext.api.text.TextFormatter;
import net.minecraft.client.gui.FontRenderer;

import java.util.regex.Pattern;

/**
 * Centralised access to HexText's public API surface.
 * <p>
 * The HexText bridge is resolved once during mod compatibility initialisation
 * and reused for the lifetime of the game session.
 */
public final class HexTextServices {

    private static final Pattern VERSION_SPLIT = Pattern.compile("\\.");
    private static final String MINIMUM_VERSION = "1.1.0";
    private static final int REQUIRED_MAJOR_VERSION = parsePart(VERSION_SPLIT.split(MINIMUM_VERSION)[0]);

    private static volatile CompatState state = CompatState.uninitialised();
    private static volatile boolean diagnosticsReported;

    private HexTextServices() {
    }

    /**
     * Probes HexText availability and caches the result for subsequent lookups.
     */
    public static void init() {
        if (state.initialised) {
            return;
        }
        synchronized (HexTextServices.class) {
            if (state.initialised) {
                return;
            }
            state = CompatState.detect();
        }
    }

    private static CompatState state() {
        if (!state.initialised) {
            init();
        }
        return state;
    }

    public static boolean isSupported() {
        return state().supported;
    }

    public static String apiVersion() {
        return state().apiVersion;
    }

    public static TextRenderService textRenderer() {
        return state().textRenderer;
    }

    public static RenderPlan prepare(TextRenderService renderer, String text, boolean rawMode) {
        return renderer == null ? null : renderer.prepare(text, rawMode);
    }

    public static TokenHighlightService tokenHighlighter() {
        return state().tokenHighlighter;
    }

    public static TextFormatter textFormatter() {
        return state().textFormatter;
    }

    public static RenderingEnvironmentService renderEnvironment() {
        return state().environmentService;
    }

    public static DynamicEffectService dynamicEffects() {
        return state().dynamicEffects;
    }

    public static ColorService colorService() {
        return state().colorService;
    }

    public static boolean isRawTextRendering() {
        RenderingEnvironmentService environment = renderEnvironment();
        return environment != null && environment.isRawTextRendering();
    }

    public static TokenHighlightService.WidthProvider createWidthProvider(FontRenderer renderer) {
        if (renderer == null) {
            return null;
        }
        return text -> renderer.getStringWidth(text == null ? "" : text);
    }

    public static void reportDiagnostics() {
        CompatState current = state();
        if (diagnosticsReported) {
            return;
        }
        diagnosticsReported = true;
        current.logDiagnostics();
    }

    private static boolean isVersionSupported(String detected) {
        if (detected == null) {
            return false;
        }
        String[] detectedParts = VERSION_SPLIT.split(detected);
        if (detectedParts.length == 0 || parsePart(detectedParts[0]) != REQUIRED_MAJOR_VERSION) {
            return false;
        }

        String[] requiredParts = VERSION_SPLIT.split(MINIMUM_VERSION);
        int length = Math.max(requiredParts.length, detectedParts.length);
        for (int index = 0; index < length; index++) {
            int required = index < requiredParts.length ? parsePart(requiredParts[index]) : 0;
            int available = index < detectedParts.length ? parsePart(detectedParts[index]) : 0;
            if (available > required) {
                return true;
            }
            if (available < required) {
                return false;
            }
        }
        return true;
    }

    private static int parsePart(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static final class CompatState {
        private static final CompatState UNINITIALISED = new CompatState(false, false, null, false, false, null, null, null, null, null, null);

        final boolean initialised;
        final boolean modPresent;
        final String apiVersion;
        final boolean versionCompatible;
        final boolean supported;
        final TextRenderService textRenderer;
        final TokenHighlightService tokenHighlighter;
        final TextFormatter textFormatter;
        final RenderingEnvironmentService environmentService;
        final DynamicEffectService dynamicEffects;
        final ColorService colorService;

        private CompatState(
            boolean initialised,
            boolean modPresent,
            String apiVersion,
            boolean versionCompatible,
            boolean supported,
            TextRenderService textRenderer,
            TokenHighlightService tokenHighlighter,
            TextFormatter textFormatter,
            RenderingEnvironmentService environmentService,
            DynamicEffectService dynamicEffects,
            ColorService colorService
        ) {
            this.initialised = initialised;
            this.modPresent = modPresent;
            this.apiVersion = apiVersion;
            this.versionCompatible = versionCompatible;
            this.supported = supported;
            this.textRenderer = textRenderer;
            this.tokenHighlighter = tokenHighlighter;
            this.textFormatter = textFormatter;
            this.environmentService = environmentService;
            this.dynamicEffects = dynamicEffects;
            this.colorService = colorService;
        }

        static CompatState uninitialised() {
            return UNINITIALISED;
        }

        static CompatState detect() {
            boolean modPresent = Loader.isModLoaded("hextext");
            if (!modPresent) {
                return new CompatState(true, false, null, false, false, null, null, null, null, null, null);
            }

            String version = null;
            boolean versionCompatible = false;
            TextRenderService renderer = null;
            TokenHighlightService highlight = null;
            TextFormatter formatter = null;
            RenderingEnvironmentService environment = null;
            DynamicEffectService effects = null;
            ColorService colors = null;

            try {
                version = HexTextApi.apiVersion();
                versionCompatible = isVersionSupported(version);
                if (versionCompatible) {
                    renderer = HexTextApi.textRenderer();
                    highlight = HexTextApi.tokenHighlighter();
                    formatter = HexTextApi.textFormatter();
                    environment = HexTextApi.renderEnvironment();
                    effects = HexTextApi.dynamicEffects();
                    colors = HexTextApi.colors();
                }
            } catch (Throwable t) {
                ModStatus.LOGGER.warn("Failed to initialize HexText API bridge", t);
            }

            boolean supported = versionCompatible
                && renderer != null
                && highlight != null
                && formatter != null
                && environment != null;

            return new CompatState(
                true,
                true,
                version,
                versionCompatible,
                supported,
                renderer,
                highlight,
                formatter,
                environment,
                effects,
                colors
            );
        }

        void logDiagnostics() {
            if (!modPresent) {
                ModStatus.LOGGER.info("HexText not detected; HexText compatibility disabled");
                return;
            }

            if (apiVersion == null) {
                ModStatus.LOGGER.warn("Failed to determine HexText API version; compatibility disabled");
                return;
            }

            if (!versionCompatible) {
                ModStatus.LOGGER.warn(
                    "Detected HexText API version {} but Angelica requires major version {} and minimum {}",
                    apiVersion,
                    REQUIRED_MAJOR_VERSION,
                    MINIMUM_VERSION
                );
                return;
            }

            if (!supported) {
                ModStatus.LOGGER.warn("HexText API version {} detected but required services are unavailable; compatibility disabled", apiVersion);
                return;
            }

            ModStatus.LOGGER.info("Detected HexText API version {}", apiVersion);
            ModStatus.LOGGER.info("HexText compat - rendering service: {}", textRenderer != null ? "available" : "unavailable");
            boolean highlightingAvailable = tokenHighlighter != null && textFormatter != null;
            ModStatus.LOGGER.info("HexText compat - highlighting service: {}", highlightingAvailable ? "available" : "unavailable");
            ModStatus.LOGGER.info("HexText compat - dynamic effects service: {}", dynamicEffects != null ? "available" : "unavailable");
        }
    }
}
