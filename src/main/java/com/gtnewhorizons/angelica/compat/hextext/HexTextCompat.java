package com.gtnewhorizons.angelica.compat.hextext;

import com.gtnewhorizons.angelica.compat.ModStatus;
import java.util.Collections;
import java.util.List;
import kamkeel.hextext.client.render.FontRenderContext;
import kamkeel.hextext.client.render.RenderTextData;
import kamkeel.hextext.common.util.ColorCodeUtils;
import kamkeel.hextext.client.render.TokenHighlight;
import net.minecraft.client.gui.FontRenderer;

/**
 * Consolidated HexText compatibility helpers for Angelica.
 */
public final class HexTextCompat {

    private HexTextCompat() {
    }

    /**
     * Factory for a HexText-aware token highlighter.
     */
    public static Highlighter createHighlighter() {
        if (!FontRenderContext.isRawTextRendering()) {
            return Highlighter.NOOP;
        }
        try {
            return new HexTextTokenHighlighter();
        } catch (Throwable t) {
            ModStatus.LOGGER.warn("Failed to initialize HexText token highlighting", t);
            return Highlighter.NOOP;
        }
    }

    /**
     * Factory for the HexText render bridge used by the colour resolver.
     */
    public static Bridge tryCreateBridge() {
        try {
            return new HexTextRenderBridge();
        } catch (Throwable t) {
            ModStatus.LOGGER.warn("Failed to initialize HexText compatibility layer", t);
            return null;
        }
    }

    /**
     * Returns a helper for computing HexText dynamic effect values.
     */
    public static EffectsHelper getEffectsHelper() {
        return EffectsHelperHolder.INSTANCE;
    }

    public static int computeShadowColor(int rgb) {
        return ColorCodeUtils.calculateShadowColor(rgb);
    }

    public interface Highlighter {

        /**
         * Starts a new highlighting session.
         */
        boolean begin(FontRenderer renderer, CharSequence text, float posX, float posY);

        /**
         * Inspects the upcoming character before it is emitted.
         */
        void inspect(CharSequence text, int index, float currentX);

        /**
         * Advances the cursor and finalises any highlights that ended before {@code nextIndex}.
         */
        void advance(int nextIndex, float currentX);

        /**
         * Flushes remaining highlights at the end of the render.
         */
        void finish(int textLength, float currentX);

        /**
         * Returns the highlights computed for the current session.
         */
        List<TokenHighlight> highlights();

        /**
         * A no-op implementation used when HexText is not available.
         */
        Highlighter NOOP = new Highlighter() {
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
            public List<TokenHighlight> highlights() {
                return Collections.emptyList();
            }
        };
    }

    public interface Bridge {

        RenderTextData prepare(CharSequence text, boolean rawMode);
    }

    public interface EffectsHelper {

        int computeRainbowColor(long now, int glyphIndex, int anchorIndex);

        int computeIgniteColor(long now, int baseColor);

        float computeShakeOffset(long now, int glyphIndex);

        default boolean isOperational() {
            return true;
        }
    }

    private static final class EffectsHelperHolder {
        private static final EffectsHelper INSTANCE = create();

        private static EffectsHelper create() {
            try {
                return new HexTextDynamicEffectsHelper();
            } catch (Throwable t) {
                ModStatus.LOGGER.warn("Failed to initialize HexText dynamic effects", t);
                return NOOP_EFFECTS_HELPER;
            }
        }
    }

    private static final EffectsHelper NOOP_EFFECTS_HELPER = new EffectsHelper() {
        @Override
        public int computeRainbowColor(long now, int glyphIndex, int anchorIndex) {
            return 0;
        }

        @Override
        public int computeIgniteColor(long now, int baseColor) {
            return baseColor & 0x00FFFFFF;
        }

        @Override
        public float computeShakeOffset(long now, int glyphIndex) {
            return 0.0f;
        }

        @Override
        public boolean isOperational() {
            return false;
        }
    };
}
