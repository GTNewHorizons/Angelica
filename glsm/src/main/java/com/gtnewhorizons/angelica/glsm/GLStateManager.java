package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizon.gtnhlib.client.renderer.stacks.IStateStack;
import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.glsm.DisplayListManager.RecordMode;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.backend.VSyncMode;
import com.gtnewhorizons.angelica.glsm.ffp.VAOManager;
import com.gtnewhorizons.angelica.glsm.ffp.VertexKey;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.hooks.BatchStateGuard;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMInitConfig;
import com.gtnewhorizons.angelica.glsm.recording.ImmediateModeRecorder;
import com.gtnewhorizons.angelica.glsm.stacks.CowStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.StackIdAllocator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.Drawable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GLContext;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import static com.gtnewhorizons.angelica.glsm.Vendor.AMD;
import static com.gtnewhorizons.angelica.glsm.Vendor.INTEL;
import static com.gtnewhorizons.angelica.glsm.Vendor.MESA;
import static com.gtnewhorizons.angelica.glsm.Vendor.NVIDIA;
import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;

/**
 * GLSM core: per-thread context lookup ({@link #ctx()}), the exit-capture/batch-guard prologue, limits and lifecycle; GL entry points live in the GLSM group classes.
 * The static initializer queries the backend, so this class must not load before a GL context is current; call {@link #initialize(GLSMInitConfig)} after context creation.
 */
@SuppressWarnings("unused") // Used in ASM
public class GLStateManager {

    public static void beginExitCapture(ExitState result) { ctx().exitCapture.begin(result); }
    public static boolean finishExitCapture(ExitState result) { return ctx().exitCapture.finish(result); }
    public static boolean canApplyExitState(ExitState result) { return ctx().exitCapture.canApply(result); }
    public static void applyExitState(ExitState result) { ctx().exitCapture.apply(result); }
    public static void cancelExitCapture() { ctx().exitCapture.cancel(); }
    public static boolean suspendExitCapture() {
        if (!ExitStateCapture.anyOpen()) return false;
        ctx().exitCapture.suspend();
        return true;
    }
    public static void resumeExitCapture(boolean suspended) { if (suspended) ctx().exitCapture.resume(); }
    public static boolean isExitCaptureActive() { return ExitStateCapture.anyOpen() && ctx().exitCapture.active(); }
    public static void requireExitDrawState(boolean materialFromState) {
        if (ExitStateCapture.anyOpen()) ctx().exitCapture.requireDrawState(materialFromState);
    }
    public static void requireExitVertexDefaults(boolean normal, boolean texCoord, boolean color, boolean lightmap) {
        if (ExitStateCapture.anyOpen()) ctx().exitCapture.requireVertexDefaults(normal, texCoord, color, lightmap);
    }
    public static boolean beginExitTextureFilter(boolean nearest) { return ExitStateCapture.anyOpen() && ctx().exitCapture.beginTextureFilter(nearest); }
    public static void finishExitTextureFilterSave() { ctx().exitCapture.finishTextureFilterSave(); }
    public static boolean beginExitTextureFilterRestore() { return ExitStateCapture.anyOpen() && ctx().exitCapture.beginTextureFilterRestore(); }
    public static void finishExitTextureFilterRestore() { ctx().exitCapture.finishTextureFilterRestore(); }
    public static void failExitTextureFilter() { ctx().exitCapture.failTextureFilter(); }
    public static void requireExitTextureFilter(int texture, boolean nearest) {
        if (ExitStateCapture.anyOpen()) ctx().exitCapture.requireTextureFilter(texture, nearest);
    }
    public static int getExitPolygonOffsetWrites() { return ExitStateCapture.anyOpen() ? ctx().exitCapture.polygonOffsetWrites() : 0; }
    public static boolean isExitLightingWritten() { return ExitStateCapture.anyOpen() && ctx().exitCapture.lightingWritten(); }

    public static void recordExitCapability(GLContextState context, int cap, int unit) {
        if (context != null) context.exitCapture.capability(cap, unit);
    }

    public static void recordExitNormal() {
        if (ExitStateCapture.anyOpen()) ctx().exitCapture.write(ExitStateCapture.NORMAL);
    }

    public static void recordExitTexCoord(int unit) {
        if (!ExitStateCapture.anyOpen()) return;
        if (unit == 0) ctx().exitCapture.write(ExitStateCapture.TEXCOORD);
        else ctx().exitCapture.reject();
    }

    static void rejectExitCapture() {
        if (ExitStateCapture.anyOpen()) ctx().exitCapture.reject();
    }

    static void recordExitTextureBind(int target, int unit) {
        if (ExitStateCapture.anyOpen()) recordExitTextureBind(ctx(), target, unit);
    }

    static void recordExitTextureBind(GLContextState glCtx, int target, int unit) {
        if (target == GL11.GL_TEXTURE_2D && unit == 0) glCtx.exitCapture.write(ExitStateCapture.TEXTURE_BINDING);
        else glCtx.exitCapture.reject();
    }

    /**
     * Called before a mutation, while deferred draws can still use their original state. State the batcher does not
     * capture is state an exit capture cannot apply either, so this also rejects any open exit capture.
     */
    public static void beforeUncapturedStateChange() {
        rejectExitCapture();
        if (BatchStateGuard.isSuspended()) return;
        if (DisplayListManager.getRecordMode() == RecordMode.COMPILE) return;
        BatchStateGuard.beforeChange();
        ctx().batchStateGeneration++;
    }

    public static void beforeUncapturedCapabilityChange(int cap) {
        if (cap == GL11.GL_FOG || cap == GL11.GL_SCISSOR_TEST || cap == GL11.GL_STENCIL_TEST
            || cap == GL11.GL_COLOR_LOGIC_OP || cap == GL14.GL_COLOR_SUM || cap == GL30.GL_RASTERIZER_DISCARD
            || cap == GL13.GL_SAMPLE_ALPHA_TO_COVERAGE || cap == GL13.GL_SAMPLE_ALPHA_TO_ONE || cap == GL13.GL_SAMPLE_COVERAGE
            || (cap >= GL11.GL_CLIP_PLANE0 && cap < GL11.GL_CLIP_PLANE0 + MAX_CLIP_PLANES)) {
            beforeUncapturedStateChange();
        }
    }

    public static void beforeUncapturedTextureUnitChange(int cap, int unit) {
        if (cap == GL11.GL_TEXTURE_2D && unit <= 1) return;
        beforeUncapturedStateChange();
    }

    public static final Logger LOGGER = LogManager.getLogger("GLSM");

    private static final Set<String> WARN_ONCE = ConcurrentHashMap.newKeySet();

    public static void warnOnce(String key, String fmt, Object... args) {
        if (WARN_ONCE.add(key)) LOGGER.warn(fmt, args);
    }

    // The key check comes first so a caller repeating the mistake every frame does not capture a stack each time.
    static void warnOnceTrace(String key, String fmt, Object... args) {
        if (!WARN_ONCE.add(key)) return;
        final Object[] withTrace = Arrays.copyOf(args, args.length + 1);
        withTrace[args.length] = new Throwable("call site");
        LOGGER.warn(fmt, withTrace);
    }

    static volatile GLContextState primaryContext;
    private static final ThreadLocal<GLContextState> tlWorkerContext = new ThreadLocal<>();
    private static GLContextState mainCtx;
    private static volatile boolean workerContextsActive;
    private static int workerContextCount;
    private static final IdentityHashMap<Object, GLContextState> drawableContexts = new IdentityHashMap<>();
    static GLContextState[] drawableContextStates = new GLContextState[0];

    public static GLContextState ctx() {
        if (Thread.currentThread() == MainThread) {
            final GLContextState c = mainCtx;
            return c != null ? c : (mainCtx = primaryContext());
        }
        if (!workerContextsActive) return primaryContext();
        final GLContextState st = tlWorkerContext.get();
        return st != null ? st : primaryContext();
    }

    public static GLContextState primaryContext() {
        GLContextState p = primaryContext;
        if (p != null) return p;
        synchronized (GLStateManager.class) {
            p = primaryContext;
            if (p == null) {
                StackIdAllocator.beginContext();
                p = new GLContextState();
                primaryContext = p;
                p.init();
                StackIdAllocator.endContext();
            }
        }
        return p;
    }

    private static GLContextState newContext() {
        final GLContextState st;
        synchronized (GLStateManager.class) {
            StackIdAllocator.beginContext();
            st = new GLContextState();
            st.init();
            StackIdAllocator.endContext();
        }
        return st;
    }

    private static boolean bindThreadContext(GLContextState target) {
        final GLContextState prev = tlWorkerContext.get();
        if (prev == target) return false;
        synchronized (GLStateManager.class) {
            if (prev == null) workerContextCount++;
            if (target == null) workerContextCount--;
            workerContextsActive = workerContextCount != 0;
        }
        if (target == null) tlWorkerContext.remove(); else tlWorkerContext.set(target);
        if (Thread.currentThread() == MainThread) mainCtx = target;
        return true;
    }

    static GLContextState enterWorkerContext() {
        if (tlWorkerContext.get() != null) {
            throw new IllegalStateException("Thread " + Thread.currentThread() + " is already bound to a worker context");
        }
        final GLContextState st = newContext();
        bindThreadContext(st);
        return st;
    }

    static void exitWorkerContext() {
        bindThreadContext(null);
    }

    private static GLContextState contextFor(Drawable drawable) {
        synchronized (GLStateManager.class) {
            GLContextState st = drawableContexts.get(drawable);
            if (st == null) {
                st = newContext();
                setDrawableSize(st, Display.getWidth(), Display.getHeight());
                applyStencilBitMask(st, primaryContext().stencilBitMask);
                drawableContexts.put(drawable, st);
                final GLContextState[] states = Arrays.copyOf(drawableContextStates, drawableContextStates.length + 1);
                states[states.length - 1] = st;
                drawableContextStates = states;
            }
            return st;
        }
    }

    private static void setDrawableSize(GLContextState c, int w, int h) {
        mod(c.viewportState).setViewPort(0, 0, w, h);
        mod(c.scissorState).setScissor(0, 0, w, h);
    }

    private static void applyStencilBitMask(GLContextState c, int mask) {
        c.stencilBitMask = mask;
        c.stencilState.setValueMaskFront(mask);
        c.stencilState.setValueMaskBack(mask);
        c.stencilState.setWriteMaskFront(mask);
        c.stencilState.setWriteMaskBack(mask);
    }

    private static boolean isDisplayDrawable(Drawable d) {
        return d == Display.getDrawable();
    }

    // Thread Checking - must be early in static init order so isMainThread() works for state initialization
    @Getter static final Thread MainThread = Thread.currentThread();

    public static boolean isMainThread() {
        return Thread.currentThread() == MainThread;
    }

    public static void requireContext(String call) {
        rejectExitCapture();
        if (Thread.currentThread() == MainThread || RENDER_BACKEND.hasContextOnThread()) return;
        throw new IllegalStateException(call + " on thread " + Thread.currentThread().getName() + ", which has no GL context");
    }

    @Getter static GLSMInitConfig initConfig;

    public static ContextCapabilities capabilities;

    // Software stack depths for FFP emulation
    public static final int MAX_ATTRIB_STACK_DEPTH = 32;
    public static final int RETAINED_SLOT = MAX_ATTRIB_STACK_DEPTH;
    public static final int STATE_SLOTS = MAX_ATTRIB_STACK_DEPTH + 1;
    public static final int MAX_MODELVIEW_STACK_DEPTH = 32 + 2;
    public static final int MAX_PROJECTION_STACK_DEPTH = 4;
    public static final int MAX_TEXTURE_STACK_DEPTH = 4;
    public static final int MAX_CLIP_PLANES = 8;
    public static final int INDEXED_BUFFER_SLOTS = 64;
    public static final int MAX_TEXTURE_UNITS = RENDER_BACKEND.getInteger(GL20.GL_MAX_TEXTURE_IMAGE_UNITS);
    public static final int MAX_LEGACY_TEXTURE_UNITS = Math.min(VertexKey.MAX_UNITS, MAX_TEXTURE_UNITS);
    public static final int MAX_TEXTURE_COORDS = Math.min(VAOManager.TEX_COORD_UNITS, MAX_TEXTURE_UNITS);

    public static final GLFeatureSet HAS_MULTIPLE_SET = new GLFeatureSet();

    @Getter private static Vendor VENDOR;

    @Getter private static boolean windows;

    private static volatile Thread CurrentThread = MainThread;
    @Setter @Getter private static boolean runningSplash = true;

    static volatile boolean splashComplete = false;
    private static volatile Thread splashDisplayReleaser;
    @Getter @Setter private static volatile Thread drawableGLHolder = MainThread;
    // Reference to DrawableGL (main display context) - works with both FML and BLS splash
    @Setter private static volatile Drawable drawableGL = null;

    private static final ReentrantLock DRAW_LOCK = new ReentrantLock();

    public static boolean acquireDrawLock() {
        if (splashComplete) return false;
        DRAW_LOCK.lock();
        return true;
    }

    public static void releaseDrawLock() {
        DRAW_LOCK.unlock();
    }

    private static volatile boolean stateSeedPending = false;

    public static boolean takeStateSeedPending() {
        if (!stateSeedPending) return false;
        stateSeedPending = false;
        return true;
    }

    public static boolean isCachingEnabled() {
        if (splashComplete) return true;
        final Thread t = Thread.currentThread();
        return t == drawableGLHolder || tlWorkerContext.get() != null;
    }

    /**
     * Check if splash screen is complete. After splash, there's only one GL context and no locking is needed.
     */
    public static boolean isSplashComplete() {
        return splashComplete;
    }

    static <T extends CowStateStack<?>> T mod(T s) {
        s.beforeModify();
        return s;
    }

    public static void reset() {
        final GLContextState glCtx = ctx();
        glCtx.exitCapture.cancel();
        DisplayListManager.abortCompilation();
        glCtx.attribDepth = 0;
        glCtx.retainedOwner = null;
        for (int i = 0; i < glCtx.attribSets.length; i++) {
            glCtx.attribSets[i] = null;
        }
        for (int i = 0; i < glCtx.modifiedCount.length; i++) {
            glCtx.modifiedCount[i] = 0;
        }

        final IStateStack<?>[] stacks = glCtx.allStacks;
        for (final IStateStack<?> stack : stacks) {
            ((CowStateStack<?>) stack).drain();
        }

        Arrays.fill(glCtx.savedMvGen, 0);
        Arrays.fill(glCtx.savedMvLinearGen, 0);
        Arrays.fill(glCtx.savedProjGen, 0);
        Arrays.fill(glCtx.savedTexMatGen, 0);
        Arrays.fill(glCtx.savedLightingGen, 0);
        Arrays.fill(glCtx.savedFragmentGen, 0);
        Arrays.fill(glCtx.savedColorGen, 0);
        Arrays.fill(glCtx.savedNormalGen, 0);
        Arrays.fill(glCtx.savedTexCoordGen, 0);

        glCtx.modelViewMatrix.clear();
        glCtx.projectionMatrix.clear();
        glCtx.mvGeneration++;
        glCtx.mvLinearGeneration++;
        glCtx.projGeneration++;
        glCtx.texMatrixGeneration++;
        glCtx.lightingGeneration++;
        glCtx.colorGeneration++;
        glCtx.texGenGeneration++;
        glCtx.clipPlaneGeneration++;
        glCtx.fragmentGeneration++;
        glCtx.programGeneration++;
        glCtx.ffp.normalGeneration++;
        glCtx.ffp.texCoordGeneration++;
        glCtx.textures.texMatIdentityGen++;

        glCtx.drawFramebuffer = 0;
        glCtx.readFramebuffer = 0;
        glCtx.drawFramebufferGeneration++;

        if (RENDER_BACKEND != null) {
            RENDER_BACKEND.invalidateStateMirror();
            GLSMAttribStack.replayStateToBackend();
        }
    }

    public static class GLFeatureSet extends IntOpenHashSet {

        private static final long serialVersionUID = 8558779940775721010L;

        public GLFeatureSet addFeature(int feature) {
            super.add(feature);
            return this;
        }

    }

    public static void initialize(GLSMInitConfig config) {
        initConfig = config;
        final int width = config.getDisplayWidth() > 0 ? config.getDisplayWidth() : Display.getWidth();
        final int height = config.getDisplayHeight() > 0 ? config.getDisplayHeight() : Display.getHeight();
        preInit(width, height);
        init(config.getPostInitCallback());
    }

    static void preInit(int displayWidth, int displayHeight) {
        capabilities = GLContext.getCapabilities();
        HAS_MULTIPLE_SET
            .addFeature(GL11.GL_ACCUM_CLEAR_VALUE)
            .addFeature(GL14.GL_BLEND_COLOR)
            .addFeature(GL11.GL_COLOR_CLEAR_VALUE)
            .addFeature(GL11.GL_COLOR_WRITEMASK)
            .addFeature(GL11.GL_CURRENT_COLOR)
            .addFeature(GL11.GL_CURRENT_NORMAL)
            .addFeature(GL14.GL_POINT_DISTANCE_ATTENUATION)
            .addFeature(GL14.GL_CURRENT_SECONDARY_COLOR)
            .addFeature(GL11.GL_CURRENT_RASTER_COLOR)
            .addFeature(GL11.GL_CURRENT_RASTER_POSITION)
            .addFeature(GL11.GL_CURRENT_RASTER_TEXTURE_COORDS)
            .addFeature(GL11.GL_CURRENT_TEXTURE_COORDS)
            .addFeature(GL11.GL_DEPTH_RANGE)
            .addFeature(GL11.GL_FOG_COLOR)
            .addFeature(GL11.GL_LIGHT_MODEL_AMBIENT)
            .addFeature(GL11.GL_LINE_WIDTH_RANGE)
            .addFeature(GL11.GL_MAP1_GRID_DOMAIN)
            .addFeature(GL11.GL_MAP2_GRID_DOMAIN)
            .addFeature(GL11.GL_MAP2_GRID_SEGMENTS)
            .addFeature(GL11.GL_MAX_VIEWPORT_DIMS)
            .addFeature(GL11.GL_MODELVIEW_MATRIX)
            .addFeature(GL11.GL_POINT_SIZE_RANGE)
            .addFeature(GL11.GL_POLYGON_MODE)
            .addFeature(GL11.GL_PROJECTION_MATRIX)
            .addFeature(GL11.GL_SCISSOR_BOX)
            .addFeature(GL11.GL_TEXTURE_ENV_COLOR)
            .addFeature(GL11.GL_TEXTURE_MATRIX)
            .addFeature(GL11.GL_VIEWPORT);

        if (displayWidth > 0 && displayHeight > 0) {
            // Initialize viewport state from display dimensions.
            // After Display.create(), viewport is (0, 0, width, height)
            setDrawableSize(ctx(), displayWidth, displayHeight);
        }

        final String glVendor = RENDER_BACKEND.getString(GL11.GL_VENDOR);
        if (glVendor == null) {
            LOGGER.error("Backend {} reported no GL_VENDOR (context: {}); treating the vendor as unknown", RENDER_BACKEND.getName(), RENDER_BACKEND.hasContext());
        }
        VENDOR = Vendor.getVendor(glVendor == null ? "" : glVendor.toLowerCase());

        final String os = System.getProperty("os.name").toLowerCase();
        windows = os.contains("win");
    }

    static void init(Runnable initCallback) {
        final GLContextState glCtx = ctx();
        RenderSystem.initRenderer();

        GLSMFramebuffer.queryDefaultDrawBuffer();

        // The initial mask value should be defined as all 1's. However, some drivers have it set to 0's.
        // To ensure consistency & correctness across all drivers, we're setting them to 0xFF.
        RENDER_BACKEND.stencilFunc(glCtx.stencilState.getFuncFront(), glCtx.stencilState.getRefFront(), 0xFF);
        RENDER_BACKEND.stencilMask(0xFF);

        // Compute stencil bit mask - driver clamps stencil masks to buffer depth
        // GL_STENCIL_BITS was removed in core profile; query via default FBO attachment
        final int stencilBits = RENDER_BACKEND.getFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, GL11.GL_STENCIL, GL30.GL_FRAMEBUFFER_ATTACHMENT_STENCIL_SIZE);
        final int stencilBitMask = stencilBits >= 32 ? 0xFFFFFFFF : (1 << stencilBits) - 1;
        if (stencilBitMask == 0) {
            LOGGER.warn("Default framebuffer reports 0 stencil bits on backend {}; all stencil masks clamp to 0", RENDER_BACKEND.getClass().getSimpleName());
        }

        // Initialize stencil masks from computed bit mask
        applyStencilBitMask(glCtx, stencilBitMask);

        GLSMRasterizer.probeLineWidthRange();

        // Sync default vertex attribs with GLSM cache initial values.
        RENDER_BACKEND.vertexAttrib4f(1, 1.0f, 1.0f, 1.0f, 1.0f);    // COLOR = white
        RENDER_BACKEND.vertexAttrib3f(4, 0.0f, 0.0f, 1.0f);          // NORMAL = +Z

        if (initConfig != null && (initConfig.isLwjglDebug() || CaptureGate.enabledAtStartup())) {
            if (RENDER_BACKEND.supportsDebugOutput()) {
                GLDebug.initDebugState();
                if (initConfig.isLwjglDebug()) {
                    LOGGER.info("Enabling additional LWJGL debug output");
                    GLDebug.setupDebugMessageCallback();
                }
                GLDebug.debugMessage("Angelica Debug Annotator Initialized");
            } else {
                LOGGER.info("Debug annotations requested but unavailable in this context (requires GL 4.3 / GL_KHR_debug or SDL debug labels); skipping");
            }
        }

        glCtx.defaultVAO = RENDER_BACKEND.genVertexArrays();
        RENDER_BACKEND.bindVertexArray(glCtx.defaultVAO);
        glCtx.boundVAO = glCtx.defaultVAO;
        glCtx.vaos.init(glCtx.defaultVAO);
        if (glCtx.defaultVAO != 0) {
            RENDER_BACKEND.provokingVertex(GL32.GL_LAST_VERTEX_CONVENTION);
        }
        if (initCallback != null) {
            initCallback.run();
        }

        GLSMRasterizer.probePointSizeRange(glCtx);

        // Drain any pending GL errors from initialization. In core profile, some legacy queries may generate GL_INVALID_ENUM. The splash thread inherits the
        // DrawableGL context and its error state, so stale errors here would cause SplashProgress.checkGLError() to fail.
        int err;
        while ((err = RENDER_BACKEND.getError()) != 0) {
            LOGGER.debug("Drained GL error 0x{} during init", Integer.toHexString(err));
        }
    }

    public static void beginForeignDraw() {
        ctx().foreignDrawDepth++;
    }

    public static void endForeignDraw() {
        final GLContextState glCtx = ctx();
        if (glCtx.foreignDrawDepth > 0 && --glCtx.foreignDrawDepth == 0) {
            GLSMHooks.resolvePendingProgram();
            if (GLSMHooks.FOREIGN_DRAW_END.hasListeners()) {
                GLSMHooks.FOREIGN_DRAW_END.post(GLSMHooks.foreignDrawEndEvent);
            }
        }
    }

    public static boolean isForeignDraw() {
        return ctx().foreignDrawDepth > 0;
    }

    public static void makeCurrent(Drawable drawable) throws LWJGLException {
        final GLContextState previous = tlWorkerContext.get();
        final boolean changed = bindThreadContext(isDisplayDrawable(drawable) ? null : contextFor(drawable));
        final boolean handled;
        try {
            handled = BackendManager.RENDER_BACKEND != null && BackendManager.RENDER_BACKEND.handleMakeCurrent(drawable);
            if (!handled) drawable.makeCurrent();
        } catch (Exception | Error failure) {
            if (changed) bindThreadContext(previous);
            throw failure;
        }
        if (!handled && changed) GLSMAttribStack.replayStateToBackend();
        CurrentThread = Thread.currentThread();

        if (splashComplete) return;
        if (Thread.currentThread() == MainThread && isDisplayDrawable(drawable)) {
            final Thread r = splashDisplayReleaser;
            if (r != null && !r.isAlive()) {
                markSplashComplete("splashThreadExit");
                return;
            }
        }

        if (drawableGL == null && isDisplayDrawable(drawable)) drawableGL = drawable;

        if (drawableGL != null && drawable == drawableGL) {
            // Switching TO DrawableGL - enable caching for this thread
            drawableGLHolder = CurrentThread;
        } else if (drawableGLHolder == CurrentThread) {
            // This thread held DrawableGL but is switching AWAY - disable caching
            drawableGLHolder = null;
        }
        // else: Thread switching to non-DrawableGL, wasn't holder - no change needed
    }

    public static void releaseContext(Drawable drawable) throws LWJGLException {
        final boolean handled = BackendManager.RENDER_BACKEND != null && BackendManager.RENDER_BACKEND.handleReleaseContext(drawable);
        if (!handled) {
            drawable.releaseContext();
        }
        bindThreadContext(null);
        if (drawableGLHolder == Thread.currentThread()) {
            drawableGLHolder = null;
        }
        if (!splashComplete && Thread.currentThread() != MainThread && isDisplayDrawable(drawable)) {
            splashDisplayReleaser = Thread.currentThread();
        }
    }

    public static boolean isCurrent() {
        return RENDER_BACKEND.isCurrent();
    }

    public static void swapBuffers() throws LWJGLException {
        if (RENDER_BACKEND.handleSwapBuffers()) {
            return;
        }
        Display.swapBuffers();
    }

    public static void updateDisplay() throws LWJGLException {
        updateDisplay(true);
    }

    public static void updateDisplay(boolean processMessages) throws LWJGLException {
        if (Thread.currentThread() != MainThread) {
            RENDER_BACKEND.updateDisplayFromWorkerThread(processMessages);
            return;
        }
        if (processMessages) pumpDisplayMessages();
        if (!RENDER_BACKEND.wantsDisplayUpdateGateTiming()) {
            Display.update(false);
            return;
        }
        final long start = System.nanoTime();
        Display.update(false);
        final long end = System.nanoTime();
        RENDER_BACKEND.recordGate(end - start, end);
        GLSMFramebuffer.armFirstClear();
    }

    /**
     * Mark splash as complete - enables fast path that always caches. Called when finish() permanently switches to DrawableGL for the main game loop.
     */
    public static void markSplashComplete(String source) {
        if (splashComplete) return;
        LOGGER.info("Splash complete (source={}, thread={})", source, Thread.currentThread().getName());
        splashComplete = true;
        stateSeedPending = true;
        drawableGLHolder = null;
        drawableGL = null;
        ImmediateModeRecorder.cleanupOrphanTessellators();
        if (GLSMHooks.LOADING_CHECKPOINT.hasListeners()) {
            GLSMHooks.loadingCheckpointEvent.requiresSync = true;
            GLSMHooks.LOADING_CHECKPOINT.post(GLSMHooks.loadingCheckpointEvent);
            GLSMHooks.loadingCheckpointEvent.requiresSync = false;
        }
    }

    /**
     * Check if we're currently recording a display list.
     */
    public static boolean isRecordingDisplayList() {
        return DisplayListManager.isRecording();
    }

    public static void setVSyncEnabled(boolean enabled) {
        RENDER_BACKEND.setVSyncEnabled(enabled);
    }

    public static void setVSyncMode(VSyncMode mode) {
        RENDER_BACKEND.setVSyncMode(mode);
    }

    public static void setVSyncPreference(VSyncMode mode) {
        RENDER_BACKEND.setVSyncPreference(mode);
    }

    public static boolean supportsVSyncMode(VSyncMode mode) {
        return RENDER_BACKEND.supportsVSyncMode(mode);
    }

    public static VSyncMode preferredTearFreeMode() {
        return RENDER_BACKEND.preferredTearFreeMode();
    }

    public static String getRenderBackendName() {
        return RENDER_BACKEND.getName();
    }

    public static VSyncMode getEffectiveVSyncMode() {
        return RENDER_BACKEND.getEffectiveVSyncMode();
    }

    public static void setPresentSuppressed(boolean suppressed) {
        RENDER_BACKEND.setPresentSuppressed(suppressed);
    }

    public static boolean hasSwapchainBackpressure() {
        return RENDER_BACKEND.hasSwapchainBackpressure();
    }

    public static void pumpDisplayMessages() {
        RENDER_BACKEND.pumpDisplayMessages();
    }

    public static boolean vendorIsAMD() {
        return VENDOR == AMD;
    }

    public static boolean vendorIsIntel() {
        return VENDOR == INTEL;
    }

    public static boolean vendorIsMesa() {
        return VENDOR == MESA;
    }

    public static boolean vendorIsNVIDIA() {
        return VENDOR == NVIDIA;
    }


    /**
     * Guards against FFP features not supported in core profile.
     * Default: throws UnsupportedOperationException.
     * With {@code -Dangelica.ffp.warnOnUnsupported=true}: warns once per function (diagnostic mode).
     * @return true (always - caller should skip the GL call)
     */
    static boolean guardUnsupportedFFP(String function, String explanation) {
        rejectExitCapture();
        if (!SystemProperties.FFP_WARN_ON_UNSUPPORTED) {
            throw new UnsupportedOperationException(function + ": " + explanation);
        }
        if (WARN_ONCE.add(function)) {
            LOGGER.warn("{}: {}", function, explanation, new Throwable("Stack trace"));
        }
        return true;
    }

    public static int getFragmentGeneration() {
        return ctx().fragmentGeneration;
    }

    public static void incrementFragmentGeneration() {
        ctx().fragmentGeneration++;
    }
}
