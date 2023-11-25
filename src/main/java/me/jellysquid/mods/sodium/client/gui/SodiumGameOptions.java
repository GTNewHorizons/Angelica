package me.jellysquid.mods.sodium.client.gui;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import me.jellysquid.mods.sodium.client.SodiumClientMod;


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
    public final NotificationSettings notifications = new NotificationSettings();

    private Path configPath;

    public static class AdvancedSettings {
        public boolean useVertexArrayObjects = true;
        public boolean useChunkMultidraw = true;

        public boolean animateOnlyVisibleTextures = true;
        public boolean useEntityCulling = true;
        public boolean useParticleCulling = true;
        public boolean useFogOcclusion = true;
        public boolean useCompactVertexFormat = true;
        public boolean useBlockFaceCulling = true;
        public boolean allowDirectMemoryAccess = false;
        public boolean ignoreDriverBlacklist = false;
        public boolean translucencySorting = false;
    }

    public static class PerformanceSettings {
        public int chunkBuilderThreads = 0;
        public boolean alwaysDeferChunkUpdates = false;
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
        public GraphicsQuality cloudQuality = GraphicsQuality.DEFAULT;
        public GraphicsQuality weatherQuality = GraphicsQuality.DEFAULT;
        public GraphicsQuality leavesQuality = GraphicsQuality.DEFAULT;

        public boolean enableVignette = true;
    }

    public static class NotificationSettings {
        public boolean hideDonationButton = false;
    }

    public enum ParticleMode {
        ALL("options.graphics.fancy"),
        DECREASED("options.graphics.fast"),
        MINIMAL("options.particles.minimal");

        private static final ParticleMode[] VALUES = values();

        private final String name;

        ParticleMode(String name) {
            this.name = name;
        }

        public static ParticleMode fromOrdinal(int ordinal) {
            return VALUES[ordinal];
        }
    }

    public enum GraphicsMode {
        FANCY("options.graphics.fancy"),
        FAST("options.graphics.fast");

        private final String name;

        GraphicsMode(String name) {
            this.name = name;
        }

        public boolean isFancy() {
            return this == FANCY;
        }

        public static GraphicsMode fromBoolean(boolean isFancy) {
            return isFancy ? FANCY : FAST;
        }
    }

    public enum GraphicsQuality {
        DEFAULT("generator.default"),
        FANCY("options.clouds.fancy"),
        FAST("options.clouds.fast");

        private final String name;

        GraphicsQuality(String name) {
            this.name = name;
        }
    }

    public enum LightingQuality {
        OFF("options.ao.off"),
        LOW("options.ao.min"),
        HIGH("options.ao.max");

        private static final LightingQuality[] VALUES = values();

        private final String name;

        private final int vanilla;

        LightingQuality(String name) {
            this.name = name;
            this.vanilla = ordinal();
        }

        public String getLocalizedName() {
            return this.name;
        }

        public int getVanilla() {
            return vanilla;
        }

        public static LightingQuality fromOrdinal(int ordinal) {
            return VALUES[ordinal];
        }
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
                SodiumClientMod.logger().error("Could not parse config, will fallback to default settings", e);
                config = new SodiumGameOptions();
                resaveConfig = false;
            }
        } else {
            config = new SodiumGameOptions();
        }

        config.configPath = path;

        try {
            if(resaveConfig)
                config.writeChanges();
        } catch (IOException e) {
            throw new RuntimeException("Couldn't update config file", e);
        }

        return config;
    }

    public void writeChanges() throws IOException {
        Path dir = this.configPath.getParent();

        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        } else if (!Files.isDirectory(dir)) {
            throw new IOException("Not a directory: " + dir);
        }

        Files.write(this.configPath, GSON.toJson(this)
                .getBytes(StandardCharsets.UTF_8));
    }
}
