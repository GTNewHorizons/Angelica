package com.gtnewhorizons.angelica.debug.flyby;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.proxy.ClientProxy;
import me.jellysquid.mods.sodium.client.gui.SodiumGameOptions;
import net.coderbot.iris.Iris;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import org.lwjgl.opengl.GL11;

import java.lang.management.ManagementFactory;
import java.lang.management.GarbageCollectorMXBean;
import java.util.ArrayList;
import java.util.List;

/**
 * List all settings that could affect a Tracy profile.
 */
final class FlybySettings {

    private FlybySettings() {}

    static String describe() {
        final Minecraft mc = Minecraft.getMinecraft();
        final GameSettings gs = mc.gameSettings;
        final SodiumGameOptions options = ClientProxy.options();
        final List<String> parts = new ArrayList<>(40);

        parts.add("backend=" + backendName());
        parts.add("gpu=" + glString(GL11.GL_RENDERER));
        parts.add("driver=" + glString(GL11.GL_VERSION));
        parts.add("cpus=" + Runtime.getRuntime().availableProcessors());
        parts.add("window=" + mc.displayWidth + "x" + mc.displayHeight);
        parts.add("renderDistance=" + gs.renderDistanceChunks);
        parts.add("guiScale=" + gs.guiScale);
        parts.add("fov=" + gs.fovSetting);
        parts.add("fancyGraphics=" + gs.fancyGraphics);
        parts.add("ambientOcclusion=" + gs.ambientOcclusion);
        parts.add("mipmapLevels=" + gs.mipmapLevels);
        parts.add("particles=" + gs.particleSetting);
        parts.add("anaglyph=" + gs.anaglyph);
        parts.add("fbo=" + gs.fboEnable);

        parts.add("shaders=" + shaderPackName());

        parts.add("chunkBuilderThreads=" + options.performance.chunkBuilderThreads);
        parts.add("asyncOcclusion=" + options.performance.asyncOcclusionMode);
        parts.add("occlusionCulling=" + options.performance.useOcclusionCulling);
        parts.add("entityCulling=" + options.performance.useEntityCulling);
        parts.add("tesrCulling=" + options.performance.sectionGatedTesrCulling);
        parts.add("fogOcclusion=" + options.performance.useFogOcclusion);
        parts.add("translucencySorting=" + options.performance.translucencySorting);
        parts.add("deferChunkUpdates=" + options.performance.alwaysDeferChunkUpdates);
        parts.add("compactVertexFormat=" + options.performance.useCompactVertexFormat);
        parts.add("renderPassOptimization=" + options.performance.useRenderPassOptimization);
        parts.add("renderAhead=" + options.performance.cpuRenderAheadLimit);
        parts.add("multidraw=" + options.advanced.useChunkMultidraw + "/" + options.advanced.multiDrawMode);
        parts.add("uploadStrategy=" + options.advanced.streamingUploadStrategy);
        parts.add("deferredBatching=" + options.advanced.enableDeferredBatching);
        parts.add("vao=" + options.advanced.useVertexArrayObjects);
        parts.add("grassQuality=" + options.quality.grassQuality);
        parts.add("smoothLighting=" + options.quality.useCeleritasSmoothLighting);
        parts.add("textureFilter=" + SodiumGameOptions.effectiveTextureFilterMode());

        parts.add("java=" + System.getProperty("java.version"));
        parts.add("maxHeap=" + (Runtime.getRuntime().maxMemory() >> 20) + "M");
        parts.add("gc=" + garbageCollectors());

        return String.join(" ", parts);
    }

    static String tag(String description) {
        return String.format("%08x", description.hashCode());
    }

    private static String glString(int pname) {
        try {
            final String value = BackendManager.RENDER_BACKEND.getString(pname);
            return value == null ? "unknown" : value.replace(' ', '-');
        } catch (RuntimeException | LinkageError e) {
            return "unknown";
        }
    }

    private static String backendName() {
        try {
            return BackendManager.RENDER_BACKEND.getName();
        } catch (RuntimeException | LinkageError e) {
            return "unknown";
        }
    }

    private static String shaderPackName() {
        if (!AngelicaConfig.enableIris) return "iris-disabled";
        try {
            if (Iris.getIrisConfig() == null || !Iris.getIrisConfig().areShadersEnabled()) return "off";
            final String name = Iris.getCurrentPackName();
            return name == null ? "none" : name;
        } catch (RuntimeException | LinkageError e) {
            return "unknown";
        }
    }

    private static String garbageCollectors() {
        final List<String> names = new ArrayList<>(4);
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            names.add(bean.getName().replace(' ', '-'));
        }
        return names.isEmpty() ? "unknown" : String.join("+", names);
    }
}
