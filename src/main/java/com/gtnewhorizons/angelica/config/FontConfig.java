package com.gtnewhorizons.angelica.config;

import com.gtnewhorizon.gtnhlib.config.Config;

@Config(modid = "angelica", category = "customfont")
public class FontConfig {
    @Config.Comment("Whether or not to use a custom font when drawing text.")
    @Config.DefaultBoolean(false)
    public static boolean enableCustomFont;

    @Config.Comment("Name of the custom font in use. Best not to set it from here.")
    @Config.DefaultString("(none)")
    public static String customFontName;

    @Config.Comment("The quality at which the custom font is rendered, making it less pixelated but increasing memory usage.")
    @Config.DefaultInt(36)
    @Config.RangeInt(min = 12, max = 72)
    public static int customFontQuality;

    @Config.Comment("Controls the distance at which the font's shadow is drawn")
    @Config.DefaultFloat(1F)
    @Config.RangeFloat(min = 0F, max = 2F)
    public static float fontShadowOffset;

    @Config.Comment("Horizontal scale of each glyph, whitespace excluded.")
    @Config.DefaultFloat(1F)
    @Config.RangeFloat(min = 0.1F, max = 3F)
    public static float glyphScaleX;

    @Config.Comment("Vertical scale of each glyph, whitespace excluded.")
    @Config.DefaultFloat(1F)
    @Config.RangeFloat(min = 0.1F, max = 3F)
    public static float glyphScaleY;

    @Config.Comment("Whitespace scale.")
    @Config.DefaultFloat(1F)
    @Config.RangeFloat(min = 0.1F, max = 3F)
    public static float whitespaceScale;

    @Config.Comment("Adds extra spacing between glyphs.")
    @Config.DefaultFloat(0F)
    @Config.RangeFloat(min = -2F, max = 2F)
    public static float glyphSpacing;

    @Config.Comment("Controls font antialiasing. 0 = none, 1 = 4x MSAA, 2 = 16x MSAA.")
    @Config.DefaultInt(1)
    @Config.RangeInt(min = 0, max = 2)
    public static int fontAAMode;

    @Config.Comment("Affects font antialiasing sample spacing. Higher values increase blur.")
    @Config.DefaultInt(2)
    @Config.RangeInt(min = 1, max = 12)
    public static int fontAAStrength;
}
