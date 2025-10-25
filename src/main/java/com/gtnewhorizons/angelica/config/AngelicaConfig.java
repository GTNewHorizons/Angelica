package com.gtnewhorizons.angelica.config;

import com.gtnewhorizon.gtnhlib.config.Config;

@Config(modid = "angelica", filename = "angelica-modules")
public class AngelicaConfig {
    @Config.Comment("Enable Sodium rendering")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean enableSodium;

    @Config.Comment("Enable NotFine Options")
    @Config.DefaultBoolean(false)
    public static boolean enableNotFineOptions;

    @Config.Comment("Enable Reese's Sodium Options")
    @Config.DefaultBoolean(true)
    public static boolean enableReesesSodiumOptions;

    @Config.Comment("Enable Sodium fluid rendering")
    @Config.DefaultBoolean(false)
    @Config.RequiresMcRestart
    public static boolean enableSodiumFluidRendering;

    @Config.Comment("Inject BakedModel rendering into some vanilla blocks")
    @Config.DefaultBoolean(false)
    @Config.RequiresMcRestart
    public static boolean injectQPRendering;

    @Config.Comment("Enable Angelica's test blocks")
    @Config.DefaultBoolean(false)
    @Config.Ignore()
    public static boolean enableTestBlocks;

    @Config.Comment("Enable Iris Shaders [Requires Sodium]")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean enableIris;

    @Config.Comment("Enable MCPatcherForge features, still in Alpha. Individual features are toggled in mcpatcher.json")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean enableMCPatcherForgeFeatures;

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

    @Config.Comment("Renders the HUD elements once per 20 frames (by default) and reuses the pixels to improve performance. [Experimental]")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean enableHudCaching;
    @Config.Comment("Inject a conditional early return into all RenderGameOverlayEvent receivers; Requires enableHudCaching")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean enableHudCachingEventTransformer;

    @Config.Comment("The amount of frames to wait before updating the HUD elements. [Experimental]")
    @Config.DefaultInt(20)
    @Config.RangeInt(min = 1, max = 60)
    public static int hudCachingFPS = 20;

    @Config.Comment("Batch drawScreen fonts [Experimental]")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean enableFontRenderer;

    @Config.Comment("Enable Dynamic Lights")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean enableDynamicLights;

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

    @Config.Comment("Fix RenderBlockFluid reading the block type from the world access multiple times")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean fixFluidRendererCheckingBlockAgain;

    @Config.Comment("Dynamically modifies the render distance of dropped items entities to preserve performance."
                  + " It starts reducing the render distance when exceeding the threshold set below.")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean dynamicItemRenderDistance;

    @Config.Comment("Max amount of dropped item rendered")
    @Config.DefaultInt(256)
    @Config.RangeInt(min = 32, max = 2048)
    public static int droppedItemLimit;

    @Config.Comment("Use total world time instead of normal world time. Allows most shader animations to play when "
                  + "doDaylightCycle is off, but causes shader animations to desync from time of day.")
    @Config.DefaultBoolean(false)
    public static boolean useTotalWorldTime;

    @Config.Comment("Enable Debug Logging")
    @Config.DefaultBoolean(false)
    @Config.RequiresMcRestart
    public static boolean enableDebugLogging;

    @Config.Comment("Enables PBR atlas dumping")
    @Config.DefaultBoolean(false)
    @Config.Name("Enable PBR Debug")
    public static boolean enablePBRDebug;

    @Config.Comment("Enable Zoom")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean enableZoom;

    @Config.Comment("Optimizes in-world item rendering")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean optimizeInWorldItemRendering;

    @Config.Comment("Upper limit for the amount of VBO's to cache for optimized item rendering. Higher number can potentially use more VRAM.")
    @Config.DefaultInt(512)
    @Config.RangeInt(min = 256, max = 1024)
    public static int itemRendererCacheSize;

    @Config.Comment("Render distance for the spinning mob inside mod spawners")
    @Config.DefaultDouble(16D)
    @Config.RangeDouble(min = 16D, max = 64D)
    public static double mobSpawnerRenderDistance;

    @Config.Comment("Switches to an alternate FPS limiter that gives more stable frametimes, in exchange for slightly " +
        "more latency. Will never introduce more than one frame of latency, and has a lower impact at higher framerates.")
    @Config.DefaultBoolean(false)
    public static boolean sleepBeforeSwap;

    @Config.Comment("Allows unicode languages to use an odd gui scale")
    @Config.DefaultBoolean(true)
    public static boolean removeUnicodeEvenScaling;
}
