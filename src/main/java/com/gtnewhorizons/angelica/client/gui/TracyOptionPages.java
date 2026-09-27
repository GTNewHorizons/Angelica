package com.gtnewhorizons.angelica.client.gui;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.debug.profiling.TracyCaptureNotifier;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.glsm.profiling.TracyBackend;
import com.gtnewhorizons.angelica.glsm.profiling.TracyOptions;
import me.jellysquid.mods.sodium.client.gui.options.ActionOption;
import me.jellysquid.mods.sodium.client.gui.options.OptionFlag;
import me.jellysquid.mods.sodium.client.gui.options.OptionGroup;
import me.jellysquid.mods.sodium.client.gui.options.OptionImpl;
import me.jellysquid.mods.sodium.client.gui.options.OptionPage;
import me.jellysquid.mods.sodium.client.gui.options.control.ControlValueFormatter;
import me.jellysquid.mods.sodium.client.gui.options.control.SliderControl;
import me.jellysquid.mods.sodium.client.gui.options.control.TickBoxControl;
import me.jellysquid.mods.sodium.client.gui.options.storage.AngelicaOptionsStorage;
import net.minecraft.client.resources.I18n;

import java.util.function.Supplier;

public class TracyOptionPages {

    private static final AngelicaOptionsStorage angelicaOpts = new AngelicaOptionsStorage();

    public static OptionPage tracy() {
        final String stopLabel = I18n.format("options.angelica.tracy.captureAction.stop");
        final String savingLabel = I18n.format("options.angelica.tracy.captureAction.saving");
        final String startLabel = I18n.format("options.angelica.tracy.captureAction.start");
        final Supplier<String> captureActionLabel = () -> switch (Tracy.captureState()) {
            case TracyBackend.CAPTURE_CONNECTING, TracyBackend.CAPTURE_RECORDING -> stopLabel;
            case TracyBackend.CAPTURE_SAVING -> savingLabel;
            default -> startLabel;
        };

        final String restartLabel = I18n.format("options.angelica.tracy.status.restart");
        final String runningLabel = I18n.format("options.angelica.tracy.status.running");
        final String runningForcedLabel = I18n.format("options.angelica.tracy.status.runningForced");
        final String failedLabel = I18n.format("options.angelica.tracy.status.failed");
        final String offLabel = I18n.format("options.angelica.tracy.status.off");
        final Supplier<String> statusLabel = () -> {
            if (AngelicaConfig.enableTracy != TracyOptions.configEnabled()
                || AngelicaConfig.tracyAllowRemote != TracyOptions.configRemote()
                || AngelicaConfig.tracyFineZones != TracyOptions.configFineZones()
                || AngelicaConfig.tracyMaxSrcLocs != TracyOptions.configMaxSrcLocs()) {
                return restartLabel;
            }
            if (Tracy.ENABLED) {
                return SystemProperties.TRACY_OVERRIDE != null ? runningForcedLabel : runningLabel;
            }
            return TracyOptions.enabled() ? failedLabel : offLabel;
        };

        return new OptionPage(I18n.format("options.angelica.tracy.page"), ImmutableList.of(
            OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(boolean.class, angelicaOpts)
                        .setName(I18n.format("options.angelica.tracy.enable"))
                        .setTooltip(overrideTooltip("options.angelica.tracy.enable.tooltip", "angelica.tracy", SystemProperties.TRACY_OVERRIDE != null))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> AngelicaConfig.enableTracy = value, opts -> AngelicaConfig.enableTracy)
                        .setEnabled(SystemProperties.TRACY_OVERRIDE == null)
                        .setFlags(OptionFlag.REQUIRES_GAME_RESTART)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, angelicaOpts)
                        .setName(I18n.format("options.angelica.tracy.allowRemote"))
                        .setTooltip(I18n.format("options.angelica.tracy.allowRemote.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> AngelicaConfig.tracyAllowRemote = value, opts -> AngelicaConfig.tracyAllowRemote)
                        .setFlags(OptionFlag.REQUIRES_GAME_RESTART)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, angelicaOpts)
                        .setName(I18n.format("options.angelica.tracy.fineZones"))
                        .setTooltip(overrideTooltip("options.angelica.tracy.fineZones.tooltip", "angelica.tracy.fineZones", SystemProperties.TRACY_FINE_ZONES_OVERRIDE != null))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> AngelicaConfig.tracyFineZones = value, opts -> AngelicaConfig.tracyFineZones)
                        .setEnabled(SystemProperties.TRACY_FINE_ZONES_OVERRIDE == null)
                        .setFlags(OptionFlag.REQUIRES_GAME_RESTART)
                        .build())
                .add(OptionImpl.createBuilder(int.class, angelicaOpts)
                        .setName(I18n.format("options.angelica.tracy.maxSrcLocs"))
                        .setTooltip(overrideTooltip("options.angelica.tracy.maxSrcLocs.tooltip", "angelica.tracy.maxSrcLocs", SystemProperties.TRACY_MAX_SRC_LOCS_OVERRIDE != null))
                        .setControl(o -> new SliderControl(o, 1024, 16384, 1024, ControlValueFormatter.number()))
                        .setBinding((opts, value) -> AngelicaConfig.tracyMaxSrcLocs = value, opts -> AngelicaConfig.tracyMaxSrcLocs)
                        .setEnabled(SystemProperties.TRACY_MAX_SRC_LOCS_OVERRIDE == null)
                        .setFlags(OptionFlag.REQUIRES_GAME_RESTART)
                        .build())
                .build(),
            OptionGroup.createBuilder()
                .add(new ActionOption(I18n.format("options.angelica.tracy.status"), I18n.format("options.angelica.tracy.status.tooltip"),
                        statusLabel, () -> {}, () -> false))
                .build(),
            OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(int.class, angelicaOpts)
                        .setName(I18n.format("options.angelica.tracy.captureLength"))
                        .setTooltip(I18n.format("options.angelica.tracy.captureLength.tooltip"))
                        .setControl(o -> new SliderControl(o, 10, 300, 10, ControlValueFormatter.seconds()))
                        .setBinding((opts, value) -> AngelicaConfig.tracyCaptureSeconds = value, opts -> AngelicaConfig.tracyCaptureSeconds)
                        .setEnabled(Tracy.ENABLED)
                        .build())
                .add(new ActionOption(I18n.format("options.angelica.tracy.captureAction"), I18n.format("options.angelica.tracy.captureAction.tooltip"),
                        captureActionLabel, TracyCaptureNotifier.INSTANCE::toggle, () -> Tracy.ENABLED))
                .build()));
    }

    private static String overrideTooltip(String baseKey, String flag, boolean overridden) {
        final String base = I18n.format(baseKey);
        return overridden ? base + " " + I18n.format("options.angelica.tracy.overriddenBy", flag) : base;
    }
}
