package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import com.gtnewhorizon.gtnhlib.bytebuf.Pointer;
import com.gtnewhorizon.gtnhlib.client.renderer.DirectTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.stacks.IStateStack;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement.Usage;
import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.glsm.DisplayListManager.RecordMode;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.backend.GLDebugMessageListener;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import com.gtnewhorizons.angelica.glsm.ffp.VAOManager;
import com.gtnewhorizons.angelica.glsm.hooks.DeferredAlphaHandler;
import com.gtnewhorizons.angelica.glsm.hooks.DeferredBlendHandler;
import com.gtnewhorizons.angelica.glsm.hooks.DeferredDepthColorHandler;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMConfig;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMInitConfig;
import com.gtnewhorizons.angelica.glsm.hooks.GlintColorHandler;
import com.gtnewhorizons.angelica.glsm.recording.CommandRecorder;
import com.gtnewhorizons.angelica.glsm.recording.CompiledDisplayList;
import com.gtnewhorizons.angelica.glsm.recording.ImmediateModeRecorder;
import com.gtnewhorizons.angelica.glsm.recording.commands.IndexedDrawCapture;
import com.gtnewhorizons.angelica.glsm.recording.commands.TexImage2DCmd;
import com.gtnewhorizons.angelica.glsm.recording.commands.TexSubImage2DCmd;
import com.gtnewhorizons.angelica.glsm.stacks.AlphaStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.BlendStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.BooleanStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.Color4Stack;
import com.gtnewhorizons.angelica.glsm.stacks.ColorMaskStack;
import com.gtnewhorizons.angelica.glsm.stacks.DepthStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.FogStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.IntegerStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.LightModelStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.LightStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.LineStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.MaterialStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.MatrixModeStack;
import com.gtnewhorizons.angelica.glsm.stacks.PointStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.PolygonStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.StencilStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.ViewPortStateStack;
import com.gtnewhorizons.angelica.glsm.states.ClipPlaneState;
import com.gtnewhorizons.angelica.glsm.states.Color4;
import com.gtnewhorizons.angelica.glsm.states.PixelUnpackState;
import com.gtnewhorizons.angelica.glsm.states.PolygonState;
import com.gtnewhorizons.angelica.glsm.states.ImageUnitBinding;
import com.gtnewhorizons.angelica.glsm.states.TextureBinding;
import com.gtnewhorizons.angelica.glsm.states.TextureUnitArray;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfo;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfoCache;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntStack;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.ARBShaderObjects;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.Drawable;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.opengl.KHRDebug;
import org.lwjgl.opengl.KHRDebugCallback;
import org.lwjgl.util.glu.GLU;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntSupplier;

import static com.gtnewhorizons.angelica.glsm.Vendor.AMD;
import static com.gtnewhorizons.angelica.glsm.Vendor.INTEL;
import static com.gtnewhorizons.angelica.glsm.Vendor.MESA;
import static com.gtnewhorizons.angelica.glsm.Vendor.NVIDIA;
import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;

/**
 * OpenGL State Manager - Provides cached state tracking and management for Backend Renderer Actions
 *   IMPORTANT NOTE: Do NOT call open gl directly, route everything through {@link BackendManager#RENDER_BACKEND}
 *
 * <p><b>IMPORTANT INITIALIZATION ORDER:</b></p>
 * <ul>
 *   <li>This class performs GL queries in static initializers (MAX_CLIP_PLANES, MAX_TEXTURE_UNITS, etc.)</li>
 *   <li>The class MUST NOT be loaded until after the GL context has been created and made current</li>
 *   <li>Violating this requirement will cause crashes with "No current context" or return invalid values</li>
 *   <li>Call {@link #initialize(GLSMInitConfig)} after GL context creation to initialize runtime state</li>
 * </ul>
 */
@SuppressWarnings("unused") // Used in ASM
public class GLStateManager {

    public static final Logger LOGGER = LogManager.getLogger("GLSM");

    private static final Set<String> WARN_ONCE = ConcurrentHashMap.newKeySet();

    public static void warnOnce(String key, String fmt, Object... args) {
        if (WARN_ONCE.add(key)) LOGGER.warn(fmt, args);
    }

    private static final CopyOnWriteArrayList<GLContextState> registeredContexts = new CopyOnWriteArrayList<>();
    private static volatile GLContextState primaryContext;
    private static final ThreadLocal<GLContextState> tlWorkerContext = new ThreadLocal<>();
    private static volatile boolean workerContextsActive;
    private static int workerContextCount;

    static GLContextState ctx() {
        if (!workerContextsActive) return primaryContext();
        final GLContextState st = tlWorkerContext.get();
        return st != null ? st : primaryContext();
    }

    private static GLContextState primaryContext() {
        GLContextState p = primaryContext;
        if (p != null) return p;
        synchronized (GLStateManager.class) {
            p = primaryContext;
            if (p == null) {
                p = new GLContextState();
                primaryContext = p;
                registeredContexts.add(p);
                p.init();
            }
        }
        return p;
    }

    static GLContextState enterWorkerContext() {
        final GLContextState st = new GLContextState();
        tlWorkerContext.set(st);
        synchronized (GLStateManager.class) {
            workerContextCount++;
            workerContextsActive = true;
        }
        registeredContexts.add(st);
        st.init();
        return st;
    }

    static void exitWorkerContext() {
        final GLContextState st = tlWorkerContext.get();
        if (st != null) {
            registeredContexts.remove(st);
            tlWorkerContext.remove();
            synchronized (GLStateManager.class) {
                if (--workerContextCount == 0) workerContextsActive = false;
            }
        }
    }

    // Tracy profiling counters
    public static long drawCalls;
    public static long texBindMisses;
    public static long programSwitches;
    public static int programGeneration;
    public static int drawFramebufferGeneration;

    // Thread Checking - must be early in static init order so isMainThread() works for state initialization
    @Getter private static final Thread MainThread = Thread.currentThread();

    public static boolean isMainThread() {
        return Thread.currentThread() == MainThread;
    }

    @Getter private static GLSMInitConfig initConfig;

    public static ContextCapabilities capabilities;

    // Software stack depths for FFP emulation
    public static final int MAX_ATTRIB_STACK_DEPTH = 16 + 2;
    public static final int MAX_MODELVIEW_STACK_DEPTH = 32 + 2;
    public static final int MAX_PROJECTION_STACK_DEPTH = 4;
    public static final int MAX_TEXTURE_STACK_DEPTH = 4;
    public static final int MAX_CLIP_PLANES = 8;
    public static final int MAX_TEXTURE_UNITS = RENDER_BACKEND.getInteger(GL20.GL_MAX_TEXTURE_IMAGE_UNITS);

    public static final GLFeatureSet HAS_MULTIPLE_SET = new GLFeatureSet();

    // Generation counters for FFP uniform dirty tracking. Bumped when the corresponding GLSM state changes.
    // Per-matrix-mode generation counters - avoids re-uploading all matrices when only one mode changed
    public static void setOverlayColor(float r, float g, float b, float a) {
        final GLContextState glCtx = ctx();
        if (r == glCtx.overlayR && g == glCtx.overlayG && b == glCtx.overlayB && a == glCtx.overlayA) return;
        glCtx.overlayR = r; glCtx.overlayG = g; glCtx.overlayB = b; glCtx.overlayA = a;
        glCtx.fragmentGeneration++;
    }

    public static void setShaderColor(float r, float g, float b, float a) {
        final GLContextState glCtx = ctx();
        if (r == glCtx.shaderColorR && g == glCtx.shaderColorG && b == glCtx.shaderColorB && a == glCtx.shaderColorA) return;
        glCtx.shaderColorR = r; glCtx.shaderColorG = g; glCtx.shaderColorB = b; glCtx.shaderColorA = a;
        if (GLSMHooks.SHADER_COLOR_CHANGE.hasListeners()) {
            GLSMHooks.shaderColorChangeEvent.red = r;
            GLSMHooks.shaderColorChangeEvent.green = g;
            GLSMHooks.shaderColorChangeEvent.blue = b;
            GLSMHooks.shaderColorChangeEvent.alpha = a;
            GLSMHooks.SHADER_COLOR_CHANGE.post(GLSMHooks.shaderColorChangeEvent);
        }
    }


    public static boolean consumeUnit23TexCoordSetDuringDraw() {
        final GLContextState glCtx = ctx();
        boolean v = glCtx.unit23TexCoordSetDuringDraw;
        glCtx.unit23TexCoordSetDuringDraw = false;
        return v;
    }

    public static void forceAttribDefaultsDirty() {
        final GLContextState glCtx = ctx();
        glCtx.dirtyNormalAttrib = true;
        glCtx.dirtyTexCoordAttrib = true;
    }

    // has* bits mark attribs the VBO already supplies; FFP supplies the rest via u_Current* uniforms.
    // Skip the backend vertexAttrib call in either case
    public static void flushDeferredVertexAttribs(boolean hasColor, boolean hasNormal, boolean hasTexCoord, boolean hasLightmap) {
        final GLContextState glCtx = ctx();
        final ShaderManager sm = ShaderManager.getInstance();
        final boolean ffpWillHandle = sm.isActive() || (sm.isEnabled() && getActiveProgram() == 0);
        if (!hasColor && glCtx.dirtyColorAttrib && !ffpWillHandle) {
            RENDER_BACKEND.vertexAttrib4f(Usage.COLOR.getAttributeLocation(), glCtx.color.getRed(), glCtx.color.getGreen(), glCtx.color.getBlue(), glCtx.color.getAlpha());
            glCtx.dirtyColorAttrib = false;
        }
        if (!hasNormal && glCtx.dirtyNormalAttrib && !ffpWillHandle) {
            final var n = ShaderManager.getCurrentNormal();
            RENDER_BACKEND.vertexAttrib3f(Usage.NORMAL.getAttributeLocation(), n.x, n.y, n.z);
            glCtx.dirtyNormalAttrib = false;
        }
        if (!hasTexCoord && glCtx.dirtyTexCoordAttrib && !ffpWillHandle) {
            final var tc = ShaderManager.getCurrentTexCoord();
            RENDER_BACKEND.vertexAttrib4f(Usage.PRIMARY_UV.getAttributeLocation(), tc.x, tc.y, tc.z, tc.w);
            glCtx.dirtyTexCoordAttrib = false;
        }
        if (!hasLightmap && glCtx.dirtyLightmapAttrib && !ffpWillHandle) {
            RENDER_BACKEND.vertexAttrib4f(Usage.SECONDARY_UV.getAttributeLocation(), GLSMConfig.lastBrightnessX, GLSMConfig.lastBrightnessY, 0.0f, 1.0f);
            glCtx.dirtyLightmapAttrib = false;
        }
    }


    public static int getMaxBoundTextureUnit() {
        return ctx().maxBoundTextureUnit;
    }

    public static void trackMaxBoundTextureUnit(int unit) {
        final GLContextState glCtx = ctx();
        if (unit > glCtx.maxBoundTextureUnit) glCtx.maxBoundTextureUnit = unit;
    }
    // Lock guard for texture bind callback (prevents recursion from RenderSystem.bindTextureToUnit)
    private static boolean lockBindCallback;

    // Deferred texture deletion: names kept valid until glGenTextures recycles them.
    // Emulates Mesa/compat-profile behavior where delete-then-bind doesn't error.
    private static final IntOpenHashSet deferredDeleteTextures = new IntOpenHashSet();

    @Getter private static Vendor VENDOR;

    @Getter private static boolean windows;

    // This setting varies depending on driver, so it gets queried at runtime
    public static int DEFAULT_DRAW_BUFFER = GL11.GL_BACK;

    private static Thread CurrentThread = MainThread;
    @Setter @Getter private static boolean runningSplash = true;

    private static volatile boolean splashComplete = false;
    @Getter @Setter private static Thread drawableGLHolder = MainThread;
    // Reference to DrawableGL (main display context) - works with both FML and BLS splash
    @Setter private static Drawable drawableGL = null;

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
        return Thread.currentThread() == drawableGLHolder;
    }

    /**
     * Check if splash screen is complete. After splash, there's only one GL context and no locking is needed.
     */
    public static boolean isSplashComplete() {
        return splashComplete;
    }

    /**
     * Get the active texture unit for server-side state operations. If caching is enabled, returns cached value. If caching is disabled (SharedDrawable),
     * queries actual GL state.
     */
    public static int getActiveTextureUnitForServerState() {
        if (isCachingEnabled()) {
            return getActiveTextureUnit();
        }
        // Query actual GL state for SharedDrawable context
        return RENDER_BACKEND.getInteger(GL13.GL_ACTIVE_TEXTURE) - GL13.GL_TEXTURE0;
    }

    /**
     * Get the texture bound to the current texture unit for server-side state operations.
     * If caching is enabled, returns cached value.
     * If caching is disabled (SharedDrawable), queries actual GL state.
     */
    public static int getBoundTextureForServerState() {
        if (isCachingEnabled()) {
            return getBoundTexture();
        }
        // Query actual GL state for SharedDrawable context
        return RENDER_BACKEND.getInteger(GL11.GL_TEXTURE_BINDING_2D);
    }

    private static TextureInfo getBoundTextureInfo() {
        final GLContextState glCtx = ctx();
        if (isCachingEnabled()) {
            return glCtx.textures.getTextureUnitBindings(glCtx.activeTextureUnit.getValue()).getOrResolveInfo();
        }
        return TextureInfoCache.INSTANCE.getInfo(getBoundTextureForServerState());
    }

    /**
     * Get the texture bound to a specific texture unit for server-side state operations.
     * If caching is enabled, returns cached value.
     * If caching is disabled (SharedDrawable), returns -1 to force operations to proceed
     */
    public static int getBoundTextureForServerState(int unit) {
        if (isCachingEnabled()) {
            return getBoundTexture(unit);
        }
        return -1;
    }

    private static final String TEXTURE = "texture";
    private static final String TEXTURE_RENAMED = "gtexture";

    /** Register a state stack as modified at the current depth (called from beforeModify). */
    public static void registerModifiedState(IStateStack<?> stack) {
        final GLContextState c = ctx();
        if (c.attribDepth > 0) {
            c.modifiedAtDepth[c.attribDepth - 1].add(stack);
        }
    }



    // Additional enable bit states tracked by GL_ENABLE_BIT


    // Line width range queried at init from GL_ALIASED_LINE_WIDTH_RANGE
    static float lineWidthMin = 1.0f;
    static float lineWidthMax = 1.0f;
    /** True when the driver cannot render wide lines natively (Mesa forward-compat). GS emulation handles widths > 1.0. */
    public static boolean wideLineEmulationEnabled = false;
    public static boolean supportsGeometryShaders() { return RENDER_BACKEND.supportsGeometryShaders(); }
    public static boolean framebufferCompletenessIsMeaningful() { return RENDER_BACKEND.framebufferCompletenessIsMeaningful(); }
    /**
     * Set per draw call: true when the current draw is a line primitive that needs GS expansion. Read by
     * {@link com.gtnewhorizons.angelica.glsm.ffp.VertexKey}.
     */
    public static boolean wideLineEmulationActive = false;
    public static boolean lineStippleActive = false;

    public static boolean instancedFfpDrawActive = false;





    private static final MethodHandle MAT4_STACK_CURR_DEPTH;

    static {
        try {
            final Field curr = Matrix4fStack.class.getDeclaredField("curr");
            curr.setAccessible(true);
            MAT4_STACK_CURR_DEPTH = MethodHandles.lookup().unreflectGetter(curr);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static int getBoundEBO() {
        return VAOManager.boundEBO;
    }

    @Getter private static int defaultVAO; // Non-zero on core profile

    public static void reset() {
        final GLContextState glCtx = ctx();
        runningSplash = true;
        while (!glCtx.attribs.isEmpty()) {
            glCtx.attribs.popInt();
        }

        final List<IStateStack<?>> stacks = Feature.maskToFeatures(GL11.GL_ALL_ATTRIB_BITS);
        final int size = stacks.size();

        for (int i = 0; i < size; i++) {
            final IStateStack<?> stack = stacks.get(i);

            while (!stack.isEmpty()) {
                stack.pop();
            }
        }

        glCtx.modelViewMatrix.clear();
        glCtx.projectionMatrix.clear();
    }

    /**
     * Check if a display list exists (has been compiled and stored). Delegates to DisplayListManager.
     *
     * @param list The display list ID to check
     * @return true if the display list exists, false otherwise
     */
    public static boolean displayListExists(int list) {
        return DisplayListManager.displayListExists(list);
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
        preInit(config.getDisplayWidth(), config.getDisplayHeight());
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
            ctx().viewportState.setViewPort(0, 0, displayWidth, displayHeight);
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

        DEFAULT_DRAW_BUFFER = RENDER_BACKEND.getInteger(GL11.GL_DRAW_BUFFER);

        // The initial mask value should be defined as all 1's. However, some drivers have it set to 0's.
        // To ensure consistency & correctness across all drivers, we're setting them to 0xFF.
        RENDER_BACKEND.stencilFunc(glCtx.stencilState.getFuncFront(), glCtx.stencilState.getRefFront(), 0xFF);
        RENDER_BACKEND.stencilMask(0xFF);

        // Compute stencil bit mask - driver clamps stencil masks to buffer depth
        // GL_STENCIL_BITS was removed in core profile; query via default FBO attachment
        final int stencilBits = RENDER_BACKEND.getFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, GL11.GL_STENCIL, GL30.GL_FRAMEBUFFER_ATTACHMENT_STENCIL_SIZE);
        glCtx.stencilBitMask = stencilBits >= 32 ? 0xFFFFFFFF : (1 << stencilBits) - 1;
        if (glCtx.stencilBitMask == 0) {
            LOGGER.warn("Default framebuffer reports 0 stencil bits on backend {}; all stencil masks clamp to 0", RENDER_BACKEND.getClass().getSimpleName());
        }

        // Initialize stencil masks from computed bit mask
        glCtx.stencilState.setValueMaskFront(glCtx.stencilBitMask);
        glCtx.stencilState.setValueMaskBack(glCtx.stencilBitMask);
        glCtx.stencilState.setWriteMaskFront(glCtx.stencilBitMask);
        glCtx.stencilState.setWriteMaskBack(glCtx.stencilBitMask);

        final FloatBuffer lwRange = BufferUtils.createFloatBuffer(16);
        RENDER_BACKEND.getFloat(GL12.GL_ALIASED_LINE_WIDTH_RANGE, lwRange);
        lineWidthMin = lwRange.get(0);
        lineWidthMax = lwRange.get(1);
        final float queriedMax = lineWidthMax;
        // Probe whether the driver actually accepts the queried max (Mesa forward-compat rejects > 1.0)
        if (lineWidthMax > 1.0f) {
            while (RENDER_BACKEND.getError() != GL11.GL_NO_ERROR) {}
            RENDER_BACKEND.lineWidth(lineWidthMax);
            if (RENDER_BACKEND.getError() != GL11.GL_NO_ERROR) {
                // Binary search for the actual accepted max between 1.0 and queriedMax
                float lo = 1.0f, hi = queriedMax;
                while (hi - lo > 0.01f) {
                    final float mid = (lo + hi) * 0.5f;
                    while (RENDER_BACKEND.getError() != GL11.GL_NO_ERROR) {}
                    RENDER_BACKEND.lineWidth(mid);
                    if (RENDER_BACKEND.getError() == GL11.GL_NO_ERROR) {
                        lo = mid;
                    } else {
                        hi = mid;
                    }
                }
                lineWidthMax = lo;
            }
            RENDER_BACKEND.lineWidth(1.0f);
            while (RENDER_BACKEND.getError() != GL11.GL_NO_ERROR) {}
        }
        wideLineEmulationEnabled = lineWidthMax <= 1.0f;
        if (wideLineEmulationEnabled) {
            LOGGER.info("GL line width: aliased range [{}, {}], probe at {} rejected, native [{}, {}], GS emulation active for [{}, {}]", lineWidthMin, queriedMax, queriedMax, lineWidthMin, lineWidthMax, lineWidthMax, queriedMax);
        } else {
            LOGGER.info("GL line width: aliased range [{}, {}], probe at {} accepted, native [{}, {}]", lineWidthMin, queriedMax, queriedMax, lineWidthMin, lineWidthMax);
        }

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

        defaultVAO = RENDER_BACKEND.genVertexArrays();
        RENDER_BACKEND.bindVertexArray(defaultVAO);
        glCtx.boundVAO = defaultVAO;
        VAOManager.init(defaultVAO);
        if (defaultVAO != 0) {
            RENDER_BACKEND.provokingVertex(GL32.GL_LAST_VERTEX_CONVENTION);
        }
        if (initCallback != null) {
            initCallback.run();
        }

        // Drain any pending GL errors from initialization. In core profile, some legacy queries may generate GL_INVALID_ENUM. The splash thread inherits the
        // DrawableGL context and its error state, so stale errors here would cause SplashProgress.checkGLError() to fail.
        int err;
        while ((err = RENDER_BACKEND.getError()) != 0) {
            LOGGER.debug("Drained GL error 0x{} during init", Integer.toHexString(err));
        }
    }

    // LWJGL Overrides
    public static void glEnable(int cap) {
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordEnable(cap);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        // Handle clip planes dynamically (supports up to MAX_CLIP_PLANES)
        if (cap >= GL11.GL_CLIP_PLANE0 && cap < GL11.GL_CLIP_PLANE0 + MAX_CLIP_PLANES) {
            glCtx.clipPlaneStates[cap - GL11.GL_CLIP_PLANE0].enable();
            glCtx.clipPlaneGeneration++;
            return;
        }

        switch (cap) {
            case GL11.GL_ALPHA_TEST -> enableAlphaTest();
            case GL11.GL_AUTO_NORMAL -> glCtx.autoNormalState.enable();
            case GL11.GL_BLEND -> enableBlend();
            case GL11.GL_COLOR_MATERIAL -> enableColorMaterial();
            case GL11.GL_COLOR_LOGIC_OP -> glCtx.colorLogicOpState.enable();
            case GL11.GL_CULL_FACE -> enableCull();
            case GL11.GL_DEPTH_TEST -> enableDepthTest();
            case GL11.GL_DITHER -> glCtx.ditherState.enable();
            case GL11.GL_FOG -> enableFog();
            case GL11.GL_INDEX_LOGIC_OP -> glCtx.indexLogicOpState.enable();
            case GL11.GL_LIGHTING -> enableLighting();
            case GL11.GL_LIGHT0 -> enableLight(0);
            case GL11.GL_LIGHT1 -> enableLight(1);
            case GL11.GL_LIGHT2 -> enableLight(2);
            case GL11.GL_LIGHT3 -> enableLight(3);
            case GL11.GL_LIGHT4 -> enableLight(4);
            case GL11.GL_LIGHT5 -> enableLight(5);
            case GL11.GL_LIGHT6 -> enableLight(6);
            case GL11.GL_LIGHT7 -> enableLight(7);
            case GL11.GL_LINE_SMOOTH -> glCtx.lineSmoothState.enable();
            case GL11.GL_LINE_STIPPLE -> glCtx.lineStippleState.enable();
            case GL11.GL_MAP1_COLOR_4 -> glCtx.map1Color4State.enable();
            case GL11.GL_MAP1_INDEX -> glCtx.map1IndexState.enable();
            case GL11.GL_MAP1_NORMAL -> glCtx.map1NormalState.enable();
            case GL11.GL_MAP1_TEXTURE_COORD_1 -> glCtx.map1TextureCoord1State.enable();
            case GL11.GL_MAP1_TEXTURE_COORD_2 -> glCtx.map1TextureCoord2State.enable();
            case GL11.GL_MAP1_TEXTURE_COORD_3 -> glCtx.map1TextureCoord3State.enable();
            case GL11.GL_MAP1_TEXTURE_COORD_4 -> glCtx.map1TextureCoord4State.enable();
            case GL11.GL_MAP1_VERTEX_3 -> glCtx.map1Vertex3State.enable();
            case GL11.GL_MAP1_VERTEX_4 -> glCtx.map1Vertex4State.enable();
            case GL11.GL_MAP2_COLOR_4 -> glCtx.map2Color4State.enable();
            case GL11.GL_MAP2_INDEX -> glCtx.map2IndexState.enable();
            case GL11.GL_MAP2_NORMAL -> glCtx.map2NormalState.enable();
            case GL11.GL_MAP2_TEXTURE_COORD_1 -> glCtx.map2TextureCoord1State.enable();
            case GL11.GL_MAP2_TEXTURE_COORD_2 -> glCtx.map2TextureCoord2State.enable();
            case GL11.GL_MAP2_TEXTURE_COORD_3 -> glCtx.map2TextureCoord3State.enable();
            case GL11.GL_MAP2_TEXTURE_COORD_4 -> glCtx.map2TextureCoord4State.enable();
            case GL11.GL_MAP2_VERTEX_3 -> glCtx.map2Vertex3State.enable();
            case GL11.GL_MAP2_VERTEX_4 -> glCtx.map2Vertex4State.enable();
            case GL13.GL_MULTISAMPLE -> glCtx.multisampleState.enable();
            case GL11.GL_NORMALIZE -> glCtx.normalizeState.enable();
            case GL11.GL_POINT_SMOOTH -> glCtx.pointSmoothState.enable();
            case GL11.GL_POLYGON_OFFSET_POINT -> glCtx.polygonOffsetPointState.enable();
            case GL11.GL_POLYGON_OFFSET_LINE -> glCtx.polygonOffsetLineState.enable();
            case GL11.GL_POLYGON_OFFSET_FILL -> glCtx.polygonOffsetFillState.enable();
            case GL11.GL_POLYGON_SMOOTH -> glCtx.polygonSmoothState.enable();
            case GL11.GL_POLYGON_STIPPLE -> glCtx.polygonStippleState.enable();
            case GL12.GL_RESCALE_NORMAL -> enableRescaleNormal();
            case GL13.GL_SAMPLE_ALPHA_TO_COVERAGE -> glCtx.sampleAlphaToCoverageState.enable();
            case GL13.GL_SAMPLE_ALPHA_TO_ONE -> glCtx.sampleAlphaToOneState.enable();
            case GL13.GL_SAMPLE_COVERAGE -> glCtx.sampleCoverageState.enable();
            case GL11.GL_SCISSOR_TEST -> enableScissorTest();
            case GL11.GL_STENCIL_TEST -> glCtx.stencilTest.enable();
            case GL11.GL_TEXTURE_1D -> glCtx.textures.getTexture1DStates(glCtx.activeTextureUnit.getValue()).enable();
            case GL11.GL_TEXTURE_2D -> enableTexture();
            case GL12.GL_TEXTURE_3D -> glCtx.textures.getTexture3DStates(glCtx.activeTextureUnit.getValue()).enable();
            case GL11.GL_TEXTURE_GEN_S -> glCtx.textures.getTexGenSStates(glCtx.activeTextureUnit.getValue()).enable();
            case GL11.GL_TEXTURE_GEN_T -> glCtx.textures.getTexGenTStates(glCtx.activeTextureUnit.getValue()).enable();
            case GL11.GL_TEXTURE_GEN_R -> glCtx.textures.getTexGenRStates(glCtx.activeTextureUnit.getValue()).enable();
            case GL11.GL_TEXTURE_GEN_Q -> glCtx.textures.getTexGenQStates(glCtx.activeTextureUnit.getValue()).enable();
            default -> RENDER_BACKEND.enable(cap);
        }
    }

    public static void glDisable(int cap) {
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDisable(cap);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        // Handle clip planes dynamically (supports up to MAX_CLIP_PLANES)
        if (cap >= GL11.GL_CLIP_PLANE0 && cap < GL11.GL_CLIP_PLANE0 + MAX_CLIP_PLANES) {
            glCtx.clipPlaneStates[cap - GL11.GL_CLIP_PLANE0].disable();
            glCtx.clipPlaneGeneration++;
            return;
        }

        switch (cap) {
            case GL11.GL_ALPHA_TEST -> disableAlphaTest();
            case GL11.GL_AUTO_NORMAL -> glCtx.autoNormalState.disable();
            case GL11.GL_BLEND -> disableBlend();
            case GL11.GL_COLOR_MATERIAL -> disableColorMaterial();
            case GL11.GL_COLOR_LOGIC_OP -> glCtx.colorLogicOpState.disable();
            case GL11.GL_CULL_FACE -> disableCull();
            case GL11.GL_DEPTH_TEST -> disableDepthTest();
            case GL11.GL_DITHER -> glCtx.ditherState.disable();
            case GL11.GL_FOG -> disableFog();
            case GL11.GL_INDEX_LOGIC_OP -> glCtx.indexLogicOpState.disable();
            case GL11.GL_LIGHTING -> disableLighting();
            case GL11.GL_LIGHT0 -> disableLight(0);
            case GL11.GL_LIGHT1 -> disableLight(1);
            case GL11.GL_LIGHT2 -> disableLight(2);
            case GL11.GL_LIGHT3 -> disableLight(3);
            case GL11.GL_LIGHT4 -> disableLight(4);
            case GL11.GL_LIGHT5 -> disableLight(5);
            case GL11.GL_LIGHT6 -> disableLight(6);
            case GL11.GL_LIGHT7 -> disableLight(7);
            case GL11.GL_LINE_SMOOTH -> glCtx.lineSmoothState.disable();
            case GL11.GL_LINE_STIPPLE -> glCtx.lineStippleState.disable();
            case GL11.GL_MAP1_COLOR_4 -> glCtx.map1Color4State.disable();
            case GL11.GL_MAP1_INDEX -> glCtx.map1IndexState.disable();
            case GL11.GL_MAP1_NORMAL -> glCtx.map1NormalState.disable();
            case GL11.GL_MAP1_TEXTURE_COORD_1 -> glCtx.map1TextureCoord1State.disable();
            case GL11.GL_MAP1_TEXTURE_COORD_2 -> glCtx.map1TextureCoord2State.disable();
            case GL11.GL_MAP1_TEXTURE_COORD_3 -> glCtx.map1TextureCoord3State.disable();
            case GL11.GL_MAP1_TEXTURE_COORD_4 -> glCtx.map1TextureCoord4State.disable();
            case GL11.GL_MAP1_VERTEX_3 -> glCtx.map1Vertex3State.disable();
            case GL11.GL_MAP1_VERTEX_4 -> glCtx.map1Vertex4State.disable();
            case GL11.GL_MAP2_COLOR_4 -> glCtx.map2Color4State.disable();
            case GL11.GL_MAP2_INDEX -> glCtx.map2IndexState.disable();
            case GL11.GL_MAP2_NORMAL -> glCtx.map2NormalState.disable();
            case GL11.GL_MAP2_TEXTURE_COORD_1 -> glCtx.map2TextureCoord1State.disable();
            case GL11.GL_MAP2_TEXTURE_COORD_2 -> glCtx.map2TextureCoord2State.disable();
            case GL11.GL_MAP2_TEXTURE_COORD_3 -> glCtx.map2TextureCoord3State.disable();
            case GL11.GL_MAP2_TEXTURE_COORD_4 -> glCtx.map2TextureCoord4State.disable();
            case GL11.GL_MAP2_VERTEX_3 -> glCtx.map2Vertex3State.disable();
            case GL11.GL_MAP2_VERTEX_4 -> glCtx.map2Vertex4State.disable();
            case GL13.GL_MULTISAMPLE -> glCtx.multisampleState.disable();
            case GL11.GL_NORMALIZE -> glCtx.normalizeState.disable();
            case GL11.GL_POINT_SMOOTH -> glCtx.pointSmoothState.disable();
            case GL11.GL_POLYGON_OFFSET_POINT -> glCtx.polygonOffsetPointState.disable();
            case GL11.GL_POLYGON_OFFSET_LINE -> glCtx.polygonOffsetLineState.disable();
            case GL11.GL_POLYGON_OFFSET_FILL -> glCtx.polygonOffsetFillState.disable();
            case GL11.GL_POLYGON_SMOOTH -> glCtx.polygonSmoothState.disable();
            case GL11.GL_POLYGON_STIPPLE -> glCtx.polygonStippleState.disable();
            case GL12.GL_RESCALE_NORMAL -> disableRescaleNormal();
            case GL13.GL_SAMPLE_ALPHA_TO_COVERAGE -> glCtx.sampleAlphaToCoverageState.disable();
            case GL13.GL_SAMPLE_ALPHA_TO_ONE -> glCtx.sampleAlphaToOneState.disable();
            case GL13.GL_SAMPLE_COVERAGE -> glCtx.sampleCoverageState.disable();
            case GL11.GL_SCISSOR_TEST -> disableScissorTest();
            case GL11.GL_STENCIL_TEST -> glCtx.stencilTest.disable();
            case GL11.GL_TEXTURE_1D -> glCtx.textures.getTexture1DStates(glCtx.activeTextureUnit.getValue()).disable();
            case GL11.GL_TEXTURE_2D -> disableTexture();
            case GL12.GL_TEXTURE_3D -> glCtx.textures.getTexture3DStates(glCtx.activeTextureUnit.getValue()).disable();
            case GL11.GL_TEXTURE_GEN_S -> glCtx.textures.getTexGenSStates(glCtx.activeTextureUnit.getValue()).disable();
            case GL11.GL_TEXTURE_GEN_T -> glCtx.textures.getTexGenTStates(glCtx.activeTextureUnit.getValue()).disable();
            case GL11.GL_TEXTURE_GEN_R -> glCtx.textures.getTexGenRStates(glCtx.activeTextureUnit.getValue()).disable();
            case GL11.GL_TEXTURE_GEN_Q -> glCtx.textures.getTexGenQStates(glCtx.activeTextureUnit.getValue()).disable();
            default -> RENDER_BACKEND.disable(cap);
        }
    }

    public static boolean glIsEnabled(int cap) {
        final GLContextState glCtx = ctx();
        if (!isCachingEnabled()) {
            return RENDER_BACKEND.getBoolean(cap);
        }
        // Handle clip planes dynamically (supports up to MAX_CLIP_PLANES)
        if (cap >= GL11.GL_CLIP_PLANE0 && cap < GL11.GL_CLIP_PLANE0 + MAX_CLIP_PLANES) {
            return glCtx.clipPlaneStates[cap - GL11.GL_CLIP_PLANE0].isEnabled();
        }

        return switch (cap) {
            case GL11.GL_ALPHA_TEST -> glCtx.alphaTest.isEnabled();
            case GL11.GL_AUTO_NORMAL -> glCtx.autoNormalState.isEnabled();
            case GL11.GL_BLEND -> glCtx.blendMode.isEnabled();
            case GL11.GL_COLOR_MATERIAL -> glCtx.colorMaterial.isEnabled();
            case GL11.GL_COLOR_LOGIC_OP -> glCtx.colorLogicOpState.isEnabled();
            case GL11.GL_CULL_FACE -> glCtx.cullState.isEnabled();
            case GL11.GL_DEPTH_TEST -> glCtx.depthTest.isEnabled();
            case GL11.GL_DITHER -> glCtx.ditherState.isEnabled();
            case GL11.GL_FOG -> glCtx.fogMode.isEnabled();
            case GL11.GL_INDEX_LOGIC_OP -> glCtx.indexLogicOpState.isEnabled();
            case GL11.GL_LIGHTING -> glCtx.lightingState.isEnabled();
            case GL11.GL_LIGHT0 -> glCtx.lightStates[0].isEnabled();
            case GL11.GL_LIGHT1 -> glCtx.lightStates[1].isEnabled();
            case GL11.GL_LIGHT2 -> glCtx.lightStates[2].isEnabled();
            case GL11.GL_LIGHT3 -> glCtx.lightStates[3].isEnabled();
            case GL11.GL_LIGHT4 -> glCtx.lightStates[4].isEnabled();
            case GL11.GL_LIGHT5 -> glCtx.lightStates[5].isEnabled();
            case GL11.GL_LIGHT6 -> glCtx.lightStates[6].isEnabled();
            case GL11.GL_LIGHT7 -> glCtx.lightStates[7].isEnabled();
            case GL11.GL_LINE_SMOOTH -> glCtx.lineSmoothState.isEnabled();
            case GL11.GL_LINE_STIPPLE -> glCtx.lineStippleState.isEnabled();
            case GL11.GL_MAP1_COLOR_4 -> glCtx.map1Color4State.isEnabled();
            case GL11.GL_MAP1_INDEX -> glCtx.map1IndexState.isEnabled();
            case GL11.GL_MAP1_NORMAL -> glCtx.map1NormalState.isEnabled();
            case GL11.GL_MAP1_TEXTURE_COORD_1 -> glCtx.map1TextureCoord1State.isEnabled();
            case GL11.GL_MAP1_TEXTURE_COORD_2 -> glCtx.map1TextureCoord2State.isEnabled();
            case GL11.GL_MAP1_TEXTURE_COORD_3 -> glCtx.map1TextureCoord3State.isEnabled();
            case GL11.GL_MAP1_TEXTURE_COORD_4 -> glCtx.map1TextureCoord4State.isEnabled();
            case GL11.GL_MAP1_VERTEX_3 -> glCtx.map1Vertex3State.isEnabled();
            case GL11.GL_MAP1_VERTEX_4 -> glCtx.map1Vertex4State.isEnabled();
            case GL11.GL_MAP2_COLOR_4 -> glCtx.map2Color4State.isEnabled();
            case GL11.GL_MAP2_INDEX -> glCtx.map2IndexState.isEnabled();
            case GL11.GL_MAP2_NORMAL -> glCtx.map2NormalState.isEnabled();
            case GL11.GL_MAP2_TEXTURE_COORD_1 -> glCtx.map2TextureCoord1State.isEnabled();
            case GL11.GL_MAP2_TEXTURE_COORD_2 -> glCtx.map2TextureCoord2State.isEnabled();
            case GL11.GL_MAP2_TEXTURE_COORD_3 -> glCtx.map2TextureCoord3State.isEnabled();
            case GL11.GL_MAP2_TEXTURE_COORD_4 -> glCtx.map2TextureCoord4State.isEnabled();
            case GL11.GL_MAP2_VERTEX_3 -> glCtx.map2Vertex3State.isEnabled();
            case GL11.GL_MAP2_VERTEX_4 -> glCtx.map2Vertex4State.isEnabled();
            case GL13.GL_MULTISAMPLE -> glCtx.multisampleState.isEnabled();
            case GL11.GL_NORMALIZE -> glCtx.normalizeState.isEnabled();
            case GL11.GL_POINT_SMOOTH -> glCtx.pointSmoothState.isEnabled();
            case GL11.GL_POLYGON_OFFSET_POINT -> glCtx.polygonOffsetPointState.isEnabled();
            case GL11.GL_POLYGON_OFFSET_LINE -> glCtx.polygonOffsetLineState.isEnabled();
            case GL11.GL_POLYGON_OFFSET_FILL -> glCtx.polygonOffsetFillState.isEnabled();
            case GL11.GL_POLYGON_SMOOTH -> glCtx.polygonSmoothState.isEnabled();
            case GL11.GL_POLYGON_STIPPLE -> glCtx.polygonStippleState.isEnabled();
            case GL12.GL_RESCALE_NORMAL -> glCtx.rescaleNormalState.isEnabled();
            case GL13.GL_SAMPLE_ALPHA_TO_COVERAGE -> glCtx.sampleAlphaToCoverageState.isEnabled();
            case GL13.GL_SAMPLE_ALPHA_TO_ONE -> glCtx.sampleAlphaToOneState.isEnabled();
            case GL13.GL_SAMPLE_COVERAGE -> glCtx.sampleCoverageState.isEnabled();
            case GL11.GL_SCISSOR_TEST -> glCtx.scissorTest.isEnabled();
            case GL11.GL_STENCIL_TEST -> glCtx.stencilTest.isEnabled();
            case GL11.GL_TEXTURE_1D -> glCtx.textures.getTexture1DStates(glCtx.activeTextureUnit.getValue()).isEnabled();
            case GL11.GL_TEXTURE_2D -> glCtx.textures.getTextureUnitStates(glCtx.activeTextureUnit.getValue()).isEnabled();
            case GL12.GL_TEXTURE_3D -> glCtx.textures.getTexture3DStates(glCtx.activeTextureUnit.getValue()).isEnabled();
            case GL11.GL_TEXTURE_GEN_S -> glCtx.textures.getTexGenSStates(glCtx.activeTextureUnit.getValue()).isEnabled();
            case GL11.GL_TEXTURE_GEN_T -> glCtx.textures.getTexGenTStates(glCtx.activeTextureUnit.getValue()).isEnabled();
            case GL11.GL_TEXTURE_GEN_R -> glCtx.textures.getTexGenRStates(glCtx.activeTextureUnit.getValue()).isEnabled();
            case GL11.GL_TEXTURE_GEN_Q -> glCtx.textures.getTexGenQStates(glCtx.activeTextureUnit.getValue()).isEnabled();
            default -> RENDER_BACKEND.getBoolean(cap);
        };
    }

    public static boolean glGetBoolean(int pname) {
        final GLContextState glCtx = ctx();
        if (!isCachingEnabled()) {
            return RENDER_BACKEND.getBoolean(pname);
        }
        // Handle clip planes dynamically (supports up to MAX_CLIP_PLANES)
        if (pname >= GL11.GL_CLIP_PLANE0 && pname < GL11.GL_CLIP_PLANE0 + MAX_CLIP_PLANES) {
            return glCtx.clipPlaneStates[pname - GL11.GL_CLIP_PLANE0].isEnabled();
        }

        return switch (pname) {
            case GL11.GL_ALPHA_TEST -> glCtx.alphaTest.isEnabled();
            case GL11.GL_AUTO_NORMAL -> glCtx.autoNormalState.isEnabled();
            case GL11.GL_BLEND -> glCtx.blendMode.isEnabled();
            case GL11.GL_COLOR_MATERIAL -> glCtx.colorMaterial.isEnabled();
            case GL11.GL_COLOR_LOGIC_OP -> glCtx.colorLogicOpState.isEnabled();
            case GL11.GL_CULL_FACE -> glCtx.cullState.isEnabled();
            case GL11.GL_DEPTH_TEST -> glCtx.depthTest.isEnabled();
            case GL11.GL_DEPTH_WRITEMASK -> glCtx.depthState.isEnabled();
            case GL11.GL_DITHER -> glCtx.ditherState.isEnabled();
            case GL11.GL_FOG -> glCtx.fogMode.isEnabled();
            case GL11.GL_INDEX_LOGIC_OP -> glCtx.indexLogicOpState.isEnabled();
            case GL11.GL_LIGHTING -> glCtx.lightingState.isEnabled();
            case GL11.GL_LIGHT0 -> glCtx.lightStates[0].isEnabled();
            case GL11.GL_LIGHT1 -> glCtx.lightStates[1].isEnabled();
            case GL11.GL_LIGHT2 -> glCtx.lightStates[2].isEnabled();
            case GL11.GL_LIGHT3 -> glCtx.lightStates[3].isEnabled();
            case GL11.GL_LIGHT4 -> glCtx.lightStates[4].isEnabled();
            case GL11.GL_LIGHT5 -> glCtx.lightStates[5].isEnabled();
            case GL11.GL_LIGHT6 -> glCtx.lightStates[6].isEnabled();
            case GL11.GL_LIGHT7 -> glCtx.lightStates[7].isEnabled();
            case GL11.GL_LINE_SMOOTH -> glCtx.lineSmoothState.isEnabled();
            case GL11.GL_LINE_STIPPLE -> glCtx.lineStippleState.isEnabled();
            case GL11.GL_MAP1_COLOR_4 -> glCtx.map1Color4State.isEnabled();
            case GL11.GL_MAP1_INDEX -> glCtx.map1IndexState.isEnabled();
            case GL11.GL_MAP1_NORMAL -> glCtx.map1NormalState.isEnabled();
            case GL11.GL_MAP1_TEXTURE_COORD_1 -> glCtx.map1TextureCoord1State.isEnabled();
            case GL11.GL_MAP1_TEXTURE_COORD_2 -> glCtx.map1TextureCoord2State.isEnabled();
            case GL11.GL_MAP1_TEXTURE_COORD_3 -> glCtx.map1TextureCoord3State.isEnabled();
            case GL11.GL_MAP1_TEXTURE_COORD_4 -> glCtx.map1TextureCoord4State.isEnabled();
            case GL11.GL_MAP1_VERTEX_3 -> glCtx.map1Vertex3State.isEnabled();
            case GL11.GL_MAP1_VERTEX_4 -> glCtx.map1Vertex4State.isEnabled();
            case GL11.GL_MAP2_COLOR_4 -> glCtx.map2Color4State.isEnabled();
            case GL11.GL_MAP2_INDEX -> glCtx.map2IndexState.isEnabled();
            case GL11.GL_MAP2_NORMAL -> glCtx.map2NormalState.isEnabled();
            case GL11.GL_MAP2_TEXTURE_COORD_1 -> glCtx.map2TextureCoord1State.isEnabled();
            case GL11.GL_MAP2_TEXTURE_COORD_2 -> glCtx.map2TextureCoord2State.isEnabled();
            case GL11.GL_MAP2_TEXTURE_COORD_3 -> glCtx.map2TextureCoord3State.isEnabled();
            case GL11.GL_MAP2_TEXTURE_COORD_4 -> glCtx.map2TextureCoord4State.isEnabled();
            case GL11.GL_MAP2_VERTEX_3 -> glCtx.map2Vertex3State.isEnabled();
            case GL11.GL_MAP2_VERTEX_4 -> glCtx.map2Vertex4State.isEnabled();
            case GL13.GL_MULTISAMPLE -> glCtx.multisampleState.isEnabled();
            case GL11.GL_NORMALIZE -> glCtx.normalizeState.isEnabled();
            case GL11.GL_POINT_SMOOTH -> glCtx.pointSmoothState.isEnabled();
            case GL11.GL_POLYGON_OFFSET_POINT -> glCtx.polygonOffsetPointState.isEnabled();
            case GL11.GL_POLYGON_OFFSET_LINE -> glCtx.polygonOffsetLineState.isEnabled();
            case GL11.GL_POLYGON_OFFSET_FILL -> glCtx.polygonOffsetFillState.isEnabled();
            case GL11.GL_POLYGON_SMOOTH -> glCtx.polygonSmoothState.isEnabled();
            case GL11.GL_POLYGON_STIPPLE -> glCtx.polygonStippleState.isEnabled();
            case GL12.GL_RESCALE_NORMAL -> glCtx.rescaleNormalState.isEnabled();
            case GL13.GL_SAMPLE_ALPHA_TO_COVERAGE -> glCtx.sampleAlphaToCoverageState.isEnabled();
            case GL13.GL_SAMPLE_ALPHA_TO_ONE -> glCtx.sampleAlphaToOneState.isEnabled();
            case GL13.GL_SAMPLE_COVERAGE -> glCtx.sampleCoverageState.isEnabled();
            case GL11.GL_SCISSOR_TEST -> glCtx.scissorTest.isEnabled();
            case GL11.GL_STENCIL_TEST -> glCtx.stencilTest.isEnabled();
            case GL11.GL_TEXTURE_1D -> glCtx.textures.getTexture1DStates(glCtx.activeTextureUnit.getValue()).isEnabled();
            case GL11.GL_TEXTURE_2D -> glCtx.textures.getTextureUnitStates(glCtx.activeTextureUnit.getValue()).isEnabled();
            case GL12.GL_TEXTURE_3D -> glCtx.textures.getTexture3DStates(glCtx.activeTextureUnit.getValue()).isEnabled();
            case GL11.GL_TEXTURE_GEN_S -> glCtx.textures.getTexGenSStates(glCtx.activeTextureUnit.getValue()).isEnabled();
            case GL11.GL_TEXTURE_GEN_T -> glCtx.textures.getTexGenTStates(glCtx.activeTextureUnit.getValue()).isEnabled();
            case GL11.GL_TEXTURE_GEN_R -> glCtx.textures.getTexGenRStates(glCtx.activeTextureUnit.getValue()).isEnabled();
            case GL11.GL_TEXTURE_GEN_Q -> glCtx.textures.getTexGenQStates(glCtx.activeTextureUnit.getValue()).isEnabled();
            case GL11.GL_LIGHT_MODEL_LOCAL_VIEWER -> glCtx.lightModel.localViewer != 0.0f;
            case GL11.GL_LIGHT_MODEL_TWO_SIDE -> glCtx.lightModel.twoSide != 0.0f;
            default -> RENDER_BACKEND.getBoolean(pname);
        };
    }

    public static void glGetBoolean(int pname, ByteBuffer params) {
        final GLContextState glCtx = ctx();
        if (!isCachingEnabled()) {
            RENDER_BACKEND.getBoolean(pname, params);
            return;
        }

        switch (pname) {
            case GL11.GL_COLOR_WRITEMASK -> {
                final int pos = params.position();
                params.put(pos, (byte) (glCtx.colorMask.red ? GL11.GL_TRUE : GL11.GL_FALSE));
                params.put(pos + 1, (byte) (glCtx.colorMask.green ? GL11.GL_TRUE : GL11.GL_FALSE));
                params.put(pos + 2, (byte) (glCtx.colorMask.blue ? GL11.GL_TRUE : GL11.GL_FALSE));
                params.put(pos + 3, (byte) (glCtx.colorMask.alpha ? GL11.GL_TRUE : GL11.GL_FALSE));
            }
            default -> {
                if (!HAS_MULTIPLE_SET.contains(pname)) {
                    params.put(0, (byte) (glGetBoolean(pname) ? GL11.GL_TRUE : GL11.GL_FALSE));
                } else {
                    RENDER_BACKEND.getBoolean(pname, params);
                }
            }
        }
    }

    @SneakyThrows
    public static int getMatrixStackDepth(Matrix4fStack stack) {
        // JOML's curr is 0-based (0 = no pushes), but OpenGL starts at 1 (base matrix counts)
        return (int) MAT4_STACK_CURR_DEPTH.invokeExact(stack) + 1;
    }

    public static int glGetInteger(int pname) {
        final GLContextState glCtx = ctx();
        if (!isCachingEnabled()) {
            return RENDER_BACKEND.getInteger(pname);
        }

        return switch (pname) {
            case GL11.GL_ALPHA_TEST_FUNC -> glCtx.alphaState.getFunction();
            case GL11.GL_DEPTH_FUNC -> glCtx.depthState.getFunc();
            case GL11.GL_LIST_BASE -> glCtx.listBase;
            case GL11.GL_LIST_MODE -> DisplayListManager.getListMode();
            case GL11.GL_MATRIX_MODE -> glCtx.matrixMode.getMode();
            case GL11.GL_SHADE_MODEL -> glCtx.shadeModelState.getValue();
            // GL_TEXTURE_2D makes no sense here, but some mod still queries it...
            case GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_BINDING_2D -> getBoundTextureForServerState();
            case GL13.GL_ACTIVE_TEXTURE -> GL13.GL_TEXTURE0 + getActiveTextureUnitForServerState();
            case GL11.GL_COLOR_MATERIAL_FACE -> glCtx.colorMaterialFace.getValue();
            case GL11.GL_COLOR_MATERIAL_PARAMETER -> glCtx.colorMaterialParameter.getValue();
            case GL11.GL_MODELVIEW_STACK_DEPTH -> getMatrixStackDepth(glCtx.modelViewMatrix);
            case GL11.GL_PROJECTION_STACK_DEPTH -> getMatrixStackDepth(glCtx.projectionMatrix);
            case GL11.GL_DEPTH_WRITEMASK -> glCtx.depthState.isEnabled() ? GL11.GL_TRUE : GL11.GL_FALSE;
            case GL11.GL_CULL_FACE_MODE -> glCtx.polygonState.getCullFaceMode();
            case GL11.GL_FRONT_FACE -> glCtx.polygonState.getFrontFace();
            case GL11.GL_POLYGON_MODE -> glCtx.polygonState.getFrontMode();
            case GL11.GL_UNPACK_ALIGNMENT -> glCtx.pixelUnpackState.alignment();
            case GL11.GL_UNPACK_ROW_LENGTH -> glCtx.pixelUnpackState.rowLength();
            case GL11.GL_UNPACK_SKIP_ROWS -> glCtx.pixelUnpackState.skipRows();
            case GL11.GL_UNPACK_SKIP_PIXELS -> glCtx.pixelUnpackState.skipPixels();

            case GL12.GL_LIGHT_MODEL_COLOR_CONTROL -> glCtx.lightModel.colorControl;

            case GL14.GL_BLEND_DST_ALPHA -> glCtx.blendState.getDstAlpha();
            case GL11.GL_BLEND_DST, GL14.GL_BLEND_DST_RGB -> glCtx.blendState.getDstRgb();
            case GL14.GL_BLEND_SRC_ALPHA -> glCtx.blendState.getSrcAlpha();
            case GL11.GL_BLEND_SRC, GL14.GL_BLEND_SRC_RGB -> glCtx.blendState.getSrcRgb();
            case GL14.GL_BLEND_EQUATION -> glCtx.blendState.getEquationRgb();
            case GL20.GL_BLEND_EQUATION_ALPHA -> glCtx.blendState.getEquationAlpha();

            case GL11.GL_LOGIC_OP_MODE -> glCtx.logicOpMode.getValue();
            case GL11.GL_DRAW_BUFFER -> glCtx.drawBuffer.getValue();

            case GL11.GL_STENCIL_FUNC -> glCtx.stencilState.getFuncFront();
            case GL11.GL_STENCIL_REF -> glCtx.stencilState.getRefFront();
            case GL11.GL_STENCIL_VALUE_MASK -> glCtx.stencilState.getValueMaskFront();
            case GL11.GL_STENCIL_FAIL -> glCtx.stencilState.getFailOpFront();
            case GL11.GL_STENCIL_PASS_DEPTH_FAIL -> glCtx.stencilState.getZFailOpFront();
            case GL11.GL_STENCIL_PASS_DEPTH_PASS -> glCtx.stencilState.getZPassOpFront();
            case GL11.GL_STENCIL_WRITEMASK -> glCtx.stencilState.getWriteMaskFront();
            case GL11.GL_STENCIL_CLEAR_VALUE -> glCtx.stencilState.getClearValue();

            case GL15.GL_ARRAY_BUFFER_BINDING -> glCtx.boundVBO;
            case GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING -> VAOManager.boundEBO;
            case GL21.GL_PIXEL_UNPACK_BUFFER_BINDING -> glCtx.boundPixelUnpackBuffer;
            case GL21.GL_PIXEL_PACK_BUFFER_BINDING -> glCtx.boundPixelPackBuffer;

            case GL20.GL_CURRENT_PROGRAM -> glCtx.activeProgram;

            case GL30.GL_VERTEX_ARRAY_BINDING -> glCtx.boundVAO;

            case GL30.GL_DRAW_FRAMEBUFFER_BINDING -> glCtx.drawFramebuffer;
            case GL30.GL_READ_FRAMEBUFFER_BINDING -> glCtx.readFramebuffer;

            default -> switch (pname) {
                case GL11.GL_FOG_MODE -> glCtx.fogState.getFogMode();
                case GL11.GL_LINE_STIPPLE_PATTERN -> glCtx.lineState.getStipplePattern() & 0xFFFF;
                case GL11.GL_LINE_STIPPLE_REPEAT -> glCtx.lineState.getStippleFactor();
                case GL11.GL_LIST_INDEX -> DisplayListManager.isRecording() ? Math.max(DisplayListManager.getRecordingListId(), 0) : 0;
                default -> RENDER_BACKEND.getInteger(pname);
            };
        };
    }

    public static void glGetInteger(int pname, IntBuffer params) {
        if (!isCachingEnabled()) {
            RENDER_BACKEND.getInteger(pname, params);
            return;
        }

        switch (pname) {
            case GL11.GL_VIEWPORT -> ctx().viewportState.get(params);
            case GL11.GL_POLYGON_MODE -> {
                final PolygonState polygon = ctx().polygonState;
                final int pos = params.position();
                params.put(pos, polygon.getFrontMode());
                params.put(pos + 1, polygon.getBackMode());
            }
            default -> {
                if (!HAS_MULTIPLE_SET.contains(pname)) {
                    params.put(0, glGetInteger(pname));
                } else {
                    RENDER_BACKEND.getInteger(pname, params);
                }
            }
        }
    }

    public static void glGetMaterial(int face, int pname, FloatBuffer params) {
        final GLContextState glCtx = ctx();
        final MaterialStateStack state;
        if (face == GL11.GL_FRONT) {
            state = glCtx.frontMaterial;
        } else if (face == GL11.GL_BACK) {
            state = glCtx.backMaterial;
        } else {
            throw new RuntimeException("Invalid face parameter specified to glGetMaterial: " + face);
        }

        switch (pname) {
            case GL11.GL_AMBIENT -> state.ambient.get(0, params);
            case GL11.GL_DIFFUSE -> state.diffuse.get(0, params);
            case GL11.GL_SPECULAR -> state.specular.get(0, params);
            case GL11.GL_EMISSION -> state.emission.get(0, params);
            case GL11.GL_SHININESS -> params.put(params.position(), state.shininess);
            case GL11.GL_COLOR_INDEXES -> state.colorIndexes.get(0, params);
            default -> {
            }
        }
    }

    public static void glGetLight(int light, int pname, FloatBuffer params) {
        final LightStateStack state = ctx().lightDataStates[light - GL11.GL_LIGHT0];
        switch (pname) {
            case GL11.GL_AMBIENT -> state.ambient.get(0, params);
            case GL11.GL_DIFFUSE -> state.diffuse.get(0, params);
            case GL11.GL_SPECULAR -> state.specular.get(0, params);
            case GL11.GL_POSITION -> state.position.get(0, params);
            case GL11.GL_SPOT_DIRECTION -> state.spotDirection.get(0, params);
            case GL11.GL_SPOT_EXPONENT -> params.put(params.position(), state.spotExponent);
            case GL11.GL_SPOT_CUTOFF -> params.put(params.position(), state.spotCutoff);
            case GL11.GL_CONSTANT_ATTENUATION -> params.put(params.position(), state.constantAttenuation);
            case GL11.GL_LINEAR_ATTENUATION -> params.put(params.position(), state.linearAttenuation);
            case GL11.GL_QUADRATIC_ATTENUATION -> params.put(params.position(), state.quadraticAttenuation);
            default -> {
            }
        }
    }

    public static void glGetFloat(int pname, FloatBuffer params) {
        final GLContextState glCtx = ctx();
        if (!isCachingEnabled()) {
            RENDER_BACKEND.getFloat(pname, params);
            return;
        }

        switch (pname) {
            case GL11.GL_MODELVIEW_MATRIX -> glCtx.modelViewMatrix.get(0, params);
            case GL11.GL_PROJECTION_MATRIX -> glCtx.projectionMatrix.get(0, params);
            case GL11.GL_TEXTURE_MATRIX -> glCtx.textures.getTextureUnitMatrix(getActiveTextureUnit()).get(0, params);
            case GL11.GL_COLOR_CLEAR_VALUE -> glCtx.clearColor.get(params);
            case GL11.GL_CURRENT_COLOR -> glCtx.color.get(params);
            case GL11.GL_DEPTH_RANGE -> {
                final int pos = params.position();
                params.put(pos, (float) glCtx.viewportState.depthRangeNear);
                params.put(pos + 1, (float) glCtx.viewportState.depthRangeFar);
            }
            case GL14.GL_BLEND_COLOR -> {
                final int pos = params.position();
                params.put(pos, glCtx.blendState.getBlendColorR());
                params.put(pos + 1, glCtx.blendState.getBlendColorG());
                params.put(pos + 2, glCtx.blendState.getBlendColorB());
                params.put(pos + 3, glCtx.blendState.getBlendColorA());
            }
            case GL11.GL_CURRENT_NORMAL -> {
                final Vector3f normal = ShaderManager.getCurrentNormal();
                final int pos = params.position();
                params.put(pos, normal.x);
                params.put(pos + 1, normal.y);
                params.put(pos + 2, normal.z);
            }
            case GL11.GL_CURRENT_TEXTURE_COORDS -> {
                final Vector4f tc = ShaderManager.getCurrentTexCoord();
                final int pos = params.position();
                params.put(pos, tc.x);
                params.put(pos + 1, tc.y);
                params.put(pos + 2, tc.z);
                params.put(pos + 3, tc.w);
            }
            case GL11.GL_FOG_COLOR -> {
                final FloatBuffer fogBuf = glCtx.fogState.getFogColorBuffer();
                final int pos = params.position();
                params.put(pos, fogBuf.get(0));
                params.put(pos + 1, fogBuf.get(1));
                params.put(pos + 2, fogBuf.get(2));
                params.put(pos + 3, fogBuf.get(3));
            }
            case GL11.GL_LIGHT_MODEL_AMBIENT -> glCtx.lightModel.ambient.get(0, params);
            default -> {
                if (!HAS_MULTIPLE_SET.contains(pname)) {
                    params.put(0, glGetFloat(pname));
                } else {
                    RENDER_BACKEND.getFloat(pname, params);
                }
            }
        }
    }

    public static float glGetFloat(int pname) {
        final GLContextState glCtx = ctx();
        return switch (pname) {
            case GL11.GL_ALPHA_TEST_REF -> glCtx.alphaState.getReference();
            case GL11.GL_FOG_DENSITY -> glCtx.fogState.getDensity();
            case GL11.GL_FOG_START -> glCtx.fogState.getStart();
            case GL11.GL_FOG_END -> glCtx.fogState.getEnd();
            case GL11.GL_DEPTH_CLEAR_VALUE -> (float) glCtx.depthState.getClearValue();
            case GL11.GL_LINE_WIDTH -> glCtx.lineState.getWidth();
            case GL11.GL_POINT_SIZE -> glCtx.pointState.getSize();
            default -> RENDER_BACKEND.getFloat(pname);
        };
    }

    public static void glGetFloat(int pname, float[] params) {
        final FloatBuffer scratch = ctx().queryScratch;
        final FloatBuffer buf;
        if (params.length <= scratch.capacity()) {
            buf = scratch;
            buf.clear();
            buf.limit(params.length);
        } else {
            buf = BufferUtils.createFloatBuffer(params.length);
        }
        glGetFloat(pname, buf);
        buf.position(0);
        buf.get(params);
    }

    public static double glGetDouble(int pname) {
        return switch (pname) {
            case GL11.GL_DEPTH_CLEAR_VALUE -> ctx().depthState.getClearValue();
            default -> glGetFloat(pname);
        };
    }

    public static void glGetDouble(int pname, DoubleBuffer params) {
        final GLContextState glCtx = ctx();
        if (!isCachingEnabled()) {
            RENDER_BACKEND.getDouble(pname, params);
            return;
        }

        final int pos = params.position();
        switch (pname) {
            case GL11.GL_DEPTH_CLEAR_VALUE -> params.put(pos, glCtx.depthState.getClearValue());
            case GL11.GL_DEPTH_RANGE -> {
                params.put(pos, glCtx.viewportState.depthRangeNear);
                params.put(pos + 1, glCtx.viewportState.depthRangeFar);
            }
            case GL11.GL_MODELVIEW_MATRIX, GL11.GL_PROJECTION_MATRIX, GL11.GL_TEXTURE_MATRIX -> widenFromFloat(pname, params, 16);
            case GL11.GL_COLOR_CLEAR_VALUE, GL11.GL_CURRENT_COLOR, GL11.GL_CURRENT_TEXTURE_COORDS, GL11.GL_FOG_COLOR,
                GL11.GL_LIGHT_MODEL_AMBIENT, GL14.GL_BLEND_COLOR -> widenFromFloat(pname, params, 4);
            case GL11.GL_CURRENT_NORMAL -> widenFromFloat(pname, params, 3);
            default -> {
                if (!HAS_MULTIPLE_SET.contains(pname)) {
                    params.put(pos, glGetDouble(pname));
                } else {
                    RENDER_BACKEND.getDouble(pname, params);
                }
            }
        }
    }

    public static void glGetDouble(int pname, double[] params) {
        final DoubleBuffer scratch = ctx().queryScratchDouble;
        final DoubleBuffer buf;
        if (params.length <= scratch.capacity()) {
            buf = scratch;
            buf.clear();
            buf.limit(params.length);
        } else {
            buf = BufferUtils.createDoubleBuffer(params.length);
        }
        glGetDouble(pname, buf);
        buf.position(0);
        buf.get(params);
    }

    private static void widenFromFloat(int pname, DoubleBuffer params, int count) {
        final FloatBuffer scratch = ctx().queryScratch;
        scratch.clear();
        glGetFloat(pname, scratch);
        final int pos = params.position();
        for (int i = 0; i < count; i++) {
            params.put(pos + i, scratch.get(i));
        }
    }

    public static void glGetClipPlane(int plane, DoubleBuffer equation) {
        final int index = plane - GL11.GL_CLIP_PLANE0;
        if (index < 0 || index >= MAX_CLIP_PLANES) return;
        ctx().clipPlaneState.getEyePlane(index, equation);
    }

    // GLStateManager Functions

    public static void glBlendColor(float red, float green, float blue, float alpha) {
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordBlendColor(red, green, blue, alpha);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();
        final boolean bypass = !caching;
        if (bypass || red != glCtx.blendState.getBlendColorR() || green != glCtx.blendState.getBlendColorG() || blue != glCtx.blendState.getBlendColorB() || alpha != glCtx.blendState.getBlendColorA()) {
            glCtx.blendState.setBlendColorR(red);
            glCtx.blendState.setBlendColorG(green);
            glCtx.blendState.setBlendColorB(blue);
            glCtx.blendState.setBlendColorA(alpha);
            RENDER_BACKEND.blendColor(red, green, blue, alpha);
        }
    }

    public static void enableBlend() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordEnable(GL11.GL_BLEND);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final DeferredBlendHandler bh = GLSMHooks.blendHandler;
        if (bh != null && bh.isBlendLocked()) {
            bh.deferBlendModeToggle(true);
            return;
        }
        ctx().blendMode.enable();
    }

    public static void disableBlend() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDisable(GL11.GL_BLEND);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final DeferredBlendHandler bh = GLSMHooks.blendHandler;
        if (bh != null && bh.isBlendLocked()) {
            bh.deferBlendModeToggle(false);
            return;
        }
        ctx().blendMode.disable();
    }

    public static void enableScissorTest() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordEnable(GL11.GL_SCISSOR_TEST);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        ctx().scissorTest.enable();
    }

    public static void disableScissorTest() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDisable(GL11.GL_SCISSOR_TEST);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        ctx().scissorTest.disable();
    }

    public static void glBlendFunc(int srcFactor, int dstFactor) {
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordBlendFunc(srcFactor, dstFactor, srcFactor, dstFactor);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final DeferredBlendHandler bh = GLSMHooks.blendHandler;
        if (bh != null && bh.isBlendLocked()) {
            bh.deferBlendFunc(srcFactor, dstFactor, srcFactor, dstFactor);
            return;
        }
        final boolean caching = isCachingEnabled();
        if (GLSMConfig.hudCacheOverride) {
            glCtx.blendState.setAll(srcFactor, dstFactor, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            RENDER_BACKEND.blendFuncSeparate(srcFactor, dstFactor, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            if (GLSMHooks.BLEND_FUNC_CHANGE.hasListeners()) {
                GLSMHooks.blendFuncChangeEvent.srcRgb = srcFactor;
                GLSMHooks.blendFuncChangeEvent.dstRgb = dstFactor;
                GLSMHooks.blendFuncChangeEvent.srcAlpha = GL11.GL_ONE;
                GLSMHooks.blendFuncChangeEvent.dstAlpha = GL11.GL_ONE_MINUS_SRC_ALPHA;
                GLSMHooks.BLEND_FUNC_CHANGE.post(GLSMHooks.blendFuncChangeEvent);
            }
            return;
        }
        final boolean bypass = !caching;
        if (bypass
                || glCtx.blendState.getSrcRgb() != srcFactor
                || glCtx.blendState.getDstRgb() != dstFactor
                || glCtx.blendState.getSrcAlpha() != srcFactor
                || glCtx.blendState.getDstAlpha() != dstFactor) {
            glCtx.blendState.setAll(srcFactor, dstFactor, srcFactor, dstFactor);
            RENDER_BACKEND.blendFunc(srcFactor, dstFactor);
            if (GLSMHooks.BLEND_FUNC_CHANGE.hasListeners()) {
                GLSMHooks.blendFuncChangeEvent.srcRgb = srcFactor;
                GLSMHooks.blendFuncChangeEvent.dstRgb = dstFactor;
                GLSMHooks.blendFuncChangeEvent.srcAlpha = srcFactor;
                GLSMHooks.blendFuncChangeEvent.dstAlpha = dstFactor;
                GLSMHooks.BLEND_FUNC_CHANGE.post(GLSMHooks.blendFuncChangeEvent);
            }
        }
    }

    public static void glBlendEquation(int mode) {
        final GLContextState glCtx = ctx();
        if (DisplayListManager.isRecording()) {
            throw DisplayListManager.unsupportedInList("glBlendEquation");
        }
        final boolean caching = isCachingEnabled();
        final boolean bypass = !caching;
        if (bypass || glCtx.blendState.getEquationRgb() != mode || glCtx.blendState.getEquationAlpha() != mode) {
            glCtx.blendState.setEquationRgb(mode);
            glCtx.blendState.setEquationAlpha(mode);
            RENDER_BACKEND.blendEquation(mode);
        }
    }

    public static void glBlendEquationSeparate(int modeRGB, int modeAlpha) {
        final GLContextState glCtx = ctx();
        if (DisplayListManager.isRecording()) {
            throw DisplayListManager.unsupportedInList("glBlendEquationSeparate");
        }
        final boolean caching = isCachingEnabled();
        final boolean bypass = !caching;
        if (bypass || glCtx.blendState.getEquationRgb() != modeRGB || glCtx.blendState.getEquationAlpha() != modeAlpha) {
            glCtx.blendState.setEquationRgb(modeRGB);
            glCtx.blendState.setEquationAlpha(modeAlpha);
            RENDER_BACKEND.blendEquationSeparate(modeRGB, modeAlpha);
        }
    }

    public static void tryBlendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordBlendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final DeferredBlendHandler bh = GLSMHooks.blendHandler;
        if (bh != null && bh.isBlendLocked()) {
            bh.deferBlendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha);
            return;
        }
        if (GLSMConfig.hudCacheOverride && dstAlpha != GL11.GL_ONE_MINUS_SRC_ALPHA) {
            srcAlpha = GL11.GL_ONE;
            dstAlpha = GL11.GL_ONE_MINUS_SRC_ALPHA;
        }
        final boolean caching = isCachingEnabled();
        final boolean bypass = !caching;
        if (bypass || glCtx.blendState.getSrcRgb() != srcRgb || glCtx.blendState.getDstRgb() != dstRgb || glCtx.blendState.getSrcAlpha() != srcAlpha || glCtx.blendState.getDstAlpha() != dstAlpha) {
            glCtx.blendState.setAll(srcRgb, dstRgb, srcAlpha, dstAlpha);
            RENDER_BACKEND.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (GLSMHooks.BLEND_FUNC_CHANGE.hasListeners()) {
                GLSMHooks.blendFuncChangeEvent.srcRgb = srcRgb;
                GLSMHooks.blendFuncChangeEvent.dstRgb = dstRgb;
                GLSMHooks.blendFuncChangeEvent.srcAlpha = srcAlpha;
                GLSMHooks.blendFuncChangeEvent.dstAlpha = dstAlpha;
                GLSMHooks.BLEND_FUNC_CHANGE.post(GLSMHooks.blendFuncChangeEvent);
            }
        }
    }

    public static void glBlendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        tryBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
    }

    public static void checkCompiling() {
        checkCompiling("");
    }

    public static void checkCompiling(String name) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("Not yet implemented (" + name + ")");
        }
    }

    // Records glBegin/glEnd/glVertex and compiles them to a VBO
    // Currently only used inside of display lists

    public static void glNormal3b(byte nx, byte ny, byte nz) {
        final float fnx = b2f(nx), fny = b2f(ny), fnz = b2f(nz);
        ShaderManager.setCurrentNormal(fnx, fny, fnz);
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.setNormal(fnx, fny, fnz);
            return;
        }
        ctx().dirtyNormalAttrib = true;
    }

    public static void glNormal3d(double nx, double ny, double nz) {
        final float fnx = (float) nx, fny = (float) ny, fnz = (float) nz;
        ShaderManager.setCurrentNormal(fnx, fny, fnz);
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.setNormal(fnx, fny, fnz);
            return;
        }
        ctx().dirtyNormalAttrib = true;
    }

    public static void glNormal3f(float nx, float ny, float nz) {
        ShaderManager.setCurrentNormal(nx, ny, nz);
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.setNormal(nx, ny, nz);
            return;
        }
        ctx().dirtyNormalAttrib = true;
    }

    public static void glNormal3i(int nx, int ny, int nz) {
        final float fnx = nx / 2147483647.0f, fny = ny / 2147483647.0f, fnz = nz / 2147483647.0f;
        ShaderManager.setCurrentNormal(fnx, fny, fnz);
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.setNormal(fnx, fny, fnz);
            return;
        }
        ctx().dirtyNormalAttrib = true;
    }

    public static void glDepthFunc(int func) {
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDepthFunc(func);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();
        if (!caching || func != glCtx.depthState.getFunc()) {
            glCtx.depthState.setFunc(func);
            RENDER_BACKEND.depthFunc(func);
        }
    }

    public static void glDepthMask(boolean mask) {
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDepthMask(mask);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final DeferredDepthColorHandler dch = GLSMHooks.depthColorHandler;
        if (dch != null && dch.isDepthColorLocked()) {
            dch.deferDepthEnable(mask);
            return;
        }
        final boolean caching = isCachingEnabled();
        if (!caching || mask != glCtx.depthState.isEnabled()) {
            glCtx.depthState.setEnabled(mask);
            RENDER_BACKEND.depthMask(mask);
        }
    }

    public static void glEdgeFlag(boolean flag) {
        // No-op: edge flags are removed in GL 3.3 core profile and have no equivalent
    }

    public static void glColor4f(float red, float green, float blue, float alpha) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordColor(red, green, blue, alpha);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        changeColor(red, green, blue, alpha);
    }

    public static void glColor4d(double red, double green, double blue, double alpha) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordColor((float) red, (float) green, (float) blue, (float) alpha);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        changeColor((float) red, (float) green, (float) blue, (float) alpha);
    }

    public static void glColor4b(byte red, byte green, byte blue, byte alpha) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordColor(b2f(red), b2f(green), b2f(blue), b2f(alpha));
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        changeColor(b2f(red), b2f(green), b2f(blue), b2f(alpha));
    }

    public static void glColor4ub(byte red, byte green, byte blue, byte alpha) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordColor(ub2f(red), ub2f(green), ub2f(blue), ub2f(alpha));
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        changeColor(ub2f(red), ub2f(green), ub2f(blue), ub2f(alpha));
    }

    public static void glColor3f(float red, float green, float blue) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordColor(red, green, blue, 1.0F);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        changeColor(red, green, blue, 1.0F);
    }

    public static void glColor3d(double red, double green, double blue) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordColor((float) red, (float) green, (float) blue, 1.0F);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        changeColor((float) red, (float) green, (float) blue, 1.0F);
    }

    public static void glColor3b(byte red, byte green, byte blue) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordColor(b2f(red), b2f(green), b2f(blue), 1.0F);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        changeColor(b2f(red), b2f(green), b2f(blue), 1.0F);
    }

    public static void glColor3ub(byte red, byte green, byte blue) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordColor(ub2f(red), ub2f(green), ub2f(blue), 1.0F);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        changeColor(ub2f(red), ub2f(green), ub2f(blue), 1.0F);
    }

    private static float ub2f(byte b) {
        return (b & 0xFF) / 255.0F;
    }

    private static float b2f(byte b) {
        return ((b - Byte.MIN_VALUE) & 0xFF) / 255.0F;
    }

    public static float i2f(int i) {return ((i - Integer.MIN_VALUE) & 0xFFFFFFFFL) / 4294967295.0F;}

    private static boolean changeColor(float red, float green, float blue, float alpha) {
        final GLContextState glCtx = ctx();
        // Helper function for glColor*
        final boolean caching = isCachingEnabled();
        if (!caching || red != glCtx.color.getRed() || green != glCtx.color.getGreen() || blue != glCtx.color.getBlue() || alpha != glCtx.color.getAlpha()) {
            glCtx.color.setRed(red);
            glCtx.color.setGreen(green);
            glCtx.color.setBlue(blue);
            glCtx.color.setAlpha(alpha);
            glCtx.colorGeneration++;
            if (ImmediateModeRecorder.isDrawing()) {
                ImmediateModeRecorder.setColor(red, green, blue, alpha);
            }
            glCtx.dirtyColorAttrib = true;
            // Only set while an enchantment-glint span is active
            final GlintColorHandler glintHandler = GLSMHooks.glintColorHandler;
            if (glintHandler != null) {
                glintHandler.onColorChanged(red, green, blue, alpha);
            }
            return true;
        }
        return false;
    }

    private static final Color4 DirtyColor = new Color4(-1.0F, -1.0F, -1.0F, -1.0F);

    public static void clearCurrentColor() {
        // Marks the cache dirty, doesn't actually reset the color
        ctx().color.set(DirtyColor);
    }

    public static void glColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordColorMask(red, green, blue, alpha);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final DeferredDepthColorHandler dch = GLSMHooks.depthColorHandler;
        if (dch != null && dch.isDepthColorLocked()) {
            dch.deferColorMask(red, green, blue, alpha);
            return;
        }
        final boolean caching = isCachingEnabled();
        final boolean bypass = !caching;
        if (bypass || red != glCtx.colorMask.red || green != glCtx.colorMask.green || blue != glCtx.colorMask.blue || alpha != glCtx.colorMask.alpha) {
            glCtx.colorMask.setAll(red, green, blue, alpha);
            RENDER_BACKEND.colorMask(red, green, blue, alpha);
        }
    }

    // Clear Color
    public static void glClearColor(float red, float green, float blue, float alpha) {
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordClearColor(red, green, blue, alpha);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();
        if (!caching || red != glCtx.clearColor.getRed() || green != glCtx.clearColor.getGreen() || blue != glCtx.clearColor.getBlue() || alpha != glCtx.clearColor.getAlpha()) {
            glCtx.clearColor.setRed(red);
            glCtx.clearColor.setGreen(green);
            glCtx.clearColor.setBlue(blue);
            glCtx.clearColor.setAlpha(alpha);
            RENDER_BACKEND.clearColor(red, green, blue, alpha);
        }
    }

    public static void glClearDepth(double depth) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordClearDepth(depth);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        if (isCachingEnabled()) ctx().depthState.setClearValue(depth);
        RENDER_BACKEND.clearDepth(depth);
    }

    // ALPHA
    public static void enableAlphaTest() {
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordEnable(GL11.GL_ALPHA_TEST);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final DeferredAlphaHandler ah = GLSMHooks.alphaHandler;
        if (ah != null && ah.isAlphaTestLocked()) {
            ah.deferAlphaTestToggle(true);
            return;
        }
        glCtx.alphaTest.enable();
        glCtx.fragmentGeneration++;
        if (GLSMHooks.ALPHA_STATE_CHANGE.hasListeners()) {
            GLSMHooks.ALPHA_STATE_CHANGE.post(GLSMHooks.alphaStateChangeEvent);
        }
    }

    public static void disableAlphaTest() {
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDisable(GL11.GL_ALPHA_TEST);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final DeferredAlphaHandler ah = GLSMHooks.alphaHandler;
        if (ah != null && ah.isAlphaTestLocked()) {
            ah.deferAlphaTestToggle(false);
            return;
        }
        glCtx.alphaTest.disable();
        glCtx.fragmentGeneration++;
        if (GLSMHooks.ALPHA_STATE_CHANGE.hasListeners()) {
            GLSMHooks.ALPHA_STATE_CHANGE.post(GLSMHooks.alphaStateChangeEvent);
        }
    }

    public static void glAlphaFunc(int function, float reference) {
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordAlphaFunc(function, reference);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final DeferredAlphaHandler ah = GLSMHooks.alphaHandler;
        if (ah != null && ah.isAlphaTestLocked()) {
            ah.deferAlphaFunc(function, reference);
            return;
        }
        if (isCachingEnabled()) {
            glCtx.alphaState.setFunction(function);
            glCtx.alphaState.setReference(reference);
            glCtx.fragmentGeneration++;
            if (GLSMHooks.ALPHA_STATE_CHANGE.hasListeners()) {
                GLSMHooks.ALPHA_STATE_CHANGE.post(GLSMHooks.alphaStateChangeEvent);
            }
        }
    }

    // Textures
    public static void glActiveTexture(int texture) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordActiveTexture(texture);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final int newTexture = texture - GL13.GL_TEXTURE0;
        if (!isValidTextureUnit(newTexture, texture)) {
            return;
        }
        final boolean caching = isCachingEnabled();
        if (!caching || getActiveTextureUnit() != newTexture) {
            ctx().activeTextureUnit.setValue(newTexture);
            RENDER_BACKEND.activeTexture(texture);
        }
    }

    private static boolean isValidTextureUnit(int unit, int rawEnum) {
        if (unit >= 0 && unit < MAX_TEXTURE_UNITS) {
            return true;
        }
        if (!warnedInvalidTextureUnit) {
            warnedInvalidTextureUnit = true;
            LOGGER.warn("glActiveTexture({}) is not a valid texture unit enum (expected GL_TEXTURE0..GL_TEXTURE{}); ignoring", rawEnum, MAX_TEXTURE_UNITS - 1, new Throwable("call site"));
        }
        return false;
    }

    private static boolean warnedInvalidTextureUnit = false;

    public static void glActiveTextureARB(int texture) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordActiveTexture(texture);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final int newTexture = texture - GL13.GL_TEXTURE0;
        if (!isValidTextureUnit(newTexture, texture)) {
            return;
        }
        final boolean caching = isCachingEnabled();
        if (!caching || getActiveTextureUnit() != newTexture) {
            ctx().activeTextureUnit.setValue(newTexture);
            RENDER_BACKEND.activeTexture(texture);
        }
    }

    public static void glClientActiveTexture(int texture) {
        ctx().clientActiveTextureUnit = texture - GL13.GL_TEXTURE0;
    }

    public static int getClientActiveTextureUnit() {
        return ctx().clientActiveTextureUnit;
    }

    private static int texCoordAttributeLocation() {
        return switch (ctx().clientActiveTextureUnit) {
            case 0 -> Usage.PRIMARY_UV.getAttributeLocation();
            case 1 -> Usage.SECONDARY_UV.getAttributeLocation();
            default -> -1;
        };
    }

    private static int getBoundTexture() {
        return getBoundTexture(ctx().activeTextureUnit.getValue());
    }

    private static int getBoundTexture(int unit) {
        return ctx().textures.getTextureUnitBindings(unit).getBinding();
    }

    public static void glBindTexture(int target, int texture) {
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordBindTexture(target, texture);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        // Rebinding a deferred-delete texture rescues it (Mesa/compat-profile behavior)
        if (texture != 0) {
            deferredDeleteTextures.remove(texture);
        }

        if (!isCachingEnabled()) {
            RENDER_BACKEND.bindTexture(target, texture);
            return;
        }

        final int activeUnit = glCtx.activeTextureUnit.getValue();
        final TextureBinding textureUnit = glCtx.textures.getTextureUnitBindings(activeUnit);
        final int cachedBinding = textureUnit.getBinding();

        if (cachedBinding != texture || (texture == 0 && textureUnit.getTarget() != target)) {
            if (Tracy.ENABLED) texBindMisses++;
            RENDER_BACKEND.bindTexture(target, texture);
            textureUnit.setBinding(texture, target);
            if (texture != 0) {
                trackMaxBoundTextureUnit(activeUnit);
            }
            if (!lockBindCallback && target == GL11.GL_TEXTURE_2D && glCtx.activeTextureUnit.getValue() == 0) {
                lockBindCallback = true;
                if (GLSMHooks.TEXTURE_BIND.hasListeners()) {
                    GLSMHooks.textureBindEvent.textureId = texture;
                    GLSMHooks.TEXTURE_BIND.post(GLSMHooks.textureBindEvent);
                }
                RenderSystem.bindTextureToUnit(0, texture);
                lockBindCallback = false;
            }
        }
    }

    private static int changeFormatIfDeprecated(int internalformat) {
        internalformat = GLESFormatRemap.promoteAlphaFormat(internalformat);
        if (RenderSystem.isGLES()) {
            internalformat = GLESFormatRemap.remapInternalFormat(internalformat);
        }
        return internalformat;
    }

    public static final class GLESTexImageRemap {
        private final GLESFormatRemap.Result r;
        private GLESTexImageRemap(GLESFormatRemap.Result r) { this.r = r; }
        public int internalFormat() { return r.internalFormat(); }
        public int format() { return r.format(); }
        public int type() { return r.type(); }
    }

    public static GLESTexImageRemap remapTexImageForGLES(int internalformat, int format, int type) {
        return new GLESTexImageRemap(GLESFormatRemap.apply(internalformat, format, type, RenderSystem.isGLES()));
    }

    static {
        if (ByteOrder.nativeOrder() != ByteOrder.LITTLE_ENDIAN) {
            LOGGER.warn("GLSM GLES pixel-type remap assumes little-endian host; big-endian detected - BGRA uploads may be byte-swapped");
        }
    }

    private static int remapPixelTypeForGLES(int format, int type) {
        return RenderSystem.isGLES() ? GLESFormatRemap.remapPixelType(format, type) : type;
    }

    private static int remapTypeForRemappedInternalFormat(int internalformat, int type) {
        if (!RenderSystem.isGLES()) return type;
        if (!GLESFormatRemap.isGenericPixelType(type)) return type;
        return GLESFormatRemap.typeForInternalFormatES32(internalformat, type);
    }

    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, IntBuffer pixels) {
        internalformat = changeFormatIfDeprecated(internalformat);
        type = remapPixelTypeForGLES(format, type);
        type = remapTypeForRemappedInternalFormat(internalformat, type);
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordComplexCommand(TexImage2DCmd.fromIntBuffer(target, level, internalformat, width, height, border, format, type, pixels, ctx().pixelUnpackState));
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        TextureInfoCache.INSTANCE.onTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
        suspendPixelUnpackBuffer();
        RENDER_BACKEND.texImage2D(target, level, internalformat, width, height, border, format, type, pixels);
        restorePixelUnpackBuffer();
        if (mode == RecordMode.NONE && RenderSystem.isLTW()) LTWWorkaround.onTexImage2D(target, level, format, pixels != null);
        maybeGenerateMipmap(target, level);
    }

    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, FloatBuffer pixels) {
        internalformat = changeFormatIfDeprecated(internalformat);
        type = remapPixelTypeForGLES(format, type);
        type = remapTypeForRemappedInternalFormat(internalformat, type);
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordComplexCommand(TexImage2DCmd.fromFloatBuffer(target, level, internalformat, width, height, border, format, type, pixels, ctx().pixelUnpackState));
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        TextureInfoCache.INSTANCE.onTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
        suspendPixelUnpackBuffer();
        RENDER_BACKEND.texImage2D(target, level, internalformat, width, height, border, format, type, pixels);
        restorePixelUnpackBuffer();
        if (mode == RecordMode.NONE && RenderSystem.isLTW()) LTWWorkaround.onTexImage2D(target, level, format, pixels != null);
        maybeGenerateMipmap(target, level);
    }

    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, DoubleBuffer pixels) {
        internalformat = changeFormatIfDeprecated(internalformat);
        type = remapPixelTypeForGLES(format, type);
        type = remapTypeForRemappedInternalFormat(internalformat, type);
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordComplexCommand(TexImage2DCmd.fromDoubleBuffer(target, level, internalformat, width, height, border, format, type, pixels, ctx().pixelUnpackState));
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        TextureInfoCache.INSTANCE.onTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
        suspendPixelUnpackBuffer();
        RENDER_BACKEND.texImage2D(target, level, internalformat, width, height, border, format, type, pixels);
        restorePixelUnpackBuffer();
        if (mode == RecordMode.NONE && RenderSystem.isLTW()) LTWWorkaround.onTexImage2D(target, level, format, pixels != null);
        maybeGenerateMipmap(target, level);
    }

    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, ByteBuffer pixels) {
        internalformat = changeFormatIfDeprecated(internalformat);
        type = remapPixelTypeForGLES(format, type);
        type = remapTypeForRemappedInternalFormat(internalformat, type);
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordComplexCommand(TexImage2DCmd.fromByteBuffer(target, level, internalformat, width, height, border, format, type, pixels, ctx().pixelUnpackState));
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        TextureInfoCache.INSTANCE.onTexImage2D(target, level, internalformat, width, height, border, format, type, pixels != null ? pixels.asIntBuffer() : null);
        suspendPixelUnpackBuffer();
        RENDER_BACKEND.texImage2D(target, level, internalformat, width, height, border, format, type, pixels);
        restorePixelUnpackBuffer();
        if (mode == RecordMode.NONE && RenderSystem.isLTW()) LTWWorkaround.onTexImage2D(target, level, format, pixels != null);
        maybeGenerateMipmap(target, level);
    }

    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, long pixels_buffer_offset) {
        internalformat = changeFormatIfDeprecated(internalformat);
        type = remapPixelTypeForGLES(format, type);
        type = remapTypeForRemappedInternalFormat(internalformat, type);
        if (DisplayListManager.isRecording()) {
            throw DisplayListManager.unsupportedInList("glTexImage2D with buffer offset");
        }
        TextureInfoCache.INSTANCE.onTexImage2D(target, level, internalformat, width, height, border, format, type, pixels_buffer_offset);
        RENDER_BACKEND.texImage2D(target, level, internalformat, width, height, border, format, type, pixels_buffer_offset);
        maybeGenerateMipmap(target, level);
    }

    public static void glTexCoord1f(float s) {
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.setTexCoord(s, 0.0f);
            return;
        }
        ShaderManager.setCurrentTexCoord(s, 0.0f, 0.0f, 1.0f);
        ctx().dirtyTexCoordAttrib = true;
    }

    public static void glTexCoord1d(double s) {
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.setTexCoord((float) s, 0.0f);
            return;
        }
        ShaderManager.setCurrentTexCoord((float) s, 0.0f, 0.0f, 1.0f);
        ctx().dirtyTexCoordAttrib = true;
    }

    public static void glTexCoord2f(float s, float t) {
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.setTexCoord(s, t);
            return;
        }
        ShaderManager.setCurrentTexCoord(s, t, 0.0f, 1.0f);
        ctx().dirtyTexCoordAttrib = true;
    }

    public static void glTexCoord2d(double s, double t) {
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.setTexCoord((float) s, (float) t);
            return;
        }
        ShaderManager.setCurrentTexCoord((float) s, (float) t, 0.0f, 1.0f);
        ctx().dirtyTexCoordAttrib = true;
    }

    public static void glTexCoord3f(float s, float t, float r) {
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.setTexCoord(s, t);  // Only track s,t for 2D textures
            return;
        }
        ShaderManager.setCurrentTexCoord(s, t, r, 1.0f);
        ctx().dirtyTexCoordAttrib = true;
    }

    public static void glTexCoord3d(double s, double t, double r) {
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.setTexCoord((float) s, (float) t);
            return;
        }
        ShaderManager.setCurrentTexCoord((float) s, (float) t, (float) r, 1.0f);
        ctx().dirtyTexCoordAttrib = true;
    }

    public static void glTexCoord4f(float s, float t, float r, float q) {
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.setTexCoord(s, t);
            return;
        }
        ShaderManager.setCurrentTexCoord(s, t, r, q);
        ctx().dirtyTexCoordAttrib = true;
    }

    public static void glTexCoord4d(double s, double t, double r, double q) {
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.setTexCoord((float) s, (float) t);
            return;
        }
        ShaderManager.setCurrentTexCoord((float) s, (float) t, (float) r, (float) q);
        ctx().dirtyTexCoordAttrib = true;
    }

    public static void glDeleteTextures(int id) {
        deferDeleteTexture(id);
        onDeleteTexture(id);
    }

    public static void glDeleteTextures(IntBuffer ids) {
        for (int i = 0; i < ids.remaining(); i++) {
            final int id = ids.get(ids.position() + i);
            deferDeleteTexture(id);
            onDeleteTexture(id);
        }
    }

    /**
     * Defer deleting textures until it has been reclaimed.
     * Why you might ask?  In compat profile if you bind a texture that doesn't exist, ie a deleted texture,
     * mesa will silently create it for you, in core this is an error.  Why would you be binding a deleted texture?
     * Damned if I know, but vanilla and mods do it... they create one, delete it, and then bind it....
     *
     * So... we shrink the texture to 1x1 to free GPU memory, unbind from all units, but keep the name valid.
     * */
    private static void deferDeleteTexture(int id) {
        final GLContextState glCtx = ctx();
        if (id == 0) return;

        final int savedUnit = glCtx.activeTextureUnit.getValue();
        final int savedBinding = glCtx.textures.getTextureUnitBindings(savedUnit).getBinding();
        boolean changedUnit = false;

        // Unbind from all units that have this texture
        for (int i = 0; i <= glCtx.maxBoundTextureUnit; i++) {
            if (glCtx.textures.getTextureUnitBindings(i).getBinding() == id) {
                if (i != savedUnit) {
                    RENDER_BACKEND.activeTexture(GL13.GL_TEXTURE0 + i);
                    changedUnit = true;
                }
                RENDER_BACKEND.bindTexture(GL11.GL_TEXTURE_2D, 0);
            }
        }

        // Restore active unit, then shrink the texture via a temporary bind
        if (changedUnit) {
            RENDER_BACKEND.activeTexture(GL13.GL_TEXTURE0 + savedUnit);
        }
        RENDER_BACKEND.bindTexture(GL11.GL_TEXTURE_2D, id);
        RENDER_BACKEND.texParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        suspendPixelUnpackBuffer();
        RENDER_BACKEND.texImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R8, 1, 1, 0, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        restorePixelUnpackBuffer();

        // Restore previous binding on active unit
        RENDER_BACKEND.bindTexture(GL11.GL_TEXTURE_2D, savedBinding == id ? 0 : savedBinding);

        deferredDeleteTextures.add(id);
    }

    /** Flush deferred deletes so the driver can recycle names. */
    private static void flushDeferredTextureDeletes() {
        if (deferredDeleteTextures.isEmpty()) return;
        final var it = deferredDeleteTextures.iterator();
        while (it.hasNext()) {
            RENDER_BACKEND.deleteTextures(it.nextInt());
        }
        deferredDeleteTextures.clear();
    }

    public static int glGenTextures() {
        flushDeferredTextureDeletes();
        return RENDER_BACKEND.genTextures();
    }

    public static void glGenTextures(IntBuffer textures) {
        flushDeferredTextureDeletes();
        RENDER_BACKEND.genTextures(textures);
    }

    public static void enableTexture() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordEnable(GL11.GL_TEXTURE_2D);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        ctx().textures.getTextureUnitStates(getActiveTextureUnit()).enable();
    }

    public static void disableTexture() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDisable(GL11.GL_TEXTURE_2D);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        ctx().textures.getTextureUnitStates(getActiveTextureUnit()).disable();
    }

    private static final String PIXELTRANSFER_MSG = "glPixelTransfer is not available in GL 3.3 core profile.";

    public static void glPixelTransferf(int pname, float param) {
        guardUnsupportedFFP("glPixelTransfer", PIXELTRANSFER_MSG);
    }

    public static void glPixelTransferi(int pname, int param) {
        guardUnsupportedFFP("glPixelTransfer", PIXELTRANSFER_MSG);
    }

    private static final String RASTERPOS_MSG = "glRasterPos is not available in GL 3.3 core profile.";

    public static void glRasterPos2f(float x, float y) { guardUnsupportedFFP("glRasterPos", RASTERPOS_MSG); }
    public static void glRasterPos2d(double x, double y) { guardUnsupportedFFP("glRasterPos", RASTERPOS_MSG); }
    public static void glRasterPos2i(int x, int y) { guardUnsupportedFFP("glRasterPos", RASTERPOS_MSG); }
    public static void glRasterPos3f(float x, float y, float z) { guardUnsupportedFFP("glRasterPos", RASTERPOS_MSG); }
    public static void glRasterPos3d(double x, double y, double z) { guardUnsupportedFFP("glRasterPos", RASTERPOS_MSG); }
    public static void glRasterPos3i(int x, int y, int z) { guardUnsupportedFFP("glRasterPos", RASTERPOS_MSG); }
    public static void glRasterPos4f(float x, float y, float z, float w) { guardUnsupportedFFP("glRasterPos", RASTERPOS_MSG); }
    public static void glRasterPos4d(double x, double y, double z, double w) { guardUnsupportedFFP("glRasterPos", RASTERPOS_MSG); }
    public static void glRasterPos4i(int x, int y, int z, int w) { guardUnsupportedFFP("glRasterPos", RASTERPOS_MSG); }

    public static void setFilter(boolean bilinear, boolean mipmap) {
        final int i, j;
        if (bilinear) {
            i = mipmap ? GL11.GL_LINEAR_MIPMAP_LINEAR : GL11.GL_LINEAR;
            j = GL11.GL_LINEAR;
        } else {
            i = mipmap ? GL11.GL_LINEAR_MIPMAP_LINEAR : GL11.GL_NEAREST;
            j = GL11.GL_NEAREST;
        }
        glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, i);
        glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, j);
    }

    public static void glBegin(int mode) {
        ctx().unit23TexCoordSetDuringDraw = false;
        if (DisplayListManager.isRecording()) {
            ImmediateModeRecorder.begin(mode);
            return;
        }
        ImmediateModeRecorder.beginLive(mode);
    }

    public static void glEnd() {
        final DirectTessellator result = ImmediateModeRecorder.end();
        if (result != null) {
            if (DisplayListManager.isRecording()) {
                if (DisplayListManager.isCompileAndExecute() && initConfig != null && initConfig.getDirectDrawer() != null) {
                    final CommandRecorder recorder = DisplayListManager.pauseRecording();
                    initConfig.getDirectDrawer().accept(result);
                    DisplayListManager.resumeRecording(recorder);
                }
                DisplayListManager.addImmediateModeDraw(result);
                return;
            }
            if (initConfig != null && initConfig.getDirectDrawer() != null) {
                initConfig.getDirectDrawer().accept(result);
            }
        }
    }

    // Vertex methods for display list recording and live immediate mode
    public static void glVertex2f(float x, float y) {
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.vertex(x, y, 0.0f);
        }
    }

    public static void glVertex2i(int x, int y) {
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.vertex((float) x, (float) y, 0.0f);
        }
    }

    public static void glVertex2d(double x, double y) {
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.vertex((float) x, (float) y, 0.0f);
        }
    }

    public static void glVertex3f(float x, float y, float z) {
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.vertex(x, y, z);
        }
    }

    public static void glVertex3d(double x, double y, double z) {
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.vertex((float) x, (float) y, (float) z);
        }
    }

    public static void glVertex3i(int x, int y, int z) {
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.vertex((float) x, (float) y, (float) z);
        }
    }

    public static void preDraw(int drawMode) {
        final boolean locked = acquireDrawLock();
        try {
            prepareLineEmulation(drawMode);
            ShaderManager.getInstance().preDraw();
            prepareClientArrays();
        } finally {
            if (locked) releaseDrawLock();
        }
    }

    public static void preDraw() {
        final boolean locked = acquireDrawLock();
        try {
            disableLineEmulation();
            ShaderManager.getInstance().preDraw();
            prepareClientArrays();
        } finally {
            if (locked) releaseDrawLock();
        }
    }

    public static void glDrawElements(int mode, ByteBuffer indices) {
        drawCalls++;
        CommandRecorder savedRecorder = null;
        final RecordMode recordMode = DisplayListManager.getRecordMode();
        if (recordMode != RecordMode.NONE) {
            final IndexedDrawCapture capture = IndexedDrawCapture.createFromClientIndices(mode, indices.remaining(), GL11.GL_UNSIGNED_BYTE, MemoryUtilities.memAddress(indices), indices.remaining());
            if (capture != null) DisplayListManager.recordIndexedDrawCapture(capture);
            if (recordMode == RecordMode.COMPILE) return;
            savedRecorder = DisplayListManager.pauseRecording();
        } else if (FeedbackManager.isFeedbackMode()) {
            FeedbackManager.processDrawElements(mode, indices);
            return;
        }
        try {
            if (mode == GL11.GL_QUADS) {
                QuadConverter.drawQuadElementsAsTriangles(indices.remaining(), GL11.GL_UNSIGNED_BYTE, indices);
                return;
            }
            preDraw(mode);
            RENDER_BACKEND.drawElements(mode, indices);
        } finally {
            if (savedRecorder != null) DisplayListManager.resumeRecording(savedRecorder);
        }
    }

    public static void glDrawElements(int mode, IntBuffer indices) {
        drawCalls++;
        CommandRecorder savedRecorder = null;
        final RecordMode recordMode = DisplayListManager.getRecordMode();
        if (recordMode != RecordMode.NONE) {
            final IndexedDrawCapture capture = IndexedDrawCapture.createFromClientIndices(mode, indices.remaining(), GL11.GL_UNSIGNED_INT, MemoryUtilities.memAddress(indices), (long) indices.remaining() << 2);
            if (capture != null) DisplayListManager.recordIndexedDrawCapture(capture);
            if (recordMode == RecordMode.COMPILE) return;
            savedRecorder = DisplayListManager.pauseRecording();
        } else if (FeedbackManager.isFeedbackMode()) {
            FeedbackManager.processDrawElements(mode, indices);
            return;
        }
        try {
            if (mode == GL11.GL_QUADS) {
                QuadConverter.drawQuadElementsAsTriangles(indices);
                return;
            }
            preDraw(mode);
            RENDER_BACKEND.drawElements(mode, indices);
        } finally {
            if (savedRecorder != null) DisplayListManager.resumeRecording(savedRecorder);
        }
    }

    public static void glDrawElements(int mode, ShortBuffer indices) {
        drawCalls++;
        CommandRecorder savedRecorder = null;
        final RecordMode recordMode = DisplayListManager.getRecordMode();
        if (recordMode != RecordMode.NONE) {
            final IndexedDrawCapture capture = IndexedDrawCapture.createFromClientIndices(mode, indices.remaining(), GL11.GL_UNSIGNED_SHORT, MemoryUtilities.memAddress(indices), (long) indices.remaining() << 1);
            if (capture != null) DisplayListManager.recordIndexedDrawCapture(capture);
            if (recordMode == RecordMode.COMPILE) return;
            savedRecorder = DisplayListManager.pauseRecording();
        } else if (FeedbackManager.isFeedbackMode()) {
            FeedbackManager.processDrawElements(mode, indices);
            return;
        }
        try {
            if (mode == GL11.GL_QUADS) {
                QuadConverter.drawQuadElementsAsTriangles(indices);
                return;
            }
            preDraw(mode);
            RENDER_BACKEND.drawElements(mode, indices);
        } finally {
            if (savedRecorder != null) DisplayListManager.resumeRecording(savedRecorder);
        }
    }

    public static void glDrawElements(int mode, int count, int type, ByteBuffer indices) {
        drawCalls++;
        CommandRecorder savedRecorder = null;
        final RecordMode recordMode = DisplayListManager.getRecordMode();
        if (recordMode != RecordMode.NONE) {
            final IndexedDrawCapture capture = IndexedDrawCapture.createFromClientIndices(mode, count, type, MemoryUtilities.memAddress(indices), indices.remaining());
            if (capture != null) DisplayListManager.recordIndexedDrawCapture(capture);
            if (recordMode == RecordMode.COMPILE) return;
            savedRecorder = DisplayListManager.pauseRecording();
        } else if (FeedbackManager.isFeedbackMode()) {
            FeedbackManager.processDrawElements(mode, count, type, indices);
            return;
        }
        try {
            if (mode == GL11.GL_QUADS) {
                QuadConverter.drawQuadElementsAsTriangles(count, type, indices);
                return;
            }
            preDraw(mode);
            RENDER_BACKEND.drawElements(mode, count, type, indices);
        } finally {
            if (savedRecorder != null) DisplayListManager.resumeRecording(savedRecorder);
        }
    }

    public static void glDrawElements(int mode, int indices_count, int type, long indices_buffer_offset) {
        drawCalls++;
        CommandRecorder savedRecorder = null;
        final RecordMode recordMode = DisplayListManager.getRecordMode();
        if (recordMode != RecordMode.NONE) {
            // Core profile: a default VAO is generated at init and glBindVertexArray(0)
            // is redirected to it, so a VAO is always bound here — no fallback branches.
            final IndexedDrawCapture capture = IndexedDrawCapture.create(mode, indices_count, type, indices_buffer_offset, VAOManager.boundEBO);
            if (capture != null) {
                DisplayListManager.recordIndexedDrawCapture(capture);
            }
            if (recordMode == RecordMode.COMPILE) {
                return;
            }
            savedRecorder = DisplayListManager.pauseRecording();
        } else if (FeedbackManager.isFeedbackMode()) {
            FeedbackManager.processDrawElements(mode, indices_count, type, indices_buffer_offset);
            return;
        }
        try {
            if (mode == GL11.GL_QUADS) {
                QuadConverter.drawQuadElementsAsTriangles(indices_count, type, indices_buffer_offset);
                return;
            }
            preDraw(mode);
            RENDER_BACKEND.drawElements(mode, indices_count, type, indices_buffer_offset);
        } finally {
            if (savedRecorder != null) DisplayListManager.resumeRecording(savedRecorder);
        }
    }

    public static void glDrawBuffer(int mode) {
        final RecordMode recordMode = DisplayListManager.getRecordMode();
        if (recordMode != RecordMode.NONE) {
            DisplayListManager.recordDrawBuffer(mode);
            if (recordMode == RecordMode.COMPILE) {
                return;
            }
        }
        if (isCachingEnabled()) ctx().drawBuffer.setValue(mode);
        RENDER_BACKEND.drawBuffer(mode);
    }

    public static void glMultiDrawElementsIndirect(int mode, int type, long indirect, int drawcount, int stride) {
        drawCalls += drawcount;
        RENDER_BACKEND.multiDrawElementsIndirect(mode, type, indirect, drawcount, stride);
    }

    public static void glMultiDrawElementsBaseVertex(int mode, long pCount, int type, long pIndices, int drawcount, long pBaseVertex) {
        drawCalls += drawcount;
        RENDER_BACKEND.multiDrawElementsBaseVertex(mode, pCount, type, pIndices, drawcount, pBaseVertex);
    }

    public static void glDrawElementsBaseVertex(int mode, int count, int type, long indices, int basevertex) {
        drawCalls++;
        RENDER_BACKEND.drawElementsBaseVertex(mode, count, type, indices, basevertex);
    }

    public static void glDrawElementsInstanced(int mode, int count, int type, long indices, int primcount) {
        drawCalls++;
        if (mode == GL11.GL_QUADS) {
            QuadConverter.drawQuadElementsAsTrianglesInstanced(count, type, indices, primcount);
            return;
        }
        preDraw(mode);
        RENDER_BACKEND.drawElementsInstanced(mode, count, type, indices, primcount);
    }

    public static void glDrawArraysInstanced(int mode, int first, int count, int primcount) {
        drawCalls++;
        if (mode == GL11.GL_QUADS) {
            QuadConverter.drawQuadsAsTrianglesInstanced(first, count, primcount);
        } else if (mode == GL11.GL_QUAD_STRIP) {
            preDraw();
            RENDER_BACKEND.drawArraysInstanced(GL11.GL_TRIANGLE_STRIP, first, count & ~1, primcount);
        } else if (mode == GL11.GL_POLYGON) {
            preDraw();
            RENDER_BACKEND.drawArraysInstanced(GL11.GL_TRIANGLE_FAN, first, count, primcount);
        } else {
            preDraw(mode);
            RENDER_BACKEND.drawArraysInstanced(mode, first, count, primcount);
        }
    }

    public static void glDrawRangeElements(int mode, int start, int end, int count, int type, long indices) {
        ShaderManager.getInstance().preDraw();
        prepareClientArrays();
        RENDER_BACKEND.drawRangeElements(mode, start, end, count, type, indices);
    }

    public static void glDrawRangeElementsBaseVertex(int mode, int start, int end, int count, int type, long indices, int baseVertex) {
        RENDER_BACKEND.drawRangeElementsBaseVertex(mode, start, end, count, type, indices, baseVertex);
    }

    public static void glMultiDrawArrays(int mode, IntBuffer firsts, IntBuffer counts) {
        ShaderManager.getInstance().preDraw();
        prepareClientArrays();
        RENDER_BACKEND.multiDrawArrays(mode, firsts, counts);
    }

    public static void glPrimitiveRestartIndex(int index) {
        RENDER_BACKEND.primitiveRestartIndex(index);
    }

    public static void glPointParameterf(int pname, float param) {
        RENDER_BACKEND.pointParameterf(pname, param);
    }

    public static void glPointParameteri(int pname, int param) {
        RENDER_BACKEND.pointParameteri(pname, param);
    }

    private static void prepareClientArrays() {
        if (VAOManager.hasAnyClientSideEnabledAttrib()) {
            VAOManager.uploadClientArraysToVBO();
        }
    }

    public static void glDrawArrays(int mode, int first, int count) {
        drawCalls++;
        CommandRecorder savedRecorder = null;
        final RecordMode recordMode = DisplayListManager.getRecordMode();
        if (recordMode != RecordMode.NONE) {
            final DirectTessellator result = ImmediateModeRecorder.processDrawArraysFromAttribs(mode, first, count);
            if (result != null) {
                DisplayListManager.addImmediateModeDraw(result);
            }
            if (recordMode == RecordMode.COMPILE) {
                return;
            }
            savedRecorder = DisplayListManager.pauseRecording();
        } else if (FeedbackManager.isFeedbackMode()) {
            FeedbackManager.processDrawArrays(mode, first, count);
            return;
        }
        if (mode == GL11.GL_QUADS) {
            QuadConverter.drawQuadsAsTriangles(first, count);
        } else if (mode == GL11.GL_QUAD_STRIP) {
            preDraw();
            RENDER_BACKEND.drawArrays(GL11.GL_TRIANGLE_STRIP, first, count & ~1);
        } else if (mode == GL11.GL_POLYGON) {
            preDraw();
            RENDER_BACKEND.drawArrays(GL11.GL_TRIANGLE_FAN, first, count);
        } else {
            preDraw(mode);
            RENDER_BACKEND.drawArrays(mode, first, count);
        }
        if (savedRecorder != null) DisplayListManager.resumeRecording(savedRecorder);
    }

    private static void ffpClientArrayPointer(int index, int size, int type, boolean normalized, int stride, long offset) {
        if (VAOManager.isGenericPointerEnabled(index)) return;
        VAOManager.setAttribute(index, size, type, normalized, stride, offset, ctx().boundVBO);
        VAOManager.markConventional(index);
        RENDER_BACKEND.vertexAttribPointer(index, size, type, normalized, stride, offset);
    }

    private static void ffpClientArrayPointer(int index, int size, int type, boolean normalized, int stride, ByteBuffer pointer) {
        if (VAOManager.isGenericPointerEnabled(index)) return;
        VAOManager.setAttribute(index, size, type, normalized, stride, pointer);
        VAOManager.markConventional(index);
    }

    public static void glVertexPointer(int size, int stride, IntBuffer pointer) {
        glVertexPointer(size, GL11.GL_INT, stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glVertexPointer(int size, int stride, ShortBuffer pointer) {
        glVertexPointer(size, GL11.GL_SHORT, stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glVertexPointer(int size, int stride, FloatBuffer pointer) {
        ffpClientArrayPointer(Usage.POSITION.getAttributeLocation(), size, GL11.GL_FLOAT, false, stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glVertexPointer(int size, int stride, DoubleBuffer pointer) {
        ffpClientArrayPointer(Usage.POSITION.getAttributeLocation(), size, GL11.GL_DOUBLE, false, stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glVertexPointer(int size, int type, int stride, ByteBuffer pointer) {
        ffpClientArrayPointer(Usage.POSITION.getAttributeLocation(), size, type, false, stride, pointer);
    }

    public static void glVertexPointer(int size, int type, int stride, long pointer_buffer_offset) {
        ffpClientArrayPointer(Usage.POSITION.getAttributeLocation(), size, type, false, stride, pointer_buffer_offset);
    }

    public static void glColorPointer(int size, int stride, FloatBuffer pointer) {
        ffpClientArrayPointer(Usage.COLOR.getAttributeLocation(), size, GL11.GL_FLOAT, Usage.COLOR.isNormalized(), stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glColorPointer(int size, int stride, DoubleBuffer pointer) {
        ffpClientArrayPointer(Usage.COLOR.getAttributeLocation(), size, GL11.GL_DOUBLE, Usage.COLOR.isNormalized(), stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glColorPointer(int size, int type, int stride, ByteBuffer pointer) {
        ffpClientArrayPointer(Usage.COLOR.getAttributeLocation(), size, type, Usage.COLOR.isNormalized(), stride, pointer);
    }

    public static void glColorPointer(int size, boolean unsigned, int stride, ByteBuffer pointer) {
        final int type = unsigned ? GL11.GL_UNSIGNED_BYTE : GL11.GL_BYTE;
        ffpClientArrayPointer(Usage.COLOR.getAttributeLocation(), size, type, Usage.COLOR.isNormalized(), stride, pointer);
    }

    public static void glColorPointer(int size, int type, int stride, long pointer_buffer_offset) {
        ffpClientArrayPointer(Usage.COLOR.getAttributeLocation(), size, type, Usage.COLOR.isNormalized(), stride, pointer_buffer_offset);
    }

    public static void glNormalPointer(int stride, FloatBuffer pointer) {
        glNormalPointer(GL11.GL_FLOAT, stride, pointer);
    }

    public static void glNormalPointer(int stride, ByteBuffer pointer) {
        glNormalPointer(GL11.GL_BYTE, stride, pointer);
    }

    public static void glNormalPointer(int stride, IntBuffer pointer) {
        glNormalPointer(GL11.GL_INT, stride, pointer);
    }

    public static void glNormalPointer(int stride, DoubleBuffer pointer) {
        glNormalPointer(GL11.GL_DOUBLE, stride, pointer);
    }

    public static void glNormalPointer(int type, int stride, ByteBuffer pointer) {
        ffpClientArrayPointer(Usage.NORMAL.getAttributeLocation(), 3, type, Usage.NORMAL.isNormalized(), stride, pointer);
    }

    public static void glNormalPointer(int type, int stride, FloatBuffer pointer) {
        ffpClientArrayPointer(Usage.NORMAL.getAttributeLocation(), 3, GL11.GL_FLOAT, Usage.NORMAL.isNormalized(), stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glNormalPointer(int type, int stride, ShortBuffer pointer) {
        ffpClientArrayPointer(Usage.NORMAL.getAttributeLocation(), 3, GL11.GL_SHORT, Usage.NORMAL.isNormalized(), stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glNormalPointer(int type, int stride, IntBuffer pointer) {
        ffpClientArrayPointer(Usage.NORMAL.getAttributeLocation(), 3, GL11.GL_INT, Usage.NORMAL.isNormalized(), stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glNormalPointer(int type, int stride, DoubleBuffer pointer) {
        ffpClientArrayPointer(Usage.NORMAL.getAttributeLocation(), 3, GL11.GL_DOUBLE, Usage.NORMAL.isNormalized(), stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glNormalPointer(int type, int stride, long pointer_buffer_offset) {
        ffpClientArrayPointer(Usage.NORMAL.getAttributeLocation(), 3, type, Usage.NORMAL.isNormalized(), stride, pointer_buffer_offset);
    }

    public static void glTexCoordPointer(int size, int type, int stride, ByteBuffer pointer) {
        final int loc = texCoordAttributeLocation();
        if (loc < 0) return;
        ffpClientArrayPointer(loc, size, type, false, stride, pointer);
    }

    public static void glTexCoordPointer(int size, int stride, FloatBuffer pointer) {
        glTexCoordPointer(size, GL11.GL_FLOAT, stride, pointer);
    }

    public static void glTexCoordPointer(int size, int stride, IntBuffer pointer) {
        glTexCoordPointer(size, GL11.GL_INT, stride, pointer);
    }

    public static void glTexCoordPointer(int size, int stride, ShortBuffer pointer) {
        glTexCoordPointer(size, GL11.GL_SHORT, stride, pointer);
    }

    public static void glTexCoordPointer(int size, int stride, DoubleBuffer pointer) {
        glTexCoordPointer(size, GL11.GL_DOUBLE, stride, pointer);
    }

    public static void glTexCoordPointer(int size, int type, int stride, FloatBuffer pointer) {
        final int loc = texCoordAttributeLocation();
        if (loc < 0) return;
        ffpClientArrayPointer(loc, size, GL11.GL_FLOAT, false, stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glTexCoordPointer(int size, int type, int stride, ShortBuffer pointer) {
        final int loc = texCoordAttributeLocation();
        if (loc < 0) return;
        ffpClientArrayPointer(loc, size, GL11.GL_SHORT, false, stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glTexCoordPointer(int size, int type, int stride, IntBuffer pointer) {
        final int loc = texCoordAttributeLocation();
        if (loc < 0) return;
        ffpClientArrayPointer(loc, size, GL11.GL_INT, false, stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glTexCoordPointer(int size, int type, int stride, DoubleBuffer pointer) {
        final int loc = texCoordAttributeLocation();
        if (loc < 0) return;
        ffpClientArrayPointer(loc, size, GL11.GL_DOUBLE, false, stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glTexCoordPointer(int size, int type, int stride, long pointer_buffer_offset) {
        final int loc = texCoordAttributeLocation();
        if (loc < 0) return;
        ffpClientArrayPointer(loc, size, type, false, stride, pointer_buffer_offset);
    }

    public static void glEnableClientState(int cap) {
        final int location = clientStateToAttributeLocation(cap);
        if (location >= 0) glEnableVertexAttribArray(location);
        final int flag = clientStateToVertexFlag(cap);
        if (flag != 0) VAOManager.enableClientVertexFlag(flag);
    }

    public static void glDisableClientState(int cap) {
        final int location = clientStateToAttributeLocation(cap);
        if (location >= 0) glDisableVertexAttribArray(location);
        final int flag = clientStateToVertexFlag(cap);
        if (flag != 0) VAOManager.disableClientVertexFlag(flag);
    }

    public static void glVertexAttrib2f(int index, float v0, float v1) {
        RENDER_BACKEND.vertexAttrib2f(index, v0, v1);
    }

    public static void glVertexAttrib2s(int index, short v0, short v1) {
        RENDER_BACKEND.vertexAttrib2f(index, v0, v1);
    }

    public static void glVertexAttrib3f(int index, float v0, float v1, float v2) {
        RENDER_BACKEND.vertexAttrib3f(index, v0, v1, v2);
    }

    public static void glVertexAttrib4f(int index, float v0, float v1, float v2, float v3) {
        RENDER_BACKEND.vertexAttrib4f(index, v0, v1, v2, v3);
    }

    public static void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long offset) {
        VAOManager.setAttribute(index, size, type, normalized, stride, offset, ctx().boundVBO);
        RENDER_BACKEND.vertexAttribPointer(index, size, type, normalized, stride, offset);
    }

    public static void glVertexAttribPointer(int index, int size, boolean normalized, int stride, FloatBuffer pointer) {
        glVertexAttribPointer(index, size, GL11.GL_FLOAT, normalized, stride, pointer);
    }

    public static void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, FloatBuffer pointer) {
        VAOManager.setAttribute(index, size, type, normalized, stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, DoubleBuffer pointer) {
        VAOManager.setAttribute(index, size, type, normalized, stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, IntBuffer pointer) {
        VAOManager.setAttribute(index, size, type, normalized, stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, ShortBuffer pointer) {
        VAOManager.setAttribute(index, size, type, normalized, stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, ByteBuffer pointer) {
        VAOManager.setAttribute(index, size, type, normalized, stride, pointer);
    }

    public static void glVertexAttribIPointer(int index, int size, int type, int stride, long offset) {
        VAOManager.setAttribute(index, size, type, false, stride, offset, ctx().boundVBO);
        RENDER_BACKEND.vertexAttribIPointer(index, size, type, stride, offset);
    }

    public static void glVertexAttribIPointer(int index, int size, int type, int stride, ByteBuffer pointer) {
        VAOManager.setAttribute(index, size, type, false, stride, pointer);
    }

    public static void glVertexAttribIPointer(int index, int size, int type, int stride, IntBuffer pointer) {
        VAOManager.setAttribute(index, size, type, false, stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glVertexAttribIPointer(int index, int size, int type, int stride, ShortBuffer pointer) {
        VAOManager.setAttribute(index, size, type, false, stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glVertexAttribDivisor(int index, int divisor) { RENDER_BACKEND.vertexAttribDivisor(index, divisor); }
    public static void glVertexAttribDivisorARB(int index, int divisor) { RENDER_BACKEND.vertexAttribDivisor(index, divisor); }
    public static void glBindVertexBuffer(int bindingindex, int buffer, long offset, int stride) { RENDER_BACKEND.bindVertexBuffer(bindingindex, buffer, offset, stride); }
    public static void glVertexAttribFormat(int attribindex, int size, int type, boolean normalized, int relativeoffset) { RENDER_BACKEND.vertexAttribFormat(attribindex, size, type, normalized, relativeoffset); }
    public static void glVertexAttribIFormat(int attribindex, int size, int type, int relativeoffset) { RENDER_BACKEND.vertexAttribIFormat(attribindex, size, type, relativeoffset); }
    public static void glVertexAttribBinding(int attribindex, int bindingindex) { RENDER_BACKEND.vertexAttribBinding(attribindex, bindingindex); }

    public static void glEnableVertexAttribArray(int index) {
        VAOManager.enableAttribute(index);
        RENDER_BACKEND.enableVertexAttribArray(index);
    }

    public static void glDisableVertexAttribArray(int index) {
        VAOManager.disableAttribute(index);
        RENDER_BACKEND.disableVertexAttribArray(index);
    }

    private static int clientStateToAttributeLocation(int cap) {
        return switch (cap) {
            case GL11.GL_VERTEX_ARRAY -> Usage.POSITION.getAttributeLocation();
            case GL11.GL_COLOR_ARRAY -> Usage.COLOR.getAttributeLocation();
            case GL11.GL_NORMAL_ARRAY -> Usage.NORMAL.getAttributeLocation();
            case GL11.GL_TEXTURE_COORD_ARRAY -> texCoordAttributeLocation();
            default -> -1;
        };
    }

    private static int clientStateToVertexFlag(int cap) {
        return switch (cap) {
            case GL11.GL_COLOR_ARRAY -> VertexFlags.COLOR_BIT;
            case GL11.GL_NORMAL_ARRAY -> VertexFlags.NORMAL_BIT;
            case GL11.GL_TEXTURE_COORD_ARRAY -> switch (ctx().clientActiveTextureUnit) {
                case 0 -> VertexFlags.TEXTURE_BIT;
                case 1 -> VertexFlags.BRIGHTNESS_BIT;
                default -> 0;
            };
            default -> 0; // GL_VERTEX_ARRAY - position is implicit
        };
    }

    static final int CLIENT_ATTRIB_STACK_DEPTH = 16;
    public static void glPushClientAttrib(int mask) {
        final GLContextState glCtx = ctx();
        if (glCtx.clientAttribStackPointer < CLIENT_ATTRIB_STACK_DEPTH) {
            glCtx.clientAttribSavedTextureUnit[glCtx.clientAttribStackPointer] = glCtx.clientActiveTextureUnit;
            glCtx.clientAttribSavedVertexFlags[glCtx.clientAttribStackPointer] = VAOManager.getCurrentVertexFlags();
            glCtx.clientAttribStackPointer++;
        }
    }

    public static void glPopClientAttrib() {
        final GLContextState glCtx = ctx();
        if (glCtx.clientAttribStackPointer > 0) {
            glCtx.clientAttribStackPointer--;
            glCtx.clientActiveTextureUnit = glCtx.clientAttribSavedTextureUnit[glCtx.clientAttribStackPointer];
            VAOManager.setCurrentVertexFlags(glCtx.clientAttribSavedVertexFlags[glCtx.clientAttribStackPointer]);
        }
    }

    public static void glInterleavedArrays(int format, int stride, long pointer) {
        if (!ShaderManager.getInstance().isActive()) {
            return;
        }

        // Mesa _mesa_get_interleaved_layout decomposition
        final int f = 4; // sizeof(float)
        final int c = f * ((4 + (f - 1)) / f); // 4 ubytes padded to float alignment = 4

        boolean tflag = false, cflag = false, nflag = false;
        int tcomps = 0, ccomps = 0, vcomps = 0;
        int ctype = 0;
        final int toffset = 0;
        int coffset = 0;
        int noffset = 0;
        int voffset = 0;
        final int defstride;

        switch (format) {
            case GL11.GL_V2F:
                vcomps = 2; defstride = 2 * f; break;
            case GL11.GL_V3F:
                vcomps = 3; defstride = 3 * f; break;
            case GL11.GL_C4UB_V2F:
                cflag = true; ccomps = 4; vcomps = 2;
                ctype = GL11.GL_UNSIGNED_BYTE; voffset = c; defstride = c + 2 * f; break;
            case GL11.GL_C4UB_V3F:
                cflag = true; ccomps = 4; vcomps = 3;
                ctype = GL11.GL_UNSIGNED_BYTE; voffset = c; defstride = c + 3 * f; break;
            case GL11.GL_C3F_V3F:
                cflag = true; ccomps = 3; vcomps = 3;
                ctype = GL11.GL_FLOAT; voffset = 3 * f; defstride = 6 * f; break;
            case GL11.GL_N3F_V3F:
                nflag = true; vcomps = 3;
                voffset = 3 * f; defstride = 6 * f; break;
            case GL11.GL_C4F_N3F_V3F:
                cflag = true; nflag = true; ccomps = 4; vcomps = 3;
                ctype = GL11.GL_FLOAT; noffset = 4 * f; voffset = 7 * f; defstride = 10 * f; break;
            case GL11.GL_T2F_V3F:
                tflag = true; tcomps = 2; vcomps = 3;
                voffset = 2 * f; defstride = 5 * f; break;
            case GL11.GL_T4F_V4F:
                tflag = true; tcomps = 4; vcomps = 4;
                voffset = 4 * f; defstride = 8 * f; break;
            case GL11.GL_T2F_C4UB_V3F:
                tflag = true; cflag = true; tcomps = 2; ccomps = 4; vcomps = 3;
                ctype = GL11.GL_UNSIGNED_BYTE; coffset = 2 * f; voffset = c + 2 * f; defstride = c + 5 * f; break;
            case GL11.GL_T2F_C3F_V3F:
                tflag = true; cflag = true; tcomps = 2; ccomps = 3; vcomps = 3;
                ctype = GL11.GL_FLOAT; coffset = 2 * f; voffset = 5 * f; defstride = 8 * f; break;
            case GL11.GL_T2F_N3F_V3F:
                tflag = true; nflag = true; tcomps = 2; vcomps = 3;
                noffset = 2 * f; voffset = 5 * f; defstride = 8 * f; break;
            case GL11.GL_T2F_C4F_N3F_V3F:
                tflag = true; cflag = true; nflag = true; tcomps = 2; ccomps = 4; vcomps = 3;
                ctype = GL11.GL_FLOAT; coffset = 2 * f; noffset = 6 * f; voffset = 9 * f; defstride = 12 * f; break;
            case GL11.GL_T4F_C4F_N3F_V4F:
                tflag = true; cflag = true; nflag = true; tcomps = 4; ccomps = 4; vcomps = 4;
                ctype = GL11.GL_FLOAT; coffset = 4 * f; noffset = 8 * f; voffset = 11 * f; defstride = 15 * f; break;
            default:
                return; // Invalid format
        }

        if (stride == 0) stride = defstride;

        if (tflag) {
            glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            glTexCoordPointer(tcomps, GL11.GL_FLOAT, stride, pointer + toffset);
        } else {
            glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        }

        if (cflag) {
            glEnableClientState(GL11.GL_COLOR_ARRAY);
            glColorPointer(ccomps, ctype, stride, pointer + coffset);
        } else {
            glDisableClientState(GL11.GL_COLOR_ARRAY);
        }

        if (nflag) {
            glEnableClientState(GL11.GL_NORMAL_ARRAY);
            glNormalPointer(GL11.GL_FLOAT, stride, pointer + noffset);
        } else {
            glDisableClientState(GL11.GL_NORMAL_ARRAY);
        }

        glEnableClientState(GL11.GL_VERTEX_ARRAY);
        glVertexPointer(vcomps, GL11.GL_FLOAT, stride, pointer + voffset);
    }

    public static void glInterleavedArrays(int format, int stride, ByteBuffer pointer) {
        glInterleavedArrays(format, stride, MemoryUtilities.memAddress0(pointer));
    }

    public static void glInterleavedArrays(int format, int stride, FloatBuffer pointer) {
        glInterleavedArrays(format, stride, MemoryUtilities.memAddress0(MemoryUtilities.memByteBuffer(pointer)));
    }

    public static void glInterleavedArrays(int format, int stride, DoubleBuffer pointer) {
        glInterleavedArrays(format, stride, MemoryUtilities.memAddress0(MemoryUtilities.memByteBuffer(pointer)));
    }

    public static void glInterleavedArrays(int format, int stride, IntBuffer pointer) {
        glInterleavedArrays(format, stride, MemoryUtilities.memAddress0(MemoryUtilities.memByteBuffer(pointer)));
    }

    public static void glInterleavedArrays(int format, int stride, ShortBuffer pointer) {
        glInterleavedArrays(format, stride, MemoryUtilities.memAddress0(MemoryUtilities.memByteBuffer(pointer)));
    }

    public static void glLogicOp(int opcode) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordLogicOp(opcode);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        if (isCachingEnabled()) ctx().logicOpMode.setValue(opcode);
        RENDER_BACKEND.logicOp(opcode);
    }

    public static void defaultBlendFunc() {
        tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
    }

    public static void enableCull() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordEnable(GL11.GL_CULL_FACE);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        ctx().cullState.enable();
    }

    public static void disableCull() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDisable(GL11.GL_CULL_FACE);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        ctx().cullState.disable();
    }

    public static void enableDepthTest() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordEnable(GL11.GL_DEPTH_TEST);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        ctx().depthTest.enable();
    }

    public static void disableDepthTest() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDisable(GL11.GL_DEPTH_TEST);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        ctx().depthTest.disable();
    }

    public static void enableLighting() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordEnable(GL11.GL_LIGHTING);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        ctx().lightingState.enable();
    }

    public static void enableLight(int light) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordEnable(GL11.GL_LIGHT0 + light);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        ctx().lightStates[light].enable();
    }

    public static void enableColorMaterial() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordEnable(GL11.GL_COLOR_MATERIAL);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        ctx().colorMaterial.enable();
        bakeCurrentColorIntoTrackedMaterial();
    }

    public static void disableColorMaterial() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDisable(GL11.GL_COLOR_MATERIAL);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        bakeCurrentColorIntoTrackedMaterial();
        ctx().colorMaterial.disable();
    }

    private static void bakeCurrentColorIntoTrackedMaterial() {
        final GLContextState glCtx = ctx();
        final float r = getColor().getRed();
        final float g = getColor().getGreen();
        final float b = getColor().getBlue();
        final float a = getColor().getAlpha();
        if (glCtx.colorMaterialFace.getValue() == GL11.GL_FRONT || glCtx.colorMaterialFace.getValue() == GL11.GL_FRONT_AND_BACK) {
            switch (glCtx.colorMaterialParameter.getValue()) {
                case GL11.GL_AMBIENT_AND_DIFFUSE -> {
                    glCtx.frontMaterial.ambient.set(r, g, b, a);
                    glCtx.frontMaterial.diffuse.set(r, g, b, a);
                }
                case GL11.GL_AMBIENT -> glCtx.frontMaterial.ambient.set(r, g, b, a);
                case GL11.GL_DIFFUSE -> glCtx.frontMaterial.diffuse.set(r, g, b, a);
                case GL11.GL_SPECULAR -> glCtx.frontMaterial.specular.set(r, g, b, a);
                case GL11.GL_EMISSION -> glCtx.frontMaterial.emission.set(r, g, b, a);
            }
        }
        if (glCtx.colorMaterialFace.getValue() == GL11.GL_BACK || glCtx.colorMaterialFace.getValue() == GL11.GL_FRONT_AND_BACK) {
            switch (glCtx.colorMaterialParameter.getValue()) {
                case GL11.GL_AMBIENT_AND_DIFFUSE -> {
                    glCtx.backMaterial.ambient.set(r, g, b, a);
                    glCtx.backMaterial.diffuse.set(r, g, b, a);
                }
                case GL11.GL_AMBIENT -> glCtx.backMaterial.ambient.set(r, g, b, a);
                case GL11.GL_DIFFUSE -> glCtx.backMaterial.diffuse.set(r, g, b, a);
                case GL11.GL_SPECULAR -> glCtx.backMaterial.specular.set(r, g, b, a);
                case GL11.GL_EMISSION -> glCtx.backMaterial.emission.set(r, g, b, a);
            }
        }
    }

    public static void disableLighting() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDisable(GL11.GL_LIGHTING);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        ctx().lightingState.disable();
    }

    public static void disableLight(int light) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDisable(GL11.GL_LIGHT0 + light);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        ctx().lightStates[light].disable();
    }

    public static void enableRescaleNormal() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordEnable(GL12.GL_RESCALE_NORMAL);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        ctx().rescaleNormalState.enable();
    }

    public static void disableRescaleNormal() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDisable(GL12.GL_RESCALE_NORMAL);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        ctx().rescaleNormalState.disable();
    }

    public static void enableFog() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordEnable(GL11.GL_FOG);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        ctx().fogMode.enable();
        if (GLSMHooks.FOG_STATE_CHANGE.hasListeners()) {
            GLSMHooks.FOG_STATE_CHANGE.post(GLSMHooks.fogStateChangeEvent);
        }
    }

    public static void disableFog() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDisable(GL11.GL_FOG);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        ctx().fogMode.disable();
        if (GLSMHooks.FOG_STATE_CHANGE.hasListeners()) {
            GLSMHooks.FOG_STATE_CHANGE.post(GLSMHooks.fogStateChangeEvent);
        }
    }

    public static void glFog(int pname, FloatBuffer param) {
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordFog(pname, param);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        if (HAS_MULTIPLE_SET.contains(pname)) {
            if (pname == GL11.GL_FOG_COLOR && isCachingEnabled()) {
                final float red = param.get(0);
                final float green = param.get(1);
                final float blue = param.get(2);

                glCtx.fogState.getFogColor().set(red, green, blue);
                glCtx.fogState.setFogAlpha(param.get(3));
                glCtx.fogState.getFogColorBuffer().clear();
                glCtx.fogState.getFogColorBuffer().put((FloatBuffer) param.position(0)).flip();
                glCtx.fragmentGeneration++;
                if (GLSMHooks.FOG_STATE_CHANGE.hasListeners()) {
                    GLSMHooks.FOG_STATE_CHANGE.post(GLSMHooks.fogStateChangeEvent);
                }
            }
        } else {
            GLStateManager.glFogf(pname, param.get(0));
        }
    }

    public static Vector3d getFogColor() {
        return ctx().fogState.getFogColor();
    }

    public static void fogColor(float red, float green, float blue, float alpha) {
        final GLContextState glCtx = ctx();
        final boolean caching = isCachingEnabled();
        if (!caching || red != glCtx.fogState.getFogColor().x || green != glCtx.fogState.getFogColor().y || blue != glCtx.fogState.getFogColor().z || alpha != glCtx.fogState.getFogAlpha()) {
            glCtx.fogState.getFogColor().set(red, green, blue);
            glCtx.fogState.setFogAlpha(alpha);
            glCtx.fogState.getFogColorBuffer().clear();
            glCtx.fogState.getFogColorBuffer().put(red).put(green).put(blue).put(alpha).flip();
            glCtx.fragmentGeneration++;
            if (GLSMHooks.FOG_STATE_CHANGE.hasListeners()) {
                GLSMHooks.FOG_STATE_CHANGE.post(GLSMHooks.fogStateChangeEvent);
            }
        }
    }

    public static void glFogf(int pname, float param) {
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordFogf(pname, param);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        // Update cached state (FFP shader reads from cache)
        if (isCachingEnabled()) {
            boolean changed = false;
            switch (pname) {
                case GL11.GL_FOG_DENSITY -> { if (!isCachingEnabled() || glCtx.fogState.getDensity() != param) { glCtx.fogState.setDensity(param); changed = true; } }
                case GL11.GL_FOG_START -> { if (!isCachingEnabled() || glCtx.fogState.getStart() != param) { glCtx.fogState.setStart(param); changed = true; } }
                case GL11.GL_FOG_END -> { if (!isCachingEnabled() || glCtx.fogState.getEnd() != param) { glCtx.fogState.setEnd(param); changed = true; } }
                case GL11.GL_FOG_MODE -> { if (!isCachingEnabled() || glCtx.fogState.getFogMode() != (int) param) { glCtx.fogState.setFogMode((int) param); changed = true; } }
                default -> {}
            }
            if (changed) {
                glCtx.fragmentGeneration++;
                if (GLSMHooks.FOG_STATE_CHANGE.hasListeners()) {
                    GLSMHooks.FOG_STATE_CHANGE.post(GLSMHooks.fogStateChangeEvent);
                }
            }
        }
    }

    public static void glFogi(int pname, int param) {
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordFogi(pname, param);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        // Update cached state (FFP shader reads from cache)
        if (isCachingEnabled() && pname == GL11.GL_FOG_MODE && (!isCachingEnabled() || glCtx.fogState.getFogMode() != param)) {
            glCtx.fogState.setFogMode(param);
            glCtx.fragmentGeneration++;
            if (GLSMHooks.FOG_STATE_CHANGE.hasListeners()) {
                GLSMHooks.FOG_STATE_CHANGE.post(GLSMHooks.fogStateChangeEvent);
            }
        }
    }

    public static void setFogBlack() {
        glFogf(GL11.GL_FOG_COLOR, 0.0F);
    }

    public static void glShadeModel(int mode) {
        final GLContextState glCtx = ctx();
        final RecordMode recordMode = DisplayListManager.getRecordMode();
        if (recordMode != RecordMode.NONE) {
            DisplayListManager.recordShadeModel(mode);
            if (recordMode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();
        final int oldValue = glCtx.shadeModelState.getValue();
        final boolean needsUpdate = !caching || oldValue != mode;

        if (needsUpdate) {
            glCtx.shadeModelState.setValue(mode);
        }
    }

    // Iris Functions
    private static void onDeleteTexture(int id) {
        final GLContextState glCtx = ctx();
        TextureInfoCache.INSTANCE.onDeleteTexture(id);
        if (GLSMHooks.TEXTURE_DELETE.hasListeners()) {
            GLSMHooks.textureDeleteEvent.textureId = id;
            GLSMHooks.TEXTURE_DELETE.post(GLSMHooks.textureDeleteEvent);
        }

        // Always delete
        for (int i = 0; i <= glCtx.maxBoundTextureUnit; i++) {
            if (glCtx.textures.getTextureUnitBindings(i).getBinding() == id) {
                glCtx.textures.getTextureUnitBindings(i).setBinding(0);
            }
        }

        for (int i = 0; i <= glCtx.maxBoundImageUnit; i++) {
            final ImageUnitBinding image = glCtx.imageUnits.get(i);
            if (image != null && image.getTexture() == id) {
                image.setBinding(0, 0, false, 0, 0, 0);
            }
        }
    }

    private static final ThreadLocal<Boolean> autoVaoBound = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public static void makeCurrent(Drawable drawable) throws LWJGLException {
        final boolean handled = BackendManager.RENDER_BACKEND != null && BackendManager.RENDER_BACKEND.handleMakeCurrent(drawable);
        if (!handled) {
            drawable.makeCurrent();
        }
        CurrentThread = Thread.currentThread();

        if (!splashComplete && CurrentThread != MainThread && !autoVaoBound.get()) {
            autoVaoBound.set(Boolean.TRUE);
            glBindVertexArray(glGenVertexArrays());
        }

        if (splashComplete) return;

        if (drawableGL == null) {
            // Lazy-capture DrawableGL reference (Display.getDrawable()) on first opportunity
            try {
                if (drawable == Display.getDrawable()) {
                    drawableGL = drawable;
                }
            } catch (Exception e) {
                // Display not ready - can't identify DrawableGL yet, will retry next call
                LOGGER.warn("Display not ready in makeCurrent", e);
                return;
            }
        }

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
        if (drawableGLHolder == Thread.currentThread()) {
            drawableGLHolder = null;
        }
        if (!splashComplete && MainThread != null && Thread.currentThread() != MainThread) {
            markSplashComplete("releaseContext");
        }
    }

    public static boolean isCurrent() {
        if (BackendManager.RENDER_BACKEND == null) {
            return Thread.currentThread() == MainThread;
        }
        return BackendManager.RENDER_BACKEND.isCurrent();
    }

    public static void swapBuffers() throws LWJGLException {
        if (BackendManager.RENDER_BACKEND != null && BackendManager.RENDER_BACKEND.handleSwapBuffers()) {
            return;
        }
        Display.swapBuffers();
    }

    public static void updateDisplay() throws LWJGLException {
        updateDisplay(true);
    }

    public static void updateDisplay(boolean processMessages) throws LWJGLException {
        if (Thread.currentThread() != MainThread) {
            swapBuffers();
            if (processMessages) Display.processMessages();
            return;
        }
        Display.update(processMessages);
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

    public static void glNewList(int list, int mode) {
        DisplayListManager.glNewList(list, mode);
    }

    public static void glEndList() {
        DisplayListManager.glEndList();
    }

    /**
     * Check if we're currently recording a display list.
     */
    public static boolean isRecordingDisplayList() {
        return DisplayListManager.isRecording();
    }

    public static int getRecordingDisplayListId() {
        return DisplayListManager.getRecordingListId();
    }

    private static final DisplayListIDAllocator displayListIdAllocator = new DisplayListIDAllocator();

    public static int glGenLists(int range) {
        return displayListIdAllocator.allocRange(range);
    }

    public static boolean glIsList(int list) {
        return displayListIdAllocator.isAllocated(list);
    }

    /**
     * Delete display lists and free their VBO resources.
     */
    public static void glDeleteLists(int list, int range) {
        displayListIdAllocator.freeRange(list, range);
        DisplayListManager.glDeleteLists(list, range);
    }

    /**
     * Get a compiled display list from the cache. Used for executing nested display lists.
     *
     * @param list The display list ID
     * @return The CompiledDisplayList, or null if not found
     */
    public static CompiledDisplayList getDisplayList(int list) {
        return DisplayListManager.getDisplayList(list);
    }

    public static void glCallList(int list) {
        if (!GLDebug.isActive()) {
            DisplayListManager.glCallList(list);
            return;
        }
        GLDebug.pushGroup("glCallList ", list);
        try {
            DisplayListManager.glCallList(list);
        } finally {
            GLDebug.popGroup();
        }
    }

    public static void pushState(int mask) {
        final GLContextState glCtx = ctx();
        if (glCtx.attribDepth >= MAX_ATTRIB_STACK_DEPTH) {
            throw new IllegalStateException("Attrib stack overflow: max depth " + MAX_ATTRIB_STACK_DEPTH + " reached");
        }
        glCtx.attribs.push(mask);

        // Snapshot generation counters so we can detect actual changes at pop time
        glCtx.savedMvGen[glCtx.attribDepth] = glCtx.mvGeneration;
        glCtx.savedMvLinearGen[glCtx.attribDepth] = glCtx.mvLinearGeneration;
        glCtx.savedProjGen[glCtx.attribDepth] = glCtx.projGeneration;
        glCtx.savedTexMatGen[glCtx.attribDepth] = glCtx.texMatrixGeneration;
        glCtx.savedLightingGen[glCtx.attribDepth] = glCtx.lightingGeneration;
        glCtx.savedFragmentGen[glCtx.attribDepth] = glCtx.fragmentGeneration;
        glCtx.savedColorGen[glCtx.attribDepth] = glCtx.colorGeneration;
        glCtx.savedNormalGen[glCtx.attribDepth] = ShaderManager.getNormalGeneration();
        glCtx.savedTexCoordGen[glCtx.attribDepth] = ShaderManager.getTexCoordGeneration();

        // Clear modified list for this depth level
        glCtx.modifiedAtDepth[glCtx.attribDepth].clear();
        glCtx.attribDepth++;

        // Only iterate non-boolean stacks - BooleanStateStack uses global depth tracking
        // so pushDepth() is a no-op for them
        final IStateStack<?>[] nonBooleanStacks = Feature.maskToNonBooleanStacks(mask);
        for (IStateStack<?> stack : nonBooleanStacks) {
            stack.pushDepth();
        }
    }

    public static void popState() {
        final GLContextState glCtx = ctx();
        final int mask = glCtx.attribs.popInt();
        glCtx.attribDepth--;

        // First: restore BooleanStateStack states that were actually modified (fast path)
        // These use lazy copy-on-write with global depth tracking
        final List<IStateStack<?>> modified = glCtx.modifiedAtDepth[glCtx.attribDepth];
        for (int i = 0; i < modified.size(); i++) {
            modified.get(i).popDepth();
        }
        modified.clear();

        captureRestoreChanges(mask);

        // Second: restore non-boolean state stacks the traditional way
        final IStateStack<?>[] nonBooleanStacks = Feature.maskToNonBooleanStacks(mask);
        for (IStateStack<?> stack : nonBooleanStacks) {
            stack.popDepth();
        }

        // Third: apply restored state to the GL driver. BooleanStateStacks already issue GL calls via setEnabled(); non-boolean stacks are pure data
        // containers, so we must explicitly drive GL here.
        applyRestoredState(mask);
    }

    private static void captureRestoreChanges(int mask) {
        final GLContextState glCtx = ctx();
        if ((mask & GL11.GL_DEPTH_BUFFER_BIT) != 0) {
            glCtx.restoreDepthChanged = glCtx.depthState.topChanged();
        }
        if ((mask & GL11.GL_COLOR_BUFFER_BIT) != 0) {
            glCtx.restoreBlendChanged = glCtx.blendState.topChanged();
            glCtx.restoreColorMaskChanged = glCtx.colorMask.topChanged();
            glCtx.restoreClearColorChanged = glCtx.clearColor.topChanged();
            glCtx.restoreDrawBufferChanged = glCtx.drawBuffer.topChanged();
            glCtx.restoreLogicOpChanged = glCtx.logicOpMode.topChanged();
        }
        if ((mask & GL11.GL_STENCIL_BUFFER_BIT) != 0) {
            glCtx.restoreStencilChanged = glCtx.stencilState.topChanged();
        }
        if ((mask & GL11.GL_VIEWPORT_BIT) != 0) {
            glCtx.restoreViewportChanged = glCtx.viewportState.topChanged();
        }
        if ((mask & GL11.GL_LINE_BIT) != 0) {
            glCtx.restoreLineChanged = glCtx.lineState.topChanged();
        }
        if ((mask & GL11.GL_POINT_BIT) != 0) {
            glCtx.restorePointChanged = glCtx.pointState.topChanged();
        }
        if ((mask & GL11.GL_POLYGON_BIT) != 0) {
            glCtx.restorePolygonChanged = glCtx.polygonState.topChanged();
        }
        if ((mask & GL11.GL_TEXTURE_BIT) != 0) {
            for (int i = 0; i < MAX_TEXTURE_UNITS; i++) {
                glCtx.restoreUnitChanged[i] = glCtx.textures.getTextureUnitBindings(i).topChanged();
            }
            glCtx.restoreActiveUnitChanged = glCtx.activeTextureUnit.topChanged();
        }
    }

    public static void replayStateToBackend() {
        final GLContextState glCtx = ctx();
        if (RENDER_BACKEND == null) return;

        final List<BooleanStateStack> booleanStates = glCtx.allBooleanStates;
        for (int i = 0; i < booleanStates.size(); i++) {
            booleanStates.get(i).applyToBackend();
        }

        RENDER_BACKEND.depthFunc(glCtx.depthState.getFunc());
        RENDER_BACKEND.depthMask(glCtx.depthState.isEnabled());
        RENDER_BACKEND.clearDepth(glCtx.depthState.getClearValue());

        RENDER_BACKEND.blendFuncSeparate(glCtx.blendState.getSrcRgb(), glCtx.blendState.getDstRgb(), glCtx.blendState.getSrcAlpha(), glCtx.blendState.getDstAlpha());
        RENDER_BACKEND.blendEquationSeparate(glCtx.blendState.getEquationRgb(), glCtx.blendState.getEquationAlpha());
        RENDER_BACKEND.blendColor(glCtx.blendState.getBlendColorR(), glCtx.blendState.getBlendColorG(), glCtx.blendState.getBlendColorB(), glCtx.blendState.getBlendColorA());
        RENDER_BACKEND.colorMask(glCtx.colorMask.red, glCtx.colorMask.green, glCtx.colorMask.blue, glCtx.colorMask.alpha);
        RENDER_BACKEND.clearColor(glCtx.clearColor.getRed(), glCtx.clearColor.getGreen(), glCtx.clearColor.getBlue(), glCtx.clearColor.getAlpha());
        RENDER_BACKEND.logicOp(glCtx.logicOpMode.getValue());

        RENDER_BACKEND.stencilFuncSeparate(GL11.GL_FRONT, glCtx.stencilState.getFuncFront(), glCtx.stencilState.getRefFront(), glCtx.stencilState.getValueMaskFront());
        RENDER_BACKEND.stencilFuncSeparate(GL11.GL_BACK, glCtx.stencilState.getFuncBack(), glCtx.stencilState.getRefBack(), glCtx.stencilState.getValueMaskBack());
        RENDER_BACKEND.stencilOpSeparate(GL11.GL_FRONT, glCtx.stencilState.getFailOpFront(), glCtx.stencilState.getZFailOpFront(), glCtx.stencilState.getZPassOpFront());
        RENDER_BACKEND.stencilOpSeparate(GL11.GL_BACK, glCtx.stencilState.getFailOpBack(), glCtx.stencilState.getZFailOpBack(), glCtx.stencilState.getZPassOpBack());
        RENDER_BACKEND.stencilMaskSeparate(GL11.GL_FRONT, glCtx.stencilState.getWriteMaskFront());
        RENDER_BACKEND.stencilMaskSeparate(GL11.GL_BACK, glCtx.stencilState.getWriteMaskBack());
        RENDER_BACKEND.clearStencil(glCtx.stencilState.getClearValue());

        RENDER_BACKEND.viewport(glCtx.viewportState.x, glCtx.viewportState.y, glCtx.viewportState.width, glCtx.viewportState.height);
        RENDER_BACKEND.depthRange(glCtx.viewportState.depthRangeNear, glCtx.viewportState.depthRangeFar);
        RENDER_BACKEND.lineWidth(Math.clamp(glCtx.lineState.getWidth(), lineWidthMin, lineWidthMax));
        RENDER_BACKEND.pointSize(glCtx.pointState.getSize());

        RENDER_BACKEND.polygonMode(GL11.GL_FRONT_AND_BACK, glCtx.polygonState.getFrontMode());
        applyPolygonOffset(glCtx);
        RENDER_BACKEND.cullFace(glCtx.polygonState.getCullFaceMode());
        RENDER_BACKEND.frontFace(glCtx.polygonState.getFrontFace());
    }

    private static void applyPolygonOffset(GLContextState glCtx) {
        final float clamp = glCtx.polygonState.getOffsetClamp();
        if (clamp == 0.0f) {
            RENDER_BACKEND.polygonOffset(glCtx.polygonState.getOffsetFactor(), glCtx.polygonState.getOffsetUnits());
        } else {
            RENDER_BACKEND.polygonOffsetClamp(glCtx.polygonState.getOffsetFactor(), glCtx.polygonState.getOffsetUnits(), clamp);
        }
    }

    /**
     * After popping GLSM stacks, apply restored state to the GL driver.
     */
    private static void applyRestoredState(int mask) {
        final GLContextState glCtx = ctx();
        if ((mask & GL11.GL_DEPTH_BUFFER_BIT) != 0 && glCtx.restoreDepthChanged) {
            RENDER_BACKEND.depthFunc(glCtx.depthState.getFunc());
            RENDER_BACKEND.depthMask(glCtx.depthState.isEnabled());
            RENDER_BACKEND.clearDepth(glCtx.depthState.getClearValue());
        }
        if ((mask & GL11.GL_COLOR_BUFFER_BIT) != 0) {
            if (glCtx.restoreBlendChanged) {
                RENDER_BACKEND.blendFuncSeparate(glCtx.blendState.getSrcRgb(), glCtx.blendState.getDstRgb(), glCtx.blendState.getSrcAlpha(), glCtx.blendState.getDstAlpha());
                RENDER_BACKEND.blendEquationSeparate(glCtx.blendState.getEquationRgb(), glCtx.blendState.getEquationAlpha());
                RENDER_BACKEND.blendColor(glCtx.blendState.getBlendColorR(), glCtx.blendState.getBlendColorG(), glCtx.blendState.getBlendColorB(), glCtx.blendState.getBlendColorA());
            }
            if (glCtx.restoreColorMaskChanged) {
                RENDER_BACKEND.colorMask(glCtx.colorMask.red, glCtx.colorMask.green, glCtx.colorMask.blue, glCtx.colorMask.alpha);
            }
            if (glCtx.restoreClearColorChanged) {
                RENDER_BACKEND.clearColor(glCtx.clearColor.getRed(), glCtx.clearColor.getGreen(), glCtx.clearColor.getBlue(), glCtx.clearColor.getAlpha());
            }
            // Draw buffer is per-framebuffer state; only restore on the default framebuffer
            if (glCtx.restoreDrawBufferChanged && glCtx.drawFramebuffer == 0) {
                RENDER_BACKEND.drawBuffer(glCtx.drawBuffer.getValue());
            }
            if (glCtx.restoreLogicOpChanged) {
                RENDER_BACKEND.logicOp(glCtx.logicOpMode.getValue());
            }
        }
        if ((mask & GL11.GL_STENCIL_BUFFER_BIT) != 0 && glCtx.restoreStencilChanged) {
            RENDER_BACKEND.stencilFuncSeparate(GL11.GL_FRONT, glCtx.stencilState.getFuncFront(), glCtx.stencilState.getRefFront(), glCtx.stencilState.getValueMaskFront());
            RENDER_BACKEND.stencilFuncSeparate(GL11.GL_BACK, glCtx.stencilState.getFuncBack(), glCtx.stencilState.getRefBack(), glCtx.stencilState.getValueMaskBack());
            RENDER_BACKEND.stencilOpSeparate(GL11.GL_FRONT, glCtx.stencilState.getFailOpFront(), glCtx.stencilState.getZFailOpFront(), glCtx.stencilState.getZPassOpFront());
            RENDER_BACKEND.stencilOpSeparate(GL11.GL_BACK, glCtx.stencilState.getFailOpBack(), glCtx.stencilState.getZFailOpBack(), glCtx.stencilState.getZPassOpBack());
            RENDER_BACKEND.stencilMaskSeparate(GL11.GL_FRONT, glCtx.stencilState.getWriteMaskFront());
            RENDER_BACKEND.stencilMaskSeparate(GL11.GL_BACK, glCtx.stencilState.getWriteMaskBack());
            RENDER_BACKEND.clearStencil(glCtx.stencilState.getClearValue());
        }
        if ((mask & GL11.GL_VIEWPORT_BIT) != 0 && glCtx.restoreViewportChanged) {
            RENDER_BACKEND.viewport(glCtx.viewportState.x, glCtx.viewportState.y, glCtx.viewportState.width, glCtx.viewportState.height);
            RENDER_BACKEND.depthRange(glCtx.viewportState.depthRangeNear, glCtx.viewportState.depthRangeFar);
        }
        if ((mask & GL11.GL_LINE_BIT) != 0 && glCtx.restoreLineChanged) {
            RENDER_BACKEND.lineWidth(Math.clamp(glCtx.lineState.getWidth(), lineWidthMin, lineWidthMax));
        }
        if ((mask & GL11.GL_POINT_BIT) != 0 && glCtx.restorePointChanged) {
            RENDER_BACKEND.pointSize(glCtx.pointState.getSize());
        }
        if ((mask & GL11.GL_POLYGON_BIT) != 0 && glCtx.restorePolygonChanged) {
            // Core profile only supports GL_FRONT_AND_BACK; use frontMode (front/back are always kept in sync since glPolygonMode also forces GL_FRONT_AND_BACK)
            RENDER_BACKEND.polygonMode(GL11.GL_FRONT_AND_BACK, glCtx.polygonState.getFrontMode());
            applyPolygonOffset(glCtx);
            RENDER_BACKEND.cullFace(glCtx.polygonState.getCullFaceMode());
            RENDER_BACKEND.frontFace(glCtx.polygonState.getFrontFace());
        }
        if ((mask & GL11.GL_TEXTURE_BIT) != 0) {
            // Restore only the units whose binding changed, then restore the active unit.
            // activeTextureUnit stores the 0-based index, glActiveTexture needs GL_TEXTURE0 + index.
            boolean unitTouched = false;
            for (int i = 0; i < MAX_TEXTURE_UNITS; i++) {
                if (glCtx.restoreUnitChanged[i]) {
                    final TextureBinding restored = glCtx.textures.getTextureUnitBindings(i);
                    final int restoredTarget = restored.getTarget() != 0 ? restored.getTarget() : GL11.GL_TEXTURE_2D;
                    RENDER_BACKEND.activeTexture(GL13.GL_TEXTURE0 + i);
                    RENDER_BACKEND.bindTexture(restoredTarget, restored.getBinding());
                    unitTouched = true;
                }
            }
            if (unitTouched || glCtx.restoreActiveUnitChanged) {
                RENDER_BACKEND.activeTexture(GL13.GL_TEXTURE0 + glCtx.activeTextureUnit.getValue());
            }
        }

        // Bump generation counters only if state actually changed during this push/pop scope.
        // If nothing changed, the shader already has the right uniforms - no need to re-upload.
        final int depth = glCtx.attribDepth; // already decremented by popState()
        if ((mask & GL11.GL_LIGHTING_BIT) != 0 && glCtx.lightingGeneration != glCtx.savedLightingGen[depth]) glCtx.lightingGeneration++;
        if ((mask & GL11.GL_FOG_BIT) != 0 && glCtx.fragmentGeneration != glCtx.savedFragmentGen[depth]) glCtx.fragmentGeneration++;
        if ((mask & GL11.GL_COLOR_BUFFER_BIT) != 0 && glCtx.fragmentGeneration != glCtx.savedFragmentGen[depth]) glCtx.fragmentGeneration++; // alpha ref
        if ((mask & GL11.GL_TEXTURE_BIT) != 0 && glCtx.fragmentGeneration != glCtx.savedFragmentGen[depth]) glCtx.fragmentGeneration++; // texenv state
        if ((mask & GL11.GL_TRANSFORM_BIT) != 0) {
            if (glCtx.mvGeneration != glCtx.savedMvGen[depth]) glCtx.mvGeneration++;
            if (glCtx.mvLinearGeneration != glCtx.savedMvLinearGen[depth]) glCtx.mvLinearGeneration++;
            if (glCtx.projGeneration != glCtx.savedProjGen[depth]) glCtx.projGeneration++;
            if (glCtx.texMatrixGeneration != glCtx.savedTexMatGen[depth]) glCtx.texMatrixGeneration++;
        }
        if ((mask & GL11.GL_CURRENT_BIT) != 0) {
            if (glCtx.colorGeneration != glCtx.savedColorGen[depth]) {
                glCtx.colorGeneration++;
                glCtx.dirtyColorAttrib = true;
            }
            if (ShaderManager.getNormalGeneration() != glCtx.savedNormalGen[depth]) {
                ShaderManager.bumpNormalGeneration();
                glCtx.dirtyNormalAttrib = true;
            }
            if (ShaderManager.getTexCoordGeneration() != glCtx.savedTexCoordGen[depth]) {
                ShaderManager.bumpTexCoordGeneration();
                glCtx.dirtyTexCoordAttrib = true;
            }
        }
    }

    public static void glClear(int mask) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordClear(mask);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        RENDER_BACKEND.clear(mask);
    }

    public static void glPushAttrib(int mask) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordPushAttrib(mask);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        GLDebug.pushGroup("pushState");
        pushState(mask);
    }

    public static void glPopAttrib() {
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordPopAttrib();
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        GLDebug.popGroup();
        glCtx.poppingAttributes = true;
        GLDebug.pushGroup("popState");
        try {
            popState();
        } finally {
            GLDebug.popGroup();
            glCtx.poppingAttributes = false;
        }
    }

    // Matrix Operations
    public static void glMatrixMode(int mode) {
        final RecordMode recordMode = DisplayListManager.getRecordMode();
        if (recordMode != RecordMode.NONE) {
            DisplayListManager.recordMatrixMode(mode);
            if (recordMode == RecordMode.COMPILE) {
                return;
            }
        }
        ctx().matrixMode.setMode(mode);
    }

    public static void glLoadMatrix(FloatBuffer m) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            final Matrix4f matrix = new Matrix4f().set(m);
            m.rewind();
            DisplayListManager.recordLoadMatrix(matrix);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        if (isCachingEnabled()) {
            getMatrixStack().set(m);
            bumpMatrixGeneration();
        }
    }

    public static void glLoadMatrix(DoubleBuffer m) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            // Convert double buffer to float buffer for recording
            conversionMatrix4d.set(m);
            m.rewind();
            final Matrix4f floatMatrix = new Matrix4f();
            floatMatrix.set(conversionMatrix4d);
            DisplayListManager.recordLoadMatrix(floatMatrix);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        if (isCachingEnabled()) {
            conversionMatrix4d.set(m);
            getMatrixStack().set(conversionMatrix4d);
            bumpMatrixGeneration();
        }
    }

    public static void setModelViewMatrix(Matrix4fc m) {
        final GLContextState glCtx = ctx();
        if (isCachingEnabled()) {
            glCtx.modelViewMatrix.set(m);
            glCtx.mvGeneration++;
            glCtx.mvLinearGeneration++;
        }
    }

    public static void setProjectionMatrix(Matrix4fc m) {
        final GLContextState glCtx = ctx();
        if (isCachingEnabled()) {
            glCtx.projectionMatrix.set(m);
            glCtx.projGeneration++;
        }
    }

    public static Matrix4fStack getMatrixStack() {
        final GLContextState glCtx = ctx();
        switch (glCtx.matrixMode.getMode()) {
            case GL11.GL_MODELVIEW -> {
                return glCtx.modelViewMatrix;
            }
            case GL11.GL_PROJECTION -> {
                return glCtx.projectionMatrix;
            }
            case GL11.GL_TEXTURE -> {
                return glCtx.textures.getTextureUnitMatrix(getActiveTextureUnit());
            }
            default -> throw new IllegalStateException("Unknown matrix mode: " + glCtx.matrixMode.getMode());
        }
    }

    /** Bump the generation counter for the currently active matrix mode. */
    private static void bumpMatrixGeneration() {
        bumpMatrixGeneration(true);
    }

    private static void bumpMatrixGeneration(boolean linearChanged) {
        final GLContextState glCtx = ctx();
        switch (glCtx.matrixMode.getMode()) {
            case GL11.GL_MODELVIEW -> {
                glCtx.mvGeneration++;
                if (linearChanged) glCtx.mvLinearGeneration++;
            }
            case GL11.GL_PROJECTION -> glCtx.projGeneration++;
            case GL11.GL_TEXTURE -> glCtx.texMatrixGeneration++;
        }
    }

    public static void glLoadIdentity() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordLoadIdentity();
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        if (isCachingEnabled()) {
            getMatrixStack().identity();
            bumpMatrixGeneration();
        }
    }

    public static void glTranslatef(float x, float y, float z) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.applyMatrixTranslation(x, y, z);
            return;
        }
        if (isCachingEnabled()) {
            getMatrixStack().translate(x, y, z);
            bumpMatrixGeneration(false);
        }
    }

    public static void glTranslated(double x, double y, double z) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.applyMatrixTranslation((float) x, (float) y, (float) z);
            return;
        }
        if (isCachingEnabled()) {
            getMatrixStack().translate((float) x, (float) y, (float) z);
            bumpMatrixGeneration(false);
        }
    }

    public static void glScalef(float x, float y, float z) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.applyMatrixScale(x, y, z);
            return;
        }
        if (isCachingEnabled()) {
            getMatrixStack().scale(x, y, z);
            bumpMatrixGeneration();
        }
    }

    public static void glScaled(double x, double y, double z) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.applyMatrixScale((float) x, (float) y, (float) z);
            return;
        }
        if (isCachingEnabled()) {
            getMatrixStack().scale((float) x, (float) y, (float) z);
            bumpMatrixGeneration();
        }
    }

    private static final Matrix4f multMatrix = new Matrix4f();

    public static void glMultMatrix(FloatBuffer floatBuffer) {
        multMatrix.set(floatBuffer);
        final int currentMode = ctx().matrixMode.getMode();

        if (DisplayListManager.isRecording()) {
            DisplayListManager.updateRelativeTransform(multMatrix);
            return;
        }
        if (isCachingEnabled()) {
            getMatrixStack().mul(multMatrix);
            bumpMatrixGeneration();
        }
    }

    public static void applyMultMatrix(Matrix4f multMatrix) {
        if (isCachingEnabled()) {
            getMatrixStack().mul(multMatrix);
            bumpMatrixGeneration();
        }
    }

    public static final Matrix4d conversionMatrix4d = new Matrix4d();
    public static final Matrix4f conversionMatrix4f = new Matrix4f();

    public static void glMultMatrix(DoubleBuffer matrix) {
        conversionMatrix4d.set(matrix);
        conversionMatrix4f.set(conversionMatrix4d);

        if (DisplayListManager.isRecording()) {
            DisplayListManager.updateRelativeTransform(conversionMatrix4f);
            return;
        }
        if (isCachingEnabled()) {
            getMatrixStack().mul(conversionMatrix4f);
            bumpMatrixGeneration();
        }
    }

    public static void glRotatef(float angle, float x, float y, float z) {
        final float lenSq = x * x + y * y + z * z;
        if (lenSq == 0.0f) return;

        // Fast way to normalize rotation
        final float scalar = 1.0f / (float) Math.sqrt(lenSq);
        x *= scalar;
        y *= scalar;
        z *= scalar;
        // Convert deg to rad
        angle = (float) Math.toRadians(angle);

        if (DisplayListManager.isRecording()) {
            DisplayListManager.applyMatrixRotation(angle, x, y, z);
            return;
        }
        if (isCachingEnabled()) {
            getMatrixStack().rotate(angle, x, y, z);
            bumpMatrixGeneration();
        }
    }

    public static void glRotated(double angle, double x, double y, double z) {
        final double lenSq = x * x + y * y + z * z;
        if (lenSq == 0.0) return;

        // Fast way to normalize rotation
        final double scalar = 1.0 / Math.sqrt(lenSq);
        x *= scalar;
        y *= scalar;
        z *= scalar;
        // Convert deg to rad
        angle = Math.toRadians(angle);

        if (DisplayListManager.isRecording()) {
            DisplayListManager.applyMatrixRotation((float) angle, (float) x, (float) y, (float) z);
            return;
        }
        if (isCachingEnabled()) {
            getMatrixStack().rotate((float) angle, (float) x, (float) y, (float) z);
            bumpMatrixGeneration();
        }
    }

    public static void glOrtho(double left, double right, double bottom, double top, double zNear, double zFar) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.updateRelativeTransformOrtho(left, right, bottom, top, zNear, zFar);
            return;
        }
        if (isCachingEnabled()) {
            getMatrixStack().ortho((float) left, (float) right, (float) bottom, (float) top, (float) zNear, (float) zFar);
            bumpMatrixGeneration();
        }
    }

    public static void glFrustum(double left, double right, double bottom, double top, double zNear, double zFar) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.updateRelativeTransformFrustum(left, right, bottom, top, zNear, zFar);
            return;
        }
        if (isCachingEnabled()) {
            getMatrixStack().frustum((float) left, (float) right, (float) bottom, (float) top, (float) zNear, (float) zFar);
            bumpMatrixGeneration();
        }
    }

    public static void glPushMatrix() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordPushMatrix();  // Handles flush + relativeTransform stack
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        // Only track stack on main thread (splash thread has separate GL context)
        if (isCachingEnabled()) {
            try {
                getMatrixStack().pushMatrix();
            } catch (IllegalStateException ignored) {
                if (initConfig != null && initConfig.isLwjglDebug()) LOGGER.warn("Matrix stack overflow ", new Throwable());
            }
        }
    }

    public static void glPopMatrix() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordPopMatrix();  // Handles flush + relativeTransform stack + lastRecordedTransform sync
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        // Only track stack on main thread (splash thread has separate GL context)
        if (isCachingEnabled()) {
            try {
                getMatrixStack().popMatrix();
                bumpMatrixGeneration();
            } catch (IllegalStateException ignored) {
                if (initConfig != null && initConfig.isLwjglDebug()) LOGGER.warn("Matrix stack underflow ", new Throwable());
            }
        }
    }

    private static final Matrix4f gluMatrix = new Matrix4f();
    private static final Matrix4f gluMatrix2 = new Matrix4f();
    private static final Vector4f gluVec4 = new Vector4f();
    private static final FloatBuffer gluBuffer = BufferUtils.createFloatBuffer(16);

    public static void gluPerspective(float fovy, float aspect, float zNear, float zFar) {
        gluMatrix.identity().perspective((float) Math.toRadians(fovy), aspect, zNear, zFar);
        gluMatrix.get(0, gluBuffer);
        GLStateManager.glMultMatrix(gluBuffer);
    }

    public static void gluLookAt(float eyex, float eyey, float eyez, float centerx, float centery, float centerz, float upx, float upy, float upz) {
        gluMatrix.identity().lookAt(eyex, eyey, eyez, centerx, centery, centerz, upx, upy, upz);
        gluMatrix.get(0, gluBuffer);
        GLStateManager.glMultMatrix(gluBuffer);
    }

    public static void gluOrtho2D(float left, float right, float bottom, float top) {
        GLStateManager.glOrtho(left, right, bottom, top, -1.0, 1.0);
    }

    public static void gluPickMatrix(float x, float y, float deltaX, float deltaY, IntBuffer viewport) {
        if (deltaX <= 0 || deltaY <= 0) return;
        GLStateManager.glTranslatef(
                (viewport.get(viewport.position() + 2) - 2 * (x - viewport.get(viewport.position() + 0))) / deltaX,
                (viewport.get(viewport.position() + 3) - 2 * (y - viewport.get(viewport.position() + 1))) / deltaY,
                0);
        GLStateManager.glScalef(viewport.get(viewport.position() + 2) / deltaX, viewport.get(viewport.position() + 3) / deltaY, 1.0f);
    }

    public static int gluBuild2DMipmaps(int target, int components, int width, int height, int format, int type, ByteBuffer data) {
        if (width < 1 || height < 1) return GLU.GLU_INVALID_VALUE;
        glTexImage2D(target, 0, components, width, height, 0, format, type, data);
        glGenerateMipmap(target);
        return 0;
    }

    public static String gluErrorString(int errorCode) {
        return switch (errorCode) {
            case GLU.GLU_INVALID_ENUM -> "Invalid enum (glu)";
            case GLU.GLU_INVALID_VALUE -> "Invalid value (glu)";
            case GLU.GLU_OUT_OF_MEMORY -> "Out of memory (glu)";
            case GL11.GL_NO_ERROR -> "No error";
            case GL11.GL_INVALID_ENUM -> "Invalid enum";
            case GL11.GL_INVALID_VALUE -> "Invalid value";
            case GL11.GL_INVALID_OPERATION -> "Invalid operation";
            case GL11.GL_OUT_OF_MEMORY -> "Out of memory";
            default -> "Unknown error: " + errorCode;
        };
    }

    public static boolean gluUnProject(float winx, float winy, float winz, FloatBuffer modelMatrix, FloatBuffer projMatrix, IntBuffer viewport, FloatBuffer obj_pos) {
        gluMatrix.set(projMatrix).mul(gluMatrix2.set(modelMatrix));
        if (Math.abs(gluMatrix.determinant()) < 1.0e-10f) return false;
        gluMatrix.invert(gluMatrix);

        float ndcX = (winx - viewport.get(viewport.position())) / viewport.get(viewport.position() + 2) * 2.0f - 1.0f;
        float ndcY = (winy - viewport.get(viewport.position() + 1)) / viewport.get(viewport.position() + 3) * 2.0f - 1.0f;
        float ndcZ = winz * 2.0f - 1.0f;

        gluVec4.set(ndcX, ndcY, ndcZ, 1.0f);
        gluMatrix.transform(gluVec4);
        if (gluVec4.w == 0.0f) return false;

        float invW = 1.0f / gluVec4.w;
        obj_pos.put(obj_pos.position(), gluVec4.x * invW);
        obj_pos.put(obj_pos.position() + 1, gluVec4.y * invW);
        obj_pos.put(obj_pos.position() + 2, gluVec4.z * invW);
        return true;
    }

    public static void glViewport(int x, int y, int width, int height) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordViewport(x, y, width, height);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        RENDER_BACKEND.viewport(x, y, width, height);
        // Only update cached state when caching is enabled
        if (isCachingEnabled()) {
            ctx().viewportState.setViewPort(x, y, width, height);
        }
    }

    public static int getActiveTextureUnit() {
        return ctx().activeTextureUnit.getValue();
    }

    public static int getListMode() {
        return DisplayListManager.getListMode();
    }

    private static boolean handleRemovedTexParam(int target, int pname, int value) {
        if (pname == GL14.GL_GENERATE_MIPMAP) {
            if (target == GL11.GL_TEXTURE_2D) {
                final TextureInfo info = getBoundTextureInfo();
                if (info != null) info.setGenerateMipmap(value != 0);
            }
            return true;
        }
        return false;
    }

    private static void maybeGenerateMipmap(int target, int level) {
        if (target != GL11.GL_TEXTURE_2D) return;
        final TextureInfo info = getBoundTextureInfo();
        if (info != null && info.isGenerateMipmap() && level == info.getBaseLevel() && level < info.getMaxLevel()) {
            RENDER_BACKEND.generateMipmap(target);
        }
    }

    public static boolean updateTexParameteriCache(int target, int texture, int pname, int param) {
        if (target != GL11.GL_TEXTURE_2D) {
            return true;
        }
        return updateTexParameteriCache(TextureInfoCache.INSTANCE.getInfo(texture), pname, param);
    }

    static boolean updateTexParameteriCache(TextureInfo info, int pname, int param) {
        if (info == null) {
            return true;
        }
        switch (pname) {
            case GL11.GL_TEXTURE_MIN_FILTER -> {
                if (info.getMinFilter() == param && isCachingEnabled()) return false;
                info.setMinFilter(param);
            }
            case GL11.GL_TEXTURE_MAG_FILTER -> {
                if (info.getMagFilter() == param && isCachingEnabled()) return false;
                info.setMagFilter(param);
            }
            case GL11.GL_TEXTURE_WRAP_S -> {
                if (info.getWrapS() == param && isCachingEnabled()) return false;
                info.setWrapS(param);
            }
            case GL11.GL_TEXTURE_WRAP_T -> {
                if (info.getWrapT() == param && isCachingEnabled()) return false;
                info.setWrapT(param);
            }
            case GL12.GL_TEXTURE_BASE_LEVEL -> {
                if (info.getBaseLevel() == param && isCachingEnabled()) return false;
                info.setBaseLevel(param);
            }
            case GL12.GL_TEXTURE_MAX_LEVEL -> {
                if (info.getMaxLevel() == param && isCachingEnabled()) return false;
                info.setMaxLevel(param);
            }
            case GL12.GL_TEXTURE_MIN_LOD -> {
                if (info.getMinLod() == param && isCachingEnabled()) return false;
                info.setMinLod(param);
            }
            case GL12.GL_TEXTURE_MAX_LOD -> {
                if (info.getMaxLod() == param && isCachingEnabled()) return false;
                info.setMaxLod(param);
            }
        }
        return true;
    }

    public static void glTexParameter(int target, int pname, IntBuffer params) {
        if (handleRemovedTexParam(target, pname, params.remaining() >= 1 ? params.get(params.position()) : 0)) return;
        if (params.remaining() >= 1) {
            final int val = params.get(params.position());
            final int remapped = remapTexClamp(pname, val);
            if (remapped != val) params.put(params.position(), remapped);
            if (target == GL11.GL_TEXTURE_2D) {
                updateTexParameteriCache(getBoundTextureInfo(), pname, remapped);
            }
        }
        RENDER_BACKEND.texParameteriv(target, pname, params);
    }

    public static void glTexParameter(int target, int pname, FloatBuffer params) {
        if (handleRemovedTexParam(target, pname, params.remaining() >= 1 ? (int) params.get(params.position()) : 0)) return;
        if (params.remaining() >= 1) {
            final float val = params.get(params.position());
            final float remapped = remapTexClamp(pname, val);
            if (remapped != val) params.put(params.position(), remapped);
            if (target == GL11.GL_TEXTURE_2D) {
                updateTexParameterfCache(getBoundTextureInfo(), pname, remapped);
            }
        }
        RENDER_BACKEND.texParameterfv(target, pname, params);
    }

    public static void glTexParameteri(int target, int pname, int param) {
        if (handleRemovedTexParam(target, pname, param)) return;
        param = remapTexClamp(pname, param);
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordTexParameteri(target, pname, param);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        if (target != GL11.GL_TEXTURE_2D) {
            RENDER_BACKEND.texParameteri(target, pname, param);
            return;
        }
        if (!updateTexParameteriCache(getBoundTextureInfo(), pname, param)) return;

        RENDER_BACKEND.texParameteri(target, pname, param);
    }

    public static boolean updateTexParameterfCache(int target, int texture, int pname, float param) {
        if (target != GL11.GL_TEXTURE_2D) {
            return true;
        }
        return updateTexParameterfCache(TextureInfoCache.INSTANCE.getInfo(texture), pname, param);
    }

    static boolean updateTexParameterfCache(TextureInfo info, int pname, float param) {
        if (info == null) {
            return true;
        }
        switch (pname) {
            case EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT -> {
                if (info.getMaxAnisotropy() == param && isCachingEnabled()) return false;
                info.setMaxAnisotropy(param);
            }
            case GL14.GL_TEXTURE_LOD_BIAS -> {
                if (info.getLodBias() == param && isCachingEnabled()) return false;
                info.setLodBias(param);
            }
        }
        return true;
    }

    public static void glTexParameterf(int target, int pname, float param) {
        if (handleRemovedTexParam(target, pname, param > 0 ? (int) (param + 0.5f) : (int) (param - 0.5f))) return;
        param = remapTexClamp(pname, param);
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordTexParameterf(target, pname, param);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        if (target != GL11.GL_TEXTURE_2D) {
            RENDER_BACKEND.texParameterf(target, pname, param);
            return;
        }
        if (!updateTexParameterfCache(getBoundTextureInfo(), pname, param)) return;

        RENDER_BACKEND.texParameterf(target, pname, param);
    }

    public static int getTexParameterOrDefault(int texture, int pname, IntSupplier defaultSupplier) {
        return getTexParameterOrDefault(TextureInfoCache.INSTANCE.getInfo(texture), pname, defaultSupplier);
    }

    static int getTexParameterOrDefault(TextureInfo info, int pname, IntSupplier defaultSupplier) {
        if (info == null) {
            if (isRecordingDisplayList()) {
                throw new IllegalStateException(
                    "glGetTexParameteri called during display list recording with no cached TextureInfo. " +
                        "Cannot query OpenGL state during compilation!");
            }
            return defaultSupplier.getAsInt();
        }
        return switch (pname) {
            case GL11.GL_TEXTURE_MIN_FILTER -> info.getMinFilter();
            case GL11.GL_TEXTURE_MAG_FILTER -> info.getMagFilter();
            case GL11.GL_TEXTURE_WRAP_S -> info.getWrapS();
            case GL11.GL_TEXTURE_WRAP_T -> info.getWrapT();
            case GL12.GL_TEXTURE_BASE_LEVEL -> info.getBaseLevel();
            case GL12.GL_TEXTURE_MAX_LEVEL -> info.getMaxLevel();
            case GL12.GL_TEXTURE_MIN_LOD -> info.getMinLod();
            case GL12.GL_TEXTURE_MAX_LOD -> info.getMaxLod();
            case GL14.GL_GENERATE_MIPMAP -> info.isGenerateMipmap() ? GL11.GL_TRUE : GL11.GL_FALSE;
            default -> {
                if (isRecordingDisplayList()) {
                    throw new IllegalStateException(String.format(
                        "glGetTexParameteri called during display list recording with uncached pname 0x%s. " +
                            "Cannot query OpenGL state during compilation!", Integer.toHexString(pname)));
                }
                yield defaultSupplier.getAsInt();
            }
        };
    }

    public static int glGetTexParameteri(int target, int pname) {
        if (target != GL11.GL_TEXTURE_2D || !isCachingEnabled()) {
            return RENDER_BACKEND.getTexParameteri(target, pname);
        }
        return getTexParameterOrDefault(getBoundTextureInfo(), pname, () -> RENDER_BACKEND.getTexParameteri(target, pname));
    }

    public static float glGetTexParameterf(int target, int pname) {
        if (target != GL11.GL_TEXTURE_2D || !isCachingEnabled()) {
            return RENDER_BACKEND.getTexParameterf(target, pname);
        }
        final TextureInfo info = getBoundTextureInfo();
        if(info == null) {
            if (isRecordingDisplayList()) {
                throw new IllegalStateException(
                    "glGetTexParameterf called during display list recording with no cached TextureInfo. " +
                    "Cannot query OpenGL state during compilation!");
            }
            return RENDER_BACKEND.getTexParameterf(target, pname);
        }

        return switch (pname) {
            case EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT -> info.getMaxAnisotropy();
            case GL14.GL_TEXTURE_LOD_BIAS -> info.getLodBias();
            default -> {
                if (isRecordingDisplayList()) {
                    throw new IllegalStateException(String.format(
                        "glGetTexParameterf called during display list recording with uncached pname 0x%s. " +
                        "Cannot query OpenGL state during compilation!", Integer.toHexString(pname)));
                }
                yield RENDER_BACKEND.getTexParameterf(target, pname);
            }
        };
    }

    public static int glGetTexLevelParameteri(int target, int level, int pname) {
        if (target != GL11.GL_TEXTURE_2D || !isCachingEnabled()) {
            return RENDER_BACKEND.getTexLevelParameteri(target, level, pname);
        }
        final TextureInfo info = TextureInfoCache.INSTANCE.getInfo(getBoundTextureForServerState());
        if (info == null) {
            if (isRecordingDisplayList()) {
                throw new IllegalStateException(String.format(
                    "glGetTexLevelParameteri called during display list recording with no cached TextureInfo for texture %d. " +
                    "Cannot query OpenGL state during compilation!", getBoundTextureForServerState()));
            }
            return RENDER_BACKEND.getTexLevelParameteri(target, level, pname);
        }
        return switch (pname) {
            case GL11.GL_TEXTURE_WIDTH -> Math.max(info.getWidth() >> level, 1);
            case GL11.GL_TEXTURE_HEIGHT -> Math.max(info.getHeight() >> level, 1);
            case GL11.GL_TEXTURE_INTERNAL_FORMAT -> {
                if (info.needsInternalFormatResolve() && isRecordingDisplayList()) {
                    throw new IllegalStateException(String.format("glGetTexLevelParameteri(GL_TEXTURE_INTERNAL_FORMAT) needs to resolve a generic compressed format for texture %d during display list recording", getBoundTextureForServerState()));
                }
                yield info.getResolvedInternalFormat();
            }
            default -> {
                if (isRecordingDisplayList()) {
                    throw new IllegalStateException(String.format(
                        "glGetTexLevelParameteri called during display list recording with uncached pname 0x%s for texture %d. " +
                        "Cannot query OpenGL state during compilation!", Integer.toHexString(pname), getBoundTextureForServerState()));
                }
                yield RENDER_BACKEND.getTexLevelParameteri(target, level, pname);
            }
        };
    }

    public static void glGetTexImage(int target, int level, int format, int type, ByteBuffer pixels) {
        if (RenderSystem.isGLES()) {
            getTexImageViaFboReadPixels(target, level, format, type, pixels);
            return;
        }
        suspendPixelPackBuffer();
        RENDER_BACKEND.getTexImage(target, level, format, type, pixels);
        restorePixelPackBuffer();
    }
    public static void glGetTexImage(int target, int level, int format, int type, IntBuffer pixels) {
        if (RenderSystem.isGLES()) {
            getTexImageViaFboReadPixels(target, level, format, type, pixels);
            return;
        }
        suspendPixelPackBuffer();
        RENDER_BACKEND.getTexImage(target, level, format, type, pixels);
        restorePixelPackBuffer();
    }

    private static void getTexImageViaFboReadPixels(int target, int level, int format, int type, Buffer pixels) {
        if (target != GL11.GL_TEXTURE_2D) {
            LOGGER.warn("glGetTexImage ES: unsupported target 0x{}", Integer.toHexString(target));
            return;
        }
        final int texId = getBoundTextureForServerState();
        if (texId == 0) return;
        final int width = RENDER_BACKEND.getTexLevelParameteri(target, level, GL11.GL_TEXTURE_WIDTH);
        final int height = RENDER_BACKEND.getTexLevelParameteri(target, level, GL11.GL_TEXTURE_HEIGHT);
        if (width <= 0 || height <= 0) return;

        final int prevReadFb = RENDER_BACKEND.getInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        final int fbo = RENDER_BACKEND.genFramebuffers();
        try {
            RENDER_BACKEND.bindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fbo);
            RENDER_BACKEND.framebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, target, texId, level);
            final int status = RENDER_BACKEND.checkFramebufferStatus(GL30.GL_READ_FRAMEBUFFER);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                LOGGER.warn("glGetTexImage ES: FBO incomplete (0x{})", Integer.toHexString(status));
                return;
            }
            final int esType = remapPixelTypeForGLES(format, type);
            final boolean bgra = format == GL12.GL_BGRA;
            final int esFormat = bgra ? GL11.GL_RGBA : format;
            suspendPixelPackBuffer();
            try {
                if (pixels instanceof ByteBuffer bb) {
                    RENDER_BACKEND.readPixels(0, 0, width, height, esFormat, esType, bb);
                } else if (pixels instanceof IntBuffer ib) {
                    RENDER_BACKEND.readPixels(0, 0, width, height, esFormat, esType, ib);
                }
                if (bgra) swapRedBlueInPlace(pixels);
            } finally {
                restorePixelPackBuffer();
            }
        } finally {
            RENDER_BACKEND.bindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFb);
            RENDER_BACKEND.deleteFramebuffers(fbo);
        }
    }

    private static void swapRedBlueInPlace(Buffer pixels) {
        if (pixels instanceof ByteBuffer bb) {
            final int p = bb.position();
            final int lim = bb.limit();
            for (int i = p; i + 3 < lim; i += 4) {
                final byte r = bb.get(i);
                bb.put(i, bb.get(i + 2));
                bb.put(i + 2, r);
            }
        } else if (pixels instanceof IntBuffer ib) {
            final int p = ib.position();
            final int lim = ib.limit();
            for (int i = p; i < lim; i++) {
                final int w = ib.get(i);
                ib.put(i, (w & 0xFF00FF00) | ((w & 0xFF) << 16) | ((w >> 16) & 0xFF));
            }
        }
    }

    public static void glReadPixels(int x, int y, int width, int height, int format, int type, ByteBuffer pixels) {
        type = remapPixelTypeForGLES(format, type);
        suspendPixelPackBuffer();
        RENDER_BACKEND.readPixels(x, y, width, height, format, type, pixels);
        restorePixelPackBuffer();
    }
    public static void glReadPixels(int x, int y, int width, int height, int format, int type, FloatBuffer pixels) {
        type = remapPixelTypeForGLES(format, type);
        suspendPixelPackBuffer();
        RENDER_BACKEND.readPixels(x, y, width, height, format, type, pixels);
        restorePixelPackBuffer();
    }
    public static void glReadPixels(int x, int y, int width, int height, int format, int type, IntBuffer pixels) {
        type = remapPixelTypeForGLES(format, type);
        suspendPixelPackBuffer();
        RENDER_BACKEND.readPixels(x, y, width, height, format, type, pixels);
        restorePixelPackBuffer();
    }
    public static void glTexStorage2D(int target, int levels, int internalFormat, int width, int height) { RENDER_BACKEND.texStorage2D(target, levels, internalFormat, width, height); }
    public static void glClearTexImage(int texture, int level, int format, int type, ByteBuffer data) { RENDER_BACKEND.clearTexImage(texture, level, format, type); }

    public static int glGenSamplers() { return RENDER_BACKEND.genSamplers(); }
    public static void glDeleteSamplers(int sampler) { RENDER_BACKEND.deleteSamplers(sampler); }
    public static void glBindSampler(int unit, int sampler) { RENDER_BACKEND.bindSampler(unit, sampler); }
    public static void glSamplerParameteri(int sampler, int pname, int param) { RENDER_BACKEND.samplerParameteri(sampler, pname, param); }
    public static void glSamplerParameterf(int sampler, int pname, float param) { RENDER_BACKEND.samplerParameterf(sampler, pname, param); }

    private static boolean glMaterialFront(int pname, FloatBuffer params) {
        final GLContextState glCtx = ctx();
        return switch (pname) {
            case GL11.GL_AMBIENT -> glCtx.frontMaterial.setAmbient(params);
            case GL11.GL_DIFFUSE -> glCtx.frontMaterial.setDiffuse(params);
            case GL11.GL_SPECULAR -> glCtx.frontMaterial.setSpecular(params);
            case GL11.GL_EMISSION -> glCtx.frontMaterial.setEmission(params);
            case GL11.GL_SHININESS -> glCtx.frontMaterial.setShininess(params);
            case GL11.GL_AMBIENT_AND_DIFFUSE -> {
                final boolean a = glCtx.frontMaterial.setAmbient(params);
                final boolean d = glCtx.frontMaterial.setDiffuse(params);
                yield a || d;
            }
            case GL11.GL_COLOR_INDEXES -> glCtx.frontMaterial.setColorIndexes(params);
            default -> false;
        };
    }

    private static boolean glMaterialBack(int pname, FloatBuffer params) {
        final GLContextState glCtx = ctx();
        return switch (pname) {
            case GL11.GL_AMBIENT -> glCtx.backMaterial.setAmbient(params);
            case GL11.GL_DIFFUSE -> glCtx.backMaterial.setDiffuse(params);
            case GL11.GL_SPECULAR -> glCtx.backMaterial.setSpecular(params);
            case GL11.GL_EMISSION -> glCtx.backMaterial.setEmission(params);
            case GL11.GL_SHININESS -> glCtx.backMaterial.setShininess(params);
            case GL11.GL_AMBIENT_AND_DIFFUSE -> {
                final boolean a = glCtx.backMaterial.setAmbient(params);
                final boolean d = glCtx.backMaterial.setDiffuse(params);
                yield a || d;
            }
            case GL11.GL_COLOR_INDEXES -> glCtx.backMaterial.setColorIndexes(params);
            default -> false;
        };
    }

    private static boolean glMaterialFront(int pname, IntBuffer params) {
        final GLContextState glCtx = ctx();
        return switch (pname) {
            case GL11.GL_AMBIENT -> glCtx.frontMaterial.setAmbient(params);
            case GL11.GL_DIFFUSE -> glCtx.frontMaterial.setDiffuse(params);
            case GL11.GL_SPECULAR -> glCtx.frontMaterial.setSpecular(params);
            case GL11.GL_EMISSION -> glCtx.frontMaterial.setEmission(params);
            case GL11.GL_SHININESS -> glCtx.frontMaterial.setShininess(params);
            case GL11.GL_AMBIENT_AND_DIFFUSE -> {
                final boolean a = glCtx.frontMaterial.setAmbient(params);
                final boolean d = glCtx.frontMaterial.setDiffuse(params);
                yield a || d;
            }
            case GL11.GL_COLOR_INDEXES -> glCtx.frontMaterial.setColorIndexes(params);
            default -> false;
        };
    }

    private static boolean glMaterialBack(int pname, IntBuffer params) {
        final GLContextState glCtx = ctx();
        return switch (pname) {
            case GL11.GL_AMBIENT -> glCtx.backMaterial.setAmbient(params);
            case GL11.GL_DIFFUSE -> glCtx.backMaterial.setDiffuse(params);
            case GL11.GL_SPECULAR -> glCtx.backMaterial.setSpecular(params);
            case GL11.GL_EMISSION -> glCtx.backMaterial.setEmission(params);
            case GL11.GL_SHININESS -> glCtx.backMaterial.setShininess(params);
            case GL11.GL_AMBIENT_AND_DIFFUSE -> {
                final boolean a = glCtx.backMaterial.setAmbient(params);
                final boolean d = glCtx.backMaterial.setDiffuse(params);
                yield a || d;
            }
            case GL11.GL_COLOR_INDEXES -> glCtx.backMaterial.setColorIndexes(params);
            default -> false;
        };
    }

    public static void glMaterial(int face, int pname, FloatBuffer params) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordMaterial(face, pname, params);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        boolean changed = false;
        if (face == GL11.GL_FRONT) {
            changed = glMaterialFront(pname, params);
        } else if (face == GL11.GL_BACK) {
            changed = glMaterialBack(pname, params);
        } else if (face == GL11.GL_FRONT_AND_BACK) {
            final boolean f = glMaterialFront(pname, params);
            final boolean b = glMaterialBack(pname, params);
            changed = f || b;
        } else {
            throw new RuntimeException("Unsupported face value for glMaterial: " + face);
        }
        if (changed) ctx().lightingGeneration++;
    }

    public static void glMaterial(int face, int pname, IntBuffer params) {
        // For IntBuffer version, we need to convert to float for recording
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            // Convert int params to float for recording
            final float[] floatParams = new float[params.remaining()];
            params.mark();
            for (int i = 0; i < floatParams.length; i++) {
                floatParams[i] = GLStateManager.i2f(params.get());
            }
            params.reset();
            DisplayListManager.recordMaterial(face, pname, FloatBuffer.wrap(floatParams));
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        boolean changed = false;
        if (face == GL11.GL_FRONT) {
            changed = glMaterialFront(pname, params);
        } else if (face == GL11.GL_BACK) {
            changed = glMaterialBack(pname, params);
        } else if (face == GL11.GL_FRONT_AND_BACK) {
            final boolean f = glMaterialFront(pname, params);
            final boolean b = glMaterialBack(pname, params);
            changed = f || b;
        } else {
            throw new RuntimeException("Unsupported face value for glMaterial: " + face);
        }
        if (changed) ctx().lightingGeneration++;
    }

    public static void glMaterialf(int face, int pname, float val) {
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordMaterialf(face, pname, val);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        if (pname != GL11.GL_SHININESS) {
            // it is only valid to call glMaterialf for the GL_SHININESS parameter
            return;
        }

        boolean changed = false;
        if (face == GL11.GL_FRONT) {
            changed = glCtx.frontMaterial.setShininess(val);
        } else if (face == GL11.GL_BACK) {
            changed = glCtx.backMaterial.setShininess(val);
        } else if (face == GL11.GL_FRONT_AND_BACK) {
            final boolean f = glCtx.frontMaterial.setShininess(val);
            final boolean b = glCtx.backMaterial.setShininess(val);
            changed = f || b;
        } else {
            throw new RuntimeException("Unsupported face value for glMaterial: " + face);
        }
        if (changed) glCtx.lightingGeneration++;
    }

    public static void glMateriali(int face, int pname, int val) {
        // This will end up no-opping if pname != GL_SHININESS, it is invalid to call this with another pname
        // Command recording happens in glMaterialf
        glMaterialf(face, pname, (float) val);
    }

    public static void glLight(int light, int pname, FloatBuffer params) {
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordLight(light, pname, params);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final LightStateStack lightState = glCtx.lightDataStates[light - GL11.GL_LIGHT0];
        final boolean changed = switch (pname) {
            case GL11.GL_AMBIENT -> lightState.setAmbient(params);
            case GL11.GL_DIFFUSE -> lightState.setDiffuse(params);
            case GL11.GL_SPECULAR -> lightState.setSpecular(params);
            case GL11.GL_POSITION -> lightState.setPosition(params);
            case GL11.GL_SPOT_DIRECTION -> lightState.setSpotDirection(params);
            case GL11.GL_SPOT_EXPONENT -> lightState.setSpotExponent(params);
            case GL11.GL_SPOT_CUTOFF -> lightState.setSpotCutoff(params);
            case GL11.GL_CONSTANT_ATTENUATION -> lightState.setConstantAttenuation(params);
            case GL11.GL_LINEAR_ATTENUATION -> lightState.setLinearAttenuation(params);
            case GL11.GL_QUADRATIC_ATTENUATION -> lightState.setQuadraticAttenuation(params);
            default -> false;
        };
        if (changed) glCtx.lightingGeneration++;
    }

    public static void glLight(int light, int pname, IntBuffer params) {
        final GLContextState glCtx = ctx();
        // For IntBuffer version, we need to convert to float for recording
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            // Convert int params to float for recording
            final float[] floatParams = new float[params.remaining()];
            params.mark();
            for (int i = 0; i < floatParams.length; i++) {
                floatParams[i] = GLStateManager.i2f(params.get());
            }
            params.reset();
            DisplayListManager.recordLight(light, pname, FloatBuffer.wrap(floatParams));
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final LightStateStack lightState = glCtx.lightDataStates[light - GL11.GL_LIGHT0];
        final boolean changed = switch (pname) {
            case GL11.GL_AMBIENT -> lightState.setAmbient(params);
            case GL11.GL_DIFFUSE -> lightState.setDiffuse(params);
            case GL11.GL_SPECULAR -> lightState.setSpecular(params);
            case GL11.GL_POSITION -> lightState.setPosition(params);
            case GL11.GL_SPOT_DIRECTION -> lightState.setSpotDirection(params);
            case GL11.GL_SPOT_EXPONENT -> lightState.setSpotExponent(params);
            case GL11.GL_SPOT_CUTOFF -> lightState.setSpotCutoff(params);
            case GL11.GL_CONSTANT_ATTENUATION -> lightState.setConstantAttenuation(params);
            case GL11.GL_LINEAR_ATTENUATION -> lightState.setLinearAttenuation(params);
            case GL11.GL_QUADRATIC_ATTENUATION -> lightState.setQuadraticAttenuation(params);
            default -> false;
        };
        if (changed) glCtx.lightingGeneration++;
    }

    public static void glLightf(int light, int pname, float param) {
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordLightf(light, pname, param);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final LightStateStack lightState = glCtx.lightDataStates[light - GL11.GL_LIGHT0];
        final boolean changed = switch (pname) {
            case GL11.GL_SPOT_EXPONENT -> lightState.setSpotExponent(param);
            case GL11.GL_SPOT_CUTOFF -> lightState.setSpotCutoff(param);
            case GL11.GL_CONSTANT_ATTENUATION -> lightState.setConstantAttenuation(param);
            case GL11.GL_LINEAR_ATTENUATION -> lightState.setLinearAttenuation(param);
            case GL11.GL_QUADRATIC_ATTENUATION -> lightState.setQuadraticAttenuation(param);
            default -> false;
        };
        if (changed) glCtx.lightingGeneration++;
    }

    public static void glLighti(int light, int pname, int param) {
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordLighti(light, pname, param);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final LightStateStack lightState = glCtx.lightDataStates[light - GL11.GL_LIGHT0];
        final boolean changed = switch (pname) {
            case GL11.GL_SPOT_EXPONENT -> lightState.setSpotExponent(param);
            case GL11.GL_SPOT_CUTOFF -> lightState.setSpotCutoff(param);
            case GL11.GL_CONSTANT_ATTENUATION -> lightState.setConstantAttenuation(param);
            case GL11.GL_LINEAR_ATTENUATION -> lightState.setLinearAttenuation(param);
            case GL11.GL_QUADRATIC_ATTENUATION -> lightState.setQuadraticAttenuation(param);
            default -> false;
        };
        if (changed) glCtx.lightingGeneration++;
    }

    public static void glLightModel(int pname, FloatBuffer params) {
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordLightModel(pname, params);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean changed = switch (pname) {
            case GL11.GL_LIGHT_MODEL_AMBIENT -> glCtx.lightModel.setAmbient(params);
            case GL11.GL_LIGHT_MODEL_LOCAL_VIEWER -> glCtx.lightModel.setLocalViewer(params);
            case GL11.GL_LIGHT_MODEL_TWO_SIDE -> glCtx.lightModel.setTwoSide(params);
            default -> false;
        };
        if (changed) glCtx.lightingGeneration++;
    }

    public static void glLightModel(int pname, IntBuffer params) {
        final GLContextState glCtx = ctx();
        // For IntBuffer version, we need to convert to float for recording
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            // Convert int params to float for recording
            final float[] floatParams = new float[params.remaining()];
            params.mark();
            for (int i = 0; i < floatParams.length; i++) {
                floatParams[i] = GLStateManager.i2f(params.get());
            }
            params.reset();
            DisplayListManager.recordLightModel(pname, FloatBuffer.wrap(floatParams));
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean changed = switch (pname) {
            case GL11.GL_LIGHT_MODEL_AMBIENT -> glCtx.lightModel.setAmbient(params);
            case GL12.GL_LIGHT_MODEL_COLOR_CONTROL -> glCtx.lightModel.setColorControl(params);
            case GL11.GL_LIGHT_MODEL_LOCAL_VIEWER -> glCtx.lightModel.setLocalViewer(params);
            case GL11.GL_LIGHT_MODEL_TWO_SIDE -> glCtx.lightModel.setTwoSide(params);
            default -> false;
        };
        if (changed) glCtx.lightingGeneration++;
    }

    public static void glLightModelf(int pname, float param) {
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordLightModelf(pname, param);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }

        if (isCachingEnabled()) {
            final boolean changed = switch (pname) {
                case GL11.GL_LIGHT_MODEL_LOCAL_VIEWER -> glCtx.lightModel.setLocalViewer(param);
                case GL11.GL_LIGHT_MODEL_TWO_SIDE -> glCtx.lightModel.setTwoSide(param);
                default -> false;
            };
            if (changed) glCtx.lightingGeneration++;
        }
    }

    public static void glLightModeli(int pname, int param) {
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordLightModeli(pname, param);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }

        if (isCachingEnabled()) {
            final boolean changed = switch (pname) {
                case GL12.GL_LIGHT_MODEL_COLOR_CONTROL -> glCtx.lightModel.setColorControl(param);
                case GL11.GL_LIGHT_MODEL_LOCAL_VIEWER -> glCtx.lightModel.setLocalViewer(param);
                case GL11.GL_LIGHT_MODEL_TWO_SIDE -> glCtx.lightModel.setTwoSide(param);
                default -> false;
            };
            if (changed) glCtx.lightingGeneration++;
        }
    }

    public static void glColorMaterial(int face, int mode) {
        final GLContextState glCtx = ctx();
        final RecordMode recordMode = DisplayListManager.getRecordMode();
        if (recordMode != RecordMode.NONE) {
            DisplayListManager.recordColorMaterial(face, mode);
            if (recordMode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();
        if (!caching || glCtx.colorMaterialFace.getValue() != face || glCtx.colorMaterialParameter.getValue() != mode) {
            glCtx.colorMaterialFace.setValue(face);
            glCtx.colorMaterialParameter.setValue(mode);
            glCtx.lightingGeneration++;
        }
    }

    public static void glDepthRange(double near, double far) {
        if (isCachingEnabled()) ctx().viewportState.setDepthRange(near, far);
        RENDER_BACKEND.depthRange(near, far);
    }

    public static void glUseProgram(int program) {
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordUseProgram(program);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }

        final ShaderManager ffp = ShaderManager.getInstance();
        if (program == 0 && ffp.isEnabled()) {
            final int prev = glCtx.activeProgram;
            final boolean caching = isCachingEnabled();
            if (prev != 0) glCtx.programGeneration++;
            glCtx.activeProgram = 0; // Track that FFP was requested
            if (GLSMHooks.PROGRAM_CHANGE.hasListeners()) {
                GLSMHooks.programChangeEvent.previousProgram = prev;
                GLSMHooks.programChangeEvent.newProgram = 0;
                GLSMHooks.programChangeEvent.postBind = false;
                GLSMHooks.PROGRAM_CHANGE.post(GLSMHooks.programChangeEvent);
            }
            ffp.activate();
            return;
        }

        // Non-zero program or FFP emulation not enabled
        if (ffp.isActive()) {
            ffp.deactivate();
        }

        final boolean caching = isCachingEnabled();
        if (!caching || program != glCtx.activeProgram) {
            if (Tracy.ENABLED) programSwitches++;
            glCtx.programGeneration++;
            final int prev = glCtx.activeProgram;
            glCtx.activeProgram = program;
            if (GLSMHooks.PROGRAM_CHANGE.hasListeners()) {
                GLSMHooks.programChangeEvent.previousProgram = prev;
                GLSMHooks.programChangeEvent.newProgram = program;
                GLSMHooks.programChangeEvent.postBind = false;
                GLSMHooks.PROGRAM_CHANGE.post(GLSMHooks.programChangeEvent);
            }
            if (initConfig != null && initConfig.isLwjglDebug()) {
                final String programName = GLDebug.getObjectLabel(KHRDebug.GL_PROGRAM, program);
                GLDebug.debugMessage("Activating Program - " + program + ":" + programName);
            }
            RENDER_BACKEND.useProgram(program);
            if (GLSMHooks.PROGRAM_CHANGE.hasListeners()) {
                GLSMHooks.programChangeEvent.previousProgram = prev;
                GLSMHooks.programChangeEvent.newProgram = program;
                GLSMHooks.programChangeEvent.postBind = true;
                GLSMHooks.PROGRAM_CHANGE.post(GLSMHooks.programChangeEvent);
            }
        }
    }

    // Missing GL commands from Mesa cross-check
    public static void glTexImage1D(int target, int level, int internalformat, int width, int border, int format, int type, ByteBuffer pixels) {
        internalformat = changeFormatIfDeprecated(internalformat);
        type = remapPixelTypeForGLES(format, type);
        type = remapTypeForRemappedInternalFormat(internalformat, type);
        if (DisplayListManager.isRecording()) {
            throw DisplayListManager.unsupportedInList("glTexImage1D");
        }
        suspendPixelUnpackBuffer();
        RENDER_BACKEND.texImage1D(target, level, internalformat, width, border, format, type, pixels);
        restorePixelUnpackBuffer();
    }

    public static void glTexImage3D(int target, int level, int internalformat, int width, int height, int depth, int border, int format, int type, ByteBuffer pixels) {
        internalformat = changeFormatIfDeprecated(internalformat);
        type = remapPixelTypeForGLES(format, type);
        type = remapTypeForRemappedInternalFormat(internalformat, type);
        if (DisplayListManager.isRecording()) {
            throw DisplayListManager.unsupportedInList("glTexImage3D");
        }
        suspendPixelUnpackBuffer();
        RENDER_BACKEND.texImage3D(target, level, internalformat, width, height, depth, border, format, type, pixels);
        restorePixelUnpackBuffer();
    }

    public static void glTexImage3D(int target, int level, int internalformat, int width, int height, int depth, int border, int format, int type, IntBuffer pixels) {
        internalformat = changeFormatIfDeprecated(internalformat);
        type = remapPixelTypeForGLES(format, type);
        type = remapTypeForRemappedInternalFormat(internalformat, type);
        if (DisplayListManager.isRecording()) {
            throw DisplayListManager.unsupportedInList("glTexImage3D");
        }
        suspendPixelUnpackBuffer();
        RENDER_BACKEND.texImage3D(target, level, internalformat, width, height, depth, border, format, type, pixels);
        restorePixelUnpackBuffer();
    }

    public static void glTexSubImage1D(int target, int level, int xoffset, int width, int format, int type, ByteBuffer pixels) {
        if (DisplayListManager.isRecording()) {
            throw DisplayListManager.unsupportedInList("glTexSubImage1D");
        }
        suspendPixelUnpackBuffer();
        RENDER_BACKEND.texSubImage1D(target, level, xoffset, width, format, type, pixels);
        restorePixelUnpackBuffer();
    }

    public static void glLineWidth(float width) {
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordLineWidth(width);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();
        if (!caching || glCtx.lineState.getWidth() != width) {
            glCtx.lineState.setWidth(width);
            RENDER_BACKEND.lineWidth(Math.clamp(width, lineWidthMin, lineWidthMax));
        }
    }

    public static void prepareLineEmulation(int drawMode) {
        final GLContextState glCtx = ctx();
        final boolean isLine = drawMode == GL11.GL_LINES || drawMode == GL11.GL_LINE_STRIP || drawMode == GL11.GL_LINE_LOOP;
        wideLineEmulationActive = wideLineEmulationEnabled && RENDER_BACKEND.supportsGeometryShaders() && isLine && glCtx.lineState.getWidth() > 1.0f;
        setLineStippleActive(isLine && glCtx.lineStippleState.isEnabled());
    }

    public static void disableLineEmulation() {
        wideLineEmulationActive = false;
        setLineStippleActive(false);
    }

    private static void setLineStippleActive(boolean active) {
        if (lineStippleActive == active) return;
        lineStippleActive = active;
        RENDER_BACKEND.provokingVertex(active ? GL32.GL_FIRST_VERTEX_CONVENTION : GL32.GL_LAST_VERTEX_CONVENTION);
    }

    public static void glFlush() {
        RENDER_BACKEND.flush();
    }

    public static void glFinish() {
        RENDER_BACKEND.finish();
    }

    public static void setVSyncEnabled(boolean enabled) {
        RENDER_BACKEND.setVSyncEnabled(enabled);
    }

    public static int glGetError() {
        if (!RENDER_BACKEND.hasContext()) return 0;
        if (initConfig.isNoErrorChecks()) return 0;
        return RENDER_BACKEND.getError();
    }

    public static String glGetString(int pname) {
        if (!RENDER_BACKEND.hasContext()) return "no valid GL/render context";
        return RENDER_BACKEND.getString(pname);
    }

    public static String glGetStringi(int name, int index) {
        if (!RENDER_BACKEND.hasContext()) return "no valid GL/render context";
        return RENDER_BACKEND.getStringi(name, index);
    }

    public static void glShaderSource(int shader, CharSequence source) {
        String src = source.toString();
        // Always rename reserved words for the target GLSL version (e.g. 'sampler' at 460)
        src = GlslTransformUtils.renameReservedWords(src, RENDER_BACKEND.getMinGLSLVersion());
        if (ShaderManager.getInstance().isEnabled()) {
            final int shaderType = RENDER_BACKEND.getShaderi(shader, GL20.GL_SHADER_TYPE);
            src = CompatShaderTransformer.transform(src, shaderType, shaderType == GL20.GL_FRAGMENT_SHADER);
        }
        RENDER_BACKEND.shaderSource(shader, src);
    }

    public static void nglShaderSource(int shader, int count, long strings, long lengths) {
        if (count <= 0) return;
        final CharSequence source;
        if (count == 1) {
            source = decodeShaderSourceString(strings, lengths, 0);
        } else {
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < count; i++) sb.append(decodeShaderSourceString(strings, lengths, i));
            source = sb.toString();
        }
        glShaderSource(shader, source);
    }

    private static String decodeShaderSourceString(long strings, long lengths, int i) {
        final long strAddr = MemoryUtilities.memGetAddress(strings + (long) i * Pointer.POINTER_SIZE);
        final int len = lengths == MemoryUtilities.NULL ? -1 : MemoryUtilities.memGetInt(lengths + (long) i * 4);
        return len < 0 ? MemoryUtilities.memUTF8(strAddr) : MemoryUtilities.memUTF8(strAddr, len);
    }

    public static void glShaderSource(int shader, ByteBuffer source) {
        final byte[] bytes = new byte[source.remaining()];
        final int pos = source.position();
        source.get(bytes);
        source.position(pos);
        glShaderSource(shader, new String(bytes, StandardCharsets.UTF_8));
    }

    public static void glShaderSource(int shader, CharSequence[] sources) {
        int totalLen = 0;
        for (CharSequence s : sources) totalLen += s.length();
        final StringBuilder sb = new StringBuilder(totalLen);
        for (CharSequence s : sources) sb.append(s);
        glShaderSource(shader, sb.toString());
    }

    /** Check shader type to determine if fragment output transformation is needed. */
    private static boolean isFragmentShader(int shader) {
        return RENDER_BACKEND.getShaderi(shader, GL20.GL_SHADER_TYPE) == GL20.GL_FRAGMENT_SHADER;
    }

    // Texture commands
    public static void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, ByteBuffer pixels) {
        type = remapPixelTypeForGLES(format, type);
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordComplexCommand(TexSubImage2DCmd.fromByteBuffer(target, level, xoffset, yoffset, width, height, format, type, pixels, ctx().pixelUnpackState));
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        suspendPixelUnpackBuffer();
        RENDER_BACKEND.texSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels);
        restorePixelUnpackBuffer();
        maybeGenerateMipmap(target, level);
    }

    public static void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, IntBuffer pixels) {
        type = remapPixelTypeForGLES(format, type);
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordComplexCommand(TexSubImage2DCmd.fromIntBuffer(target, level, xoffset, yoffset, width, height, format, type, pixels, ctx().pixelUnpackState));
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        suspendPixelUnpackBuffer();
        RENDER_BACKEND.texSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels);
        restorePixelUnpackBuffer();
        maybeGenerateMipmap(target, level);
    }

    public static void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, long pixels_buffer_offset) {
        type = remapPixelTypeForGLES(format, type);
        if (DisplayListManager.isRecording()) {
            throw DisplayListManager.unsupportedInList("glTexSubImage2D with buffer offset");
        }
        RENDER_BACKEND.texSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels_buffer_offset);
        maybeGenerateMipmap(target, level);
    }

    public static void glTexSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset, int width, int height, int depth, int format, int type, ByteBuffer pixels) {
        if (DisplayListManager.isRecording()) {
            throw DisplayListManager.unsupportedInList("glTexSubImage3D");
        }
        suspendPixelUnpackBuffer();
        RENDER_BACKEND.texSubImage3D(target, level, xoffset, yoffset, zoffset, width, height, depth, format, type, pixels);
        restorePixelUnpackBuffer();
    }

    public static void glCopyTexImage1D(int target, int level, int internalFormat, int x, int y, int width, int border) {
        if (DisplayListManager.isRecording()) {
            throw DisplayListManager.unsupportedInList("glCopyTexImage1D");
        }
        RENDER_BACKEND.copyTexImage1D(target, level, internalFormat, x, y, width, border);
    }

    public static void glCopyTexImage2D(int target, int level, int internalFormat, int x, int y, int width, int height, int border) {
        internalFormat = changeFormatIfDeprecated(internalFormat);
        if (DisplayListManager.isRecording()) {
            throw DisplayListManager.unsupportedInList("glCopyTexImage2D");
        }
        RENDER_BACKEND.copyTexImage2D(target, level, internalFormat, x, y, width, height, border);
        maybeGenerateMipmap(target, level);
    }

    public static void glCopyTexSubImage1D(int target, int level, int xoffset, int x, int y, int width) {
        if (DisplayListManager.isRecording()) {
            throw DisplayListManager.unsupportedInList("glCopyTexSubImage1D");
        }
        RENDER_BACKEND.copyTexSubImage1D(target, level, xoffset, x, y, width);
    }

    public static void glCopyTexSubImage2D(int target, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        if (DisplayListManager.isRecording()) {
            throw DisplayListManager.unsupportedInList("glCopyTexSubImage2D");
        }
        RENDER_BACKEND.copyTexSubImage2D(target, level, xoffset, yoffset, x, y, width, height);
        maybeGenerateMipmap(target, level);
    }

    public static void glCopyTexSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset, int x, int y, int width, int height) {
        if (DisplayListManager.isRecording()) {
            throw DisplayListManager.unsupportedInList("glCopyTexSubImage3D");
        }
        RENDER_BACKEND.copyTexSubImage3D(target, level, xoffset, yoffset, zoffset, x, y, width, height);
    }

    // State commands
    public static void glCullFace(int mode) {
        final GLContextState glCtx = ctx();
        final RecordMode recordMode = DisplayListManager.getRecordMode();
        if (recordMode != RecordMode.NONE) {
            DisplayListManager.recordCullFace(mode);
            if (recordMode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();
        if (!caching || glCtx.polygonState.getCullFaceMode() != mode) {
            glCtx.polygonState.setCullFaceMode(mode);
            RENDER_BACKEND.cullFace(mode);
        }
    }

    public static void glFrontFace(int mode) {
        final GLContextState glCtx = ctx();
        final RecordMode recordMode = DisplayListManager.getRecordMode();
        if (recordMode != RecordMode.NONE) {
            DisplayListManager.recordFrontFace(mode);
            if (recordMode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();
        if (!caching || glCtx.polygonState.getFrontFace() != mode) {
            glCtx.polygonState.setFrontFace(mode);
            RENDER_BACKEND.frontFace(mode);
        }
    }

    public static void glHint(int target, int hint) {
        // These hint targets are removed in core profile, ignore them
        switch (target) {
            case GL11.GL_PERSPECTIVE_CORRECTION_HINT, GL11.GL_POINT_SMOOTH_HINT, GL11.GL_FOG_HINT, GL14.GL_GENERATE_MIPMAP_HINT -> {
                return;
            }
        }
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordHint(target, hint);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        RENDER_BACKEND.hint(target, hint);
    }

    public static void glLineStipple(int factor, short pattern) {
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordLineStipple(factor, pattern);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        glCtx.lineState.setStippleFactor(factor);
        glCtx.lineState.setStipplePattern(pattern);
    }

    public static void glPointSize(float size) {
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordPointSize(size);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();
        if (!caching || glCtx.pointState.getSize() != size) {
            glCtx.pointState.setSize(size);
            RENDER_BACKEND.pointSize(size);
        }
    }

    public static void glPolygonMode(int face, int polygonMode) {
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordPolygonMode(face, polygonMode);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        // Track front/back separately in cache (Mesa compat semantics), but always issue GL_FRONT_AND_BACK to the driver (core profile constraint).
        final boolean caching = isCachingEnabled();
        final boolean needsUpdate;
        if (face == GL11.GL_FRONT) {
            needsUpdate = !caching || glCtx.polygonState.getFrontMode() != polygonMode;
            if (caching && needsUpdate) glCtx.polygonState.setFrontMode(polygonMode);
        } else if (face == GL11.GL_BACK) {
            needsUpdate = !caching || glCtx.polygonState.getBackMode() != polygonMode;
            if (caching && needsUpdate) glCtx.polygonState.setBackMode(polygonMode);
        } else { // GL_FRONT_AND_BACK
            needsUpdate = !caching || glCtx.polygonState.getFrontMode() != polygonMode || glCtx.polygonState.getBackMode() != polygonMode;
            if (caching && needsUpdate) {
                glCtx.polygonState.setFrontMode(polygonMode);
                glCtx.polygonState.setBackMode(polygonMode);
            }
        }
        if (needsUpdate) {
            RENDER_BACKEND.polygonMode(GL11.GL_FRONT_AND_BACK, polygonMode);
        }
    }

    public static void glPolygonOffset(float factor, float units) {
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordPolygonOffset(factor, units);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();

        if (!caching || glCtx.polygonState.getOffsetFactor() != factor || glCtx.polygonState.getOffsetUnits() != units || glCtx.polygonState.getOffsetClamp() != 0.0f) {
            glCtx.polygonState.setOffsetFactor(factor);
            glCtx.polygonState.setOffsetUnits(units);
            glCtx.polygonState.setOffsetClamp(0.0f);
            RENDER_BACKEND.polygonOffset(factor, units);
        }
    }

    public static void glPolygonOffsetClamp(float factor, float units, float clamp) {
        if (clamp == 0.0f) {
            glPolygonOffset(factor, units);
            return;
        }
        if (DisplayListManager.isRecording()) {
            throw DisplayListManager.unsupportedInList("glPolygonOffsetClamp");
        }
        final GLContextState glCtx = ctx();
        final boolean caching = isCachingEnabled();
        if (!caching || glCtx.polygonState.getOffsetFactor() != factor || glCtx.polygonState.getOffsetUnits() != units || glCtx.polygonState.getOffsetClamp() != clamp) {
            glCtx.polygonState.setOffsetFactor(factor);
            glCtx.polygonState.setOffsetUnits(units);
            glCtx.polygonState.setOffsetClamp(clamp);
            RENDER_BACKEND.polygonOffsetClamp(factor, units, clamp);
        }
    }

    public static void glMultiDrawArraysIndirectCount(int mode, long indirect, long drawcount, int maxdrawcount, int stride) {
        RENDER_BACKEND.multiDrawArraysIndirectCount(mode, indirect, drawcount, maxdrawcount, stride);
    }

    public static void glMultiDrawElementsIndirectCount(int mode, int type, long indirect, long drawcount, int maxdrawcount, int stride) {
        RENDER_BACKEND.multiDrawElementsIndirectCount(mode, type, indirect, drawcount, maxdrawcount, stride);
    }

    public static void glSpecializeShader(int shader, CharSequence entryPoint, IntBuffer constantIndex, IntBuffer constantValue) {
        RENDER_BACKEND.specializeShader(shader, entryPoint, constantIndex, constantValue);
    }

    public static void glReadBuffer(int mode) {
        if (DisplayListManager.isRecording()) {
            throw DisplayListManager.unsupportedInList("glReadBuffer");
        }
        RENDER_BACKEND.readBuffer(mode);
    }

    public static void glScissor(int x, int y, int width, int height) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordScissor(x, y, width, height);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        RENDER_BACKEND.scissor(x, y, width, height);
    }

    public static void glStencilFunc(int func, int ref, int mask) {
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordStencilFunc(func, ref, mask);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final int clampedMask = mask & glCtx.stencilBitMask;
        final boolean caching = isCachingEnabled();
        if (!caching || glCtx.stencilState.getFuncFront() != func || glCtx.stencilState.getRefFront() != ref || glCtx.stencilState.getValueMaskFront() != clampedMask) {
            glCtx.stencilState.setFunc(func, ref, clampedMask);
            RENDER_BACKEND.stencilFunc(func, ref, clampedMask);
        }
    }

    public static void glStencilMask(int mask) {
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordStencilMask(mask);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final int clampedMask = mask & glCtx.stencilBitMask;
        final boolean caching = isCachingEnabled();
        if (!caching || glCtx.stencilState.getWriteMaskFront() != clampedMask) {
            glCtx.stencilState.setWriteMask(clampedMask);
            RENDER_BACKEND.stencilMask(clampedMask);
        }
    }

    public static void glStencilOp(int fail, int zfail, int zpass) {
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordStencilOp(fail, zfail, zpass);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();
        if (!caching || glCtx.stencilState.getFailOpFront() != fail || glCtx.stencilState.getZFailOpFront() != zfail || glCtx.stencilState.getZPassOpFront() != zpass) {
            glCtx.stencilState.setOp(fail, zfail, zpass);
            RENDER_BACKEND.stencilOp(fail, zfail, zpass);
        }
    }

    // glPixelStore* isn'tcompiled into display lists, it executes immediately
    public static void glPixelStorei(int pname, int param) {
        final GLContextState glCtx = ctx();
        if (isCachingEnabled()) {
            glCtx.pixelUnpackState = glCtx.pixelUnpackState.with(pname, param);
        }
        RENDER_BACKEND.pixelStorei(pname, param);
    }

    public static void glPixelStoref(int pname, float param) {
        glPixelStorei(pname, param < 0 ? -Math.round(-param) : Math.round(param));
    }

    // Display List Commands
    public static void glCallLists(IntBuffer lists) {
        while (lists.hasRemaining()) {
            final int listId = lists.get() + ctx().listBase;
            glCallList(listId);
        }
    }

    public static void glCallLists(ShortBuffer lists) {
        while (lists.hasRemaining()) {
            final int listId = (lists.get() & 0xFFFF) + ctx().listBase;
            glCallList(listId);
        }
    }

    public static void glCallLists(ByteBuffer lists) {
        while (lists.hasRemaining()) {
            final int listId = (lists.get() & 0xFF) + ctx().listBase;
            glCallList(listId);
        }
    }

    public static void glListBase(int base) {
        ctx().listBase = base;
    }

    // Clip Plane Commands
    public static void glClipPlane(int plane, DoubleBuffer equation) {
        final GLContextState glCtx = ctx();
        if (DisplayListManager.isRecording()) {
            final int pos = equation.position();
            DisplayListManager.recordClipPlane(plane, equation.get(pos), equation.get(pos + 1), equation.get(pos + 2), equation.get(pos + 3));
            return;
        }
        final int index = plane - GL11.GL_CLIP_PLANE0;
        if (index < 0 || index >= MAX_CLIP_PLANES) return;
        final int pos = equation.position();
        glCtx.clipPlaneState.setPlane(index, equation.get(pos), equation.get(pos + 1), equation.get(pos + 2), equation.get(pos + 3), glCtx.modelViewMatrix);
        glCtx.clipPlaneGeneration++;
    }

    /** Returns true if any GL_CLIP_PLANE0..7 is currently enabled. */
    public static boolean anyClipPlaneEnabled() {
        for (int i = 0; i < MAX_CLIP_PLANES; i++) {
            if (ctx().clipPlaneStates[i].isEnabled()) return true;
        }
        return false;
    }

    // Clear Commands
    public static void glClearStencil(int s) {
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordClearStencil(s);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();
        if (!caching || glCtx.stencilState.getClearValue() != s) {
            glCtx.stencilState.setClearValue(s);
            RENDER_BACKEND.clearStencil(s);
        }
    }

    // Draw Buffer Commands (GL 2.0+)
    public static void glDrawBuffers(int buffer) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDrawBuffers(1, buffer);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        RENDER_BACKEND.drawBuffers(buffer);
    }

    public static void glDrawBuffers(IntBuffer bufs) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDrawBuffers(bufs.remaining(), bufs);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        RENDER_BACKEND.drawBuffers(bufs);
    }

    // Multisample Commands
    public static void glSampleCoverage(float value, boolean invert) {
        if (DisplayListManager.isRecording()) {
            throw DisplayListManager.unsupportedInList("glSampleCoverage");
        }
        RENDER_BACKEND.sampleCoverage(value, invert);
    }

    // Stencil Separate Functions (GL 2.0+)
    public static void glStencilFuncSeparate(int face, int func, int ref, int mask) {
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordStencilFuncSeparate(face, func, ref, mask);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final int clampedMask = mask & glCtx.stencilBitMask;
        final boolean caching = isCachingEnabled();
        boolean needsUpdate = !caching;
        if (!needsUpdate) {
            if (face == GL11.GL_FRONT || face == GL11.GL_FRONT_AND_BACK) {
                needsUpdate = glCtx.stencilState.getFuncFront() != func || glCtx.stencilState.getRefFront() != ref || glCtx.stencilState.getValueMaskFront() != clampedMask;
            }
            if (!needsUpdate && (face == GL11.GL_BACK || face == GL11.GL_FRONT_AND_BACK)) {
                needsUpdate = glCtx.stencilState.getFuncBack() != func || glCtx.stencilState.getRefBack() != ref || glCtx.stencilState.getValueMaskBack() != clampedMask;
            }
        }
        if (needsUpdate) {
            if (face == GL11.GL_FRONT || face == GL11.GL_FRONT_AND_BACK) {
                glCtx.stencilState.setFuncFront(func);
                glCtx.stencilState.setRefFront(ref);
                glCtx.stencilState.setValueMaskFront(clampedMask);
            }
            if (face == GL11.GL_BACK || face == GL11.GL_FRONT_AND_BACK) {
                glCtx.stencilState.setFuncBack(func);
                glCtx.stencilState.setRefBack(ref);
                glCtx.stencilState.setValueMaskBack(clampedMask);
            }
            RENDER_BACKEND.stencilFuncSeparate(face, func, ref, clampedMask);
        }
    }

    public static void glStencilMaskSeparate(int face, int mask) {
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordStencilMaskSeparate(face, mask);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final int clampedMask = mask & glCtx.stencilBitMask;
        final boolean caching = isCachingEnabled();
        boolean needsUpdate = !caching;
        if (!needsUpdate) {
            if (face == GL11.GL_FRONT || face == GL11.GL_FRONT_AND_BACK) {
                needsUpdate = glCtx.stencilState.getWriteMaskFront() != clampedMask;
            }
            if (!needsUpdate && (face == GL11.GL_BACK || face == GL11.GL_FRONT_AND_BACK)) {
                needsUpdate = glCtx.stencilState.getWriteMaskBack() != clampedMask;
            }
        }
        if (needsUpdate) {
            if (face == GL11.GL_FRONT || face == GL11.GL_FRONT_AND_BACK) {
                glCtx.stencilState.setWriteMaskFront(clampedMask);
            }
            if (face == GL11.GL_BACK || face == GL11.GL_FRONT_AND_BACK) {
                glCtx.stencilState.setWriteMaskBack(clampedMask);
            }
            RENDER_BACKEND.stencilMaskSeparate(face, clampedMask);
        }
    }

    public static void glStencilOpSeparate(int face, int sfail, int dpfail, int dppass) {
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordStencilOpSeparate(face, sfail, dpfail, dppass);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();
        boolean needsUpdate = !caching;
        if (!needsUpdate) {
            if (face == GL11.GL_FRONT || face == GL11.GL_FRONT_AND_BACK) {
                needsUpdate = glCtx.stencilState.getFailOpFront() != sfail || glCtx.stencilState.getZFailOpFront() != dpfail || glCtx.stencilState.getZPassOpFront() != dppass;
            }
            if (!needsUpdate && (face == GL11.GL_BACK || face == GL11.GL_FRONT_AND_BACK)) {
                needsUpdate = glCtx.stencilState.getFailOpBack() != sfail || glCtx.stencilState.getZFailOpBack() != dpfail || glCtx.stencilState.getZPassOpBack() != dppass;
            }
        }
        if (needsUpdate) {
            if (face == GL11.GL_FRONT || face == GL11.GL_FRONT_AND_BACK) {
                glCtx.stencilState.setFailOpFront(sfail);
                glCtx.stencilState.setZFailOpFront(dpfail);
                glCtx.stencilState.setZPassOpFront(dppass);
            }
            if (face == GL11.GL_BACK || face == GL11.GL_FRONT_AND_BACK) {
                glCtx.stencilState.setFailOpBack(sfail);
                glCtx.stencilState.setZFailOpBack(dpfail);
                glCtx.stencilState.setZPassOpBack(dppass);
            }
            RENDER_BACKEND.stencilOpSeparate(face, sfail, dpfail, dppass);
        }
    }

    public static void glDeleteBuffers(int buffer) {
        if (buffer == 0) return;
        invalidateDeletedBuffer(buffer);
        RENDER_BACKEND.deleteBuffers(buffer);
    }

    public static void glDeleteSync(long sync) {
        RENDER_BACKEND.deleteSync(sync);
    }

    public static long glFenceSync(int condition, int flags) { return RENDER_BACKEND.fenceSync(condition, flags); }
    public static int glClientWaitSync(long sync, int flags, long timeout) { return RENDER_BACKEND.clientWaitSync(sync, flags, timeout); }
    public static void glWaitSync(long sync, int flags, long timeout) { RENDER_BACKEND.waitSync(sync, flags, timeout); }
    public static int glGetSynci(long sync, int pname, IntBuffer length) { return RENDER_BACKEND.getSynci(sync, pname, length); }

    public static void glDeleteBuffers(IntBuffer buffers) {
        for (int i = buffers.position(); i < buffers.limit(); i++) {
            invalidateDeletedBuffer(buffers.get(i));
        }
        RENDER_BACKEND.deleteBuffers(buffers);
    }

    public static void glDeleteBuffers(int[] buffers) {
        for (int buffer : buffers) {
            glDeleteBuffers(buffer);
        }
    }

    private static void invalidateDeletedBuffer(int buffer) {
        final GLContextState glCtx = ctx();
        if (buffer == 0) return;
        final boolean locked = acquireDrawLock();
        try {
            if (glCtx.boundVBO == buffer) glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            if (VAOManager.boundEBO == buffer) glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
            if (glCtx.boundPixelUnpackBuffer == buffer) glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            if (glCtx.boundPixelPackBuffer == buffer) glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);

            VAOManager.onDeleteBuffer(buffer);
        } finally {
            if (locked) releaseDrawLock();
        }
    }

    public static void glBindBuffer(int target, int buffer) {
        final GLContextState glCtx = ctx();
        final boolean locked = acquireDrawLock();
        try {
            if (target == GL15.GL_ARRAY_BUFFER) {
                // if (boundVBO == buffer) return; TODO figure out why this breaks switching async occlusion mode
                glCtx.boundVBO = buffer;
            } else if (target == GL15.GL_ELEMENT_ARRAY_BUFFER) {
                VAOManager.onBindEBO(buffer);
            } else if (target == GL21.GL_PIXEL_UNPACK_BUFFER) {
                glCtx.boundPixelUnpackBuffer = buffer;
            } else if (target == GL21.GL_PIXEL_PACK_BUFFER) {
                glCtx.boundPixelPackBuffer = buffer;
            }
            RENDER_BACKEND.bindBuffer(target, buffer);
        } finally {
            if (locked) releaseDrawLock();
        }
    }

    public static void forcePixelUnpackState(PixelUnpackState target) {
        PixelUnpackState.applyDiff(ctx().pixelUnpackState, target);
    }

    public static void restorePixelUnpackState(PixelUnpackState applied) {
        PixelUnpackState.applyDiff(applied, ctx().pixelUnpackState);
    }

    private static boolean warnedUntrackedPixelBuffer;

    private static void warnUntrackedPixelBuffer(int binding, String name) {
        if (warnedUntrackedPixelBuffer || initConfig == null || !initConfig.isLwjglDebug()) return;
        final int actual = RENDER_BACKEND.getInteger(binding);
        if (actual == 0) return;
        warnedUntrackedPixelBuffer = true;
        LOGGER.warn("{} is bound to {} but GLSM's cache says 0; a call site bypassed GLStateManager.glBindBuffer", name, actual, new Throwable());
    }

    static void suspendPixelUnpackBuffer() {
        if (ctx().boundPixelUnpackBuffer != 0) {
            RENDER_BACKEND.bindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
        } else {
            warnUntrackedPixelBuffer(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING, "GL_PIXEL_UNPACK_BUFFER");
        }
    }

    static void restorePixelUnpackBuffer() {
        final GLContextState glCtx = ctx();
        if (glCtx.boundPixelUnpackBuffer != 0) {
            RENDER_BACKEND.bindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, glCtx.boundPixelUnpackBuffer);
        }
    }

    static void suspendPixelPackBuffer() {
        if (ctx().boundPixelPackBuffer != 0) {
            RENDER_BACKEND.bindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
        } else {
            warnUntrackedPixelBuffer(GL21.GL_PIXEL_PACK_BUFFER_BINDING, "GL_PIXEL_PACK_BUFFER");
        }
    }

    static void restorePixelPackBuffer() {
        final GLContextState glCtx = ctx();
        if (glCtx.boundPixelPackBuffer != 0) {
            RENDER_BACKEND.bindBuffer(GL21.GL_PIXEL_PACK_BUFFER, glCtx.boundPixelPackBuffer);
        }
    }

    public static int glGenBuffers() {
        return RENDER_BACKEND.genBuffers();
    }

    public static void glGenBuffers(IntBuffer buffers) {
        for (int i = buffers.position(); i < buffers.limit(); i++) buffers.put(i, RENDER_BACKEND.genBuffers());
    }

    public static void glGenBuffers(int[] buffers) {
        for (int i = 0; i < buffers.length; i++) buffers[i] = RENDER_BACKEND.genBuffers();
    }

    public static int glCreateBuffers() {
        return RENDER_BACKEND.createBuffers();
    }

    public static void glCreateBuffers(IntBuffer buffers) {
        for (int i = buffers.position(); i < buffers.limit(); i++) buffers.put(i, RENDER_BACKEND.createBuffers());
    }

    public static void glCreateBuffers(int[] buffers) {
        for (int i = 0; i < buffers.length; i++) buffers[i] = RENDER_BACKEND.createBuffers();
    }

    public static void glBufferData(int target, long size, int usage) { RENDER_BACKEND.bufferData(target, size, usage); }
    public static void glBufferData(int target, ByteBuffer data, int usage) { RENDER_BACKEND.bufferData(target, data, usage); }
    public static void glBufferData(int target, ShortBuffer data, int usage) { RENDER_BACKEND.bufferData(target, data, usage); }
    public static void glBufferData(int target, IntBuffer data, int usage) { RENDER_BACKEND.bufferData(target, data, usage); }
    public static void glBufferData(int target, FloatBuffer data, int usage) { RENDER_BACKEND.bufferData(target, data, usage); }
    public static void glBufferData(int target, DoubleBuffer data, int usage) { RENDER_BACKEND.bufferData(target, data, usage); }
    public static void glBufferData(int target, LongBuffer data, int usage) { glBufferData(target, MemoryUtilities.memByteBuffer(data), usage); }
    public static void glBufferData(int target, int[] data, int usage) { RENDER_BACKEND.bufferData(target, data, usage); }
    public static void glBufferData(int target, float[] data, int usage) { RENDER_BACKEND.bufferData(target, data, usage); }
    public static void glBufferData(int target, short[] data, int usage) {
        final ByteBuffer copy = copyOf(data);
        glBufferData(target, copy, usage);
        MemoryUtilities.memFree(copy);
    }
    public static void glBufferData(int target, long[] data, int usage) {
        final ByteBuffer copy = copyOf(data);
        glBufferData(target, copy, usage);
        MemoryUtilities.memFree(copy);
    }
    public static void glBufferData(int target, double[] data, int usage) {
        final ByteBuffer copy = copyOf(data);
        glBufferData(target, copy, usage);
        MemoryUtilities.memFree(copy);
    }

    public static void nglBufferData(int target, long size, long data, int usage) {
        if (data == 0L) {
            glBufferData(target, size, usage);
            return;
        }
        glBufferData(target, MemoryUtilities.memByteBuffer(data, checkedSize(size)), usage);
    }

    public static void glBufferSubData(int target, long offset, ByteBuffer data) { RENDER_BACKEND.bufferSubData(target, offset, data); }
    public static void glBufferSubData(int target, long offset, ShortBuffer data) { RENDER_BACKEND.bufferSubData(target, offset, data); }
    public static void glBufferSubData(int target, long offset, IntBuffer data) { RENDER_BACKEND.bufferSubData(target, offset, data); }
    public static void glBufferSubData(int target, long offset, FloatBuffer data) { RENDER_BACKEND.bufferSubData(target, offset, data); }
    public static void glBufferSubData(int target, long offset, DoubleBuffer data) { RENDER_BACKEND.bufferSubData(target, offset, data); }
    public static void glBufferSubData(int target, long offset, LongBuffer data) { glBufferSubData(target, offset, MemoryUtilities.memByteBuffer(data)); }
    public static void glBufferSubData(int target, long offset, short[] data) {
        final ByteBuffer copy = copyOf(data);
        glBufferSubData(target, offset, copy);
        MemoryUtilities.memFree(copy);
    }
    public static void glBufferSubData(int target, long offset, int[] data) {
        final ByteBuffer copy = copyOf(data);
        glBufferSubData(target, offset, copy);
        MemoryUtilities.memFree(copy);
    }
    public static void glBufferSubData(int target, long offset, long[] data) {
        final ByteBuffer copy = copyOf(data);
        glBufferSubData(target, offset, copy);
        MemoryUtilities.memFree(copy);
    }
    public static void glBufferSubData(int target, long offset, float[] data) {
        final ByteBuffer copy = copyOf(data);
        glBufferSubData(target, offset, copy);
        MemoryUtilities.memFree(copy);
    }
    public static void glBufferSubData(int target, long offset, double[] data) {
        final ByteBuffer copy = copyOf(data);
        glBufferSubData(target, offset, copy);
        MemoryUtilities.memFree(copy);
    }

    public static void nglBufferSubData(int target, long offset, long size, long data) { glBufferSubData(target, offset, MemoryUtilities.memByteBuffer(data, checkedSize(size))); }

    public static ByteBuffer glMapBuffer(int target, int access) { return RENDER_BACKEND.mapBuffer(target, access); }
    public static ByteBuffer glMapBuffer(int target, int access, ByteBuffer old_buffer) { return glMapBuffer(target, access); }
    public static ByteBuffer glMapBuffer(int target, int access, long length, ByteBuffer old_buffer) { return RENDER_BACKEND.mapBuffer(target, access, length, old_buffer); }
    public static ByteBuffer glMapBufferRange(int target, long offset, long length, int access) { return RENDER_BACKEND.mapBufferRange(target, offset, length, access); }
    public static ByteBuffer glMapBufferRange(int target, long offset, long length, int access, ByteBuffer old_buffer) { return glMapBufferRange(target, offset, length, access); }
    public static void glFlushMappedBufferRange(int target, long offset, long length) { RENDER_BACKEND.flushMappedBufferRange(target, offset, length); }
    public static boolean glUnmapBuffer(int target) { return RENDER_BACKEND.unmapBuffer(target); }

    public static void glGetBufferSubData(int target, long offset, ByteBuffer data) { RENDER_BACKEND.getBufferSubData(target, offset, data); }
    public static void glGetBufferSubData(int target, long offset, ShortBuffer data) { RENDER_BACKEND.getBufferSubData(target, offset, data); }
    public static void glGetBufferSubData(int target, long offset, IntBuffer data) { RENDER_BACKEND.getBufferSubData(target, offset, data); }
    public static void glGetBufferSubData(int target, long offset, FloatBuffer data) { RENDER_BACKEND.getBufferSubData(target, offset, data); }
    public static void glGetBufferSubData(int target, long offset, DoubleBuffer data) { RENDER_BACKEND.getBufferSubData(target, offset, data); }
    public static void glGetBufferSubData(int target, long offset, LongBuffer data) { glGetBufferSubData(target, offset, MemoryUtilities.memByteBuffer(data)); }
    public static void glGetBufferSubData(int target, long offset, short[] data) {
        final ByteBuffer copy = MemoryUtilities.memAlloc(data.length << 1);
        glGetBufferSubData(target, offset, copy);
        copy.asShortBuffer().get(data);
        MemoryUtilities.memFree(copy);
    }
    public static void glGetBufferSubData(int target, long offset, int[] data) {
        final ByteBuffer copy = MemoryUtilities.memAlloc(data.length << 2);
        glGetBufferSubData(target, offset, copy);
        copy.asIntBuffer().get(data);
        MemoryUtilities.memFree(copy);
    }
    public static void glGetBufferSubData(int target, long offset, long[] data) {
        final ByteBuffer copy = MemoryUtilities.memAlloc(data.length << 3);
        glGetBufferSubData(target, offset, copy);
        copy.asLongBuffer().get(data);
        MemoryUtilities.memFree(copy);
    }
    public static void glGetBufferSubData(int target, long offset, float[] data) {
        final ByteBuffer copy = MemoryUtilities.memAlloc(data.length << 2);
        glGetBufferSubData(target, offset, copy);
        copy.asFloatBuffer().get(data);
        MemoryUtilities.memFree(copy);
    }
    public static void glGetBufferSubData(int target, long offset, double[] data) {
        final ByteBuffer copy = MemoryUtilities.memAlloc(data.length << 3);
        glGetBufferSubData(target, offset, copy);
        copy.asDoubleBuffer().get(data);
        MemoryUtilities.memFree(copy);
    }

    public static void nglGetBufferSubData(int target, long offset, long size, long data) { glGetBufferSubData(target, offset, MemoryUtilities.memByteBuffer(data, checkedSize(size))); }

    public static int glGetBufferParameteri(int target, int pname) { return RENDER_BACKEND.getBufferParameteri(target, pname); }
    public static int glGetBufferParameter(int target, int pname) { return glGetBufferParameteri(target, pname); }
    public static void glGetBufferParameter(int target, int pname, IntBuffer params) { params.put(params.position(), glGetBufferParameteri(target, pname)); }
    public static void glGetBufferParameteriv(int target, int pname, IntBuffer params) { params.put(params.position(), glGetBufferParameteri(target, pname)); }
    public static void glGetBufferParameteriv(int target, int pname, int[] params) { params[0] = glGetBufferParameteri(target, pname); }

    public static void glBufferStorage(int target, long size, int flags) { RENDER_BACKEND.bufferStorage(target, size, flags); }
    public static void glBufferStorage(int target, ByteBuffer data, int flags) { RENDER_BACKEND.bufferStorage(target, data, flags); }
    public static void glBufferStorage(int target, ShortBuffer data, int flags) { glBufferStorage(target, MemoryUtilities.memByteBuffer(data), flags); }
    public static void glBufferStorage(int target, IntBuffer data, int flags) { glBufferStorage(target, MemoryUtilities.memByteBuffer(data), flags); }
    public static void glBufferStorage(int target, FloatBuffer data, int flags) { glBufferStorage(target, MemoryUtilities.memByteBuffer(data), flags); }
    public static void glBufferStorage(int target, DoubleBuffer data, int flags) { glBufferStorage(target, MemoryUtilities.memByteBuffer(data), flags); }
    public static void glBufferStorage(int target, short[] data, int flags) {
        final ByteBuffer copy = copyOf(data);
        glBufferStorage(target, copy, flags);
        MemoryUtilities.memFree(copy);
    }
    public static void glBufferStorage(int target, int[] data, int flags) {
        final ByteBuffer copy = copyOf(data);
        glBufferStorage(target, copy, flags);
        MemoryUtilities.memFree(copy);
    }
    public static void glBufferStorage(int target, float[] data, int flags) {
        final ByteBuffer copy = copyOf(data);
        glBufferStorage(target, copy, flags);
        MemoryUtilities.memFree(copy);
    }
    public static void glBufferStorage(int target, double[] data, int flags) {
        final ByteBuffer copy = copyOf(data);
        glBufferStorage(target, copy, flags);
        MemoryUtilities.memFree(copy);
    }

    public static void glNamedBufferData(int buffer, long size, int usage) { RENDER_BACKEND.namedBufferData(buffer, size, usage); }
    public static void glNamedBufferData(int buffer, ByteBuffer data, int usage) { RENDER_BACKEND.namedBufferData(buffer, data, usage); }
    public static void glNamedBufferData(int buffer, FloatBuffer data, int usage) { RENDER_BACKEND.namedBufferData(buffer, data, usage); }
    public static void glNamedBufferData(int buffer, ShortBuffer data, int usage) { glNamedBufferData(buffer, MemoryUtilities.memByteBuffer(data), usage); }
    public static void glNamedBufferData(int buffer, IntBuffer data, int usage) { glNamedBufferData(buffer, MemoryUtilities.memByteBuffer(data), usage); }
    public static void glNamedBufferData(int buffer, LongBuffer data, int usage) { glNamedBufferData(buffer, MemoryUtilities.memByteBuffer(data), usage); }
    public static void glNamedBufferData(int buffer, DoubleBuffer data, int usage) { glNamedBufferData(buffer, MemoryUtilities.memByteBuffer(data), usage); }
    public static void glNamedBufferData(int buffer, short[] data, int usage) {
        final ByteBuffer copy = copyOf(data);
        glNamedBufferData(buffer, copy, usage);
        MemoryUtilities.memFree(copy);
    }
    public static void glNamedBufferData(int buffer, int[] data, int usage) {
        final ByteBuffer copy = copyOf(data);
        glNamedBufferData(buffer, copy, usage);
        MemoryUtilities.memFree(copy);
    }
    public static void glNamedBufferData(int buffer, long[] data, int usage) {
        final ByteBuffer copy = copyOf(data);
        glNamedBufferData(buffer, copy, usage);
        MemoryUtilities.memFree(copy);
    }
    public static void glNamedBufferData(int buffer, float[] data, int usage) {
        final ByteBuffer copy = copyOf(data);
        glNamedBufferData(buffer, copy, usage);
        MemoryUtilities.memFree(copy);
    }
    public static void glNamedBufferData(int buffer, double[] data, int usage) {
        final ByteBuffer copy = copyOf(data);
        glNamedBufferData(buffer, copy, usage);
        MemoryUtilities.memFree(copy);
    }

    public static void glNamedBufferSubData(int buffer, long offset, ByteBuffer data) { RENDER_BACKEND.namedBufferSubData(buffer, offset, data); }
    public static void glNamedBufferSubData(int buffer, long offset, ShortBuffer data) { glNamedBufferSubData(buffer, offset, MemoryUtilities.memByteBuffer(data)); }
    public static void glNamedBufferSubData(int buffer, long offset, IntBuffer data) { glNamedBufferSubData(buffer, offset, MemoryUtilities.memByteBuffer(data)); }
    public static void glNamedBufferSubData(int buffer, long offset, LongBuffer data) { glNamedBufferSubData(buffer, offset, MemoryUtilities.memByteBuffer(data)); }
    public static void glNamedBufferSubData(int buffer, long offset, FloatBuffer data) { glNamedBufferSubData(buffer, offset, MemoryUtilities.memByteBuffer(data)); }
    public static void glNamedBufferSubData(int buffer, long offset, DoubleBuffer data) { glNamedBufferSubData(buffer, offset, MemoryUtilities.memByteBuffer(data)); }
    public static void glNamedBufferSubData(int buffer, long offset, short[] data) {
        final ByteBuffer copy = copyOf(data);
        glNamedBufferSubData(buffer, offset, copy);
        MemoryUtilities.memFree(copy);
    }
    public static void glNamedBufferSubData(int buffer, long offset, int[] data) {
        final ByteBuffer copy = copyOf(data);
        glNamedBufferSubData(buffer, offset, copy);
        MemoryUtilities.memFree(copy);
    }
    public static void glNamedBufferSubData(int buffer, long offset, long[] data) {
        final ByteBuffer copy = copyOf(data);
        glNamedBufferSubData(buffer, offset, copy);
        MemoryUtilities.memFree(copy);
    }
    public static void glNamedBufferSubData(int buffer, long offset, float[] data) {
        final ByteBuffer copy = copyOf(data);
        glNamedBufferSubData(buffer, offset, copy);
        MemoryUtilities.memFree(copy);
    }
    public static void glNamedBufferSubData(int buffer, long offset, double[] data) {
        final ByteBuffer copy = copyOf(data);
        glNamedBufferSubData(buffer, offset, copy);
        MemoryUtilities.memFree(copy);
    }

    public static void glClearBufferSubData(int target, int internalFormat, long offset, long size, int format, int type, ByteBuffer data) { RENDER_BACKEND.clearBufferSubData(target, internalFormat, offset, size, format, type, data); }
    public static void glClearBufferData(int target, int internalformat, int format, int type, ByteBuffer data) { RENDER_BACKEND.clearBufferData(target, internalformat, format, type, data); }
    public static boolean glIsBuffer(int buffer) { return RENDER_BACKEND.isBuffer(buffer); }
    public static boolean glIsTexture(int texture) { return RENDER_BACKEND.isTexture(texture); }
    public static boolean glIsFramebuffer(int framebuffer) { return RENDER_BACKEND.isFramebuffer(framebuffer); }
    public static boolean glIsRenderbuffer(int renderbuffer) { return RENDER_BACKEND.isRenderbuffer(renderbuffer); }
    public static boolean glIsSampler(int sampler) { return RENDER_BACKEND.isSampler(sampler); }
    public static boolean glIsQuery(int query) { return RENDER_BACKEND.isQuery(query); }
    public static void glCopyBufferSubData(int readTarget, int writeTarget, long readOffset, long writeOffset, long size) { RENDER_BACKEND.copyBufferSubData(readTarget, writeTarget, readOffset, writeOffset, size); }

    private static int checkedSize(long size) {
        if (size < 0 || size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Buffer range does not fit a ByteBuffer wrapper: " + size);
        }
        return (int) size;
    }

    private static ByteBuffer copyOf(short[] data) {
        final ByteBuffer bb = MemoryUtilities.memAlloc(data.length << 1);
        bb.asShortBuffer().put(data);
        return bb;
    }

    private static ByteBuffer copyOf(int[] data) {
        final ByteBuffer bb = MemoryUtilities.memAlloc(data.length << 2);
        bb.asIntBuffer().put(data);
        return bb;
    }

    private static ByteBuffer copyOf(long[] data) {
        final ByteBuffer bb = MemoryUtilities.memAlloc(data.length << 3);
        bb.asLongBuffer().put(data);
        return bb;
    }

    private static ByteBuffer copyOf(float[] data) {
        final ByteBuffer bb = MemoryUtilities.memAlloc(data.length << 2);
        bb.asFloatBuffer().put(data);
        return bb;
    }

    private static ByteBuffer copyOf(double[] data) {
        final ByteBuffer bb = MemoryUtilities.memAlloc(data.length << 3);
        bb.asDoubleBuffer().put(data);
        return bb;
    }

    public static void glBindVertexArray(int array) {
        final GLContextState glCtx = ctx();
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordBindVAO(array);
            // Since the vao needs to be bound to do stuff like state setup & data upload, it'll still execute the bind call.
            // This is technically wrong, but I'm not sure if there's a better solution here.
        }
        if (array == 0) {
            array = defaultVAO;
        }
        final boolean locked = acquireDrawLock();
        try {
            if (glCtx.boundVAO != array) {
                glCtx.boundVAO = array;
                VAOManager.onBindVertexArrayPre(array);
                RENDER_BACKEND.bindVertexArray(array);
            }
        } finally {
            if (locked) releaseDrawLock();
        }
    }

    public static void glDeleteVertexArrays(int array) {
        final GLContextState glCtx = ctx();
        final boolean locked = acquireDrawLock();
        try {
            VAOManager.onDeleteVertexArray(array);
            if (array == glCtx.boundVAO) {
                // Deleting the bound VAO implicitly unbinds it. Rebind the default VAO.
                glCtx.boundVAO = defaultVAO;
                VAOManager.onBindVertexArrayPre(defaultVAO);
                RENDER_BACKEND.bindVertexArray(defaultVAO);
            }
            RENDER_BACKEND.deleteVertexArrays(array);
        } finally {
            if (locked) releaseDrawLock();
        }
    }

    public static int glGenVertexArrays() {
        return RENDER_BACKEND.genVertexArrays();
    }

    public static boolean glIsVertexArray(int array) {
        return RENDER_BACKEND.isVertexArray(array);
    }

    public static void glBindVertexArrayAPPLE(int array) {
        glBindVertexArray(array);
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

    public static void glBindFramebuffer(int target, int framebuffer) {
        final GLContextState glCtx = ctx();
        if (target == GL30.GL_FRAMEBUFFER) {
            if (glCtx.drawFramebuffer == framebuffer && glCtx.readFramebuffer == framebuffer) return;
            if (glCtx.drawFramebuffer != framebuffer) glCtx.drawFramebufferGeneration++;
            glCtx.drawFramebuffer = framebuffer;
            glCtx.readFramebuffer = framebuffer;
        } else if (target == GL30.GL_DRAW_FRAMEBUFFER) {
            if (glCtx.drawFramebuffer == framebuffer) return;
            glCtx.drawFramebufferGeneration++;
            glCtx.drawFramebuffer = framebuffer;
        } else if (target == GL30.GL_READ_FRAMEBUFFER) {
            if (glCtx.readFramebuffer == framebuffer) return;
            glCtx.readFramebuffer = framebuffer;
        }
        RENDER_BACKEND.bindFramebuffer(target, framebuffer);
    }

    public static void glDeleteFramebuffers(int framebuffer) {
        final GLContextState glCtx = ctx();
        if (glCtx.drawFramebuffer == framebuffer) { glCtx.drawFramebuffer = 0; glCtx.drawFramebufferGeneration++; }
        if (glCtx.readFramebuffer == framebuffer) glCtx.readFramebuffer = 0;
        RENDER_BACKEND.deleteFramebuffers(framebuffer);
    }

    public static int glGenFramebuffers() {
        return RENDER_BACKEND.genFramebuffers();
    }

    public static int glCheckFramebufferStatus(int target) {
        return RENDER_BACKEND.checkFramebufferStatus(target);
    }

    public static int glGetFramebufferAttachmentParameteri(int target, int attachment, int pname) {
        return RENDER_BACKEND.getFramebufferAttachmentParameteri(target, attachment, pname);
    }

    public static void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        RENDER_BACKEND.framebufferTexture2D(target, attachment, textarget, texture, level);
    }

    public static void glFramebufferTexture(int target, int attachment, int texture, int level) { RENDER_BACKEND.framebufferTexture(target, attachment, texture, level); }

    public static int glGenRenderbuffers() { return RENDER_BACKEND.genRenderbuffers(); }
    public static void glDeleteRenderbuffers(int renderbuffer) { RENDER_BACKEND.deleteRenderbuffers(renderbuffer); }
    public static void glBindRenderbuffer(int target, int renderbuffer) { RENDER_BACKEND.bindRenderbuffer(target, renderbuffer); }
    public static void glRenderbufferStorage(int target, int internalformat, int width, int height) { RENDER_BACKEND.renderbufferStorage(target, internalformat, width, height); }
    public static void glRenderbufferStorageMultisample(int target, int samples, int internalformat, int width, int height) { RENDER_BACKEND.renderbufferStorageMultisample(target, samples, internalformat, width, height); }
    public static void glFramebufferRenderbuffer(int target, int attachment, int renderbuffertarget, int renderbuffer) { RENDER_BACKEND.framebufferRenderbuffer(target, attachment, renderbuffertarget, renderbuffer); }

    public static void glGenQueries(IntBuffer ids) { RENDER_BACKEND.genQueries(ids); }
    public static int glGenQueries() { return RENDER_BACKEND.genQueries(); }
    public static void glDeleteQueries(int id) { RENDER_BACKEND.deleteQueries(id); }
    public static void glBeginQuery(int target, int id) { RENDER_BACKEND.beginQuery(target, id); }
    public static void glEndQuery(int target) { RENDER_BACKEND.endQuery(target); }
    public static void glGetQueryObjectui(int id, int pname, IntBuffer params) { RENDER_BACKEND.getQueryObjectui(id, pname, params); }
    public static int glGetQueryObjecti(int id, int pname) { return RENDER_BACKEND.getQueryObjecti(id, pname); }

    public static void glBindBufferBase(int target, int index, int buffer) { RENDER_BACKEND.bindBufferBase(target, index, buffer); }
    public static int glGetUniformBlockIndex(int program, CharSequence name) { return RENDER_BACKEND.getUniformBlockIndex(program, name); }
    public static void glUniformBlockBinding(int program, int blockIndex, int binding) { RENDER_BACKEND.uniformBlockBinding(program, blockIndex, binding); }
    public static void glBindFragDataLocation(int program, int colorNumber, CharSequence name) { RENDER_BACKEND.bindFragDataLocation(program, colorNumber, name); }
    public static void glQueryCounter(int id, int target) { RENDER_BACKEND.queryCounter(id, target); }
    public static long glGetQueryObjectui64(int id, int pname) { return RENDER_BACKEND.getQueryObjectui64(id, pname); }
    public static void glUniform3(int location, float[] values) { RENDER_BACKEND.uniform3fv(location, values); }
    public static void glUniform4(int location, float[] values) { RENDER_BACKEND.uniform4fv(location, values); }

    public static void glGetInteger(int pname, int[] params) {
        if (!isCachingEnabled() || params.length == 0) {
            RENDER_BACKEND.getInteger(pname, params);
            return;
        }

        switch (pname) {
            case GL11.GL_VIEWPORT -> {
                if (params.length < 4) {
                    RENDER_BACKEND.getInteger(pname, params);
                } else {
                    ctx().viewportState.get(params);
                }
            }
            case GL11.GL_POLYGON_MODE -> {
                final PolygonState polygon = ctx().polygonState;
                params[0] = polygon.getFrontMode();
                if (params.length > 1) params[1] = polygon.getBackMode();
            }
            default -> {
                if (!HAS_MULTIPLE_SET.contains(pname)) {
                    params[0] = glGetInteger(pname);
                } else {
                    RENDER_BACKEND.getInteger(pname, params);
                }
            }
        }
    }
    public static long nglMapBuffer(int target, int access) {
        final ByteBuffer buf = RENDER_BACKEND.mapBuffer(target, access);
        return buf == null ? 0L : MemoryUtilities.memAddress0(buf);
    }
    public static void nglMultiDrawElementsBaseVertex(int mode, long count, int type, long indices, int primcount, long basevertex) {
        RENDER_BACKEND.multiDrawElementsBaseVertex(mode, count, type, indices, primcount, basevertex);
    }

    public static void glObjectLabel(int identifier, int name, CharSequence label) { RENDER_BACKEND.objectLabel(identifier, name, label); }
    public static String glGetObjectLabel(int identifier, int name, int bufSize) { return RENDER_BACKEND.getObjectLabel(identifier, name); }
    public static void glPushDebugGroup(int source, int id, CharSequence message) { RENDER_BACKEND.pushDebugGroup(source, id, message); }
    public static void glPopDebugGroup() { RENDER_BACKEND.popDebugGroup(); }
    public static void glDebugMessageInsert(int source, int type, int id, int severity, CharSequence buf) { RENDER_BACKEND.debugMessageInsert(source, type, id, severity, buf); }
    public static void glDebugMessageControl(int source, int type, int severity, IntBuffer ids, boolean enabled) { RENDER_BACKEND.debugMessageControl(source, type, severity, ids, enabled); }

    public static int glGetDebugMessageLog(int count, IntBuffer sources, IntBuffer types, IntBuffer ids, IntBuffer severities, IntBuffer lengths, ByteBuffer messageLog) {
        return RENDER_BACKEND.getDebugMessageLog(count, sources, types, ids, severities, lengths, messageLog);
    }

    public static void registerDebugMessageListener(GLDebugMessageListener listener, long userParam) {
        RENDER_BACKEND.debugMessageCallback(listener, userParam);
    }

    public static void glDebugMessageCallback(KHRDebugCallback callback) {
        RENDER_BACKEND.debugMessageCallback(GLDebug.adaptDebugCallback(callback), 0L);
    }

    public static void glGenerateMipmap(int target) {
        RENDER_BACKEND.generateMipmap(target);
    }

    public static void glBlitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        RENDER_BACKEND.blitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }

    public static void setActiveTexture(int textureUnit) {
        glActiveTexture(textureUnit);
    }

    public static void glMultiTexCoord2f(int target, float s, float t) {
        final GLContextState glCtx = ctx();
        if (target == GL13.GL_TEXTURE0) {
            if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
                ImmediateModeRecorder.setTexCoord(s, t);
                return;
            }
            ShaderManager.setCurrentTexCoord(s, t, 0.0f, 1.0f);
            glCtx.dirtyTexCoordAttrib = true;
        } else if (target == GL13.GL_TEXTURE1) {
            if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
                ImmediateModeRecorder.setLightmapCoord(s, t);
                return;
            }
            setLightmapTextureCoords(target, s, t);
        } else {
            final int unit = target - GL13.GL_TEXTURE0;
            if (unit >= 2 && unit < 4) {
                if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
                    glCtx.unit23TexCoordSetDuringDraw = true;
                }
                ShaderManager.setCurrentTexCoord(unit, s, t, 0.0f, 1.0f);
            }
        }
    }

    public static void glMultiTexCoord2d(int target, double s, double t) {
        glMultiTexCoord2f(target, (float) s, (float) t);
    }

    public static void glMultiTexCoord2s(int target, short s, short t) {
        glMultiTexCoord2f(target, s, t);
    }

    public static void setLightmapTextureCoords(int unit, float x, float y) {
        if (unit == GL13.GL_TEXTURE1) {
            GLSMConfig.lastBrightnessX = x;
            GLSMConfig.lastBrightnessY = y;
            ctx().dirtyLightmapAttrib = true;
            if (GLSMHooks.LIGHTMAP_COORDS.hasListeners()) {
                GLSMHooks.lightmapCoordsEvent.x = x;
                GLSMHooks.lightmapCoordsEvent.y = y;
                GLSMHooks.LIGHTMAP_COORDS.post(GLSMHooks.lightmapCoordsEvent);
            }
        }
    }

    private static void checkMismatch(int cap) {
        final int cached = GLStateManager.glGetInteger(cap);
        final int actual = RENDER_BACKEND.getInteger(cap);
        if (actual != cached) {
            throw new IllegalStateException("GLSM Mismatch! cap=" + cap + " cached=" + cached + " actual=" + actual);
        }
    }

    public static boolean isFramebufferEnabled() {
        return true;
    }


    private static final Set<String> warnedFFPFunctions = new HashSet<>();

    /**
     * Guards against FFP features not supported in core profile.
     * Default: throws UnsupportedOperationException.
     * With {@code -Dangelica.ffp.warnOnUnsupported=true}: warns once per function (diagnostic mode).
     * @return true (always - caller should skip the GL call)
     */
    private static boolean guardUnsupportedFFP(String function, String explanation) {
        if (!SystemProperties.FFP_WARN_ON_UNSUPPORTED) {
            throw new UnsupportedOperationException(function + ": " + explanation);
        }
        if (warnedFFPFunctions.add(function)) {
            LOGGER.warn("{}: {}", function, explanation, new Throwable("Stack trace"));
        }
        return true;
    }

    /**
     * In core profile, GL_CLAMP (0x2900) is removed. Remap to GL_CLAMP_TO_EDGE for wrap modes. Semantically equivalent when texture border is not used (border
     * textures are also removed in core).
     */
    public static int remapTexClamp(int pname, int param) {
        if (param == GL11.GL_CLAMP && (pname == GL11.GL_TEXTURE_WRAP_S || pname == GL11.GL_TEXTURE_WRAP_T || pname == GL12.GL_TEXTURE_WRAP_R)) {
            return GL12.GL_CLAMP_TO_EDGE;
        }
        return param;
    }

    public static float remapTexClamp(int pname, float param) {
        if ((int) param == GL11.GL_CLAMP && (pname == GL11.GL_TEXTURE_WRAP_S || pname == GL11.GL_TEXTURE_WRAP_T || pname == GL12.GL_TEXTURE_WRAP_R)) {
            return (float) GL12.GL_CLAMP_TO_EDGE;
        }
        return param;
    }

    public static void remapTexClampBuffer(int pname, IntBuffer params) {
        if (params.remaining() >= 1) {
            final int val = params.get(params.position());
            final int remapped = remapTexClamp(pname, val);
            if (remapped != val) params.put(params.position(), remapped);
        }
    }

    public static void glTexEnvi(int target, int pname, int param) {
        handleTexEnvScalar(target, pname, param);
    }

    public static void glTexEnvf(int target, int pname, float param) {
        handleTexEnvScalar(target, pname, param);
    }

    public static void glTexEnv(int target, int pname, FloatBuffer params) {
        final GLContextState glCtx = ctx();
        if (target == GL11.GL_TEXTURE_ENV && pname == GL11.GL_TEXTURE_ENV_COLOR && params.remaining() >= 4) {
            final int pos = params.position();
            final var envState = glCtx.textures.getTexEnvState(glCtx.activeTextureUnit.getValue());
            envState.envColorR = params.get(pos);
            envState.envColorG = params.get(pos + 1);
            envState.envColorB = params.get(pos + 2);
            envState.envColorA = params.get(pos + 3);
            glCtx.fragmentGeneration++;
            return;
        }
        if (target == GL14.GL_TEXTURE_FILTER_CONTROL) {
            guardUnsupportedFFP(
                    "glTexEnv",
                    "LOD bias via buffer glTexEnv is not supported; use glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_LOD_BIAS, value) instead.");
            return;
        }
        // Silently ignore other buffer glTexEnv calls (some mods pass combine params via buffers)
    }

    public static void glTexEnv(int target, int pname, IntBuffer params) {
        if (target == GL11.GL_TEXTURE_ENV) {
            handleTexEnvScalar(target, pname, (float) params.get(params.position()));
        }
    }

    /**
     * Scalar glTexEnvi/glTexEnvf dispatch. glTexEnv is removed in core profile. GL_TEXTURE_FILTER_CONTROL (LOD bias) is remapped to glTexParameterf. All
     * GL_TEXTURE_ENV parameters including GL_COMBINE sub-params are tracked in per-unit TexEnvState.
     */
    private static void handleTexEnvScalar(int target, int pname, float param) {
        final GLContextState glCtx = ctx();
        if (target == GL14.GL_TEXTURE_FILTER_CONTROL) {
            // LOD bias via legacy glTexEnv path - remap to core-profile glTexParameterf
            glTexParameterf(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_LOD_BIAS, param);
            return;
        }
        if (target != GL11.GL_TEXTURE_ENV) return;

        final var envState = glCtx.textures.getTexEnvState(glCtx.activeTextureUnit.getValue());
        final int iparam = (int) param;

        switch (pname) {
            case GL11.GL_TEXTURE_ENV_MODE -> {
                envState.mode = iparam;
                glCtx.fragmentGeneration++;
            }
            case GL13.GL_COMBINE_RGB -> {
                envState.combineRgb = iparam;
                glCtx.fragmentGeneration++;
            }
            case GL13.GL_COMBINE_ALPHA -> {
                envState.combineAlpha = iparam;
                glCtx.fragmentGeneration++;
            }
            case GL13.GL_SOURCE0_RGB -> {
                envState.sourceRgb[0] = iparam;
                glCtx.fragmentGeneration++;
            }
            case GL13.GL_SOURCE1_RGB -> {
                envState.sourceRgb[1] = iparam;
                glCtx.fragmentGeneration++;
            }
            case GL13.GL_SOURCE2_RGB -> {
                envState.sourceRgb[2] = iparam;
                glCtx.fragmentGeneration++;
            }
            case GL13.GL_SOURCE0_ALPHA -> {
                envState.sourceAlpha[0] = iparam;
                glCtx.fragmentGeneration++;
            }
            case GL13.GL_SOURCE1_ALPHA -> {
                envState.sourceAlpha[1] = iparam;
                glCtx.fragmentGeneration++;
            }
            case GL13.GL_SOURCE2_ALPHA -> {
                envState.sourceAlpha[2] = iparam;
                glCtx.fragmentGeneration++;
            }
            case GL13.GL_OPERAND0_RGB -> {
                envState.operandRgb[0] = iparam;
                glCtx.fragmentGeneration++;
            }
            case GL13.GL_OPERAND1_RGB -> {
                envState.operandRgb[1] = iparam;
                glCtx.fragmentGeneration++;
            }
            case GL13.GL_OPERAND2_RGB -> {
                envState.operandRgb[2] = iparam;
                glCtx.fragmentGeneration++;
            }
            case GL13.GL_OPERAND0_ALPHA -> {
                envState.operandAlpha[0] = iparam;
                glCtx.fragmentGeneration++;
            }
            case GL13.GL_OPERAND1_ALPHA -> {
                envState.operandAlpha[1] = iparam;
                glCtx.fragmentGeneration++;
            }
            case GL13.GL_OPERAND2_ALPHA -> {
                envState.operandAlpha[2] = iparam;
                glCtx.fragmentGeneration++;
            }
            case GL13.GL_RGB_SCALE -> {
                envState.scaleRgb = param;
                glCtx.fragmentGeneration++;
            }
            case GL11.GL_ALPHA_SCALE -> {
                envState.scaleAlpha = param;
                glCtx.fragmentGeneration++;
            }
            // Silently ignore unknown pname - other mods may pass unrecognized params
        }
    }

    public static float glGetTexEnvf(int target, int pname) {
        return (float) glGetTexEnvi(target, pname);
    }

    public static int glGetTexEnvi(int target, int pname) {
        final GLContextState glCtx = ctx();
        if (target != GL11.GL_TEXTURE_ENV) return 0;

        final var envState = glCtx.textures.getTexEnvState(glCtx.activeTextureUnit.getValue());
        return switch (pname) {
            case GL11.GL_TEXTURE_ENV_MODE -> envState.mode;
            case GL13.GL_COMBINE_RGB -> envState.combineRgb;
            case GL13.GL_COMBINE_ALPHA -> envState.combineAlpha;
            case GL13.GL_SOURCE0_RGB -> envState.sourceRgb[0];
            case GL13.GL_SOURCE1_RGB -> envState.sourceRgb[1];
            case GL13.GL_SOURCE2_RGB -> envState.sourceRgb[2];
            case GL13.GL_SOURCE0_ALPHA -> envState.sourceAlpha[0];
            case GL13.GL_SOURCE1_ALPHA -> envState.sourceAlpha[1];
            case GL13.GL_SOURCE2_ALPHA -> envState.sourceAlpha[2];
            case GL13.GL_OPERAND0_RGB -> envState.operandRgb[0];
            case GL13.GL_OPERAND1_RGB -> envState.operandRgb[1];
            case GL13.GL_OPERAND2_RGB -> envState.operandRgb[2];
            case GL13.GL_OPERAND0_ALPHA -> envState.operandAlpha[0];
            case GL13.GL_OPERAND1_ALPHA -> envState.operandAlpha[1];
            case GL13.GL_OPERAND2_ALPHA -> envState.operandAlpha[2];
            case GL13.GL_RGB_SCALE -> (int) envState.scaleRgb;
            case GL11.GL_ALPHA_SCALE -> (int) envState.scaleAlpha;
            default -> 0;
        };
    }

    public static void glGetTexEnv(int target, int pname, IntBuffer params) {
        final GLContextState glCtx = ctx();
        if (target != GL11.GL_TEXTURE_ENV) return;
        if (pname == GL11.GL_TEXTURE_ENV_COLOR) {
            final var envState = glCtx.textures.getTexEnvState(glCtx.activeTextureUnit.getValue());
            params.put(params.position(), (int) (envState.envColorR * 2147483647.0f));
            params.put(params.position() + 1, (int) (envState.envColorG * 2147483647.0f));
            params.put(params.position() + 2, (int) (envState.envColorB * 2147483647.0f));
            params.put(params.position() + 3, (int) (envState.envColorA * 2147483647.0f));
        } else {
            params.put(params.position(), glGetTexEnvi(target, pname));
        }
    }

    public static void glGetTexEnv(int target, int pname, FloatBuffer params) {
        final GLContextState glCtx = ctx();
        if (target != GL11.GL_TEXTURE_ENV) return;
        if (pname == GL11.GL_TEXTURE_ENV_COLOR) {
            final var envState = glCtx.textures.getTexEnvState(glCtx.activeTextureUnit.getValue());
            params.put(params.position(), envState.envColorR);
            params.put(params.position() + 1, envState.envColorG);
            params.put(params.position() + 2, envState.envColorB);
            params.put(params.position() + 3, envState.envColorA);
        } else {
            params.put(params.position(), (float) glGetTexEnvi(target, pname));
        }
    }


    private static final float[] texGenTempPlane = new float[4];

    public static void glTexGeni(int coord, int pname, int param) {
        final GLContextState glCtx = ctx();
        if (!isCachingEnabled()) return;
        if (pname == GL11.GL_TEXTURE_GEN_MODE) {
            final int unit = glCtx.activeTextureUnit.getValue();
            glCtx.textures.getTexGenState(unit).setMode(coord, param);
            glCtx.texGenGeneration++;
        }
    }

    public static void glTexGenf(int coord, int pname, float param) {
        // Single-float form only valid for GL_TEXTURE_GEN_MODE
        glTexGeni(coord, pname, (int) param);
    }

    public static void glTexGend(int coord, int pname, double param) {
        glTexGeni(coord, pname, (int) param);
    }

    public static void glTexGen(int coord, int pname, FloatBuffer params) {
        final GLContextState glCtx = ctx();
        if (!isCachingEnabled()) return;
        final int unit = glCtx.activeTextureUnit.getValue();
        final var texGenState = glCtx.textures.getTexGenState(unit);
        final int pos = params.position();
        switch (pname) {
            case GL11.GL_TEXTURE_GEN_MODE -> {
                texGenState.setMode(coord, (int) params.get(pos));
                glCtx.texGenGeneration++;
            }
            case GL11.GL_OBJECT_PLANE -> {
                texGenTempPlane[0] = params.get(pos);
                texGenTempPlane[1] = params.get(pos + 1);
                texGenTempPlane[2] = params.get(pos + 2);
                texGenTempPlane[3] = params.get(pos + 3);
                texGenState.setObjectPlane(coord, texGenTempPlane);
                glCtx.texGenGeneration++;
            }
            case GL11.GL_EYE_PLANE -> {
                texGenTempPlane[0] = params.get(pos);
                texGenTempPlane[1] = params.get(pos + 1);
                texGenTempPlane[2] = params.get(pos + 2);
                texGenTempPlane[3] = params.get(pos + 3);
                texGenState.setEyePlane(coord, texGenTempPlane, glCtx.modelViewMatrix);
                glCtx.texGenGeneration++;
            }
        }
    }

    public static void glTexGen(int coord, int pname, DoubleBuffer params) {
        final GLContextState glCtx = ctx();
        if (!isCachingEnabled()) return;
        final int pos = params.position();
        texGenTempPlane[0] = (float) params.get(pos);
        texGenTempPlane[1] = (float) params.get(pos + 1);
        texGenTempPlane[2] = (float) params.get(pos + 2);
        texGenTempPlane[3] = (float) params.get(pos + 3);
        final int unit = glCtx.activeTextureUnit.getValue();
        final var texGenState = glCtx.textures.getTexGenState(unit);
        switch (pname) {
            case GL11.GL_TEXTURE_GEN_MODE -> {
                texGenState.setMode(coord, (int) params.get(pos));
                glCtx.texGenGeneration++;
            }
            case GL11.GL_OBJECT_PLANE -> {
                texGenState.setObjectPlane(coord, texGenTempPlane);
                glCtx.texGenGeneration++;
            }
            case GL11.GL_EYE_PLANE -> {
                texGenState.setEyePlane(coord, texGenTempPlane, glCtx.modelViewMatrix);
                glCtx.texGenGeneration++;
            }
        }
    }

    public static void glTexGen(int coord, int pname, IntBuffer params) {
        final GLContextState glCtx = ctx();
        if (!isCachingEnabled()) return;
        if (pname == GL11.GL_TEXTURE_GEN_MODE) {
            final int unit = glCtx.activeTextureUnit.getValue();
            glCtx.textures.getTexGenState(unit).setMode(coord, params.get(params.position()));
            glCtx.texGenGeneration++;
        }
    }

    public static int glGetTexGeni(int coord, int pname) {
        final GLContextState glCtx = ctx();
        if (pname == GL11.GL_TEXTURE_GEN_MODE) {
            return glCtx.textures.getTexGenState(glCtx.activeTextureUnit.getValue()).getMode(coord);
        }
        return 0;
    }

    public static float glGetTexGenf(int coord, int pname) {
        return (float) glGetTexGeni(coord, pname);
    }

    public static double glGetTexGend(int coord, int pname) {
        return glGetTexGeni(coord, pname);
    }

    public static void glGetTexGen(int coord, int pname, IntBuffer params) {
        if (pname == GL11.GL_TEXTURE_GEN_MODE) {
            params.put(params.position(), glGetTexGeni(coord, pname));
        }
    }

    public static void glGetTexGen(int coord, int pname, FloatBuffer params) {
        final GLContextState glCtx = ctx();
        final var state = glCtx.textures.getTexGenState(glCtx.activeTextureUnit.getValue());
        if (pname == GL11.GL_TEXTURE_GEN_MODE) {
            params.put(params.position(), (float) state.getMode(coord));
        } else if (pname == GL11.GL_OBJECT_PLANE) {
            final float[] plane = state.getObjectPlane(coord);
            params.put(params.position(), plane[0]);
            params.put(params.position() + 1, plane[1]);
            params.put(params.position() + 2, plane[2]);
            params.put(params.position() + 3, plane[3]);
        } else if (pname == GL11.GL_EYE_PLANE) {
            final float[] plane = state.getEyePlane(coord);
            params.put(params.position(), plane[0]);
            params.put(params.position() + 1, plane[1]);
            params.put(params.position() + 2, plane[2]);
            params.put(params.position() + 3, plane[3]);
        }
    }

    public static void glGetTexGen(int coord, int pname, DoubleBuffer params) {
        final GLContextState glCtx = ctx();
        final var state = glCtx.textures.getTexGenState(glCtx.activeTextureUnit.getValue());
        if (pname == GL11.GL_TEXTURE_GEN_MODE) {
            params.put(params.position(), state.getMode(coord));
        } else if (pname == GL11.GL_OBJECT_PLANE) {
            final float[] plane = state.getObjectPlane(coord);
            params.put(params.position(), plane[0]);
            params.put(params.position() + 1, plane[1]);
            params.put(params.position() + 2, plane[2]);
            params.put(params.position() + 3, plane[3]);
        } else if (pname == GL11.GL_EYE_PLANE) {
            final float[] plane = state.getEyePlane(coord);
            params.put(params.position(), plane[0]);
            params.put(params.position() + 1, plane[1]);
            params.put(params.position() + 2, plane[2]);
            params.put(params.position() + 3, plane[3]);
        }
    }

    public static void glPolygonStipple(ByteBuffer pattern) {
        guardUnsupportedFFP("glPolygonStipple", "glPolygonStipple is not available in GL 3.3 core profile.");
    }

    public static void glAccum(int op, float value) {
        guardUnsupportedFFP("glAccum", "The accumulation buffer is not available in GL 3.3 core profile.");
    }

    public static void glFeedbackBuffer(int type, FloatBuffer buffer) {
        FeedbackManager.glFeedbackBuffer(type, buffer);
    }

    public static int glRenderMode(int mode) {
        return FeedbackManager.glRenderMode(mode);
    }

    public static void glPassThrough(float token) {
        FeedbackManager.glPassThrough(token);
    }

    private static boolean selectWarned = false;

    public static void glSelectBuffer(IntBuffer buffer) {
        if (!selectWarned) {
            LOGGER.warn("glSelectBuffer: selection mode not emulated");
            selectWarned = true;
        }
    }

    public static void glInitNames() { /* no-op: ignored outside GL_SELECT */ }

    public static void glPushName(int name) { /* no-op */ }

    public static void glPopName() { /* no-op */ }

    public static void glLoadName(int name) { /* no-op */ }

    public static void glLinkProgram(int program) {
        if (ShaderManager.getInstance().isEnabled()) {
            generateVertexShaderIfNeeded(program);
        }
        RENDER_BACKEND.linkProgram(program);
        if (ShaderManager.getInstance().isEnabled() && RENDER_BACKEND.getProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            LOGGER.warn("Program {} failed to link: {}", program, RENDER_BACKEND.getProgramInfoLog(program));
        }
        CompatUniformManager.onLinkProgram(program);
    }

    private static final IntBuffer SHADER_BUF = BufferUtils.createIntBuffer(8);

    /**
     * If a program has a fragment shader but no vertex shader, generate a passthrough vertex shader from the fragment's in declarations and attach it.
     */
    private static void generateVertexShaderIfNeeded(int program) {
        final int shaderCount = RENDER_BACKEND.getProgrami(program, GL20.GL_ATTACHED_SHADERS);
        if (shaderCount == 0 || shaderCount > SHADER_BUF.capacity()) return;

        SHADER_BUF.clear().limit(shaderCount);
        RENDER_BACKEND.getAttachedShaders(program, null, SHADER_BUF);

        int fragmentShader = 0;
        boolean hasVertex = false;
        for (int i = 0; i < shaderCount; i++) {
            final int shader = SHADER_BUF.get(i);
            final int type = RENDER_BACKEND.getShaderi(shader, GL20.GL_SHADER_TYPE);
            if (type == GL20.GL_VERTEX_SHADER) {
                hasVertex = true;
                break;
            } else if (type == GL20.GL_FRAGMENT_SHADER) {
                fragmentShader = shader;
            }
        }

        if (hasVertex || fragmentShader == 0) return;

        // Fragment-only program - generate a passthrough vertex shader
        final String fragSource = RENDER_BACKEND.getShaderSource(fragmentShader, 65536);
        String vertSource = CompatShaderTransformer.generatePassthroughVertexShader(fragSource);
        if (RenderSystem.isGLES()) {
            vertSource = CompatShaderTransformer.toGLES(vertSource, GL20.GL_VERTEX_SHADER, false);
        }

        final int vertShader = RENDER_BACKEND.createShader(GL20.GL_VERTEX_SHADER);
        RENDER_BACKEND.shaderSource(vertShader, vertSource);
        RENDER_BACKEND.compileShader(vertShader);

        if (RENDER_BACKEND.getShaderi(vertShader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            final String log = RENDER_BACKEND.getShaderInfoLog(vertShader, 8192);
            LOGGER.warn("CompatShaderTransformer: Generated passthrough vertex shader failed to compile:\n{}\nSource:\n{}", log, vertSource);
            RENDER_BACKEND.deleteShader(vertShader);
            return;
        }

        LOGGER.debug("CompatShaderTransformer: Generated passthrough vertex shader for fragment-only program {}", program);
        RENDER_BACKEND.attachShader(program, vertShader);
        RENDER_BACKEND.deleteShader(vertShader);
    }

    public static void glDeleteProgram(int program) {
        if (program == 0) return;
        CompatUniformManager.onDeleteProgram(program);

        // GL defers deletion of a bound program until unbind; activeProgram stays set so the
        // cache matches GL_CURRENT_PROGRAM and the next glUseProgram is not skipped as redundant.
        if (GLSMHooks.PROGRAM_DELETE.hasListeners()) {
            GLSMHooks.programDeleteEvent.program = program;
            GLSMHooks.PROGRAM_DELETE.post(GLSMHooks.programDeleteEvent);
        }
        RENDER_BACKEND.deleteProgram(program);
    }

    public static int glCreateShader(int type) {
        return RENDER_BACKEND.createShader(type);
    }

    public static void glCompileShader(int shader) {
        RENDER_BACKEND.compileShader(shader);
    }

    public static int glCreateProgram() {
        return RENDER_BACKEND.createProgram();
    }

    public static void glAttachShader(int program, int shader) {
        RENDER_BACKEND.attachShader(program, shader);
    }

    public static void glDetachShader(int program, int shader) {
        RENDER_BACKEND.detachShader(program, shader);
    }

    public static void glDeleteShader(int shader) {
        RENDER_BACKEND.deleteShader(shader);
    }

    public static void glBindAttribLocation(int program, int index, CharSequence name) {
        RENDER_BACKEND.bindAttribLocation(program, index, name);
    }

    public static void glValidateProgram(int program) {
        RENDER_BACKEND.validateProgram(program);
    }

    public static int glGetShaderi(int shader, int pname) {
        return RENDER_BACKEND.getShaderi(shader, pname);
    }

    public static String glGetShaderInfoLog(int shader, int maxLength) {
        return RENDER_BACKEND.getShaderInfoLog(shader, maxLength);
    }

    public static String glGetShaderInfoLog(int shader) {
        return RENDER_BACKEND.getShaderInfoLog(shader);
    }

    public static int glGetProgrami(int program, int pname) {
        return RENDER_BACKEND.getProgrami(program, pname);
    }

    public static String glGetProgramInfoLog(int program, int maxLength) {
        return RENDER_BACKEND.getProgramInfoLog(program, maxLength);
    }

    public static String glGetProgramInfoLog(int program) {
        return RENDER_BACKEND.getProgramInfoLog(program);
    }

    public static boolean glIsProgram(int obj) {
        return RENDER_BACKEND.isProgram(obj);
    }

    public static boolean glIsShader(int obj) {
        return RENDER_BACKEND.isShader(obj);
    }

    public static void glBindImageTexture(int unit, int texture, int level, boolean layered, int layer, int access, int format) {
        final GLContextState glCtx = ctx();
        final ImageUnitBinding binding = glCtx.imageUnits.get(unit);
        if (binding == null) {
            warnOnce("imageUnitRange", "glBindImageTexture unit {} exceeds GL_MAX_IMAGE_UNITS {}", unit, glCtx.imageUnits.size());
        } else {
            binding.setBinding(texture, level, layered, layer, access, format);
            if (texture != 0 && unit > glCtx.maxBoundImageUnit) glCtx.maxBoundImageUnit = unit;
        }
        RENDER_BACKEND.bindImageTexture(unit, texture, level, layered, layer, access, format);
    }

    public static ImageUnitBinding getImageUnitBinding(int unit) {
        return ctx().imageUnits.get(unit);
    }

    private static int imageBindingOrBackend(int pname, int index) {
        final ImageUnitBinding binding = !isCachingEnabled() ? null : ctx().imageUnits.get(index);
        if (binding == null) return RENDER_BACKEND.getIntegerIndexed(pname, index);
        return switch (pname) {
            case GL42.GL_IMAGE_BINDING_NAME -> binding.getTexture();
            case GL42.GL_IMAGE_BINDING_LEVEL -> binding.getLevel();
            case GL42.GL_IMAGE_BINDING_LAYERED -> binding.isLayered() ? GL11.GL_TRUE : GL11.GL_FALSE;
            case GL42.GL_IMAGE_BINDING_LAYER -> binding.getLayer();
            case GL42.GL_IMAGE_BINDING_ACCESS -> binding.getAccess();
            case GL42.GL_IMAGE_BINDING_FORMAT -> binding.getFormat();
            default -> RENDER_BACKEND.getIntegerIndexed(pname, index);
        };
    }

    public static int glGetInteger(int pname, int index) {
        return imageBindingOrBackend(pname, index);
    }

    public static int glGetIntegeri(int pname, int index) {
        return imageBindingOrBackend(pname, index);
    }

    public static void glGetIntegeri(int pname, int index, IntBuffer params) {
        glGetIntegeri_v(pname, index, params);
    }

    public static void glGetIntegeri_v(int pname, int index, IntBuffer params) {
        params.put(params.position(), imageBindingOrBackend(pname, index));
    }

    public static void glGetBooleani_v(int pname, int index, ByteBuffer params) {
        params.put(params.position(), (byte) (imageBindingOrBackend(pname, index) != 0 ? GL11.GL_TRUE : GL11.GL_FALSE));
    }

    public static void glGetBooleani(int pname, int index, ByteBuffer params) {
        glGetBooleani_v(pname, index, params);
    }

    public static void glMemoryBarrier(int barriers) { RENDER_BACKEND.memoryBarrier(barriers); }
    public static void glCopyImageSubData(int srcName, int srcTarget, int srcLevel, int srcX, int srcY, int srcZ,
                                           int dstName, int dstTarget, int dstLevel, int dstX, int dstY, int dstZ,
                                           int srcWidth, int srcHeight, int srcDepth) {
        RENDER_BACKEND.copyImageSubData(srcName, srcTarget, srcLevel, srcX, srcY, srcZ,
            dstName, dstTarget, dstLevel, dstX, dstY, dstZ, srcWidth, srcHeight, srcDepth);
    }
    public static void glDispatchCompute(int x, int y, int z) { RENDER_BACKEND.dispatchCompute(x, y, z); }

    public static int glGetUniformLocation(int program, CharSequence name) {
        int loc = RENDER_BACKEND.getUniformLocation(program, name);
        if (loc == -1 && TEXTURE.contentEquals(name)) {
            loc = RENDER_BACKEND.getUniformLocation(program, TEXTURE_RENAMED);
        }
        return loc;
    }

    public static int glGetUniformLocation(int program, ByteBuffer name) {
        int loc = RENDER_BACKEND.getUniformLocation(program, name);
        if (loc == -1 && isTextureBuffer(name)) {
            loc = RENDER_BACKEND.getUniformLocation(program, TEXTURE_RENAMED);
        }
        return loc;
    }

    public static int glGetAttribLocation(int program, CharSequence name) {
        int loc = RENDER_BACKEND.getAttribLocation(program, name);
        if (loc == -1 && TEXTURE.contentEquals(name)) {
            loc = RENDER_BACKEND.getAttribLocation(program, TEXTURE_RENAMED);
        }
        return loc;
    }

    public static int glGetAttribLocation(int program, ByteBuffer name) {
        int loc = RENDER_BACKEND.getAttribLocation(program, name);
        if (loc == -1 && isTextureBuffer(name)) {
            loc = RENDER_BACKEND.getAttribLocation(program, TEXTURE_RENAMED);
        }
        return loc;
    }

    private static final ByteBuffer TEXTURE_BYTES = ByteBuffer.wrap(TEXTURE.getBytes(StandardCharsets.US_ASCII));

    private static boolean isTextureBuffer(ByteBuffer buf) {
        final int len = buf.remaining();
        if (len != 7 && len != 8) return false;
        // I believe the driver accepts null terminated or not
        if (len == 8 && buf.get(buf.position() + 7) != 0) return false;
        return buf.slice(buf.position(), 7).equals(TEXTURE_BYTES);
    }

    public static void glDeleteObjectARB(int obj) {
        if (RENDER_BACKEND.isShader(obj)) {
            RENDER_BACKEND.deleteShader(obj);
        } else {
            glDeleteProgram(obj);
        }
    }

    public static int glGetHandleARB(int pname) {
        return pname == ARBShaderObjects.GL_PROGRAM_OBJECT_ARB ? glGetInteger(GL20.GL_CURRENT_PROGRAM) : RENDER_BACKEND.getInteger(pname);
    }

    public static void glGetObjectParameterARB(int obj, int pname, IntBuffer params) {
        final var backend = RENDER_BACKEND;
        if (backend.isShader(obj)) {
            params.put(params.position(), backend.getShaderi(obj, pname));
        } else {
            backend.getProgramiv(obj, pname, params);
        }
    }

    public static void glGetObjectParameterARB(int obj, int pname, FloatBuffer params) {
        final var backend = RENDER_BACKEND;
        params.put(params.position(), (float) (backend.isShader(obj) ? backend.getShaderi(obj, pname) : backend.getProgrami(obj, pname)));
    }

    public static int glGetObjectParameteriARB(int obj, int pname) {
        final var backend = RENDER_BACKEND;
        return backend.isShader(obj) ? backend.getShaderi(obj, pname) : backend.getProgrami(obj, pname);
    }

    public static String glGetInfoLogARB(int obj, int maxLength) {
        final var backend = RENDER_BACKEND;
        return backend.isShader(obj) ? backend.getShaderInfoLog(obj, maxLength) : backend.getProgramInfoLog(obj, maxLength);
    }

    public static void glGetInfoLogARB(int obj, IntBuffer length, ByteBuffer infoLog) {
        final var backend = RENDER_BACKEND;
        if (backend.isShader(obj)) {
            backend.getShaderInfoLog(obj, length, infoLog);
        } else {
            backend.getProgramInfoLog(obj, length, infoLog);
        }
    }

    public static void glUniform1f(int location, float v0) {
        RENDER_BACKEND.uniform1f(location, v0);
    }

    public static void glUniform2f(int location, float v0, float v1) {
        RENDER_BACKEND.uniform2f(location, v0, v1);
    }

    public static void glUniform3f(int location, float v0, float v1, float v2) {
        RENDER_BACKEND.uniform3f(location, v0, v1, v2);
    }

    public static void glUniform4f(int location, float v0, float v1, float v2, float v3) {
        RENDER_BACKEND.uniform4f(location, v0, v1, v2, v3);
    }

    public static void glUniform1i(int location, int v0) {
        RENDER_BACKEND.uniform1i(location, v0);
    }

    public static void glUniform2i(int location, int v0, int v1) {
        RENDER_BACKEND.uniform2i(location, v0, v1);
    }

    public static void glUniform3i(int location, int v0, int v1, int v2) {
        RENDER_BACKEND.uniform3i(location, v0, v1, v2);
    }

    public static void glUniform4i(int location, int v0, int v1, int v2, int v3) {
        RENDER_BACKEND.uniform4i(location, v0, v1, v2, v3);
    }

    public static void glUniform1(int location, FloatBuffer values) {
        RENDER_BACKEND.uniform1fv(location, values);
    }

    public static void glUniform1(int location, IntBuffer values) {
        RENDER_BACKEND.uniform1iv(location, values);
    }

    public static void glUniform2(int location, FloatBuffer values) {
        RENDER_BACKEND.uniform2(location, values);
    }

    public static void glUniform2(int location, IntBuffer values) {
        RENDER_BACKEND.uniform2iv(location, values);
    }

    public static void glUniform3(int location, FloatBuffer values) {
        RENDER_BACKEND.uniform3(location, values);
    }

    public static void glUniform3(int location, IntBuffer values) {
        RENDER_BACKEND.uniform3iv(location, values);
    }

    public static void glUniform4(int location, FloatBuffer values) {
        RENDER_BACKEND.uniform4(location, values);
    }

    public static void glUniform4(int location, IntBuffer values) {
        RENDER_BACKEND.uniform4iv(location, values);
    }

    public static void glUniformMatrix2(int location, boolean transpose, FloatBuffer matrices) {
        RENDER_BACKEND.uniformMatrix2(location, transpose, matrices);
    }

    public static void glUniformMatrix3(int location, boolean transpose, FloatBuffer matrices) {
        RENDER_BACKEND.uniformMatrix3(location, transpose, matrices);
    }

    public static void glUniformMatrix4(int location, boolean transpose, FloatBuffer matrices) {
        RENDER_BACKEND.uniformMatrix4(location, transpose, matrices);
    }

    public static void glGetActiveUniform(int program, int index, IntBuffer length, IntBuffer size, IntBuffer type, ByteBuffer name) {
        RENDER_BACKEND.getActiveUniform(program, index, length, size, type, name);
    }

    public static void glGetActiveAttrib(int program, int index, IntBuffer length, IntBuffer size, IntBuffer type, ByteBuffer name) {
        RENDER_BACKEND.getActiveAttrib(program, index, length, size, type, name);
    }

    public static String glGetActiveAttrib(int program, int index, int maxLength, IntBuffer sizeType) {
        return RENDER_BACKEND.getActiveAttrib(program, index, maxLength, sizeType);
    }

    public static void glGetAttachedShaders(int program, IntBuffer count, IntBuffer shaders) {
        RENDER_BACKEND.getAttachedShaders(program, count, shaders);
    }

    public static String glGetShaderSource(int shader, int maxLength) {
        return RENDER_BACKEND.getShaderSource(shader, maxLength);
    }

    public static void glGetShaderSource(int shader, IntBuffer length, ByteBuffer source) {
        RENDER_BACKEND.getShaderSource(shader, length, source);
    }

    public static void glGetUniform(int program, int location, FloatBuffer params) {
        RENDER_BACKEND.getUniformfv(program, location, params);
    }

    public static void glGetUniform(int program, int location, IntBuffer params) {
        RENDER_BACKEND.getUniformiv(program, location, params);
    }
    public static void glGetTexImage(int target, int level, int format, int type, long pixels) {
        RENDER_BACKEND.getTexImage(target, level, format, type, pixels);
    }

    /**
     * Draw modes that rely on the previous vertex data cannot be merged currently.
     * It is possible to merge them using Index Buffers, but currently unimplemented.
     */
    public static boolean isContinuousDraw(int drawMode) {
        return drawMode == GL11.GL_TRIANGLE_STRIP || drawMode == GL11.GL_TRIANGLE_FAN
            || drawMode == GL11.GL_LINE_STRIP || drawMode == GL11.GL_LINE_LOOP
            || drawMode == GL11.GL_QUAD_STRIP || drawMode == GL11.GL_POLYGON;
    }

    public static int getProgramGeneration() {
        return ctx().programGeneration;
    }

    public static int getDrawFramebufferGeneration() {
        return ctx().drawFramebufferGeneration;
    }

    public static boolean isPoppingAttributes() {
        return ctx().poppingAttributes;
    }

    public static int getMvGeneration() {
        return ctx().mvGeneration;
    }

    public static int getMvLinearGeneration() {
        return ctx().mvLinearGeneration;
    }

    public static int getProjGeneration() {
        return ctx().projGeneration;
    }

    public static int getTexMatrixGeneration() {
        return ctx().texMatrixGeneration;
    }

    public static int getLightingGeneration() {
        return ctx().lightingGeneration;
    }

    public static int getFragmentGeneration() {
        return ctx().fragmentGeneration;
    }

    public static int getColorGeneration() {
        return ctx().colorGeneration;
    }

    public static int getClipPlaneGeneration() {
        return ctx().clipPlaneGeneration;
    }

    public static float getOverlayR() {
        return ctx().overlayR;
    }

    public static float getOverlayG() {
        return ctx().overlayG;
    }

    public static float getOverlayB() {
        return ctx().overlayB;
    }

    public static float getOverlayA() {
        return ctx().overlayA;
    }

    public static float getShaderColorR() {
        return ctx().shaderColorR;
    }

    public static float getShaderColorG() {
        return ctx().shaderColorG;
    }

    public static float getShaderColorB() {
        return ctx().shaderColorB;
    }

    public static float getShaderColorA() {
        return ctx().shaderColorA;
    }

    public static int getAttribDepth() {
        return ctx().attribDepth;
    }

    public static IntegerStateStack getActiveTextureUnitStack() {
        return ctx().activeTextureUnit;
    }

    public static IntegerStateStack getShadeModelState() {
        return ctx().shadeModelState;
    }

    public static TextureUnitArray getTextures() {
        return ctx().textures;
    }

    public static BlendStateStack getBlendState() {
        return ctx().blendState;
    }

    public static BooleanStateStack getBlendMode() {
        return ctx().blendMode;
    }

    public static BooleanStateStack getScissorTest() {
        return ctx().scissorTest;
    }

    public static DepthStateStack getDepthState() {
        return ctx().depthState;
    }

    public static BooleanStateStack getDepthTest() {
        return ctx().depthTest;
    }

    public static FogStateStack getFogState() {
        return ctx().fogState;
    }

    public static BooleanStateStack getFogMode() {
        return ctx().fogMode;
    }

    public static Color4Stack getColor() {
        return ctx().color;
    }

    public static Color4Stack getClearColor() {
        return ctx().clearColor;
    }

    public static ColorMaskStack getColorMask() {
        return ctx().colorMask;
    }

    public static IntegerStateStack getDrawBuffer() {
        return ctx().drawBuffer;
    }

    public static IntegerStateStack getLogicOpMode() {
        return ctx().logicOpMode;
    }

    public static BooleanStateStack getCullState() {
        return ctx().cullState;
    }

    public static AlphaStateStack getAlphaState() {
        return ctx().alphaState;
    }

    public static BooleanStateStack getAlphaTest() {
        return ctx().alphaTest;
    }

    public static BooleanStateStack getLightingState() {
        return ctx().lightingState;
    }

    public static BooleanStateStack getRescaleNormalState() {
        return ctx().rescaleNormalState;
    }

    public static BooleanStateStack getNormalizeState() {
        return ctx().normalizeState;
    }

    public static BooleanStateStack getDitherState() {
        return ctx().ditherState;
    }

    public static BooleanStateStack getStencilTest() {
        return ctx().stencilTest;
    }

    public static BooleanStateStack getLineSmoothState() {
        return ctx().lineSmoothState;
    }

    public static BooleanStateStack getLineStippleState() {
        return ctx().lineStippleState;
    }

    public static BooleanStateStack getPointSmoothState() {
        return ctx().pointSmoothState;
    }

    public static BooleanStateStack getPolygonSmoothState() {
        return ctx().polygonSmoothState;
    }

    public static BooleanStateStack getPolygonStippleState() {
        return ctx().polygonStippleState;
    }

    public static BooleanStateStack getMultisampleState() {
        return ctx().multisampleState;
    }

    public static BooleanStateStack getSampleAlphaToCoverageState() {
        return ctx().sampleAlphaToCoverageState;
    }

    public static BooleanStateStack getSampleAlphaToOneState() {
        return ctx().sampleAlphaToOneState;
    }

    public static BooleanStateStack getSampleCoverageState() {
        return ctx().sampleCoverageState;
    }

    public static BooleanStateStack getColorLogicOpState() {
        return ctx().colorLogicOpState;
    }

    public static BooleanStateStack getIndexLogicOpState() {
        return ctx().indexLogicOpState;
    }

    public static BooleanStateStack getPolygonOffsetPointState() {
        return ctx().polygonOffsetPointState;
    }

    public static BooleanStateStack getPolygonOffsetLineState() {
        return ctx().polygonOffsetLineState;
    }

    public static BooleanStateStack getPolygonOffsetFillState() {
        return ctx().polygonOffsetFillState;
    }

    public static LineStateStack getLineState() {
        return ctx().lineState;
    }

    public static PointStateStack getPointState() {
        return ctx().pointState;
    }

    public static PolygonStateStack getPolygonState() {
        return ctx().polygonState;
    }

    public static StencilStateStack getStencilState() {
        return ctx().stencilState;
    }

    public static BooleanStateStack getAutoNormalState() {
        return ctx().autoNormalState;
    }

    public static BooleanStateStack getMap1Color4State() {
        return ctx().map1Color4State;
    }

    public static BooleanStateStack getMap1IndexState() {
        return ctx().map1IndexState;
    }

    public static BooleanStateStack getMap1NormalState() {
        return ctx().map1NormalState;
    }

    public static BooleanStateStack getMap1TextureCoord1State() {
        return ctx().map1TextureCoord1State;
    }

    public static BooleanStateStack getMap1TextureCoord2State() {
        return ctx().map1TextureCoord2State;
    }

    public static BooleanStateStack getMap1TextureCoord3State() {
        return ctx().map1TextureCoord3State;
    }

    public static BooleanStateStack getMap1TextureCoord4State() {
        return ctx().map1TextureCoord4State;
    }

    public static BooleanStateStack getMap1Vertex3State() {
        return ctx().map1Vertex3State;
    }

    public static BooleanStateStack getMap1Vertex4State() {
        return ctx().map1Vertex4State;
    }

    public static BooleanStateStack getMap2Color4State() {
        return ctx().map2Color4State;
    }

    public static BooleanStateStack getMap2IndexState() {
        return ctx().map2IndexState;
    }

    public static BooleanStateStack getMap2NormalState() {
        return ctx().map2NormalState;
    }

    public static BooleanStateStack getMap2TextureCoord1State() {
        return ctx().map2TextureCoord1State;
    }

    public static BooleanStateStack getMap2TextureCoord2State() {
        return ctx().map2TextureCoord2State;
    }

    public static BooleanStateStack getMap2TextureCoord3State() {
        return ctx().map2TextureCoord3State;
    }

    public static BooleanStateStack getMap2TextureCoord4State() {
        return ctx().map2TextureCoord4State;
    }

    public static BooleanStateStack getMap2Vertex3State() {
        return ctx().map2Vertex3State;
    }

    public static BooleanStateStack getMap2Vertex4State() {
        return ctx().map2Vertex4State;
    }

    public static BooleanStateStack[] getClipPlaneStates() {
        return ctx().clipPlaneStates;
    }

    public static ClipPlaneState getClipPlaneState() {
        return ctx().clipPlaneState;
    }

    public static MatrixModeStack getMatrixMode() {
        return ctx().matrixMode;
    }

    public static Matrix4fStack getModelViewMatrix() {
        return ctx().modelViewMatrix;
    }

    public static Matrix4fStack getProjectionMatrix() {
        return ctx().projectionMatrix;
    }

    public static BooleanStateStack[] getLightStates() {
        return ctx().lightStates;
    }

    public static LightStateStack[] getLightDataStates() {
        return ctx().lightDataStates;
    }

    public static BooleanStateStack getColorMaterial() {
        return ctx().colorMaterial;
    }

    public static IntegerStateStack getColorMaterialFace() {
        return ctx().colorMaterialFace;
    }

    public static IntegerStateStack getColorMaterialParameter() {
        return ctx().colorMaterialParameter;
    }

    public static LightModelStateStack getLightModel() {
        return ctx().lightModel;
    }

    public static MaterialStateStack getFrontMaterial() {
        return ctx().frontMaterial;
    }

    public static MaterialStateStack getBackMaterial() {
        return ctx().backMaterial;
    }

    public static ViewPortStateStack getViewportState() {
        return ctx().viewportState;
    }

    public static int getActiveProgram() {
        return ctx().activeProgram;
    }

    public static int getListBase() {
        return ctx().listBase;
    }

    public static int getBoundVBO() {
        return ctx().boundVBO;
    }

    public static int getBoundVAO() {
        return ctx().boundVAO;
    }

    public static int getDrawFramebuffer() {
        return ctx().drawFramebuffer;
    }

    public static int getReadFramebuffer() {
        return ctx().readFramebuffer;
    }

    public static int getTexGenGeneration() {
        return ctx().texGenGeneration;
    }

    public static void incrementFragmentGeneration() {
        ctx().fragmentGeneration++;
    }
}
