package com.gtnewhorizons.angelica.config;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.gtnewhorizons.angelica.rendering.culling.GpuCulling;
import net.coderbot.iris.shadows.ShadowGraphGate;

@Config(modid = "angelica", filename = "angelica-modules")
public class AngelicaConfig {
    @Config.Comment("Enable multi-threaded chunk building for improved performance")
    @Config.DefaultBoolean(true)
    @Config.RequiresWorldRestart
    public static boolean enableThreadedChunkBuilding;

    @Config.Comment({
        "GPU-driven chunk culling mode (requires compute shader support):",
        "  CPU_ONLY - Compute culling off; CPU emitter handles culling.",
        "  COMPUTE  - GPU compute frustum cull."
    })
    @Config.DefaultEnum("CPU_ONLY")
    public static GpuCullingMode gpuCullingMode;

    @Config.Comment("Number of chunk builder threads. 0 = auto-detect, -1 = use single-threaded fallback")
    @Config.DefaultInt(0)
    @Config.RangeInt(min = -1, max = 16)
    @Config.RequiresWorldRestart
    public static int chunkBuilderThreadCount;

    @Config.Comment("Decode block and item textures on multiple threads for faster startup. Disable if you notice missing or broken textures.")
    @Config.DefaultBoolean(true)
    public static boolean enableParallelAtlasDecode;

    @Config.Comment("Generate texture mipmaps on multiple threads for faster startup. Disable if you notice broken mipmaps.")
    @Config.DefaultBoolean(true)
    public static boolean enableParallelAtlasMipmaps;

    @Config.Comment("Upload the block and item texture atlas in large batches instead of one texture at a time. Disable if you notice missing or broken textures.")
    @Config.DefaultBoolean(true)
    public static boolean enableBatchedAtlasUpload;

    @Config.Comment("Resource pack classes the player allows to be read on worker threads during texture loading.")
    @Config.DefaultStringList({})
    public static String[] atlasTrustedPacks;

    @Config.Comment("Built-in trusted resource pack classes the player has turned off.")
    @Config.DefaultStringList({})
    public static String[] atlasDistrustedPacks;

    @Config.Comment("Resource pack classes seen during texture loading that are not trusted. Recorded automatically; trust them from Video Settings > Performance.")
    @Config.DefaultStringList({})
    public static String[] atlasObservedPacks;

    @Config.Comment("Worker threads for startup and shader loading work. 0 = automatic.")
    @Config.DefaultInt(0)
    @Config.RangeInt(min = 0, max = 32)
    @Config.RequiresMcRestart
    public static int workerThreadCount;

    @Config.Comment("Inject BakedModel rendering into some vanilla blocks")
    @Config.DefaultBoolean(false)
    @Config.RequiresMcRestart
    public static boolean injectQPRendering;

    @Config.Comment("Enable Angelica's test blocks")
    @Config.DefaultBoolean(false)
    @Config.Ignore()
    public static boolean enableTestBlocks;

    @Config.Comment("Enable Iris Shaders")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean enableIris;

    @Config.Comment("Enable MCPatcherForge features, still in Alpha. Individual features are toggled in mcpatcher.json")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean enableMCPatcherForgeFeatures;

    @Config.Comment("Replace main menu panorama with modern equivalent.")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean enablePanoramaBlurShader;

    @Config.Comment("Replace cloud renderer with a VBO version.")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean enableVBOClouds;

    @Config.Comment("Replace rain/snow rendering with a cached, instanced version.")
    @Config.DefaultBoolean(true)
    public static boolean enableInstancedWeather;

    @Config.Comment("Uses cached attributes for VBO rendering, resulting in less CPU overhead. Disable if you notice any graphical issues.")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean enableVAO;

    @Config.Comment("Enables DSA (Direct State Access) for faster bindings. Disable if you notice terrible performance.")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean enableDSA;

    @Config.Comment("Enable NotFine features")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean enableNotFineFeatures;

    @Config.Comment("Tweak F3 screen to be closer to modern versions. [From ArchaicFix]")
    @Config.DefaultBoolean(true)
    public static boolean modernizeF3Screen;

    @Config.Comment("Group F3 debug information into labeled panels.")
    @Config.DefaultBoolean(true)
    public static boolean enableGroupedF3;

    @Config.Comment("Show block registry name and meta value in F3, similar to 1.8+. [From ArchaicFix]")
    @Config.DefaultBoolean(true)
    public static boolean showBlockDebugInfo;

    @Config.DefaultBoolean(true)
    @Config.Comment("Hide downloading terrain screen. [From ArchaicFix]")
    public static boolean hideDownloadingTerrainScreen;

    @Config.Comment("Show memory usage during game load. [From ArchaicFix]")
    @Config.DefaultBoolean(true)
    public static boolean showSplashMemoryBar;

    @Config.Comment("Renders the HUD elements once per 20 frames (by default) and reuses the pixels to improve performance. [Semi-stable]")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean enableHudCaching;
    @Config.Comment("Inject a conditional early return into all RenderGameOverlayEvent receivers; Requires enableHudCaching")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean enableHudCachingEventTransformer;

    @Config.Comment("Enable HUD Caching at runtime. Requires enableHudCaching to be on at startup. [Semi-stable]")
    @Config.DefaultBoolean(false)
    public static boolean hudCachingActive;

    @Config.Comment("The amount of frames to wait before updating the HUD elements. [Experimental]")
    @Config.DefaultInt(20)
    @Config.RangeInt(min = 1, max = 60)
    public static int hudCachingFPS = 20;

    @Config.Comment("Batch drawScreen fonts")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean enableFontRenderer;

    @Config.Comment("Collapse vanilla sign text into a single batched draw per sign (requires font batching)")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean enableTESRSignCache;

    @Config.Comment("Cache the vanilla chest mesh and share it across chests")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean enableTESRChestCache;

    @Config.Comment("Batch and instance entity model parts, items, and shadows on FFP-managed passes")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean enableEntityBatching;

    @Config.Comment("Draw cuboid model parts from a shared unit cube (requires entity batching)")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean enableCubeInstancing;

    @Config.Comment("Skip the end-of-frame shader buffer copy by ping-ponging buffers. Disable if a shader pack misrenders.")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean shaderParityFlip;

    @Config.Comment("Cache the vanilla skull mesh per skull type/player skin and batch skull draws")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean enableTESRSkullCache;

    @Config.Comment("Cache the Thaumcraft jar liquid and batch jar draws")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean enableTESRJarCache;

    @Config.Comment("Cache the vanilla beacon beam mesh and batch beam draws")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean enableTESRBeaconCache;

    @Config.Comment("Route tile-entity renderers implementing TesrMeshProvider through the batched mesh cache")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean enableTESRProviderDispatch;

    @Config.Comment("Enable full RGB color support (16.7M colors) using &#RRGGBB syntax in text")
    @Config.DefaultBoolean(true)
    public static boolean enableRGBColors;

    @Config.Comment("Enable gradient text (&g&#start&#end)")
    @Config.DefaultBoolean(true)
    public static boolean enableGradients;

    @Config.Comment("Enable rainbow cycling text (&q)")
    @Config.DefaultBoolean(true)
    public static boolean enableRainbow;

    @Config.Comment("Enable wave/bounce animated text (&z)")
    @Config.DefaultBoolean(true)
    public static boolean enableWaveText;

    @Config.Comment("Enable upside-down text (&v)")
    @Config.DefaultBoolean(true)
    public static boolean enableDinnerboneText;

    @Config.Comment("Enable per-segment drop shadow toggle (&u) and colored shadow (&u&#RRGGBB)")
    @Config.DefaultBoolean(true)
    public static boolean enableDropShadow;

    @Config.Comment("Wave text amplitude (how far characters bounce)")
    @Config.DefaultFloat(2.0f)
    @Config.RangeFloat(min = 1.0f, max = 8.0f)
    public static float waveAmplitude;

    @Config.Comment("Convert &-prefix format codes (&#RRGGBB, &c, &l, etc.) at render time")
    @Config.DefaultBoolean(true)
    public static boolean enableAmpersandConversion;

    @Config.Comment("Enable Dynamic Lights")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean enableDynamicLights;

    @Config.Comment("Optimize world update light. [From Hodgepodge]")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean optimizeWorldUpdateLight;

    @Config.Comment("Optimize Texture Animations. [From Hodgepodge]")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean speedupAnimations;

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

    @Config.Comment("Upper limit for the amount of cached item meshes (VBOs and batched item templates) for optimized item rendering. Higher number can potentially use more memory and VRAM.")
    @Config.DefaultInt(512)
    @Config.RangeInt(min = 256, max = 1024)
    public static int itemRendererCacheSize;

    @Config.Comment("Render distance for the spinning mob inside mod spawners")
    @Config.DefaultDouble(16D)
    @Config.RangeDouble(min = 16D, max = 64D)
    public static double mobSpawnerRenderDistance;

    @Config.Comment("Allows unicode languages to use an odd gui scale")
    @Config.DefaultBoolean(true)
    public static boolean removeUnicodeEvenScaling;

    @Config.Comment({"Block corners and edges between chunks might have \"cracks\" (various lines/dots) in them.",
            "While using \"Compact Vertex Format\" makes the situation even worse.",
            "This option fixes it, though may lead to other visual artifacts.",
            "Requires game restart after changing this option to take effect"})
    @Config.DefaultBoolean(false)
    public static boolean blockCrackFix;

    @Config.Comment({
            "The \"epsilon\" value for the blockCrackFix option. ",
            "Set this a bit higher if you can still see lines/dots between solid blocks in dark areas.",
            "May cause intense flickering (z-fighting) between blocks if the value is too high"
    })
    @Config.RangeDouble(min = 0, max = 0.005)
    @Config.DefaultDouble(0.001)
    public static double blockCrackFixEpsilon;

    @Config.Comment("Block classes that have bugs when rendering with the blockCrackFix can be put here to avoid manipulating them")
    @Config.DefaultStringList({
            "net.minecraft.block.BlockCauldron",
            "net.minecraft.block.BlockStairs"
    })
    public static String[] blockCrackFixBlacklist;

    @Config.Comment({"Block classes that have render pass other than 0 but still need to be manipulated.",
                     "Add a block class here if you see flickering (z-fighting) with blockCrackFix enabled"
    })
    @Config.DefaultStringList({
            "gregtech.common.blocks.BlockOres",
            "gregtech.common.blocks.GTBlockOre",
            "shukaro.artifice.block.world.BlockOre",
            "bartworks.system.material.BWMetaGeneratedOres",
            "gtPlusPlus.core.block.base.BlockBaseOre",
            "org.pfaa.geologica.block.BrokenGeoBlock",
            "org.pfaa.geologica.block.BrickGeoBlock",
    })
    public static String[] blockCrackFixRenderPassWhitelist__;

    @Config.Comment({"List of sprites which should always be treated as translucent.",
                     "Sprites added to this list will always be considered translucent,",
                     "",
                     "Requires texture reload (F3+T) to take effect."})
    @Config.DefaultStringList({"jewelrycraft2:blockCrystal"})
    public static String[] alwaysTranslucentSprites;

    @Config.Comment({"TileEntity classnames whose render bounds change at runtime (e.g. OpenBlocks Guide).",
                     "These are always rendered and frustum-tested against their live bounds instead of the cached."})
    @Config.DefaultStringList({"openblocks.common.tileentity.TileEntityGuide",
                               "openblocks.common.tileentity.TileEntityBuilderGuide"})
    @Config.RequiresMcRestart
    public static String[] dynamicBoundsTileEntities;

    @Config.Comment("Register HardcodedCustomUniforms in Iris Shaders. May help with compatibility in certain shader packs")
    @Config.DefaultBoolean(false)
    @Config.RequiresMcRestart
    public static boolean enableHardcodedCustomUniforms;

    @Config.Comment("Modern MC_VERSION to try if shader pack has no 1.7.10 section. 0 = default (260101)")
    @Config.DefaultInt(0)
    @Config.RangeInt(min = 0)
    public static int modernFallbackMcVersion;

    @Config.Comment("Define IS_IRIS in shader macros.")
    @Config.DefaultBoolean(true)
    public static boolean defineIsIris;

    @Config.Comment("Cull tile entities in the Iris shadow pass.")
    @Config.DefaultBoolean(true)
    public static boolean cullShadowTileEntities;

    @Config.Comment("Max distance (blocks) a tile entity is re-drawn into the shadow pass when cullShadowTileEntities is on")
    @Config.DefaultInt(32)
    @Config.RangeInt(min = 8, max = 256)
    public static int shadowTileEntityMaxDistance;

    @Config.Comment({"Skip tile entities whose block already renders in the terrain mesh (getRenderType() != -1) from the shadow pass"})
    @Config.DefaultBoolean(true)
    public static boolean shadowSkipInMeshTileEntities;

    @Config.Comment({
        "How much a celestial body can move before the shadow map can update.",
        "",
        "Note this option is disabled when a shader pack utilizes voxelization features."
    })
    @Config.DefaultFloat(ShadowGraphGate.DEFAULT_ANGLE_DELTA_DEGREES)
    @Config.RangeFloat(min = 0f, max = 0.1f)
    public static float shadowGraphAngleDelta;

    @Config.Comment("Adjusts the rate the shadow map rebuilds when when a celestial body is near the horizon. Rate is sinusoidal")
    @Config.DefaultFloat(ShadowGraphGate.DEFAULT_HORIZON_SCALE)
    @Config.RangeFloat(min = 0f, max = 1.0f)
    public static float shadowGraphHorizonScale;

    @Config.Comment("ASM transformer exclusion narrowing for mod compatibility. Disable per-mod if narrowing causes class loading issues.")
    public static TransformerCompat transformerCompat = new TransformerCompat();

    public static class TransformerCompat {
        @Config.Comment("Narrow DragonAPI transformer exclusions to allow GL redirection")
        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public boolean narrowDragonAPI;

        @Config.Comment("Narrow Xaeros Minimap/Worldmap transformer exclusions to allow GL redirection")
        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public boolean narrowXaeros;

        @Config.Comment("Narrow AdvancedLightsabers transformer exclusions to allow GL redirection")
        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public boolean narrowAdvancedLightsabers;

        @Config.Comment("Narrow Alfheim transformer exclusions to allow GL redirection")
        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public boolean narrowAlfheim;

        @Config.Comment("Narrow Ears transformer exclusions to allow GL redirection")
        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public boolean narrowEars;

        @Config.Comment("Narrow Fisk's Superheroes transformer exclusions to allow GL redirection")
        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public boolean narrowFiskHeroes;

        @Config.Comment("Narrow FoamFix transformer exclusions to allow GL redirection in its repackaged Ears")
        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public boolean narrowFoamFix;

        @Config.Comment("Narrow Legends Mod transformer exclusions to allow GL redirection")
        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public boolean narrowLegendsMod;

        @Config.Comment("Narrow Power Converters transformer exclusions to allow GL redirection")
        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public boolean narrowPowerConverters;
    }

    @Config.Comment("Renders chunks before neighbors are ready. Improves loading at render distance edges, useful for low render distance servers.")
    @Config.DefaultBoolean(false)
    public static boolean useVanillaChunkTracking;

    @Config.Comment("Disables additional F3 information added by Angelica.")
    @Config.DefaultBoolean(false)
    public static boolean disableF3Additions;

    @Config.Comment("Shows developer counters (FFP, streaming, TESR, transfer) on the F3 screen. Toggle in game with F3+V.")
    @Config.DefaultBoolean(false)
    public static boolean verboseF3;

    @Config.Comment("Replaces various FFP uploads with statically allocated VBO's.")
    @Config.DefaultBoolean(true)
    public static boolean replaceFFPUploads;

    @Config.Comment("Pinned OpenGL version an integer (e.g. 46, 41, 33). 0 = auto-detect. [33, 46]. (Disable with disableGLVersionPinning=true)")
    @Config.DefaultInt(0)
    @Config.RangeInt(min = 0, max = 46)
    @Config.RequiresMcRestart
    public static int pinnedGLVersion;

    @Config.Comment("Disable automatic GL version pinning. When true, always probes from highest on every launch.")
    @Config.DefaultBoolean(false)
    @Config.RequiresMcRestart
    public static boolean disableGLVersionPinning;

    @Config.Comment("Requested OpenGL context version as an integer (e.g. 46, 41, 33). 0 = highest available. Falls back to probing when the version cannot be created.")
    @Config.DefaultInt(0)
    @Config.RangeInt(min = 0, max = 46)
    @Config.RequiresMcRestart
    public static int glVersion;

    @Config.Comment("Render backend: OPENGL, or SDL_GPU (experimental; Vulkan/Metal/Direct3D 12 through SDL3, falls back to OpenGL when no device can be created). -Dangelica.sdlgpu.enable=true|false overrides this.")
    @Config.DefaultEnum("OPENGL")
    @Config.RequiresMcRestart
    public static RenderBackendChoice renderBackend;

    @Config.Comment("SDL GPU driver: AUTO, VULKAN, METAL (macOS), D3D12 (Windows). Vulkan on macOS needs MoltenVK and uses Metal without it. -Dangelica.sdlgpu.driver overrides this.")
    @Config.DefaultEnum("AUTO")
    @Config.RequiresMcRestart
    public static SdlGpuDriver sdlGpuDriver;

    @Config.Comment("Disables GL Error checks. Always set to false in dev env or if LWJGL debug is on. Improves performance.")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean disableErrorChecks;

    @Config.Comment("Fixes various issues with entity overlays, such as z-fighting and eyes.")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean entityOverlayFixes;

    @Config.Comment("Swap the vanilla damage overlay with one similar to modern. Fixes specific issues with z-fighting.")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean entityModernDamageOverlay;

    public enum GLProfile { AUTO, CORE, ES }

    @Config.Comment("GL context profile: AUTO (probes Core first), CORE (desktop only), ES (GLES 3.2 only). Also settable via -Dangelica.glProfile=auto|core|es.")
    @Config.DefaultEnum("AUTO")
    @Config.RequiresMcRestart
    public static GLProfile glProfile;

    @Config.Comment("Enable random top-face texture orientation for configured blocks")
    @Config.DefaultBoolean(true)
    public static boolean enableNaturalTextures = true;

    @Config.Comment("List of block registry names to apply random top-face texture rotation to")
    @Config.DefaultStringList({
        "minecraft:andesite", "minecraft:dirt", "minecraft:granite", "minecraft:grass", "minecraft:mycelium", "minecraft:sand", "minecraft:soul_sand", "etfuturum:calcite", "etfuturum:coarse_dirt", "etfuturum:concrete_powder", "etfuturum:grass_path", "BiomesOPlenty:ash", "BiomesOPlenty:driedDirt", "BiomesOPlenty:hardDirt", "BiomesOPlenty:hardSand", "BiomesOPlenty:mud", "BiomesOPlenty:newBopDirt", "Botania:dirtPath", "Botania:enchantedSoil", "Botania:livingrock", "Botania:prismarine", "Botania:shimmerrock", "Botany:loam", "Botany:loamNoWeed", "Botany:soil", "Botany:soilNoWeed", "chisel:moss", "chisel:moss_carpet", "ExtraUtilities:color_hellsand", "ExtraUtilities:cursedearthside", "GalaxySpace:acentauribbgrunt", "GalaxySpace:acentauribbsubgrunt", "GalaxySpace:barnardaCdirt", "GalaxySpace:barnardaEgrunt", "GalaxySpace:barnardaEsubgrunt", "GalaxySpace:barnardaFgrunt", "GalaxySpace:barnardaFsubgrunt", "GalaxySpace:callistoblocks", "GalaxySpace:ceresblocks", "GalaxySpace:deimosblocks", "GalaxySpace:europagrunt", "GalaxySpace:ganymedeblocks", "GalaxySpace:haumeablocks", "GalaxySpace:ioblocks", "GalaxySpace:makemakegrunt", "GalaxySpace:mercuryblocks", "GalaxySpace:mirandablocks", "GalaxySpace:oberonblocks", "GalaxySpace:phobosblocks", "GalaxySpace:proteusblocks", "GalaxySpace:tcetieblocks", "GalaxySpace:titanblocks", "GalaxySpace:tritonblocks", "GalaxySpace:vegabgrunt", "GalaxySpace:vegabsubgrunt", "GalaxySpace:venusblocks", "gregtech:gt.blockgranites", "IC2:blockBasalt", "MagicBees:magicbees.enchantedEarth", "RandomThings:fertilizedDirt", "ToxicEverglades:blockDarkWorldGround2", "VillageNames:concretePowder", "witchery:pitdirt"
    })
    public static String[] naturalTextureBlocks = new String[] {
        "minecraft:andesite", "minecraft:dirt", "minecraft:granite", "minecraft:grass", "minecraft:mycelium", "minecraft:sand", "minecraft:soul_sand", "etfuturum:calcite", "etfuturum:coarse_dirt", "etfuturum:concrete_powder", "etfuturum:grass_path", "BiomesOPlenty:ash", "BiomesOPlenty:driedDirt", "BiomesOPlenty:hardDirt", "BiomesOPlenty:hardSand", "BiomesOPlenty:mud", "BiomesOPlenty:newBopDirt", "Botania:dirtPath", "Botania:enchantedSoil", "Botania:livingrock", "Botania:prismarine", "Botania:shimmerrock", "Botany:loam", "Botany:loamNoWeed", "Botany:soil", "Botany:soilNoWeed", "chisel:moss", "chisel:moss_carpet", "ExtraUtilities:color_hellsand", "ExtraUtilities:cursedearthside", "GalaxySpace:acentauribbgrunt", "GalaxySpace:acentauribbsubgrunt", "GalaxySpace:barnardaCdirt", "GalaxySpace:barnardaEgrunt", "GalaxySpace:barnardaEsubgrunt", "GalaxySpace:barnardaFgrunt", "GalaxySpace:barnardaFsubgrunt", "GalaxySpace:callistoblocks", "GalaxySpace:ceresblocks", "GalaxySpace:deimosblocks", "GalaxySpace:europagrunt", "GalaxySpace:ganymedeblocks", "GalaxySpace:haumeablocks", "GalaxySpace:ioblocks", "GalaxySpace:makemakegrunt", "GalaxySpace:mercuryblocks", "GalaxySpace:mirandablocks", "GalaxySpace:oberonblocks", "GalaxySpace:phobosblocks", "GalaxySpace:proteusblocks", "GalaxySpace:tcetieblocks", "GalaxySpace:titanblocks", "GalaxySpace:tritonblocks", "GalaxySpace:vegabgrunt", "GalaxySpace:vegabsubgrunt", "GalaxySpace:venusblocks", "gregtech:gt.blockgranites", "IC2:blockBasalt", "MagicBees:magicbees.enchantedEarth", "RandomThings:fertilizedDirt", "ToxicEverglades:blockDarkWorldGround2", "VillageNames:concretePowder", "witchery:pitdirt"
    };

    @Config.Comment("Enable the Tracy profiler backend. Requires angelica-tracy.jar on the classpath. Overridden by -Dangelica.tracy")
    @Config.DefaultBoolean(false)
    @Config.RequiresMcRestart
    public static boolean enableTracy;

    @Config.Comment("Allow Tracy to accept connections from other machines, not just localhost")
    @Config.DefaultBoolean(false)
    @Config.RequiresMcRestart
    public static boolean tracyAllowRemote;

    @Config.Comment("Emit Tracy zones for many more call sites. More detail, more overhead. Overridden by -Dangelica.tracy.fineZones")
    @Config.DefaultBoolean(false)
    @Config.RequiresMcRestart
    public static boolean tracyFineZones;

    @Config.Comment("Max distinct Tracy zone/message call sites to preallocate. Overridden by -Dangelica.tracy.maxSrcLocs")
    @Config.DefaultInt(4096)
    @Config.RangeInt(min = 1024, max = 16384)
    @Config.RequiresMcRestart
    public static int tracyMaxSrcLocs;

    @Config.Comment("Default length in seconds for a Tracy capture started from the command or video settings. 0 = until stopped")
    @Config.DefaultInt(60)
    @Config.RangeInt(min = 0, max = 3600)
    public static int tracyCaptureSeconds;

    public static void applyGpuCullingMode() {
        GpuCulling.setMode(gpuCullingMode == null ? GpuCullingMode.CPU_ONLY : gpuCullingMode);
    }

    public static boolean cubeInstancingEnabled() {
        return enableEntityBatching && enableCubeInstancing;
    }

    public static boolean sdlGpuConfigured() {
        return renderBackend == RenderBackendChoice.SDL_GPU;
    }

    public static String sdlGpuDriverName() {
        return sdlGpuDriver == null ? "" : sdlGpuDriver.sdlName();
    }

    public static GLProfile getEffectiveGlProfile() {
        final String sys = SystemProperties.GL_PROFILE.trim().toUpperCase();
        if ("GLES".equals(sys)) return GLProfile.ES;
        if (sys.isEmpty()) return glProfile;
        try { return GLProfile.valueOf(sys); } catch (IllegalArgumentException e) { return glProfile; }
    }
}
