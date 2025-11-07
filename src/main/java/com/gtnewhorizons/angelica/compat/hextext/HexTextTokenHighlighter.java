package com.gtnewhorizons.angelica.compat.hextext;

import com.gtnewhorizons.angelica.compat.hextext.HexTextCompat.Highlighter;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Iterator;
import java.util.List;
import kamkeel.hextext.api.HexTextApi;
import kamkeel.hextext.api.text.TextFormattingApi;
import kamkeel.hextext.api.text.TextFormattingApi.FormattingEnvironment;
import kamkeel.hextext.client.render.TokenHighlight;
import kamkeel.hextext.client.render.TokenHighlightUtils;
import net.minecraft.client.gui.FontRenderer;

/**
 * HexText-backed implementation that mirrors the behaviour of the HexText font renderer token highlighter.
 */
final class HexTextTokenHighlighter implements Highlighter {

    private static final char VANILLA_FORMATTING_CHAR = 167;
    private static final char RAW_FORMATTING_CHAR = '&';

    private final List<PendingHighlight> activeHighlights = new ObjectArrayList<>();
    private final List<TokenHighlight> completedHighlights = new ObjectArrayList<>();
    private final TextFormattingApi formatting = HexTextApi.textFormatting();

    private FormattingEnvironment formattingEnvironment;
    private float baseY;
    private boolean active;
    private int skip;

    @Override
    public boolean begin(FontRenderer renderer, CharSequence text, float posX, float posY) {
        activeHighlights.clear();
        completedHighlights.clear();
        formattingEnvironment = null;
        skip = 0;

        if (renderer == null || text == null || text.length() == 0) {
            active = false;
            return false;
        }

        formattingEnvironment = formatting.captureEnvironment(false);
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

        int tokenLength = formatting.detectColorCodeLength(text, index, false, formattingEnvironment);
        if (tokenLength == 0) {
            tokenLength = detectRawAmpersandTokenLength(text, index);
        }
        if (tokenLength <= 0) {
            return;
        }

        char current = text.charAt(index);
        if (current != VANILLA_FORMATTING_CHAR) {
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
                    completedHighlights.add(new TokenHighlight(highlight.startX, highlight.baseY, width, highlight.color));
                }
                iterator.remove();
            }
        }
    }

    @Override
    public void finish(int textLength, float currentX) {
        advance(textLength, currentX);
        active = false;
    }

    @Override
    public List<TokenHighlight> highlights() {
        return completedHighlights;
    }

    private int detectRawAmpersandTokenLength(CharSequence text, int index) {
        if (text == null || index < 0 || index >= text.length()) {
            return 0;
        }
        if (text.charAt(index) != RAW_FORMATTING_CHAR) {
            return 0;
        }
        return formatting.detectColorCodeLength(text, index, true, formattingEnvironment);
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
}
