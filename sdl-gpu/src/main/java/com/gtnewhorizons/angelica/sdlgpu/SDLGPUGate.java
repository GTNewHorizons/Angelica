package com.gtnewhorizons.angelica.sdlgpu;

import static org.lwjgl.sdl.SDLVideo.SDL_PROP_WINDOW_CREATE_METAL_BOOLEAN;

import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.backend.RenderBackend;
import org.lwjgl.sdl.SDLProperties;
import org.lwjgl.system.Platform;
import org.lwjglx.Sys;
import org.lwjglx.opengl.ContextAttribs;
import org.lwjglx.opengl.Display;
import org.lwjglx.opengl.PixelFormat;

import me.eigenraven.lwjgl3ify.api.DisplayEvents;
import me.eigenraven.lwjgl3ify.api.DisplayWindowContext;
import me.eigenraven.lwjgl3ify.api.SwapchainInvalidatingChange;

import java.util.function.Consumer;

public final class SDLGPUGate {

    private SDLGPUGate() {}

    private static volatile boolean fatalInit;

    public static void markFatalInit() { fatalInit = true; }

    public static boolean isFatalInit() { return fatalInit; }

    public static boolean isSDLGPUAvailable() {
        return SDLGPUGate.class.getClassLoader().getResource("org/lwjgl/sdl/SDLGPU.class") != null;
    }

    public static boolean isActive() {
        if (!isSDLGPUAvailable()) return false;
        final RenderBackend rb = BackendManager.RENDER_BACKEND;
        return rb instanceof SDLGPURenderBackend;
    }

    public static Consumer<DisplayWindowContext> sdlGpuPreWindowCreateCallback() {
        if (Platform.get() != Platform.MACOSX) return null;
        return ctx -> Sys.checkSdl(SDLProperties.SDL_SetBooleanProperty(ctx.props(), SDL_PROP_WINDOW_CREATE_METAL_BOOLEAN, true));
    }

    public static Consumer<DisplayWindowContext> sdlGpuPostWindowCreateCallback(boolean debug) {
        return ctx -> BackendManager.RENDER_BACKEND.onPostWindowCreate(ctx.window(), debug);
    }

    public static Consumer<SwapchainInvalidatingChange> sdlGpuPreSwapchainInvalidatingCallback() {
        return change -> BackendManager.RENDER_BACKEND.onPreSwapchainInvalidatingChange(change);
    }

    public static void createSDLGPUDisplay(Object format, Object attribs, boolean debug) {
        DisplayEvents.setCreateGLContext(false);
        final Consumer<DisplayWindowContext> pre = sdlGpuPreWindowCreateCallback(); // null on non-Mac
        if (pre != null) DisplayEvents.addPreWindowCreateListener(pre);
        DisplayEvents.addPostWindowCreateListener(sdlGpuPostWindowCreateCallback(debug));
        Display.create((PixelFormat) format, (ContextAttribs) attribs, /*sharedWindow*/ 0L);
    }
}
