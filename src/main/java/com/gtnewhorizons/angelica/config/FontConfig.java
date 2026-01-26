package com.gtnewhorizons.angelica.config;

import com.gtnewhorizon.gtnhlib.config.Config;

@Config(modid = "angelica", category = "customfont")
public class FontConfig {
    @Config.Comment("Whether or not to use custom fonts when drawing text.")
    @Config.DefaultBoolean(false)
    public static boolean enableCustomFont;

    @Config.Comment("Name of the primary custom font. Best not to set it from here.")
    @Config.DefaultString("(none)")
    public static String customFontNamePrimary;

    @Config.Comment("Name of the fallback custom font. Best not to set it from here.")
    @Config.DefaultString("(none)")
    public static String customFontNameFallback;

    @Config.Comment("The quality at which custom fonts are rendered, making them less pixelated but increasing memory usage.")
    @Config.DefaultInt(30)
    @Config.RangeInt(min = 6, max = 72)
    public static int customFontQuality;

    @Config.Comment("Controls the distance at which the font's shadow is drawn.")
    @Config.DefaultFloat(1F)
    @Config.RangeFloat(min = 0F, max = 2F)
    public static float fontShadowOffset;

    @Config.Comment("The number of shadows to be drawn behind text at various offsets.")
    @Config.DefaultInt(1)
    @Config.RangeInt(min = 1, max = 8)
    public static int shadowCopies;

    @Config.Comment("The number of bold copies to be drawn behind text at various offsets.")
    @Config.DefaultInt(2)
    @Config.RangeInt(min = 1, max = 8)
    public static int boldCopies;

    @Config.Comment("Influences the aspect ratio of each glyph.")
    @Config.DefaultFloat(0F)
    @Config.RangeFloat(min = -1F, max = 1F)
    public static float glyphAspect;

    @Config.Comment("Scale of each glyph, whitespace excluded.")
    @Config.DefaultFloat(1F)
    @Config.RangeFloat(min = 0.1F, max = 3F)
    public static float glyphScale;

    @Config.Comment("Whitespace scale.")
    @Config.DefaultFloat(1F)
    @Config.RangeFloat(min = 0.1F, max = 3F)
    public static float whitespaceScale;

    @Config.Comment("Adds extra spacing between glyphs.")
    @Config.DefaultFloat(0F)
    @Config.RangeFloat(min = -2F, max = 2F)
    public static float glyphSpacing;

    @Config.Comment("Controls font antialiasing. 0 = none, 1 = 4x MSAA, 2 = 16x MSAA.")
    @Config.DefaultInt(2)
    @Config.RangeInt(min = 0, max = 2)
    public static int fontAAMode;

    @Config.Comment("Affects font antialiasing sample spacing. Higher values increase blur.")
    @Config.DefaultInt(7)
    @Config.RangeInt(min = 1, max = 24)
    public static int fontAAStrength;

    @Config.Comment("Custom font scale multiplier, for bugfixes.")
    @Config.DefaultFloat(1.5F)
    @Config.RangeFloat(min = 0.1F, max = 3F)
    public static float customFontScale;

    @Config.Comment({"Mods can provide Angelica with a mapping of special characters to either get replaced with " +
        "different characters or rendered using the default Unicode font. Setting this to false disables the " +
        "replacement, which is useful in cases where custom fonts have direct support for these special characters. ",
        "See for additional details: https://github.com/GTNewHorizons/Angelica/issues/1239#issuecomment-3729877936"})
    @Config.DefaultBoolean(true)
    public static boolean enableGlyphReplacements;
}
