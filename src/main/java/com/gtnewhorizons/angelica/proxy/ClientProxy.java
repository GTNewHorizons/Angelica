package com.gtnewhorizons.angelica.proxy;

import com.google.common.base.Objects;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.hudcaching.HUDCaching;
import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import me.jellysquid.mods.sodium.client.SodiumDebugScreenHandler;
import net.coderbot.iris.Iris;
import net.coderbot.iris.client.IrisDebugScreenHandler;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.Direction;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;

import java.lang.management.ManagementFactory;
import java.util.Locale;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void init(FMLInitializationEvent event) {
        if(AngelicaConfig.enableHudCaching) {
            FMLCommonHandler.instance().bus().register(HUDCaching.INSTANCE);
            MinecraftForge.EVENT_BUS.register(HUDCaching.INSTANCE); // TODO remove debug stuff, unused registration}
        }
        if(AngelicaConfig.enableSodium) {
            MinecraftForge.EVENT_BUS.register(SodiumDebugScreenHandler.INSTANCE);
        }
        if(AngelicaConfig.enableIris) {
            MinecraftForge.EVENT_BUS.register(IrisDebugScreenHandler.INSTANCE);

            Iris.INSTANCE.registerKeybindings();
            FMLCommonHandler.instance().bus().register(Iris.INSTANCE);
            MinecraftForge.EVENT_BUS.register(Iris.INSTANCE);

        }
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        // Nothing to do here (yet)
    }

    float lastIntegratedTickTime;
    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if(FMLCommonHandler.instance().getSide().isClient() && event.phase == TickEvent.Phase.END) {
            IntegratedServer srv = Minecraft.getMinecraft().getIntegratedServer();
            if(srv != null) {
                long currentTickTime = srv.tickTimeArray[srv.getTickCounter() % 100];
                lastIntegratedTickTime = lastIntegratedTickTime * 0.8F + (float)currentTickTime / 1000000.0F * 0.2F;
            } else
                lastIntegratedTickTime = 0;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        Minecraft mc = Minecraft.getMinecraft();
        if(event.isCanceled() || !mc.gameSettings.showDebugInfo || event.left.size() < 1)
            return;
        NetHandlerPlayClient cl = mc.getNetHandler();
        if(cl != null) {
            IntegratedServer srv = mc.getIntegratedServer();

            if (srv != null) {
                String s = String.format("Integrated server @ %.0f ms ticks", lastIntegratedTickTime);
                event.left.add(1, s);
            }
        }
        if(AngelicaConfig.showBlockDebugInfo && mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            if(!event.right.isEmpty() && Objects.firstNonNull(event.right.get(event.right.size() - 1), "").length() > 0)
                event.right.add("");
            Block block = mc.theWorld.getBlock(mc.objectMouseOver.blockX, mc.objectMouseOver.blockY, mc.objectMouseOver.blockZ);
            int meta = mc.theWorld.getBlockMetadata(mc.objectMouseOver.blockX, mc.objectMouseOver.blockY, mc.objectMouseOver.blockZ);
            event.right.add(Block.blockRegistry.getNameForObject(block));
            event.right.add("meta: " + meta);
        }
        if(AngelicaConfig.modernizeF3Screen) {
            boolean hasReplacedXYZ = false;
            for(int i = 0; i < event.left.size() - 3; i++) {
                /* These checks should not be inefficient as most of the time the first one will already fail */
                if(!hasReplacedXYZ && Objects.firstNonNull(event.left.get(i), "").startsWith("x:")
                    && Objects.firstNonNull(event.left.get(i + 1), "").startsWith("y:")
                    && Objects.firstNonNull(event.left.get(i + 2), "").startsWith("z:")
                    && Objects.firstNonNull(event.left.get(i + 3), "").startsWith("f:")) {
                    hasReplacedXYZ = true;
                    int heading = MathHelper.floor_double((double)(mc.thePlayer.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3;
                    String heading_str = switch (heading) {
                        case 0 -> "Towards positive Z";
                        case 1 -> "Towards negative X";
                        case 2 -> "Towards negative Z";
                        case 3 -> "Towards positive X";
                        default -> throw new RuntimeException();
                    };
                    event.left.set(i, String.format("XYZ: %.3f / %.5f / %.3f", mc.thePlayer.posX, mc.thePlayer.boundingBox.minY, mc.thePlayer.posZ));
                    int blockX = MathHelper.floor_double(mc.thePlayer.posX);
                    int blockY = MathHelper.floor_double(mc.thePlayer.boundingBox.minY);
                    int blockZ = MathHelper.floor_double(mc.thePlayer.posZ);
                    event.left.set(i + 1, String.format("Block: %d %d %d [%d %d %d]", blockX, blockY, blockZ, blockX & 15, blockY & 15, blockZ & 15));
                    event.left.set(i + 2, String.format("Chunk: %d %d %d", blockX >> 4, blockY >> 4, blockZ >> 4));
                    event.left.set(i + 3, String.format("Facing: %s (%s) (%.1f / %.1f)", Direction.directions[heading].toLowerCase(Locale.ROOT), heading_str, MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw), MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationPitch)));
                }
            }
        }
    }

    private float gameStartTime = -1;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onGuiOpen(GuiOpenEvent event) {
        if(!event.isCanceled() && event.gui instanceof GuiMainMenu && gameStartTime == -1) {
            gameStartTime = ManagementFactory.getRuntimeMXBean().getUptime() / 1000f;
            AngelicaTweaker.LOGGER.info("The game loaded in " + gameStartTime + " seconds.");
        }
    }

    /* coerce NaN fog values back to 0 (https://bugs.mojang.com/browse/MC-10480) - from ArchaicFix */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onFogColor(EntityViewRenderEvent.FogColors event) {
        if(Float.isNaN(event.red))
            event.red = 0f;
        if(Float.isNaN(event.green))
            event.green = 0f;
        if(Float.isNaN(event.blue))
            event.blue = 0f;
    }

}
