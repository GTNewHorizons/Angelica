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
 * Centralised access to HexText's public API surface. All lookups are
 * performed lazily and cached once the API reports a supported version.
 */
public final class HexTextServices {

    private static final Pattern VERSION_SPLIT = Pattern.compile("\\.");
    private static final String MINIMUM_VERSION = "1.1.0";

    private static volatile Bridge cachedBridge;

    private HexTextServices() {
    }

    public static boolean isSupported() {
        return bridge().isSupported();
    }

    public static String apiVersion() {
        return bridge().apiVersion();
    }

    public static TextRenderService textRenderer() {
        Bridge bridge = bridge();
        return bridge.isSupported() ? bridge.textRenderer : null;
    }

    public static RenderPlan prepare(TextRenderService renderer, String text, boolean rawMode) {
        return renderer == null ? null : renderer.prepare(text, rawMode);
    }

    public static TokenHighlightService tokenHighlighter() {
        Bridge bridge = bridge();
        return bridge.isSupported() ? bridge.tokenHighlighter : null;
    }

    public static TextFormatter textFormatter() {
        Bridge bridge = bridge();
        return bridge.isSupported() ? bridge.textFormatter : null;
    }

    public static RenderingEnvironmentService renderEnvironment() {
        Bridge bridge = bridge();
        return bridge.isSupported() ? bridge.environmentService : null;
    }

    public static DynamicEffectService dynamicEffects() {
        Bridge bridge = bridge();
        return bridge.isSupported() ? bridge.dynamicEffects : null;
    }

    public static ColorService colorService() {
        Bridge bridge = bridge();
        return bridge.isSupported() ? bridge.colorService : null;
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
        Bridge bridge = bridge();
        if (!bridge.modPresent) {
            ModStatus.LOGGER.info("HexText not detected; HexText compatibility disabled");
            return;
        }

        String version = bridge.apiVersion();
        if (version == null) {
            ModStatus.LOGGER.warn("Failed to determine HexText API version; compatibility disabled");
            return;
        }

        if (!bridge.isSupported()) {
            ModStatus.LOGGER.warn(
                "Detected HexText API version {} but Angelica requires {} or newer; compatibility disabled",
                version,
                MINIMUM_VERSION);
            return;
        }

        ModStatus.LOGGER.info("Detected HexText API version {}", version);
        ModStatus.LOGGER.info("HexText compat - rendering service: {}", bridge.textRenderer != null ? "available" : "unavailable");
        ModStatus.LOGGER.info("HexText compat - highlighting service: {}", bridge.highlightingAvailable() ? "available" : "unavailable");
        ModStatus.LOGGER.info("HexText compat - dynamic effects service: {}", bridge.dynamicEffects != null ? "available" : "unavailable");
    }

    private static Bridge bridge() {
        Bridge current = cachedBridge;
        boolean modPresent = Loader.isModLoaded("hextext");
        if (current != null && current.modPresent == modPresent && !current.requiresRefresh()) {
            return current;
        }
        synchronized (HexTextServices.class) {
            current = cachedBridge;
            if (current != null && current.modPresent == modPresent && !current.requiresRefresh()) {
                return current;
            }
            current = createBridge(modPresent);
            cachedBridge = current;
            return current;
        }
    }

    private static Bridge createBridge(boolean modPresent) {
        if (!modPresent) {
            return Bridge.unavailable();
        }
        try {
            return new Bridge(modPresent);
        } catch (Throwable t) {
            ModStatus.LOGGER.warn("Failed to initialize HexText API bridge", t);
            return Bridge.unavailable();
        }
    }

    private static final class Bridge {
        final boolean modPresent;
        final String apiVersion;
        final boolean supported;
        final boolean retryableFailure;

        final TextRenderService textRenderer;
        final TokenHighlightService tokenHighlighter;
        final TextFormatter textFormatter;
        final RenderingEnvironmentService environmentService;
        final DynamicEffectService dynamicEffects;
        final ColorService colorService;

        private Bridge(boolean modPresent) {
            this.modPresent = modPresent;

            String version = null;
            TextRenderService renderer = null;
            TokenHighlightService highlight = null;
            TextFormatter formatter = null;
            RenderingEnvironmentService environment = null;
            DynamicEffectService effects = null;
            ColorService colors = null;
            boolean retry = false;
            boolean enabled = false;

            try {
                version = HexTextApi.apiVersion();
                if (isVersionSupported(version)) {
                    renderer = HexTextApi.textRenderer();
                    highlight = HexTextApi.tokenHighlighter();
                    formatter = HexTextApi.textFormatter();
                    environment = HexTextApi.renderEnvironment();
                    effects = HexTextApi.dynamicEffects();
                    colors = HexTextApi.colors();
                    enabled = renderer != null && highlight != null && formatter != null && environment != null;
                }
            } catch (IllegalStateException e) {
                retry = true;
            }

            this.apiVersion = version;
            this.textRenderer = renderer;
            this.tokenHighlighter = highlight;
            this.textFormatter = formatter;
            this.environmentService = environment;
            this.dynamicEffects = effects;
            this.colorService = colors;
            this.supported = enabled;
            this.retryableFailure = retry;
        }

        private Bridge(
            boolean modPresent,
            String apiVersion,
            boolean supported,
            boolean retryableFailure,
            TextRenderService textRenderer,
            TokenHighlightService tokenHighlighter,
            TextFormatter textFormatter,
            RenderingEnvironmentService environmentService,
            DynamicEffectService dynamicEffects,
            ColorService colorService
        ) {
            this.modPresent = modPresent;
            this.apiVersion = apiVersion;
            this.supported = supported;
            this.retryableFailure = retryableFailure;
            this.textRenderer = textRenderer;
            this.tokenHighlighter = tokenHighlighter;
            this.textFormatter = textFormatter;
            this.environmentService = environmentService;
            this.dynamicEffects = dynamicEffects;
            this.colorService = colorService;
        }

        static Bridge unavailable() {
            return new Bridge(false, null, false, false, null, null, null, null, null, null);
        }

        boolean requiresRefresh() {
            return retryableFailure;
        }

        boolean isSupported() {
            return supported;
        }

        String apiVersion() {
            return apiVersion;
        }

        boolean highlightingAvailable() {
            return tokenHighlighter != null && textFormatter != null;
        }
    }

    private static boolean isVersionSupported(String detected) {
        if (detected == null) {
            return false;
        }
        String[] requiredParts = VERSION_SPLIT.split(MINIMUM_VERSION);
        String[] detectedParts = VERSION_SPLIT.split(detected);
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
}
