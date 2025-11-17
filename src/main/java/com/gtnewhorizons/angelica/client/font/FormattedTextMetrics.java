package com.gtnewhorizons.angelica.client.font;

/**
 * Utility methods for measuring formatted Minecraft text.
 * <p>
 * These helpers understand Angelica's extended colour codes and new-line handling, allowing
 * shared logic between rendering code and mixins that replicate vanilla string layout.
 */
public final class FormattedTextMetrics {

    private FormattedTextMetrics() {}

    @FunctionalInterface
    public interface CharWidthFunction {
        float getWidth(char character);
    }

    /**
     * Calculate the maximum line width for a formatted string, respecting Angelica colour codes and explicit newlines.
     *
     * @param text            The formatted text to measure.
     * @param rawMode         Whether formatting codes should be ignored (raw text rendering).
     * @param charWidthFunc   Provider that returns the width of a character in pixels.
     * @param glyphSpacing    Additional spacing applied after every visible glyph.
     * @param boldExtra       Extra width applied when bold formatting is active.
     * @return The width of the widest line in the input text.
     */
    public static float calculateMaxLineWidth(CharSequence text, boolean rawMode,
            CharWidthFunction charWidthFunc, float glyphSpacing, float boldExtra) {
        if (text == null || text.length() == 0) {
            return 0.0f;
        }

        float maxWidth = 0.0f;
        float currentLineWidth = 0.0f;
        boolean isBold = false;
        final int length = text.length();

        for (int index = 0; index < length; ) {
            if (!rawMode) {
                int codeLen = ColorCodeUtils.detectColorCodeLength(text, index);
                if (codeLen > 0) {
                    if (codeLen == 2 && index + 1 < length) {
                        char fmt = Character.toLowerCase(text.charAt(index + 1));
                        if (fmt == 'l') {
                            isBold = true;
                        } else if (fmt == 'r') {
                            isBold = false;
                        } else if ((fmt >= '0' && fmt <= '9') || (fmt >= 'a' && fmt <= 'f')) {
                            isBold = false;
                        }
                    }
                    index += codeLen;
                    continue;
                }
            }

            char character = text.charAt(index);
            if (character == '\n') {
                maxWidth = Math.max(maxWidth, currentLineWidth);
                currentLineWidth = 0.0f;
                index++;
                continue;
            }

            float charWidth = charWidthFunc.getWidth(character);
            if (charWidth > 0.0f) {
                currentLineWidth += charWidth;
                if (isBold) {
                    currentLineWidth += boldExtra;
                }
                currentLineWidth += glyphSpacing;
                maxWidth = Math.max(maxWidth, currentLineWidth);
            }
            index++;
        }

        return Math.max(maxWidth, currentLineWidth);
    }

    /**
     * Determine a safe break index for wrapping formatted text to the supplied width.
     *
     * @param text            The formatted text.
     * @param maxWidth        Maximum width allowed for the line in pixels.
     * @param rawMode         Whether formatting codes should be ignored (raw text rendering).
     * @param charWidthFunc   Provider that returns the width of a character in pixels.
     * @param glyphSpacing    Additional spacing applied after every visible glyph.
     * @param boldExtra       Extra width applied when bold formatting is active.
     * @return Index where the line should be split. Returns {@code text.length()} if everything fits.
     */
    public static int computeLineBreakIndex(CharSequence text, int maxWidth, boolean rawMode,
            CharWidthFunction charWidthFunc, float glyphSpacing, float boldExtra) {
        if (text == null || text.length() == 0 || maxWidth <= 0) {
            return 0;
        }

        int lastSafePosition = 0;
        float currentWidth = 0.0f;
        boolean isBold = false;
        final int length = text.length();

        for (int index = 0; index < length; ) {
            if (!rawMode) {
                int codeLen = ColorCodeUtils.detectColorCodeLength(text, index);
                if (codeLen > 0) {
                    if (codeLen == 2 && index + 1 < length) {
                        char fmt = Character.toLowerCase(text.charAt(index + 1));
                        if (fmt == 'l') {
                            isBold = true;
                        } else if (fmt == 'r') {
                            isBold = false;
                        } else if ((fmt >= '0' && fmt <= '9') || (fmt >= 'a' && fmt <= 'f')) {
                            isBold = false;
                        }
                    }
                    index += codeLen;
                    lastSafePosition = index;
                    continue;
                }
            }

            char character = text.charAt(index);
            if (character == '\n') {
                return index;
            }

            float charWidth = charWidthFunc.getWidth(character);
            if (charWidth < 0.0f) {
                charWidth = 0.0f;
            }

            float nextWidth = currentWidth;
            if (charWidth > 0.0f) {
                nextWidth += charWidth;
                if (isBold) {
                    nextWidth += boldExtra;
                }
                nextWidth += glyphSpacing;
            }

            if (nextWidth > maxWidth) {
                return Math.min(lastSafePosition, length);
            }

            currentWidth = nextWidth;
            index++;
            lastSafePosition = index;
        }

        return length;
    }
}
