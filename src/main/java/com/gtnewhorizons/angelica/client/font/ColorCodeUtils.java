package com.gtnewhorizons.angelica.client.font;

/**
 * Utility class for parsing RGB color codes in text.
 * Supports multiple formats:
 * - Traditional: §0-9a-f (handled elsewhere)
 * - Ampersand: &RRGGBB (6 hex digits)
 * - Tag style: <RRGGBB>text</RRGGBB>
 */
public class ColorCodeUtils {

    /**
     * Check if a character is a valid hexadecimal digit (0-9, a-f, A-F)
     */
    public static boolean isValidHexChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    /**
     * Check if a string contains exactly 6 valid hexadecimal characters
     */
    public static boolean isValidHexString(String hex) {
        if (hex == null || hex.length() != 6) {
            return false;
        }
        for (int i = 0; i < 6; i++) {
            if (!isValidHexChar(hex.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if a character represents a traditional Minecraft formatting code (0-9, a-f, k-o, r)
     */
    public static boolean isFormattingCode(char c) {
        char lower = Character.toLowerCase(c);
        return (lower >= '0' && lower <= '9')
            || (lower >= 'a' && lower <= 'f')
            || (lower >= 'k' && lower <= 'o')
            || lower == 'r';
    }

    /**
     * Check if 6 characters starting at position are valid hex
     */
    public static boolean isValidHexString(CharSequence str, int start) {
        if (str == null || start < 0 || start + 6 > str.length()) {
            return false;
        }
        for (int i = 0; i < 6; i++) {
            if (!isValidHexChar(str.charAt(start + i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Parse a 6-digit hexadecimal string to an RGB integer (0xRRGGBB)
     * @param hex String containing exactly 6 hex digits
     * @return RGB value as integer, or -1 if invalid
     */
    public static int parseHexColor(String hex) {
        if (!isValidHexString(hex)) {
            return -1;
        }
        try {
            return Integer.parseInt(hex, 16) & 0x00FFFFFF;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Parse 6 hex characters from a CharSequence starting at position
     * @param str The string to parse
     * @param start Starting position
     * @return RGB value as integer, or -1 if invalid
     */
    public static int parseHexColor(CharSequence str, int start) {
        if (!isValidHexString(str, start)) {
            return -1;
        }
        try {
            String hex = str.subSequence(start, start + 6).toString();
            return Integer.parseInt(hex, 16) & 0x00FFFFFF;
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            return -1;
        }
    }

    /**
     * Detect the length of a color code starting at position, or 0 if none.
     *
     * @param str The string to check
     * @param pos Position to check
     * @return Length of color code:
     *         - 7 for &RRGGBB format (& + 6 hex)
     *         - 9 for <RRGGBB> format (< + 6 hex + >)
     *         - 10 for </RRGGBB> format (</ + 6 hex + >)
     *         - 2 for §X format (handled elsewhere, but counted here)
     *         - 0 for no color code
     */
    public static int detectColorCodeLength(CharSequence str, int pos) {
        return detectColorCodeLengthInternal(str, pos, AngelicaFontRenderContext.isRawTextRendering());
    }

    public static int detectColorCodeLengthIgnoringRaw(CharSequence str, int pos) {
        return detectColorCodeLengthInternal(str, pos, false);
    }

    private static int detectColorCodeLengthInternal(CharSequence str, int pos, boolean skipDueToRaw) {
        if (str == null || pos < 0 || pos >= str.length()) {
            return 0;
        }

        if (skipDueToRaw) {
            return 0;
        }

        char c = str.charAt(pos);

        // Check for §X format (traditional Minecraft)
        if (c == 167 && pos + 1 < str.length()) { // 167 is §
            return 2;
        }

        // Check for &RRGGBB format
        if (c == '&' && pos + 7 <= str.length()) {
            if (isValidHexString(str, pos + 1)) {
                return 7;
            }
        }

        // Check for &X format (traditional formatting alias)
        if (c == '&' && pos + 1 < str.length() && isFormattingCode(str.charAt(pos + 1))) {
            return 2;
        }

        // Check for </RRGGBB> format (closing tag)
        if (c == '<' && pos + 9 <= str.length() && str.charAt(pos + 1) == '/' && str.charAt(pos + 8) == '>') {
            if (isValidHexString(str, pos + 2)) {
                return 10;
            }
        }

        // Check for <RRGGBB> format (opening tag)
        if (c == '<' && pos + 8 <= str.length() && str.charAt(pos + 7) == '>') {
            if (isValidHexString(str, pos + 1)) {
                return 9;
            }
        }

        return 0;
    }

    /**
     * Calculate the shadow color for a given RGB color.
     * Shadow is typically darker (divided by 4 per component).
     *
     * @param rgb The base RGB color (0xRRGGBB)
     * @return Shadow RGB color (0xRRGGBB)
     */
    public static int calculateShadowColor(int rgb) {
        return (rgb & 0xFCFCFC) >> 2;
    }
}
