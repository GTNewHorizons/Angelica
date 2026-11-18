package com.gtnewhorizons.angelica.debug.screen;

import com.gtnewhorizons.angelica.client.font.FormatCodes;

final class FixedWidthText extends DebugText {

    private final Format format = new Format();

    @Override
    protected float advance(char c, boolean bold) {
        return bold ? 2.0f : 1.0f;
    }

    @Override
    public float measure(CharSequence text, int start, int end) {
        final Format state = format;
        state.reset();
        float x = 0.0f;
        int i = start;
        while (i < end) {
            if (text.charAt(i) == FORMAT && i + 1 < end) {
                i += applyFormat(text, i, end, state);
                continue;
            }
            x += advance(text.charAt(i), (state.styles & 1 << FormatCodes.BOLD) != 0);
            i++;
        }
        return x;
    }

    @Override
    public void draw(CharSequence text, int start, int end, float x, float y, int argb) {
    }

    @Override
    public void rect(int x1, int y1, int x2, int y2, int argb) {
    }
}
