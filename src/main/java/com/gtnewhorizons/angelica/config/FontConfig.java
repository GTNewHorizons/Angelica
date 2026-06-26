package com.gtnewhorizons.angelica.config;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.gtnewhorizons.angelica.client.font.BatchingFontRenderer;

import static com.gtnewhorizons.angelica.client.font.BatchingFontRenderer.reloadShaders;
import static com.gtnewhorizons.angelica.client.font.BatchingFontRenderer.reloadFonts;

@Config(modid = "angelica", category = "customfont")
public class FontConfig {
    @Config.Comment("Whether or not to use custom fonts when drawing text.")
    @Config.DefaultBoolean(false)
    public static boolean enableCustomFont;

    public static void setEnableCustomFont(boolean enableCustomFont) {
        FontConfig.enableCustomFont = enableCustomFont;
        reloadFonts();
    }

    @Config.Comment("Name of the primary custom font. Best not to set it from here.")
    @Config.DefaultString("(none)")
    public static String customFontNamePrimary;

    @Config.Comment("Name of the fallback custom font. Best not to set it from here.")
    @Config.DefaultString("(none)")
    public static String customFontNameFallback;

    @Config.Comment("The quality at which custom fonts are rendered, making them less pixelated but increasing memory usage.")
    @Config.DefaultInt(32)
    @Config.RangeInt(min = 6, max = 72)
    public static int customFontQuality;

    public static void setCustomFontQuality(float customFontQuality) {
        FontConfig.customFontQuality = (int) customFontQuality;
        reloadFonts();
    }

    @Config.Comment("Controls the distance at which the font's shadow is drawn.")
    @Config.DefaultFloat(1F)
    @Config.RangeFloat(min = 0F, max = 2F)
    public static float fontShadowOffset;

    @Config.Comment("Adds extra brightness to fonts. 0 = None, 10 = 2x brighter")
    @Config.DefaultInt(0)
    @Config.RangeInt(min = 0, max = 10)
    public static int fontBrightness;

    public static void setFontBrightness(float fontBrightness) {
        FontConfig.fontBrightness = (int) fontBrightness;
        BatchingFontRenderer.reloadShaders();
    }

    @Config.Comment("The number of bold copies to be drawn behind text at various offsets.")
    @Config.DefaultInt(2)
    @Config.RangeInt(min = 1, max = 8)
    public static int boldCopies;

    @Config.Comment("Influences the aspect ratio of each glyph.")
    @Config.DefaultFloat(0F)
    @Config.RangeFloat(min = -1F, max = 1F)
    public static float glyphAspect;

    public static void setGlyphAspect(float glyphAspect) {
        FontConfig.glyphAspect = glyphAspect;
        reloadFonts();
    }

    @Config.Comment("Scale of each glyph, whitespace excluded.")
    @Config.DefaultFloat(1F)
    @Config.RangeFloat(min = 0.1F, max = 3F)
    public static float glyphScale;

    public static void setGlyphScale(float glyphScale) {
        FontConfig.glyphScale = glyphScale;
        reloadFonts();
    }

    @Config.Comment("Whitespace scale.")
    @Config.DefaultInt(1)
    @Config.RangeInt(min = 1, max = 3)
    public static int whitespaceScale;

    public static void setWhitespaceScale(int whitespaceScale) {
        FontConfig.whitespaceScale = whitespaceScale;
        reloadFonts();
    }

    @Config.Comment("Adds extra spacing between glyphs.")
    @Config.DefaultFloat(0F)
    @Config.RangeFloat(min = -2F, max = 2F)
    public static float glyphSpacing;

    public static void setGlyphSpacing(float glyphSpacing) {
        FontConfig.glyphSpacing = glyphSpacing;
        reloadFonts();
    }

    @Config.Comment("Controls font antialiasing. 0 = none, 1 = 4x MSAA, 2 = 16x MSAA.")
    @Config.DefaultInt(2)
    @Config.RangeInt(min = 0, max = 2)
    public static int fontAAMode;

    public static void setFontAAMode(float fontAAMode) {
        FontConfig.fontAAMode = (int) fontAAMode;
        reloadShaders();
    }

    @Config.Comment("Affects font antialiasing sample spacing. Higher values increase blur.")
    @Config.DefaultInt(7)
    @Config.RangeInt(min = 1, max = 24)
    public static int fontAAStrength;

    public static void setFontAAStrength(float fontAAStrength) {
        FontConfig.fontAAStrength = (int) fontAAStrength;
        reloadShaders();
    }

    @Config.Comment("Custom font scale multiplier, for bugfixes.")
    @Config.DefaultFloat(1.5F)
    @Config.RangeFloat(min = 0.1F, max = 3F)
    public static float customFontScale; //TODO wat

    public static void setCustomFontScale(float customFontScale) {
        FontConfig.customFontScale = customFontScale;
        reloadFonts();
    }

    @Config.Comment("A shadow offset multiplier applied only to the Unicode font.")
    @Config.DefaultFloat(0.5F)
    @Config.RangeFloat(min = 0F, max = 2F)
    public static float fontShadowOffsetUC;

    @Config.Comment({"Mods can provide Angelica with a mapping of special characters to either get replaced with " +
        "different characters or rendered using the default Unicode font. Setting this to false disables the " +
        "replacement, which is useful in cases where custom fonts have direct support for these special characters. ",
        "See for additional details: https://github.com/GTNewHorizons/Angelica/issues/1239#issuecomment-3729877936"})
    @Config.DefaultBoolean(true)
    public static boolean enableGlyphReplacements;

    public static void setEnableGlyphReplacements(boolean enableGlyphReplacements) {
        FontConfig.enableGlyphReplacements = enableGlyphReplacements;
        reloadFonts();
    }
}
