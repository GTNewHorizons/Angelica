package com.gtnewhorizons.angelica.client.font;

import java.util.ArrayDeque;

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
     * Also includes custom codes: g (rainbow), h (dinnerbone)
     */
    public static boolean isFormattingCode(char c) {
        char lower = Character.toLowerCase(c);
        return (lower >= '0' && lower <= '9')
            || (lower >= 'a' && lower <= 'f')
            || lower == 'g' // rainbow
            || lower == 'h' // dinnerbone
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
     *         - 8 for <RRGGBB> format (< + 6 hex + >)
     *         - 9 for </RRGGBB> format (</ + 6 hex + >)
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
                return 9;
            }
        }

        // Check for <RRGGBB> format (opening tag)
        if (c == '<' && pos + 8 <= str.length() && str.charAt(pos + 7) == '>') {
            if (isValidHexString(str, pos + 1)) {
                return 8;
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

    /**
     * Convert HSV (Hue, Saturation, Value) color to RGB.
     *
     * @param hue Hue in degrees (0-360)
     * @param saturation Saturation (0.0-1.0)
     * @param value Value/Brightness (0.0-1.0)
     * @return RGB color as integer (0xRRGGBB)
     */
    public static int hsvToRgb(float hue, float saturation, float value) {
        // Normalize hue to 0-360 range
        hue = hue % 360.0f;
        if (hue < 0) hue += 360.0f;

        // If saturation is 0, it's grayscale
        if (saturation == 0) {
            int gray = (int) (value * 255);
            return (gray << 16) | (gray << 8) | gray;
        }

        // Calculate which sector (0-5) of the color wheel we're in
        float h = hue / 60.0f;
        int sector = (int) Math.floor(h);
        float fractionalSector = h - sector;

        float p = value * (1.0f - saturation);
        float q = value * (1.0f - saturation * fractionalSector);
        float t = value * (1.0f - saturation * (1.0f - fractionalSector));

        float r, g, b;
        switch (sector) {
            case 0:  r = value; g = t;     b = p;     break;
            case 1:  r = q;     g = value; b = p;     break;
            case 2:  r = p;     g = value; b = t;     break;
            case 3:  r = p;     g = q;     b = value; break;
            case 4:  r = t;     g = p;     b = value; break;
            default: r = value; g = p;     b = q;     break; // sector 5
        }

        int red = (int) (r * 255);
        int green = (int) (g * 255);
        int blue = (int) (b * 255);

        return (red << 16) | (green << 8) | blue;
    }

    /**
     * Extract the currently active formatting codes from {@code str}.
     *
     * @param str The formatted string.
     * @return A string containing the colour code (if any) followed by active style codes.
     */
    public static String extractFormatFromString(String str) {
        if (str == null || str.isEmpty()) {
            return "";
        }

        String currentColorCode = null;
        StringBuilder styleCodes = new StringBuilder();
        ArrayDeque<String> colorStack = new ArrayDeque<>();

        for (int i = 0; i < str.length(); ) {
            int codeLen = detectColorCodeLengthIgnoringRaw(str, i);

            if (codeLen > 0) {
                char firstChar = str.charAt(i);
                String code = str.substring(i, i + codeLen);

                if (codeLen == 7 && firstChar == '&') {
                    currentColorCode = code;
                    colorStack.clear();
                    styleCodes.setLength(0);
                } else if (codeLen == 8 && firstChar == '<') {
                    colorStack.push(currentColorCode);
                    currentColorCode = code;
                    styleCodes.setLength(0);
                } else if (codeLen == 9 && firstChar == '<') {
                    currentColorCode = colorStack.isEmpty() ? null : colorStack.pop();
                    styleCodes.setLength(0);
                } else if (codeLen == 2) {
                    char fmt = Character.toLowerCase(str.charAt(i + 1));

                    if ((fmt >= '0' && fmt <= '9') || (fmt >= 'a' && fmt <= 'f')) {
                        currentColorCode = code;
                        colorStack.clear();
                        styleCodes.setLength(0);
                    } else if (fmt == 'r') {
                        currentColorCode = null;
                        colorStack.clear();
                        styleCodes.setLength(0);
                    } else if (fmt == 'l' || fmt == 'o' || fmt == 'n' || fmt == 'm' || fmt == 'k') {
                        styleCodes.append(code);
                    }
                }

                i += codeLen;
                continue;
            }

            i++;
        }

        StringBuilder result = new StringBuilder();
        if (currentColorCode != null) {
            result.append(currentColorCode);
        }
        if (styleCodes.length() > 0) {
            result.append(styleCodes);
        }

        return result.toString();
    }

    /**
     * Remove all recognised colour/formatting codes from {@code input}.
     *
     * @param input Text that may contain formatting codes.
     * @return The input with all colour codes removed, or {@code null} if the input was {@code null}.
     */
    public static String stripColorCodes(CharSequence input) {
        if (input == null) {
            return null;
        }

        StringBuilder builder = new StringBuilder(input.length());
        for (int index = 0; index < input.length(); ) {
            int codeLen = detectColorCodeLengthIgnoringRaw(input, index);
            if (codeLen > 0) {
                index += codeLen;
                continue;
            }

            builder.append(input.charAt(index));
            index++;
        }

        return builder.toString();
    }
}
