package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizons.angelica.Tags;
import com.gtnewhorizons.angelica.dynamiclights.DynamicLights;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.FogState;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.lwjgl.opengl.GL11;

public class CeleritasDebugScreenHandler {
    public static final CeleritasDebugScreenHandler INSTANCE = new CeleritasDebugScreenHandler();

    /** Toggle for fog debug display on F3 screen */
    public static boolean showFogDebug = false;

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

        // Fog debug on left side (when enabled)
        if (showFogDebug) {
            event.left.add(getFogDebugString());
        }

        if (DynamicLights.configEnabled && DynamicLights.isEnabled()) {
            event.left.add(getDynamicLightsDebugString());
        }

        event.right.add("");
        event.right.add(EnumChatFormatting.GREEN + "Angelica " + Tags.VERSION + " [Celeritas Renderer]");
        event.right.addAll(renderer.getDebugStrings());
        event.right.add("");
    }

    public static String getFogDebugString() {
        final boolean enabled = GLStateManager.getFogMode().isEnabled();
        final FogState fog = GLStateManager.getFogState();
        final String modeName = switch (fog.getFogMode()) {
            case GL11.GL_LINEAR -> "LINEAR";
            case GL11.GL_EXP -> "EXP";
            case GL11.GL_EXP2 -> "EXP2";
            default -> "?";
        };
        return String.format("Fog: %s %s %.0f-%.0f d=%.2f (%.2f, %.2f, %.2f)",
            enabled ? "ON" : "OFF",
            modeName,
            fog.getStart(),
            fog.getEnd(),
            fog.getDensity(),
            fog.getFogColor().x,
            fog.getFogColor().y,
            fog.getFogColor().z);
    }

    public static String getDynamicLightsDebugString() {
        final DynamicLights dl = DynamicLights.get();
        int sources = dl.getLightSourcesCount();
        int updated = dl.getLastUpdateCount();

        if (DynamicLights.FrustumCullingEnabled) {
            int pending = dl.getChunkRebuildManager().getPendingCount();
            return String.format("DynLights: %d src, %d upd, %d pending", sources, updated, pending);
        }
        return String.format("DynLights: %d sources, %d updated", sources, updated);
    }
}
