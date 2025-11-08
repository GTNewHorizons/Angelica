package com.gtnewhorizons.angelica.compat.hextext.highlight;

import com.gtnewhorizons.angelica.compat.hextext.HexTextCompat;
import com.gtnewhorizons.angelica.compat.hextext.HexTextServices;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Collections;
import java.util.List;
import kamkeel.hextext.api.rendering.HighlightSpan;
import kamkeel.hextext.api.rendering.RenderingEnvironmentService;
import kamkeel.hextext.api.rendering.TokenHighlightService;
import kamkeel.hextext.api.rendering.TokenHighlightService.WidthProvider;
import kamkeel.hextext.api.text.TextFormatter;
import kamkeel.hextext.api.text.TextFormatter.FormattingEnvironment;
import net.minecraft.client.gui.FontRenderer;

/**
 * HexText-backed implementation that mirrors the behaviour of the HexText font renderer token highlighter.
 */
public final class HexTextTokenHighlighter implements HexTextCompat.Highlighter {

    private static final char VANILLA_FORMATTING_CHAR = 167;

    private final List<HexTextCompat.Highlight> highlights = new ObjectArrayList<>();
    private final RenderingEnvironmentService environmentService;
    private final TokenHighlightService highlightService;
    private final TextFormatter formatter;

    private FormattingEnvironment environment;
    private WidthProvider widthProvider;
    private boolean rawMode;
    private float baseY;
    private boolean active;
    private int skip;

    public HexTextTokenHighlighter(
        RenderingEnvironmentService environmentService,
        TokenHighlightService highlightService,
        TextFormatter formatter
    ) {
        this.environmentService = environmentService;
        this.highlightService = highlightService;
        this.formatter = formatter;
    }

    @Override
    public boolean begin(FontRenderer renderer, CharSequence text, float posX, float posY) {
        highlights.clear();
        environment = null;
        widthProvider = renderer == null ? null : HexTextServices.createWidthProvider(renderer);
        skip = 0;

        if (renderer == null || text == null || text.length() == 0) {
            active = false;
            return false;
        }
        if (highlightService == null || formatter == null || environmentService == null) {
            active = false;
            return false;
        }

        rawMode = environmentService.isRawTextRendering();
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

        if (widthProvider == null) {
            skip = Math.max(tokenLength - 1, 0);
            return;
        }

        char current = text.charAt(index);
        if (current != VANILLA_FORMATTING_CHAR) {
            int color = highlightService.getTokenHighlightColor(text, index);
            float width = highlightService.measureLiteralWidth(widthProvider, text, index, tokenLength);
            if (width > 0.0f) {
                HighlightSpan span = highlightService.createHighlight(currentX, baseY, width, color);
                if (span != null) {
                    highlights.add(new HexTextCompat.Highlight(span.getX(), span.getY(), span.getWidth(), span.getColor()));
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
    public List<HexTextCompat.Highlight> highlights() {
        return active ? Collections.unmodifiableList(highlights) : highlights;
    }

    private int detectTokenLength(CharSequence text, int index) {
        int length = formatter.detectColorCodeLength(text, index, rawMode, environment);
        if (length == 0 && text.charAt(index) == '&') {
            length = formatter.detectColorCodeLength(text, index, true, environment);
        }
        return length;
    }
}
