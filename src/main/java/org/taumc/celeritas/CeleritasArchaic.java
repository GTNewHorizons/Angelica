package org.taumc.celeritas;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.embeddium.impl.common.util.MathUtil;
import org.embeddedt.embeddium.impl.common.util.NativeBuffer;
import org.embeddedt.embeddium.impl.gl.device.GLRenderDevice;
import org.lwjgl.opengl.GL15C;
import org.taumc.celeritas.command.TogglePassCommand;
import org.taumc.celeritas.impl.render.terrain.CeleritasWorldRenderer;

import java.lang.management.ManagementFactory;

@Mod(modid = CeleritasArchaic.MODID, useMetadata = true)
public class CeleritasArchaic {
    public static final String MODID = "celeritas";
    private static final Logger LOGGER = LogManager.getLogger("Celeritas");
    public static String VERSION;

    @Mod.EventHandler
    public void onInit(FMLInitializationEvent event) {
        LOGGER.info("Hello from Forge!");
        GLRenderDevice.VANILLA_STATE_RESETTER = () -> {
            GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, 0);
        };
        VERSION = Loader.instance().getIndexedModList().get(MODID).getVersion();
        MinecraftForge.EVENT_BUS.register(this);

        if ((Boolean)Launch.blackboard.get("fml.deobfuscatedEnvironment")) {
            ClientCommandHandler.instance.registerCommand(new TogglePassCommand());
        }
    }

    @SubscribeEvent
    public void onF3Text(RenderGameOverlayEvent.Text event) {
        if (!Minecraft.getMinecraft().gameSettings.showDebugInfo) {
            return;
        }

        var strings = event.right;
        strings.add("");
        strings.add("%s%s Renderer (%s)".formatted(EnumChatFormatting.GREEN, "Celeritas", VERSION));

        var renderer = CeleritasWorldRenderer.instanceNullable();

        if (renderer != null) {
            strings.addAll(renderer.getDebugStrings());
        }

        for (int i = 0; i < strings.size(); i++) {
            String str = strings.get(i);

            if (str != null && str.startsWith("Allocated:")) {
                strings.add(i + 1, getNativeMemoryString());

                break;
            }
        }
    }

    private static String getNativeMemoryString() {
        return "Off-Heap: +" + MathUtil.toMib(getNativeMemoryUsage()) + "MB";
    }

    private static long getNativeMemoryUsage() {
        return ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed() + NativeBuffer.getTotalAllocated();
    }

    public static Logger logger() {
        return LOGGER;
    }
}
