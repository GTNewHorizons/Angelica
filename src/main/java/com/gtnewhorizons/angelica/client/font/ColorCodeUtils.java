package com.gtnewhorizons.angelica.client.font;

/**
 * Utility for converting {@code &}-based shorthand into {@code §}-based
 * format codes understood by the batched font renderer.
 * <p>
 * {@code &#RRGGBB} → {@code §x§R§R§G§G§B§B} (RGB color)<br>
 * {@code &g&#RRGGBB&#RRGGBB} → {@code §g§x§R§R§G§G§B§B§x§R§R§G§G§B§B} (gradient)<br>
 * {@code &c}, {@code &l}, {@code &r}, {@code &q}, etc. → {@code §c}, {@code §l}, {@code §r}, {@code §q}
 * <p>
 * Effect codes use low-collision letters to avoid false positives on natural text:
 * rainbow = {@code &q}, wave = {@code &z}, dinnerbone = {@code &v}, gradient = {@code &g} (only with &#-prefixed colors).
 */
public final class ColorCodeUtils {

    private ColorCodeUtils() {}

    /** Valid single {@code &} codes. Note: {@code g} is excluded — {@code &g} only converts as part of {@code &g&#RRGGBB&#RRGGBB} gradient syntax. */
    public static final String VALID_SINGLE_CODES = "0123456789abcdefklmnorqzv";

    public static String convertAmpersandToSectionX(String text) {
        if (text == null) return text;
        int idx = text.indexOf('&');
        if (idx == -1) return text;

        int len = text.length();
        StringBuilder sb = new StringBuilder(len + 48);
        int last = 0;
        while (idx != -1 && idx + 1 < len) {
            // Try &#RRGGBB (hash + 6 hex digits) — no ambiguity with legacy &a-f
            if (text.charAt(idx + 1) == '#' && idx + 7 < len) {
                boolean validHex = true;
                for (int i = 2; i <= 7; i++) {
                    if (Character.digit(text.charAt(idx + i), 16) == -1) {
                        validHex = false;
                        break;
                    }
                }
                if (validHex) {
                    sb.append(text, last, idx);
                    sb.append('\u00a7').append('x');
                    for (int i = 2; i <= 7; i++) {
                        sb.append('\u00a7').append(text.charAt(idx + i));
                    }
                    last = idx + 8; // skip &#RRGGBB (8 chars)
                    idx = text.indexOf('&', last);
                    continue;
                }
            }
            // Try &g gradient — only when followed by &#RRGGBB&#RRGGBB
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
                    sb.append(text, last, idx);
                    sb.append('\u00a7').append('g');
                    last = idx + 2; // skip &g, let the two &#RRGGBB be caught on next iterations
                    idx = text.indexOf('&', last);
                    continue;
                }
            }
            // Fall back to single format code
            char code = Character.toLowerCase(text.charAt(idx + 1));
            if (VALID_SINGLE_CODES.indexOf(code) != -1) {
                sb.append(text, last, idx);
                sb.append('\u00a7').append(text.charAt(idx + 1));
                last = idx + 2;
            }
            idx = text.indexOf('&', idx + 1);
        }
        if (last == 0) return text;
        sb.append(text, last, len);
        return sb.toString();
    }
}
