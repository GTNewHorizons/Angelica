package com.gtnewhorizons.angelica.sdlgpu.device;

import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.glsm.backend.BackendOptions;
import com.gtnewhorizons.angelica.glsm.backend.MoltenVK;
import com.gtnewhorizons.angelica.glsm.backend.VSyncMode;
import com.gtnewhorizons.angelica.glsm.backend.RenderBackend;
import com.gtnewhorizons.angelica.sdlgpu.compute.ImageAtomicsProbe;
import com.gtnewhorizons.angelica.sdlgpu.util.DebugMessageRelay;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.lwjgl.sdl.SDLGPU.*;
import static org.lwjgl.sdl.SDLHints.SDL_HINT_VULKAN_LIBRARY;
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
    private boolean fencePollEnabled;
    private boolean fenceQueryInverted;
    private int defaultMslVersion;
    private ImageAtomicsProbe imageAtomicsProbe;
    private final Long2BooleanOpenHashMap imageAtomicsBySize = new Long2BooleanOpenHashMap();
    private final FenceReleaser fenceReleaser = new FenceReleaser(fence -> device == 0 || FenceWait.isSignaled(this, fence), fence -> { if (device != 0) SDL_ReleaseGPUFence(device, fence); });
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

    static String[] driverCandidates(String requested, Platform platform) {
        final String[] platformOrder = switch (platform) {
            case MACOSX -> new String[] { "metal", "vulkan" };
            case WINDOWS -> new String[] { "vulkan", "direct3d12" };
            default -> new String[] { "vulkan" };
        };
        if (requested.isEmpty()) return platformOrder;
        final List<String> candidates = new ArrayList<>(platformOrder.length + 1);
        candidates.add(requested);
        for (final String driver : platformOrder) {
            if (!driver.equalsIgnoreCase(requested)) candidates.add(driver);
        }
        return candidates.toArray(new String[0]);
    }

    static boolean isVulkan(String driverName) {
        return "vulkan".equalsIgnoreCase(driverName);
    }

    static boolean isMetal(String driverName) {
        return "metal".equalsIgnoreCase(driverName);
    }

    static boolean metalNeedsNewerSdl(String driverName, int sdlVersion) {
        return isMetal(driverName) && sdlVersion < SDLVersion.SDL_VERSIONNUM(3, 4, 6);
    }

    public boolean createDevice() {
        if (device != 0) return true;

        installLogCallback();

        final int ver = SDLVersion.SDL_GetVersion();
        LOG.info("SDL runtime version {}.{}.{} (raw {})", SDLVersion.SDL_VERSIONNUM_MAJOR(ver), SDLVersion.SDL_VERSIONNUM_MINOR(ver), SDLVersion.SDL_VERSIONNUM_MICRO(ver), ver);

        final String requestedDriver = BackendOptions.sdlGpuDriver();
        final String[] candidates = driverCandidates(requestedDriver, Platform.get());
        if (!requestedDriver.isEmpty()) {
            LOG.info("SDL GPU driver requested: '{}'", requestedDriver);
        }

        final boolean gpuDebug = SystemProperties.LWJGL_DEBUG || SystemProperties.SDL_GPU_DEBUG;
        device = createGPUDevice(gpuDebug, candidates);
        if (device == 0 && VideoDriverRecovery.shouldRetryOnX11(SDL_GetCurrentVideoDriver(), vulkanLoaderAvailable(), System.getenv("DISPLAY"))) {
            device = VideoDriverRecovery.retryUnderX11(() -> createGPUDevice(gpuDebug, candidates));
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

        LOG.info("SDL GPU device created: gpu={}, driver={} {}, video={}, formats=0x{}, debug={}, requestedDriver={}", deviceName, driverName, driverVersion, SDL_GetCurrentVideoDriver(), Integer.toHexString(supportedShaderFormats), gpuDebug, requestedDriver);

        if (metalNeedsNewerSdl(driverName, ver)) {
            LOG.error("SDL GPU on Metal requires SDL 3.4.6 or newer (have {}.{}.{}); falling back to OpenGL", SDLVersion.SDL_VERSIONNUM_MAJOR(ver), SDLVersion.SDL_VERSIONNUM_MINOR(ver), SDLVersion.SDL_VERSIONNUM_MICRO(ver));
            destroyDevice();
            return false;
        }

        probeFences(ver);
        readMetalLanguageVersion();

        return true;
    }

    private void readMetalLanguageVersion() {
        if (!isMetal(driverName)) return;
        defaultMslVersion = ImageAtomicsProbe.defaultMslVersion();
        if (metalTextureAtomics()) {
            LOG.info("Metal image atomics: MSL 3.1 available, checked per image size");
        } else {
            LOG.info("Metal image atomics: unavailable, this Java runtime compiles Metal shaders as MSL {}.{} and they need 3.1. A runtime built with the macOS 14 SDK or newer enables them.", defaultMslVersion >>> 16, defaultMslVersion & 0xFFFF);
        }
    }

    public boolean metalTextureAtomics() {
        return defaultMslVersion >= ImageAtomicsProbe.MTL_LANGUAGE_VERSION_3_1;
    }

    public boolean supportsImageAtomics(int width, int height, int depth) {
        if (!metalTextureAtomics() || width <= 0 || height <= 0 || depth > 1) return false;
        final long key = (long) width << 32 | height;
        if (imageAtomicsBySize.containsKey(key)) return imageAtomicsBySize.get(key);
        if (imageAtomicsProbe == null) imageAtomicsProbe = new ImageAtomicsProbe(device);
        final boolean ok = imageAtomicsProbe.run(width, height);
        if (ok) LOG.info("Metal image atomics on {}x{}: ok", width, height);
        imageAtomicsBySize.put(key, ok);
        return ok;
    }

    private void probeFences(int ver) {
        fencePollEnabled = FenceWait.pollEnabled(driverName, ver);
        final boolean expectInverted = isMetal(driverName) && ver == SDLVersion.SDL_VERSIONNUM(3, 4, 14);
        fenceQueryInverted = FenceProbe.queryInverted(device, driverName, expectInverted);
        LOG.info("SDL {}.{}.{} driver={} fenceQueryInverted={} fencePoll={}", SDLVersion.SDL_VERSIONNUM_MAJOR(ver), SDLVersion.SDL_VERSIONNUM_MINOR(ver), SDLVersion.SDL_VERSIONNUM_MICRO(ver), driverName, fenceQueryInverted, fencePollEnabled);
        if (fenceQueryInverted != expectInverted) {
            LOG.warn("Fence query probe disagrees with the SDL version table: inverted={}, expected {}", fenceQueryInverted, expectInverted);
        }
    }

    private static void useMoltenVk() {
        final String path = MoltenVK.locate();
        if (path == null || MoltenVK.source() == MoltenVK.Source.ENV) return;
        if (SDL_SetHint(SDL_HINT_VULKAN_LIBRARY, path)) {
            LOG.info("MoltenVK: {}", path);
        } else {
            LOG.warn("Could not point SDL at MoltenVK {}: {}", path, SDLError.SDL_GetError());
        }
    }

    private long createGPUDevice(boolean gpuDebug, String[] candidates) {
        final boolean macos = Platform.get() == Platform.MACOSX;
        for (final String driver : candidates) {
            final boolean macVulkan = macos && isVulkan(driver);
            if (macVulkan) useMoltenVk();
            final long dev = SDL_CreateGPUDevice(REQUESTED_SHADER_FORMATS, gpuDebug, driver);
            if (dev != 0) return dev;
            LOG.warn("Failed to create a '{}' SDL GPU device: {}", driver, SDLError.SDL_GetError());
            if (macVulkan && MoltenVK.locate() == null) {
                LOG.warn("Vulkan on macOS needs MoltenVK. Add {} to the mods folder or install MoltenVK.", MoltenVK.JAR_NAME);
            }
        }
        return 0;
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
        LOG.error("Continuing on OpenGL. Set Video Settings > Renderer > Backend to OpenGL, or -D{}=false, to skip this probe entirely.", SystemProperties.KEY_USE_SDL_GPU);
    }

    public void destroyDevice() {
        if (device == 0) return;
        if (claimed) {
            final long window = Display.getWindow();
            if (window != 0) SDL_ReleaseWindowFromGPUDevice(device, window);
            claimed = false;
        }
        fenceReleaser.releaseAll();
        if (imageAtomicsProbe != null) imageAtomicsProbe.release();
        imageAtomicsProbe = null;
        SDL_DestroyGPUDevice(device);
        device = 0;
        driverName = null;
        fencePollEnabled = false;
        fenceQueryInverted = false;
        defaultMslVersion = 0;
        imageAtomicsBySize.clear();
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

    public boolean isMetal() {
        return isMetal(driverName);
    }

    public boolean isFencePollEnabled() {
        return fencePollEnabled;
    }

    public boolean isFenceQueryInverted() {
        return fenceQueryInverted;
    }

    public FenceReleaser fenceReleaser() {
        return fenceReleaser;
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
                candidate, preferred, RenderBackend.hzFromPeriod(getDisplayRefreshPeriodNanos()),
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

    public long getDisplayRefreshPeriodNanos() {
        final long window = Display.getWindow();
        final int displayId = window == 0 ? SDL_GetPrimaryDisplay() : SDL_GetDisplayForWindow(window);
        if (displayId == 0) return 0L;
        final SDL_DisplayMode mode = SDL_GetCurrentDisplayMode(displayId);
        if (mode == null) return 0L;
        return RenderBackend.periodFromRational(mode.refresh_rate_numerator(), mode.refresh_rate_denominator(), mode.refresh_rate());
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
