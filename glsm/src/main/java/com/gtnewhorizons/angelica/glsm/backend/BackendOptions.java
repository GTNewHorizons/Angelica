package com.gtnewhorizons.angelica.glsm.backend;

import com.gtnewhorizons.angelica.config.SystemProperties;

public final class BackendOptions {

    private static boolean configSdlGpu;
    private static String configDriver = "";
    private static int configGlVersion;

    private BackendOptions() {}

    public static void latch(boolean sdlGpu, String driver, int glVersion) {
        configSdlGpu = sdlGpu;
        configDriver = driver;
        configGlVersion = glVersion;
    }

    public static boolean sdlGpuRequested() {
        return SystemProperties.SDL_GPU_OVERRIDE != null ? SystemProperties.SDL_GPU_OVERRIDE : configSdlGpu;
    }

    public static String sdlGpuDriver() {
        return SystemProperties.SDL_GPU_DRIVER_OVERRIDE != null ? SystemProperties.SDL_GPU_DRIVER_OVERRIDE : configDriver;
    }

    public static boolean configSdlGpu() {
        return configSdlGpu;
    }

    public static String configDriver() {
        return configDriver;
    }

    public static int configGlVersion() {
        return configGlVersion;
    }
}
