package me.jellysquid.mods.sodium.client.gui;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.backend.VSyncMode;
import me.jellysquid.mods.sodium.client.gui.options.Option;
import me.jellysquid.mods.sodium.client.gui.options.OptionImpact;
import me.jellysquid.mods.sodium.client.gui.options.OptionImpl;
import me.jellysquid.mods.sodium.client.gui.options.control.ControlValueFormatter;
import me.jellysquid.mods.sodium.client.gui.options.control.CyclingControl;
import me.jellysquid.mods.sodium.client.gui.options.control.SliderControl;
import me.jellysquid.mods.sodium.client.gui.options.storage.MinecraftOptionsStorage;
import me.jellysquid.mods.sodium.client.gui.options.storage.SodiumOptionsStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;

import java.util.Arrays;
import java.util.Locale;

public record FrameRateOptions(Option<VSyncMode> vsync, Option<Integer> maxFramerate) {

    public static FrameRateOptions create(MinecraftOptionsStorage vanillaOpts, SodiumOptionsStorage sodiumOpts) {
        final VSyncMode[] allowed = selectableModes();
        final String[] names = modeNames(VSyncMode.values());

        final OptionImpl<SodiumGameOptions, VSyncMode> vsync = OptionImpl.createBuilder(VSyncMode.class, sodiumOpts)
            .setName(I18n.format("options.vsync"))
            .setTooltip(I18n.format("sodium.options.v_sync.tooltip"))
            .setControl(option -> new CyclingControl<>(option, VSyncMode.class, allowed, names))
            .setBinding((opts, value) -> {
                opts.advanced.vsyncMode = value;
                final Minecraft mc = Minecraft.getMinecraft();
                mc.gameSettings.enableVsync = value.tearFree();
                mc.gameSettings.saveOptions();
                GLStateManager.setVSyncMode(value);
            }, FrameRateOptions::storedMode)
            .setImpact(OptionImpact.VARIES)
            .build();

        final OptionImpl<GameSettings, Integer> maxFramerate = OptionImpl.createBuilder(int.class, vanillaOpts)
            .setName(I18n.format("options.framerateLimit"))
            .setTooltip(I18n.format("sodium.options.fps_limit.tooltip"))
            .setControl(option -> new SliderControl(option, 5, 260, 1, ControlValueFormatter.fpsLimit()))
            .setBinding((opts, value) -> opts.limitFramerate = value, opts -> opts.limitFramerate)
            .build();

        return new FrameRateOptions(vsync, maxFramerate);
    }

    static VSyncMode[] selectableModes() {
        final VSyncMode[] supported = Arrays.stream(VSyncMode.values())
            .filter(GLStateManager::supportsVSyncMode)
            .toArray(VSyncMode[]::new);
        return supported.length == 0 ? new VSyncMode[]{ VSyncMode.ON } : supported;
    }

    static String[] modeKeys(VSyncMode[] modes) {
        return Arrays.stream(modes)
            .map(mode -> "sodium.options.vsync_mode." + mode.name().toLowerCase(Locale.ROOT))
            .toArray(String[]::new);
    }

    private static String[] modeNames(VSyncMode[] modes) {
        return Arrays.stream(modeKeys(modes)).map(I18n::format).toArray(String[]::new);
    }

    public static VSyncMode defaultMode() {
        return Minecraft.getMinecraft().gameSettings.enableVsync ? GLStateManager.preferredTearFreeMode() : VSyncMode.OFF;
    }

    public static VSyncMode storedMode(SodiumGameOptions opts) {
        final VSyncMode stored = opts.advanced.vsyncMode;
        if (stored == null) return defaultMode();
        return GLStateManager.supportsVSyncMode(stored) ? stored : VSyncMode.ON;
    }
}
