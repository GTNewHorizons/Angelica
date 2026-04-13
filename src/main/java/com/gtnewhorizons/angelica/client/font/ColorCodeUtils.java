package com.gtnewhorizons.angelica.client.font;

/**
 * Utility for converting {@code &}-based shorthand into {@code §}-based
 * format codes understood by the batched font renderer.
 * <p>
 * {@code &#RRGGBB} → {@code §x§R§R§G§G§B§B} (RGB color)<br>
 * {@code &c}, {@code &l}, {@code &r}, {@code &y}, etc. → {@code §c}, {@code §l}, {@code §r}, {@code §y}
 */
public final class ColorCodeUtils {

    private ColorCodeUtils() {}

    private static final String VALID_SINGLE_CODES = "0123456789abcdefklmnorywjg";

    public static String convertAmpersandToSectionX(String text) {
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
