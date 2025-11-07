package com.gtnewhorizons.angelica.compat.hextext;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import kamkeel.hextext.client.render.FontRenderContext;
import kamkeel.hextext.client.render.TokenHighlightUtils;
import kamkeel.hextext.common.util.ColorCodeUtils;
import net.minecraft.client.gui.FontRenderer;

import java.util.Iterator;
import java.util.List;

/**
 * Direct bridge that mirrors HexText's raw token highlighting logic.
 */
final class DirectHexTextHighlighter implements HexTextHighlighter {

    private final List<PendingHighlight> activeHighlights = new ObjectArrayList<>();
    private final List<Highlight> completedHighlights = new ObjectArrayList<>();

    private ColorCodeUtils.FormattingEnvironment formattingEnvironment;
    private CharSequence text;
    private float baseY;
    private boolean active;
    private int skip;

    @Override
    public boolean begin(FontRenderer renderer, CharSequence text, float posX, float posY) {
        activeHighlights.clear();
        completedHighlights.clear();
        formattingEnvironment = null;
        skip = 0;
        this.text = text;

        if (renderer == null || text == null || text.length() == 0) {
            active = false;
            return false;
        }

        if (!FontRenderContext.isRawTextRendering()) {
            active = false;
            return false;
        }

        formattingEnvironment = ColorCodeUtils.captureFormattingEnvironment(false);
        baseY = posY;
        active = true;
        return true;
    }

    @Override
    public void inspect(CharSequence text, int index, float currentX) {
        if (!active || text == null || index < 0 || index >= text.length()) {
            return;
        }

        if (skip > 0) {
            skip--;
            return;
        }

        int tokenLength = ColorCodeUtils.detectColorCodeLengthIgnoringRaw(text, index, formattingEnvironment);
        if (tokenLength == 0) {
            tokenLength = detectRawAmpersandTokenLength(text, index);
        }
        if (tokenLength <= 0) {
            return;
        }

        char current = text.charAt(index);
        if (current != BatchingConstants.FORMATTING_CHAR) {
            int color = TokenHighlightUtils.getTokenHighlightColor(text, index);
            activeHighlights.add(new PendingHighlight(index + tokenLength, currentX, baseY, color));
        }
        skip = Math.max(tokenLength - 1, 0);
    }

    @Override
    public void advance(int nextIndex, float currentX) {
        if (!active || activeHighlights.isEmpty()) {
            return;
        }

        Iterator<PendingHighlight> iterator = activeHighlights.iterator();
        while (iterator.hasNext()) {
            PendingHighlight highlight = iterator.next();
            if (highlight.endIndex <= nextIndex) {
                float width = currentX - highlight.startX;
                if (width > 0.0f) {
                    completedHighlights.add(new Highlight(highlight.startX, highlight.baseY, width, highlight.color));
                }
                iterator.remove();
            }
        }
    }

    @Override
    public void finish(int textLength, float currentX) {
        advance(textLength, currentX);
        active = false;
        text = null;
    }

    @Override
    public List<Highlight> highlights() {
        return completedHighlights;
    }

    private static int detectRawAmpersandTokenLength(CharSequence text, int index) {
        if (text == null || index < 0 || index >= text.length() - 1) {
            return 0;
        }
        if (text.charAt(index) != '&') {
            return 0;
        }
        if (text.charAt(index + 1) == '#') {
            return index + 8 <= text.length() && ColorCodeUtils.isValidHexString(text, index + 2) ? 8 : 0;
        }
        return ColorCodeUtils.isFormattingCode(text.charAt(index + 1)) ? 2 : 0;
    }

    private static final class PendingHighlight {
        private final int endIndex;
        private final float startX;
        private final float baseY;
        private final int color;

        private PendingHighlight(int endIndex, float startX, float baseY, int color) {
            this.endIndex = Math.max(endIndex, 0);
            this.startX = startX;
            this.baseY = baseY;
            this.color = color;
        }
    }

    private static final class BatchingConstants {
        private static final char FORMATTING_CHAR = 167;

        private BatchingConstants() {
        }
    }
}
