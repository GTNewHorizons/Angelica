package com.gtnewhorizons.angelica.compat.debug;

import com.gtnewhorizons.angelica.api.debug.DebugContext;
import com.gtnewhorizons.angelica.api.debug.DebugEntryHandle;
import com.gtnewhorizons.angelica.api.debug.DebugGroupHandle;
import com.gtnewhorizons.angelica.api.debug.DebugScreenApi;
import com.gtnewhorizons.angelica.api.debug.DebugScreenDisplayer;
import com.gtnewhorizons.angelica.api.debug.DebugScreenEntry;
import com.gtnewhorizons.angelica.glsm.GLSMQueries;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.world.World;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import vazkii.botania.client.fx.ParticleRenderDispatcher;
import vazkii.botania.common.core.handler.ConfigHandler;
import vazkii.botania.common.core.handler.ManaNetworkHandler;
import vazkii.botania.common.lib.LibMisc;

public final class BotaniaDebugEntry implements DebugScreenEntry {

    static final String GROUP_ID = "botania:botania";
    static final String ENTRY_ID = "botania:debug";

    private static DebugEntryHandle handle;

    private final DebugGroupHandle group;
    private final String version = displayVersion(runtimeVersion());
    private int collectors;
    private int pools;
    private String glVersion;
    private String glRenderer;
    private String glslVersion;

    private BotaniaDebugEntry(DebugGroupHandle group) {
        this.group = group;
    }

    static void register() {
        handle = DebugCompat.register(GROUP_ID, ENTRY_ID, "Botania", 0x00AA00, entry -> new BotaniaDebugEntry(DebugScreenApi.group(GROUP_ID)));
    }

    public static boolean active() {
        return DebugCompat.active(handle);
    }

    @Override
    public void display(DebugScreenDisplayer displayer, DebugContext context) {
        if (!ConfigHandler.showF3Debug) return;
        if (context.isSampleDue()) {
            final World world = context.world();
            collectors = ManaNetworkHandler.instance.getAllCollectorsInWorld(world).size();
            pools = ManaNetworkHandler.instance.getAllPoolsInWorld(world).size();
        }
        writeFacts(displayer, group, ParticleRenderDispatcher.sparkleFxCount, ParticleRenderDispatcher.fakeSparkleFxCount, ParticleRenderDispatcher.wispFxCount, ParticleRenderDispatcher.depthIgnoringWispFxCount, ParticleRenderDispatcher.lightningCount, collectors, pools, version);
        if (!GuiScreen.isCtrlKeyDown() || !GuiScreen.isShiftKeyDown()) {
            writeContextHint(displayer, group, Minecraft.isRunningOnMac);
            return;
        }
        if (glVersion == null) {
            glVersion = GLSMQueries.glGetString(GL11.GL_VERSION);
            glRenderer = GLSMQueries.glGetString(GL11.GL_RENDERER);
            glslVersion = GLSMQueries.glGetString(GL20.GL_SHADING_LANGUAGE_VERSION);
        }
        final ContextCapabilities caps = GLStateManager.capabilities;
        writeContext(displayer, group, ConfigHandler.useShaders, ConfigHandler.glSecondaryTextureUnit, glVersion, glRenderer, glslVersion, GLStateManager.MAX_TEXTURE_UNITS, caps.GL_ARB_multitexture, caps.GL_ARB_texture_non_power_of_two, caps.OpenGL13);
    }

    private static String runtimeVersion() {
        try {
            return (String) LibMisc.class.getField("VERSION").get(null);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    static String displayVersion(String version) {
        return version == null || version.contains("GRADLE") ? "N/A" : version;
    }

    static void writeFacts(DebugScreenDisplayer displayer, DebugGroupHandle group, int sparkles, int fakeSparkles, int wisps, int depthIgnoringWisps, int lightning, int collectors, int pools, String version) {
        displayer.fact(group, "Sparkles").value(sparkles).text(" (").value(fakeSparkles).text(" fake)");
        displayer.fact(group, "Wisps").value(wisps).text(" (").value(depthIgnoringWisps).text(" depth-ignoring)");
        displayer.fact(group, "Lightning").value(lightning);
        displayer.fact(group, "Mana Network").value(collectors).text(" collectors, ").value(pools).text(" pools");
        displayer.fact(group, "Version").value(version);
    }

    static void writeContextHint(DebugScreenDisplayer displayer, DebugGroupHandle group, boolean mac) {
        displayer.line(group).text(mac ? "SHIFT+CMD for context" : "SHIFT+CTRL for context");
    }

    static void writeContext(DebugScreenDisplayer displayer, DebugGroupHandle group, boolean useShaders, int secondaryUnit, String glVersion, String glRenderer, String glslVersion, int maxTextureUnits, boolean multitexture, boolean nonPowerOfTwo, boolean openGl13) {
        displayer.line(group).text("Config Context");
        displayer.fact(group, "shaders.enabled").value(String.valueOf(useShaders));
        displayer.fact(group, "shaders.secondaryUnit").value(secondaryUnit);
        displayer.line(group).text("OpenGL Context");
        displayer.fact(group, "GL_VERSION").value(String.valueOf(glVersion));
        displayer.fact(group, "GL_RENDERER").value(String.valueOf(glRenderer));
        displayer.fact(group, "GL_SHADING_LANGUAGE_VERSION").value(String.valueOf(glslVersion));
        displayer.fact(group, "GL_MAX_TEXTURE_IMAGE_UNITS_ARB").value(maxTextureUnits);
        displayer.fact(group, "GL_ARB_multitexture").value(String.valueOf(multitexture));
        displayer.fact(group, "GL_ARB_texture_non_power_of_two").value(String.valueOf(nonPowerOfTwo));
        displayer.fact(group, "OpenGL13").value(String.valueOf(openGl13));
    }
}
