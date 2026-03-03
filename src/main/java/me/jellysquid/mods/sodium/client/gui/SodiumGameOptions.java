package me.jellysquid.mods.sodium.client.gui;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import me.jellysquid.mods.sodium.client.gui.options.named.GraphicsQuality;
import me.jellysquid.mods.sodium.client.gui.options.named.MultiDrawMode;
import net.coderbot.iris.Iris;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.AsyncOcclusionMode;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class SodiumGameOptions {
    public final QualitySettings quality = new QualitySettings();
    public final AdvancedSettings advanced = new AdvancedSettings();
    public final PerformanceSettings performance = new PerformanceSettings();

    private Path configPath;

    public static class AdvancedSettings {
        public boolean useVertexArrayObjects = true;
        public boolean useChunkMultidraw = true;
        public MultiDrawMode multiDrawMode = MultiDrawMode.DIRECT;

        public boolean useParticleCulling = true;
        public boolean allowDirectMemoryAccess = true;
        public boolean ignoreDriverBlacklist = false;
        public boolean useMapBufferRange = false;
        public boolean enableDeferredBatching = true;
    }

    public static class PerformanceSettings {
        public int chunkBuilderThreads = 0;
        public boolean alwaysDeferChunkUpdates = true;
        public boolean useNoErrorGLContext = true;
        public AsyncOcclusionMode asyncOcclusionMode = AsyncOcclusionMode.EVERYTHING;
        public boolean useOcclusionCulling = true;

        public boolean animateOnlyVisibleTextures = true;
        public boolean useEntityCulling = true;
        public boolean useFogOcclusion = true;
        public boolean useBlockFaceCulling = true;
        public boolean useCompactVertexFormat = true;
        public boolean translucencySorting = true;
        public boolean useRenderPassOptimization = true;
        public int cpuRenderAheadLimit = 3;
    }

    public static class EntityRenderDistance {
        public static double entityRenderDistanceMultiplier = 1.0;

        public static double getRenderDistanceMult() {
            return entityRenderDistanceMultiplier;
        }
        public static void setRenderDistanceMult(double value) {
            entityRenderDistanceMultiplier = value;
        }
    }
    public static class QualitySettings {
        public GraphicsQuality grassQuality = GraphicsQuality.DEFAULT;
        public boolean useCeleritasSmoothLighting = true;
    }

    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .excludeFieldsWithModifiers(Modifier.PRIVATE)
            .create();

    public static SodiumGameOptions load(Path path) {
        SodiumGameOptions config;
        boolean resaveConfig = true;

        if (Files.exists(path)) {
            try (FileReader reader = new FileReader(path.toFile())) {
                config = GSON.fromJson(reader, SodiumGameOptions.class);
            } catch (IOException e) {
                throw new RuntimeException("Could not parse config", e);
            } catch (JsonSyntaxException e) {
                AngelicaMod.LOGGER.error("Could not parse config, will fallback to default settings", e);
                config = new SodiumGameOptions();
                resaveConfig = false;
            }
        } else {
            config = new SodiumGameOptions();
            // Lwjgl2 doesn't expose multidraw and actually does individual, so try indirect if available
            if (isLwjgl2() && !isMacOS()) {
                config.advanced.multiDrawMode = MultiDrawMode.INDIRECT;
            }
        }

        config.configPath = path;

        // Clamp render-ahead to 0 if GL 3.2 fences aren't available
        if (GLStateManager.capabilities == null || !GLStateManager.capabilities.OpenGL32) {
            config.performance.cpuRenderAheadLimit = 0;
        }

        // Downgrade INDIRECT if hardware doesn't support it
        if (config.advanced.multiDrawMode == MultiDrawMode.INDIRECT && (GLStateManager.capabilities == null || (!GLStateManager.capabilities.OpenGL43 && !GLStateManager.capabilities.GL_ARB_multi_draw_indirect))) {
            config.advanced.multiDrawMode = MultiDrawMode.DIRECT;
        }

        try {
            if(resaveConfig)
                config.writeChanges();
        } catch (IOException e) {
            throw new RuntimeException("Couldn't update config file", e);
        }

        return config;
    }

    private static boolean isLwjgl2() {
        return SodiumGameOptions.class.getClassLoader().getResource("org/lwjgl/Sys.class") != null;
    }

    private static boolean isMacOS() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    public void writeChanges() throws IOException {
        final Path dir = this.configPath.getParent();

        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        } else if (!Files.isDirectory(dir)) {
            throw new IOException("Not a directory: " + dir);
        }

        Files.write(this.configPath, GSON.toJson(this).getBytes(StandardCharsets.UTF_8));
        if(Iris.enabled) {
            try {
                if (Iris.getIrisConfig() != null) {
                    Iris.getIrisConfig().save();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
