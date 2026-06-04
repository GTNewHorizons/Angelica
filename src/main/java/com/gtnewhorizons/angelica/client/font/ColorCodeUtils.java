package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizons.angelica.config.AngelicaConfig;

/**
 * Utility for converting {@code &}-based shorthand into {@code §}-based
 * format codes understood by the batched font renderer.
 * <p>
 * {@code &#RRGGBB} -> {@code §x§R§R§G§G§B§B} (RGB color)<br>
 * {@code &g&#RRGGBB&#RRGGBB} -> {@code §g§x§R§R§G§G§B§B§x§R§R§G§G§B§B} (gradient)<br>
 * {@code &c}, {@code &l}, {@code &r}, {@code &q}, etc. -> {@code §c}, {@code §l}, {@code §r}, {@code §q}
 * <p>
 * Effect codes use low-collision letters to avoid false positives on natural text:
 * rainbow = {@code &q}, wave = {@code &z}, dinnerbone = {@code &v}, gradient = {@code &g} (only with &#-prefixed colors).
 */
public final class ColorCodeUtils {

    private ColorCodeUtils() {}

    public static final char FORMATTING_CHAR = '§';
    public static final char ESCAPED_AMPERSAND = '';

    public static final int SECTION_X_PAYLOAD = 12;
    public static final int SECTION_X_LENGTH = 14;
    public static final int GRADIENT_PAYLOAD = 28;
    public static final int GRADIENT_LENGTH = 30;

    /** Valid single {@code &} codes. {@code g} is excluded; {@code &g} only converts as part of a gradient. */
    public static final String VALID_SINGLE_CODES = "0123456789abcdefklmnorqzvuy";

    private static final String[] SECTION_PREFIX = new String[128];
    static {
        for (int i = 0; i < VALID_SINGLE_CODES.length(); i++) {
            char c = VALID_SINGLE_CODES.charAt(i);
            SECTION_PREFIX[c] = String.valueOf(FORMATTING_CHAR) + c;
        }
        SECTION_PREFIX['x'] = String.valueOf(FORMATTING_CHAR) + 'x';
        SECTION_PREFIX['g'] = String.valueOf(FORMATTING_CHAR) + 'g';
    }

    public static String sectionPrefix(char code) {
        char c = (code < 128) ? Character.toLowerCase(code) : code;
        return (c < 128) ? SECTION_PREFIX[c] : null;
    }

    public static int parseHexPairs(CharSequence str, int start, int count) {
        int result = 0;
        for (int i = 0; i < count; i++) {
            int p = start + i * 2;
            if (str.charAt(p) != FORMATTING_CHAR) return -1;
            int digit = Character.digit(str.charAt(p + 1), 16);
            if (digit == -1) return -1;
            result = (result << 4) | digit;
        }
        return result;
    }

    public static int parseSectionXAt(CharSequence str, int start) {
        if (start + SECTION_X_LENGTH > str.length()) return -1;
        if (str.charAt(start) != FORMATTING_CHAR) return -1;
        if (Character.toLowerCase(str.charAt(start + 1)) != 'x') return -1;
        return parseHexPairs(str, start + 2, 6);
    }

    public static boolean isValidSectionX(CharSequence str, int start) {
        return parseSectionXAt(str, start) != -1;
    }

    private static String lastConversionInput;
    private static String lastConversionOutput;

    public static String convertAmpersandToSectionX(String text) {
        if (text == null || !AngelicaConfig.enableAmpersandConversion) return text;

        if (text.equals(lastConversionInput)) return lastConversionOutput;

        final String out = convertImpl(text);
        lastConversionInput = text;
        lastConversionOutput = out;
        return out;
    }

    private static String convertImpl(String text) {
        int idx = text.indexOf('&');
        if (idx == -1) return text;

        final int len = text.length();
        StringBuilder sb = null;
        int last = 0;
        while (idx != -1 && idx + 1 < len) {
            if (idx > 0 && text.charAt(idx - 1) == '\\') {
                if (sb == null) sb = new StringBuilder(len + 16);
                sb.append(text, last, idx - 1);
                sb.append(ESCAPED_AMPERSAND);
                last = idx + 1;
                idx = text.indexOf('&', last);
                continue;
            }
            if (text.charAt(idx + 1) == '#' && idx + 7 < len) {
                boolean validHex = true;
                for (int i = 2; i <= 7; i++) {
                    if (Character.digit(text.charAt(idx + i), 16) == -1) {
                        validHex = false;
                        break;
                    }
                }
                if (validHex) {
                    if (sb == null) sb = new StringBuilder(len + 16);
                    sb.append(text, last, idx);
                    sb.append(FORMATTING_CHAR).append('x');
                    for (int i = 2; i <= 7; i++) {
                        sb.append(FORMATTING_CHAR).append(text.charAt(idx + i));
                    }
                    last = idx + 8;
                    idx = text.indexOf('&', last);
                    continue;
                }
            }
            if (Character.toLowerCase(text.charAt(idx + 1)) == 'g' && idx + 17 < len
                && text.charAt(idx + 2) == '&' && text.charAt(idx + 3) == '#'
                && text.charAt(idx + 10) == '&' && text.charAt(idx + 11) == '#') {
                boolean valid1 = true, valid2 = true;
                for (int i = 4; i <= 9; i++) {
                    if (Character.digit(text.charAt(idx + i), 16) == -1) { valid1 = false; break; }
                }
                if (valid1) {
                    for (int i = 12; i <= 17; i++) {
                        if (Character.digit(text.charAt(idx + i), 16) == -1) { valid2 = false; break; }
                    }
                }
                if (valid1 && valid2) {
                    if (sb == null) sb = new StringBuilder(len + 16);
                    sb.append(text, last, idx);
                    sb.append(FORMATTING_CHAR).append('g');
                    last = idx + 2;
                    idx = text.indexOf('&', last);
                    continue;
                }
            }
            char code = Character.toLowerCase(text.charAt(idx + 1));
            if (VALID_SINGLE_CODES.indexOf(code) != -1) {
                if (sb == null) sb = new StringBuilder(len + 16);
                sb.append(text, last, idx);
                sb.append(FORMATTING_CHAR).append(text.charAt(idx + 1));
                last = idx + 2;
            }
            idx = text.indexOf('&', idx + 1);
        }
        if (sb == null) return text;
        sb.append(text, last, len);
        return sb.toString();
    }
}
