package com.gtnewhorizons.angelica.debug.screen;

import com.gtnewhorizons.angelica.client.font.BatchingFontRenderer;
import com.gtnewhorizons.angelica.client.font.FormatCodes;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.client.gui.FontRenderer;

public abstract class DebugText {

    static final char FORMAT = '\u00a7';

    static final class Format {

        int colorStart;
        int colorLength;
        int styles;

        void reset() {
            colorStart = 0;
            colorLength = 0;
            styles = 0;
        }

        void copyFrom(Format other) {
            colorStart = other.colorStart;
            colorLength = other.colorLength;
            styles = other.styles;
        }
    }

    private final Format format = new Format();
    private final Format atSpace = new Format();

    static int applyFormat(CharSequence text, int index, int end, Format state) {
        final long fmt = FormatCodes.parse(text, index + 1, end);
        final int length = 2 + FormatCodes.extraChars(fmt);
        switch (FormatCodes.kind(fmt)) {
            case FormatCodes.COLOR, FormatCodes.HEX -> {
                state.colorStart = index;
                state.colorLength = length;
            }
            case FormatCodes.RESET -> state.colorLength = 0;
            case FormatCodes.OBFUSCATED, FormatCodes.BOLD, FormatCodes.STRIKETHROUGH, FormatCodes.UNDERLINE, FormatCodes.ITALIC -> state.styles |= 1 << FormatCodes.kind(fmt);
            default -> {}
        }
        if (FormatCodes.endsBold(fmt)) state.styles = 0;
        return length;
    }

    protected abstract float advance(char c, boolean bold);

    public abstract float measure(CharSequence text, int start, int end);

    public abstract void draw(CharSequence text, int start, int end, float x, float y, int argb);

    public abstract void rect(int x1, int y1, int x2, int y2, int argb);

    static void appendStripped(StringBuilder out, CharSequence text) {
        final int last = text.length() - 1;
        for (int i = 0; i <= last; i++) {
            final char c = text.charAt(i);
            if (c == FORMAT && i < last) {
                i++;
                continue;
            }
            out.append(c);
        }
    }

    public final int width(CharSequence text, int start, int end) {
        return (int) Math.ceil(measure(text, start, end));
    }

    final void wrap(DebugGroupContents.Slot slot, int budget) {
        final CharSequence src = slot.text;
        final StringBuilder out = slot.wrapped;
        final int len = src.length();
        final Format state = format;
        final Format spaceState = atSpace;
        state.reset();
        out.setLength(0);
        int rowStart = 0;
        float pen = 0.0f;
        boolean rowHasContent = false;
        int spaceOut = -1;
        int spaceSrc = -1;
        float penAtSpace = 0.0f;
        int i = 0;
        while (i < len) {
            final char c = src.charAt(i);
            if (c == FORMAT && i + 1 < len) {
                final int n = applyFormat(src, i, len, state);
                out.append(src, i, i + n);
                i += n;
                continue;
            }
            final float adv = advance(c, (state.styles & 1 << FormatCodes.BOLD) != 0);
            if (rowHasContent && pen + adv > budget) {
                if (c == ' ') {
                    slot.addRow(rowStart, out.length(), pen);
                    i++;
                } else if (spaceOut >= 0) {
                    slot.addRow(rowStart, spaceOut, penAtSpace);
                    out.setLength(spaceOut);
                    state.copyFrom(spaceState);
                    i = spaceSrc + 1;
                } else {
                    slot.addRow(rowStart, out.length(), pen);
                }
                rowStart = out.length();
                if (state.colorLength > 0) out.append(src, state.colorStart, state.colorStart + state.colorLength);
                for (int k = FormatCodes.OBFUSCATED; k <= FormatCodes.ITALIC; k++) if ((state.styles & 1 << k) != 0) out.append(FORMAT).append((char) ('k' + k - FormatCodes.OBFUSCATED));
                pen = 0.0f;
                rowHasContent = false;
                spaceOut = -1;
                continue;
            }
            if (c == ' ' && rowHasContent) {
                spaceOut = out.length();
                spaceSrc = i;
                penAtSpace = pen;
                spaceState.copyFrom(state);
            }
            out.append(c);
            pen += adv;
            rowHasContent = true;
            i++;
        }
        if (rowHasContent || slot.rowCount == 0) slot.addRow(rowStart, out.length(), pen);
    }

    public static final class Batched extends DebugText {

        private final FontRenderer font;
        final BatchingFontRenderer batcher;
        private boolean depthWasEnabled;

        public Batched(FontRenderer font, BatchingFontRenderer batcher) {
            this.font = font;
            this.batcher = batcher;
        }

        @Override
        protected float advance(char c, boolean bold) {
            return batcher.advance(c, bold, font.getUnicodeFlag());
        }

        @Override
        public float measure(CharSequence text, int start, int end) {
            return batcher.measure(text, start, end, font.getUnicodeFlag());
        }

        @Override
        public void draw(CharSequence text, int start, int end, float x, float y, int argb) {
            batcher.drawString(x, y, argb, false, font.getUnicodeFlag(), text, start, end - start);
        }

        public void beginRects() {
            depthWasEnabled = GLStateManager.getDepthTest().isEnabled();
            GLStateManager.disableDepthTest();
        }

        @Override
        public void rect(int x1, int y1, int x2, int y2, int argb) {
            batcher.fillRect(x1, y1, x2, y2, argb);
        }

        public void endRects() {
            if (depthWasEnabled) GLStateManager.enableDepthTest();
        }
    }
}
