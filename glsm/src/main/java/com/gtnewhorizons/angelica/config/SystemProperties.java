package com.gtnewhorizons.angelica.config;

import net.minecraft.launchwrapper.Launch;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;

public final class SystemProperties {

    private static final Logger LOGGER = LogManager.getLogger("SystemProperties");

    // Backend selection
    public static final String KEY_USE_SDL_GPU = "angelica.sdlgpu.enable";
    public static final Boolean SDL_GPU_OVERRIDE = parseBooleanOverride(KEY_USE_SDL_GPU);
    public static final String GL_PROFILE = System.getProperty("angelica.glProfile", "");
    public static final boolean DISABLE_NVIDIA_WORKAROUND = Boolean.getBoolean("angelica.disableNvidiaWorkaround");
    public static final boolean DISABLE_LTW_WORKAROUND = Boolean.getBoolean("angelica.disableLtwWorkaround");

    // Redirector
    public static final UnmappedGLMode UNMAPPED_GL = parseEnum("angelica.unmappedGL", UnmappedGLMode.WARN, UnmappedGLMode.WARN, UnmappedGLMode.class);
    public static final boolean FFP_WARN_ON_UNSUPPORTED = Boolean.getBoolean("angelica.ffp.warnOnUnsupported");

    // SDL-GPU
    public static final String KEY_SDL_GPU_DRIVER = "angelica.sdlgpu.driver";
    public static final String SDL_GPU_DRIVER_OVERRIDE = parseStringOverride(KEY_SDL_GPU_DRIVER);
    public static final String MOLTENVK_DIR = System.getProperty("angelica.moltenvk.dir", "angelica" + File.separator + "natives" + File.separator + "moltenvk");
    public static final boolean SDL_GPU_DEBUG = Boolean.getBoolean("angelica.sdlgpu.debug");
    private static final SdlAssertionMode ENCODER_ASSERTIONS = parseEnum("angelica.sdlgpu.encoderAssertions", SdlAssertionMode.OFF, SdlAssertionMode.WARN, SdlAssertionMode.class);
    public static final boolean SDL_ENCODER_ASSERTIONS = ENCODER_ASSERTIONS != SdlAssertionMode.OFF;
    public static final boolean SDL_ENCODER_ASSERTIONS_FATAL = ENCODER_ASSERTIONS == SdlAssertionMode.FATAL;
    public static final boolean SDL_VERIFY_STATE_SYNC = Boolean.getBoolean("angelica.sdlgpu.verifyStateSync");
    public static final boolean SDL_VERIFY_PER_FRAME_UNIFORM_BLOCK = Boolean.getBoolean("angelica.sdlgpu.verifyPerFrameUniformBlock");
    public static final int SDL_FRAMES_IN_FLIGHT = Integer.getInteger("angelica.sdlgpu.framesInFlight", 2);
    public static final boolean DISABLE_SDL_PRESENTER_THREAD = Boolean.getBoolean("angelica.sdlgpu.disablePresenterThread");
    public static final boolean SDL_DISABLE_IN_PASS_CLEAR = Boolean.getBoolean("angelica.sdlgpu.disableInPassClear");

    // Tracy
    public static final Boolean TRACY_OVERRIDE = parseBooleanOverride("angelica.tracy");
    public static final Boolean TRACY_FINE_ZONES_OVERRIDE = parseBooleanOverride("angelica.tracy.fineZones");
    public static final String TRACY_DIR = System.getProperty("angelica.tracy.dir", "angelica" + File.separator + "natives" + File.separator + "tracy");
    public static final Integer TRACY_MAX_SRC_LOCS_OVERRIDE = Integer.getInteger("angelica.tracy.maxSrcLocs");

    // Profiling
    public static final String PROFILE_OPTS = System.getProperty("angelica.profile.opts", "event=wall,interval=5ms,alloc=512k,lock=10ms");
    public static final String PROFILE_DIR = System.getProperty("angelica.profile.dir", "angelica" + File.separator + "profiles");
    public static final String PROFILE_OUTPUT = System.getProperty("angelica.profile.output", "");

    // Flyby
    public static final String FLYBY_ROUTE = System.getProperty("angelica.flyby.route", "");
    public static final boolean FLYBY_WAIT_FOR_TRACY = Boolean.getBoolean("angelica.flyby.waitForTracy");
    public static final boolean FLYBY_WAIT_FOR_FOCUS = Boolean.getBoolean("angelica.flyby.waitForFocus");
    public static final int FLYBY_WARMUP_TICKS = Integer.getInteger("angelica.flyby.warmupTicks", 400);
    public static final int FLYBY_LENGTH = Integer.getInteger("angelica.flyby.length", 0); // Blocks for moving routes, ticks for stationary
    public static final double FLYBY_SPEED = parseDouble("angelica.flyby.speed"); // Travel speed in blocks per tick
    public static final boolean FLYBY_EXIT_WHEN_DONE = Boolean.getBoolean("angelica.flyby.exitWhenDone");
    public static final long FLYBY_TIME_OF_DAY = Math.floorMod(Long.getLong("angelica.flyby.timeOfDay", 6000L), 24000L);
    public static final String FLYBY_COMMANDS = System.getProperty("angelica.flyby.commands", "");
    public static final String FLYBY_ORIGIN = System.getProperty("angelica.flyby.origin", "");
    public static final FlybyPacing FLYBY_PACING = parseEnum("angelica.flyby.pacing", FlybyPacing.UNCAPPED, FlybyPacing.UNCAPPED, FlybyPacing.class);
    public static final FlybyWeather FLYBY_WEATHER = parseEnum("angelica.flyby.weather", FlybyWeather.CLEAR, FlybyWeather.CLEAR, FlybyWeather.class);
    public static final FlybyCamera FLYBY_CAMERA = parseEnum("angelica.flyby.camera", FlybyCamera.FIRST_PERSON, FlybyCamera.FIRST_PERSON, FlybyCamera.class);
    public static final boolean FLYBY_JFR = Boolean.getBoolean("angelica.flyby.jfr");
    public static final float FLYBY_PITCH = parseFloat("angelica.flyby.pitch");
    public static final int FLYBY_SCREENSHOTS = Integer.getInteger("angelica.flyby.screenshots", 0);
    public static final boolean FLYBY_DEBUG_HUD = Boolean.getBoolean("angelica.flyby.debugHud");
    public static final String FLYBY_CRASH_TEST = System.getProperty("angelica.flyby.crashTest", "");

    // Debug
    public static final boolean LWJGL_DEBUG = Boolean.getBoolean("org.lwjgl.util.Debug");
    public static final boolean DEBUG_MARKERS = Boolean.getBoolean("angelica.debug.markers");
    public static final boolean DEBUG_F3_DETAIL = Boolean.getBoolean("angelica.debug.f3Detail");
    public static final boolean ENABLE_TEST_BLOCKS = Boolean.getBoolean("angelica.debug.testBlocks");
    public static final boolean DUMP_CLASS = Boolean.getBoolean("angelica.debug.dumpClass");
    private static final boolean DUMP_SHADERS_PROP = Boolean.getBoolean("angelica.debug.dumpShaders");
    private static final boolean DEBUG_TOOLING_PROP = Boolean.getBoolean("angelica.debug.tooling");
    public static final boolean REDIRECTOR_LOGSPAM = Boolean.getBoolean("angelica.debug.redirectorLogspam");
    public static final boolean DEBUG_DISPLAY_LISTS = Boolean.getBoolean("angelica.debug.displayLists");
    public static final boolean FFP_TRACE = Boolean.getBoolean("angelica.debug.ffpTrace");
    public static final boolean LOG_DISPLAY_LIST_COMPILATION = Boolean.getBoolean("angelica.debug.displayLists.compilation");
    public static final boolean FORCE_ORPHAN_STREAMING = Boolean.getBoolean("angelica.debug.forceOrphanStreaming");
    public static final boolean WEATHER_REBUILD_ALWAYS = Boolean.getBoolean("angelica.debug.weatherRebuildAlways");
    public static final String SHADER_DUMP_ROOT = "angelica_dumps";

    // Set by us, read by celeritas
    public static final String KEY_CELERITAS_ENABLE_GL_DEBUG = "celeritas.enableGLDebug";
    public static final String KEY_CELERITAS_LWJGL_SERVICE = "celeritas.lwjglService";

    public static boolean dumpShaders() {
        return DUMP_SHADERS_PROP || isDeobf();
    }

    public static boolean debugTooling() {
        return DEBUG_TOOLING_PROP || isDeobf();
    }

    public static Path shaderDumpDir(String phase) {
        return dumpShaders() ? Paths.get(SHADER_DUMP_ROOT, phase) : null;
    }

    public enum UnmappedGLMode {
        OFF,
        WARN,
        FAIL,
        STRICT;

        public boolean detects() {
            return this != OFF;
        }

        public boolean failsUnmapped() {
            return this == FAIL || this == STRICT;
        }

        public boolean failsAwareUnroutable() {
            return this == STRICT;
        }
    }

    private enum SdlAssertionMode {
        OFF,
        WARN,
        FATAL
    }

    public enum FlybyPacing {
        UNCAPPED,
        CONFIGURED
    }

    public enum FlybyCamera {
        FIRST_PERSON,
        THIRD_PERSON_BACK,
        THIRD_PERSON_FRONT
    }

    public enum FlybyWeather {
        CLEAR,
        RAIN,
        THUNDER;

        public boolean isRaining() {
            return this != CLEAR;
        }

        public boolean isThundering() {
            return this == THUNDER;
        }
    }

    private static <E extends Enum<E>> E parseEnum(String key, E whenAbsent, E whenInvalid, Class<E> type) {
        final String raw = System.getProperty(key);
        if (raw == null || raw.isEmpty()) return whenAbsent;
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Unrecognized value '{}' for -D{}; using {}. Valid values: {}", raw, key, whenInvalid, Arrays.toString(type.getEnumConstants()));
            return whenInvalid;
        }
    }

    private static Boolean parseBooleanOverride(String key) {
        final String raw = System.getProperty(key);
        return raw == null || raw.isEmpty() ? null : Boolean.parseBoolean(raw);
    }

    private static String parseStringOverride(String key) {
        final String raw = System.getProperty(key);
        return raw == null || raw.isEmpty() ? null : raw;
    }

    private static double parseDouble(String key) {
        final String raw = System.getProperty(key);
        if (raw == null || raw.isEmpty()) return 0.0D;
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return 0.0D;
        }
    }

    private static float parseFloat(String key) {
        final String raw = System.getProperty(key);
        if (raw == null || raw.isEmpty()) return Float.NaN;
        try {
            return Float.parseFloat(raw);
        } catch (NumberFormatException e) {
            return Float.NaN;
        }
    }

    public static boolean isDeobf() {
        return Launch.blackboard != null && Boolean.TRUE.equals(Launch.blackboard.get("fml.deobfuscatedEnvironment"));
    }

    private SystemProperties() {}
}
