package com.gtnewhorizons.angelica.glsm.profiling;

import com.gtnewhorizons.angelica.config.SystemProperties;

public final class TracyOptions {

    private static boolean configEnabled;
    private static boolean configRemote;
    private static boolean configFineZones;
    private static int configMaxSrcLocs = 4096;

    private TracyOptions() {}

    public static void latch(boolean enabled, boolean allowRemote, boolean fineZones, int maxSrcLocs) {
        configEnabled = enabled;
        configRemote = allowRemote;
        configFineZones = fineZones;
        configMaxSrcLocs = maxSrcLocs;
    }

    public static boolean enabled() {
        return resolveBoolean(SystemProperties.TRACY_OVERRIDE, configEnabled);
    }

    public static boolean allowRemote() {
        return configRemote;
    }

    public static boolean fineZones() {
        return resolveBoolean(SystemProperties.TRACY_FINE_ZONES_OVERRIDE, configFineZones);
    }

    public static int maxSrcLocs() {
        return resolveMaxSrcLocs(SystemProperties.TRACY_MAX_SRC_LOCS_OVERRIDE, configMaxSrcLocs);
    }

    public static boolean configEnabled() {
        return configEnabled;
    }

    public static boolean configRemote() {
        return configRemote;
    }

    public static boolean configFineZones() {
        return configFineZones;
    }

    public static int configMaxSrcLocs() {
        return configMaxSrcLocs;
    }

    public static boolean backendPresent() {
        return TracyOptions.class.getResource("/com/gtnewhorizons/angelica/tracy/TracyClientBackend.class") != null;
    }

    static boolean resolveBoolean(Boolean override, boolean config) {
        return override != null ? override : config;
    }

    static int resolveMaxSrcLocs(Integer override, int config) {
        return Math.max(16, override != null ? override : config);
    }
}
