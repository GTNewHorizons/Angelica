package com.gtnewhorizons.angelica.proxy;

import com.google.common.base.Objects;
import com.gtnewhorizon.gtnhlib.client.model.loading.ModelRegistry;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.VAOManager;
import com.gtnewhorizons.angelica.commands.AngelicaCommand;
import com.gtnewhorizons.angelica.compat.ModStatus;
import com.gtnewhorizons.angelica.compat.bettercrashes.BetterCrashesCompat;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.config.CompatConfig;
import com.gtnewhorizons.angelica.debug.F3Direction;
import com.gtnewhorizons.angelica.debug.FrametimeGraph;
import com.gtnewhorizons.angelica.debug.TPSGraph;
import com.gtnewhorizons.angelica.dynamiclights.DynamicLights;
import com.gtnewhorizons.angelica.dynamiclights.config.EntityLightConfig;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.hudcaching.HUDCaching;
import com.gtnewhorizons.angelica.iris.IrisGLSMBridge;
import com.gtnewhorizons.angelica.loading.AngelicaClientTweaker;
import com.gtnewhorizons.angelica.mixins.interfaces.IGameSettingsExt;
import com.gtnewhorizons.angelica.render.CloudRenderer;
import com.gtnewhorizons.angelica.rendering.AngelicaBlockSafetyRegistry;
import com.gtnewhorizons.angelica.rendering.celeritas.CeleritasDebugScreenHandler;
import com.gtnewhorizons.angelica.rendering.celeritas.CeleritasSetup;
import com.gtnewhorizons.angelica.rendering.celeritas.threading.ChunkTaskRegistry;
import com.gtnewhorizons.angelica.rendering.celeritas.threading.DefaultChunkTaskProvider;
import com.gtnewhorizons.angelica.rendering.celeritas.threading.ThreadedChunkTaskProvider;
import com.gtnewhorizons.angelica.zoom.Zoom;
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
import jss.notfine.gui.GuiCustomMenu;
import jss.notfine.gui.NotFineGameOptionPages;
import me.flashyreese.mods.reeses_sodium_options.client.gui.ReeseSodiumVideoOptionsScreen;
import me.jellysquid.mods.sodium.client.gui.SodiumOptionsGUI;
import net.coderbot.iris.Iris;
import net.coderbot.iris.client.IrisDebugScreenHandler;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiVideoSettings;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.Direction;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.chunk.Chunk;
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

import java.lang.management.ManagementFactory;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import static com.gtnewhorizons.angelica.AngelicaMod.MOD_ID;

public class ClientProxy extends CommonProxy {

    private static final Logger LOGGER = LogManager.getLogger("Angelica");
    final Minecraft mc = Minecraft.getMinecraft();
    final FrametimeGraph frametimeGraph = new FrametimeGraph();
    final TPSGraph tpsGraph = new TPSGraph();

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);

        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(this);

        ModelRegistry.registerModid(MOD_ID);
    }

    @SubscribeEvent
    public void worldLoad(WorldEvent.Load event) {
        if (GLStateManager.isRunningSplash()) {
            GLStateManager.setRunningSplash(false);
            LOGGER.info("World loaded - Enabling GLSM Cache");
        }

        if (AngelicaConfig.enableCeleritas) {
            ChunkTaskRegistry.reset();
            ChunkTaskRegistry.registerProvider(DefaultChunkTaskProvider.INSTANCE);
            ChunkTaskRegistry.registerProvider(ThreadedChunkTaskProvider.INSTANCE);

            // Register all blocks. Because blockids are unique to a world, this must be done each load
            GameData.getBlockRegistry().typeSafeIterable().forEach(o -> {
                AngelicaBlockSafetyRegistry.canBlockRenderOffThread(o, true, true);
                AngelicaBlockSafetyRegistry.canBlockRenderOffThread(o, false, true);
            });
        }
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        if (AngelicaConfig.enableHudCaching) {
            HUDCaching.init();
        }
        if (AngelicaConfig.enableCeleritas) {
            CeleritasSetup.ensureInitialized();
            MinecraftForge.EVENT_BUS.register(CeleritasDebugScreenHandler.INSTANCE);
        } else {
            LOGGER.info("Celeritas is disabled, skipping initialization from init()");
        }
        if (AngelicaConfig.enableIris) {
            IrisGLSMBridge.register();
            MinecraftForge.EVENT_BUS.register(IrisDebugScreenHandler.INSTANCE);

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
        if (AngelicaConfig.enableDynamicLights) {
            EntityLightConfig.init(new java.io.File(mc.mcDataDir, "config"));
        }

        // Register debug commands in dev environment only
        if (!AngelicaClientTweaker.isObfEnv()) {
            ClientCommandHandler.instance.registerCommand(new AngelicaCommand());
        }
    }


    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);

        if (ModStatus.isLotrLoaded && AngelicaConfig.enableCeleritas && CompatConfig.fixLotr) {
            try {
                final Class<?> lotrRendering = Class.forName("lotr.common.coremod.LOTRReplacedMethods$BlockRendering");
                ReflectionHelper.setPrivateValue(lotrRendering, null, new ConcurrentHashMap<>(), "naturalBlockClassTable");
                ReflectionHelper.setPrivateValue(lotrRendering, null, new ConcurrentHashMap<>(), "naturalBlockTable");
                ReflectionHelper.setPrivateValue(lotrRendering, null, new ConcurrentHashMap<>(), "cachedNaturalBlocks");
            } catch (ClassNotFoundException e) {
                LOGGER.error("Could not replace LOTR handle render code with thread safe version");
            }
        }
    }

    float lastIntegratedTickTime;

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (FMLCommonHandler.instance().getSide().isClient() && event.phase == TickEvent.Phase.END) {
            final IntegratedServer srv = Minecraft.getMinecraft().getIntegratedServer();
            if (srv != null) {
                final long currentTickTime = srv.tickTimeArray[srv.getTickCounter() % 100];
                lastIntegratedTickTime = lastIntegratedTickTime * 0.8F + (float) currentTickTime / 1000000.0F * 0.2F;
            } else lastIntegratedTickTime = 0;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRenderOverlay(RenderGameOverlayEvent.Pre event) {
        if (!mc.gameSettings.showDebugInfo) return;
        if (AngelicaConfig.modernizeF3Screen && event.type == RenderGameOverlayEvent.ElementType.CROSSHAIRS) {
            F3Direction.renderWorldDirectionsEvent(mc, event);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        final Minecraft mc = Minecraft.getMinecraft();
        if (event.isCanceled() || !mc.gameSettings.showDebugInfo || event.left.isEmpty()) return;

        final NetHandlerPlayClient cl = mc.getNetHandler();
        if (cl != null) {
            final IntegratedServer srv = mc.getIntegratedServer();

            if (srv != null) {
                final String s = String.format("Integrated server @ %.0f ms ticks", lastIntegratedTickTime);
                event.left.add(Math.min(event.left.size(), 1), s);
            }
        }

        if (AngelicaConfig.showBlockDebugInfo && mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            if (!event.right.isEmpty() && !Objects.firstNonNull(event.right.get(event.right.size() - 1), "").isEmpty()) event.right.add("");
            final Block block = mc.theWorld.getBlock(mc.objectMouseOver.blockX, mc.objectMouseOver.blockY, mc.objectMouseOver.blockZ);
            final int meta = mc.theWorld.getBlockMetadata(mc.objectMouseOver.blockX, mc.objectMouseOver.blockY, mc.objectMouseOver.blockZ);
            event.right.add(Block.blockRegistry.getNameForObject(block));
            event.right.add("meta: " + meta);
        }

        if (DynamicLights.isEnabled()) {
            final var builder = new StringBuilder("Dynamic Light Sources: ");
            final DynamicLights dl = DynamicLights.get();
            builder.append(dl.getLightSourcesCount()).append(" (U: ").append(dl.getLastUpdateCount()).append(')');

            event.right.add(builder.toString());
        }

        if (AngelicaConfig.modernizeF3Screen) {
            boolean hasReplacedXYZ = false;
            for (int i = 0; i < event.left.size() - 3; i++) {
                /* These checks should not be inefficient as most of the time the first one will already fail */
                if (!hasReplacedXYZ && Objects.firstNonNull(event.left.get(i), "").startsWith("x:") && Objects.firstNonNull(event.left.get(i + 1), "")
                        .startsWith("y:") && Objects.firstNonNull(event.left.get(i + 2), "").startsWith("z:") && Objects.firstNonNull(event.left.get(i + 3), "")
                        .startsWith("f:")) {
                    hasReplacedXYZ = true;
                    final int heading = MathHelper.floor_double((double) (mc.thePlayer.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3;
                    final String heading_str = switch (heading) {
                        case 0 -> "Towards positive Z";
                        case 1 -> "Towards negative X";
                        case 2 -> "Towards negative Z";
                        case 3 -> "Towards positive X";
                        default -> throw new RuntimeException();
                    };
                    event.left.set(i, String.format("XYZ: %.3f / %.5f / %.3f", mc.thePlayer.posX, mc.thePlayer.boundingBox.minY, mc.thePlayer.posZ));
                    final int bX = MathHelper.floor_double(mc.thePlayer.posX);
                    final int bY = MathHelper.floor_double(mc.thePlayer.boundingBox.minY);
                    final int bZ = MathHelper.floor_double(mc.thePlayer.posZ);
                    event.left.set(i + 1, String.format("Block: %d %d %d [%d %d %d]", bX, bY, bZ, bX & 15, bY & 15, bZ & 15));
                    event.left.set(i + 2, String.format("Chunk: %d %d %d", bX >> 4, bY >> 4, bZ >> 4));
                    event.left.set(
                            i + 3, String.format(
                                    "Facing: %s (%s) (%.1f / %.1f)",
                                    Direction.directions[heading].toLowerCase(Locale.ROOT),
                                    heading_str,
                                    MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw),
                                    MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationPitch)));

                    final Chunk chunk = this.mc.theWorld.getChunkFromBlockCoords(bX, bZ);
                    event.left.set(i + 4, String.format(
                        "lc: %d b: %s bl: %d sl: %d rl: %d",
                        chunk.getTopFilledSegment() + 15,
                        chunk.getBiomeGenForWorldCoords(bX & 15, bZ & 15, mc.theWorld.getWorldChunkManager()).biomeName,
                        chunk.getSavedLightValue(EnumSkyBlock.Block, bX & 15, MathHelper.clamp_int(bY, 0, 255), bZ & 15),
                        chunk.getSavedLightValue(EnumSkyBlock.Sky, bX & 15, MathHelper.clamp_int(bY, 0, 255), bZ & 15),
                        chunk.getBlockLightValue(bX & 15, MathHelper.clamp_int(bY, 0, 255), bZ & 15, 0)));
                }
            }
            // Draw a frametime graph
            if (((IGameSettingsExt) mc.gameSettings).angelica$showFpsGraph()) {
                frametimeGraph.render();
                if (Minecraft.getMinecraft().isSingleplayer()) {
                    tpsGraph.render();
                }
            }
        }
    }

    private float gameStartTime = -1;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onGuiOpen(GuiOpenEvent event) {
        if (!event.isCanceled()) {
            if (event.gui instanceof GuiMainMenu && gameStartTime == -1) {
                gameStartTime = ManagementFactory.getRuntimeMXBean().getUptime() / 1000f;
                LOGGER.info("The game loaded in {} seconds.", gameStartTime);
            }

            // force reset zoom when a GUI is opened
            if (AngelicaConfig.enableZoom && event.gui != null) Zoom.resetZoom();
        }
    }

    @SubscribeEvent
    public void onGuiInit(GuiScreenEvent.InitGuiEvent.Pre event) {
        if (event.gui instanceof GuiVideoSettings eventGui) {
            event.setCanceled(true);
            if (AngelicaConfig.enableNotFineOptions || GuiScreen.isShiftKeyDown()) {
                Minecraft.getMinecraft().displayGuiScreen(new GuiCustomMenu(
                        eventGui.parentGuiScreen,
                        NotFineGameOptionPages.general(),
                        NotFineGameOptionPages.detail(),
                        NotFineGameOptionPages.atmosphere(),
                        NotFineGameOptionPages.particles(),
                        NotFineGameOptionPages.other()));
            } else if (!AngelicaConfig.enableReesesSodiumOptions || GuiScreen.isCtrlKeyDown()) {
                Minecraft.getMinecraft().displayGuiScreen(new SodiumOptionsGUI(eventGui.parentGuiScreen));
            } else {
                Minecraft.getMinecraft().displayGuiScreen(new ReeseSodiumVideoOptionsScreen(eventGui.parentGuiScreen));
            }
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
        if (event.phase == TickEvent.Phase.END && mc.theWorld != null) {
            CloudRenderer.getCloudRenderer().checkSettings();
        }
    }

    // This is a bit of a hack to prevent the FOV from being modified by other mods
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onFOVModifierUpdate(FOVUpdateEvent event) {
        if (!(boolean) Settings.DYNAMIC_FOV.option.getStore()) {
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
