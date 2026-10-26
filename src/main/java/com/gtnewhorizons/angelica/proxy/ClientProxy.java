package com.gtnewhorizons.angelica.proxy;

import com.gtnewhorizons.angelica.debug.DebugKeyHandler;
import com.gtnewhorizons.angelica.debug.screen.DebugScreen;
import com.gtnewhorizons.angelica.debug.screen.DebugScreenEntries;
import com.gtnewhorizons.angelica.debug.screen.DebugScreenOverlay;
import com.gtnewhorizons.angelica.rendering.culling.GpuCulling;
import static com.gtnewhorizons.angelica.AngelicaMod.MOD_ID;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiVideoSettings;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.FOVUpdateEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import biomesoplenty.api.content.BOPCBlocks;
import com.gtnewhorizon.gtnhlib.client.model.loading.ModelRegistry;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.VAOManager;
import com.gtnewhorizons.angelica.client.font.DarkModeUtils;
import com.gtnewhorizons.angelica.client.font.FontProviderBitmap;
import com.gtnewhorizons.angelica.commands.AngelicaCommand;
import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.common.BlockError;
import com.gtnewhorizons.angelica.common.BlockIsbrhTest;
import com.gtnewhorizons.angelica.compat.ModStatus;
import com.gtnewhorizons.angelica.compat.bettercrashes.BetterCrashesCompat;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.rendering.TileEntityRenderBoundsRegistry;
import com.gtnewhorizons.angelica.rendering.tesr.AngelicaTesrMeshCache;
import com.gtnewhorizons.angelica.rendering.tesr.EntityTextureAtlas;
import com.gtnewhorizons.angelica.rendering.tesr.TesrLightCache;
import com.gtnewhorizons.angelica.config.CompatConfig;
import com.gtnewhorizons.angelica.config.ConfigMigrator;
import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.debug.F3Direction;
import com.gtnewhorizons.angelica.debug.flyby.FlybyFallGuard;
import com.gtnewhorizons.angelica.debug.flyby.FlybyRunner;
import com.gtnewhorizons.angelica.debug.profiling.TracyCaptureNotifier;
import com.gtnewhorizons.angelica.debug.FrametimeGraph;
import com.gtnewhorizons.angelica.debug.TPSGraph;
import com.gtnewhorizons.angelica.dynamiclights.DynamicLights;
import com.gtnewhorizons.angelica.dynamiclights.config.EntityLightConfig;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.backend.BackendStartGuard;
import com.gtnewhorizons.angelica.glsm.backend.VSyncMode;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfoCache;
import com.gtnewhorizons.angelica.hudcaching.HUDCaching;
import com.gtnewhorizons.angelica.iris.IrisGLSMBridge;
import com.gtnewhorizons.angelica.iris.ShaderWarmup;
import com.gtnewhorizons.angelica.mixins.interfaces.IGameSettingsExt;
import com.gtnewhorizons.angelica.render.CloudRenderer;
import com.gtnewhorizons.angelica.render.EmissiveTextureAutoloader;
import com.gtnewhorizons.angelica.rendering.AngelicaBlockSafetyRegistry;
import com.gtnewhorizons.angelica.rendering.FpsReducer;
import com.gtnewhorizons.angelica.rendering.FramePacer;
import com.gtnewhorizons.angelica.rendering.IsbrhTestRenderer;
import com.gtnewhorizons.angelica.rendering.TextureSamplers;
import com.gtnewhorizons.angelica.rendering.celeritas.CeleritasSetup;
import com.gtnewhorizons.angelica.rendering.celeritas.CeleritasWorldRenderer;
import com.gtnewhorizons.angelica.rendering.celeritas.threading.ChunkTaskRegistry;
import com.gtnewhorizons.angelica.rendering.celeritas.threading.DefaultChunkTaskProvider;
import com.gtnewhorizons.angelica.rendering.celeritas.threading.ThreadedChunkTaskProvider;
import com.gtnewhorizons.angelica.rendering.items.BlockRenderListManager;
import com.gtnewhorizons.angelica.rendering.items.ItemRenderListManager;
import com.gtnewhorizons.angelica.rendering.tesr.TesrLifecycle;
import com.gtnewhorizons.angelica.utils.AngelicaJar;
import com.gtnewhorizons.angelica.utils.AnimationMode;
import com.gtnewhorizons.angelica.utils.ManagedEnum;
import com.gtnewhorizons.angelica.zoom.Zoom;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.registry.GameData;
import cpw.mods.fml.relauncher.ReflectionHelper;
import jss.notfine.core.Settings;
import jss.notfine.gui.options.named.FOVMode;
import me.flashyreese.mods.reeses_sodium_options.client.gui.ReeseSodiumVideoOptionsScreen;
import me.jellysquid.mods.sodium.client.gui.FrameRateOptions;
import me.jellysquid.mods.sodium.client.gui.SodiumGameOptions;
import net.coderbot.iris.Iris;

public final class ClientProxy extends CommonProxy {

    public static BlockError blockError;
    public static final ManagedEnum<AnimationMode> animationsMode = new ManagedEnum<>(AnimationMode.VISIBLE_ONLY);
    private static final Logger LOGGER = LogManager.getLogger("Angelica");
    private static SodiumGameOptions CONFIG;
    private final Minecraft mc = Minecraft.getMinecraft();
    private final FrametimeGraph frametimeGraph = new FrametimeGraph();
    private final TPSGraph tpsGraph = new TPSGraph();
    public static Block bopGrass;

    public static SodiumGameOptions options() {
        if (CONFIG == null) {
            CONFIG = SodiumGameOptions.load(ConfigMigrator.handleConfigMigration("angelica-options.json"));
        }
        return CONFIG;
    }

    public static void toggleVSync(boolean enabled) {
        final SodiumGameOptions opts = options();
        opts.advanced.vsyncMode = enabled ? GLStateManager.preferredTearFreeMode() : VSyncMode.OFF;
        try {
            opts.writeChanges();
        } catch (IOException e) {
            AngelicaMod.LOGGER.warn("Could not persist the vsync mode", e);
        }
        GLStateManager.setVSyncMode(opts.advanced.vsyncMode);
    }

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        ModStatus.preInit();
        for (String name : AngelicaConfig.dynamicBoundsTileEntities) TileEntityRenderBoundsRegistry.registerDynamicClass(name);
        super.preInit(event);
        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new EmissiveTextureAutoloader());
        MinecraftForge.EVENT_BUS.register(new AngelicaTesrMeshCache.ReloadListener());
        ModelRegistry.registerModid(MOD_ID);
        blockError = new BlockError();
        if (AngelicaConfig.enableTestBlocks) {
            BlockIsbrhTest.renderId = RenderingRegistry.getNextAvailableRenderId();
            RenderingRegistry.registerBlockHandler(BlockIsbrhTest.renderId, new IsbrhTestRenderer());
        }
        AngelicaJar.configureShaderDiskCache(event.getSourceFile());
        if (AngelicaConfig.enableIris) {
            IrisGLSMBridge.installImmediateExtendedHandler();
            Iris.warmupShaderTransforms();
        }
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        if (AngelicaConfig.enableHudCaching) {
            HUDCaching.init();
        }
        CeleritasSetup.ensureInitialized();
        TextureSamplers.initRenderer();
        DarkModeUtils.init();
        final SodiumGameOptions opts = options();
        if (opts.advanced.vsyncMode == null) {
            opts.advanced.vsyncMode = FrameRateOptions.defaultMode();
        }
        Minecraft.getMinecraft().gameSettings.enableVsync = opts.advanced.vsyncMode.tearFree();
        GLStateManager.setVSyncPreference(opts.advanced.vsyncMode);
        FramePacer.setIdleWork(deadlineNanos -> {
            final CeleritasWorldRenderer renderer = CeleritasWorldRenderer.getInstanceOrNull();
            if (renderer != null) renderer.runFrameIdleWork(deadlineNanos);
        });
        if (AngelicaConfig.enableIris) {
            IrisGLSMBridge.register();

            Iris.INSTANCE.fmlInitEvent();
            FMLCommonHandler.instance().bus().register(Iris.INSTANCE);
            MinecraftForge.EVENT_BUS.register(Iris.INSTANCE);
        }
        if (!AngelicaConfig.enableVAO) {
            VAOManager.disableVao();
        }

        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(this);

        if (ModStatus.isBetterCrashesLoaded) {
            BetterCrashesCompat.init();
        }
        if (AngelicaConfig.enableZoom) {
            Zoom.init();
        }
        FpsReducer.init();
        FMLCommonHandler.instance().bus().register(new DebugKeyHandler());
        AngelicaConfig.applyGpuCullingMode();
        if (AngelicaConfig.enableDynamicLights) {
            EntityLightConfig.init(new java.io.File(mc.mcDataDir, "config"));
        }
        if(AngelicaConfig.optimizeInWorldItemRendering){
            ItemRenderListManager.registerReloadListener();
            BlockRenderListManager.registerReloadListener();
        }
        ((IReloadableResourceManager) mc.getResourceManager()).registerReloadListener(manager -> EntityTextureAtlas.INSTANCE.clear());
        TextureInfoCache.INSTANCE.addDeleteListener(EntityTextureAtlas.INSTANCE::onTextureDeleted);
        FontProviderBitmap.registerReloadListener();

        // Debug tooling
        if (SystemProperties.debugTooling() || Tracy.ENABLED) {
            ClientCommandHandler.instance.registerCommand(new AngelicaCommand());
        }
        if (SystemProperties.debugTooling()) {
            FMLCommonHandler.instance().bus().register(FlybyRunner.INSTANCE);
            MinecraftForge.EVENT_BUS.register(FlybyFallGuard.INSTANCE);
            FlybyRunner.INSTANCE.startFromProperties();
        }
        if (Tracy.ENABLED) {
            TracyCaptureNotifier.INSTANCE.register();
        }
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);

        if (ModStatus.isLotrLoaded && CompatConfig.fixLotr) {
            try {
                final Class<?> lotrRendering = Class.forName("lotr.common.coremod.LOTRReplacedMethods$BlockRendering");
                ReflectionHelper.setPrivateValue(lotrRendering, null, new ConcurrentHashMap<>(), "naturalBlockClassTable");
                ReflectionHelper.setPrivateValue(lotrRendering, null, new ConcurrentHashMap<>(), "naturalBlockTable");
                ReflectionHelper.setPrivateValue(lotrRendering, null, new ConcurrentHashMap<>(), "cachedNaturalBlocks");
            } catch (ClassNotFoundException e) {
                LOGGER.error("Could not replace LOTR handle render code with thread safe version");
            }
        }
        if(ModStatus.isBOPLoaded) {
            bopGrass = BOPCBlocks.newBopGrass;
        }
    }

    private long worldSection;

    @SubscribeEvent
    public void worldLoad(WorldEvent.Load event) {
        if (!event.world.isRemote) return;
        if (Tracy.ENABLED) {
            Tracy.sectionLeave(this.worldSection);
            this.worldSection = Tracy.sectionEnter(Tracy.SECTION_WORLD, "world " + event.world.provider.getDimensionName() + " (dim " + event.world.provider.dimensionId + ")");
        }
        if (GLStateManager.isRunningSplash()) {
            GLStateManager.setRunningSplash(false);
            LOGGER.info("World loaded - Enabling GLSM Cache");
        }

        ChunkTaskRegistry.reset();
        ChunkTaskRegistry.registerProvider(DefaultChunkTaskProvider.INSTANCE);
        ChunkTaskRegistry.registerProvider(ThreadedChunkTaskProvider.INSTANCE);

        // Register all blocks. Because blockids are unique to a world, this must be done each load
        GameData.getBlockRegistry().typeSafeIterable().forEach(o -> {
            AngelicaBlockSafetyRegistry.canBlockRenderOffThread(o);
        });
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (!event.world.isRemote) return;
        Tracy.sectionLeave(this.worldSection);
        this.worldSection = 0L;
        DynamicLights.get().removeAllLightSources();
        TesrLifecycle.reset();
        GpuCulling.onWorldUnload();
    }

    private static float lastIntegratedTickTime;

    public static float integratedTickTime() {
        return lastIntegratedTickTime;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            final IntegratedServer srv = Minecraft.getMinecraft().getIntegratedServer();
            if (srv != null) {
                final long currentTickTime = srv.tickTimeArray[srv.getTickCounter() % 100];
                lastIntegratedTickTime = lastIntegratedTickTime * 0.8F + (float) currentTickTime / 1000000.0F * 0.2F;
            } else lastIntegratedTickTime = 0;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRenderOverlay(RenderGameOverlayEvent.Pre event) {
        if (AngelicaConfig.modernizeF3Screen && event.type == RenderGameOverlayEvent.ElementType.CROSSHAIRS && DebugScreenEntries.THREE_DIMENSIONAL_CROSSHAIR.isEnabled()) {
            F3Direction.renderWorldDirectionsEvent(mc, event);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        if (event.isCanceled() || !DebugScreen.isOverlayVisible() || !DebugScreenOverlay.INSTANCE.debugBlockRan()) return;
        if (!DebugScreen.isGroupedActive()) DebugScreenOverlay.INSTANCE.addFlatLines(mc, event.left, event.right);
        if (AngelicaConfig.modernizeF3Screen && ((IGameSettingsExt) mc.gameSettings).angelica$showFpsGraph()) {
            final int tick = DebugScreen.clientTick();
            frametimeGraph.render(event.resolution, tick);
            if (mc.isSingleplayer()) {
                tpsGraph.render(event.resolution, tick);
            }
        }
    }

    private float gameStartTime = -1;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onGuiOpen(GuiOpenEvent event) {
        if (BackendManager.RENDER_BACKEND.isSDLGPU()) BackendStartGuard.latch();
        if (!event.isCanceled()) {
            if (event.gui instanceof GuiMainMenu && gameStartTime == -1) {
                gameStartTime = ManagementFactory.getRuntimeMXBean().getUptime() / 1000f;
                LOGGER.info("The game loaded in {} seconds.", gameStartTime);
                if (AngelicaConfig.enableIris) ShaderWarmup.start();
            }

            // force reset zoom when a GUI is opened
            if (AngelicaConfig.enableZoom && event.gui != null) Zoom.resetZoom();
        }
    }

    @SubscribeEvent
    public void onGuiInit(GuiScreenEvent.InitGuiEvent.Pre event) {
        if (event.gui instanceof GuiVideoSettings eventGui) {
            event.setCanceled(true);
            Minecraft.getMinecraft().displayGuiScreen(new ReeseSodiumVideoOptionsScreen(eventGui.parentGuiScreen));
        }
    }

    /* coerce NaN fog values back to 0 (https://bugs.mojang.com/browse/MC-10480) - from ArchaicFix */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onFogColor(EntityViewRenderEvent.FogColors event) {
        if (Float.isNaN(event.red)) event.red = 0f;
        if (Float.isNaN(event.green)) event.green = 0f;
        if (Float.isNaN(event.blue)) event.blue = 0f;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            DebugScreen.onClientTickStart();
            return;
        }
        DebugScreen.onClientTickEnd();
        TesrLightCache.onClientTick();
        if (mc.theWorld != null) {
            CloudRenderer.getCloudRenderer().checkSettings();
        }
    }

    // This only disables FOV changes from vanilla, leaving mod FOV changes untouched
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onFOVModifierUpdateEarly(FOVUpdateEvent event) {
        if(Settings.DYNAMIC_FOV.option.getStore() == FOVMode.MODS) {
            event.fov = 1.0F;
            event.newfov = 1.0F;
        }
    }

    // This removes all FOV dynamics, both from vanilla (speed/sprinting), and mods
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onFOVModifierUpdateLate(FOVUpdateEvent event) {
        if(Settings.DYNAMIC_FOV.option.getStore() == FOVMode.NONE) {
            event.newfov = 1.0F;
        }
    }

    @Override
    public void putFrametime(long time) {
        frametimeGraph.putSample(time);
    }

    @Override
    public void putTicktime(long time) {
        tpsGraph.putSample(time);
    }
}
