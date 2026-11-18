package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizon.gtnhlib.util.font.FontRendering;
import com.gtnewhorizons.angelica.config.AngelicaConfig;

import static com.gtnewhorizons.angelica.client.font.ColorCodeUtils.GRADIENT_PAYLOAD;
import static com.gtnewhorizons.angelica.client.font.ColorCodeUtils.SECTION_X_LENGTH;
import static com.gtnewhorizons.angelica.client.font.ColorCodeUtils.SECTION_X_PAYLOAD;

public final class FormatCodes {

    public static final int NONE = 0;
    public static final int HEX = 1;
    public static final int COLOR = 2;
    public static final int OBFUSCATED = 3;
    public static final int BOLD = 4;
    public static final int STRIKETHROUGH = 5;
    public static final int UNDERLINE = 6;
    public static final int ITALIC = 7;
    public static final int RAINBOW = 8;
    public static final int WAVE = 9;
    public static final int DINNERBONE = 10;
    public static final int SHADOW_COLOR = 11;
    public static final int SHADOW_TOGGLE = 12;
    public static final int GRADIENT = 13;
    public static final int CUSTOM = 14;
    public static final int RESET = 15;

    private static final int EXTRA_SHIFT = 8;
    private static final int PAYLOAD_SHIFT = 16;
    private static final long RGB_MASK = 0xFFFFFFL;

    private FormatCodes() {}

    public static long parse(CharSequence s, int codeIndex, int end) {
        final char code = Character.toLowerCase(s.charAt(codeIndex));
        return switch (code) {
            case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> pack(COLOR, 0, code - '0');
            case 'a', 'b', 'c', 'd', 'e', 'f' -> pack(COLOR, 0, code - 'a' + 10);
            case 'k' -> OBFUSCATED;
            case 'l' -> BOLD;
            case 'm' -> STRIKETHROUGH;
            case 'n' -> UNDERLINE;
            case 'o' -> ITALIC;
            case 'r' -> RESET;
            case 'q' -> AngelicaConfig.enableRainbow ? RAINBOW : NONE;
            case 'z' -> AngelicaConfig.enableWaveText ? WAVE : NONE;
            case 'v' -> AngelicaConfig.enableDinnerboneText ? DINNERBONE : NONE;
            case 'x' -> {
                if (!AngelicaConfig.enableRGBColors || codeIndex + SECTION_X_PAYLOAD >= end) yield NONE;
                final int rgb = ColorCodeUtils.parseHexPairs(s, codeIndex + 1, 6);
                yield rgb == -1 ? NONE : pack(HEX, SECTION_X_PAYLOAD, rgb);
            }
            case 'u' -> {
                if (!AngelicaConfig.enableDropShadow) yield NONE;
                final int rgb = codeIndex + SECTION_X_LENGTH < end ? ColorCodeUtils.parseSectionXAt(s, codeIndex + 1) : -1;
                yield rgb == -1 ? SHADOW_TOGGLE : pack(SHADOW_COLOR, SECTION_X_LENGTH, rgb);
            }
            case 'g' -> {
                if (!AngelicaConfig.enableGradients || codeIndex + GRADIENT_PAYLOAD >= end) yield NONE;
                final int from = ColorCodeUtils.parseSectionXAt(s, codeIndex + 1);
                final int to = ColorCodeUtils.parseSectionXAt(s, codeIndex + 1 + SECTION_X_LENGTH);
                yield from == -1 || to == -1 ? NONE : pack(GRADIENT, GRADIENT_PAYLOAD, ((long) from << 24) | to);
            }
            default -> FontEffectRegistry.isRegistered(code) ? pack(CUSTOM, 0, code - 'a') : NONE;
        };
    }

    private static long pack(int kind, int extraChars, long payload) {
        return kind | ((long) extraChars << EXTRA_SHIFT) | (payload << PAYLOAD_SHIFT);
    }

    public static int kind(long code) {
        return (int) (code & 0xFF);
    }

    public static int extraChars(long code) {
        return (int) ((code >>> EXTRA_SHIFT) & 0xFF);
    }

    public static int rgb(long code) {
        return (int) ((code >>> PAYLOAD_SHIFT) & RGB_MASK);
    }

    public static int colorIndex(long code) {
        return (int) ((code >>> PAYLOAD_SHIFT) & 0xF);
    }

    public static int gradientFrom(long code) {
        return (int) ((code >>> (PAYLOAD_SHIFT + 24)) & RGB_MASK);
    }

    public static int gradientTo(long code) {
        return rgb(code);
    }

    public static long customBit(long code) {
        return 1L << ((code >>> PAYLOAD_SHIFT) & 0x1F);
    }

    public static boolean endsBold(long code) {
        return switch (kind(code)) {
            case COLOR, RESET -> true;
            case HEX -> FontRendering.hexColorResetsStyles();
            default -> false;
        };
    }
}
