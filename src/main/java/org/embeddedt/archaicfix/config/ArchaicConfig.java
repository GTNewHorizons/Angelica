package org.embeddedt.archaicfix.config;

@Config(modid = "archaicfix")
public class ArchaicConfig {
    @Config.Comment("Enables the 1.8-style occlusion culling originally developed by CoFHTweaks. Not compatible with OptiFine or FastCraft.")
    @Config.DefaultBoolean(false)
    @Config.RequiresMcRestart
    public static boolean enableOcclusionTweaks;

    @Config.Comment("EXPERIMENTAL: Enables multi-threaded chunk updating. At the moment it only works if enableOcclusionTweaks is turned on.")
    @Config.DefaultBoolean(false)
    @Config.RequiresMcRestart
    public static boolean enableThreadedChunkUpdates;

    @Config.Comment("The number of threads to use for chunk building. The default is 1, which shouldn't be any laggier than vanilla but will reduce stutter. If you have a lot of cores increasing this may be beneficial.")
    @Config.DefaultInt(1)
    @Config.RangeInt(min = 1)
    @Config.RequiresMcRestart
    public static int numChunkUpdateThreads;

    @Config.Comment("Changes the enableThreadedChunkUpdates option to never wait for chunk updates. Improves framerate when blocks are placed or destroyed, at the cost of introducing visual delay. This is analogous to 1.18's 'Chunk Builder' option, false meaning 'Fully Blocking', and true meaning 'Threaded'.")
    @Config.DefaultBoolean(false)
    public static boolean disableBlockingChunkUpdates;

    @Config.DefaultBoolean(true)
    public static boolean hideDownloadingTerrainScreen;

    @Config.Comment("Prevents buttons from showing a yellow text color when hovered, as was done in 1.14+.")
    @Config.DefaultBoolean(false)
    public static boolean enableNewButtonAppearance;

    @Config.Comment("Use a faster and asynchronous implementation of creative search.")
    @Config.DefaultBoolean(true)
    public static boolean asyncCreativeSearch;

    @Config.Comment("Cap the integrated server render distance at a minimum of 8 chunks, and adjust despawn ranges so mobs will always despawn properly on low render distances.")
    @Config.DefaultBoolean(true)
    public static boolean fixMobSpawnsAtLowRenderDist;

    @Config.Comment("Replace the regional difficulty calculation with a TMCW-style one that increases with playtime, not time per chunk.")
    @Config.DefaultBoolean(false)
    public static boolean betterRegionalDifficulty;

    @Config.Comment("Allow 65000 block updates to be performed per tick, rather than 1000.")
    @Config.DefaultBoolean(true)
    public static boolean increaseBlockUpdateLimit;

    @Config.Comment("EXPERIMENTAL: Cache matching crafting recipes to avoid needing to scan the whole list each time.")
    @Config.DefaultBoolean(false)
    public static boolean cacheRecipes;

    @Config.Comment("Disable spawn chunks")
    @Config.DefaultBoolean(false)
    public static boolean disableSpawnChunks;

    @Config.Comment("Reduce lag caused by item entities")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean itemLagReduction;

    @Config.Comment("Increase the amount of armor mobs wear on average. From TMCW.")
    @Config.DefaultBoolean(false)
    public static boolean increaseMobArmor;

    @Config.Comment("Increase the maximum render distance if OptiFine and FastCraft are not installed.")
    @Config.DefaultBoolean(false)
    public static boolean raiseMaxRenderDistance;

    @Config.Comment("What the maximum render distance should be if raiseMaxRenderDistance is enabled.")
    @Config.DefaultInt(32)
    @Config.RangeInt(min = 16, max = 128)
    public static int newMaxRenderDistance;

    @Config.Comment("EXPERIMENTAL: Replace the Thaumcraft hashing implementation. This really hasn't been tested and probably breaks everything.")
    @Config.DefaultBoolean(false)
    public static boolean betterThaumcraftHashing;

    @Config.Comment("Log when cascading worldgen occurs.")
    @Config.DefaultBoolean(true)
    public static boolean logCascadingWorldgen;

    @Config.Comment("Print a stacktrace when cascading worldgen occurs. Use only for development as this will add more lag in game.")
    @Config.DefaultBoolean(false)
    public static boolean logCascadingWorldgenStacktrace;

    @Config.Comment("Fix instances of cascading worldgen in Mekanism.")
    @Config.DefaultBoolean(true)
    public static boolean fixMekanismCascadingWorldgen;

    @Config.Comment("Fix instances of cascading worldgen in vanilla Minecraft. Turn this option off if you require 100% seed parity.")
    @Config.DefaultBoolean(true)
    public static boolean fixVanillaCascadingWorldgen;

    @Config.Comment("Force all mixins to be loaded and the cache to be cleared. This saves RAM, but may reveal bugs in mods' mixin configs. Based on MemoryLeakFix.")
    @Config.DefaultBoolean(false)
    public static boolean clearMixinCache;

    @Config.Comment("Clean up LaunchClassLoader cache.")
    @Config.DefaultBoolean(true)
    public static boolean clearLaunchLoaderCache;

    @Config.Comment("Only show GT6 tooltip data when Shift is pressed.")
    @Config.DefaultBoolean(true)
    public static boolean hideGT6TooltipDataBehindKey;

    @Config.Comment("Fix the 'TickNextTick list out of synch' error.")
    @Config.DefaultBoolean(true)
    public static boolean fixTickListSynchronization;

    @Config.Comment("Make sure entities don't spawn inside blocks that would make them suffocate. Off by default because it might reduce the number of passive entities that spawn during worldgen.")
    @Config.DefaultBoolean(false)
    public static boolean preventEntitySuffocationWorldgen;

    @Config.Comment("Show block registry name and meta value in F3, similar to 1.8+.")
    @Config.DefaultBoolean(false)
    public static boolean showBlockDebugInfo;

    @Config.Comment("Prevent entities outside a certain distance from being ticked. This does not affect tile entities, and is essentially another view distance slider.")
    @Config.DefaultBoolean(false)
    public static boolean optimizeEntityTicking;

    @Config.Comment("Squared distance outside which most entities aren't ticked, default is 64 blocks.")
    @Config.DefaultInt(4096)
    public static int optimizeEntityTickingDistance;

    @Config.Comment("Distance in chunks at which blocks are ticked, the default value of 0 means to use the render distance.")
    @Config.DefaultInt(0)
    public static int optimizeBlockTickingDistance;

    @Config.Comment("List of entities to ignore for entity ticking optimization.")
    @Config.DefaultStringList({ "Wither", "EnderDragon" })
    public static String[] optimizeEntityTickingIgnoreList;

    @Config.Comment("Disable OptiFine's version checker.")
    @Config.DefaultBoolean(true)
    public static boolean disableOFVersionCheck;

    @Config.Comment("Fix TileEntity unloading being laggy.")
    @Config.DefaultBoolean(true)
    public static boolean fixTEUnloadLag;

    @Config.Comment("Attempt to fix hanging in SP when logging in.")
    @Config.DefaultBoolean(true)
    public static boolean fixLoginRaceCondition;

    @Config.Comment("Fix MC-1406.")
    @Config.DefaultBoolean(true)
    public static boolean fixPlacementFlicker;

    @Config.Comment("Optimize vanilla's ID-to-object map.")
    @Config.DefaultBoolean(true)
    public static boolean optimizeObjectIntIdentityMap;

    @Config.Comment("Fix the client leaking memory by holding on to skins and player instances that aren't needed.")
    @Config.DefaultBoolean(true)
    public static boolean fixSkinMemoryLeak;

    @Config.Comment("Show memory usage during game load.")
    @Config.DefaultBoolean(true)
    public static boolean showSplashMemoryBar;

    @Config.Comment("Tweak F3 screen to be closer to modern versions.")
    @Config.DefaultBoolean(true)
    public static boolean modernizeF3Screen;

    @Config.Comment("Replace tall grass texture with modern version.")
    @Config.DefaultBoolean(false)
    public static boolean modernizeTextures;

    @Config.Comment("Remove JourneyMap debug feature.")
    @Config.DefaultBoolean(false)
    public static boolean removeJourneymapDebug;

    @Config.Comment("When true, the Phosphor backport is enabled. Disabling is not recommended as it will generally add lag.")
    @Config.DefaultBoolean(true)
    public static boolean enablePhosphor;

    @Config.Comment("When true, dropped items will always render as though fancy graphics are enabled.")
    @Config.DefaultBoolean(false)
    public static boolean forceFancyItems;

    @Config.Comment("Disable Food Plus' update message.")
    @Config.DefaultBoolean(true)
    public static boolean disableFoodPlusUpdates;

    @Config.Comment("Add the Let's Encrypt root certificates to the default trust store.")
    @Config.DefaultBoolean(false)
    public static boolean enableLetsEncryptRoot;
}
