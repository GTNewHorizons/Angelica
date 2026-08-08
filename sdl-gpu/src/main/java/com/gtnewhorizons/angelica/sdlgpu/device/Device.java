package com.gtnewhorizons.angelica.sdlgpu.device;

import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.sdlgpu.SDLGPUGate;
import com.gtnewhorizons.angelica.sdlgpu.util.DebugMessageRelay;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLLog;
import org.lwjgl.sdl.SDLProperties;
import org.lwjgl.sdl.SDLStdinc;
import org.lwjgl.sdl.SDL_DisplayMode;
import org.lwjgl.sdl.SDL_LogOutputFunction;
import org.lwjgl.sdl.SDLVersion;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Platform;
import org.lwjglx.opengl.Display;

import java.nio.IntBuffer;

import static org.lwjgl.sdl.SDLGPU.*;
import static org.lwjgl.sdl.SDLHints.SDL_HINT_GPU_DRIVER;
import static org.lwjgl.sdl.SDLHints.SDL_SetHint;
import static org.lwjgl.sdl.SDLVideo.*;
import static org.lwjgl.system.MemoryUtil.memUTF8;

public final class Device {
    private static final Logger LOG = LogManager.getLogger("Angelica-SDLGPU");

    public static final int FRAMES_IN_FLIGHT = 3;

    private long device;
    private int supportedShaderFormats;
    private String driverName;
    private String deviceName;
    private String driverVersion;
    private String driverInfo;
    private int presentMode = SDL_GPU_PRESENTMODE_VSYNC;
    private SDL_LogOutputFunction logCallback; // prevent GC

    // Default to vulkan for now until D3D is more tested
    static String resolveDriverName(String userHint, boolean windows) {
        return userHint.isEmpty() && windows ? "vulkan" : null;
    }

    public void claimWindow(long window, boolean lwjglDebug) {
        installLogCallback();

        final int ver = SDLVersion.SDL_GetVersion();
        LOG.info("SDL runtime version {}.{}.{} (raw {})", SDLVersion.SDL_VERSIONNUM_MAJOR(ver), SDLVersion.SDL_VERSIONNUM_MINOR(ver), SDLVersion.SDL_VERSIONNUM_MICRO(ver), ver);

        final String driverHint = SystemProperties.SDL_GPU_DRIVER;
        if (!driverHint.isEmpty()) {
            SDL_SetHint(SDL_HINT_GPU_DRIVER, driverHint);
            LOG.info("SDL GPU driver hint set to '{}'", driverHint);
        }

        final int requestedFormats = SDL_GPU_SHADERFORMAT_SPIRV | SDL_GPU_SHADERFORMAT_MSL | SDL_GPU_SHADERFORMAT_DXBC | SDL_GPU_SHADERFORMAT_DXIL;

        final boolean gpuDebug = lwjglDebug || SystemProperties.SDL_GPU_DEBUG;
        final String forcedDriver = resolveDriverName(driverHint, Platform.get() == Platform.WINDOWS);
        device = SDL_CreateGPUDevice(requestedFormats, gpuDebug, forcedDriver);
        if (device == 0 && forcedDriver != null) {
            LOG.warn("Failed to create a '{}' SDL GPU device ({}); falling back to SDL's default backend. Set -D{} to pin one.", forcedDriver, SDLError.SDL_GetError(), SystemProperties.KEY_SDL_GPU_DRIVER);
            device = SDL_CreateGPUDevice(requestedFormats, gpuDebug, (CharSequence) null);
        }
        if (device == 0) {
            throw new RuntimeException("Failed to create SDL GPU device: " + SDLError.SDL_GetError());
        }

        driverName = SDL_GetGPUDeviceDriver(device);
        supportedShaderFormats = SDL_GetGPUShaderFormats(device);

        final int props = SDL_GetGPUDeviceProperties(device);
        deviceName = SDLProperties.SDL_GetStringProperty(props, SDL_PROP_GPU_DEVICE_NAME_STRING, "Unknown GPU");
        driverVersion = SDLProperties.SDL_GetStringProperty(props, SDL_PROP_GPU_DEVICE_DRIVER_VERSION_STRING, "");
        driverInfo = SDLProperties.SDL_GetStringProperty(props, SDL_PROP_GPU_DEVICE_DRIVER_INFO_STRING, "");

        LOG.info("SDL GPU device created: gpu={}, driver={} {}, formats=0x{}, debug={}, forcedDriver={}", deviceName, driverName, driverVersion, Integer.toHexString(supportedShaderFormats), gpuDebug, forcedDriver);

        if ("metal".equalsIgnoreCase(driverName) && ver < SDLVersion.SDL_VERSIONNUM(3, 4, 6)) {
            LOG.fatal("SDL GPU on Metal requires SDL 3.4.6 or newer; exiting");
            SDLGPUGate.markFatalInit();
            return;
        }

        if (window == 0) {
            throw new RuntimeException("SDL window not available for GPU device claim");
        }

        if (!SDL_ClaimWindowForGPUDevice(device, window)) {
            throw new RuntimeException("Failed to claim window for SDL GPU device: " + SDLError.SDL_GetError());
        }

        final int n = FRAMES_IN_FLIGHT;
        if (!SDL_SetGPUAllowedFramesInFlight(device, n)) {
            LOG.warn("SDL_SetGPUAllowedFramesInFlight({}) failed: {}", n, SDLError.SDL_GetError());
        } else {
            LOG.info("SDL frames-in-flight set to {}", n);
        }

        logWindowDiagnostics(window);
        LOG.info("SDL GPU device claimed window successfully");
    }

    private void logWindowDiagnostics(long window) {
        final long flags = SDL_GetWindowFlags(window);
        final boolean hasMetal = (flags & SDL_WINDOW_METAL) != 0;
        final boolean hasVulkan = (flags & SDL_WINDOW_VULKAN) != 0;
        final boolean hasOpenGL = (flags & SDL_WINDOW_OPENGL) != 0;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final IntBuffer pW = stack.ints(0);
            final IntBuffer pH = stack.ints(0);
            SDL_GetWindowSize(window, pW, pH);
            final int wPoints = pW.get(0);
            final int hPoints = pH.get(0);
            pW.put(0, 0);
            pH.put(0, 0);
            SDL_GetWindowSizeInPixels(window, pW, pH);
            final int wPix = pW.get(0);
            final int hPix = pH.get(0);
            final int swapFmt = SDL_GetGPUSwapchainTextureFormat(device, window);
            LOG.info("SDL window flags=0x{} (metal={}, vulkan={}, opengl={}) size points={}x{} pixels={}x{} swapchainFormat=0x{}",
                Long.toHexString(flags), hasMetal, hasVulkan, hasOpenGL, wPoints, hPoints, wPix, hPix, Integer.toHexString(swapFmt));
        }
    }

    public int[] getMaxDesktopSizePixels() {
        int maxW = 0;
        int maxH = 0;
        final IntBuffer displays = SDL_GetDisplays();
        if (displays == null) return new int[]{0, 0};
        try {
            for (int i = 0; i < displays.remaining(); i++) {
                final SDL_DisplayMode mode = SDL_GetDesktopDisplayMode(displays.get(displays.position() + i));
                if (mode == null) continue;
                maxW = Math.max(maxW, Math.round(mode.w() * mode.pixel_density()));
                maxH = Math.max(maxH, Math.round(mode.h() * mode.pixel_density()));
            }
        } finally {
            SDLStdinc.nSDL_free(MemoryUtil.memAddress(displays));
        }
        return new int[]{maxW, maxH};
    }

    public void shutdown() {
        if (device != 0) {
            final long window = Display.getWindow();
            if (window != 0) {
                SDL_ReleaseWindowFromGPUDevice(device, window);
            }
            SDL_DestroyGPUDevice(device);
            device = 0;
            LOG.info("SDL GPU device destroyed");
        }
    }

    public long getDevice() {
        return device;
    }

    public String getDriverName() {
        return driverName;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public String getDriverVersion() {
        return driverVersion;
    }

    public String getDriverInfo() {
        return driverInfo;
    }

    public boolean supportsSpirv() {
        return (supportedShaderFormats & SDL_GPU_SHADERFORMAT_SPIRV) != 0;
    }

    public boolean supportsMsl() {
        return (supportedShaderFormats & SDL_GPU_SHADERFORMAT_MSL) != 0;
    }

    public boolean supportsDxil() {
        return (supportedShaderFormats & SDL_GPU_SHADERFORMAT_DXIL) != 0;
    }

    public boolean supportsDxbc() {
        return (supportedShaderFormats & SDL_GPU_SHADERFORMAT_DXBC) != 0;
    }

    public void setVSyncEnabled(boolean enabled) {
        final long window = Display.getWindow();
        final int mode;
        if (enabled) {
            mode = SDL_GPU_PRESENTMODE_VSYNC;
        } else if (SDL_WindowSupportsGPUPresentMode(device, window, SDL_GPU_PRESENTMODE_MAILBOX)) {
            mode = SDL_GPU_PRESENTMODE_MAILBOX;
        } else {
            mode = SDL_GPU_PRESENTMODE_IMMEDIATE;
        }
        if (!SDL_SetGPUSwapchainParameters(device, window, SDL_GPU_SWAPCHAINCOMPOSITION_SDR, mode)) {
            LOG.warn("Failed to set present mode {}: {} (keeping prior mode {})", mode, SDLError.SDL_GetError(), presentMode);
            return;
        }
        presentMode = mode;
        LOG.info("SDL present mode set to {}", mode);
    }

    public int getSwapchainTextureFormat() {
        final long window = Display.getWindow();
        return SDL_GetGPUSwapchainTextureFormat(device, window);
    }

    private void installLogCallback() {
        if (logCallback != null) return;
        logCallback = SDL_LogOutputFunction.create((userdata, category, priority, message) -> {
            final String msg = memUTF8(message);
            switch (priority) {
                case SDLLog.SDL_LOG_PRIORITY_ERROR, SDLLog.SDL_LOG_PRIORITY_CRITICAL -> LOG.error("[SDL] {}", msg);
                case SDLLog.SDL_LOG_PRIORITY_WARN -> LOG.warn("[SDL] {}", msg);
                case SDLLog.SDL_LOG_PRIORITY_INFO -> LOG.info("[SDL] {}", msg);
                default -> LOG.debug("[SDL] {}", msg);
            }
            DebugMessageRelay.onSdlMessage(priority, message);
        });
        SDLLog.SDL_SetLogOutputFunction(logCallback, 0);
        SDLLog.SDL_SetLogPriorities(SDLLog.SDL_LOG_PRIORITY_VERBOSE);
    }
}
