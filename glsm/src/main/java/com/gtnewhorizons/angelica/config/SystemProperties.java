package com.gtnewhorizons.angelica.config;

import net.minecraft.launchwrapper.Launch;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class SystemProperties {
    public static final String KEY_DUMP_SHADERS = "angelica.dumpShaders";
    public static final String KEY_DUMP_CLASS = "angelica.dumpClass";
    public static final String KEY_USE_SDL_GPU = "angelica.useSDLGPU";
    public static final String KEY_ASSERT_MAIN_THREAD = "angelica.assertMainThread";
    public static final String KEY_REDIRECTOR_LOGSPAM = "angelica.redirectorLogspam";
    public static final String KEY_DISABLE_GL_CACHE = "angelica.disableGlCache";
    public static final String KEY_FFP_WARN_ON_UNSUPPORTED = "angelica.ffp.warnOnUnsupported";
    public static final String KEY_DEBUG_DISPLAY_LISTS = "angelica.debugDisplayLists";
    public static final String KEY_LOG_DISPLAY_LIST_COMPILATION = "angelica.logDisplayListCompilation";
    public static final String KEY_FORCE_ORPHAN_STREAMING = "angelica.forceOrphanStreaming";
    public static final String KEY_ENABLE_TEST_BLOCKS = "angelica.enableTestBlocks";
    public static final String KEY_DISABLE_NVIDIA_WORKAROUND = "angelica.disableNvidiaWorkaround";
    public static final String KEY_GL_PROFILE = "angelica.glProfile";
    public static final String KEY_SDL_GPU_DRIVER = "angelica.sdlgpu.driver";
    public static final String KEY_SDL_GPU_DEBUG = "angelica.sdlgpu.debug";
    public static final String KEY_SDL_ENCODER_ASSERTIONS = "angelica.sdlgpu.encoderAssertions";
    public static final String KEY_SDL_ENCODER_ASSERTIONS_FATAL = "angelica.sdlgpu.encoderAssertionsFatal";
    public static final String KEY_SDL_COPY_ASSERTIONS = "angelica.sdlgpu.copyAssertions";
    public static final String KEY_SDL_MEMBARRIER_ENDS_RENDER_PASS = "angelica.sdlgpu.memoryBarrierEndsRenderPass";
    public static final String KEY_SDL_FBOBIND_ENDS_RENDER_PASS = "angelica.sdlgpu.fboBindEndsRenderPass";
    public static final String KEY_DEBUG_MARKERS = "angelica.debug.markers";
    public static final String KEY_DEBUG_F3_DETAIL = "angelica.debug.f3Detail";
    public static final String KEY_VERIFY_PER_FRAME_UNIFORM_BLOCK = "angelica.sdlgpu.verifyPerFrameUniformBlock";
    public static final String KEY_SDL_VERIFY_STATE_SYNC = "angelica.sdlgpu.verifyStateSync";
    public static final String KEY_DISABLE_UNMAPPED_GL_DETECTOR = "angelica.disableUnmappedGLDetector";
    public static final String KEY_FAIL_ON_UNMAPPED_GL = "angelica.failOnUnmappedGL";
    public static final String KEY_FAIL_ON_AWARE_UNROUTABLE = "angelica.failOnAwareUnroutableGL";
    public static final String KEY_TRACY = "angelica.tracy";
    public static final String KEY_TRACY_FINE_ZONES = "angelica.tracy.fineZones";
    public static final String KEY_LWJGL_DEBUG = "org.lwjgl.util.Debug";

    public static final boolean LWJGL_DEBUG = Boolean.getBoolean(KEY_LWJGL_DEBUG);
    public static final boolean DUMP_CLASS = Boolean.getBoolean(KEY_DUMP_CLASS);
    public static final boolean ASSERT_MAIN_THREAD = Boolean.getBoolean(KEY_ASSERT_MAIN_THREAD);
    public static final boolean REDIRECTOR_LOGSPAM = Boolean.getBoolean(KEY_REDIRECTOR_LOGSPAM);
    public static final boolean FFP_WARN_ON_UNSUPPORTED = Boolean.getBoolean(KEY_FFP_WARN_ON_UNSUPPORTED);
    public static final boolean DEBUG_DISPLAY_LISTS = Boolean.getBoolean(KEY_DEBUG_DISPLAY_LISTS);
    public static final boolean LOG_DISPLAY_LIST_COMPILATION = Boolean.getBoolean(KEY_LOG_DISPLAY_LIST_COMPILATION);
    public static final boolean SDL_ENCODER_ASSERTIONS = Boolean.getBoolean(KEY_SDL_ENCODER_ASSERTIONS);
    public static final boolean SDL_ENCODER_ASSERTIONS_FATAL = Boolean.getBoolean(KEY_SDL_ENCODER_ASSERTIONS_FATAL);
    public static final boolean SDL_COPY_ASSERTIONS = Boolean.getBoolean(KEY_SDL_COPY_ASSERTIONS);
    public static final boolean SDL_GPU_DEBUG = Boolean.getBoolean(KEY_SDL_GPU_DEBUG);
    public static final boolean TRACY = Boolean.getBoolean(KEY_TRACY);
    public static final boolean TRACY_FINE_ZONES = Boolean.getBoolean(KEY_TRACY_FINE_ZONES);
    public static final boolean SDL_MEMBARRIER_ENDS_RENDER_PASS = Boolean.getBoolean(KEY_SDL_MEMBARRIER_ENDS_RENDER_PASS);
    public static final boolean SDL_FBOBIND_ENDS_RENDER_PASS = Boolean.getBoolean(KEY_SDL_FBOBIND_ENDS_RENDER_PASS);
    public static final boolean DISABLE_NVIDIA_WORKAROUND = Boolean.getBoolean(KEY_DISABLE_NVIDIA_WORKAROUND);
    public static final boolean USE_SDL_GPU = Boolean.getBoolean(KEY_USE_SDL_GPU);
    public static final boolean ENABLE_TEST_BLOCKS = Boolean.getBoolean(KEY_ENABLE_TEST_BLOCKS);
    public static final boolean DEBUG_MARKERS = Boolean.getBoolean(KEY_DEBUG_MARKERS);
    public static final boolean DEBUG_F3_DETAIL = Boolean.getBoolean(KEY_DEBUG_F3_DETAIL);
    public static final boolean VERIFY_PER_FRAME_UNIFORM_BLOCK = Boolean.getBoolean(KEY_VERIFY_PER_FRAME_UNIFORM_BLOCK);
    public static final boolean SDL_VERIFY_STATE_SYNC = Boolean.getBoolean(KEY_SDL_VERIFY_STATE_SYNC);
    public static final boolean FORCE_ORPHAN_STREAMING = Boolean.getBoolean(KEY_FORCE_ORPHAN_STREAMING);
    public static final int SDL_FRAMES_IN_FLIGHT = 3;


    public static final String GL_PROFILE = System.getProperty(KEY_GL_PROFILE, "");
    public static final String SDL_GPU_DRIVER = System.getProperty(KEY_SDL_GPU_DRIVER, "");

    public static final boolean DUMP_SHADERS = Boolean.getBoolean(KEY_DUMP_SHADERS) || isDeobf();

    public static volatile boolean DISABLE_UNMAPPED_GL_DETECTOR = Boolean.getBoolean(KEY_DISABLE_UNMAPPED_GL_DETECTOR);
    public static volatile boolean FAIL_ON_UNMAPPED_GL = Boolean.getBoolean(KEY_FAIL_ON_UNMAPPED_GL);
    public static volatile boolean FAIL_ON_AWARE_UNROUTABLE = Boolean.getBoolean(KEY_FAIL_ON_AWARE_UNROUTABLE);

    public static volatile boolean BYPASS_GL_CACHE = Boolean.getBoolean(KEY_DISABLE_GL_CACHE);

    public static final String SHADER_DUMP_ROOT = "angelica_dumps";

    public static Path shaderDumpDir(String phase) {
        return DUMP_SHADERS ? Paths.get(SHADER_DUMP_ROOT, phase) : null;
    }

    private static boolean isDeobf() {
        return Launch.blackboard != null && Boolean.TRUE.equals(Launch.blackboard.get("fml.deobfuscatedEnvironment"));
    }

    private SystemProperties() {}
}
