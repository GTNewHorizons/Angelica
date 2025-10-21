package com.gtnewhorizons.angelica.proxy;

import static com.gtnewhorizons.angelica.loading.AngelicaTweaker.LOGGER;

import com.google.common.base.Objects;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizons.angelica.compat.ModStatus;
import com.gtnewhorizons.angelica.compat.bettercrashes.BetterCrashesCompat;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.config.CompatConfig;
import com.gtnewhorizons.angelica.debug.F3Direction;
import com.gtnewhorizons.angelica.debug.FrametimeGraph;
import com.gtnewhorizons.angelica.debug.TPSGraph;
import com.gtnewhorizons.angelica.dynamiclights.DynamicLights;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.debug.OpenGLDebugging;
import com.gtnewhorizons.angelica.hudcaching.HUDCaching;
import com.gtnewhorizons.angelica.mixins.interfaces.IGameSettingsExt;
import com.gtnewhorizons.angelica.render.CloudRenderer;
import com.gtnewhorizons.angelica.rendering.AngelicaBlockSafetyRegistry;
import com.gtnewhorizons.angelica.zoom.Zoom;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.registry.GameData;
import cpw.mods.fml.relauncher.ReflectionHelper;
import java.lang.management.ManagementFactory;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import jss.notfine.core.Settings;
import me.jellysquid.mods.sodium.client.SodiumDebugScreenHandler;
import net.coderbot.iris.Iris;
import net.coderbot.iris.client.IrisDebugScreenHandler;
import net.coderbot.iris.vertices.IrisVertexFormats;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.Direction;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.FOVUpdateEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import org.lwjgl.input.Keyboard;

public class ClientProxy extends CommonProxy {

    final Minecraft mc = Minecraft.getMinecraft();
    final FrametimeGraph frametimeGraph = new FrametimeGraph();
    final TPSGraph tpsGraph = new TPSGraph();

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);

        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void worldLoad(WorldEvent.Load event) {
        if (GLStateManager.isRunningSplash()) {
            GLStateManager.setRunningSplash(false);
            LOGGER.info("World loaded - Enabling GLSM Cache");
        }

        if (AngelicaConfig.enableSodium) {
            // Register all blocks. Because blockids are unique to a world, this must be done each load
            GameData.getBlockRegistry().typeSafeIterable().forEach(o -> {
                AngelicaBlockSafetyRegistry.canBlockRenderOffThread(o, true, true);
                AngelicaBlockSafetyRegistry.canBlockRenderOffThread(o, false, true);
            });
        }
    }

    private static KeyBinding glsmKeyBinding;

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        if (AngelicaConfig.enableHudCaching) {
            FMLCommonHandler.instance().bus().register(HUDCaching.INSTANCE);
            MinecraftForge.EVENT_BUS.register(HUDCaching.INSTANCE);
        }
        if (AngelicaConfig.enableSodium) {
            MinecraftForge.EVENT_BUS.register(SodiumDebugScreenHandler.INSTANCE);
        }
        if (AngelicaConfig.enableIris) {
            MinecraftForge.EVENT_BUS.register(IrisDebugScreenHandler.INSTANCE);

            Iris.INSTANCE.fmlInitEvent();
            FMLCommonHandler.instance().bus().register(Iris.INSTANCE);
            MinecraftForge.EVENT_BUS.register(Iris.INSTANCE);

            VertexFormat.registerSetupBufferStateOverride((vertexFormat, l) -> {
                if (vertexFormat == DefaultVertexFormat.POSITION_COLOR_TEXTURE_LIGHT_NORMAL
                    || vertexFormat == DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP) {
                    IrisVertexFormats.TERRAIN.setupBufferState(l);
                    return true;
                }
                return false;
            });
            VertexFormat.registerClearBufferStateOverride(vertexFormat -> {
                if (vertexFormat == DefaultVertexFormat.POSITION_COLOR_TEXTURE_LIGHT_NORMAL
                    || vertexFormat == DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP) {
                    IrisVertexFormats.TERRAIN.clearBufferState();
                    return true;
                }
                return false;
            });
        }

        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(this);

        glsmKeyBinding = new KeyBinding("Print GLSM Debug", Keyboard.KEY_NONE, "Angelica");
        ClientRegistry.registerKeyBinding(glsmKeyBinding);

        if (ModStatus.isBetterCrashesLoaded) {
            BetterCrashesCompat.init();
        }
        if (AngelicaConfig.enableZoom) {
            Zoom.init();
        }
    }

    private boolean wasGLSMKeyPressed;

    @SubscribeEvent
    public void onKeypress(TickEvent.ClientTickEvent event) {
        final boolean isPressed = glsmKeyBinding.getKeyCode() != 0 && GameSettings.isKeyDown(glsmKeyBinding);
        if (isPressed && !wasGLSMKeyPressed) {
            OpenGLDebugging.checkGLSM();
        }
        wasGLSMKeyPressed = isPressed;
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);

        if (ModStatus.isLotrLoaded && AngelicaConfig.enableSodium && CompatConfig.fixLotr) {
            try {
                Class<?> lotrRendering = Class.forName("lotr.common.coremod.LOTRReplacedMethods$BlockRendering");
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
            IntegratedServer srv = Minecraft.getMinecraft().getIntegratedServer();
            if (srv != null) {
                long currentTickTime = srv.tickTimeArray[srv.getTickCounter() % 100];
                lastIntegratedTickTime = lastIntegratedTickTime * 0.8F + (float) currentTickTime / 1000000.0F * 0.2F;
            } else lastIntegratedTickTime = 0;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRenderOverlay(RenderGameOverlayEvent.Pre event) {
        if (event.isCanceled() || !mc.gameSettings.showDebugInfo) return;
        if (AngelicaConfig.modernizeF3Screen && event.type == RenderGameOverlayEvent.ElementType.CROSSHAIRS) {
            F3Direction.renderWorldDirectionsEvent(mc, event);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.isCanceled() || !mc.gameSettings.showDebugInfo || event.left.isEmpty()) return;

        NetHandlerPlayClient cl = mc.getNetHandler();
        if (cl != null) {
            IntegratedServer srv = mc.getIntegratedServer();

            if (srv != null) {
                String s = String.format("Integrated server @ %.0f ms ticks", lastIntegratedTickTime);
                event.left.add(Math.min(event.left.size(), 1), s);
            }
        }

        if (AngelicaConfig.showBlockDebugInfo && mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            if (!event.right.isEmpty() && !Objects.firstNonNull(event.right.get(event.right.size() - 1), "").isEmpty()) event.right.add("");
            Block block = mc.theWorld.getBlock(mc.objectMouseOver.blockX, mc.objectMouseOver.blockY, mc.objectMouseOver.blockZ);
            int meta = mc.theWorld.getBlockMetadata(mc.objectMouseOver.blockX, mc.objectMouseOver.blockY, mc.objectMouseOver.blockZ);
            event.right.add(Block.blockRegistry.getNameForObject(block));
            event.right.add("meta: " + meta);
        }

        if (DynamicLights.isEnabled()) {
            var builder = new StringBuilder("Dynamic Light Sources: ");
            DynamicLights dl = DynamicLights.get();
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
                    int heading = MathHelper.floor_double((double) (mc.thePlayer.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3;
                    String heading_str = switch (heading) {
                        case 0 -> "Towards positive Z";
                        case 1 -> "Towards negative X";
                        case 2 -> "Towards negative Z";
                        case 3 -> "Towards positive X";
                        default -> throw new RuntimeException();
                    };
                    event.left.set(i, String.format("XYZ: %.3f / %.5f / %.3f", mc.thePlayer.posX, mc.thePlayer.boundingBox.minY, mc.thePlayer.posZ));
                    int bX = MathHelper.floor_double(mc.thePlayer.posX);
                    int bY = MathHelper.floor_double(mc.thePlayer.boundingBox.minY);
                    int bZ = MathHelper.floor_double(mc.thePlayer.posZ);
                    event.left.set(i + 1, String.format("Block: %d %d %d [%d %d %d]", bX, bY, bZ, bX & 15, bY & 15, bZ & 15));
                    event.left.set(i + 2, String.format("Chunk: %d %d %d", bX >> 4, bY >> 4, bZ >> 4));
                    event.left.set(i + 3, String.format(
                        "Facing: %s (%s) (%.1f / %.1f)",
                        Direction.directions[heading].toLowerCase(Locale.ROOT),
                        heading_str,
                        MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw),
                        MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationPitch)));

                    Chunk chunk = this.mc.theWorld.getChunkFromBlockCoords(bX, bZ);
                    event.left.set(i + 4, String.format(
                        "lc: %d b: %s bl: %d sl: %d rl: %d",
                        chunk.getTopFilledSegment() + 15,
                        chunk.getBiomeGenForWorldCoords(bX & 15, bZ & 15, mc.theWorld.getWorldChunkManager()).biomeName,
                        chunk.getSavedLightValue(EnumSkyBlock.Block, bX & 15, MathHelper.clamp_int(bY, 0, 255), bZ & 15),
                        chunk.getSavedLightValue(EnumSkyBlock.Sky, bX & 15, MathHelper.clamp_int(bY, 0, 255), bZ & 15),
                        chunk.getBlockLightValue(bX & 15, MathHelper.clamp_int(bY, 0, 255), bZ & 15, 0)));
                }
            }
            event.setCanceled(true);
            // TODO don't cancel the event and render here,
            //  instead mixin into the vanilla code and add a background to it
            /* render ourselves for modern background */
            FontRenderer fontrenderer = mc.fontRenderer;
            int fontColor = 0xe0e0e0;
            int rectColor = 0x90505050;
            for (int x = 0; x < event.left.size(); x++) {
                String msg = event.left.get(x);
                if (msg == null) continue;
                int strX = 2;
                int strY = 2 + x * fontrenderer.FONT_HEIGHT;
                Gui.drawRect(1, strY - 1, strX + fontrenderer.getStringWidth(msg) + 1, strY + fontrenderer.FONT_HEIGHT - 1, rectColor);
                fontrenderer.drawString(msg, strX, strY, fontColor);
            }
            int width = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaledWidth();
            for (int x = 0; x < event.right.size(); x++) {
                String msg = event.right.get(x);
                if (msg == null) continue;
                int w = fontrenderer.getStringWidth(msg);
                int strX = width - w - 2;
                int strY = 2 + x * fontrenderer.FONT_HEIGHT;
                Gui.drawRect(strX - 1, strY - 1, strX + w + 1, strY + fontrenderer.FONT_HEIGHT - 1, rectColor);
                fontrenderer.drawString(msg, strX, strY, fontColor);
            }

            // Draw a frametime graph
            if (((IGameSettingsExt)mc.gameSettings).angelica$showFpsGraph()) {
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
            if (event.gui instanceof GuiMainMenu && gameStartTime == -1 ) {
                gameStartTime = ManagementFactory.getRuntimeMXBean().getUptime() / 1000f;
                LOGGER.info("The game loaded in {} seconds.", gameStartTime);
            }

            // force reset zoom when a GUI is opened
            if (AngelicaConfig.enableZoom && event.gui != null) Zoom.resetZoom();
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
