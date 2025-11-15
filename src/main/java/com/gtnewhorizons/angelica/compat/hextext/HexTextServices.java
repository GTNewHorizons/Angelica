package com.gtnewhorizons.angelica.compat.hextext;

import com.gtnewhorizons.angelica.compat.ModStatus;
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
 * Lightweight helpers around the HexText API.
 */
public final class HexTextServices {

    private static final Pattern VERSION_SPLIT = Pattern.compile("\\.");
    private static final int REQUIRED_MAJOR_VERSION = 1;
    private static final int REQUIRED_MINOR_VERSION = 1;

    private HexTextServices() {
    }

    public static boolean isApiCompatible() {
        try {
            String detected = HexTextApi.apiVersion();
            if (hasMatchingMajorMinor(detected)) {
                ModStatus.LOGGER.info("HexText compatibility enabled for API version {}", detected);
                return true;
            }

            ModStatus.LOGGER.warn(
                "HexText API version {} detected but Angelica expects {}.{}. Compatibility disabled.",
                detected,
                REQUIRED_MAJOR_VERSION,
                REQUIRED_MINOR_VERSION
            );
        } catch (Throwable t) {
            ModStatus.LOGGER.warn("Failed to verify HexText API version", t);
        }
        return false;
    }

    private static boolean hasMatchingMajorMinor(String detected) {
        if (detected == null || detected.isEmpty()) {
            return false;
        }
        String[] parts = VERSION_SPLIT.split(detected, 3);
        if (parts.length < 2) {
            return false;
        }
        int major = parsePart(parts[0]);
        int minor = parsePart(parts[1]);
        return major == REQUIRED_MAJOR_VERSION && minor == REQUIRED_MINOR_VERSION;
    }

    private static int parsePart(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    public static boolean isSupported() {
        return ModStatus.isHexTextLoaded;
    }

    public static TextRenderService textRenderer() {
        return isSupported() ? HexTextApi.textRenderer() : null;
    }

    public static RenderPlan prepare(TextRenderService renderer, String text, boolean rawMode) {
        return renderer == null ? null : renderer.prepare(text, rawMode);
    }

    public static TokenHighlightService tokenHighlighter() {
        return isSupported() ? HexTextApi.tokenHighlighter() : null;
    }

    public static TextFormatter textFormatter() {
        return isSupported() ? HexTextApi.textFormatter() : null;
    }

    public static RenderingEnvironmentService renderEnvironment() {
        return isSupported() ? HexTextApi.renderEnvironment() : null;
    }

    public static DynamicEffectService dynamicEffects() {
        return isSupported() ? HexTextApi.dynamicEffects() : null;
    }

    public static ColorService colorService() {
        return isSupported() ? HexTextApi.colors() : null;
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
}
