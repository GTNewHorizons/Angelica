package com.gtnewhorizons.angelica.stereo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.ARBCopyImage;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.opengl.GLSync;

import java.lang.reflect.Method;
import java.nio.IntBuffer;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs cursor presentation on a separate thread that owns its own GL context, sharing GL
 * resources (textures, fences) with MC's main context but bound to the same window so its
 * renders + swaps actually present to what the user sees.
 *
 * <h2>Why we can't just use {@code SharedDrawable}</h2>
 *
 * <p>The lwjglx wrapper's {@code SharedDrawable} on GLFW creates a brand-new hidden window for
 * the secondary context — GLFW binds each context to its creation window. The cursor thread's
 * default framebuffer would then be the hidden window's, and its swap wouldn't reach the
 * visible window.</p>
 *
 * <p>To get a context bound to the <em>main</em> window we bypass GLFW and use WGL directly:
 * grab the main window's HWND and HDC, grab main's HGLRC, then
 * {@code wglCreateContextAttribsARB(mainHdc, mainHglrc, attribs)} for a new context that
 * shares resources with main and renders into the main window. The cursor thread
 * {@code wglMakeCurrent}s it, renders, and {@code SwapBuffers(mainHdc)} presents to the user.
 * All of this is reachable via reflection on real LWJGL3 — lwjgl3ify rewrites bytecode class
 * refs but not {@code Class.forName} string literals.</p>
 *
 * <h2>Producer/consumer</h2>
 *
 * <p>Main thread runs {@link #publishFrame()} at end-of-frame: copies framebufferMc into one
 * of two present textures via {@code glCopyImageSubData}, fences the copy, atomically publishes
 * the (writeIdx, fence) pair. Cursor thread consumes the pair, waits the fence, switches its
 * read index, blits, draws cursor sprites, swaps. Cursor sprite uses the live mouse position
 * sampled inside its own loop so it always lands at the most recent position.</p>
 *
 * <p>Platform: Windows only. macOS and Linux fall back to no async cursor (main thread draws
 * the cursor as it did before this feature existed).</p>
 */
public final class CursorPresentThread {

    private static final Logger LOGGER = LogManager.getLogger("AngelicaCursorThread");

    // WGL/GL constants we use via reflection. Verified against runtime LWJGL 3.3.3 jars.
    private static final int WGL_CONTEXT_MAJOR_VERSION_ARB = 0x2091;
    private static final int WGL_CONTEXT_MINOR_VERSION_ARB = 0x2092;
    private static final int WGL_CONTEXT_PROFILE_MASK_ARB = 0x9126;
    private static final int WGL_CONTEXT_COMPATIBILITY_PROFILE_BIT_ARB = 0x0002;
    private static final int GL_MAJOR_VERSION = 0x821B;
    private static final int GL_MINOR_VERSION = 0x821C;

    private static volatile CursorPresentThread INSTANCE;

    // ----------------------------------------------------------------------
    // Reflection helpers for LWJGL3 entry points the lwjglx wrapper doesn't expose.
    //
    // lwjgl3ify's ASM transformer rewrites {@code org/lwjgl/*} bytecode refs to
    // {@code org/lwjglx/*}. {@code Class.forName} string literals are NOT rewritten, so this
    // reaches the real LWJGL3 classes that ship with lwjgl3ify (currently 3.3.3).
    // ----------------------------------------------------------------------

    private static volatile Method REAL_GL32_glClientWaitSync;
    private static volatile Method REAL_GL32_glDeleteSync;
    private static volatile Method REAL_GL_createCapabilities;
    private static volatile Method REAL_GLFW_glfwGetCurrentContext;
    private static volatile Method REAL_GLFWNativeWin32_getWin32Window;
    private static volatile Method REAL_GLFWNativeWGL_getWGLContext;
    private static volatile Method REAL_User32_GetDC;
    private static volatile Method REAL_User32_ReleaseDC;
    private static volatile Method REAL_WGL_wglMakeCurrent;
    private static volatile Method REAL_WGL_wglDeleteContext;
    private static volatile Method REAL_WGLARBCreateContext_wglCreateContextAttribsARB;
    private static volatile Method REAL_GDI32_SwapBuffers;
    private static volatile Method REAL_WGLEXTSwapControl_wglSwapIntervalEXT;
    private static volatile boolean reflectionInitialized = false;
    private static volatile boolean reflectionOk = false;

    private static synchronized void ensureReflectionInitialized() {
        if (reflectionInitialized) return;
        try {
            final Class<?> gl32 = Class.forName("org.lwjgl.opengl.GL32");
            REAL_GL32_glClientWaitSync = gl32.getMethod("glClientWaitSync", long.class, int.class, long.class);
            REAL_GL32_glDeleteSync = gl32.getMethod("glDeleteSync", long.class);

            final Class<?> gl = Class.forName("org.lwjgl.opengl.GL");
            REAL_GL_createCapabilities = gl.getMethod("createCapabilities");

            final Class<?> glfw = Class.forName("org.lwjgl.glfw.GLFW");
            REAL_GLFW_glfwGetCurrentContext = glfw.getMethod("glfwGetCurrentContext");

            final Class<?> nWin32 = Class.forName("org.lwjgl.glfw.GLFWNativeWin32");
            REAL_GLFWNativeWin32_getWin32Window = nWin32.getMethod("glfwGetWin32Window", long.class);

            final Class<?> nWGL = Class.forName("org.lwjgl.glfw.GLFWNativeWGL");
            REAL_GLFWNativeWGL_getWGLContext = nWGL.getMethod("glfwGetWGLContext", long.class);

            final Class<?> user32 = Class.forName("org.lwjgl.system.windows.User32");
            REAL_User32_GetDC = user32.getMethod("GetDC", long.class);
            REAL_User32_ReleaseDC = user32.getMethod("ReleaseDC", long.class, long.class);

            final Class<?> wgl = Class.forName("org.lwjgl.opengl.WGL");
            REAL_WGL_wglMakeCurrent = wgl.getMethod("wglMakeCurrent", long.class, long.class);
            REAL_WGL_wglDeleteContext = wgl.getMethod("wglDeleteContext", long.class);

            final Class<?> wglAttribs = Class.forName("org.lwjgl.opengl.WGLARBCreateContext");
            REAL_WGLARBCreateContext_wglCreateContextAttribsARB =
                wglAttribs.getMethod("wglCreateContextAttribsARB", long.class, long.class, int[].class);

            final Class<?> gdi32 = Class.forName("org.lwjgl.system.windows.GDI32");
            REAL_GDI32_SwapBuffers = gdi32.getMethod("SwapBuffers", long.class);

            // Optional — only present if the WGL_EXT_swap_control extension is exposed.
            // We don't fail reflection setup if it's missing; we just won't vsync.
            try {
                final Class<?> wglSwapCtl = Class.forName("org.lwjgl.opengl.WGLEXTSwapControl");
                REAL_WGLEXTSwapControl_wglSwapIntervalEXT =
                    wglSwapCtl.getMethod("wglSwapIntervalEXT", int.class);
            } catch (Throwable t) {
                LOGGER.warn("wglSwapIntervalEXT not available — cursor thread will not vsync.", t);
            }

            reflectionOk = true;
            LOGGER.info("LWJGL3 reflection helpers resolved.");
        } catch (Throwable t) {
            LOGGER.error("Could not resolve LWJGL3 reflection helpers; async cursor disabled.", t);
        }
        reflectionInitialized = true;
    }

    private static long invokeLong(Method m, Object... args) throws Throwable {
        return ((Number) m.invoke(null, args)).longValue();
    }

    private static boolean invokeBool(Method m, Object... args) throws Throwable {
        return (Boolean) m.invoke(null, args);
    }

    // ----------------------------------------------------------------------
    // Per-instance state
    // ----------------------------------------------------------------------

    private final Thread worker;
    private volatile boolean running = true;

    /** Native handles for cleanup. */
    private final long mainHwnd;
    private final long mainHdc;
    private final long cursorHglrc;

    // Double-buffer present textures, allocated on the main thread on first publish.
    // Both contexts share these (wglCreateContextAttribsARB with shareContext = mainHglrc).
    private volatile int texA = 0;
    private volatile int texB = 0;
    private volatile int cachedW = 0;
    private volatile int cachedH = 0;

    /**
     * Tuple of "the next index the cursor thread should switch to" and a GL fence that signals
     * when the copy into that texture has finished on the GPU. The cursor thread atomically
     * consumes this (via getAndSet(null)), waits on the fence, then updates its own read index.
     */
    private static final class Pending {
        final int writeIdx;
        final GLSync copyFence;
        Pending(int writeIdx, GLSync copyFence) {
            this.writeIdx = writeIdx;
            this.copyFence = copyFence;
        }
    }
    private final AtomicReference<Pending> pendingPublish = new AtomicReference<>();

    /**
     * Main thread's previous-frame fence, used to rate-limit main to "at most one frame
     * ahead of GPU". Without this bound, main produces GL commands faster than the GPU can
     * execute them, the driver queues them, and latency between input and what's rendered
     * grows without bound. With this bound, main waits for last frame's GPU work to finish
     * before queueing next frame's commands — preserving CPU/GPU pipelining (unlike
     * {@code glFinish} which serialises) while keeping the queue depth at 1.
     *
     * <p>Only accessed from the main thread inside {@link #publishFrame()}, so no
     * synchronisation required.</p>
     */
    private GLSync previousFrameFence;

    /** Which texture the cursor thread is currently reading: 0 = texA, 1 = texB. */
    private volatile int currentReadIdx = 0;

    private boolean useArbFallback = false;

    private CursorPresentThread(long mainHwnd, long mainHdc, long cursorHglrc) {
        this.mainHwnd = mainHwnd;
        this.mainHdc = mainHdc;
        this.cursorHglrc = cursorHglrc;
        this.worker = new Thread(this::runLoop, "AngelicaCursorPresent");
        this.worker.setDaemon(true);
    }

    public static boolean isSupportedPlatform() {
        final String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("windows");
    }

    public static synchronized void ensureStarted() {
        if (INSTANCE != null) return;
        if (!isSupportedPlatform()) {
            LOGGER.info("Async cursor disabled: only Windows is supported in this build.");
            return;
        }
        if (!Display.isCreated()) {
            LOGGER.warn("Async cursor: Display not created yet, deferring.");
            return;
        }

        ensureReflectionInitialized();
        if (!reflectionOk) return;

        // Sanity check the API we depend on for the GPU copy: glCopyImageSubData lives in
        // OpenGL 4.3 core, with ARB_copy_image as the legacy fallback. If neither is present
        // we can't run the double-buffer copy efficiently — bail and stick with frame-locked
        // cursor rather than degrading silently.
        final ContextCapabilities caps = GLContext.getCapabilities();
        final boolean hasCore = caps.OpenGL43;
        final boolean hasArb = caps.GL_ARB_copy_image;
        if (!hasCore && !hasArb) {
            LOGGER.warn("Async cursor disabled: glCopyImageSubData (GL 4.3 / ARB_copy_image) not available.");
            return;
        }

        long mainHwnd = 0, mainHdc = 0, cursorHglrc = 0;
        try {
            // glfwGetCurrentContext returns the GLFW window whose context is current on the
            // calling thread — that's the main window since we're called from the main thread
            // with MC's context current.
            final long mainGlfw = invokeLong(REAL_GLFW_glfwGetCurrentContext);
            if (mainGlfw == 0L) throw new IllegalStateException("glfwGetCurrentContext() returned NULL");

            mainHwnd = invokeLong(REAL_GLFWNativeWin32_getWin32Window, mainGlfw);
            if (mainHwnd == 0L) throw new IllegalStateException("glfwGetWin32Window returned NULL");

            mainHdc = invokeLong(REAL_User32_GetDC, mainHwnd);
            if (mainHdc == 0L) throw new IllegalStateException("User32.GetDC returned NULL");

            final long mainHglrc = invokeLong(REAL_GLFWNativeWGL_getWGLContext, mainGlfw);
            if (mainHglrc == 0L) throw new IllegalStateException("glfwGetWGLContext returned NULL");

            // Match MC's GL version for cleanest sharing. Fall back to 3.2 compat if the query
            // can't be answered (e.g., GL_MAJOR_VERSION enum unsupported on a pre-3.0 context —
            // unlikely on Iris/Sodium, but we guard anyway).
            int major = GL11.glGetInteger(GL_MAJOR_VERSION);
            int minor = GL11.glGetInteger(GL_MINOR_VERSION);
            if (major <= 0) { major = 3; minor = 2; }

            final int[] attribs = {
                WGL_CONTEXT_MAJOR_VERSION_ARB, major,
                WGL_CONTEXT_MINOR_VERSION_ARB, minor,
                WGL_CONTEXT_PROFILE_MASK_ARB,  WGL_CONTEXT_COMPATIBILITY_PROFILE_BIT_ARB,
                0
            };
            cursorHglrc = invokeLong(REAL_WGLARBCreateContext_wglCreateContextAttribsARB,
                                     mainHdc, mainHglrc, attribs);
            if (cursorHglrc == 0L) throw new IllegalStateException("wglCreateContextAttribsARB returned NULL");

            // Crucially, wglCreateContextAttribsARB does NOT change the calling thread's current
            // context (unlike SDL_GL_CreateContext). Main's context stays current on the main
            // thread — no restore call needed.

            INSTANCE = new CursorPresentThread(mainHwnd, mainHdc, cursorHglrc);
            INSTANCE.useArbFallback = !hasCore && hasArb;
            INSTANCE.worker.start();
            LOGGER.info("Async cursor present thread started (GL {}.{} compat, using {}).",
                        major, minor, hasCore ? "GL4.3" : "ARB_copy_image");
        } catch (Throwable t) {
            LOGGER.error("Failed to set up async cursor context; feature disabled.", t);
            // Best-effort cleanup of anything we did allocate.
            try {
                if (cursorHglrc != 0L) invokeBool(REAL_WGL_wglDeleteContext, cursorHglrc);
            } catch (Throwable ignored) {}
            try {
                if (mainHdc != 0L && mainHwnd != 0L) invokeBool(REAL_User32_ReleaseDC, mainHwnd, mainHdc);
            } catch (Throwable ignored) {}
        }
    }

    public static synchronized void stop() {
        final CursorPresentThread it = INSTANCE;
        if (it == null) return;
        INSTANCE = null;
        it.running = false;
        try {
            it.worker.join(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // Cursor thread released its context in its finally block. Now we can destroy it.
        try {
            invokeBool(REAL_WGL_wglDeleteContext, it.cursorHglrc);
        } catch (Throwable t) {
            LOGGER.warn("wglDeleteContext failed on stop.", t);
        }
        try {
            invokeBool(REAL_User32_ReleaseDC, it.mainHwnd, it.mainHdc);
        } catch (Throwable t) {
            LOGGER.warn("User32.ReleaseDC failed on stop.", t);
        }
        // Free the textures on the main thread (caller). Safe to delete textures from any
        // sharing context per the spec.
        try {
            if (it.texA != 0) GL11.glDeleteTextures(it.texA);
            if (it.texB != 0) GL11.glDeleteTextures(it.texB);
        } catch (Throwable t) {
            LOGGER.warn("Error freeing present textures on stop.", t);
        }
        if (it.previousFrameFence != null) {
            deleteSyncReflective(it.previousFrameFence);
            it.previousFrameFence = null;
        }
    }

    public static boolean isRunning() {
        return INSTANCE != null;
    }

    /**
     * Called from the main thread at the end of its render. Copies framebufferMc's color into
     * the WRITE-side present texture (the one the cursor thread is not currently reading),
     * inserts a GL fence on the copy, and atomically publishes both. The cursor thread waits
     * on the fence before switching its read index — guaranteeing it never reads a texture
     * the GPU is still writing to.
     */
    public static void publishFrame() {
        final CursorPresentThread it = INSTANCE;
        if (it == null) return;
        try {
            // Rate-limit main to "one frame ahead of GPU". Wait for last frame's fence to
            // signal before queueing anything for this frame. See previousFrameFence javadoc.
            if (it.previousFrameFence != null) {
                clientWaitSyncReflective(it.previousFrameFence,
                                         GL32.GL_SYNC_FLUSH_COMMANDS_BIT,
                                         1_000_000_000L); // 1s — generous timeout for slow frames
                deleteSyncReflective(it.previousFrameFence);
                it.previousFrameFence = null;
            }

            final Minecraft mc = Minecraft.getMinecraft();
            if (mc == null) return;
            final Framebuffer fb = mc.getFramebuffer();
            if (fb == null) return;
            final int srcTex = fb.framebufferTexture;
            if (srcTex <= 0) return;
            final int w = fb.framebufferTextureWidth;
            final int h = fb.framebufferTextureHeight;
            if (w <= 0 || h <= 0) return;

            final boolean justAllocated;
            if (it.texA == 0 || it.texB == 0 || w != it.cachedW || h != it.cachedH) {
                LOGGER.info("Cursor present: (re)allocating textures at {}×{} (was {}×{})",
                            w, h, it.cachedW, it.cachedH);
                it.allocatePresentTextures(w, h);
                justAllocated = true;
            } else {
                justAllocated = false;
            }

            // Write to the texture the cursor thread is NOT currently reading.
            final int writeIdx = 1 - it.currentReadIdx;
            final int writeTex = (writeIdx == 0) ? it.texA : it.texB;

            // CRITICAL: glCopyImageSubData has undefined behavior when the source texture is
            // currently bound as a color attachment to the active framebuffer. We're called from
            // an inject at RETURN of updateCameraAndRender, but Minecraft.runGameLoop doesn't
            // unbind framebufferMc until AFTER updateCameraAndRender returns — meaning srcTex
            // (framebufferMc's color attachment) is still the active draw target right now. On
            // strict drivers the copy silently fails and writeTex gets garbage. Unbind to the
            // default framebuffer before the copy. MC re-binds framebufferMc at the top of the
            // next iteration so this has no other side effects.
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);

            if (it.useArbFallback) {
                ARBCopyImage.glCopyImageSubData(srcTex, GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
                                                 writeTex, GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
                                                 w, h, 1);
            } else {
                GL43.glCopyImageSubData(srcTex, GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
                                        writeTex, GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
                                        w, h, 1);
            }

            // If we just (re)allocated the textures, also copy framebufferMc into the OTHER
            // texture (the one cursor is reading right now). Otherwise the cursor thread's
            // next iteration would sample the just-allocated, never-written texture, whose
            // contents are undefined per the spec — many drivers return 0xFFFFFFFF (white),
            // producing a "full screen white flash" until the next publish.
            if (justAllocated) {
                final int otherTex = (writeIdx == 0) ? it.texB : it.texA;
                if (it.useArbFallback) {
                    ARBCopyImage.glCopyImageSubData(srcTex, GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
                                                     otherTex, GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
                                                     w, h, 1);
                } else {
                    GL43.glCopyImageSubData(srcTex, GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
                                            otherTex, GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
                                            w, h, 1);
                }
            }

            // Fence the copy. The cursor thread waits on this before switching its read index,
            // so it never samples writeTex while the GPU is still executing the copy.
            // glFenceSync exists in the lwjglx wrapper; the wait/delete pair is missing and is
            // called via reflection against real LWJGL3 — see REAL_GL32_glClientWaitSync.
            final GLSync fence = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            GL11.glFlush(); // ensure the fence command is actually submitted

            final Pending old = it.pendingPublish.getAndSet(new Pending(writeIdx, fence));
            if (old != null) {
                // Cursor thread didn't consume the previous publish before we made a new one
                // (cursor thread slept past two main frames, unlikely but possible). Delete
                // the orphaned fence on this thread (main context) so it doesn't leak.
                deleteSyncReflective(old.copyFence);
            }

            // Insert a fence at the end of this frame's GL work. Next call to publishFrame
            // will wait on this fence before queueing more — bounds main to "at most one
            // frame ahead of GPU" without forcing strict serial execution.
            it.previousFrameFence = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            GL11.glFlush();
        } catch (Throwable t) {
            LOGGER.warn("Failed to publish frame to cursor thread.", t);
        }
    }

    /** Return values from glClientWaitSync. */
    private static final int GL_ALREADY_SIGNALED  = 0x911A;
    private static final int GL_TIMEOUT_EXPIRED   = 0x911B;
    private static final int GL_CONDITION_SATISFIED = 0x911C;
    private static final int GL_WAIT_FAILED       = 0x911D;

    /** Returns the wait result code, or {@link #GL_WAIT_FAILED} on reflection error. */
    private static int clientWaitSyncReflective(GLSync fence, int flags, long timeoutNs) {
        final Method m = REAL_GL32_glClientWaitSync;
        if (m == null) return GL_WAIT_FAILED;
        try {
            return ((Number) m.invoke(null, fence.getPointer(), flags, timeoutNs)).intValue();
        } catch (Throwable t) {
            LOGGER.warn("glClientWaitSync invoke failed.", t);
            return GL_WAIT_FAILED;
        }
    }

    private static void deleteSyncReflective(GLSync fence) {
        final Method m = REAL_GL32_glDeleteSync;
        if (m == null) return;
        try {
            m.invoke(null, fence.getPointer());
        } catch (Throwable t) {
            LOGGER.warn("glDeleteSync invoke failed.", t);
        }
    }

    /** Allocate or resize the two present textures. Must be called on a GL-current thread. */
    private void allocatePresentTextures(int w, int h) {
        if (texA == 0) texA = GL11.glGenTextures();
        if (texB == 0) texB = GL11.glGenTextures();
        for (int tex : new int[] { texA, texB }) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0,
                              GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (IntBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        cachedW = w;
        cachedH = h;
    }

    private void runLoop() {
        // Make our wgl context current on the main window. From here on, our default
        // framebuffer IS the main window's framebuffer, our renders go there, and SwapBuffers
        // on mainHdc swaps what the user sees.
        try {
            if (!invokeBool(REAL_WGL_wglMakeCurrent, mainHdc, cursorHglrc)) {
                LOGGER.error("wglMakeCurrent failed on cursor thread; exiting.");
                return;
            }
            // LWJGL3 maintains per-thread GLCapabilities; populate them for this thread.
            REAL_GL_createCapabilities.invoke(null);

            // Vsync our context. Without this, SwapBuffers is non-blocking, so the cursor
            // thread queues swaps faster than the display drains them — the driver buffers
            // them, latency grows unbounded between input/sound and what the user sees, and
            // GPU bandwidth contention with the main thread tanks framerate. With swap-interval
            // 1, SwapBuffers blocks until V-blank, capping our swap rate at the display
            // refresh and bounding latency.
            if (REAL_WGLEXTSwapControl_wglSwapIntervalEXT != null) {
                try {
                    final boolean ok = (Boolean) REAL_WGLEXTSwapControl_wglSwapIntervalEXT.invoke(null, 1);
                    LOGGER.info("wglSwapIntervalEXT(1) returned {} — vsync {} active on cursor context.",
                                ok, ok ? "IS" : "is NOT");
                } catch (Throwable t) {
                    LOGGER.warn("wglSwapIntervalEXT(1) failed; running without vsync.", t);
                }
            } else {
                LOGGER.warn("WGLEXTSwapControl missing — cursor thread will not vsync.");
            }
            LOGGER.info("Async cursor thread context made current on main window.");
        } catch (Throwable t) {
            LOGGER.error("Async cursor thread failed to make context current; exiting.", t);
            return;
        }

        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_ALPHA_TEST);

            // No throttle: SwapBuffers blocks on V-blank because we set swap-interval 1, so
            // the loop naturally paces itself at the display refresh rate. Whatever the user's
            // monitor refresh is, that's our cursor rate.
            while (running) {
                try {
                    presentOnce();
                } catch (Throwable t) {
                    LOGGER.error("Async cursor present iteration failed; continuing.", t);
                }
            }
        } catch (Throwable t) {
            LOGGER.error("Async cursor thread crashed; feature will be inactive until restart.", t);
        } finally {
            // Release our context so main can wglDeleteContext it safely.
            try {
                invokeBool(REAL_WGL_wglMakeCurrent, 0L, 0L);
            } catch (Throwable t) {
                LOGGER.warn("Error releasing async cursor context.", t);
            }
            LOGGER.info("Async cursor thread exited.");
        }
    }

    /**
     * Read the current present-side texture (the one main thread is NOT writing into right now),
     * blit it as a fullscreen quad to the default framebuffer (= main window), draw the cursor
     * sprites on top, swap the main window.
     */
    private void presentOnce() {
        if (!StereoState.INSTANCE.isActive()) return;

        // Consume any newly-published frame. We wait on the copy fence in our own context so we
        // never sample writeTex while the GPU is still executing the copy that targeted it.
        //
        // The fence signals only after EVERY prior command in main's stream completes — that
        // includes all of Iris's shader passes, not just our copy. With shaders enabled the
        // drain can easily exceed 16ms (especially per-eye in stereo), and a too-short timeout
        // would return GL_TIMEOUT_EXPIRED while the copy is still in flight. If we then
        // switched currentReadIdx and sampled, we'd be reading a partially-written texture
        // (undefined per spec, often surfaces as all-white on Windows drivers — the "white
        // flash" symptom). Use a generous 1s timeout and, on the rare full timeout, keep the
        // existing read index for this iteration so we re-present the previous valid frame.
        final Pending pending = pendingPublish.getAndSet(null);
        if (pending != null) {
            final int waitResult = clientWaitSyncReflective(
                pending.copyFence, GL32.GL_SYNC_FLUSH_COMMANDS_BIT, 1_000_000_000L);
            deleteSyncReflective(pending.copyFence);
            if (waitResult == GL_ALREADY_SIGNALED || waitResult == GL_CONDITION_SATISFIED) {
                currentReadIdx = pending.writeIdx;
            }
            // GL_TIMEOUT_EXPIRED / GL_WAIT_FAILED: don't switch — keep showing the previous
            // frame for one more iteration rather than sampling a half-written texture.
        }

        final int idx = currentReadIdx;
        final int tex = (idx == 0) ? texA : texB;
        if (tex == 0) return; // first frame, no publish yet — skip; let main's first frame land

        final int w = Display.getWidth();
        final int h = Display.getHeight();
        if (w <= 0 || h <= 0) return;

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GL11.glViewport(0, 0, w, h);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0, w, h, 0, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1f, 1f, 1f, 1f);

        // Note V is flipped: framebufferMc / its copy has bottom-up origin in GL coords; we
        // draw in top-down ortho here.
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0f, 1f); GL11.glVertex2f(0, 0);
        GL11.glTexCoord2f(1f, 1f); GL11.glVertex2f(w, 0);
        GL11.glTexCoord2f(1f, 0f); GL11.glVertex2f(w, h);
        GL11.glTexCoord2f(0f, 0f); GL11.glVertex2f(0, h);
        GL11.glEnd();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        if (StereoCursor.isActive()) {
            drawStereoCursors(w, h);
        }

        try {
            invokeBool(REAL_GDI32_SwapBuffers, mainHdc);
        } catch (Throwable t) {
            LOGGER.warn("GDI32.SwapBuffers failed.", t);
        }
    }

    private static void drawStereoCursors(int fullW, int fullH) {
        final int halfW = fullW / 2;
        final int leftX = StereoCursor.virtualX();
        final int leftY = fullH - StereoCursor.virtualY() - 1;
        final int rightX = leftX + halfW;
        final int rightY = leftY;
        drawArrowAt(leftX, leftY);
        drawArrowAt(rightX, rightY);
    }

    private static void drawArrowAt(int x, int y) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(0f, 0f, 0f, 1f);
        for (int i = 0; i < 9; i++) {
            GL11.glRecti(x - 1, y + i - 1, x + i + 2, y + i);
        }
        GL11.glRecti(x - 1, y + 8, x + 9, y + 9);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        for (int i = 0; i < 8; i++) {
            GL11.glRecti(x, y + i, x + i + 1, y + i + 1);
        }
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }
}
