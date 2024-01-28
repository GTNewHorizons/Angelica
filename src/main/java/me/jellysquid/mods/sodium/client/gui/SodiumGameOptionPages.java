package me.jellysquid.mods.sodium.client.gui;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import jss.notfine.core.Settings;
import jss.notfine.core.SettingsManager;
import me.flashyreese.mods.reeses_sodium_options.client.gui.ReeseSodiumVideoOptionsScreen;
import me.jellysquid.mods.sodium.client.gui.options.OptionFlag;
import me.jellysquid.mods.sodium.client.gui.options.OptionGroup;
import me.jellysquid.mods.sodium.client.gui.options.OptionImpact;
import me.jellysquid.mods.sodium.client.gui.options.OptionImpl;
import me.jellysquid.mods.sodium.client.gui.options.OptionPage;
import me.jellysquid.mods.sodium.client.gui.options.control.ControlValueFormatter;
import me.jellysquid.mods.sodium.client.gui.options.control.CyclingControl;
import me.jellysquid.mods.sodium.client.gui.options.control.SliderControl;
import me.jellysquid.mods.sodium.client.gui.options.control.TickBoxControl;
import me.jellysquid.mods.sodium.client.gui.options.named.GraphicsMode;
import me.jellysquid.mods.sodium.client.gui.options.named.GraphicsQuality;
import me.jellysquid.mods.sodium.client.gui.options.named.LightingQuality;
import me.jellysquid.mods.sodium.client.gui.options.named.ParticleMode;
import me.jellysquid.mods.sodium.client.gui.options.storage.MinecraftOptionsStorage;
import me.jellysquid.mods.sodium.client.gui.options.storage.SodiumOptionsStorage;
import me.jellysquid.mods.sodium.client.render.chunk.backends.multidraw.MultidrawChunkRenderBackend;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gui.option.IrisVideoSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import org.lwjgl.opengl.Display;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SodiumGameOptionPages {
    private static final SodiumOptionsStorage sodiumOpts = new SodiumOptionsStorage();
    private static final MinecraftOptionsStorage vanillaOpts = new MinecraftOptionsStorage();

    public static OptionPage general() {
        final List<OptionGroup> groups = new ArrayList<>();
        final OptionGroup.Builder firstGroupBuilder = OptionGroup.createBuilder();

        firstGroupBuilder.add(OptionImpl.createBuilder(int.class, vanillaOpts)
                .setName(I18n.format("options.renderDistance"))
                .setTooltip(I18n.format("sodium.options.view_distance.tooltip"))
                .setControl(option -> new SliderControl(option, 2, (int) GameSettings.Options.RENDER_DISTANCE.getValueMax(), 1, ControlValueFormatter.quantity("options.chunks")))
                .setBinding((options, value) -> options.renderDistanceChunks = value, options -> options.renderDistanceChunks)
                .setImpact(OptionImpact.HIGH)
                .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                .build());

        if(AngelicaConfig.enableIris) {
            final OptionImpl<GameSettings, Integer> maxShadowDistanceSlider = OptionImpl.createBuilder(int.class, vanillaOpts)
                .setName(I18n.format("options.iris.shadowDistance"))
                .setTooltip(I18n.format("options.iris.shadowDistance.sodium_tooltip"))
                .setControl(option -> new SliderControl(option, 0, 32, 1, ControlValueFormatter.quantity("options.chunks")))
                .setBinding((options, value) -> {
                        IrisVideoSettings.shadowDistance = value;
                        try {
                            Iris.getIrisConfig().save();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    },
                    options -> IrisVideoSettings.getOverriddenShadowDistance(IrisVideoSettings.shadowDistance))
                .setImpact(OptionImpact.HIGH)
                .setEnabled(true)
                .build();

            maxShadowDistanceSlider.iris$dynamicallyEnable(IrisVideoSettings::isShadowDistanceSliderEnabled);
            firstGroupBuilder.add(maxShadowDistanceSlider).build();
        }

        firstGroupBuilder.add(OptionImpl.createBuilder(int.class, vanillaOpts)
                .setName(I18n.format("options.gamma"))
                .setTooltip(I18n.format("sodium.options.brightness.tooltip"))
                .setControl(opt -> new SliderControl(opt, 0, 100, 1, ControlValueFormatter.brightness()))
                .setBinding((opts, value) -> opts.gammaSetting = value * 0.01F, (opts) -> (int) (opts.gammaSetting / 0.01F))
                .build());
        firstGroupBuilder.add(Settings.MODE_SKY.option);
        firstGroupBuilder.add(Settings.MODE_SUN_MOON.option);
        firstGroupBuilder.add(Settings.MODE_STARS.option);
        firstGroupBuilder.add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                .setName(I18n.format("sodium.options.clouds.name"))
                .setTooltip(I18n.format("sodium.options.clouds.tooltip"))
                .setControl(TickBoxControl::new)
                .setBinding((opts, value) -> opts.clouds = value, (opts) -> opts.clouds)
                .setImpact(OptionImpact.LOW)
                .build());
        groups.add(firstGroupBuilder.build());


        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setName(I18n.format("options.guiScale"))
                        .setTooltip(I18n.format("sodium.options.gui_scale.tooltip"))
                        .setControl(option -> new SliderControl(option, 0, 3, 1, ControlValueFormatter.guiScale()))
                        .setBinding((opts, value) -> {
                            opts.guiScale = value;
                            // Resizing our window
                            if(Minecraft.getMinecraft().currentScreen instanceof ReeseSodiumVideoOptionsScreen oldGui) {
                                Minecraft.getMinecraft().displayGuiScreen(new ReeseSodiumVideoOptionsScreen(oldGui.prevScreen));
                            }
                            else if(Minecraft.getMinecraft().currentScreen instanceof SodiumOptionsGUI oldGui) {
                                Minecraft.getMinecraft().displayGuiScreen(new SodiumOptionsGUI(oldGui.prevScreen));
                            }
                        }, opts -> opts.guiScale)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                        .setName(I18n.format("options.fullscreen"))
                        .setTooltip(I18n.format("sodium.options.fullscreen.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> {
                            opts.fullScreen = value;

                            final Minecraft client = Minecraft.getMinecraft();

                            if (client.isFullScreen() != opts.fullScreen) {
                                client.toggleFullscreen();

                                // The client might not be able to enter full-screen mode
                                opts.fullScreen = client.isFullScreen();
                            }
                        }, (opts) -> opts.fullScreen)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                        .setName(I18n.format("options.vsync"))
                        .setTooltip(I18n.format("sodium.options.v_sync.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> {
                            opts.enableVsync = value;
                            Display.setVSyncEnabled(opts.enableVsync);
                        }, opts -> opts.enableVsync)
                        .setImpact(OptionImpact.VARIES)
                        .build())
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setName(I18n.format("options.framerateLimit"))
                        .setTooltip(I18n.format("sodium.options.fps_limit.tooltip"))
                        .setControl(option -> new SliderControl(option, 5, 260, 5, ControlValueFormatter.fpsLimit()))
                        .setBinding((opts, value) -> opts.limitFramerate = value, opts -> opts.limitFramerate)
                        .build())
                .build());

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                        .setName(I18n.format("options.viewBobbing"))
                        .setTooltip(I18n.format("sodium.options.view_bobbing.tooltip"))
                        .setControl(TickBoxControl::new)
                    .setBinding((opts, value) -> opts.viewBobbing = value, opts -> opts.viewBobbing)
                        .build())
                .build());

        return new OptionPage(I18n.format("stat.generalButton"), ImmutableList.copyOf(groups));
    }

    public static OptionPage quality() {
        final List<OptionGroup> groups = new ArrayList<>();

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(GraphicsMode.class, vanillaOpts)
                        .setName(I18n.format("options.graphics"))
                        .setTooltip(I18n.format("sodium.options.graphics_quality.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, GraphicsMode.class))
                        .setBinding(
                                (opts, value) -> { opts.fancyGraphics = value.isFancy();
                                    SettingsManager.graphicsUpdated(); },
                                opts -> GraphicsMode.fromBoolean(opts.fancyGraphics))
                        .setImpact(OptionImpact.HIGH)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .build());

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(GraphicsQuality.class, sodiumOpts)
                        .setName(I18n.format("options.renderClouds"))
                        .setTooltip(I18n.format("sodium.options.clouds_quality.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, GraphicsQuality.class))
                        .setBinding((opts, value) -> opts.quality.cloudQuality = value, opts -> opts.quality.cloudQuality)
                        .setImpact(OptionImpact.LOW)
                        .build())
                .add(OptionImpl.createBuilder(GraphicsQuality.class, sodiumOpts)
                        .setName(I18n.format("soundCategory.weather"))
                        .setTooltip(I18n.format("sodium.options.weather_quality.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, GraphicsQuality.class))
                        .setBinding((opts, value) -> opts.quality.weatherQuality = value, opts -> opts.quality.weatherQuality)
                        .setImpact(OptionImpact.MEDIUM)
                        .build())
                .add(AngelicaConfig.enableNotFineFeatures ? Settings.MODE_LEAVES.option :
                    OptionImpl.createBuilder(GraphicsQuality.class, sodiumOpts)
                        .setName(I18n.format("sodium.options.leaves_quality.name"))
                        .setTooltip(I18n.format("sodium.options.leaves_quality.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, GraphicsQuality.class))
                        .setBinding((opts, value) -> opts.quality.leavesQuality = value, opts -> opts.quality.leavesQuality)
                        .setImpact(OptionImpact.MEDIUM)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.createBuilder(ParticleMode.class, vanillaOpts)
                        .setName(I18n.format("options.particles"))
                        .setTooltip(I18n.format("sodium.options.particle_quality.tooltip"))
                        .setControl(opt -> new CyclingControl<>(opt, ParticleMode.class))
                        .setBinding((opts, value) -> opts.particleSetting = value.ordinal(), (opts) -> ParticleMode.fromOrdinal(opts.particleSetting))
                        .setImpact(OptionImpact.LOW)
                        .build())
                .add(OptionImpl.createBuilder(GraphicsQuality.class, sodiumOpts)
                    .setName(I18n.format("sodium.options.grass_quality.name"))
                    .setTooltip(I18n.format("sodium.options.grass_quality.tooltip"))
                    .setControl(option -> new CyclingControl<>(option, GraphicsQuality.class))
                    .setBinding((opts, value) -> opts.quality.grassQuality = value, opts -> opts.quality.grassQuality)
                    .setImpact(OptionImpact.MEDIUM)
                    .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                    .build())
                .add(OptionImpl.createBuilder(LightingQuality.class, vanillaOpts)
                        .setName(I18n.format("options.ao"))
                        .setTooltip(I18n.format("sodium.options.smooth_lighting.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, LightingQuality.class))
                        .setBinding((opts, value) -> opts.ambientOcclusion = value.getVanilla(), opts -> LightingQuality.fromOrdinal(opts.ambientOcclusion))
                        .setImpact(OptionImpact.MEDIUM)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                // TODO
                /*.add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setName(new TranslatableText("options.biomeBlendRadius"))
                        .setTooltip(new TranslatableText("sodium.options.biome_blend.tooltip"))
                        .setControl(option -> new SliderControl(option, 0, 7, 1, ControlValueFormatter.quantityOrDisabled("sodium.options.biome_blend.value", "gui.none")))
                        .setBinding((opts, value) -> opts.biomeBlendRadius = value, opts -> opts.biomeBlendRadius)
                        .setImpact(OptionImpact.LOW)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setName(new TranslatableText("options.entityDistanceScaling"))
                        .setTooltip(new TranslatableText("sodium.options.entity_distance.tooltip"))
                        .setControl(option -> new SliderControl(option, 50, 500, 25, ControlValueFormatter.percentage()))
                        .setBinding((opts, value) -> opts.entityDistanceScaling = value / 100.0F, opts -> Math.round(opts.entityDistanceScaling * 100.0F))
                        .setImpact(OptionImpact.MEDIUM)
                        .build()
                )*/
                .add(OptionImpl.createBuilder(GraphicsQuality.class, sodiumOpts)
                        .setName(I18n.format("options.entityShadows"))
                        .setTooltip(I18n.format("sodium.options.entity_shadows.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, GraphicsQuality.class))
                        .setBinding((opts, value) -> opts.quality.entityShadows = value, opts -> opts.quality.entityShadows)
                        .setImpact(OptionImpact.LOW)
                        .build())
                .add(OptionImpl.createBuilder(GraphicsQuality.class, sodiumOpts)
                        .setName(I18n.format("sodium.options.vignette.name"))
                        .setTooltip(I18n.format("sodium.options.vignette.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, GraphicsQuality.class))
                        .setBinding((opts, value) -> opts.quality.enableVignette = value, opts -> opts.quality.enableVignette)
                        .setImpact(OptionImpact.LOW)
                        .build())
                    .add(Settings.TOTAL_STARS.option)
                .build());


        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setName(I18n.format("options.mipmapLevels"))
                        .setTooltip(I18n.format("sodium.options.mipmap_levels.tooltip"))
                        .setControl(option -> new SliderControl(option, 0, 4, 1, ControlValueFormatter.multiplier()))
                        .setBinding((opts, value) -> opts.mipmapLevels = value, opts -> opts.mipmapLevels)
                        .setImpact(OptionImpact.MEDIUM)
                        .setFlags(OptionFlag.REQUIRES_ASSET_RELOAD)
                        .build())
                .build());
        groups.add(OptionGroup.createBuilder()
            .add(Settings.MODE_GLINT_INV.option)
            .add(Settings.MODE_GLINT_WORLD.option)
            .build());

        return new OptionPage(I18n.format("sodium.options.pages.quality"), ImmutableList.copyOf(groups));
    }

    public static OptionPage advanced() {
        final List<OptionGroup> groups = new ArrayList<>();

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName(I18n.format("sodium.options.use_chunk_multidraw.name"))
                        .setTooltip(I18n.format("sodium.options.use_chunk_multidraw.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.advanced.useChunkMultidraw = value, opts -> opts.advanced.useChunkMultidraw)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .setImpact(OptionImpact.EXTREME)
                        .setEnabled(MultidrawChunkRenderBackend.isSupported(sodiumOpts.getData().advanced.ignoreDriverBlacklist))
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName(I18n.format("sodium.options.use_vertex_objects.name"))
                        .setTooltip(I18n.format("sodium.options.use_vertex_objects.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.advanced.useVertexArrayObjects = value, opts -> opts.advanced.useVertexArrayObjects)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .setImpact(OptionImpact.LOW)
                        .build())
                .build());

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName(I18n.format("sodium.options.use_block_face_culling.name"))
                        .setTooltip(I18n.format("sodium.options.use_block_face_culling.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setImpact(OptionImpact.MEDIUM)
                        .setBinding((opts, value) -> opts.advanced.useBlockFaceCulling = value, opts -> opts.advanced.useBlockFaceCulling)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build()
                )
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName(I18n.format("sodium.options.use_compact_vertex_format.name"))
                        .setTooltip(I18n.format("sodium.options.use_compact_vertex_format.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setImpact(OptionImpact.MEDIUM)
                        .setBinding((opts, value) -> opts.advanced.useCompactVertexFormat = value, opts -> opts.advanced.useCompactVertexFormat)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build()
                )
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName(I18n.format("sodium.options.use_fog_occlusion.name"))
                        .setTooltip(I18n.format("sodium.options.use_fog_occlusion.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.advanced.useFogOcclusion = value, opts -> opts.advanced.useFogOcclusion)
                        .setImpact(OptionImpact.MEDIUM)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build()
                )
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                    .setName(I18n.format("sodium.options.translucency_sorting.name"))
                    .setTooltip(I18n.format("sodium.options.translucency_sorting.tooltip"))
                    .setControl(TickBoxControl::new)
                    .setBinding((opts, value) -> opts.advanced.translucencySorting = value, opts -> opts.advanced.translucencySorting)
                    .setImpact(OptionImpact.MEDIUM)
                    .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                    .build()
                )
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName(I18n.format("sodium.options.use_entity_culling.name"))
                        .setTooltip(I18n.format("sodium.options.use_entity_culling.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setImpact(OptionImpact.MEDIUM)
                        .setBinding((opts, value) -> opts.advanced.useEntityCulling = value, opts -> opts.advanced.useEntityCulling)
                        .build()
                )
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName(I18n.format("sodium.options.use_particle_culling.name"))
                        .setTooltip(I18n.format("sodium.options.use_particle_culling.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setImpact(OptionImpact.MEDIUM)
                        .setBinding((opts, value) -> opts.advanced.useParticleCulling = value, opts -> opts.advanced.useParticleCulling)
                        .build()
                )
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName(I18n.format("sodium.options.animate_only_visible_textures.name"))
                        .setTooltip(I18n.format("sodium.options.animate_only_visible_textures.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setImpact(OptionImpact.MEDIUM)
                        .setBinding((opts, value) -> opts.advanced.animateOnlyVisibleTextures = value, opts -> opts.advanced.animateOnlyVisibleTextures)
                        .build()
                )
                .build());

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName(I18n.format("sodium.options.allow_direct_memory_access.name"))
                        .setTooltip(I18n.format("sodium.options.allow_direct_memory_access.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setImpact(OptionImpact.HIGH)
                        .setBinding((opts, value) -> opts.advanced.allowDirectMemoryAccess = value, opts -> opts.advanced.allowDirectMemoryAccess)
                        .build()
                )
                .build());

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName(I18n.format("sodium.options.ignore_driver_blacklist.name"))
                        .setTooltip(I18n.format("sodium.options.ignore_driver_blacklist.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.advanced.ignoreDriverBlacklist = value, opts -> opts.advanced.ignoreDriverBlacklist)
                        .build()
                )
                .build());

        groups.add(OptionGroup.createBuilder()
                .add(Settings.MODE_GUI_BACKGROUND.option)
                .add(Settings.GUI_BACKGROUND.option)
            .build());

        return new OptionPage(I18n.format("sodium.options.pages.advanced"), ImmutableList.copyOf(groups));
    }

    public static OptionPage performance() {
        final List<OptionGroup> groups = new ArrayList<>();

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(int.class, sodiumOpts)
                        .setName(I18n.format("sodium.options.chunk_update_threads.name"))
                        .setTooltip(I18n.format("sodium.options.chunk_update_threads.tooltip"))
                        .setControl(o -> new SliderControl(o, 0, Runtime.getRuntime().availableProcessors(), 1, ControlValueFormatter.quantityOrDisabled("sodium.options.threads.value", "sodium.options.default")))
                        .setImpact(OptionImpact.HIGH)
                        .setBinding((opts, value) -> opts.performance.chunkBuilderThreads = value, opts -> opts.performance.chunkBuilderThreads)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build()
                )
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName(I18n.format("sodium.options.always_defer_chunk_updates.name"))
                        .setTooltip(I18n.format("sodium.options.always_defer_chunk_updates.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setImpact(OptionImpact.HIGH)
                        .setBinding((opts, value) -> opts.performance.alwaysDeferChunkUpdates = value, opts -> opts.performance.alwaysDeferChunkUpdates)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName(I18n.format("sodium.options.use_no_error_context.name"))
                        .setTooltip(I18n.format("sodium.options.use_no_error_context.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setImpact(OptionImpact.LOW)
                        .setBinding((opts, value) -> opts.performance.useNoErrorGLContext = value, opts -> opts.performance.useNoErrorGLContext)
                        .setFlags(OptionFlag.REQUIRES_GAME_RESTART)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName(I18n.format("sodium.options.use_gl_state_cache.name"))
                        .setTooltip(I18n.format("sodium.options.use_gl_state_cache.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setImpact(OptionImpact.EXTREME)
                        .setBinding((opts, value) -> GLStateManager.BYPASS_CACHE = !value, opts -> !GLStateManager.BYPASS_CACHE)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())

                .build());

        return new OptionPage(I18n.format("sodium.options.pages.performance"), ImmutableList.copyOf(groups));
    }
}
