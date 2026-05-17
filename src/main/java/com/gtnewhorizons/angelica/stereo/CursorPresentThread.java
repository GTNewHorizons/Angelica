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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.concurrent.atomic.AtomicReference;

/**
 * GLFW binds each GL context to its creation window, so lwjglx's SharedDrawable would create a
 * hidden window for the secondary context and its swap wouldn't reach what the user sees. We
 * bypass GLFW and use WGL directly: grab the main window's HWND/HDC + main's HGLRC, then
 * wglCreateContextAttribsARB(mainHdc, mainHglrc, attribs) for a context that shares resources
 * with main and renders into the main window. lwjgl3ify rewrites org/lwjgl/* bytecode refs to
 * org/lwjglx/* but NOT Class.forName string literals — reflection hits real LWJGL3.
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
    // Win32 IDC_ARROW resource ID — passed to LoadCursorW as MAKEINTRESOURCE-style integer pointer
    private static final long IDC_ARROW = 32512L;

    private static volatile CursorPresentThread INSTANCE;

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

    private static volatile long PFN_LoadCursorW;
    private static volatile long PFN_GetIconInfo;
    private static volatile long PFN_CreateCompatibleDC;
    private static volatile long PFN_DeleteDC;
    private static volatile long PFN_DeleteObject;
    private static volatile long PFN_GetObject;
    private static volatile long PFN_SelectObject;
    private static volatile long PFN_GetPixel;

    private static volatile Method JNI_invokePI;
    private static volatile Method JNI_invokePI_PII;
    private static volatile Method JNI_invokePP;
    private static volatile Method JNI_invokePPI;
    private static volatile Method JNI_invokePPI_PIP;
    private static volatile Method JNI_invokePPP;
    private static volatile Method MEMUTIL_memAddress;
    private static volatile boolean cursorReflectionInitialized = false;
    private static volatile boolean cursorReflectionOk = false;

    private static volatile boolean cursorTextureTried = false;
    private static volatile int cursorTexId = 0;
    private static volatile int cursorTexW = 0;
    private static volatile int cursorTexH = 0;
    private static volatile int cursorHotspotX = 0;
    private static volatile int cursorHotspotY = 0;

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

    private static synchronized void ensureCursorReflectionInitialized() {
        if (cursorReflectionInitialized) return;
        try {
            final Class<?> winLibCls = Class.forName("org.lwjgl.system.windows.WindowsLibrary");
            final Class<?> funcProvCls = Class.forName("org.lwjgl.system.FunctionProvider");
            final Class<?> jniCls = Class.forName("org.lwjgl.system.JNI");
            final Class<?> memUtilCls = Class.forName("org.lwjgl.system.MemoryUtil");

            final Constructor<?> winLibCtor = winLibCls.getConstructor(String.class);
            final Object user32 = winLibCtor.newInstance("user32");
            final Object gdi32 = winLibCtor.newInstance("gdi32");

            final Method getFnAddr = funcProvCls.getMethod("getFunctionAddress", CharSequence.class);
            // LoadCursorW(NULL, IDC_ARROW) reliably returns the standard arrow regardless of
            // what the system happens to be showing at the moment of capture. GetCursor() would
            // return whatever's currently displayed — e.g., the wait spinner if a background
            // process briefly took over — and we'd be stuck with that for the session.
            PFN_LoadCursorW        = (Long) getFnAddr.invoke(user32, "LoadCursorW");
            PFN_GetIconInfo        = (Long) getFnAddr.invoke(user32, "GetIconInfo");
            PFN_CreateCompatibleDC = (Long) getFnAddr.invoke(gdi32, "CreateCompatibleDC");
            PFN_DeleteDC           = (Long) getFnAddr.invoke(gdi32, "DeleteDC");
            PFN_DeleteObject       = (Long) getFnAddr.invoke(gdi32, "DeleteObject");
            PFN_GetObject          = (Long) getFnAddr.invoke(gdi32, "GetObjectW");
            PFN_SelectObject       = (Long) getFnAddr.invoke(gdi32, "SelectObject");
            PFN_GetPixel           = (Long) getFnAddr.invoke(gdi32, "GetPixel");

            if (PFN_LoadCursorW == 0L || PFN_GetIconInfo == 0L || PFN_CreateCompatibleDC == 0L
                || PFN_DeleteDC == 0L || PFN_DeleteObject == 0L || PFN_GetObject == 0L
                || PFN_SelectObject == 0L || PFN_GetPixel == 0L) {
                LOGGER.warn("One or more Win32 cursor function pointers failed to resolve;"
                          + " LoadCursorW={}, GetIconInfo={}, CreateCompatibleDC={}, DeleteDC={},"
                          + " DeleteObject={}, GetObjectW={}, SelectObject={}, GetPixel={}.",
                          PFN_LoadCursorW, PFN_GetIconInfo, PFN_CreateCompatibleDC, PFN_DeleteDC,
                          PFN_DeleteObject, PFN_GetObject, PFN_SelectObject, PFN_GetPixel);
                return;
            }

            JNI_invokePI      = jniCls.getMethod("invokePI",  long.class, long.class);
            JNI_invokePI_PII  = jniCls.getMethod("invokePI",  long.class, int.class, int.class, long.class);
            JNI_invokePP      = jniCls.getMethod("invokePP",  long.class, long.class);
            JNI_invokePPI     = jniCls.getMethod("invokePPI", long.class, long.class, long.class);
            JNI_invokePPI_PIP = jniCls.getMethod("invokePPI", long.class, int.class, long.class, long.class);
            JNI_invokePPP     = jniCls.getMethod("invokePPP", long.class, long.class, long.class);
            MEMUTIL_memAddress = memUtilCls.getMethod("memAddress", ByteBuffer.class);

            cursorReflectionOk = true;
        } catch (Throwable t) {
            LOGGER.warn("Could not resolve cursor capture reflection; falling back to drawn arrow.", t);
        }
        cursorReflectionInitialized = true;
    }

    private static long invokeLong(Method m, Object... args) throws Throwable {
        return ((Number) m.invoke(null, args)).longValue();
    }

    private static boolean invokeBool(Method m, Object... args) throws Throwable {
        return (Boolean) m.invoke(null, args);
    }

    private final Thread worker;
    private volatile boolean running = true;

    private final long mainHwnd;
    private final long mainHdc;
    private final long cursorHglrc;

    private volatile int texA = 0;
    private volatile int texB = 0;
    private volatile int cachedW = 0;
    private volatile int cachedH = 0;

    private static final class Pending {
        final int writeIdx;
        final GLSync copyFence;
        Pending(int writeIdx, GLSync copyFence) {
            this.writeIdx = writeIdx;
            this.copyFence = copyFence;
        }
    }
    private final AtomicReference<Pending> pendingPublish = new AtomicReference<>();

    private GLSync previousFrameFence;

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
            final long mainGlfw = invokeLong(REAL_GLFW_glfwGetCurrentContext);
            if (mainGlfw == 0L) throw new IllegalStateException("glfwGetCurrentContext() returned NULL");

            mainHwnd = invokeLong(REAL_GLFWNativeWin32_getWin32Window, mainGlfw);
            if (mainHwnd == 0L) throw new IllegalStateException("glfwGetWin32Window returned NULL");

            mainHdc = invokeLong(REAL_User32_GetDC, mainHwnd);
            if (mainHdc == 0L) throw new IllegalStateException("User32.GetDC returned NULL");

            final long mainHglrc = invokeLong(REAL_GLFWNativeWGL_getWGLContext, mainGlfw);
            if (mainHglrc == 0L) throw new IllegalStateException("glfwGetWGLContext returned NULL");

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

            // wglCreateContextAttribsARB does NOT change the calling thread's current context, so
            // main's context stays current on the main thread — no restore call needed.
            INSTANCE = new CursorPresentThread(mainHwnd, mainHdc, cursorHglrc);
            INSTANCE.useArbFallback = !hasCore && hasArb;
            INSTANCE.worker.start();
            LOGGER.info("Async cursor present thread started (GL {}.{} compat, using {}).",
                        major, minor, hasCore ? "GL4.3" : "ARB_copy_image");
        } catch (Throwable t) {
            LOGGER.error("Failed to set up async cursor context; feature disabled.", t);
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
        try {
            if (it.texA != 0) GL11.glDeleteTextures(it.texA);
            if (it.texB != 0) GL11.glDeleteTextures(it.texB);
            if (cursorTexId != 0) GL11.glDeleteTextures(cursorTexId);
        } catch (Throwable t) {
            LOGGER.warn("Error freeing present textures on stop.", t);
        }
        cursorTexId = 0;
        cursorTextureTried = false;
        if (it.previousFrameFence != null) {
            deleteSyncReflective(it.previousFrameFence);
            it.previousFrameFence = null;
        }
    }

    public static boolean isRunning() {
        return INSTANCE != null;
    }

    public static void publishFrame() {
        final CursorPresentThread it = INSTANCE;
        if (it == null) return;
        try {
            // Rate-limit main to "one frame ahead of GPU" — wait for last frame's fence before
            // queueing this frame's commands. Without this, driver queue depth grows unbounded
            // and input-to-display latency blows up.
            if (it.previousFrameFence != null) {
                clientWaitSyncReflective(it.previousFrameFence,
                                         GL32.GL_SYNC_FLUSH_COMMANDS_BIT,
                                         1_000_000_000L);
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

            // glCopyImageSubData is undefined when the source texture is bound as a color
            // attachment to the active framebuffer. Unbind to the default FB before the copy;
            // MC re-binds framebufferMc at the top of the next iteration.
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

            // On (re)allocate, also seed the OTHER texture so the cursor thread doesn't sample
            // an uninitialized texture next iteration (Windows drivers often return all-white).
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

            // Fence the copy. Cursor thread waits on this before switching its read index, so
            // it never samples writeTex while the GPU is still executing the copy. glFenceSync
            // exists in lwjglx; the wait/delete pair is reflective against real LWJGL3.
            final GLSync fence = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            GL11.glFlush();

            final Pending old = it.pendingPublish.getAndSet(new Pending(writeIdx, fence));
            if (old != null) {
                deleteSyncReflective(old.copyFence);
            }

            it.previousFrameFence = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            GL11.glFlush();
        } catch (Throwable t) {
            LOGGER.warn("Failed to publish frame to cursor thread.", t);
        }
    }

    private static final int GL_ALREADY_SIGNALED  = 0x911A;
    private static final int GL_TIMEOUT_EXPIRED   = 0x911B;
    private static final int GL_CONDITION_SATISFIED = 0x911C;
    private static final int GL_WAIT_FAILED       = 0x911D;

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
        // Make our wgl context current on the main window. Our default framebuffer is now the
        // main window's framebuffer; SwapBuffers(mainHdc) swaps what the user sees.
        try {
            if (!invokeBool(REAL_WGL_wglMakeCurrent, mainHdc, cursorHglrc)) {
                LOGGER.error("wglMakeCurrent failed on cursor thread; exiting.");
                return;
            }
            // LWJGL3 maintains per-thread GLCapabilities; populate them for this thread.
            REAL_GL_createCapabilities.invoke(null);

            // Vsync via swap-interval 1: SwapBuffers blocks on V-blank, capping cursor rate at
            // the display refresh and bounding latency. Without it the driver queues swaps
            // unboundedly and GPU bandwidth contention with the main thread tanks framerate.
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

            // SwapBuffers blocks on V-blank (swap-interval 1) so the loop paces itself.
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

    private void presentOnce() {
        if (!StereoState.INSTANCE.isActive()) return;

        // Consume any newly-published frame. Wait on the copy fence with a generous 1s timeout —
        // it signals only after EVERY prior command in main's stream completes (including Iris's
        // shader passes), so per-eye stereo + shaders can push the drain well past one frame.
        // On full timeout, KEEP the existing read index — switching would sample a partially-
        // written texture (undefined per spec, typically all-white on Windows drivers).
        final Pending pending = pendingPublish.getAndSet(null);
        if (pending != null) {
            final int waitResult = clientWaitSyncReflective(
                pending.copyFence, GL32.GL_SYNC_FLUSH_COMMANDS_BIT, 1_000_000_000L);
            deleteSyncReflective(pending.copyFence);
            if (waitResult == GL_ALREADY_SIGNALED || waitResult == GL_CONDITION_SATISFIED) {
                currentReadIdx = pending.writeIdx;
            }
        }

        final int idx = currentReadIdx;
        final int tex = (idx == 0) ? texA : texB;
        if (tex == 0) return;

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

        // V is flipped: framebufferMc is bottom-up GL coords, we draw in top-down ortho.
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
        ensureCursorTexture();
        if (cursorTexId != 0) {
            drawCursorTextureAt(leftX, leftY);
            drawCursorTextureAt(rightX, rightY);
        } else {
            drawArrowAt(leftX, leftY);
            drawArrowAt(rightX, rightY);
        }
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

    private static void drawCursorTextureAt(int x, int y) {
        // Half X width: each SBS half is stretched 2× horizontally by the display, so a sprite
        // drawn at natural pixel width here would render as a 2× wide oval. Pre-compress on X
        // (size and hotspot offset) so it lands at native dimensions after stretching. Y is
        // unaffected — SBS only compresses horizontally.
        final int halfW = Math.max(1, cursorTexW / 2);
        final int halfHotX = cursorHotspotX / 2;
        final int x0 = x - halfHotX;
        final int y0 = y - cursorHotspotY;
        final int x1 = x0 + halfW;
        final int y1 = y0 + cursorTexH;
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, cursorTexId);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0f, 0f); GL11.glVertex2f(x0, y0);
        GL11.glTexCoord2f(1f, 0f); GL11.glVertex2f(x1, y0);
        GL11.glTexCoord2f(1f, 1f); GL11.glVertex2f(x1, y1);
        GL11.glTexCoord2f(0f, 1f); GL11.glVertex2f(x0, y1);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    private static void ensureCursorTexture() {
        if (cursorTextureTried) return;
        cursorTextureTried = true;
        ensureCursorReflectionInitialized();
        if (!cursorReflectionOk) return;
        try {
            // LoadCursorW(hInstance=NULL, lpCursorName=MAKEINTRESOURCE(IDC_ARROW)). Win32 detects
            // resource-ID-as-pointer by zero high bits.
            final long hCursor = (Long) JNI_invokePPP.invoke(null, 0L, IDC_ARROW, PFN_LoadCursorW);
            if (hCursor == 0L) {
                LOGGER.warn("LoadCursorW(IDC_ARROW) returned NULL; cursor capture aborted.");
                return;
            }
            // ICONINFO on x64 (32 bytes total):
            //   off  0: BOOL  fIcon       (4)
            //   off  4: DWORD xHotspot    (4)
            //   off  8: DWORD yHotspot    (4)
            //   off 12: pad for HBITMAP alignment (4)
            //   off 16: HBITMAP hbmMask   (8)
            //   off 24: HBITMAP hbmColor  (8)
            final ByteBuffer iconBuf = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder());
            final long iconAddr = (Long) MEMUTIL_memAddress.invoke(null, iconBuf);
            final int rc = (Integer) JNI_invokePPI.invoke(null, hCursor, iconAddr, PFN_GetIconInfo);
            if (rc == 0) {
                LOGGER.warn("GetIconInfo failed; cursor capture aborted.");
                return;
            }
            final int xHot = iconBuf.getInt(4);
            final int yHot = iconBuf.getInt(8);
            final long hbmMask = iconBuf.getLong(16);
            final long hbmColor = iconBuf.getLong(24);
            try {
                captureToTexture(hbmColor, hbmMask, xHot, yHot);
            } finally {
                // GetIconInfo docs: caller owns hbmColor and hbmMask — must DeleteObject them.
                if (hbmColor != 0L) try { JNI_invokePI.invoke(null, hbmColor, PFN_DeleteObject); } catch (Throwable t) {}
                if (hbmMask  != 0L) try { JNI_invokePI.invoke(null, hbmMask,  PFN_DeleteObject); } catch (Throwable t) {}
            }
        } catch (Throwable t) {
            LOGGER.warn("OS cursor capture failed; falling back to drawn arrow.", t);
        }
    }

    private static void captureToTexture(long hbmColor, long hbmMask, int xHot, int yHot) throws Throwable {
        final boolean useColor = hbmColor != 0L;
        final long primary = useColor ? hbmColor : hbmMask;
        if (primary == 0L) return;

        // BITMAP on x64 (32 bytes): bmType (4), bmWidth (4), bmHeight (4), bmWidthBytes (4),
        // bmPlanes (2), bmBitsPixel (2), pad (4), bmBits (8).
        final ByteBuffer bmpBuf = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder());
        final long bmpAddr = (Long) MEMUTIL_memAddress.invoke(null, bmpBuf);
        final int got = (Integer) JNI_invokePPI_PIP.invoke(null, primary, 32, bmpAddr, PFN_GetObject);
        if (got == 0) {
            LOGGER.warn("GetObjectW on cursor bitmap returned 0.");
            return;
        }
        final int w = bmpBuf.getInt(4);
        final int rawH = bmpBuf.getInt(8);
        final int absH = Math.abs(rawH);
        if (w <= 0 || absH == 0) {
            LOGGER.warn("Cursor bitmap has invalid dimensions {}x{}.", w, rawH);
            return;
        }
        if (!useColor && (absH % 2 != 0)) {
            LOGGER.warn("Mono cursor mask has odd height {}; cannot split AND/XOR.", absH);
            return;
        }
        final int finalH = useColor ? absH : (absH / 2);

        final long memDC = (Long) JNI_invokePP.invoke(null, 0L, PFN_CreateCompatibleDC);
        if (memDC == 0L) {
            LOGGER.warn("CreateCompatibleDC failed; cursor capture aborted.");
            return;
        }

        final byte[] colorRGB = new byte[w * finalH * 3];
        final byte[] alpha = new byte[w * finalH];

        try {
            final long colorSrc = useColor ? hbmColor : hbmMask;
            final long prevColor = (Long) JNI_invokePPP.invoke(null, memDC, colorSrc, PFN_SelectObject);
            if (prevColor == 0L) {
                LOGGER.warn("SelectObject(color) failed.");
                return;
            }
            try {
                for (int y = 0; y < finalH; y++) {
                    final int srcY = useColor ? y : (y + finalH);
                    for (int x = 0; x < w; x++) {
                        final int c = (Integer) JNI_invokePI_PII.invoke(null, memDC, x, srcY, PFN_GetPixel);
                        final int o = (y * w + x) * 3;
                        colorRGB[o]     = (byte) (c & 0xFF);
                        colorRGB[o + 1] = (byte) ((c >> 8) & 0xFF);
                        colorRGB[o + 2] = (byte) ((c >> 16) & 0xFF);
                    }
                }
            } finally {
                JNI_invokePPP.invoke(null, memDC, prevColor, PFN_SelectObject);
            }

            if (hbmMask != 0L) {
                final long prevMask = (Long) JNI_invokePPP.invoke(null, memDC, hbmMask, PFN_SelectObject);
                if (prevMask == 0L) {
                    LOGGER.warn("SelectObject(mask) failed; assuming fully opaque.");
                    for (int i = 0; i < alpha.length; i++) alpha[i] = (byte) 0xFF;
                } else {
                    try {
                        for (int y = 0; y < finalH; y++) {
                            for (int x = 0; x < w; x++) {
                                final int m = (Integer) JNI_invokePI_PII.invoke(null, memDC, x, y, PFN_GetPixel);
                                alpha[y * w + x] = (m == 0) ? (byte) 0xFF : 0;
                            }
                        }
                    } finally {
                        JNI_invokePPP.invoke(null, memDC, prevMask, PFN_SelectObject);
                    }
                }
            } else {
                for (int i = 0; i < alpha.length; i++) alpha[i] = (byte) 0xFF;
            }

            final ByteBuffer rgba = ByteBuffer.allocateDirect(w * finalH * 4).order(ByteOrder.nativeOrder());
            if (useColor) {
                for (int i = 0; i < w * finalH; i++) {
                    final int co = i * 3;
                    final int ro = i * 4;
                    rgba.put(ro,     colorRGB[co]);
                    rgba.put(ro + 1, colorRGB[co + 1]);
                    rgba.put(ro + 2, colorRGB[co + 2]);
                    rgba.put(ro + 3, alpha[i]);
                }
            } else {
                for (int i = 0; i < w * finalH; i++) {
                    final boolean opaque = alpha[i] != 0;
                    final boolean xorWhite = (colorRGB[i * 3] & 0xFF) != 0;
                    final byte v, a;
                    if (!opaque && !xorWhite) { v = 0;           a = 0; }
                    else if (!opaque)         { v = (byte) 0xFF; a = (byte) 0xFF; }
                    else if (!xorWhite)       { v = 0;           a = (byte) 0xFF; }
                    else                      { v = (byte) 0xFF; a = (byte) 0xFF; }
                    final int ro = i * 4;
                    rgba.put(ro,     v);
                    rgba.put(ro + 1, v);
                    rgba.put(ro + 2, v);
                    rgba.put(ro + 3, a);
                }
            }
            rgba.position(0);

            final int texId = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, finalH, 0,
                              GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, rgba);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

            cursorTexId = texId;
            cursorTexW = w;
            cursorTexH = finalH;
            cursorHotspotX = xHot;
            cursorHotspotY = yHot;
            LOGGER.info("OS cursor captured: {}x{}, hotspot=({},{}), color={}, texId={}",
                        w, finalH, xHot, yHot, useColor, texId);
        } finally {
            try { JNI_invokePI.invoke(null, memDC, PFN_DeleteDC); } catch (Throwable t) {}
        }
    }
}
