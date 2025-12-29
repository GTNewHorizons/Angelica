package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizons.angelica.Tags;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

public class CeleritasDebugScreenHandler {
    public static final CeleritasDebugScreenHandler INSTANCE = new CeleritasDebugScreenHandler();

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onRenderGameOverlayTextEvent(RenderGameOverlayEvent.Text event) {
        final Minecraft mc = Minecraft.getMinecraft();
        if (!mc.gameSettings.showDebugInfo) {
            return;
        }

        final CeleritasWorldRenderer renderer = CeleritasWorldRenderer.getInstance();
        if (renderer == null) {
            return;
        }

        event.right.add("");
        event.right.add(EnumChatFormatting.GREEN + "Angelica " + Tags.VERSION + " [Celeritas Renderer]");
        event.right.addAll(renderer.getDebugStrings());
        event.right.add("");
    }
}
