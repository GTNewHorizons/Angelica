package me.jellysquid.mods.sodium.client.gui;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.streaming.StreamingUploader;
import com.gtnewhorizons.angelica.proxy.ClientProxy;
import me.jellysquid.mods.sodium.client.gui.options.named.GraphicsQuality;
import me.jellysquid.mods.sodium.client.gui.options.named.MultiDrawMode;
import me.jellysquid.mods.sodium.client.gui.options.named.TextureFilterMode;
import net.coderbot.iris.Iris;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.MathHelper;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.AsyncOcclusionMode;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;

public class SodiumGameOptions {
    private static final int MIN_ANISOTROPY_FOR_PADDING = 2;
    private static final int MIN_ANISOTROPY_LEVEL = 1;
    private static final int MAX_ANISOTROPY_LEVEL = 4;
    private static final Gson GSON = new GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .setPrettyPrinting()
        .excludeFieldsWithModifiers(Modifier.PRIVATE)
        .create();
    private static int resourcePackMipmapCeiling = Integer.MAX_VALUE;
    public final QualitySettings quality = new QualitySettings();
    public final AdvancedSettings advanced = new AdvancedSettings();
    public final PerformanceSettings performance = new PerformanceSettings();
    private Path configPath;

    public static void recordAtlasMipmapClamp(int requestedMipmapLevels, int actualMipmapLevels) {
        resourcePackMipmapCeiling = actualMipmapLevels < requestedMipmapLevels ? actualMipmapLevels : Integer.MAX_VALUE;
    }

    public static void applyAtlasSettings() {
        final Minecraft mc = Minecraft.getMinecraft();
        final TextureMap atlas = mc.getTextureMapBlocks();

        atlas.setMipmapLevels(mc.gameSettings.mipmapLevels);
        atlas.setAnisotropicFiltering(resolvedAnisotropicFiltering());
    }

    public static int resolvedAnisotropicFiltering() {
        final Minecraft mc = Minecraft.getMinecraft();
        final TextureFilterMode mode = filteringPossible()
            ? resolveFilterMode(ClientProxy.options().quality.textureFilterMode)
            : TextureFilterMode.NONE;

        return mode.usesAnisotropy()
            ? MathHelper.clamp_int(mc.gameSettings.anisotropicFiltering, MIN_ANISOTROPY_FOR_PADDING, 1 << maxAnisotropyLevel())
            : 1;
    }

    private static boolean filteringPossible() {
        return effectiveMipmapLevelsEstimate() > 0;
    }

    private static int effectiveMipmapLevelsEstimate() {
        return Math.min(Minecraft.getMinecraft().gameSettings.mipmapLevels, resourcePackMipmapCeiling);
    }

    public static boolean needsForcedSpritePadding() {
        if (!AngelicaConfig.enableCeleritas) {
            return false;
        }
        final TextureFilterMode mode = filteringPossible()
            ? resolveFilterMode(ClientProxy.options().quality.textureFilterMode)
            : TextureFilterMode.NONE;
        return mode.needsSpritePadding() && !mode.usesAnisotropy();
    }

    public static TextureFilterMode effectiveTextureFilterMode() {
        final TextureFilterMode mode = resolveFilterMode(ClientProxy.options().quality.textureFilterMode);
        if (mode == TextureFilterMode.NONE) {
            return mode;
        }
        return Minecraft.getMinecraft().getTextureMapBlocks().mipmapLevels > 0 ? mode : TextureFilterMode.NONE;
    }

    public static TextureFilterMode resolveFilterMode(TextureFilterMode mode) {
        if (mode == null) {
            mode = TextureFilterMode.RGSS;
        }
        return mode.usesAnisotropy() && !anisotropySupported() ? TextureFilterMode.RGSS : mode;
    }

    public static int maxAnisotropyLevel() {
        final int driverMax = OpenGlHelper.anisotropicFilteringMax;
        if (driverMax < 1 << MIN_ANISOTROPY_LEVEL) {
            return 0;
        }
        return Math.min(31 - Integer.numberOfLeadingZeros(driverMax), MAX_ANISOTROPY_LEVEL);
    }

    public static boolean anisotropySupported() {
        return maxAnisotropyLevel() > 0;
    }

    public static boolean hasAnisotropyRange() {
        return maxAnisotropyLevel() > MIN_ANISOTROPY_LEVEL;
    }

    public static int minAnisotropyLevel() {
        return MIN_ANISOTROPY_LEVEL;
    }

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

        if (config.advanced.streamingUploadStrategy == null) {
            config.advanced.streamingUploadStrategy = StreamingUploader.UploadStrategy.MAP_BUFFER_RANGE;
        }

        if (config.quality.textureFilterMode == null) {
            config.quality.textureFilterMode = TextureFilterMode.RGSS;
        }

        // Clamp render-ahead to 0 if GL 3.2 fences aren't available
        if (GLStateManager.capabilities == null || !GLStateManager.capabilities.OpenGL32) {
            config.performance.cpuRenderAheadLimit = 0;
        }

        // Downgrade INDIRECT if hardware doesn't support it
        if (config.advanced.multiDrawMode == MultiDrawMode.INDIRECT && (GLStateManager.capabilities == null || (!GLStateManager.capabilities.OpenGL43 && !GLStateManager.capabilities.GL_ARB_multi_draw_indirect))) {
            config.advanced.multiDrawMode = MultiDrawMode.DIRECT;
        }

        try {
            if (resaveConfig)
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

        Files.writeString(this.configPath, GSON.toJson(this));
        if (Iris.enabled) {
            try {
                if (Iris.getIrisConfig() != null) {
                    Iris.getIrisConfig().save();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static class AdvancedSettings {
        public boolean useVertexArrayObjects = true;
        public boolean useChunkMultidraw = true;
        public MultiDrawMode multiDrawMode = MultiDrawMode.DIRECT;

        public boolean useParticleCulling = true;
        public boolean allowDirectMemoryAccess = true;
        public boolean ignoreDriverBlacklist = false;
        public StreamingUploader.UploadStrategy streamingUploadStrategy = StreamingUploader.UploadStrategy.MAP_BUFFER_RANGE;
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
        public TextureFilterMode textureFilterMode = TextureFilterMode.RGSS;
    }
}
