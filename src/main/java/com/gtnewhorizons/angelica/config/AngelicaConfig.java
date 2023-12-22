package com.gtnewhorizons.angelica.config;

@Config(modid = "angelica")
public class AngelicaConfig {
    @Config.Comment("Enable Sodium rendering")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean enableSodium;

    @Config.Comment("Enable Sodium fluid rendering")
    @Config.DefaultBoolean(false)
    @Config.RequiresMcRestart
    public static boolean enableSodiumFluidRendering;

    @Config.Comment("Enable Iris Shaders [Requires Sodium]")
    @Config.DefaultBoolean(false)
    @Config.RequiresMcRestart
    public static boolean enableIris;

    @Config.Comment("Enable MCPatcherForge features, still in Alpha. Individual features are toggled in mcpatcher.json")
    @Config.DefaultBoolean(false)
    @Config.RequiresMcRestart
    public static boolean enableMCPatcherForgeFeatures;

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
    // TODO disabled for now as it doesn't drawScreen items with an enchantment glint in the hotbar properly as well as doesn't drawScreen the vignette
    public static boolean enableHudCaching;

    @Config.Comment("Batch drawScreen fonts [Experimental]")
    @Config.DefaultBoolean(false)
    @Config.RequiresMcRestart
    public static boolean enableFontRenderer;
}
