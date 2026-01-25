package jss.notfine.gui;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizons.angelica.dynamiclights.DynamicLights;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import jss.notfine.config.NotFineConfig;
import jss.notfine.core.Settings;
import jss.notfine.core.SettingsManager;
import jss.notfine.gui.options.control.NotFineControlValueFormatter;
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
import net.coderbot.iris.Iris;
import net.coderbot.iris.gui.option.IrisVideoSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import org.lwjgl.opengl.Display;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class NotFineGameOptionPages {
    private static final MinecraftOptionsStorage vanillaOpts = new MinecraftOptionsStorage();
    private static final SodiumOptionsStorage sodiumOpts = new SodiumOptionsStorage();

    public static OptionPage general() {
        List<OptionGroup> groups = new ArrayList<>();

        groups.add(OptionGroup.createBuilder()
            .add(OptionImpl.createBuilder(GraphicsMode.class, vanillaOpts)
                .setName(I18n.format("options.graphics"))
                .setTooltip(I18n.format("sodium.options.graphics_quality.tooltip"))
                .setControl(option -> new CyclingControl<>(option, GraphicsMode.class))
                .setBinding((opts, value) -> {
                    opts.fancyGraphics = value.isFancy();
                    SettingsManager.graphicsUpdated();
                }, opts -> GraphicsMode.fromBoolean(opts.fancyGraphics))
                .setImpact(OptionImpact.HIGH)
                .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                .build())
            .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                .setName(I18n.format("options.renderDistance"))
                .setTooltip(I18n.format("sodium.options.view_distance.tooltip"))
                .setControl(option -> new SliderControl(option, 2, (int)GameSettings.Options.RENDER_DISTANCE.getValueMax(), 1, ControlValueFormatter.quantity("options.chunks")))
                .setBinding((options, value) -> options.renderDistanceChunks = value, options -> options.renderDistanceChunks)
                .setImpact(OptionImpact.HIGH)
                .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
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
            .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                .setName(I18n.format("options.fullscreen"))
                .setTooltip(I18n.format("sodium.options.fullscreen.tooltip"))
                .setControl(TickBoxControl::new)
                .setBinding((opts, value) -> {
                    opts.fullScreen = value;
                    Minecraft client = Minecraft.getMinecraft();
                    if (client.isFullScreen() != opts.fullScreen) {
                        client.toggleFullscreen();
                        //The client might not be able to enter full-screen mode
                        opts.fullScreen = client.isFullScreen();
                    }
                }, (opts) -> opts.fullScreen)
                .build())
            .build());

        groups.add(OptionGroup.createBuilder()
            .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                .setName(I18n.format("options.framerateLimit"))
                .setTooltip(I18n.format("sodium.options.fps_limit.tooltip"))
                .setControl(option -> new SliderControl(option, 5, 260, 1, ControlValueFormatter.fpsLimit()))
                .setBinding((opts, value) -> opts.limitFramerate = value, opts -> opts.limitFramerate)
                .build())
            .build());

        int maxGuiScale = Math.max(3, Math.min(Minecraft.getMinecraft().displayWidth / 320, Minecraft.getMinecraft().displayHeight / 240));
        groups.add(OptionGroup.createBuilder()
            .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                .setName(I18n.format("options.guiScale"))
                .setTooltip(I18n.format("sodium.options.gui_scale.tooltip"))
                .setControl(option -> new SliderControl(option, 0, maxGuiScale, 1, ControlValueFormatter.guiScale()))
                .setBinding((opts, value) -> opts.guiScale = value, opts -> opts.guiScale)
                .build())
            .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                .setName(I18n.format("options.viewBobbing"))
                .setTooltip(I18n.format("sodium.options.view_bobbing.tooltip"))
                .setControl(TickBoxControl::new)
                .setBinding((opts, value) -> opts.viewBobbing = value, opts -> opts.viewBobbing)
                .build())
            .add(OptionImpl.createBuilder(LightingQuality.class, vanillaOpts)
                .setName(I18n.format("options.ao"))
                .setTooltip(I18n.format("sodium.options.smooth_lighting.tooltip"))
                .setControl(option -> new CyclingControl<>(option, LightingQuality.class))
                .setBinding((opts, value) -> opts.ambientOcclusion = value.getVanilla(), opts -> LightingQuality.fromOrdinal(opts.ambientOcclusion))
                .setImpact(OptionImpact.MEDIUM)
                .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                .build())
            .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                .setName(I18n.format("options.gamma"))
                .setTooltip(I18n.format("sodium.options.brightness.tooltip"))
                .setControl(opt -> new SliderControl(opt, 0, 100, 1, ControlValueFormatter.brightness()))
                .setBinding((opts, value) -> opts.gammaSetting = value * 0.01F, (opts) -> (int) (opts.gammaSetting / 0.01F))
                .build())
            .add(Settings.MODE_LIGHT_FLICKER.option)
            .add(Settings.DYNAMIC_FOV.option)
            .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                .setName(I18n.format("options.mipmapLevels"))
                .setTooltip(I18n.format("sodium.options.mipmap_levels.tooltip"))
                .setControl(option -> new SliderControl(option, 0, 4, 1, ControlValueFormatter.multiplier()))
                //mc.getTextureMapBlocks().setMipmapLevels(this.mipmapLevels); ?
                .setBinding((opts, value) -> opts.mipmapLevels = value, opts -> opts.mipmapLevels)
                .setImpact(OptionImpact.MEDIUM)
                .setFlags(OptionFlag.REQUIRES_ASSET_RELOAD)
                .build())
            .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                .setName(I18n.format("options.anisotropicFiltering"))
                .setTooltip(I18n.format("sodium.options.anisotropic_filtering.tooltip"))
                .setControl(option -> new SliderControl(option, 0, 4, 1, NotFineControlValueFormatter.powerOfTwo()))
                .setBinding(
                    //mc.getTextureMapBlocks().setAnisotropicFiltering(this.anisotropicFiltering); ?
                    (opts, value) -> opts.anisotropicFiltering = value == 0 ? 1 : (int)Math.pow(2, value),
                    (opts) -> opts.anisotropicFiltering == 1 ? 0 : (int)(Math.log(opts.anisotropicFiltering) / Math.log(2)))
                .setImpact(OptionImpact.MEDIUM)
                .setFlags(OptionFlag.REQUIRES_ASSET_RELOAD)
                .build())
            .build());

        if(Iris.enabled) {
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
            groups.add(OptionGroup.createBuilder()
                .add(maxShadowDistanceSlider)
                .build());
        }


        return new OptionPage(I18n.format("options.video"), ImmutableList.copyOf(groups));
    }

    public static OptionPage detail() {
        List<OptionGroup> groups = new ArrayList<>();
        groups.add(OptionGroup.createBuilder()
            .add(Settings.MODE_LEAVES.option)
            .add(OptionImpl.createBuilder(GraphicsQuality.class, sodiumOpts)
                .setName(I18n.format("sodium.options.grass_quality.name"))
                .setTooltip(I18n.format("sodium.options.grass_quality.tooltip"))
                .setControl(option -> new CyclingControl<>(option, GraphicsQuality.class))
                .setBinding((opts, value) -> opts.quality.grassQuality = value, opts -> opts.quality.grassQuality)
                .setImpact(OptionImpact.MEDIUM)
                .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                .build())
            .add(Settings.MODE_WATER.option)
            .add(Settings.DOWNFALL_DISTANCE.option)
            .add(Settings.MODE_VIGNETTE.option)
            .add(Settings.MODE_SHADOWS.option)
            .add(Settings.VOID_FOG.option)
            .add(Settings.MODE_DROPPED_ITEMS.option)
            .add(Settings.MODE_GLINT_WORLD.option)
            .add(Settings.MODE_GLINT_INV.option)
        .build());
        return new OptionPage(I18n.format("options.button.detail"), ImmutableList.copyOf(groups));
    }

    public static OptionPage atmosphere() {
        List<OptionGroup> groups = new ArrayList<>();
        groups.add(OptionGroup.createBuilder()
            .add(Settings.MODE_SKY.option)
            .add(Settings.MODE_SUN_MOON.option)
            .add(Settings.MODE_CLOUDS.option)
            .add(Settings.RENDER_DISTANCE_CLOUDS.option)
            .add(Settings.CLOUD_HEIGHT.option)
            .add(Settings.CLOUD_SCALE.option)
            .add(Settings.MODE_CLOUD_TRANSLUCENCY.option)
            .add(Settings.MODE_STARS.option)
            .add(Settings.TOTAL_STARS.option)
            .add(Settings.HORIZON_DISABLE.option)
            .add(Settings.FOG_DISABLE.option)
            .add(Settings.FOG_NEAR_DISTANCE.option)
        .build());
        return new OptionPage(I18n.format("options.button.sky"), ImmutableList.copyOf(groups));
    }

    public static OptionPage particles() {
        List<OptionGroup> groups = new ArrayList<>();
        groups.add(OptionGroup.createBuilder()
            .add(OptionImpl.createBuilder(ParticleMode.class, vanillaOpts)
                .setName(I18n.format("options.particles"))
                .setTooltip(I18n.format("sodium.options.particle_quality.tooltip"))
                .setControl(opt -> new CyclingControl<>(opt, ParticleMode.class))
                .setBinding((opts, value) -> opts.particleSetting = value.ordinal(), (opts) -> ParticleMode.fromOrdinal(opts.particleSetting))
                .setImpact(OptionImpact.LOW)
                .build())
            .add(Settings.PARTICLES_VOID.option)
            .add(Settings.PARTICLES_ENC_TABLE.option)
        .build());
        return new OptionPage(I18n.format("options.button.particle"), ImmutableList.copyOf(groups));
    }

    public static OptionPage other() {
        List<OptionGroup> groups = new ArrayList<>();

        groups.add(OptionGroup.createBuilder()
            .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                .setName(I18n.format("options.advancedOpengl"))
                .setTooltip(I18n.format("sodium.options.advanced_opengl.tooltip"))
                .setControl(TickBoxControl::new)
                .setBinding((opts, value) -> opts.advancedOpengl = value, opts -> opts.advancedOpengl)
                .setImpact(OptionImpact.VARIES)
                .setEnabled(NotFineConfig.allowAdvancedOpenGL)
                .build())
            .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                .setName(I18n.format("options.fboEnable"))
                .setTooltip(I18n.format("sodium.options.fbo.tooltip"))
                .setControl(TickBoxControl::new)
                .setBinding((opts, value) -> opts.fboEnable = value, opts -> opts.fboEnable)
                .setEnabled(NotFineConfig.allowToggleFBO)
                .build())
            .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                .setName(I18n.format("options.anaglyph"))
                .setTooltip(I18n.format("sodium.options.anaglyph.tooltip"))
                .setControl(TickBoxControl::new)
                .setBinding((opts, value) -> opts.anaglyph = value, opts -> opts.anaglyph)
                .setImpact(OptionImpact.HIGH)
                .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                .setEnabled(NotFineConfig.allowToggle3DAnaglyph)
                .build())
            .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                .setName(I18n.format("options.showCape"))
                .setTooltip(I18n.format("sodium.options.show_cape.tooltip"))
                .setControl(TickBoxControl::new)
                .setBinding((opts, value) -> opts.showCape = value, opts -> opts.showCape)
                .build())
            .add(Settings.MODE_GUI_BACKGROUND.option)
        .build());

        groups.add(OptionGroup.createBuilder()
            .add(Settings.GUI_BACKGROUND.option)
        .build());

        groups.add(OptionGroup.createBuilder()
            .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                .setName(I18n.format("sodium.options.use_chunk_multidraw.name"))
                .setTooltip(I18n.format("sodium.options.use_chunk_multidraw.tooltip"))
                .setControl(TickBoxControl::new)
                .setBinding((opts, value) -> opts.advanced.useChunkMultidraw = value, opts -> opts.advanced.useChunkMultidraw)
                .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                .setImpact(OptionImpact.EXTREME)
                .setEnabled(true)
                .build())
            .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                .setName(I18n.format("sodium.options.use_vertex_objects.name"))
                .setTooltip(I18n.format("sodium.options.use_vertex_objects.tooltip"))
                .setControl(TickBoxControl::new)
                .setBinding((opts, value) -> opts.advanced.useVertexArrayObjects = value, opts -> opts.advanced.useVertexArrayObjects)
                .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                .setImpact(OptionImpact.LOW)
                .build())
            .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                .setName(I18n.format("sodium.options.use_block_face_culling.name"))
                .setTooltip(I18n.format("sodium.options.use_block_face_culling.tooltip"))
                .setControl(TickBoxControl::new)
                .setImpact(OptionImpact.MEDIUM)
                .setBinding((opts, value) -> opts.advanced.useBlockFaceCulling = value, opts -> opts.advanced.useBlockFaceCulling)
                .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                .build())
            .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                .setName(I18n.format("sodium.options.use_compact_vertex_format.name"))
                .setTooltip(I18n.format("sodium.options.use_compact_vertex_format.tooltip"))
                .setControl(TickBoxControl::new)
                .setImpact(OptionImpact.MEDIUM)
                .setBinding((opts, value) -> opts.advanced.useCompactVertexFormat = value, opts -> opts.advanced.useCompactVertexFormat)
                .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                .build())
            .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                .setName(I18n.format("sodium.options.use_fog_occlusion.name"))
                .setTooltip(I18n.format("sodium.options.use_fog_occlusion.tooltip"))
                .setControl(TickBoxControl::new)
                .setBinding((opts, value) -> opts.advanced.useFogOcclusion = value, opts -> opts.advanced.useFogOcclusion)
                .setImpact(OptionImpact.MEDIUM)
                .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                .build())
            .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                .setName(I18n.format("sodium.options.translucency_sorting.name"))
                .setTooltip(I18n.format("sodium.options.translucency_sorting.tooltip"))
                .setControl(TickBoxControl::new)
                .setBinding((opts, value) -> opts.advanced.translucencySorting = value, opts -> opts.advanced.translucencySorting)
                .setImpact(OptionImpact.MEDIUM)
                .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                .build())
            .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                .setName(I18n.format("sodium.options.use_entity_culling.name"))
                .setTooltip(I18n.format("sodium.options.use_entity_culling.tooltip"))
                .setControl(TickBoxControl::new)
                .setImpact(OptionImpact.MEDIUM)
                .setBinding((opts, value) -> opts.advanced.useEntityCulling = value, opts -> opts.advanced.useEntityCulling)
                .build())
            .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                .setName(I18n.format("sodium.options.use_particle_culling.name"))
                .setTooltip(I18n.format("sodium.options.use_particle_culling.tooltip"))
                .setControl(TickBoxControl::new)
                .setImpact(OptionImpact.MEDIUM)
                .setBinding((opts, value) -> opts.advanced.useParticleCulling = value, opts -> opts.advanced.useParticleCulling)
                .build())
            .build());
        groups.add(OptionGroup.createBuilder()
            .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                .setName(I18n.format("sodium.options.animate_only_visible_textures.name"))
                .setTooltip(I18n.format("sodium.options.animate_only_visible_textures.tooltip"))
                .setControl(TickBoxControl::new)
                .setImpact(OptionImpact.MEDIUM)
                .setBinding((opts, value) -> opts.advanced.animateOnlyVisibleTextures = value, opts -> opts.advanced.animateOnlyVisibleTextures)
                .build())
            .build());
        groups.add(OptionGroup.createBuilder()
            .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                .setName(I18n.format("sodium.options.allow_direct_memory_access.name"))
                .setTooltip(I18n.format("sodium.options.allow_direct_memory_access.tooltip"))
                .setControl(TickBoxControl::new)
                .setImpact(OptionImpact.HIGH)
                .setBinding((opts, value) -> opts.advanced.allowDirectMemoryAccess = value, opts -> opts.advanced.allowDirectMemoryAccess)
                .build())
            .build());
        groups.add(OptionGroup.createBuilder()
            .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                .setName(I18n.format("sodium.options.ignore_driver_blacklist.name"))
                .setTooltip(I18n.format("sodium.options.ignore_driver_blacklist.tooltip"))
                .setControl(TickBoxControl::new)
                .setBinding((opts, value) -> opts.advanced.ignoreDriverBlacklist = value, opts -> opts.advanced.ignoreDriverBlacklist)
                .build())
            .build());
        groups.add(OptionGroup.createBuilder()
            .add(OptionImpl.createBuilder(int.class, sodiumOpts)
                .setName(I18n.format("sodium.options.chunk_update_threads.name"))
                .setTooltip(I18n.format("sodium.options.chunk_update_threads.tooltip"))
                .setControl(o -> new SliderControl(o, 0, Runtime.getRuntime().availableProcessors(), 1, ControlValueFormatter.quantityOrDisabled("sodium.options.threads.value", "sodium.options.default")))
                .setImpact(OptionImpact.HIGH)
                .setBinding((opts, value) -> opts.performance.chunkBuilderThreads = value, opts -> opts.performance.chunkBuilderThreads)
                .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                .build())
            .build());
        groups.add(OptionGroup.createBuilder()
            .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                .setName(I18n.format("sodium.options.always_defer_chunk_updates.name"))
                .setTooltip(I18n.format("sodium.options.always_defer_chunk_updates.tooltip"))
                .setControl(TickBoxControl::new)
                .setImpact(OptionImpact.HIGH)
                .setBinding((opts, value) -> opts.performance.alwaysDeferChunkUpdates = value, opts -> opts.performance.alwaysDeferChunkUpdates)
                .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                .build())
            .build());

        groups.add(OptionGroup.createBuilder()
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

        return new OptionPage(I18n.format("options.button.other"), ImmutableList.copyOf(groups));
    }

}
