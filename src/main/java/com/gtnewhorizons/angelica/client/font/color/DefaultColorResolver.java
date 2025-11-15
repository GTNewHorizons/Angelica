package com.gtnewhorizons.angelica.client.font.color;

import static com.gtnewhorizons.angelica.client.font.BatchingFontRenderer.FORMATTING_CHAR;

final class DefaultColorResolver implements AngelicaColorResolver {

    private final int[] vanillaPalette;

    DefaultColorResolver(int[] vanillaPalette) {
        this.vanillaPalette = vanillaPalette;
    }

    @Override
    public ResolvedText resolve(CharSequence text, int start, int end, int baseColor, int baseShadowColor) {
        ResolvedText.Builder builder = ResolvedText.builder(end - start);
        FormattingState state = new FormattingState(baseColor, baseShadowColor);

        for (int index = Math.max(0, start); index < Math.min(text.length(), end); index++) {
            char chr = text.charAt(index);
            if (chr == FORMATTING_CHAR && index + 1 < end) {
                char fmtCode = Character.toLowerCase(text.charAt(index + 1));
                index++;
                applyVanillaFormatting(state, fmtCode);
                continue;
            }

            if (state.random) {
                // keep vanilla behaviour; actual replacement happens during rendering
            }

            builder.append(chr, state.color, state.shadowColor, state.bold, state.italic, state.underline,
                state.strikethrough, state.random);
        }

        return builder.build();
    }

    private void applyVanillaFormatting(FormattingState state, char fmtCode) {
        if (charInRange(fmtCode, '0', '9') || charInRange(fmtCode, 'a', 'f')) {
            state.random = false;
            state.bold = false;
            state.strikethrough = false;
            state.underline = false;
            state.italic = false;
            int colorIdx = charInRange(fmtCode, '0', '9') ? (fmtCode - '0') : (fmtCode - 'a' + 10);
            int rgb = vanillaPalette[colorIdx];
            state.color = (state.color & 0xFF000000) | (rgb & 0x00FFFFFF);
            int shadowRgb = vanillaPalette[colorIdx + 16];
            state.shadowColor = (state.shadowColor & 0xFF000000) | (shadowRgb & 0x00FFFFFF);
        } else if (fmtCode == 'k') {
            state.random = true;
        } else if (fmtCode == 'l') {
            state.bold = true;
        } else if (fmtCode == 'm') {
            state.strikethrough = true;
        } else if (fmtCode == 'n') {
            state.underline = true;
        } else if (fmtCode == 'o') {
            state.italic = true;
        } else if (fmtCode == 'r') {
            state.reset();
        }
    }

    private static boolean charInRange(char what, char fromInclusive, char toInclusive) {
        return what >= fromInclusive && what <= toInclusive;
    }

    private static final class FormattingState {
        final int baseColor;
        final int baseShadowColor;
        int color;
        int shadowColor;
        boolean italic;
        boolean random;
        boolean bold;
        boolean strikethrough;
        boolean underline;

        FormattingState(int baseColor, int baseShadowColor) {
            this.baseColor = baseColor;
            this.baseShadowColor = baseShadowColor;
            this.color = baseColor;
            this.shadowColor = baseShadowColor;
        }

        void reset() {
            color = baseColor;
            shadowColor = baseShadowColor;
            italic = false;
            random = false;
            bold = false;
            strikethrough = false;
            underline = false;
        }
    }
}
