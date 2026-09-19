package com.gtnewhorizons.angelica.client.font;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.resources.IResourcePack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gtnewhorizons.angelica.hudcaching.HUDCaching;
import com.prupe.mcpatcher.mal.resource.TexturePackAPI;
import com.prupe.mcpatcher.mal.resource.TexturePackChangeHandler;

public class DarkModeUtils {

    private static final Logger LOGGER = LogManager.getLogger("DarkModeUtils");

    private static final String ROOT_KEY = "dark_mode_utils";
    private static final int SUPPORTED_SCHEMA = 1;
    private static final String DEBUG_PULSE = "debug_pulse";
    private static final double DEBUG_PULSE_TIME_SCALE = 5e-9;

    private static final String[] INPUT_STREAM_METHOD_NAMES = { "getInputStreamByName", "func_110591_a" };
    private static final Map<Class<?>, Method> INPUT_STREAM_METHOD_CACHE = new HashMap<>();

    private static FontRecolorRule guiFontRule = null;
    private static ButtonFontRules buttonFontRules = null;

    static {
        TexturePackChangeHandler.register(new TexturePackChangeHandler("Angelica Dark Mode Utils", 1) {

            @Override
            public void beforeChange() {}

            @Override
            public void afterChange() {
                reload();
            }
        });
    }

    private DarkModeUtils() {}

    public static void init() {}

    private static void reload() {
        FontRecolorRule guiFont = null;
        ButtonFontRules buttonFont = null;
        for (IResourcePack pack : TexturePackAPI.getResourcePacks(null)) {
            PackDarkModeRules rules = readPackRules(pack);
            if (rules == null) {
                continue;
            }
            if (rules.guiFont() != null) {
                guiFont = rules.guiFont();
            }
            if (rules.buttonFont() != null) {
                buttonFont = rules.buttonFont();
            }
        }
        guiFontRule = guiFont;
        buttonFontRules = buttonFont;
    }

    // Mechanism similar to GTNHLib's PackMcmetaReader. Will be refactored into one common implementation if a third use case arises.
    private static PackDarkModeRules readPackRules(IResourcePack pack) {
        try (InputStream stream = openPackMcmeta(pack)) {
            if (stream == null) {
                return null;
            }
            JsonElement rootElement = new JsonParser().parse(new InputStreamReader(stream, StandardCharsets.UTF_8));
            if (!rootElement.isJsonObject()) {
                return null;
            }
            JsonObject root = rootElement.getAsJsonObject();
            if (!root.has(ROOT_KEY) || !root.get(ROOT_KEY).isJsonObject()) {
                return null;
            }
            JsonObject darkModeUtils = root.getAsJsonObject(ROOT_KEY);
            int schema = darkModeUtils.has("schema") ? darkModeUtils.get("schema").getAsInt() : -1;
            if (schema != SUPPORTED_SCHEMA) {
                LOGGER.warn("Unsupported dark_mode_utils schema {} in pack {}", schema, pack.getPackName());
                return null;
            }
            JsonObject target = darkModeUtils.has("target") && darkModeUtils.get("target").isJsonObject()
                ? darkModeUtils.getAsJsonObject("target")
                : null;
            if (target == null) {
                return null;
            }

            FontRecolorRule guiFont = target.has("gui_font") && target.get("gui_font").isJsonObject()
                ? parseFontRecolorRule(target.getAsJsonObject("gui_font"), pack.getPackName(), "gui_font")
                : null;

            ButtonFontRules buttonFont = target.has("button_font") && target.get("button_font").isJsonObject()
                ? parseButtonFontRules(target.getAsJsonObject("button_font"), pack.getPackName())
                : null;

            return new PackDarkModeRules(guiFont, buttonFont);
        } catch (IOException e) {
            // pack.mcmeta missing/unreadable
            return null;
        } catch (RuntimeException e) {
            LOGGER.warn("Invalid dark_mode_utils block in pack {}: {}", pack.getPackName(), e.toString());
            return null;
        }
    }
    private static ButtonFontRules parseButtonFontRules(JsonObject buttonFont, String packName) {
        ButtonColorRule enabled = parseButtonState(buttonFont, "enabled", packName);
        ButtonColorRule hovered = parseButtonState(buttonFont, "hovered", packName);
        ButtonColorRule disabled = parseButtonState(buttonFont, "disabled", packName);
        if (enabled == null && hovered == null && disabled == null) {
            return null;
        }
        return new ButtonFontRules(enabled, hovered, disabled);
    }

    private static ButtonColorRule parseButtonState(JsonObject buttonFont, String state, String packName) {
        if (!buttonFont.has(state) || !buttonFont.get(state).isJsonObject()) {
            return null;
        }
        try {
            String outputValue = buttonFont.getAsJsonObject(state).get("output").getAsString().trim();
            boolean debugPulse = DEBUG_PULSE.equalsIgnoreCase(outputValue);
            return new ButtonColorRule(debugPulse ? 0 : parseHexColor(outputValue), debugPulse);
        } catch (RuntimeException e) {
            LOGGER.warn("Invalid dark_mode_utils button_font.{} rule in pack {}: {}", state, packName, e.toString());
            return null;
        }
    }

    private static FontRecolorRule parseFontRecolorRule(JsonObject obj, String packName, String context) {
        try {
            JsonObject input = obj.getAsJsonObject("input");
            int minimum = parseHexColor(input.get("minimum").getAsString());
            int maximum = parseHexColor(input.get("maximum").getAsString());
            String outputValue = obj.get("output").getAsString().trim();
            boolean debugPulse = DEBUG_PULSE.equalsIgnoreCase(outputValue);
            int output = debugPulse ? 0 : parseHexColor(outputValue);
            boolean shadow = obj.has("shadow") && obj.get("shadow").getAsBoolean();
            return new FontRecolorRule(minimum, maximum, output, debugPulse, shadow);
        } catch (RuntimeException e) {
            LOGGER.warn("Invalid dark_mode_utils {} rule in pack {}: {}", context, packName, e.toString());
            return null;
        }
    }

    private static int parseHexColor(String value) {
        return Integer.decode(value.trim()) & 0x00FFFFFF;
    }

    private static InputStream openPackMcmeta(IResourcePack pack) throws IOException {
        Method method = findGetInputStreamByName(pack.getClass());
        if (method == null) {
            return null;
        }
        try {
            return (InputStream) method.invoke(pack, "pack.mcmeta");
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            return null;
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private static Method findGetInputStreamByName(Class<?> type) {
        if (INPUT_STREAM_METHOD_CACHE.containsKey(type)) {
            return INPUT_STREAM_METHOD_CACHE.get(type);
        }
        Method found = null;
        outer: for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (String name : INPUT_STREAM_METHOD_NAMES) {
                try {
                    found = current.getDeclaredMethod(name, String.class);
                    found.setAccessible(true);
                    break outer;
                } catch (NoSuchMethodException ignored) {
                }
            }
        }
        INPUT_STREAM_METHOD_CACHE.put(type, found);
        return found;
    }

    public static GuiFontRecolor computeGuiFontRecolor(int argbColor) {
        FontRecolorRule rule = guiFontRule;
        if (rule == null) {
            return null;
        }
        return rule.tryRecolor(argbColor);
    }

    /**
     * Inside a button section, swaps text drawn in one of the button's own three colors for the matching button_font
     * color. Pure white also counts as the enabled color, since it's the most likely override of it. Any other color
     * is returned untouched.
     */
    public static int recolorButtonText(int argbColor, int enabledColor, int hoveredColor, int disabledColor) {
        final ButtonFontRules rules = buttonFontRules;
        if (rules == null) {
            return argbColor;
        }
        final int rgb = argbColor & 0x00FFFFFF;
        final ButtonColorRule rule;
        if (rgb == enabledColor || rgb == 0xFFFFFF) { rule = rules.enabled(); }
        else if (rgb == hoveredColor) { rule = rules.hovered(); }
        else if (rgb == disabledColor) { rule = rules.disabled(); }
        else { return argbColor; }

        if (rule == null) {
            return argbColor;
        }
        return (argbColor & 0xFF000000) | (rule.debugPulse() ? computeDebugPulseRgb() : rule.output());
    }

    private static int computeDebugPulseRgb() {
        final float time = HUDCaching.renderingCacheOverride ? 0f
            : (float) ((System.nanoTime() & 0xFFFFFFFFFFFFL) * DEBUG_PULSE_TIME_SCALE);
        final int animated = (int) (Math.round(0x00007F80 * (Math.sin(2 * time) + 1)) & 0x0000FFFF);
        return 0x00FF0000 | animated;
    }

    public record GuiFontRecolor(int color, int shadowRgb, boolean shadow) {}

    private record PackDarkModeRules(FontRecolorRule guiFont, ButtonFontRules buttonFont) {}

    private record ButtonFontRules(ButtonColorRule enabled, ButtonColorRule hovered, ButtonColorRule disabled) {}

    private record ButtonColorRule(int output, boolean debugPulse) {}

    private static final class FontRecolorRule {

        final int minR, minG, minB;
        final int maxR, maxG, maxB;
        final int output;
        final boolean debugPulse;
        final boolean shadow;

        FontRecolorRule(int minimum, int maximum, int output, boolean debugPulse, boolean shadow) {
            this.minR = (minimum >> 16) & 0xFF;
            this.minG = (minimum >> 8) & 0xFF;
            this.minB = minimum & 0xFF;
            this.maxR = (maximum >> 16) & 0xFF;
            this.maxG = (maximum >> 8) & 0xFF;
            this.maxB = maximum & 0xFF;
            this.output = output;
            this.debugPulse = debugPulse;
            this.shadow = shadow;
        }

        GuiFontRecolor tryRecolor(int argbColor) {
            final int r = (argbColor >> 16) & 0xFF;
            final int g = (argbColor >> 8) & 0xFF;
            final int b = argbColor & 0xFF;

            final boolean nearBlack = r <= minR && g <= minG && b <= minB;
            final boolean nearWhite = r >= maxR && g >= maxG && b >= maxB;
            if (!nearBlack && !nearWhite) {
                return null;
            }

            final int outputRgb = debugPulse ? computeDebugPulseRgb() : output;
            final int newColor = (argbColor & 0xFF000000) | outputRgb;
            final int shadowRgb = (outputRgb & 0xFCFCFC) >> 2;
            return new GuiFontRecolor(newColor, shadowRgb, shadow);
        }
    }
}
