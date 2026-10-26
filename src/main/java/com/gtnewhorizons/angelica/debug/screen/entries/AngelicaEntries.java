package com.gtnewhorizons.angelica.debug.screen.entries;

import com.gtnewhorizons.angelica.api.debug.DebugContext;
import com.gtnewhorizons.angelica.api.debug.DebugFact;
import com.gtnewhorizons.angelica.api.debug.DebugScreenDisplayer;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.debug.screen.DebugGroup;
import com.gtnewhorizons.angelica.dynamiclights.DynamicLights;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.FogState;
import com.gtnewhorizons.angelica.proxy.ClientProxy;
import org.joml.Vector3d;
import org.lwjgl.opengl.GL11;

public final class AngelicaEntries {

    private AngelicaEntries() {}

    public static void animations(DebugScreenDisplayer displayer, DebugContext context) {
        if (!AngelicaConfig.speedupAnimations) return;
        displayer.fact(DebugGroup.ANGELICA, "Animations").value(ClientProxy.animationsMode.toString());
    }

    public static void dynamicLights(DebugScreenDisplayer displayer, DebugContext context) {
        if (!DynamicLights.configEnabled || !DynamicLights.isEnabled()) return;
        final DynamicLights lights = DynamicLights.get();
        final DebugFact fact = displayer.fact(DebugGroup.ANGELICA, "Dynamic Lights");
        fact.value(lights.getLightSourcesCount()).text(" sources, ").value(lights.getLastUpdateCount()).text(" updated");
        if (DynamicLights.FrustumCullingEnabled) {
            fact.text(", ").value(lights.getChunkRebuildManager().getPendingCount()).text(" pending");
        }
    }

    public static void fog(DebugScreenDisplayer displayer, DebugContext context) {
        writeFog(displayer.fact(DebugGroup.ANGELICA, "Fog"));
    }

    public static void writeFog(DebugFact fact) {
        final FogState fog = GLStateManager.getFogState();
        final Vector3d color = fog.getFogColor();
        fact
            .value(GLStateManager.getFogMode().isEnabled() ? "ON" : "OFF")
            .text(' ')
            .value(fogModeName(fog.getFogMode()))
            .text(' ')
            .value(fog.getStart(), 0)
            .text('-')
            .value(fog.getEnd(), 0)
            .text(" d=")
            .value(fog.getDensity(), 2)
            .text(" (")
            .value(color.x, 2)
            .text(", ")
            .value(color.y, 2)
            .text(", ")
            .value(color.z, 2)
            .text(')');
    }

    private static String fogModeName(int mode) {
        return switch (mode) {
            case GL11.GL_LINEAR -> "LINEAR";
            case GL11.GL_EXP -> "EXP";
            case GL11.GL_EXP2 -> "EXP2";
            default -> "?";
        };
    }
}
