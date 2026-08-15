package com.gtnewhorizons.angelica.sdlgpu.device;

import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.glsm.backend.VSyncMode;
import com.gtnewhorizons.angelica.glsm.backend.RenderBackend;
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
import org.lwjgl.sdl.SDLVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Platform;
import org.lwjglx.opengl.Display;

import java.nio.IntBuffer;
import java.util.List;
import java.util.function.Supplier;

import static org.lwjgl.sdl.SDLGPU.*;
import static org.lwjgl.sdl.SDLHints.SDL_HINT_GPU_DRIVER;
import static org.lwjgl.sdl.SDLHints.SDL_SetHint;
import static org.lwjgl.sdl.SDLVideo.*;
import static org.lwjgl.system.MemoryUtil.memUTF8;

public final class Device {
    private static final Logger LOG = LogManager.getLogger("Angelica-SDLGPU");

    public static final int MAX_FRAMES_IN_FLIGHT = 3;

    private static final int FRAMES_IN_FLIGHT = Math.min(Math.max(SystemProperties.SDL_FRAMES_IN_FLIGHT, 1), MAX_FRAMES_IN_FLIGHT);

    public static int framesInFlight() { return FRAMES_IN_FLIGHT; }

    private static final int REQUESTED_SHADER_FORMATS = SDL_GPU_SHADERFORMAT_SPIRV | SDL_GPU_SHADERFORMAT_MSL | SDL_GPU_SHADERFORMAT_DXBC | SDL_GPU_SHADERFORMAT_DXIL;

    private long device;
    private boolean claimed;
    private int supportedShaderFormats;
    private String driverName;
    private String deviceName;
    private String driverVersion;
    private String driverInfo;
    private VSyncMode vsyncMode = VSyncMode.ON;
    private volatile Thread windowThread;
    private SDL_LogOutputFunction logCallback; // prevent GC

    private volatile boolean lost;
    private volatile Supplier<String> lossDiagnostics = () -> "";

    public boolean isLost() { return lost; }

    public void setLossDiagnostics(Supplier<String> diagnostics) { this.lossDiagnostics = diagnostics; }

    public static boolean isDeviceLossError(String sdlError) {
        if (sdlError == null) return false;
        return sdlError.contains("DEVICE_LOST") || sdlError.contains("DEVICE_REMOVED") || sdlError.contains("DEVICE_RESET");
    }

    public void reportGpuFailure(String operation) {
        if (lost) return;
        final String err = SDLError.SDL_GetError();
        if (!isDeviceLossError(err)) {
            LOG.error("{}: {}", operation, err);
            return;
        }
        lost = true;
        LOG.error("GPU DEVICE LOST during {}: {}", operation, err);
        LOG.error("  gpu={} driver={} {} sdl={}", deviceName, driverName, driverVersion, SDLVersion.SDL_GetVersion());
        for (final String line : lossDiagnostics.get().split("\n")) {
            LOG.error("  {}", line);
        }
        throw new GpuDeviceLostException(operation, err);
    }

    // Default to vulkan for now until D3D is more tested
    static String resolveDriverName(String userHint, boolean windows) {
        return userHint.isEmpty() && windows ? "vulkan" : null;
    }

    static boolean metalNeedsNewerSdl(String driverName, int sdlVersion) {
        return "metal".equalsIgnoreCase(driverName) && sdlVersion < SDLVersion.SDL_VERSIONNUM(3, 4, 6);
    }

    public boolean createDevice() {
        if (device != 0) return true;

        installLogCallback();

        final int ver = SDLVersion.SDL_GetVersion();
        LOG.info("SDL runtime version {}.{}.{} (raw {})", SDLVersion.SDL_VERSIONNUM_MAJOR(ver), SDLVersion.SDL_VERSIONNUM_MINOR(ver), SDLVersion.SDL_VERSIONNUM_MICRO(ver), ver);

        final String driverHint = SystemProperties.SDL_GPU_DRIVER;
        if (!driverHint.isEmpty()) {
            SDL_SetHint(SDL_HINT_GPU_DRIVER, driverHint);
            LOG.info("SDL GPU driver hint set to '{}'", driverHint);
        }

        final boolean gpuDebug = SystemProperties.LWJGL_DEBUG || SystemProperties.SDL_GPU_DEBUG;
        final String forcedDriver = resolveDriverName(driverHint, Platform.get() == Platform.WINDOWS);
        device = createGPUDevice(gpuDebug, forcedDriver);
        if (device == 0 && VideoDriverRecovery.shouldRetryOnX11(SDL_GetCurrentVideoDriver(), vulkanLoaderAvailable(), System.getenv("DISPLAY"))) {
            device = VideoDriverRecovery.retryUnderX11(() -> createGPUDevice(gpuDebug, forcedDriver));
        }
        if (device == 0) {
            logDeviceCreationFailure(REQUESTED_SHADER_FORMATS);
            return false;
        }

        driverName = SDL_GetGPUDeviceDriver(device);
        supportedShaderFormats = SDL_GetGPUShaderFormats(device);

        final int props = SDL_GetGPUDeviceProperties(device);
        deviceName = SDLProperties.SDL_GetStringProperty(props, SDL_PROP_GPU_DEVICE_NAME_STRING, "Unknown GPU");
        driverVersion = SDLProperties.SDL_GetStringProperty(props, SDL_PROP_GPU_DEVICE_DRIVER_VERSION_STRING, "");
        driverInfo = SDLProperties.SDL_GetStringProperty(props, SDL_PROP_GPU_DEVICE_DRIVER_INFO_STRING, "");

        LOG.info("SDL GPU device created: gpu={}, driver={} {}, video={}, formats=0x{}, debug={}, forcedDriver={}", deviceName, driverName, driverVersion, SDL_GetCurrentVideoDriver(), Integer.toHexString(supportedShaderFormats), gpuDebug, forcedDriver);

        if (metalNeedsNewerSdl(driverName, ver)) {
            LOG.error("SDL GPU on Metal requires SDL 3.4.6 or newer (have {}.{}.{}); falling back to OpenGL", SDLVersion.SDL_VERSIONNUM_MAJOR(ver), SDLVersion.SDL_VERSIONNUM_MINOR(ver), SDLVersion.SDL_VERSIONNUM_MICRO(ver));
            destroyDevice();
            return false;
        }

        return true;
    }

    private long createGPUDevice(boolean gpuDebug, String forcedDriver) {
        long dev = SDL_CreateGPUDevice(REQUESTED_SHADER_FORMATS, gpuDebug, forcedDriver);
        if (dev == 0 && forcedDriver != null) {
            LOG.warn("Failed to create a '{}' SDL GPU device ({}); falling back to SDL's default backend. Set -D{} to pin one.", forcedDriver, SDLError.SDL_GetError(), SystemProperties.KEY_SDL_GPU_DRIVER);
            dev = SDL_CreateGPUDevice(REQUESTED_SHADER_FORMATS, gpuDebug, (CharSequence) null);
        }
        return dev;
    }

    public void claimWindow(long window) {
        if (device == 0) {
            throw new IllegalStateException("SDL GPU device not created");
        }

        if (window == 0) {
            throw new RuntimeException("SDL window not available for GPU device claim");
        }

        if (!SDL_ClaimWindowForGPUDevice(device, window)) {
            throw new RuntimeException("Failed to claim window for SDL GPU device: " + SDLError.SDL_GetError());
        }
        claimed = true;
        windowThread = Thread.currentThread();

        if (!SDL_SetGPUAllowedFramesInFlight(device, FRAMES_IN_FLIGHT)) {
            LOG.warn("SDL_SetGPUAllowedFramesInFlight({}) failed: {}", FRAMES_IN_FLIGHT, SDLError.SDL_GetError());
        } else {
            LOG.info("SDL frames-in-flight set to {}", FRAMES_IN_FLIGHT);
        }

        logWindowDiagnostics(window);
        LOG.info("SDL GPU device claimed window successfully");
    }

    private static boolean vulkanLoaderAvailable() {
        if (Platform.get() == Platform.MACOSX) return false;
        if (!SDLVulkan.SDL_Vulkan_LoadLibrary((CharSequence) null)) return false;
        SDLVulkan.SDL_Vulkan_UnloadLibrary();
        return true;
    }

    private static void logDeviceCreationFailure(int requestedFormats) {
        final StringBuilder drivers = new StringBuilder();
        final int n = SDL_GetNumGPUDrivers();
        for (int i = 0; i < n; i++) {
            if (i > 0) drivers.append(", ");
            drivers.append(SDL_GetGPUDriver(i));
        }
        final String videoDriver = SDL_GetCurrentVideoDriver();
        LOG.error("SDL GPU device creation failed. platform={}, videoDriver={}, requestedShaderFormats=0x{}, compiledGpuDrivers=[{}]", Platform.get(), videoDriver, Integer.toHexString(requestedFormats), drivers);

        if (Platform.get() != Platform.MACOSX) {
            if (SDLVulkan.SDL_Vulkan_LoadLibrary((CharSequence) null)) {
                SDLVulkan.SDL_Vulkan_UnloadLibrary();
                LOG.error("A Vulkan loader is present, so no GPU met SDL's requirements");
            } else {
                LOG.error("No usable Vulkan loader: {}", SDLError.SDL_GetError());
                if (!"x11".equalsIgnoreCase(videoDriver)) {
                    LOG.error("Retrying under SDL_VIDEODRIVER=x11 did not help either.");
                }
            }
        }
        LOG.error("Continuing on OpenGL. Set -D{}=false to skip this probe entirely.", SystemProperties.KEY_USE_SDL_GPU);
    }

    public void destroyDevice() {
        if (device == 0) return;
        if (claimed) {
            final long window = Display.getWindow();
            if (window != 0) SDL_ReleaseWindowFromGPUDevice(device, window);
            claimed = false;
        }
        SDL_DestroyGPUDevice(device);
        device = 0;
        driverName = null;
        deviceName = null;
        driverVersion = null;
        driverInfo = null;
        supportedShaderFormats = 0;
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
            destroyDevice();
            LOG.info("SDL GPU device destroyed");
        }
    }

    public long getDevice() {
        return device;
    }

    public String getDriverName() {
        return driverName;
    }

    public Thread getWindowThread() {
        return windowThread;
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

    private static final List<VSyncMode> ON_ORDER = List.of(VSyncMode.ON);
    private static final List<VSyncMode> OFF_ORDER = List.of(VSyncMode.OFF, VSyncMode.ON);
    private static final List<VSyncMode> MAILBOX_ORDER = List.of(VSyncMode.MAILBOX, VSyncMode.ON);

    private static List<VSyncMode> presentModeOrder(VSyncMode preferred) {
        return switch (preferred) {
            case MAILBOX -> MAILBOX_ORDER;
            case OFF -> OFF_ORDER;
            default -> ON_ORDER;
        };
    }

    public VSyncMode chooseVSyncMode(VSyncMode preferred) {
        for (VSyncMode candidate : presentModeOrder(preferred)) {
            if (supportsVSyncMode(candidate)) return candidate;
        }
        return vsyncMode;
    }

    public VSyncMode applyVSync(VSyncMode preferred) {
        if (device == 0 || !claimed) return vsyncMode;
        final long window = Display.getWindow();
        final List<VSyncMode> order = presentModeOrder(preferred);
        for (VSyncMode candidate : order) {
            final int sdlMode = toSdl(candidate);
            if (!SDL_WindowSupportsGPUPresentMode(device, window, sdlMode)) continue;
            if (!SDL_SetGPUSwapchainParameters(device, window, SDL_GPU_SWAPCHAINCOMPOSITION_SDR, sdlMode)) {
                LOG.warn("Failed to set present mode {}: {}", candidate, SDLError.SDL_GetError());
                continue;
            }
            if (candidate != order.get(0)) {
                LOG.warn("Present mode {} is not supported by this window; using {} instead", order.get(0), candidate);
            }
            vsyncMode = candidate;
            LOG.info("SDL present mode {} (preferred={}), refresh {}Hz, {}, video={}, framesInFlight={}, supported: vsync={} immediate={} mailbox={}",
                candidate, preferred, getDisplayRefreshRateHz(),
                (SDL_GetWindowFlags(window) & SDL_WINDOW_FULLSCREEN) != 0 ? "fullscreen" : "windowed",
                SDL_GetCurrentVideoDriver(), FRAMES_IN_FLIGHT,
                supportsVSyncMode(VSyncMode.ON), supportsVSyncMode(VSyncMode.OFF), supportsVSyncMode(VSyncMode.MAILBOX));
            return candidate;
        }
        LOG.warn("No usable present mode for preference {}; keeping {}", preferred, vsyncMode);
        return vsyncMode;
    }

    public boolean supportsVSyncMode(VSyncMode mode) {
        return device != 0 && claimed && SDL_WindowSupportsGPUPresentMode(device, Display.getWindow(), toSdl(mode));
    }

    public long getClaimedWindow() {
        return device != 0 && claimed ? Display.getWindow() : 0L;
    }

    public boolean wasWindowResized() {
        return getClaimedWindow() != 0L && Display.wasResized();
    }

    public long getWindowSizeInPixels() {
        final long window = getClaimedWindow();
        if (window == 0) return 0L;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final IntBuffer w = stack.mallocInt(1);
            final IntBuffer h = stack.mallocInt(1);
            SDL_GetWindowSizeInPixels(window, w, h);
            return ((long) w.get(0) << 32) | (h.get(0) & 0xFFFFFFFFL);
        }
    }

    private static int toSdl(VSyncMode mode) {
        return switch (mode) {
            case OFF -> SDL_GPU_PRESENTMODE_IMMEDIATE;
            case MAILBOX -> SDL_GPU_PRESENTMODE_MAILBOX;
            default -> SDL_GPU_PRESENTMODE_VSYNC;
        };
    }

    public int getDisplayRefreshRateHz() {
        final long window = Display.getWindow();
        final int displayId = window == 0 ? SDL_GetPrimaryDisplay() : SDL_GetDisplayForWindow(window);
        if (displayId == 0) return 0;
        final SDL_DisplayMode mode = SDL_GetCurrentDisplayMode(displayId);
        if (mode == null) return 0;
        return RenderBackend.refreshHzFrom(mode.refresh_rate_numerator(), mode.refresh_rate_denominator(), mode.refresh_rate());
    }

    public int getSwapchainTextureFormat() {
        final long window = Display.getWindow();
        return SDL_GetGPUSwapchainTextureFormat(device, window);
    }

    private void installLogCallback() {
        if (logCallback != null) return;
        logCallback = SDL_LogOutputFunction.create((userdata, category, priority, message) -> {
            if (lost) return;
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
