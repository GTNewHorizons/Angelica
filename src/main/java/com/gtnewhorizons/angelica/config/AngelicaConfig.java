package com.gtnewhorizons.angelica.config;

@Config(modid = "angelica")
public class AngelicaConfig {
    @Config.Comment("Enable Sodium rendering")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean enableSodium;

    @Config.Comment("Enable Iris Shaders [Requires Sodium]")
    @Config.DefaultBoolean(false)
    @Config.RequiresMcRestart
    public static boolean enableIris;

    @Config.Comment("Enable NotFine optimizations")
    @Config.DefaultBoolean(false)
    @Config.RequiresMcRestart
    public static boolean enableNotFineOptimizations;

    @Config.Comment("Enable NotFine features")
    @Config.DefaultBoolean(false)
    @Config.RequiresMcRestart
    public static boolean enableNotFineFeatures;

    @Config.Comment("Tweak F3 screen to be closer to modern versions. [From ArchaicFix]")
    @Config.DefaultBoolean(true)
    public static boolean modernizeF3Screen;

    @Config.Comment("Show block registry name and meta value in F3, similar to 1.8+. [From ArchaicFix]")
    @Config.DefaultBoolean(true)
    public static boolean showBlockDebugInfo;

    @Config.DefaultBoolean(true)
    public static boolean hideDownloadingTerrainScreen;

    @Config.Comment("Show memory usage during game load. [From ArchaicFix]")
    @Config.DefaultBoolean(true)
    public static boolean showSplashMemoryBar;


    @Config.Comment("Renders the HUD elements once per tick and reuses the pixels to improve performance. [Experimental]")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    // TODO disabled for now as it doesn't render items with an enchantment glint in the hotbar properly as well as doesn't render the vignette
    public static boolean enableHudCaching;

    @Config.Comment("Batch render fonts [Experimental]")
    @Config.DefaultBoolean(false)
    @Config.RequiresMcRestart
    public static boolean enableFontRenderer;
}
