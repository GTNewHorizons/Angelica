package com.gtnewhorizons.angelica.compat.hextext;

import net.minecraft.client.gui.FontRenderer;

import java.util.Collections;
import java.util.List;

/**
 * Bridges HexText's token highlighting with Angelica's batched font renderer.
 */
public interface HexTextHighlighter {

    /**
     * Starts a new highlighting session.
     *
     * @param renderer the backing font renderer
     * @param text     the sanitized text that will be rendered
     * @param posX     the starting x position of the text
     * @param posY     the starting y position of the text
     * @return {@code true} if highlighting should be performed, {@code false} otherwise
     */
    boolean begin(FontRenderer renderer, CharSequence text, float posX, float posY);

    /**
     * Notifies the highlighter that the renderer is about to process the character at {@code index}.
     *
     * @param text      the sanitized text
     * @param index     the index of the character that will be processed
     * @param currentX  the current x position before the character is emitted
     */
    void inspect(CharSequence text, int index, float currentX);

    /**
     * Notifies the highlighter that the renderer has advanced past {@code index} and that the cursor is
     * now positioned at {@code currentX}.
     *
     * @param nextIndex the index of the next character that will be processed
     * @param currentX  the current x position after the previous character was emitted
     */
    void advance(int nextIndex, float currentX);

    /**
     * Finalises any remaining highlights at the end of rendering.
     *
     * @param textLength the length of the rendered text
     * @param currentX   the final x position of the renderer
     */
    void finish(int textLength, float currentX);

    /**
     * Returns the highlights that should be drawn.
     */
    List<Highlight> highlights();

    /**
     * Represents a token highlight rectangle.
     */
    final class Highlight {
        private final float x;
        private final float y;
        private final float width;
        private final int color;

        public Highlight(float x, float y, float width, int color) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.color = color;
        }

        public float x() {
            return x;
        }

        public float y() {
            return y;
        }

        public float width() {
            return width;
        }

        public int color() {
            return color;
        }
    }

    /**
     * A no-op implementation used when HexText is not available.
     */
    HexTextHighlighter NOOP = new HexTextHighlighter() {
        @Override
        public boolean begin(FontRenderer renderer, CharSequence text, float posX, float posY) {
            return false;
        }

        @Override
        public void inspect(CharSequence text, int index, float currentX) {
        }

        @Override
        public void advance(int nextIndex, float currentX) {
        }

        @Override
        public void finish(int textLength, float currentX) {
        }

        @Override
        public List<Highlight> highlights() {
            return Collections.emptyList();
        }
    };
}
