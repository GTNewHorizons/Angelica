package com.gtnewhorizons.angelica.sdlgpu.device;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLHints;
import org.lwjgl.sdl.SDLInit;

import java.util.function.LongSupplier;

import static org.lwjgl.sdl.SDLInit.SDL_INIT_VIDEO;
import static org.lwjgl.sdl.SDLVideo.SDL_GetCurrentVideoDriver;

public final class VideoDriverRecovery {

    private static final Logger LOG = LogManager.getLogger("Angelica-SDLGPU");

    private static final String X11 = "x11";

    private VideoDriverRecovery() {}

    static boolean shouldRetryOnX11(String videoDriver, boolean vulkanLoaderAvailable, String displayEnv) {
        if (vulkanLoaderAvailable) return false;
        if (!"wayland".equalsIgnoreCase(videoDriver)) return false;
        return displayEnv != null && !displayEnv.isEmpty();
    }

    static long retryUnderX11(LongSupplier createDevice) {
        final String original = SDL_GetCurrentVideoDriver();
        LOG.warn("SDL GPU is unavailable under the '{}' video driver; retrying under {}", original, X11);

        if (!restartVideo(X11)) {
            LOG.warn("Could not restart the SDL video subsystem under {}: {}", X11, SDLError.SDL_GetError());
            restartVideo(original);
            return 0L;
        }

        final long device = createDevice.getAsLong();
        if (device == 0) {
            LOG.warn("SDL GPU is unavailable under {} as well; restoring the '{}' video driver", X11, original);
            restartVideo(original);
            return 0L;
        }

        LOG.warn("SDL video driver switched to {} so the SDL GPU backend can run. Set SDL_VIDEODRIVER=wayland to keep the native session and run on OpenGL instead.", X11);
        return device;
    }

    private static boolean restartVideo(String driver) {
        SDLInit.SDL_QuitSubSystem(SDL_INIT_VIDEO);
        if (driver == null || driver.isEmpty()) {
            SDLHints.SDL_ResetHint(SDLHints.SDL_HINT_VIDEO_DRIVER);
        } else {
            SDLHints.SDL_SetHint(SDLHints.SDL_HINT_VIDEO_DRIVER, driver);
        }
        return SDLInit.SDL_InitSubSystem(SDL_INIT_VIDEO);
    }
}
