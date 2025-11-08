package com.gtnewhorizons.angelica.compat.hextext;

import com.gtnewhorizons.angelica.compat.ModStatus;
import com.gtnewhorizons.angelica.compat.hextext.effects.HexTextDynamicEffectsHelper;
import com.gtnewhorizons.angelica.compat.hextext.highlight.HexTextTokenHighlighter;
import com.gtnewhorizons.angelica.compat.hextext.render.HexTextRenderBridge;
import com.gtnewhorizons.angelica.compat.hextext.render.HexTextRenderData;
import com.gtnewhorizons.angelica.compat.hextext.HexTextServices;
import java.util.Collections;
import java.util.List;
import kamkeel.hextext.api.rendering.ColorService;
import kamkeel.hextext.api.rendering.RenderingEnvironmentService;
import kamkeel.hextext.api.rendering.TokenHighlightService;
import kamkeel.hextext.api.text.TextFormatter;
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
        if (!HexTextServices.isSupported()) {
            return Highlighter.NOOP;
        }

        RenderingEnvironmentService environmentService = HexTextServices.renderEnvironment();
        TokenHighlightService highlightService = HexTextServices.tokenHighlighter();
        TextFormatter formatter = HexTextServices.textFormatter();
        if (environmentService == null
            || highlightService == null
            || formatter == null
            || !environmentService.isRawTextRendering()) {
            return Highlighter.NOOP;
        }
        try {
            return new HexTextTokenHighlighter(environmentService, highlightService, formatter);
        } catch (Throwable t) {
            ModStatus.LOGGER.warn("Failed to initialize HexText token highlighting", t);
            return Highlighter.NOOP;
        }
    }

    /**
     * Factory for the HexText render bridge used by the colour resolver.
     */
    public static Bridge tryCreateBridge() {
        if (!HexTextServices.isSupported()) {
            return null;
        }
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
        ColorService colorService = HexTextServices.colorService();
        if (colorService != null) {
            return colorService.calculateShadowColor(rgb);
        }
        return (rgb & 0xFCFCFC) >> 2;
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
        List<Highlight> highlights();

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
            public List<Highlight> highlights() {
                return Collections.emptyList();
            }
        };
    }

    public interface Bridge {

        HexTextRenderData prepare(CharSequence text, boolean rawMode);
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
            if (!HexTextServices.isSupported()) {
                return NOOP_EFFECTS_HELPER;
            }
            try {
                EffectsHelper helper = new HexTextDynamicEffectsHelper(HexTextServices.dynamicEffects());
                return helper.isOperational() ? helper : NOOP_EFFECTS_HELPER;
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

    public static final class Highlight {
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

        public float getX() {
            return x;
        }

        public float getY() {
            return y;
        }

        public float getWidth() {
            return width;
        }

        public int getColor() {
            return color;
        }
    }
}
