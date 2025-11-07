package com.gtnewhorizons.angelica.compat.hextext.highlight;

import com.gtnewhorizons.angelica.compat.hextext.HexTextCompat;
import com.gtnewhorizons.angelica.compat.hextext.HexTextServices;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Collections;
import java.util.List;
import kamkeel.hextext.api.rendering.HighlightSpan;
import kamkeel.hextext.api.rendering.TokenHighlightService;
import kamkeel.hextext.api.rendering.TokenHighlightService.WidthProvider;
import kamkeel.hextext.api.text.TextFormatter;
import kamkeel.hextext.api.text.TextFormatter.FormattingEnvironment;
import kamkeel.hextext.client.render.FontRenderContext;
import net.minecraft.client.gui.FontRenderer;

/**
 * HexText-backed implementation that mirrors the behaviour of the HexText font renderer token highlighter.
 */
public final class HexTextTokenHighlighter implements HexTextCompat.Highlighter {

    private static final char VANILLA_FORMATTING_CHAR = 167;

    private final List<HighlightSpan> highlights = new ObjectArrayList<>();

    private TokenHighlightService highlightService;
    private TextFormatter formatter;
    private FormattingEnvironment environment;
    private WidthProvider widthProvider;
    private boolean rawMode;
    private float baseY;
    private boolean active;
    private int skip;

    @Override
    public boolean begin(FontRenderer renderer, CharSequence text, float posX, float posY) {
        highlights.clear();
        highlightService = HexTextServices.tokenHighlighter();
        formatter = HexTextServices.textFormatter();
        environment = null;
        widthProvider = renderer == null ? null : new RendererWidthProvider(renderer);
        skip = 0;

        if (renderer == null || text == null || text.length() == 0) {
            active = false;
            return false;
        }
        if (highlightService == null || formatter == null) {
            active = false;
            return false;
        }

        rawMode = FontRenderContext.isRawTextRendering();
        environment = formatter.captureEnvironment(rawMode);
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
        int tokenLength = detectTokenLength(text, index);
        if (tokenLength <= 0) {
            return;
        }

        char current = text.charAt(index);
        if (current != VANILLA_FORMATTING_CHAR) {
            int color = highlightService.getTokenHighlightColor(text, index);
            float width = highlightService.measureLiteralWidth(widthProvider, text, index, tokenLength);
            if (width > 0.0f) {
                HighlightSpan span = highlightService.createHighlight(currentX, baseY, width, color);
                if (span != null) {
                    highlights.add(span);
                }
            }
        }
        skip = Math.max(tokenLength - 1, 0);
    }

    @Override
    public void advance(int nextIndex, float currentX) {
        // No-op: highlight spans are emitted eagerly in {@link #inspect}.
    }

    @Override
    public void finish(int textLength, float currentX) {
        active = false;
    }

    @Override
    public List<HighlightSpan> highlights() {
        return active ? Collections.unmodifiableList(highlights) : highlights;
    }

    private int detectTokenLength(CharSequence text, int index) {
        int length = formatter.detectColorCodeLength(text, index, rawMode, environment);
        if (length == 0 && text.charAt(index) == '&') {
            length = formatter.detectColorCodeLength(text, index, true, environment);
        }
        return length;
    }

    private static final class RendererWidthProvider implements WidthProvider {
        private final FontRenderer renderer;

        private RendererWidthProvider(FontRenderer renderer) {
            this.renderer = renderer;
        }

        @Override
        public int getStringWidth(String text) {
            if (renderer == null || text == null) {
                return 0;
            }
            return renderer.getStringWidth(text);
        }
    }
}
