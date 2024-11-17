package me.jellysquid.mods.sodium.client;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderBackend;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

public class SodiumDebugScreenHandler {
    public static final SodiumDebugScreenHandler INSTANCE = new SodiumDebugScreenHandler();
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onRenderGameOverlayTextEvent(RenderGameOverlayEvent.Text event) {
        final Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings.showDebugInfo) {
            event.right.add(Math.min(event.right.size(), 2), "Off-Heap: +" + ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed() / 1024L / 1024L + "MB");

            event.right.add("");
            event.right.add("Sodium (Embeddium) Renderer");
            event.right.addAll(getChunkRendererDebugStrings());

        }
    }

    private static List<String> getChunkRendererDebugStrings() {
        ChunkRenderBackend<?> backend = SodiumWorldRenderer.getInstance().getChunkRenderer();

        List<String> strings = new ArrayList<>(5);
        strings.add("Chunk Renderer: " + backend.getRendererName());
        strings.add("Block Renderer: " + "Sodium");
        strings.addAll(backend.getDebugStrings());

        return strings;
    }

}
