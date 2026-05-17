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
 *
 * <h2>Triple-buffered producer/consumer (correctness proof — load-bearing)</h2>
 *
 * <p>Three shared present textures plus a one-slot atomic mailbox. The three texture indices
 * are partitioned across three roles at all times:</p>
 * <ul>
 *   <li>{@code mainWriteIdx} — exclusively read/written by main.</li>
 *   <li>{@code cursorReadIdx} — exclusively read/written by cursor.</li>
 *   <li>{@code pending} — atomic slot holding the third index plus an optional fence.
 *       {@code fence != null} means "main published fresh content"; {@code null} means "stale,
 *       no publish since last consume".</li>
 * </ul>
 *
 * <p>{@link #publishFrame()}: main writes {@code tex[mainWriteIdx]}, fences, then
 * {@code getAndSet}s a new slot into {@code pending}; the returned (old) slot's idx becomes
 * the new {@code mainWriteIdx}. If the old slot held a non-null fence, cursor never consumed
 * it and the fence is deleted.</p>
 *
 * <p>Each cursor iteration: peek {@code pending}, and if its fence is non-null, attempt a
 * {@code compareAndSet} replacing it with {@code (cursorReadIdx, null)}. On success, cursor
 * waits the peeked fence, sets {@code cursorReadIdx = peeked.idx}, and renders. Doing the
 * {@code compareAndSet} <em>before</em> the wait is what makes the orphaned-fence delete in
 * main safe: any concurrent main publish either (a) races ahead — our CAS fails atomically
 * and we never wait on the deleted fence — or (b) races behind and sees our null-fence slot —
 * no fence to delete. The fence we hold post-CAS is exclusively ours.</p>
 *
 * <p>Net effect: (a) main never overwrites a texture cursor is reading or about to read, and
 * (b) the fence object lifetime has exactly one owner at each instant. The previous
 * double-buffered design could let main's <em>next</em> copy land in the texture cursor
 * switched to right after the fence signaled, producing torn frames (top rows fresh, bottom
 * rows still in flight).</p>
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
    private static volatile long PFN_GetCursorPos;
    private static volatile long PFN_ClipCursor;
    private static volatile long PFN_GetClientRect;
    private static volatile long PFN_ClientToScreen;
    private static volatile long PFN_GetForegroundWindow;
    private static volatile Method REAL_GLFW_glfwSetInputMode;
    private static volatile Method JNI_invokeP_singleArg;  // (funcPtr) → long — GetForegroundWindow
    private static volatile long mainGlfwWindow = 0L;

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
            // Cursor thread polls GetCursorPos every iteration to drive vX/vY at its own rate
            // (decoupled from main's input poll cadence). Requires the GLFW cursor mode to be
            // HIDDEN, not DISABLED, so GetCursorPos returns the real moving cursor position.
            PFN_GetCursorPos       = (Long) getFnAddr.invoke(user32, "GetCursorPos");
            // ClipCursor confines the cursor to MC's client rect while MC has focus, so
            // motion can't accidentally drag the cursor into another window/monitor.
            PFN_ClipCursor         = (Long) getFnAddr.invoke(user32, "ClipCursor");
            PFN_GetClientRect      = (Long) getFnAddr.invoke(user32, "GetClientRect");
            PFN_ClientToScreen     = (Long) getFnAddr.invoke(user32, "ClientToScreen");
            PFN_GetForegroundWindow = (Long) getFnAddr.invoke(user32, "GetForegroundWindow");

            final Class<?> glfw = Class.forName("org.lwjgl.glfw.GLFW");
            REAL_GLFW_glfwSetInputMode = glfw.getMethod("glfwSetInputMode", long.class, int.class, int.class);
            JNI_invokeP_singleArg = jniCls.getMethod("invokeP", long.class);

            if (PFN_LoadCursorW == 0L || PFN_GetIconInfo == 0L || PFN_CreateCompatibleDC == 0L
                || PFN_DeleteDC == 0L || PFN_DeleteObject == 0L || PFN_GetObject == 0L
                || PFN_SelectObject == 0L || PFN_GetPixel == 0L || PFN_GetCursorPos == 0L) {
                LOGGER.warn("One or more Win32 cursor function pointers failed to resolve;"
                          + " LoadCursorW={}, GetIconInfo={}, CreateCompatibleDC={}, DeleteDC={},"
                          + " DeleteObject={}, GetObjectW={}, SelectObject={}, GetPixel={}, GetCursorPos={}.",
                          PFN_LoadCursorW, PFN_GetIconInfo, PFN_CreateCompatibleDC, PFN_DeleteDC,
                          PFN_DeleteObject, PFN_GetObject, PFN_SelectObject, PFN_GetPixel, PFN_GetCursorPos);
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

    // Triple-buffered present textures shared between main and cursor contexts. Allocated lazily
    // on main's first publishFrame. Ownership rotates per class-javadoc proof.
    private final int[] tex = new int[3];
    private volatile int cachedW = 0;
    private volatile int cachedH = 0;

    // Main's exclusive write target — only touched on main, no volatile needed.
    private int mainWriteIdx = 0;
    // Cursor's exclusive read target — only touched on cursor, no volatile needed.
    private int cursorReadIdx = 1;

    private static final class Slot {
        final int idx;
        final GLSync fence; // null = stale, no new publish
        Slot(int idx, GLSync fence) {
            this.idx = idx;
            this.fence = fence;
        }
    }
    // Initial slot holds index 2 with no fence; main owns 0, cursor owns 1. After the first
    // successful publish, the slot cycles among the three indices.
    private final AtomicReference<Slot> pending = new AtomicReference<>(new Slot(2, null));

    private GLSync previousFrameFence;

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
            // Cache for setCursorHidden() which also runs on main thread but in a different stack.
            mainGlfwWindow = mainGlfw;

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
            for (int i = 0; i < it.tex.length; i++) {
                if (it.tex[i] != 0) {
                    GL11.glDeleteTextures(it.tex[i]);
                    it.tex[i] = 0;
                }
            }
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
        // Clean up any fence still sitting in the pending slot. Cursor thread has already
        // joined, so no one else can be waiting on it.
        final Slot stale = it.pending.get();
        if (stale != null && stale.fence != null) {
            deleteSyncReflective(stale.fence);
        }
    }

    public static boolean isRunning() {
        return INSTANCE != null;
    }

    // GLFW cursor mode constants (from GLFW/glfw3.h). Public so callers can request a specific
    // mode via setCursorMode (used to forcibly resync GLFW + MC's cached grab flag).
    private static final int GLFW_CURSOR = 0x33001;
    public static final int CURSOR_NORMAL = 0x34001;
    public static final int CURSOR_HIDDEN = 0x34002;
    public static final int CURSOR_DISABLED = 0x34003;

    /**
     * Toggle the OS cursor mode on the main window between visible-normal and hidden-but-tracking.
     * HIDDEN (vs DISABLED) is load-bearing: with DISABLED, GetCursorPos returns the locked center
     * forever and the cursor present thread can't observe live motion. With HIDDEN, the OS cursor
     * is invisible but its position tracks the mouse normally, so the cursor thread can poll
     * GetCursorPos each iter to drive the virtual cursor at its own rate (decoupled from main's
     * framerate).
     */
    public static void setCursorHidden(boolean hidden) {
        setCursorMode(hidden ? CURSOR_HIDDEN : CURSOR_NORMAL);
    }

    /**
     * Force the GLFW cursor mode on the main window. Needed to override MC's cached grab flag —
     * {@code Mouse.setGrabbed} no-ops when MC thinks the requested state matches its cache, but
     * we may have changed GLFW behind its back.
     */
    public static void setCursorMode(int mode) {
        ensureCursorReflectionInitialized();
        if (!cursorReflectionOk || REAL_GLFW_glfwSetInputMode == null) return;
        if (mainGlfwWindow == 0L) {
            try {
                ensureReflectionInitialized();
                if (REAL_GLFW_glfwGetCurrentContext != null) {
                    mainGlfwWindow = ((Number) REAL_GLFW_glfwGetCurrentContext.invoke(null)).longValue();
                }
            } catch (Throwable t) {
                LOGGER.warn("Failed to resolve GLFW window handle for cursor-mode set.", t);
                return;
            }
        }
        if (mainGlfwWindow == 0L) return;
        try {
            REAL_GLFW_glfwSetInputMode.invoke(null, mainGlfwWindow, GLFW_CURSOR, mode);
        } catch (Throwable t) {
            LOGGER.warn("Failed to set cursor mode (mode=0x{}).", Integer.toHexString(mode), t);
        }
    }

    /**
     * Poll the Windows OS cursor position via GetCursorPos and return the delta from the last
     * call. First call after activation returns (0,0) and seeds the baseline. Returns null on
     * any failure. Called from the cursor present thread only.
     */
    private final long[] lastRawCursor = { Long.MIN_VALUE, Long.MIN_VALUE };
    private final ByteBuffer rawCursorPoint = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder());
    private long rawCursorPointAddr = 0L;
    private boolean pollCursorDelta(int[] outDelta) {
        if (PFN_GetCursorPos == 0L || JNI_invokePI == null || MEMUTIL_memAddress == null) return false;
        try {
            if (rawCursorPointAddr == 0L) {
                rawCursorPointAddr = ((Number) MEMUTIL_memAddress.invoke(null, rawCursorPoint)).longValue();
            }
            final int rc = ((Number) JNI_invokePI.invoke(null, rawCursorPointAddr, PFN_GetCursorPos)).intValue();
            if (rc == 0) return false;
            final int curX = rawCursorPoint.getInt(0);
            final int curY = rawCursorPoint.getInt(4);
            if (lastRawCursor[0] == Long.MIN_VALUE) {
                lastRawCursor[0] = curX;
                lastRawCursor[1] = curY;
                outDelta[0] = 0;
                outDelta[1] = 0;
                return true;
            }
            outDelta[0] = (int) (curX - lastRawCursor[0]);
            outDelta[1] = (int) (curY - lastRawCursor[1]);
            lastRawCursor[0] = curX;
            lastRawCursor[1] = curY;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Reset cursor-position baseline so the next poll seeds rather than accumulates a delta. */
    public static void resetCursorPolling() {
        final CursorPresentThread it = INSTANCE;
        if (it == null) return;
        it.lastRawCursor[0] = Long.MIN_VALUE;
        it.lastRawCursor[1] = Long.MIN_VALUE;
        it.lastClipApplied = false;
    }

    // Reusable buffers for ClipCursor / GetClientRect / ClientToScreen (avoid per-iter alloc).
    // RECT is 4 LONGs = 16 bytes (left, top, right, bottom). POINT is 2 LONGs = 8 bytes.
    private final ByteBuffer clipRect = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
    private final ByteBuffer clipPt = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder());
    private long clipRectAddr = 0L;
    private long clipPtAddr = 0L;
    private boolean lastClipApplied = false;

    /**
     * If MC's main window is the foreground window, confine the cursor to its client rect via
     * {@code ClipCursor}. If MC isn't focused, do nothing — Windows auto-releases the clip when
     * the owning window loses focus, so the user retains free control over their other windows.
     * Re-applying every iter is necessary because Windows clears the clip on focus loss, and we
     * need to re-establish it on focus regain.
     */
    private void maintainClipCursor() {
        if (PFN_ClipCursor == 0L || PFN_GetClientRect == 0L
            || PFN_ClientToScreen == 0L || PFN_GetForegroundWindow == 0L
            || JNI_invokeP_singleArg == null || JNI_invokePPI == null
            || JNI_invokePI == null || MEMUTIL_memAddress == null) return;
        try {
            final long fg = ((Number) JNI_invokeP_singleArg.invoke(null, PFN_GetForegroundWindow)).longValue();
            if (fg != mainHwnd) {
                // Lost focus: Windows already cleared the clip. Note that so the next focus-gain
                // triggers a fresh apply (cheap; just sets flag).
                lastClipApplied = false;
                return;
            }
            if (!lastClipApplied) {
                // Just regained focus. The user moved the cursor in another window while we were
                // unfocused; if we don't reset the baseline, pollCursorDelta will compute a huge
                // delta from the pre-unfocus position to the current position, jumping the virtual
                // cursor to a clamped edge. Reset so the next poll just seeds at the snapped
                // post-ClipCursor position with zero delta.
                lastRawCursor[0] = Long.MIN_VALUE;
                lastRawCursor[1] = Long.MIN_VALUE;
            }
            if (clipRectAddr == 0L) {
                clipRectAddr = ((Number) MEMUTIL_memAddress.invoke(null, clipRect)).longValue();
                clipPtAddr = ((Number) MEMUTIL_memAddress.invoke(null, clipPt)).longValue();
            }
            // GetClientRect returns (0, 0, width, height) in client coords.
            final int gcrRc = ((Number) JNI_invokePPI.invoke(null, mainHwnd, clipRectAddr, PFN_GetClientRect)).intValue();
            if (gcrRc == 0) return;
            final int clientW = clipRect.getInt(8);
            final int clientH = clipRect.getInt(12);
            // ClientToScreen translates (0,0) → screen coords of the client area's top-left.
            clipPt.putInt(0, 0);
            clipPt.putInt(4, 0);
            final int ctsRc = ((Number) JNI_invokePPI.invoke(null, mainHwnd, clipPtAddr, PFN_ClientToScreen)).intValue();
            if (ctsRc == 0) return;
            final int sx = clipPt.getInt(0);
            final int sy = clipPt.getInt(4);
            // Build the screen-space RECT and clip.
            clipRect.putInt(0, sx);
            clipRect.putInt(4, sy);
            clipRect.putInt(8, sx + clientW);
            clipRect.putInt(12, sy + clientH);
            JNI_invokePI.invoke(null, clipRectAddr, PFN_ClipCursor);
            if (!lastClipApplied) {
                LOGGER.info("ClipCursor first apply: hwnd=0x{}, GetClientRect=({}x{}), ClientToScreen(0,0)=({},{}), final rect=({},{},{},{})",
                    Long.toHexString(mainHwnd), clientW, clientH, sx, sy,
                    sx, sy, sx + clientW, sy + clientH);
            }
            lastClipApplied = true;

            // Read OS cursor position (now snapped inside the clip rect we just applied) and
            // map it absolutely to the virtual cursor. Absolute positioning — not delta
            // accumulation — guarantees no drift between OS cursor and virtual cursor when the
            // user pushes against an edge.
            if (PFN_GetCursorPos != 0L) {
                clipPt.putInt(0, 0);
                clipPt.putInt(4, 0);
                final int gcpRc = ((Number) JNI_invokePI.invoke(null, clipPtAddr, PFN_GetCursorPos)).intValue();
                if (gcpRc != 0) {
                    final int cx = clipPt.getInt(0);
                    final int cy = clipPt.getInt(4);
                    final int relX = cx - sx;
                    final int relY = cy - sy;
                    // X scaled by 0.5 because SBS_HALF compresses each eye horizontally; Y is
                    // 1:1 but flipped (Win32 grows down, vY grows up).
                    final double vx = relX * 0.5;
                    final double vy = (clientH - 1) - relY;
                    StereoCursor.setVirtualPos(vx, vy);
                }
            }
        } catch (Throwable t) {
            // Don't spam on persistent failure — disable for the rest of the session by zeroing the fn ptrs.
            LOGGER.warn("ClipCursor maintenance failed; disabling clip for this session.", t);
            PFN_ClipCursor = 0L;
        }
    }

    /** Release any ClipCursor confinement. Called when leaving stereo+GUI. */
    public static void releaseClipCursor() {
        final CursorPresentThread it = INSTANCE;
        if (it == null) return;
        if (PFN_ClipCursor == 0L || JNI_invokePI == null) return;
        try {
            // ClipCursor(NULL) releases the confinement.
            JNI_invokePI.invoke(null, 0L, PFN_ClipCursor);
            it.lastClipApplied = false;
        } catch (Throwable t) {
            LOGGER.warn("ClipCursor release failed.", t);
        }
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
            if (it.tex[0] == 0 || w != it.cachedW || h != it.cachedH) {
                LOGGER.info("Cursor present: (re)allocating textures at {}×{} (was {}×{})",
                            w, h, it.cachedW, it.cachedH);
                it.allocatePresentTextures(w, h);
                justAllocated = true;
            } else {
                justAllocated = false;
            }

            // Triple-buffer: main writes to its exclusively-owned index. mainWriteIdx rotates
            // below by taking the index of the slot we just swapped out — guaranteeing main
            // never targets the texture cursor is reading or about to read.
            final int writeTex = it.tex[it.mainWriteIdx];

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

            // On (re)allocate, also seed the OTHER two textures so the cursor thread doesn't
            // sample an uninitialized texture next iteration (Windows drivers often return all-white).
            if (justAllocated) {
                for (int i = 0; i < 3; i++) {
                    if (i == it.mainWriteIdx) continue;
                    final int otherTex = it.tex[i];
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
            }

            // Fence the copy. Cursor CAS-claims and waits this before sampling the published
            // texture, so it never reads a region the GPU is still writing. glFenceSync exists
            // in lwjglx; the wait/delete pair is reflective against real LWJGL3.
            final GLSync fence = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            GL11.glFlush();

            // Atomic publish: swap our newly-written slot into pending and take the old slot's
            // idx as our next write target. Concurrent cursor CAS either (a) raced ahead and
            // replaced the slot with a null-fence — we get that back, no delete; or (b) races
            // behind and its CAS will fail against newSlot — so it never waits on the orphan
            // fence we delete below.
            final Slot newSlot = new Slot(it.mainWriteIdx, fence);
            final Slot prev = it.pending.getAndSet(newSlot);
            it.mainWriteIdx = prev.idx;
            if (prev.fence != null) {
                // Cursor never consumed the previous publish — main outpaced it. Safe to delete
                // per the proof above: no cursor thread can be mid-wait on this fence.
                deleteSyncReflective(prev.fence);
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
        for (int i = 0; i < tex.length; i++) {
            if (tex[i] == 0) tex[i] = GL11.glGenTextures();
        }
        for (int i = 0; i < tex.length; i++) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex[i]);
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

    // Diagnostic counters: accumulate per-iteration timing and dump averages every N iters to
    // tell whether cursor latency is dominated by fence wait (main slow) or non-vsynced swap.
    private long diagIterCount = 0;
    private long diagWaitNs = 0;
    private long diagSwapNs = 0;
    private long diagIterNs = 0;
    private static final int DIAG_REPORT_EVERY = 100;

    private final int[] cursorDeltaScratch = new int[2];

    private void presentOnce() {
        if (!StereoState.INSTANCE.isActive()) return;

        // Maintain ClipCursor on MC's client area while focused. maintainClipCursor also reads
        // the OS cursor pos AFTER applying the clip so the OS cursor we observe is already
        // snapped inside the rect, and writes vX/vY absolutely from there.
        if (StereoCursor.isActive()) {
            maintainClipCursor();
        }

        final long iterStartNs = System.nanoTime();

        // Triple-buffer claim: peek the pending slot, then compareAndSet to take it before
        // waiting on its fence. The peek-then-CAS ordering is load-bearing — see class proof.
        // The fence signals only after EVERY prior command in main's stream completes
        // (including Iris's shader passes), so per-eye stereo + shaders can push the drain
        // well past one frame; use a generous 1s timeout. On full timeout, KEEP the existing
        // read index — switching would sample a partially-written texture (undefined per
        // spec, typically all-white on Windows drivers).
        final long waitStartNs = System.nanoTime();
        final Slot peeked = pending.get();
        if (peeked.fence != null) {
            final Slot replacement = new Slot(cursorReadIdx, null);
            if (pending.compareAndSet(peeked, replacement)) {
                // We exclusively own peeked.fence now. Main's next getAndSet returns our
                // null-fence replacement and won't delete it; cursor is the sole owner.
                final int waitResult = clientWaitSyncReflective(
                    peeked.fence, GL32.GL_SYNC_FLUSH_COMMANDS_BIT, 1_000_000_000L);
                deleteSyncReflective(peeked.fence);
                if (waitResult == GL_ALREADY_SIGNALED || waitResult == GL_CONDITION_SATISFIED) {
                    cursorReadIdx = peeked.idx;
                }
                // GL_TIMEOUT_EXPIRED / GL_WAIT_FAILED: don't switch — keep showing the previous
                // frame. The just-claimed slot already cycled in our null-fence replacement, so
                // main will reclaim that idx on its next publish; nothing leaks.
            }
            // CAS failed: main published between our peek and our CAS. Keep showing the previous
            // frame this iteration; the new publish is visible next iteration.
        }
        final long waitEndNs = System.nanoTime();

        final int tex = this.tex[cursorReadIdx];
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

        final long swapStartNs = System.nanoTime();
        try {
            invokeBool(REAL_GDI32_SwapBuffers, mainHdc);
        } catch (Throwable t) {
            LOGGER.warn("GDI32.SwapBuffers failed.", t);
        }
        final long swapEndNs = System.nanoTime();

        diagIterCount++;
        diagWaitNs += (waitEndNs - waitStartNs);
        diagSwapNs += (swapEndNs - swapStartNs);
        diagIterNs += (swapEndNs - iterStartNs);
        if (diagIterCount >= DIAG_REPORT_EVERY) {
            final double avgWaitMs = (diagWaitNs / (double) diagIterCount) / 1_000_000.0;
            final double avgSwapMs = (diagSwapNs / (double) diagIterCount) / 1_000_000.0;
            final double avgIterMs = (diagIterNs / (double) diagIterCount) / 1_000_000.0;
            final double effectiveHz = avgIterMs > 0 ? 1000.0 / avgIterMs : 0;
            LOGGER.info(String.format(
                "Cursor present timing (avg over %d iters): wait=%.2fms, swap=%.2fms, iter=%.2fms (%.1f Hz)",
                diagIterCount, avgWaitMs, avgSwapMs, avgIterMs, effectiveHz));
            diagIterCount = 0;
            diagWaitNs = 0;
            diagSwapNs = 0;
            diagIterNs = 0;
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
