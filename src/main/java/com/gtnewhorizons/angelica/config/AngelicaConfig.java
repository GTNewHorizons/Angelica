package com.gtnewhorizons.angelica.config;

@Config(modid = "angelica")
public class AngelicaConfig {
    @Config.Comment("Enable Sodium rendering")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean enableSodium;

    @Config.Comment("Enable Reese's Sodium Options")
    @Config.DefaultBoolean(true)
    public static boolean enableReesesSodiumOptions;

    @Config.Comment("Enable Sodium fluid rendering")
    @Config.DefaultBoolean(false)
    @Config.RequiresMcRestart
    public static boolean enableSodiumFluidRendering;

    @Config.Comment("Inject QuadProvider rendering into some vanilla blocks")
    @Config.DefaultBoolean(false)
    @Config.RequiresMcRestart
    public static boolean injectQPRendering;

    @Config.Comment("Enable Iris Shaders [Requires Sodium]")
    @Config.DefaultBoolean(false)
    @Config.RequiresMcRestart
    public static boolean enableIris;

    @Config.Comment("Enable MCPatcherForge features, still in Alpha. Individual features are toggled in mcpatcher.json")
    @Config.DefaultBoolean(false)
    @Config.RequiresMcRestart
    public static boolean enableMCPatcherForgeFeatures;

    @Config.Comment("Enable NotFine optimizations")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean enableNotFineOptimizations;

    @Config.Comment("Replace some vanilla render paths with more optimized versions. Disable if you encounter mixin conflicts.")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean enableVBO;

    @Config.Comment("Enable NotFine features")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean enableNotFineFeatures;

    @Config.Comment("Tweak F3 screen to be closer to modern versions. [From ArchaicFix]")
    @Config.DefaultBoolean(true)
    public static boolean modernizeF3Screen;

    @Config.Comment("Show block registry name and meta value in F3, similar to 1.8+. [From ArchaicFix]")
    @Config.DefaultBoolean(true)
    public static boolean showBlockDebugInfo;

    @Config.DefaultBoolean(true)
    @Config.Comment("Hide downloading terrain screen. [From ArchaicFix]")
    public static boolean hideDownloadingTerrainScreen;

    @Config.Comment("Show memory usage during game load. [From ArchaicFix]")
    @Config.DefaultBoolean(true)
    public static boolean showSplashMemoryBar;


    @Config.Comment("Renders the HUD elements once per tick and reuses the pixels to improve performance. [Experimental]")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean enableHudCaching;

    @Config.Comment("Batch drawScreen fonts [Experimental]")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean enableFontRenderer;

    @Config.Comment("Optimize world update light. [From Hodgepodge]")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean optimizeWorldUpdateLight;

    @Config.Comment("Speedup Animations. [From Hodgepodge]")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean speedupAnimations;

    @Config.Comment("Optimize Texture Loading. [From Hodgepodge]")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean optimizeTextureLoading;

    @Config.Comment("Fix thread-safety in lotrs rendering")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean fixLotrSodiumCompat;

    @Config.Comment("Enable Debug Logging")
    @Config.DefaultBoolean(false)
    @Config.RequiresMcRestart
    public static boolean enableDebugLogging;
}
