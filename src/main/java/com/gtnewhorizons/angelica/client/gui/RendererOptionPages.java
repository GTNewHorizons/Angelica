package com.gtnewhorizons.angelica.client.gui;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.config.GLVersionChoice;
import com.gtnewhorizons.angelica.config.RenderBackendChoice;
import com.gtnewhorizons.angelica.config.SdlGpuDriver;
import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.backend.BackendOptions;
import com.gtnewhorizons.angelica.glsm.backend.BackendStartGuard;
import com.gtnewhorizons.angelica.glsm.backend.MoltenVK;
import com.gtnewhorizons.angelica.sdlgpu.SDLGPUGate;
import me.flashyreese.mods.reeses_sodium_options.client.gui.ReeseSodiumVideoOptionsScreen;
import me.jellysquid.mods.sodium.client.gui.options.ActionOption;
import me.jellysquid.mods.sodium.client.gui.options.OptionFlag;
import me.jellysquid.mods.sodium.client.gui.options.OptionGroup;
import me.jellysquid.mods.sodium.client.gui.options.OptionImpl;
import me.jellysquid.mods.sodium.client.gui.options.OptionPage;
import me.jellysquid.mods.sodium.client.gui.options.control.CyclingControl;
import me.jellysquid.mods.sodium.client.gui.options.storage.AngelicaOptionsStorage;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class RendererOptionPages {

    private static final AngelicaOptionsStorage angelicaOpts = new AngelicaOptionsStorage();

    public static OptionPage renderer() {
        final Util.EnumOS os = Util.getOSType();
        final boolean sdlAvailable = SDLGPUGate.isSDLGPUAvailable();
        final RenderBackendChoice[] backends = sdlAvailable ? RenderBackendChoice.values() : new RenderBackendChoice[] { RenderBackendChoice.OPENGL };
        final SdlGpuDriver[] drivers = allowedDrivers(os);
        final GLVersionChoice[] glVersions = allowedGlVersions(glVersionCeiling(os));
        final String active = activeLabel();

        final OptionGroup.Builder status = OptionGroup.createBuilder()
            .add(ActionOption.label(I18n.format("options.angelica.renderer.status"), I18n.format("options.angelica.renderer.status.tooltip"), statusLabel()))
            .add(ActionOption.label(I18n.format("options.angelica.renderer.active"), I18n.format("options.angelica.renderer.active.tooltip"), () -> active));
        if (os == Util.EnumOS.OSX) {
            final String moltenVk = I18n.format("options.angelica.renderer.moltenvk." + MoltenVK.source().name().toLowerCase());
            status.add(ActionOption.label(I18n.format("options.angelica.renderer.moltenvk"), I18n.format("options.angelica.renderer.moltenvk.tooltip"), () -> moltenVk));
        }

        return new OptionPage(I18n.format("options.angelica.renderer.page"), ImmutableList.of(
            OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(RenderBackendChoice.class, angelicaOpts)
                        .setName(I18n.format("options.angelica.renderer.backend"))
                        .setTooltip(overrideTooltip(backendTooltip(sdlAvailable), SystemProperties.KEY_USE_SDL_GPU, SystemProperties.SDL_GPU_OVERRIDE != null))
                        .setControl(o -> new CyclingControl<>(o, RenderBackendChoice.class, backends))
                        .setBinding((opts, value) -> AngelicaConfig.renderBackend = value, opts -> backendOf(SystemProperties.SDL_GPU_OVERRIDE != null ? SystemProperties.SDL_GPU_OVERRIDE : AngelicaConfig.sdlGpuConfigured()))
                        .setEnabled(SystemProperties.SDL_GPU_OVERRIDE == null)
                        .setFlags(OptionFlag.REQUIRES_GAME_RESTART)
                        .build())
                .add(OptionImpl.createBuilder(SdlGpuDriver.class, angelicaOpts)
                        .setName(I18n.format("options.angelica.renderer.driver"))
                        .setTooltip(overrideTooltip(driverTooltip(os), SystemProperties.KEY_SDL_GPU_DRIVER, SystemProperties.SDL_GPU_DRIVER_OVERRIDE != null))
                        .setControl(o -> new CyclingControl<>(o, SdlGpuDriver.class, drivers))
                        .setBinding((opts, value) -> AngelicaConfig.sdlGpuDriver = value, opts -> shownDriver())
                        .setEnabled(SystemProperties.SDL_GPU_DRIVER_OVERRIDE == null)
                        .setFlags(OptionFlag.REQUIRES_GAME_RESTART)
                        .build())
                .add(OptionImpl.createBuilder(GLVersionChoice.class, angelicaOpts)
                        .setName(I18n.format("options.angelica.renderer.glVersion"))
                        .setTooltip(I18n.format("options.angelica.renderer.glVersion.tooltip"))
                        .setControl(o -> new CyclingControl<>(o, GLVersionChoice.class, glVersions, glVersionNames()))
                        .setBinding((opts, value) -> AngelicaConfig.glVersion = value.version(), opts -> GLVersionChoice.of(AngelicaConfig.glVersion, GLVersionChoice.GL46.version()))
                        .setFlags(OptionFlag.REQUIRES_GAME_RESTART)
                        .build())
                .build(),
            status.build()));
    }

    public static OptionPage withLink(OptionPage general, ReeseSodiumVideoOptionsScreen gui) {
        final String rendererPage = I18n.format("options.angelica.renderer.page");
        final String active = activeLabel();
        return new OptionPage(general.getName(), ImmutableList.<OptionGroup>builder()
            .add(OptionGroup.createBuilder()
                .add(new ActionOption(I18n.format("options.angelica.renderer.link"), I18n.format("options.angelica.renderer.link.tooltip"),
                        () -> active, () -> gui.showPage(rendererPage), () -> true))
                .build())
            .addAll(general.getGroups())
            .build());
    }

    private static RenderBackendChoice backendOf(boolean sdlGpu) {
        return sdlGpu ? RenderBackendChoice.SDL_GPU : RenderBackendChoice.OPENGL;
    }

    private static SdlGpuDriver shownDriver() {
        final SdlGpuDriver forced = SystemProperties.SDL_GPU_DRIVER_OVERRIDE != null ? SdlGpuDriver.fromSdlName(SystemProperties.SDL_GPU_DRIVER_OVERRIDE) : null;
        if (forced != null) return forced;
        return AngelicaConfig.sdlGpuDriver != null ? AngelicaConfig.sdlGpuDriver : SdlGpuDriver.AUTO;
    }

    static SdlGpuDriver[] allowedDrivers(Util.EnumOS os) {
        return switch (os) {
            case OSX -> new SdlGpuDriver[] { SdlGpuDriver.AUTO, SdlGpuDriver.METAL, SdlGpuDriver.VULKAN };
            case WINDOWS -> new SdlGpuDriver[] { SdlGpuDriver.AUTO, SdlGpuDriver.VULKAN, SdlGpuDriver.D3D12 };
            default -> new SdlGpuDriver[] { SdlGpuDriver.AUTO, SdlGpuDriver.VULKAN };
        };
    }

    private static int glVersionCeiling(Util.EnumOS os) {
        final int platformMax = os == Util.EnumOS.OSX ? 41 : 46;
        final int pinned = AngelicaConfig.pinnedGLVersion;
        return pinned >= 33 && pinned < platformMax ? pinned : platformMax;
    }

    static GLVersionChoice[] allowedGlVersions(int ceiling) {
        final List<GLVersionChoice> allowed = new ArrayList<>();
        for (final GLVersionChoice choice : GLVersionChoice.values()) {
            if (choice.version() <= ceiling) allowed.add(choice);
        }
        return allowed.toArray(new GLVersionChoice[0]);
    }

    private static String[] glVersionNames() {
        final GLVersionChoice[] universe = GLVersionChoice.values();
        final String[] names = new String[universe.length];
        for (int i = 0; i < universe.length; i++) {
            names[i] = universe[i] == GLVersionChoice.AUTO ? I18n.format("options.angelica.renderer.glVersion.auto") : universe[i].label();
        }
        return names;
    }

    private static String backendTooltip(boolean sdlAvailable) {
        final String base = EnumChatFormatting.RED + I18n.format("options.angelica.renderer.backend.experimental") + EnumChatFormatting.RESET + " " + I18n.format("options.angelica.renderer.backend.tooltip");
        return sdlAvailable ? base : base + " " + I18n.format("options.angelica.renderer.backend.unavailable", SDLGPUGate.missingRequirement());
    }

    private static String driverTooltip(Util.EnumOS os) {
        final String base = I18n.format("options.angelica.renderer.driver.tooltip");
        return switch (os) {
            case OSX -> base + " " + I18n.format("options.angelica.renderer.driver.tooltip.macos");
            case WINDOWS -> base + " " + I18n.format("options.angelica.renderer.driver.tooltip.windows") + " " + EnumChatFormatting.RED + I18n.format("options.angelica.renderer.driver.tooltip.d3d12") + EnumChatFormatting.RESET;
            default -> base + " " + I18n.format("options.angelica.renderer.driver.tooltip.other");
        };
    }

    private static Supplier<String> statusLabel() {
        final String restartLabel = I18n.format("options.angelica.status.restart");
        final String forcedLabel = I18n.format("options.angelica.renderer.status.forced");
        final String failedLabel = I18n.format("options.angelica.renderer.status.failed");
        final String activeLabel = I18n.format("options.angelica.renderer.status.active");
        final String resetLabel = I18n.format("options.angelica.renderer.status.reset");
        return () -> {
            if (AngelicaConfig.sdlGpuConfigured() != BackendOptions.configSdlGpu()
                || !AngelicaConfig.sdlGpuDriverName().equals(BackendOptions.configDriver())
                || AngelicaConfig.glVersion != BackendOptions.configGlVersion()) {
                return restartLabel;
            }
            if (BackendStartGuard.tripped() && !AngelicaConfig.sdlGpuConfigured()) return resetLabel;
            if (BackendOptions.sdlGpuRequested() && !BackendManager.RENDER_BACKEND.isSDLGPU()) return failedLabel;
            return SystemProperties.SDL_GPU_OVERRIDE != null || SystemProperties.SDL_GPU_DRIVER_OVERRIDE != null ? forcedLabel : activeLabel;
        };
    }

    private static String activeLabel() {
        final String name = GLStateManager.getRenderBackendName();
        return BackendManager.RENDER_BACKEND.isSDLGPU() ? name : name + " " + RenderSystem.getGlMajor() + "." + RenderSystem.getGlMinor();
    }

    static String overrideTooltip(String base, String flag, boolean overridden) {
        return overridden ? base + " " + I18n.format("options.angelica.overriddenBy", flag) : base;
    }
}
