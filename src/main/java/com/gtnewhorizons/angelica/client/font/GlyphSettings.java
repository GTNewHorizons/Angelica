package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizon.gtnhlib.util.font.FontRendering;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.config.FontConfig;
import com.gtnewhorizons.angelica.hudcaching.HUDCaching;

public record GlyphSettings(int generation, int fontAAMode, int fontAAStrength, int shadowCopies, int boldCopies, int fontShadowOffset, int fontShadowOffsetUC,
                            int glyphScale, int glyphAspect, int glyphSpacing, int whitespaceScale, int customFontScale, int waveAmplitude, int flags) {

    private static final int CUSTOM_FONT = 1;
    private static final int MODERN_FONT = 1 << 1;
    private static final int GLYPH_REPLACEMENTS = 1 << 2;
    private static final int RGB_COLORS = 1 << 3;
    private static final int GRADIENTS = 1 << 4;
    private static final int RAINBOW = 1 << 5;
    private static final int WAVE = 1 << 6;
    private static final int DINNERBONE = 1 << 7;
    private static final int DROP_SHADOW = 1 << 8;
    private static final int HEX_RESETS_STYLES = 1 << 9;
    private static final int CACHE_OVERRIDE = 1 << 10;
    private static final int METRIC_FLAGS = CUSTOM_FONT | MODERN_FONT | GLYPH_REPLACEMENTS | RGB_COLORS | GRADIENTS | DROP_SHADOW | HEX_RESETS_STYLES;

    // One slot per renderingCacheOverride value: the HUD-cache redraw flips it every refresh, and a single slot would allocate on each flip.
    private static final GlyphSettings[] CURRENT = new GlyphSettings[2];

    public static GlyphSettings current() {
        final boolean cacheOverride = HUDCaching.renderingCacheOverride;
        final int generation = FontProviderCache.generation();
        final int fontAAMode = FontConfig.fontAAMode;
        final int fontAAStrength = FontConfig.fontAAStrength;
        final int shadowCopies = FontConfig.shadowCopies;
        final int boldCopies = FontConfig.boldCopies;
        final int fontShadowOffset = Float.floatToIntBits(FontConfig.fontShadowOffset);
        final int fontShadowOffsetUC = Float.floatToIntBits(FontConfig.fontShadowOffsetUC);
        final int glyphScale = Float.floatToIntBits(FontConfig.glyphScale);
        final int glyphAspect = Float.floatToIntBits(FontConfig.glyphAspect);
        final int glyphSpacing = Float.floatToIntBits(FontConfig.glyphSpacing);
        final int whitespaceScale = Float.floatToIntBits(FontConfig.whitespaceScale);
        final int customFontScale = Float.floatToIntBits(FontConfig.customFontScale);
        final int waveAmplitude = Float.floatToIntBits(AngelicaConfig.waveAmplitude);
        final int flags = (FontConfig.enableCustomFont ? CUSTOM_FONT : 0)
            | (FontConfig.enableModernFont ? MODERN_FONT : 0)
            | (FontConfig.enableGlyphReplacements ? GLYPH_REPLACEMENTS : 0)
            | (AngelicaConfig.enableRGBColors ? RGB_COLORS : 0)
            | (AngelicaConfig.enableGradients ? GRADIENTS : 0)
            | (AngelicaConfig.enableRainbow ? RAINBOW : 0)
            | (AngelicaConfig.enableWaveText ? WAVE : 0)
            | (AngelicaConfig.enableDinnerboneText ? DINNERBONE : 0)
            | (AngelicaConfig.enableDropShadow ? DROP_SHADOW : 0)
            | (FontRendering.hexColorResetsStyles() ? HEX_RESETS_STYLES : 0)
            | (cacheOverride ? CACHE_OVERRIDE : 0);
        final int slot = cacheOverride ? 1 : 0;
        final GlyphSettings cached = CURRENT[slot];
        if (cached != null
            && cached.generation == generation
            && cached.fontAAMode == fontAAMode
            && cached.fontAAStrength == fontAAStrength
            && cached.shadowCopies == shadowCopies
            && cached.boldCopies == boldCopies
            && cached.fontShadowOffset == fontShadowOffset
            && cached.fontShadowOffsetUC == fontShadowOffsetUC
            && cached.glyphScale == glyphScale
            && cached.glyphAspect == glyphAspect
            && cached.glyphSpacing == glyphSpacing
            && cached.whitespaceScale == whitespaceScale
            && cached.customFontScale == customFontScale
            && cached.waveAmplitude == waveAmplitude
            && cached.flags == flags) {
            return cached;
        }
        final GlyphSettings live = new GlyphSettings(generation, fontAAMode, fontAAStrength, shadowCopies, boldCopies, fontShadowOffset, fontShadowOffsetUC,
            glyphScale, glyphAspect, glyphSpacing, whitespaceScale, customFontScale, waveAmplitude, flags);
        CURRENT[slot] = live;
        return live;
    }

    public boolean sameMetricsAs(GlyphSettings other) {
        return generation == other.generation
            && glyphScale == other.glyphScale
            && glyphAspect == other.glyphAspect
            && glyphSpacing == other.glyphSpacing
            && whitespaceScale == other.whitespaceScale
            && customFontScale == other.customFontScale
            && (flags & METRIC_FLAGS) == (other.flags & METRIC_FLAGS);
    }
}
