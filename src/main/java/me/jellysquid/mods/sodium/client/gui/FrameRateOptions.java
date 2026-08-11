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

public record FrameRateOptions(Option<VSyncMode> vsync, Option<Integer> maxFramerate) {

    public static FrameRateOptions create(MinecraftOptionsStorage vanillaOpts, SodiumOptionsStorage sodiumOpts) {
        final OptionImpl<SodiumGameOptions, VSyncMode> vsync = OptionImpl.createBuilder(VSyncMode.class, sodiumOpts)
            .setName(I18n.format("options.vsync"))
            .setTooltip(I18n.format("sodium.options.v_sync.tooltip"))
            .setControl(option -> new CyclingControl<>(option, VSyncMode.class,
                GLStateManager.getSelectableVSyncModes().toArray(new VSyncMode[0]),
                new String[]{
                    I18n.format("sodium.options.vsync_mode.auto"),
                    I18n.format("sodium.options.vsync_mode.on"),
                    I18n.format("sodium.options.vsync_mode.mailbox"),
                    I18n.format("sodium.options.vsync_mode.off")}))
            .setBinding((opts, value) -> {
                opts.advanced.vsyncMode = value;

                final GameSettings settings = Minecraft.getMinecraft().gameSettings;
                settings.enableVsync = value.tearFree();
                settings.saveOptions();
                GLStateManager.setVSyncMode(value, settings.enableVsync);
            }, opts -> opts.advanced.vsyncMode.resolve(Minecraft.getMinecraft().gameSettings.enableVsync))
            .setImpact(OptionImpact.VARIES)
            .build();

        final OptionImpl<GameSettings, Integer> maxFramerate = OptionImpl.createBuilder(int.class, vanillaOpts)
            .setName(I18n.format("options.framerateLimit"))
            .setTooltip(I18n.format("sodium.options.fps_limit.tooltip"))
            .setControl(option -> new SliderControl(option, 5, 260, 5, ControlValueFormatter.fpsLimit()))
            .setBinding((opts, value) -> opts.limitFramerate = value, opts -> opts.limitFramerate)
            .build();

        maxFramerate.iris$dynamicallyEnable(() -> !vsync.getValue().blocksOnVBlank());

        return new FrameRateOptions(vsync, maxFramerate);
    }
}
