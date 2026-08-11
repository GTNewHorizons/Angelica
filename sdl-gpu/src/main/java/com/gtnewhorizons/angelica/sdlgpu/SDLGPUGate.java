package com.gtnewhorizons.angelica.sdlgpu;

import static org.lwjgl.sdl.SDLVideo.SDL_PROP_WINDOW_CREATE_METAL_BOOLEAN;

import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.backend.RenderBackend;
import com.gtnewhorizons.angelica.sdlgpu.device.Device;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.sdl.SDLLog;
import org.lwjgl.sdl.SDLProperties;
import org.lwjgl.system.Platform;
import org.lwjglx.Sys;
import org.lwjglx.input.Mouse;
import org.lwjglx.opengl.ContextAttribs;
import org.lwjglx.opengl.Display;
import org.lwjglx.opengl.PixelFormat;

import me.eigenraven.lwjgl3ify.api.DisplayEvents;
import me.eigenraven.lwjgl3ify.api.DisplayWindowContext;
import me.eigenraven.lwjgl3ify.api.SwapchainInvalidatingChange;
import me.eigenraven.lwjgl3ify.client.MainThreadExec;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public final class SDLGPUGate {

    private static final Logger LOG = LogManager.getLogger("Angelica/SDLGPU");

    private SDLGPUGate() {}

    private static Device device;

    private static volatile Boolean deviceReady;
    private static volatile boolean engaged;
    private static volatile boolean disarmed;
    private static volatile Throwable initFailure;
    private static boolean listenersRegistered;
    private static ByteBuffer[] windowIcons;

    public static void rememberIcons(ByteBuffer[] icons) {
        windowIcons = icons;
    }

    public static synchronized Device device() {
        if (device == null) device = new Device();
        return device;
    }

    private static final boolean SDL_GPU_AVAILABLE = SDLGPUGate.class.getClassLoader().getResource("org/lwjgl/sdl/SDLGPU.class") != null;

    public static boolean isSDLGPUAvailable() {
        return SDL_GPU_AVAILABLE;
    }

    public static boolean isDeviceReady() {
        final Boolean ready = deviceReady;
        return ready != null ? ready : probe();
    }

    private static synchronized boolean probe() {
        if (deviceReady != null) return deviceReady;
        if (!SystemProperties.USE_SDL_GPU || !isSDLGPUAvailable()) {
            deviceReady = false;
            return false;
        }
        boolean ok;
        try {
            Sys.initialize();
            ok = MainThreadExec.runOnMainThread(device()::createDevice);
        } catch (Throwable t) {
            LOG.error("SDL GPU device probe failed", t);
            ok = false;
        }
        if (!ok) resetSdlLogging();
        deviceReady = ok;
        return ok;
    }

    public static boolean isEngaged() {
        return engaged;
    }

    public static boolean isActive() {
        if (!isSDLGPUAvailable()) return false;
        final RenderBackend rb = BackendManager.RENDER_BACKEND;
        return rb instanceof SDLGPURenderBackend;
    }

    public static void ensureDrawableInstalled() {
        if (!isActive()) return;
        SDLGPUDisplayBridge.ensureDrawableInstalled();
    }

    public static void prewarmSpirv(String transformedSource, int glShaderType) {
        if (!isActive()) return;
        ShaderManager.prewarmSpirv(transformedSource, glShaderType);
    }

    public static void clearShaderPrewarmCache() {
        if (!isActive()) return;
        ShaderManager.clearPrewarmCache();
    }

    public static Consumer<SwapchainInvalidatingChange> sdlGpuPreSwapchainInvalidatingCallback() {
        return change -> BackendManager.RENDER_BACKEND.onPreSwapchainInvalidatingChange(change);
    }

    public static boolean createSDLGPUDisplay(Object format, Object attribs) {
        if (!isDeviceReady()) return false;

        initFailure = null;
        disarmed = false;
        registerListeners();

        DisplayEvents.setCreateGLContext(false);
        Display.create((PixelFormat) format, (ContextAttribs) attribs, /*sharedWindow*/ 0L);

        final Throwable failure = initFailure;
        if (failure != null) {
            initFailure = null;
            LOG.error("SDL GPU could not take the window", failure);
            return false;
        }
        return engaged;
    }

    private static void registerListeners() {
        if (listenersRegistered) return;
        listenersRegistered = true;

        if (Platform.get() == Platform.MACOSX) {
            DisplayEvents.addPreWindowCreateListener((DisplayWindowContext ctx) -> {
                if (disarmed) return;
                Sys.checkSdl(SDLProperties.SDL_SetBooleanProperty(ctx.props(), SDL_PROP_WINDOW_CREATE_METAL_BOOLEAN, true));
            });
        }

        DisplayEvents.addPostWindowCreateListener((DisplayWindowContext ctx) -> {
            if (disarmed) return;
            try {
                device().claimWindow(ctx.window());
                engaged = true;
                BackendManager.RENDER_BACKEND.onPostWindowCreate(ctx.window());
            } catch (Throwable t) {
                initFailure = t;
            }
        });
    }

    public static void fallBackToGL() {
        disarmed = true;
        engaged = false;
        deviceReady = false;

        device().destroyDevice();
        resetSdlLogging();

        Mouse.setGrabbed(false);

        DisplayEvents.setCreateGLContext(true);
        if (Display.isCreated()) {
            Display.destroy();
        }
        Display.isCloseRequested();
        if (windowIcons != null) {
            Display.setIcon(windowIcons);
        }
    }

    private static void resetSdlLogging() {
        SDLLog.SDL_ResetLogPriorities();
    }
}
