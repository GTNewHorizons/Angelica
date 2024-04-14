package jss.notfine.gui;

import com.google.common.collect.ImmutableList;
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
import me.jellysquid.mods.sodium.client.gui.options.named.LightingQuality;
import me.jellysquid.mods.sodium.client.gui.options.named.ParticleMode;
import me.jellysquid.mods.sodium.client.gui.options.storage.MinecraftOptionsStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import org.lwjgl.opengl.Display;

import java.util.ArrayList;
import java.util.List;

public class NotFineGameOptionPages {
    private static final MinecraftOptionsStorage vanillaOpts = new MinecraftOptionsStorage();

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
            .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                .setName(I18n.format("options.framerateLimit"))
                .setTooltip(I18n.format("sodium.options.fps_limit.tooltip"))
                .setControl(option -> new SliderControl(option, 5, 260, 1, ControlValueFormatter.fpsLimit()))
                .setBinding((opts, value) -> opts.limitFramerate = value, opts -> opts.limitFramerate)
                .build())
            .build());

        groups.add(OptionGroup.createBuilder()
            .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                .setName(I18n.format("options.guiScale"))
                .setTooltip(I18n.format("sodium.options.gui_scale.tooltip"))
                .setControl(option -> new SliderControl(option, 0, 4, 1, ControlValueFormatter.guiScale()))
                .setBinding((opts, value) -> opts.guiScale = value, opts -> opts.guiScale)
                .build())
            .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                .setName(I18n.format("options.viewBobbing"))
                .setTooltip(I18n.format("sodium.options.view_bobbing.tooltip"))
                .setControl(TickBoxControl::new)
                .setBinding((opts, value) -> opts.viewBobbing = value, opts -> opts.viewBobbing)
                .build())
            .add(Settings.DYNAMIC_FOV.option)
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
        return new OptionPage(I18n.format("options.video"), ImmutableList.copyOf(groups));
    }

    public static OptionPage detail() {
        List<OptionGroup> groups = new ArrayList<>();
        groups.add(OptionGroup.createBuilder()
            .add(Settings.MODE_LEAVES.option)
            .add(Settings.MODE_WATER.option)
            .add(Settings.DOWNFALL_DISTANCE.option)
            .add(Settings.MODE_VIGNETTE.option)
            .add(Settings.MODE_SHADOWS.option)
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
            .add(Settings.TOTAL_STARS.option)
            .add(Settings.VOID_FOG.option)
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
                .build())
            .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                .setName(I18n.format("options.anaglyph"))
                .setTooltip(I18n.format("sodium.options.anaglyph.tooltip"))
                .setControl(TickBoxControl::new)
                .setBinding((opts, value) -> opts.anaglyph = value, opts -> opts.anaglyph)
                .setImpact(OptionImpact.HIGH)
                .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
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

        return new OptionPage(I18n.format("options.button.other"), ImmutableList.copyOf(groups));
    }

}
