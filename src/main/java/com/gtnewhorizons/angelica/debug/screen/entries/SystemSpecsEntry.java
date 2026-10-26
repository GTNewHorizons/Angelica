package com.gtnewhorizons.angelica.debug.screen.entries;

import com.gtnewhorizons.angelica.api.debug.DebugContext;
import com.gtnewhorizons.angelica.api.debug.DebugScreenDisplayer;
import com.gtnewhorizons.angelica.api.debug.DebugScreenEntry;
import com.gtnewhorizons.angelica.debug.screen.DebugGroup;
import com.gtnewhorizons.angelica.glsm.GLSMQueries;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

public final class SystemSpecsEntry implements DebugScreenEntry {

    private static final String UNKNOWN = "Unknown";

    private final Minecraft mc = Minecraft.getMinecraft();
    private boolean resolved;
    private String java;
    private int cores;
    private String gpu;
    private String openGl;

    @Override
    public void display(DebugScreenDisplayer displayer, DebugContext context) {
        if (!resolved) resolve();
        displayer.fact(DebugGroup.SYSTEM_SPECS, "Java").value(java);
        displayer.fact(DebugGroup.SYSTEM_SPECS, "CPU").value(cores).text('x');
        displayer.fact(DebugGroup.SYSTEM_SPECS, "Display").value(mc.displayWidth).text('x').value(mc.displayHeight);
        displayer.fact(DebugGroup.SYSTEM_SPECS, "GPU").value(gpu);
        displayer.fact(DebugGroup.SYSTEM_SPECS, "OpenGL").value(openGl);
    }

    private void resolve() {
        resolved = true;
        java = System.getProperty("java.version", UNKNOWN) + " (" + System.getProperty("java.vendor", UNKNOWN) + ")";
        cores = Runtime.getRuntime().availableProcessors();
        gpu = orUnknown(GLSMQueries.glGetString(GL11.GL_RENDERER));
        openGl = orUnknown(GLSMQueries.glGetString(GL11.GL_VERSION));
    }

    private static String orUnknown(String value) {
        return value == null ? UNKNOWN : value;
    }
}
