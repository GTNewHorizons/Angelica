package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizon.gtnhlib.client.renderer.stacks.IStateStack;
import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.glsm.recording.CompiledDisplayList;
import com.gtnewhorizons.angelica.glsm.recording.ImmediateModeRecorder;
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
import com.gtnewhorizons.angelica.glsm.stacks.TextureBindingStack;
import com.gtnewhorizons.angelica.glsm.stacks.ViewPortStateStack;
import com.gtnewhorizons.angelica.glsm.states.ClientArrayState;
import com.gtnewhorizons.angelica.glsm.states.Color4;
import com.gtnewhorizons.angelica.glsm.states.TextureBinding;
import com.gtnewhorizons.angelica.glsm.states.TextureUnitArray;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfo;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfoCache;
import com.gtnewhorizons.angelica.glsm.texture.TextureTracker;
import com.gtnewhorizons.angelica.hudcaching.HUDCaching;
import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import cpw.mods.fml.relauncher.ReflectionHelper;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntStack;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gbuffer_overrides.state.StateTracker;
import net.coderbot.iris.gl.blending.AlphaTestStorage;
import net.coderbot.iris.gl.blending.BlendModeStorage;
import net.coderbot.iris.gl.blending.DepthColorStorage;
import net.coderbot.iris.gl.state.StateUpdateNotifiers;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.samplers.IrisSamplers;
import net.coderbot.iris.texture.pbr.PBRTextureManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.ARBMultitexture;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.Drawable;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.opengl.KHRDebug;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

import static com.gtnewhorizons.angelica.glsm.Vendor.AMD;
import static com.gtnewhorizons.angelica.glsm.Vendor.INTEL;
import static com.gtnewhorizons.angelica.glsm.Vendor.MESA;
import static com.gtnewhorizons.angelica.glsm.Vendor.NVIDIA;
import static com.gtnewhorizons.angelica.loading.AngelicaTweaker.LOGGER;

/**
 * OpenGL State Manager - Provides cached state tracking and management for OpenGL operations.
 *
 * <p><b>IMPORTANT INITIALIZATION ORDER:</b></p>
 * <ul>
 *   <li>This class performs GL queries in static initializers (MAX_CLIP_PLANES, MAX_TEXTURE_UNITS, etc.)</li>
 *   <li>The class MUST NOT be loaded until after the GL context has been created and made current</li>
 *   <li>Violating this requirement will cause crashes with "No current context" or return invalid values</li>
 *   <li>Call {@link #preInit()} after GL context creation to initialize runtime state</li>
 * </ul>
 */
@SuppressWarnings("unused") // Used in ASM
public class GLStateManager {

    // Thread Checking - must be early in static init order so isMainThread() works for state initialization
    @Getter private static final Thread MainThread = Thread.currentThread();

    public static boolean isMainThread() {
        return Thread.currentThread() == MainThread;
    }

    public static ContextCapabilities capabilities;

    @Getter protected static boolean poppingAttributes;
    public static boolean BYPASS_CACHE = Boolean.parseBoolean(System.getProperty("angelica.disableGlCache", "false"));
    // +2 headroom for internal operations (display list compilation) that push cache but not driver
    public static final int MAX_ATTRIB_STACK_DEPTH = GL11.glGetInteger(GL11.GL_MAX_ATTRIB_STACK_DEPTH) + 2;
    public static final int MAX_MODELVIEW_STACK_DEPTH = GL11.glGetInteger(GL11.GL_MAX_MODELVIEW_STACK_DEPTH) + 2;
    public static final int MAX_PROJECTION_STACK_DEPTH = GL11.glGetInteger(GL11.GL_MAX_PROJECTION_STACK_DEPTH);
    public static final int MAX_TEXTURE_STACK_DEPTH = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_STACK_DEPTH);
    public static final int MAX_TEXTURE_UNITS = GL11.glGetInteger(GL20.GL_MAX_TEXTURE_IMAGE_UNITS);
    public static final int MAX_CLIP_PLANES = GL11.glGetInteger(GL11.GL_MAX_CLIP_PLANES);

    public static final GLFeatureSet HAS_MULTIPLE_SET = new GLFeatureSet();

    @Getter private static Vendor VENDOR;

    // This setting varies depending on driver, so it gets queried at runtime
    public static final int DEFAULT_DRAW_BUFFER = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);


    private static Thread CurrentThread = MainThread;
    @Setter @Getter private static boolean runningSplash = true;

    private static volatile boolean splashComplete = false;
    private static volatile Thread drawableGLHolder = MainThread;
    // Reference to SharedDrawable so makeCurrent() can distinguish it from DrawableGL
    @Setter private static volatile Drawable sharedDrawable = null;

    public static boolean isCachingEnabled() {
        if (splashComplete) return true;
        return Thread.currentThread() == drawableGLHolder;
    }

    /**
     * Check if splash screen is complete.
     * After splash, there's only one GL context and no locking is needed.
     */
    public static boolean isSplashComplete() {
        return splashComplete;
    }

    /**
     * Get the active texture unit for server-side state operations.
     * If caching is enabled, returns cached value.
     * If caching is disabled (SharedDrawable), queries actual GL state.
     */
    public static int getActiveTextureUnitForServerState() {
        if (isCachingEnabled()) {
            return getActiveTextureUnit();
        }
        // Query actual GL state for SharedDrawable context
        return GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) - GL13.GL_TEXTURE0;
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
        return GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
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

    // GLStateManager State Trackers
    private static final IntStack attribs = new IntArrayList(MAX_ATTRIB_STACK_DEPTH);

    // Lazy copy-on-write tracking: only iterate modified states during popState
    @Getter private static int attribDepth = 0;
    @SuppressWarnings("unchecked")
    private static final List<IStateStack<?>>[] modifiedAtDepth = new List[MAX_ATTRIB_STACK_DEPTH];
    static {
        for (int i = 0; i < MAX_ATTRIB_STACK_DEPTH; i++) {
            modifiedAtDepth[i] = new ArrayList<>();
        }
    }

    /** Register a state stack as modified at the current depth (called from beforeModify). */
    public static void registerModifiedState(IStateStack<?> stack) {
        if (attribDepth > 0) {
            modifiedAtDepth[attribDepth - 1].add(stack);
        }
    }
    protected static final IntegerStateStack activeTextureUnit = new IntegerStateStack(0);
    protected static final IntegerStateStack shadeModelState = new IntegerStateStack(GL11.GL_SMOOTH);

    // Client array state (not push/pop tracked, but needed for display list compilation)
    @Getter private static final ClientArrayState clientArrayState = new ClientArrayState();

    @Getter protected static final TextureUnitArray textures = new TextureUnitArray();
    @Getter protected static final BlendStateStack blendState = new BlendStateStack();
    @Getter protected static final BooleanStateStack blendMode = new BooleanStateStack(GL11.GL_BLEND);
    @Getter protected static final BooleanStateStack scissorTest = new BooleanStateStack(GL11.GL_SCISSOR_TEST);
    @Getter protected static final DepthStateStack depthState = new DepthStateStack();
    @Getter protected static final BooleanStateStack depthTest = new BooleanStateStack(GL11.GL_DEPTH_TEST);

    @Getter protected static final FogStateStack fogState = new FogStateStack();
    @Getter protected static final BooleanStateStack fogMode = new BooleanStateStack(GL11.GL_FOG);
    @Getter protected static final Color4Stack color = new Color4Stack();
    @Getter protected static final Color4Stack clearColor = new Color4Stack(new Color4(0.0F, 0.0F, 0.0F, 0.0F));
    @Getter protected static final ColorMaskStack colorMask = new ColorMaskStack();
    @Getter protected static final IntegerStateStack drawBuffer = new IntegerStateStack(DEFAULT_DRAW_BUFFER);
    @Getter protected static final BooleanStateStack cullState = new BooleanStateStack(GL11.GL_CULL_FACE);
    @Getter protected static final AlphaStateStack alphaState = new AlphaStateStack();
    @Getter protected static final BooleanStateStack alphaTest = new BooleanStateStack(GL11.GL_ALPHA_TEST);

    @Getter protected static final BooleanStateStack lightingState = new BooleanStateStack(GL11.GL_LIGHTING);
    @Getter protected static final BooleanStateStack rescaleNormalState = new BooleanStateStack(GL12.GL_RESCALE_NORMAL);
    @Getter protected static final BooleanStateStack normalizeState = new BooleanStateStack(GL11.GL_NORMALIZE);

    // Additional enable bit states tracked by GL_ENABLE_BIT
    @Getter protected static final BooleanStateStack ditherState = new BooleanStateStack(GL11.GL_DITHER, true); // Defaults to true per OpenGL spec
    @Getter protected static final BooleanStateStack stencilTest = new BooleanStateStack(GL11.GL_STENCIL_TEST);
    @Getter protected static final BooleanStateStack lineSmoothState = new BooleanStateStack(GL11.GL_LINE_SMOOTH);
    @Getter protected static final BooleanStateStack lineStippleState = new BooleanStateStack(GL11.GL_LINE_STIPPLE);
    @Getter protected static final BooleanStateStack pointSmoothState = new BooleanStateStack(GL11.GL_POINT_SMOOTH);
    @Getter protected static final BooleanStateStack polygonSmoothState = new BooleanStateStack(GL11.GL_POLYGON_SMOOTH);
    @Getter protected static final BooleanStateStack polygonStippleState = new BooleanStateStack(GL11.GL_POLYGON_STIPPLE);
    @Getter protected static final BooleanStateStack multisampleState = new BooleanStateStack(GL13.GL_MULTISAMPLE, true); // Defaults to true per OpenGL spec
    @Getter protected static final BooleanStateStack sampleAlphaToCoverageState = new BooleanStateStack(GL13.GL_SAMPLE_ALPHA_TO_COVERAGE);
    @Getter protected static final BooleanStateStack sampleAlphaToOneState = new BooleanStateStack(GL13.GL_SAMPLE_ALPHA_TO_ONE);
    @Getter protected static final BooleanStateStack sampleCoverageState = new BooleanStateStack(GL13.GL_SAMPLE_COVERAGE);
    @Getter protected static final BooleanStateStack colorLogicOpState = new BooleanStateStack(GL11.GL_COLOR_LOGIC_OP);
    @Getter protected static final BooleanStateStack indexLogicOpState = new BooleanStateStack(GL11.GL_INDEX_LOGIC_OP);

    // Polygon offset states (enable bits)
    @Getter protected static final BooleanStateStack polygonOffsetPointState = new BooleanStateStack(GL11.GL_POLYGON_OFFSET_POINT);
    @Getter protected static final BooleanStateStack polygonOffsetLineState = new BooleanStateStack(GL11.GL_POLYGON_OFFSET_LINE);
    @Getter protected static final BooleanStateStack polygonOffsetFillState = new BooleanStateStack(GL11.GL_POLYGON_OFFSET_FILL);

    // Line state (GL_LINE_BIT)
    @Getter protected static final LineStateStack lineState = new LineStateStack();

    // Point state (GL_POINT_BIT)
    @Getter protected static final PointStateStack pointState = new PointStateStack();

    // Polygon state (GL_POLYGON_BIT) - mode, offset values, cull face mode, front face
    @Getter protected static final PolygonStateStack polygonState = new PolygonStateStack();

    // Stencil state (GL_STENCIL_BUFFER_BIT)
    @Getter protected static final StencilStateStack stencilState = new StencilStateStack();

    // Evaluator states
    @Getter protected static final BooleanStateStack autoNormalState = new BooleanStateStack(GL11.GL_AUTO_NORMAL);
    @Getter protected static final BooleanStateStack map1Color4State = new BooleanStateStack(GL11.GL_MAP1_COLOR_4);
    @Getter protected static final BooleanStateStack map1IndexState = new BooleanStateStack(GL11.GL_MAP1_INDEX);
    @Getter protected static final BooleanStateStack map1NormalState = new BooleanStateStack(GL11.GL_MAP1_NORMAL);
    @Getter protected static final BooleanStateStack map1TextureCoord1State = new BooleanStateStack(GL11.GL_MAP1_TEXTURE_COORD_1);
    @Getter protected static final BooleanStateStack map1TextureCoord2State = new BooleanStateStack(GL11.GL_MAP1_TEXTURE_COORD_2);
    @Getter protected static final BooleanStateStack map1TextureCoord3State = new BooleanStateStack(GL11.GL_MAP1_TEXTURE_COORD_3);
    @Getter protected static final BooleanStateStack map1TextureCoord4State = new BooleanStateStack(GL11.GL_MAP1_TEXTURE_COORD_4);
    @Getter protected static final BooleanStateStack map1Vertex3State = new BooleanStateStack(GL11.GL_MAP1_VERTEX_3);
    @Getter protected static final BooleanStateStack map1Vertex4State = new BooleanStateStack(GL11.GL_MAP1_VERTEX_4);
    @Getter protected static final BooleanStateStack map2Color4State = new BooleanStateStack(GL11.GL_MAP2_COLOR_4);
    @Getter protected static final BooleanStateStack map2IndexState = new BooleanStateStack(GL11.GL_MAP2_INDEX);
    @Getter protected static final BooleanStateStack map2NormalState = new BooleanStateStack(GL11.GL_MAP2_NORMAL);
    @Getter protected static final BooleanStateStack map2TextureCoord1State = new BooleanStateStack(GL11.GL_MAP2_TEXTURE_COORD_1);
    @Getter protected static final BooleanStateStack map2TextureCoord2State = new BooleanStateStack(GL11.GL_MAP2_TEXTURE_COORD_2);
    @Getter protected static final BooleanStateStack map2TextureCoord3State = new BooleanStateStack(GL11.GL_MAP2_TEXTURE_COORD_3);
    @Getter protected static final BooleanStateStack map2TextureCoord4State = new BooleanStateStack(GL11.GL_MAP2_TEXTURE_COORD_4);
    @Getter protected static final BooleanStateStack map2Vertex3State = new BooleanStateStack(GL11.GL_MAP2_VERTEX_3);
    @Getter protected static final BooleanStateStack map2Vertex4State = new BooleanStateStack(GL11.GL_MAP2_VERTEX_4);

    // Clip plane states
    @Getter protected static final BooleanStateStack[] clipPlaneStates = new BooleanStateStack[MAX_CLIP_PLANES];

    @Getter protected static final MatrixModeStack matrixMode = new MatrixModeStack();
    @Getter protected static final Matrix4fStack modelViewMatrix = new Matrix4fStack(MAX_MODELVIEW_STACK_DEPTH);
    @Getter protected static final Matrix4fStack projectionMatrix = new Matrix4fStack(MAX_PROJECTION_STACK_DEPTH);

    @Getter protected static final BooleanStateStack[] lightStates = new BooleanStateStack[8];
    @Getter protected static final LightStateStack[] lightDataStates = new LightStateStack[8];
    @Getter protected static final BooleanStateStack colorMaterial = new BooleanStateStack(GL11.GL_COLOR_MATERIAL);
    @Getter protected static final IntegerStateStack colorMaterialFace = new IntegerStateStack(GL11.GL_FRONT_AND_BACK);
    @Getter protected static final IntegerStateStack colorMaterialParameter = new IntegerStateStack(GL11.GL_AMBIENT_AND_DIFFUSE);
    @Getter protected static final LightModelStateStack lightModel = new LightModelStateStack();

    @Getter protected static final MaterialStateStack frontMaterial = new MaterialStateStack(GL11.GL_FRONT);
    @Getter protected static final MaterialStateStack backMaterial = new MaterialStateStack(GL11.GL_BACK);

    private static final MethodHandle MAT4_STACK_CURR_DEPTH;

    static {
        for (int i = 0; i < lightStates.length; i ++) {
            lightStates[i] = new BooleanStateStack(GL11.GL_LIGHT0 + i);
            lightDataStates[i] = new LightStateStack(GL11.GL_LIGHT0 + i);
        }

        for (int i = 0; i < MAX_CLIP_PLANES; i++) {
            clipPlaneStates[i] = new BooleanStateStack(GL11.GL_CLIP_PLANE0 + i);
        }

        try {
            final Field curr = ReflectionHelper.findField(Matrix4fStack.class, "curr");
            curr.setAccessible(true);
            MAT4_STACK_CURR_DEPTH = MethodHandles.lookup().unreflectGetter(curr);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Getter protected static final ViewPortStateStack viewportState = new ViewPortStateStack();

    @Getter protected static int activeProgram = 0;

    public static void reset() {
        runningSplash = true;
        while(!attribs.isEmpty()) {
            attribs.popInt();
        }

        final List<IStateStack<?>> stacks = Feature.maskToFeatures(GL11.GL_ALL_ATTRIB_BITS);
        final int size = stacks.size();

        for(int i = 0; i < size; i++) {
            final IStateStack<?> stack = stacks.get(i);

            while(!stack.isEmpty()) {
                stack.pop();
            }
        }

        modelViewMatrix.clear();
        projectionMatrix.clear();
    }

    // Iris Listeners
    private static Runnable blendFuncListener = null;
    private static Runnable fogToggleListener = null;
    private static Runnable fogModeListener = null;
    private static Runnable fogStartListener = null;
    private static Runnable fogEndListener = null;
    private static Runnable fogDensityListener = null;

    /**
     * Check if a display list exists (has been compiled and stored).
     * Delegates to DisplayListManager.
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

    public static void preInit() {
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

        final String glVendor = GL11.glGetString(GL11.GL_VENDOR);
        VENDOR = Vendor.getVendor(glVendor.toLowerCase());

        if (vendorIsAMD()) {
            // AMD Drivers seem to default to 0 for the matrix mode, so we need to set it to the default
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
        }

        final Minecraft mc = Minecraft.getMinecraft();
        if (mc != null) {
            // Initialize viewport state from display dimensions.
            // After Display.create(), viewport is (0, 0, width, height)
            viewportState.setViewPort(0, 0, mc.displayWidth, mc.displayHeight);
        }
    }

    public static void init() {
        RenderSystem.initRenderer();

        if (Iris.enabled) {
            StateUpdateNotifiers.blendFuncNotifier = listener -> blendFuncListener = listener;
            StateUpdateNotifiers.fogToggleNotifier = listener -> fogToggleListener = listener;
            StateUpdateNotifiers.fogModeNotifier = listener -> fogModeListener = listener;
            StateUpdateNotifiers.fogStartNotifier = listener -> fogStartListener = listener;
            StateUpdateNotifiers.fogEndNotifier = listener -> fogEndListener = listener;
            StateUpdateNotifiers.fogDensityNotifier = listener -> fogDensityListener = listener;
        }
        if(BYPASS_CACHE) {
            LOGGER.info("GLStateManager cache bypassed");
        }
        if(AngelicaMod.lwjglDebug) {
            LOGGER.info("Enabling additional LWJGL debug output");

            GLDebug.setupDebugMessageCallback();
            GLDebug.initDebugState();

            GLDebug.debugMessage("Angelica Debug Annotator Initialized");
        }
    }


    public static void assertMainThread() {
        if (Thread.currentThread() != CurrentThread && !runningSplash) {
            LOGGER.info("Call from not the Current Thread! - " + Thread.currentThread().getName() + " Current thread: " + CurrentThread.getName());
        }
    }

    public static boolean shouldBypassCache() {
        // Bypass cache when not using the main DrawableGL context
        return BYPASS_CACHE || !isCachingEnabled();
    }

    /**
     * Returns true if we should use DSA for texture operations.
     * DSA relies on cached texture bindings, which aren't tracked when caching is disabled.
     * Also, proxy textures are special query textures that shouldn't use DSA.
     */
    private static boolean shouldUseDSA(int target) {
        if (!isCachingEnabled()) return false;
        // Proxy textures are for capability queries, not real textures
        return target != GL11.GL_PROXY_TEXTURE_1D
            && target != GL11.GL_PROXY_TEXTURE_2D
            && target != GL12.GL_PROXY_TEXTURE_3D;
    }

    // LWJGL Overrides
    public static void glEnable(int cap) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordEnable(cap);
        }
        // Handle clip planes dynamically (supports up to MAX_CLIP_PLANES)
        if (cap >= GL11.GL_CLIP_PLANE0 && cap < GL11.GL_CLIP_PLANE0 + MAX_CLIP_PLANES) {
            clipPlaneStates[cap - GL11.GL_CLIP_PLANE0].enable();
            return;
        }

        switch (cap) {
            case GL11.GL_ALPHA_TEST -> enableAlphaTest();
            case GL11.GL_AUTO_NORMAL -> autoNormalState.enable();
            case GL11.GL_BLEND -> enableBlend();
            case GL11.GL_COLOR_MATERIAL -> enableColorMaterial();
            case GL11.GL_COLOR_LOGIC_OP -> colorLogicOpState.enable();
            case GL11.GL_CULL_FACE -> enableCull();
            case GL11.GL_DEPTH_TEST -> enableDepthTest();
            case GL11.GL_DITHER -> ditherState.enable();
            case GL11.GL_FOG -> enableFog();
            case GL11.GL_INDEX_LOGIC_OP -> indexLogicOpState.enable();
            case GL11.GL_LIGHTING -> enableLighting();
            case GL11.GL_LIGHT0 -> enableLight(0);
            case GL11.GL_LIGHT1 -> enableLight(1);
            case GL11.GL_LIGHT2 -> enableLight(2);
            case GL11.GL_LIGHT3 -> enableLight(3);
            case GL11.GL_LIGHT4 -> enableLight(4);
            case GL11.GL_LIGHT5 -> enableLight(5);
            case GL11.GL_LIGHT6 -> enableLight(6);
            case GL11.GL_LIGHT7 -> enableLight(7);
            case GL11.GL_LINE_SMOOTH -> lineSmoothState.enable();
            case GL11.GL_LINE_STIPPLE -> lineStippleState.enable();
            case GL11.GL_MAP1_COLOR_4 -> map1Color4State.enable();
            case GL11.GL_MAP1_INDEX -> map1IndexState.enable();
            case GL11.GL_MAP1_NORMAL -> map1NormalState.enable();
            case GL11.GL_MAP1_TEXTURE_COORD_1 -> map1TextureCoord1State.enable();
            case GL11.GL_MAP1_TEXTURE_COORD_2 -> map1TextureCoord2State.enable();
            case GL11.GL_MAP1_TEXTURE_COORD_3 -> map1TextureCoord3State.enable();
            case GL11.GL_MAP1_TEXTURE_COORD_4 -> map1TextureCoord4State.enable();
            case GL11.GL_MAP1_VERTEX_3 -> map1Vertex3State.enable();
            case GL11.GL_MAP1_VERTEX_4 -> map1Vertex4State.enable();
            case GL11.GL_MAP2_COLOR_4 -> map2Color4State.enable();
            case GL11.GL_MAP2_INDEX -> map2IndexState.enable();
            case GL11.GL_MAP2_NORMAL -> map2NormalState.enable();
            case GL11.GL_MAP2_TEXTURE_COORD_1 -> map2TextureCoord1State.enable();
            case GL11.GL_MAP2_TEXTURE_COORD_2 -> map2TextureCoord2State.enable();
            case GL11.GL_MAP2_TEXTURE_COORD_3 -> map2TextureCoord3State.enable();
            case GL11.GL_MAP2_TEXTURE_COORD_4 -> map2TextureCoord4State.enable();
            case GL11.GL_MAP2_VERTEX_3 -> map2Vertex3State.enable();
            case GL11.GL_MAP2_VERTEX_4 -> map2Vertex4State.enable();
            case GL13.GL_MULTISAMPLE -> multisampleState.enable();
            case GL11.GL_NORMALIZE -> normalizeState.enable();
            case GL11.GL_POINT_SMOOTH -> pointSmoothState.enable();
            case GL11.GL_POLYGON_OFFSET_POINT -> polygonOffsetPointState.enable();
            case GL11.GL_POLYGON_OFFSET_LINE -> polygonOffsetLineState.enable();
            case GL11.GL_POLYGON_OFFSET_FILL -> polygonOffsetFillState.enable();
            case GL11.GL_POLYGON_SMOOTH -> polygonSmoothState.enable();
            case GL11.GL_POLYGON_STIPPLE -> polygonStippleState.enable();
            case GL12.GL_RESCALE_NORMAL -> enableRescaleNormal();
            case GL13.GL_SAMPLE_ALPHA_TO_COVERAGE -> sampleAlphaToCoverageState.enable();
            case GL13.GL_SAMPLE_ALPHA_TO_ONE -> sampleAlphaToOneState.enable();
            case GL13.GL_SAMPLE_COVERAGE -> sampleCoverageState.enable();
            case GL11.GL_SCISSOR_TEST -> enableScissorTest();
            case GL11.GL_STENCIL_TEST -> stencilTest.enable();
            case GL11.GL_TEXTURE_1D -> textures.getTexture1DStates(activeTextureUnit.getValue()).enable();
            case GL11.GL_TEXTURE_2D -> enableTexture();
            case GL12.GL_TEXTURE_3D -> textures.getTexture3DStates(activeTextureUnit.getValue()).enable();
            case GL11.GL_TEXTURE_GEN_S -> textures.getTexGenSStates(activeTextureUnit.getValue()).enable();
            case GL11.GL_TEXTURE_GEN_T -> textures.getTexGenTStates(activeTextureUnit.getValue()).enable();
            case GL11.GL_TEXTURE_GEN_R -> textures.getTexGenRStates(activeTextureUnit.getValue()).enable();
            case GL11.GL_TEXTURE_GEN_Q -> textures.getTexGenQStates(activeTextureUnit.getValue()).enable();
            default -> GL11.glEnable(cap);
        }
    }

    public static void glDisable(int cap) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordDisable(cap);
        }
        // Handle clip planes dynamically (supports up to MAX_CLIP_PLANES)
        if (cap >= GL11.GL_CLIP_PLANE0 && cap < GL11.GL_CLIP_PLANE0 + MAX_CLIP_PLANES) {
            clipPlaneStates[cap - GL11.GL_CLIP_PLANE0].disable();
            return;
        }

        switch (cap) {
            case GL11.GL_ALPHA_TEST -> disableAlphaTest();
            case GL11.GL_AUTO_NORMAL -> autoNormalState.disable();
            case GL11.GL_BLEND -> disableBlend();
            case GL11.GL_COLOR_MATERIAL -> disableColorMaterial();
            case GL11.GL_COLOR_LOGIC_OP -> colorLogicOpState.disable();
            case GL11.GL_CULL_FACE -> disableCull();
            case GL11.GL_DEPTH_TEST -> disableDepthTest();
            case GL11.GL_DITHER -> ditherState.disable();
            case GL11.GL_FOG -> disableFog();
            case GL11.GL_INDEX_LOGIC_OP -> indexLogicOpState.disable();
            case GL11.GL_LIGHTING -> disableLighting();
            case GL11.GL_LIGHT0 -> disableLight(0);
            case GL11.GL_LIGHT1 -> disableLight(1);
            case GL11.GL_LIGHT2 -> disableLight(2);
            case GL11.GL_LIGHT3 -> disableLight(3);
            case GL11.GL_LIGHT4 -> disableLight(4);
            case GL11.GL_LIGHT5 -> disableLight(5);
            case GL11.GL_LIGHT6 -> disableLight(6);
            case GL11.GL_LIGHT7 -> disableLight(7);
            case GL11.GL_LINE_SMOOTH -> lineSmoothState.disable();
            case GL11.GL_LINE_STIPPLE -> lineStippleState.disable();
            case GL11.GL_MAP1_COLOR_4 -> map1Color4State.disable();
            case GL11.GL_MAP1_INDEX -> map1IndexState.disable();
            case GL11.GL_MAP1_NORMAL -> map1NormalState.disable();
            case GL11.GL_MAP1_TEXTURE_COORD_1 -> map1TextureCoord1State.disable();
            case GL11.GL_MAP1_TEXTURE_COORD_2 -> map1TextureCoord2State.disable();
            case GL11.GL_MAP1_TEXTURE_COORD_3 -> map1TextureCoord3State.disable();
            case GL11.GL_MAP1_TEXTURE_COORD_4 -> map1TextureCoord4State.disable();
            case GL11.GL_MAP1_VERTEX_3 -> map1Vertex3State.disable();
            case GL11.GL_MAP1_VERTEX_4 -> map1Vertex4State.disable();
            case GL11.GL_MAP2_COLOR_4 -> map2Color4State.disable();
            case GL11.GL_MAP2_INDEX -> map2IndexState.disable();
            case GL11.GL_MAP2_NORMAL -> map2NormalState.disable();
            case GL11.GL_MAP2_TEXTURE_COORD_1 -> map2TextureCoord1State.disable();
            case GL11.GL_MAP2_TEXTURE_COORD_2 -> map2TextureCoord2State.disable();
            case GL11.GL_MAP2_TEXTURE_COORD_3 -> map2TextureCoord3State.disable();
            case GL11.GL_MAP2_TEXTURE_COORD_4 -> map2TextureCoord4State.disable();
            case GL11.GL_MAP2_VERTEX_3 -> map2Vertex3State.disable();
            case GL11.GL_MAP2_VERTEX_4 -> map2Vertex4State.disable();
            case GL13.GL_MULTISAMPLE -> multisampleState.disable();
            case GL11.GL_NORMALIZE -> normalizeState.disable();
            case GL11.GL_POINT_SMOOTH -> pointSmoothState.disable();
            case GL11.GL_POLYGON_OFFSET_POINT -> polygonOffsetPointState.disable();
            case GL11.GL_POLYGON_OFFSET_LINE -> polygonOffsetLineState.disable();
            case GL11.GL_POLYGON_OFFSET_FILL -> polygonOffsetFillState.disable();
            case GL11.GL_POLYGON_SMOOTH -> polygonSmoothState.disable();
            case GL11.GL_POLYGON_STIPPLE -> polygonStippleState.disable();
            case GL12.GL_RESCALE_NORMAL -> disableRescaleNormal();
            case GL13.GL_SAMPLE_ALPHA_TO_COVERAGE -> sampleAlphaToCoverageState.disable();
            case GL13.GL_SAMPLE_ALPHA_TO_ONE -> sampleAlphaToOneState.disable();
            case GL13.GL_SAMPLE_COVERAGE -> sampleCoverageState.disable();
            case GL11.GL_SCISSOR_TEST -> disableScissorTest();
            case GL11.GL_STENCIL_TEST -> stencilTest.disable();
            case GL11.GL_TEXTURE_1D -> textures.getTexture1DStates(activeTextureUnit.getValue()).disable();
            case GL11.GL_TEXTURE_2D -> disableTexture();
            case GL12.GL_TEXTURE_3D -> textures.getTexture3DStates(activeTextureUnit.getValue()).disable();
            case GL11.GL_TEXTURE_GEN_S -> textures.getTexGenSStates(activeTextureUnit.getValue()).disable();
            case GL11.GL_TEXTURE_GEN_T -> textures.getTexGenTStates(activeTextureUnit.getValue()).disable();
            case GL11.GL_TEXTURE_GEN_R -> textures.getTexGenRStates(activeTextureUnit.getValue()).disable();
            case GL11.GL_TEXTURE_GEN_Q -> textures.getTexGenQStates(activeTextureUnit.getValue()).disable();
            default -> GL11.glDisable(cap);
        }
    }

    public static boolean glIsEnabled(int cap) {
        if(shouldBypassCache()) {
            return GL11.glIsEnabled(cap);
        }
        // Handle clip planes dynamically (supports up to MAX_CLIP_PLANES)
        if (cap >= GL11.GL_CLIP_PLANE0 && cap < GL11.GL_CLIP_PLANE0 + MAX_CLIP_PLANES) {
            return clipPlaneStates[cap - GL11.GL_CLIP_PLANE0].isEnabled();
        }

        return switch (cap) {
            case GL11.GL_ALPHA_TEST -> alphaTest.isEnabled();
            case GL11.GL_AUTO_NORMAL -> autoNormalState.isEnabled();
            case GL11.GL_BLEND -> blendMode.isEnabled();
            case GL11.GL_COLOR_MATERIAL -> colorMaterial.isEnabled();
            case GL11.GL_COLOR_LOGIC_OP -> colorLogicOpState.isEnabled();
            case GL11.GL_CULL_FACE -> cullState.isEnabled();
            case GL11.GL_DEPTH_TEST -> depthTest.isEnabled();
            case GL11.GL_DITHER -> ditherState.isEnabled();
            case GL11.GL_FOG -> fogMode.isEnabled();
            case GL11.GL_INDEX_LOGIC_OP -> indexLogicOpState.isEnabled();
            case GL11.GL_LIGHTING -> lightingState.isEnabled();
            case GL11.GL_LIGHT0 -> lightStates[0].isEnabled();
            case GL11.GL_LIGHT1 -> lightStates[1].isEnabled();
            case GL11.GL_LIGHT2 -> lightStates[2].isEnabled();
            case GL11.GL_LIGHT3 -> lightStates[3].isEnabled();
            case GL11.GL_LIGHT4 -> lightStates[4].isEnabled();
            case GL11.GL_LIGHT5 -> lightStates[5].isEnabled();
            case GL11.GL_LIGHT6 -> lightStates[6].isEnabled();
            case GL11.GL_LIGHT7 -> lightStates[7].isEnabled();
            case GL11.GL_LINE_SMOOTH -> lineSmoothState.isEnabled();
            case GL11.GL_LINE_STIPPLE -> lineStippleState.isEnabled();
            case GL11.GL_MAP1_COLOR_4 -> map1Color4State.isEnabled();
            case GL11.GL_MAP1_INDEX -> map1IndexState.isEnabled();
            case GL11.GL_MAP1_NORMAL -> map1NormalState.isEnabled();
            case GL11.GL_MAP1_TEXTURE_COORD_1 -> map1TextureCoord1State.isEnabled();
            case GL11.GL_MAP1_TEXTURE_COORD_2 -> map1TextureCoord2State.isEnabled();
            case GL11.GL_MAP1_TEXTURE_COORD_3 -> map1TextureCoord3State.isEnabled();
            case GL11.GL_MAP1_TEXTURE_COORD_4 -> map1TextureCoord4State.isEnabled();
            case GL11.GL_MAP1_VERTEX_3 -> map1Vertex3State.isEnabled();
            case GL11.GL_MAP1_VERTEX_4 -> map1Vertex4State.isEnabled();
            case GL11.GL_MAP2_COLOR_4 -> map2Color4State.isEnabled();
            case GL11.GL_MAP2_INDEX -> map2IndexState.isEnabled();
            case GL11.GL_MAP2_NORMAL -> map2NormalState.isEnabled();
            case GL11.GL_MAP2_TEXTURE_COORD_1 -> map2TextureCoord1State.isEnabled();
            case GL11.GL_MAP2_TEXTURE_COORD_2 -> map2TextureCoord2State.isEnabled();
            case GL11.GL_MAP2_TEXTURE_COORD_3 -> map2TextureCoord3State.isEnabled();
            case GL11.GL_MAP2_TEXTURE_COORD_4 -> map2TextureCoord4State.isEnabled();
            case GL11.GL_MAP2_VERTEX_3 -> map2Vertex3State.isEnabled();
            case GL11.GL_MAP2_VERTEX_4 -> map2Vertex4State.isEnabled();
            case GL13.GL_MULTISAMPLE -> multisampleState.isEnabled();
            case GL11.GL_NORMALIZE -> normalizeState.isEnabled();
            case GL11.GL_POINT_SMOOTH -> pointSmoothState.isEnabled();
            case GL11.GL_POLYGON_OFFSET_POINT -> polygonOffsetPointState.isEnabled();
            case GL11.GL_POLYGON_OFFSET_LINE -> polygonOffsetLineState.isEnabled();
            case GL11.GL_POLYGON_OFFSET_FILL -> polygonOffsetFillState.isEnabled();
            case GL11.GL_POLYGON_SMOOTH -> polygonSmoothState.isEnabled();
            case GL11.GL_POLYGON_STIPPLE -> polygonStippleState.isEnabled();
            case GL12.GL_RESCALE_NORMAL -> rescaleNormalState.isEnabled();
            case GL13.GL_SAMPLE_ALPHA_TO_COVERAGE -> sampleAlphaToCoverageState.isEnabled();
            case GL13.GL_SAMPLE_ALPHA_TO_ONE -> sampleAlphaToOneState.isEnabled();
            case GL13.GL_SAMPLE_COVERAGE -> sampleCoverageState.isEnabled();
            case GL11.GL_SCISSOR_TEST -> scissorTest.isEnabled();
            case GL11.GL_STENCIL_TEST -> stencilTest.isEnabled();
            case GL11.GL_TEXTURE_1D -> textures.getTexture1DStates(activeTextureUnit.getValue()).isEnabled();
            case GL11.GL_TEXTURE_2D -> textures.getTextureUnitStates(activeTextureUnit.getValue()).isEnabled();
            case GL12.GL_TEXTURE_3D -> textures.getTexture3DStates(activeTextureUnit.getValue()).isEnabled();
            case GL11.GL_TEXTURE_GEN_S -> textures.getTexGenSStates(activeTextureUnit.getValue()).isEnabled();
            case GL11.GL_TEXTURE_GEN_T -> textures.getTexGenTStates(activeTextureUnit.getValue()).isEnabled();
            case GL11.GL_TEXTURE_GEN_R -> textures.getTexGenRStates(activeTextureUnit.getValue()).isEnabled();
            case GL11.GL_TEXTURE_GEN_Q -> textures.getTexGenQStates(activeTextureUnit.getValue()).isEnabled();
            default -> GL11.glIsEnabled(cap);
        };
    }

    public static boolean glGetBoolean(int pname) {
        if(shouldBypassCache()) {
            return GL11.glGetBoolean(pname);
        }
        // Handle clip planes dynamically (supports up to MAX_CLIP_PLANES)
        if (pname >= GL11.GL_CLIP_PLANE0 && pname < GL11.GL_CLIP_PLANE0 + MAX_CLIP_PLANES) {
            return clipPlaneStates[pname - GL11.GL_CLIP_PLANE0].isEnabled();
        }

        return switch (pname) {
            case GL11.GL_ALPHA_TEST -> alphaTest.isEnabled();
            case GL11.GL_AUTO_NORMAL -> autoNormalState.isEnabled();
            case GL11.GL_BLEND -> blendMode.isEnabled();
            case GL11.GL_COLOR_MATERIAL -> colorMaterial.isEnabled();
            case GL11.GL_COLOR_LOGIC_OP -> colorLogicOpState.isEnabled();
            case GL11.GL_CULL_FACE -> cullState.isEnabled();
            case GL11.GL_DEPTH_TEST -> depthTest.isEnabled();
            case GL11.GL_DEPTH_WRITEMASK -> depthState.isEnabled();
            case GL11.GL_DITHER -> ditherState.isEnabled();
            case GL11.GL_FOG -> fogMode.isEnabled();
            case GL11.GL_INDEX_LOGIC_OP -> indexLogicOpState.isEnabled();
            case GL11.GL_LIGHTING -> lightingState.isEnabled();
            case GL11.GL_LIGHT0 -> lightStates[0].isEnabled();
            case GL11.GL_LIGHT1 -> lightStates[1].isEnabled();
            case GL11.GL_LIGHT2 -> lightStates[2].isEnabled();
            case GL11.GL_LIGHT3 -> lightStates[3].isEnabled();
            case GL11.GL_LIGHT4 -> lightStates[4].isEnabled();
            case GL11.GL_LIGHT5 -> lightStates[5].isEnabled();
            case GL11.GL_LIGHT6 -> lightStates[6].isEnabled();
            case GL11.GL_LIGHT7 -> lightStates[7].isEnabled();
            case GL11.GL_LINE_SMOOTH -> lineSmoothState.isEnabled();
            case GL11.GL_LINE_STIPPLE -> lineStippleState.isEnabled();
            case GL11.GL_MAP1_COLOR_4 -> map1Color4State.isEnabled();
            case GL11.GL_MAP1_INDEX -> map1IndexState.isEnabled();
            case GL11.GL_MAP1_NORMAL -> map1NormalState.isEnabled();
            case GL11.GL_MAP1_TEXTURE_COORD_1 -> map1TextureCoord1State.isEnabled();
            case GL11.GL_MAP1_TEXTURE_COORD_2 -> map1TextureCoord2State.isEnabled();
            case GL11.GL_MAP1_TEXTURE_COORD_3 -> map1TextureCoord3State.isEnabled();
            case GL11.GL_MAP1_TEXTURE_COORD_4 -> map1TextureCoord4State.isEnabled();
            case GL11.GL_MAP1_VERTEX_3 -> map1Vertex3State.isEnabled();
            case GL11.GL_MAP1_VERTEX_4 -> map1Vertex4State.isEnabled();
            case GL11.GL_MAP2_COLOR_4 -> map2Color4State.isEnabled();
            case GL11.GL_MAP2_INDEX -> map2IndexState.isEnabled();
            case GL11.GL_MAP2_NORMAL -> map2NormalState.isEnabled();
            case GL11.GL_MAP2_TEXTURE_COORD_1 -> map2TextureCoord1State.isEnabled();
            case GL11.GL_MAP2_TEXTURE_COORD_2 -> map2TextureCoord2State.isEnabled();
            case GL11.GL_MAP2_TEXTURE_COORD_3 -> map2TextureCoord3State.isEnabled();
            case GL11.GL_MAP2_TEXTURE_COORD_4 -> map2TextureCoord4State.isEnabled();
            case GL11.GL_MAP2_VERTEX_3 -> map2Vertex3State.isEnabled();
            case GL11.GL_MAP2_VERTEX_4 -> map2Vertex4State.isEnabled();
            case GL13.GL_MULTISAMPLE -> multisampleState.isEnabled();
            case GL11.GL_NORMALIZE -> normalizeState.isEnabled();
            case GL11.GL_POINT_SMOOTH -> pointSmoothState.isEnabled();
            case GL11.GL_POLYGON_OFFSET_POINT -> polygonOffsetPointState.isEnabled();
            case GL11.GL_POLYGON_OFFSET_LINE -> polygonOffsetLineState.isEnabled();
            case GL11.GL_POLYGON_OFFSET_FILL -> polygonOffsetFillState.isEnabled();
            case GL11.GL_POLYGON_SMOOTH -> polygonSmoothState.isEnabled();
            case GL11.GL_POLYGON_STIPPLE -> polygonStippleState.isEnabled();
            case GL12.GL_RESCALE_NORMAL -> rescaleNormalState.isEnabled();
            case GL13.GL_SAMPLE_ALPHA_TO_COVERAGE -> sampleAlphaToCoverageState.isEnabled();
            case GL13.GL_SAMPLE_ALPHA_TO_ONE -> sampleAlphaToOneState.isEnabled();
            case GL13.GL_SAMPLE_COVERAGE -> sampleCoverageState.isEnabled();
            case GL11.GL_SCISSOR_TEST -> scissorTest.isEnabled();
            case GL11.GL_STENCIL_TEST -> stencilTest.isEnabled();
            case GL11.GL_TEXTURE_1D -> textures.getTexture1DStates(activeTextureUnit.getValue()).isEnabled();
            case GL11.GL_TEXTURE_2D -> textures.getTextureUnitStates(activeTextureUnit.getValue()).isEnabled();
            case GL12.GL_TEXTURE_3D -> textures.getTexture3DStates(activeTextureUnit.getValue()).isEnabled();
            case GL11.GL_TEXTURE_GEN_S -> textures.getTexGenSStates(activeTextureUnit.getValue()).isEnabled();
            case GL11.GL_TEXTURE_GEN_T -> textures.getTexGenTStates(activeTextureUnit.getValue()).isEnabled();
            case GL11.GL_TEXTURE_GEN_R -> textures.getTexGenRStates(activeTextureUnit.getValue()).isEnabled();
            case GL11.GL_TEXTURE_GEN_Q -> textures.getTexGenQStates(activeTextureUnit.getValue()).isEnabled();
            default -> GL11.glGetBoolean(pname);
        };
    }

    public static void glGetBoolean(int pname, ByteBuffer params) {
        if(shouldBypassCache()) {
            GL11.glGetBoolean(pname, params);
            return;
        }

        switch (pname) {
            case GL11.GL_COLOR_WRITEMASK -> {
                params.put((byte) (colorMask.red ? GL11.GL_TRUE : GL11.GL_FALSE));
                params.put((byte) (colorMask.green ? GL11.GL_TRUE : GL11.GL_FALSE));
                params.put((byte) (colorMask.blue ? GL11.GL_TRUE : GL11.GL_FALSE));
                params.put((byte) (colorMask.alpha ? GL11.GL_TRUE : GL11.GL_FALSE));
            }
            default -> {
                if(!HAS_MULTIPLE_SET.contains(pname)) {
                    params.put(0, (byte) (glGetBoolean(pname) ? GL11.GL_TRUE : GL11.GL_FALSE));
                } else {
                    GL11.glGetBoolean(pname, params);
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
        if(shouldBypassCache()) {
            return GL11.glGetInteger(pname);
        }

        return switch (pname) {
            case GL11.GL_ALPHA_TEST_FUNC -> alphaState.getFunction();
            case GL11.GL_DEPTH_FUNC -> depthState.getFunc();
            case GL11.GL_LIST_MODE -> DisplayListManager.getListMode();
            case GL11.GL_MATRIX_MODE -> matrixMode.getMode();
            case GL11.GL_SHADE_MODEL -> shadeModelState.getValue();
            case GL11.GL_TEXTURE_BINDING_2D -> getBoundTextureForServerState();
            case GL14.GL_BLEND_DST_ALPHA -> blendState.getDstAlpha();
            case GL14.GL_BLEND_DST_RGB -> blendState.getDstRgb();
            case GL14.GL_BLEND_SRC_ALPHA -> blendState.getSrcAlpha();
            case GL14.GL_BLEND_SRC_RGB -> blendState.getSrcRgb();
            case GL11.GL_COLOR_MATERIAL_FACE -> colorMaterialFace.getValue();
            case GL11.GL_COLOR_MATERIAL_PARAMETER -> colorMaterialParameter.getValue();
            case GL20.GL_CURRENT_PROGRAM -> activeProgram;
            case GL11.GL_MODELVIEW_STACK_DEPTH -> getMatrixStackDepth(modelViewMatrix);
            case GL11.GL_PROJECTION_STACK_DEPTH -> getMatrixStackDepth(projectionMatrix);

            default -> GL11.glGetInteger(pname);
        };
    }

    public static void glGetInteger(int pname, IntBuffer params) {
        if(shouldBypassCache()) {
            GL11.glGetInteger(pname, params);
            return;
        }

        switch (pname) {
            case GL11.GL_VIEWPORT -> viewportState.get(params);
            default -> {
                if(!HAS_MULTIPLE_SET.contains(pname)) {
                    params.put(0, glGetInteger(pname));
                } else {
                    GL11.glGetInteger(pname, params);
                }
            }
        }
    }

    public static void glGetMaterial(int face, int pname, FloatBuffer params) {
        if (shouldBypassCache()) {
            GL11.glGetMaterial(face, pname, params);
            return;
        }

        final MaterialStateStack state;
        if (face == GL11.GL_FRONT) {
            state = frontMaterial;
        } else  if (face == GL11.GL_BACK) {
            state = backMaterial;
        } else {
            throw new RuntimeException("Invalid face parameter specified to glGetMaterial: " + face);
        }

        switch (pname) {
            case GL11.GL_AMBIENT -> state.ambient.get(0, params);
            case GL11.GL_DIFFUSE -> state.diffuse.get(0, params);
            case GL11.GL_SPECULAR -> state.specular.get(0, params);
            case GL11.GL_EMISSION -> state.emission.get(0, params);
            case GL11.GL_SHININESS -> params.put(state.shininess);
            case GL11.GL_COLOR_INDEXES -> state.colorIndexes.get(0, params);
            default -> GL11.glGetMaterial(face, pname, params);
        }
    }

    public static void glGetLight(int light, int pname, FloatBuffer params) {
        if (shouldBypassCache()) {
            GL11.glGetLight(light, pname, params);
            return;
        }

        final LightStateStack state = lightDataStates[light - GL11.GL_LIGHT0];
        switch (pname) {
            case GL11.GL_AMBIENT -> state.ambient.get(0, params);
            case GL11.GL_DIFFUSE -> state.diffuse.get(0, params);
            case GL11.GL_SPECULAR -> state.specular.get(0, params);
            case GL11.GL_POSITION -> state.position.get(0, params);
            case GL11.GL_SPOT_DIRECTION -> state.spotDirection.get(0, params);
            case GL11.GL_SPOT_EXPONENT -> params.put(state.spotExponent);
            case GL11.GL_SPOT_CUTOFF -> params.put(state.spotCutoff);
            case GL11.GL_CONSTANT_ATTENUATION -> params.put(state.constantAttenuation);
            case GL11.GL_LINEAR_ATTENUATION -> params.put(state.linearAttenuation);
            case GL11.GL_QUADRATIC_ATTENUATION -> params.put(state.quadraticAttenuation);
            default -> GL11.glGetLight(light, pname, params);
        }
    }

    public static void glGetFloat(int pname, FloatBuffer params) {
        if(shouldBypassCache()) {
            GL11.glGetFloat(pname, params);
            return;
        }

        switch (pname) {
            case GL11.GL_MODELVIEW_MATRIX -> modelViewMatrix.get(0, params);
            case GL11.GL_PROJECTION_MATRIX -> projectionMatrix.get(0, params);
//            case GL11.GL_TEXTURE_MATRIX -> textures.getTextureUnitMatrix(getActiveTextureUnit()).get(0, params);
            case GL11.GL_COLOR_CLEAR_VALUE -> clearColor.get(params);
            case GL11.GL_CURRENT_COLOR -> color.get(params);
            default -> {
                if(!HAS_MULTIPLE_SET.contains(pname)) {
                    params.put(0, glGetFloat(pname));
                } else {
                    GL11.glGetFloat(pname, params);
                }
            }
        }
    }

    public static float glGetFloat(int pname) {
        return GL11.glGetFloat(pname);
    }

    // GLStateManager Functions

    public static void glBlendColor(float red, float green, float blue, float alpha) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordBlendColor(red, green, blue, alpha);
        } else {
            GL14.glBlendColor(red, green, blue, alpha);
        }
    }

    public static void enableBlend() {
        if (Iris.enabled) {
            if (BlendModeStorage.isBlendLocked()) {
                BlendModeStorage.deferBlendModeToggle(true);
                return;
            }
        }
        blendMode.enable();
    }

    public static void disableBlend() {
        if (Iris.enabled) {
            if (BlendModeStorage.isBlendLocked()) {
                BlendModeStorage.deferBlendModeToggle(false);
                return;
            }
        }
        blendMode.disable();
    }

    public static void enableScissorTest() {
        scissorTest.enable();
    }

    public static void disableScissorTest() {
        scissorTest.disable();
    }

    public static void glBlendFunc(int srcFactor, int dstFactor) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordBlendFunc(srcFactor, dstFactor, srcFactor, dstFactor);
        }
        if (Iris.enabled) {
            if (BlendModeStorage.isBlendLocked()) {
                BlendModeStorage.deferBlendFunc(srcFactor, dstFactor, srcFactor, dstFactor);
                return;
            }
        }
        // Cache thread check - only update state on main thread, but always make GL call if needed
        final boolean caching = isCachingEnabled();
        if (HUDCaching.renderingCacheOverride) {
            if (caching) blendState.setAll(srcFactor, dstFactor, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            // Skip actual GL call during display list recording (state tracking only)
            if (!isRecordingDisplayList()) {
                OpenGlHelper.glBlendFunc(srcFactor, dstFactor, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            }
            return;
        }
        final boolean bypass = BYPASS_CACHE || !caching;
        if (bypass || blendState.getSrcRgb() != srcFactor || blendState.getDstRgb() != dstFactor) {
            if (caching) blendState.setSrcDstRgb(srcFactor, dstFactor);
            // Skip actual GL call during display list recording (state tracking only)
            if (!isRecordingDisplayList()) {
                GL11.glBlendFunc(srcFactor, dstFactor);
            }
        }

        // Iris
        if (blendFuncListener != null) blendFuncListener.run();
    }

    public static void glBlendEquation(int mode) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glBlendEquation in display lists not yet implemented - if you see this, please report!");
        }
        GL14.glBlendEquation(mode);
    }

    public static void glBlendEquationSeparate(int modeRGB, int modeAlpha) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glBlendEquationSeparate in display lists not yet implemented - if you see this, please report!");
        }
        GL20.glBlendEquationSeparate(modeRGB, modeAlpha);
    }

    public static void tryBlendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordBlendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha);
        }
        if (Iris.enabled) {
            if (BlendModeStorage.isBlendLocked()) {
                BlendModeStorage.deferBlendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha);
                return;
            }
        }
        if (HUDCaching.renderingCacheOverride && dstAlpha != GL11.GL_ONE_MINUS_SRC_ALPHA) {
            srcAlpha = GL11.GL_ONE;
            dstAlpha = GL11.GL_ONE_MINUS_SRC_ALPHA;
        }
        // Cache thread check - only update state on main thread, but always make GL call if needed
        final boolean caching = isCachingEnabled();
        final boolean bypass = BYPASS_CACHE || !caching;
        if (bypass || blendState.getSrcRgb() != srcRgb || blendState.getDstRgb() != dstRgb || blendState.getSrcAlpha() != srcAlpha || blendState.getDstAlpha() != dstAlpha) {
            if (caching) blendState.setAll(srcRgb, dstRgb, srcAlpha, dstAlpha);
            // Skip actual GL call during display list recording (state tracking only)
            if (!isRecordingDisplayList()) {
                OpenGlHelper.glBlendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha);
            }
        }

        // Iris
        if (blendFuncListener != null) blendFuncListener.run();
    }

    public static void checkCompiling() {
        checkCompiling("");
    }
    public static void checkCompiling(String name) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("Not yet implemented (" + name + ")");
        }
    }
    public static void glNormal3b(byte nx, byte ny, byte nz) {
        if (DisplayListManager.isRecording()) {
            final ImmediateModeRecorder recorder = DisplayListManager.getImmediateModeRecorder();
            if (recorder != null) {
                recorder.setNormal(b2f(nx), b2f(ny), b2f(nz));
            }
            return;
        }
        GL11.glNormal3b(nx, ny, nz);
    }
    public static void glNormal3d(double nx, double ny, double nz) {
        if (DisplayListManager.isRecording()) {
            final ImmediateModeRecorder recorder = DisplayListManager.getImmediateModeRecorder();
            if (recorder != null) {
                recorder.setNormal((float) nx, (float) ny, (float) nz);
            }
            return;
        }
        GL11.glNormal3d(nx, ny, nz);
    }
    public static void glNormal3f(float nx, float ny, float nz) {
        if (DisplayListManager.isRecording()) {
            final ImmediateModeRecorder recorder = DisplayListManager.getImmediateModeRecorder();
            if (recorder != null) {
                recorder.setNormal(nx, ny, nz);
            }
            return;
        }
        GL11.glNormal3f(nx, ny, nz);
    }
    public static void glNormal3i(int nx, int ny, int nz) {
        if (DisplayListManager.isRecording()) {
            final ImmediateModeRecorder recorder = DisplayListManager.getImmediateModeRecorder();
            if (recorder != null) {
                recorder.setNormal((float) nx, (float) ny, (float) nz);
            }
            return;
        }
        GL11.glNormal3i(nx, ny, nz);
    }

    public static void glDepthFunc(int func) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordDepthFunc(func);
        }
        final boolean caching = isCachingEnabled();
        if (BYPASS_CACHE || !caching || func != depthState.getFunc()) {
            if (caching) depthState.setFunc(func);
            if (!isRecordingDisplayList()) {
                GL11.glDepthFunc(func);
            }
        }
    }

    public static void glDepthMask(boolean mask) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordDepthMask(mask);
        }
        if (Iris.enabled) {
            if (DepthColorStorage.isDepthColorLocked()) {
                DepthColorStorage.deferDepthEnable(mask);
                return;
            }
        }
        final boolean caching = isCachingEnabled();
        if (BYPASS_CACHE || !caching || mask != depthState.isEnabled()) {
            if (caching) depthState.setEnabled(mask);
            if (!isRecordingDisplayList()) {
                GL11.glDepthMask(mask);
            }
        }
    }

    public static void glEdgeFlag(boolean flag) {
        GL11.glEdgeFlag(flag);
    }

    public static void glColor4f(float red, float green, float blue, float alpha) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordColor(red, green, blue, alpha);
            changeColor(red, green, blue, alpha);  // Update state for glGet queries
        } else {
            if (changeColor(red, green, blue, alpha)) {
                GL11.glColor4f(red, green, blue, alpha);
            }
        }
    }

    public static void glColor4d(double red, double green, double blue, double alpha) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordColor((float) red, (float) green, (float) blue, (float) alpha);
            changeColor((float) red, (float) green, (float) blue, (float) alpha);  // Update state for glGet queries
        } else {
            if (changeColor((float) red, (float) green, (float) blue, (float) alpha)) {
                GL11.glColor4d(red, green, blue, alpha);
            }
        }
    }

    public static void glColor4b(byte red, byte green, byte blue, byte alpha) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordColor(b2f(red), b2f(green), b2f(blue), b2f(alpha));
            changeColor(b2f(red), b2f(green), b2f(blue), b2f(alpha));  // Update state for glGet queries
        } else {
            if (changeColor(b2f(red), b2f(green), b2f(blue), b2f(alpha))) {
                GL11.glColor4b(red, green, blue, alpha);
            }
        }
    }

    public static void glColor4ub(byte red, byte green, byte blue, byte alpha) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordColor(ub2f(red), ub2f(green), ub2f(blue), ub2f(alpha));
            changeColor(ub2f(red), ub2f(green), ub2f(blue), ub2f(alpha));  // Update state for glGet queries
        } else {
            if (changeColor(ub2f(red), ub2f(green), ub2f(blue), ub2f(alpha))) {
                GL11.glColor4ub(red, green, blue, alpha);
            }
        }
    }

    public static void glColor3f(float red, float green, float blue) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordColor(red, green, blue, 1.0F);
            changeColor(red, green, blue, 1.0F);  // Update state for glGet queries
        } else {
            if (changeColor(red, green, blue, 1.0F)) {
                GL11.glColor3f(red, green, blue);
            }
        }
    }

    public static void glColor3d(double red, double green, double blue) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordColor((float) red, (float) green, (float) blue, 1.0F);
            changeColor((float) red, (float) green, (float) blue, 1.0F);  // Update state for glGet queries
        } else {
            if (changeColor((float) red, (float) green, (float) blue, 1.0F)) {
                GL11.glColor3d(red, green, blue);
            }
        }
    }

    public static void glColor3b(byte red, byte green, byte blue) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordColor(b2f(red), b2f(green), b2f(blue), 1.0F);
            changeColor(b2f(red), b2f(green), b2f(blue), 1.0F);  // Update state for immediate mode vertex capture
        } else {
            if (changeColor(b2f(red), b2f(green), b2f(blue), 1.0F)) {
                GL11.glColor3b(red, green, blue);
            }
        }
    }

    public static void glColor3ub(byte red, byte green, byte blue) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordColor(ub2f(red), ub2f(green), ub2f(blue), 1.0F);
            changeColor(ub2f(red), ub2f(green), ub2f(blue), 1.0F);  // Update state for immediate mode vertex capture
        } else {
            if (changeColor(ub2f(red), ub2f(green), ub2f(blue), 1.0F)) {
                GL11.glColor3ub(red, green, blue);
            }
        }
    }

    private static float ub2f(byte b) {
        return (b & 0xFF) / 255.0F;
    }

    private static float b2f(byte b) {
        return ((b - Byte.MIN_VALUE) & 0xFF) / 255.0F;
    }

    public static float i2f(int i) { return ((i - Integer.MIN_VALUE) & 0xFFFFFFFFL) / 4294967295.0F; }

    private static boolean changeColor(float red, float green, float blue, float alpha) {
        // Helper function for glColor*
        final boolean caching = isCachingEnabled();
        if (BYPASS_CACHE || !caching || red != color.getRed() || green != color.getGreen() || blue != color.getBlue() || alpha != color.getAlpha()) {
            if (caching) {
                color.setRed(red);
                color.setGreen(green);
                color.setBlue(blue);
                color.setAlpha(alpha);
            }
            return true;
        }
        return false;
    }

    private static final Color4 DirtyColor = new Color4(-1.0F, -1.0F, -1.0F, -1.0F);
    public static void clearCurrentColor() {
        // Marks the cache dirty, doesn't actually reset the color
        color.set(DirtyColor);
    }

    public static void glColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordColorMask(red, green, blue, alpha);
        }
        if (Iris.enabled) {
            if (DepthColorStorage.isDepthColorLocked()) {
                DepthColorStorage.deferColorMask(red, green, blue, alpha);
                return;
            }
        }
        // Cache thread check - only update state on main thread, but always make GL call if needed
        final boolean caching = isCachingEnabled();
        final boolean bypass = BYPASS_CACHE || !caching;
        if (bypass || red != colorMask.red || green != colorMask.green || blue != colorMask.blue || alpha != colorMask.alpha) {
            if (caching) colorMask.setAll(red, green, blue, alpha);
            if (!isRecordingDisplayList()) {
                GL11.glColorMask(red, green, blue, alpha);
            }
        }
    }

    // Clear Color
    public static void glClearColor(float red, float green, float blue, float alpha) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordClearColor(red, green, blue, alpha);
            // Update state for glGet queries (recording only happens on main thread)
            clearColor.setRed(red);
            clearColor.setGreen(green);
            clearColor.setBlue(blue);
            clearColor.setAlpha(alpha);
        } else {
            final boolean caching = isCachingEnabled();
            if (BYPASS_CACHE || !caching || red != clearColor.getRed() || green != clearColor.getGreen() || blue != clearColor.getBlue() || alpha != clearColor.getAlpha()) {
                if (caching) {
                    clearColor.setRed(red);
                    clearColor.setGreen(green);
                    clearColor.setBlue(blue);
                    clearColor.setAlpha(alpha);
                }
                GL11.glClearColor(red, green, blue, alpha);
            }
        }
    }

    public static void glClearDepth(double depth) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordClearDepth(depth);
        } else {
            GL11.glClearDepth(depth);
        }
    }

    // ALPHA
    public static void enableAlphaTest() {
        if (Iris.enabled) {
            if (AlphaTestStorage.isAlphaTestLocked()) {
                AlphaTestStorage.deferAlphaTestToggle(true);
                return;
            }
        }
        alphaTest.enable();
    }

    public static void disableAlphaTest() {
        if (Iris.enabled) {
            if (AlphaTestStorage.isAlphaTestLocked()) {
                AlphaTestStorage.deferAlphaTestToggle(false);
                return;
            }
        }
        alphaTest.disable();
    }

    public static void glAlphaFunc(int function, float reference) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordAlphaFunc(function, reference);
        }
        if (Iris.enabled) {
            if (AlphaTestStorage.isAlphaTestLocked()) {
                AlphaTestStorage.deferAlphaFunc(function, reference);
                return;
            }
        }
        if (isCachingEnabled()) {
            alphaState.setFunction(function);
            alphaState.setReference(reference);
        }
        if (!isRecordingDisplayList()) {
            GL11.glAlphaFunc(function, reference);
        }
    }

    // Textures
    public static void glActiveTexture(int texture) {
        // Recording mode: record command and update state tracking (only on main thread)
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordActiveTexture(texture);
            final int newTexture = texture - GL13.GL_TEXTURE0;
            activeTextureUnit.setValue(newTexture);  // Update state for glGet queries
            return;  // Don't execute during recording
        }
        final int newTexture = texture - GL13.GL_TEXTURE0;
        final boolean caching = isCachingEnabled();
        if (BYPASS_CACHE || !caching || getActiveTextureUnit() != newTexture) {
            if (caching) activeTextureUnit.setValue(newTexture);
            GL13.glActiveTexture(texture);
        }
    }

    public static void glActiveTextureARB(int texture) {
        // Recording mode: record command and update state tracking (only on main thread)
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordActiveTexture(texture);
            final int newTexture = texture - GL13.GL_TEXTURE0;
            activeTextureUnit.setValue(newTexture);  // Update state for glGet queries
            return;  // Don't execute during recording
        }
        final int newTexture = texture - GL13.GL_TEXTURE0;
        final boolean caching = isCachingEnabled();
        if (BYPASS_CACHE || !caching || getActiveTextureUnit() != newTexture) {
            if (caching) activeTextureUnit.setValue(newTexture);
            ARBMultitexture.glActiveTextureARB(texture);
        }
    }

    private static int getBoundTexture() {
        return getBoundTexture(activeTextureUnit.getValue());
    }

    private static int getBoundTexture(int unit) {
        return textures.getTextureUnitBindings(unit).getBinding();
    }

    public static void glBindTexture(int target, int texture) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordBindTexture(target, texture);
        }
        if (target != GL11.GL_TEXTURE_2D) {
            // We're only supporting 2D textures for now
            if (!isRecordingDisplayList()) {
                GL11.glBindTexture(target, texture);
            }
            LOGGER.info("SKIPPING glBindTexture for target {}", target);
            return;
        }

        if (!isCachingEnabled()) {
            GL11.glBindTexture(target, texture);
            return;
        }

        final int activeUnit = GLStateManager.activeTextureUnit.getValue();
        final TextureBinding textureUnit = textures.getTextureUnitBindings(activeUnit);
        final int cachedBinding = textureUnit.getBinding();

        if (cachedBinding != texture) {
            if (!isRecordingDisplayList()) {
                GL11.glBindTexture(target, texture);
            }
            textureUnit.setBinding(texture);
            TextureTracker.INSTANCE.onBindTexture(texture);
        }
    }

    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, IntBuffer pixels) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordComplexCommand(TexImage2DCmd.fromIntBuffer(target, level, internalformat, width, height, border, format, type, pixels));
        }
        // Always update cache for glGet queries
        TextureInfoCache.INSTANCE.onTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
        if (shouldUseDSA(target)) {
            // Use DSA to upload directly to the texture
            RenderSystem.textureImage2D(getBoundTextureForServerState(), target, level, internalformat, width, height, border, format, type, pixels);
        } else {
            // Non-main thread or proxy texture - use direct GL call
            GL11.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
        }
    }

    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, FloatBuffer pixels) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordComplexCommand(TexImage2DCmd.fromFloatBuffer(target, level, internalformat, width, height, border, format, type, pixels));
        }
        // Always update cache for glGet queries
        TextureInfoCache.INSTANCE.onTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
        // FloatBuffer not in DSA interface - use direct GL call
        GL11.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
    }

    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, DoubleBuffer pixels) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordComplexCommand(TexImage2DCmd.fromDoubleBuffer(target, level, internalformat, width, height, border, format, type, pixels));
        }
        // Always update cache for glGet queries
        TextureInfoCache.INSTANCE.onTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
        // DoubleBuffer not in DSA interface - use direct GL call
        GL11.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
    }

    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, ByteBuffer pixels) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordComplexCommand(TexImage2DCmd.fromByteBuffer(target, level, internalformat, width, height, border, format, type, pixels));
        }
        // Always update cache for glGet queries
        TextureInfoCache.INSTANCE.onTexImage2D(target, level, internalformat, width, height, border, format, type, pixels != null ? pixels.asIntBuffer() : null);
        if (shouldUseDSA(target)) {
            // Use DSA to upload directly to the texture - keeps GL binding state unchanged
            RenderSystem.textureImage2D(getBoundTextureForServerState(), target, level, internalformat, width, height, border, format, type, pixels);
        } else {
            // Non-main thread or proxy texture - use direct GL call
            GL11.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
        }
    }

    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, long pixels_buffer_offset) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glTexImage2D with buffer offset in display lists not yet supported");
        }
        TextureInfoCache.INSTANCE.onTexImage2D(target, level, internalformat, width, height, border, format, type, pixels_buffer_offset);
        GL11.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels_buffer_offset);
    }

    public static void glTexCoord1f(float s) {
        if (DisplayListManager.isRecording()) {
            final ImmediateModeRecorder recorder = DisplayListManager.getImmediateModeRecorder();
            if (recorder != null) {
                recorder.setTexCoord(s, 0.0f);
            }
            return;
        }
        GL11.glTexCoord1f(s);
    }
    public static void glTexCoord1d(double s) {
        if (DisplayListManager.isRecording()) {
            final ImmediateModeRecorder recorder = DisplayListManager.getImmediateModeRecorder();
            if (recorder != null) {
                recorder.setTexCoord((float) s, 0.0f);
            }
            return;
        }
        GL11.glTexCoord1d(s);
    }
    public static void glTexCoord2f(float s, float t) {
        if (DisplayListManager.isRecording()) {
            final ImmediateModeRecorder recorder = DisplayListManager.getImmediateModeRecorder();
            if (recorder != null) {
                recorder.setTexCoord(s, t);
            }
            return;
        }
        GL11.glTexCoord2f(s, t);
    }
    public static void glTexCoord2d(double s, double t) {
        if (DisplayListManager.isRecording()) {
            final ImmediateModeRecorder recorder = DisplayListManager.getImmediateModeRecorder();
            if (recorder != null) {
                recorder.setTexCoord((float) s, (float) t);
            }
            return;
        }
        GL11.glTexCoord2d(s, t);
    }
    public static void glTexCoord3f(float s, float t, float r) {
        if (DisplayListManager.isRecording()) {
            final ImmediateModeRecorder recorder = DisplayListManager.getImmediateModeRecorder();
            if (recorder != null) {
                recorder.setTexCoord(s, t);  // Only track s,t for 2D textures
            }
            return;
        }
        GL11.glTexCoord3f(s, t, r);
    }
    public static void glTexCoord3d(double s, double t, double r) {
        if (DisplayListManager.isRecording()) {
            final ImmediateModeRecorder recorder = DisplayListManager.getImmediateModeRecorder();
            if (recorder != null) {
                recorder.setTexCoord((float) s, (float) t);
            }
            return;
        }
        GL11.glTexCoord3d(s, t, r);
    }
    public static void glTexCoord4f(float s, float t, float r, float q) {
        if (DisplayListManager.isRecording()) {
            final ImmediateModeRecorder recorder = DisplayListManager.getImmediateModeRecorder();
            if (recorder != null) {
                recorder.setTexCoord(s, t);
            }
            return;
        }
        GL11.glTexCoord4f(s, t, r, q);
    }
    public static void glTexCoord4d(double s, double t, double r, double q) {
        if (DisplayListManager.isRecording()) {
            final ImmediateModeRecorder recorder = DisplayListManager.getImmediateModeRecorder();
            if (recorder != null) {
                recorder.setTexCoord((float) s, (float) t);
            }
            return;
        }
        GL11.glTexCoord4d(s, t, r, q);
    }

    public static void glDeleteTextures(int id) {
        onDeleteTexture(id);

        GL11.glDeleteTextures(id);
    }

    public static void glDeleteTextures(IntBuffer ids) {
        for(int i = 0; i < ids.remaining(); i++) {
            onDeleteTexture(ids.get(i));
        }

        GL11.glDeleteTextures(ids);
    }

    public static void enableTexture() {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordEnable(GL11.GL_TEXTURE_2D);
        }
        final int textureUnit = getActiveTextureUnit();
        if (Iris.enabled) {
            // Iris
            boolean updatePipeline = false;
            if (textureUnit == IrisSamplers.ALBEDO_TEXTURE_UNIT) {
                StateTracker.INSTANCE.albedoSampler = true;
                updatePipeline = true;
            } else if (textureUnit == IrisSamplers.LIGHTMAP_TEXTURE_UNIT) {
                StateTracker.INSTANCE.lightmapSampler = true;
                updatePipeline = true;
            }

            if (updatePipeline) {
                Iris.getPipelineManager().getPipeline().ifPresent(p -> p.setInputs(StateTracker.INSTANCE.getInputs()));
            }
        }
        textures.getTextureUnitStates(textureUnit).enable();
    }

    public static void disableTexture() {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordDisable(GL11.GL_TEXTURE_2D);
        }
        final int textureUnit = getActiveTextureUnit();
        if (Iris.enabled) {
            // Iris
            boolean updatePipeline = false;
            if (textureUnit == IrisSamplers.ALBEDO_TEXTURE_UNIT) {
                StateTracker.INSTANCE.albedoSampler = false;
                updatePipeline = true;
            } else if (textureUnit == IrisSamplers.LIGHTMAP_TEXTURE_UNIT) {
                StateTracker.INSTANCE.lightmapSampler = false;
                updatePipeline = true;
            }

            if (updatePipeline) {
                Iris.getPipelineManager().getPipeline().ifPresent(p -> p.setInputs(StateTracker.INSTANCE.getInputs()));
            }
        }
        textures.getTextureUnitStates(textureUnit).disable();
    }

    public static void glRasterPos2f(float x, float y) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glRasterPos in display lists not yet implemented");
        }
        GL11.glRasterPos2f(x, y);
    }
    public static void glRasterPos2d(double x, double y) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glRasterPos in display lists not yet implemented");
        }
        GL11.glRasterPos2d(x, y);
    }
    public static void glRasterPos2i(int x, int y) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glRasterPos in display lists not yet implemented");
        }
        GL11.glRasterPos2i(x, y);
    }
    public static void glRasterPos3f(float x, float y, float z) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glRasterPos in display lists not yet implemented");
        }
        GL11.glRasterPos3f(x, y, z);
    }
    public static void glRasterPos3d(double x, double y, double z) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glRasterPos in display lists not yet implemented");
        }
        GL11.glRasterPos3d(x, y, z);
    }
    public static void glRasterPos3i(int x, int y, int z) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glRasterPos in display lists not yet implemented");
        }
        GL11.glRasterPos3i(x, y, z);
    }
    public static void glRasterPos4f(float x, float y, float z, float w) {
        checkCompiling("glRasterPos");
        GL11.glRasterPos4f(x, y, z, w);
    }
    public static void glRasterPos4d(double x, double y, double z, double w) {
        checkCompiling("glRasterPos");
        GL11.glRasterPos4d(x, y, z, w);
    }
    public static void glRasterPos4i(int x, int y, int z, int w) {
        checkCompiling("glRasterPos");
        GL11.glRasterPos4i(x, y, z, w);
    }


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

    public static void trySyncProgram() {
        if (Iris.enabled) {
            Iris.getPipelineManager().getPipeline().ifPresent(WorldRenderingPipeline::syncProgram);
        }
    }

    public static void glBegin(int mode) {
        if (DisplayListManager.isRecording()) {
            // Record to immediate mode recorder
            final ImmediateModeRecorder recorder = DisplayListManager.getImmediateModeRecorder();
            if (recorder != null) {
                recorder.begin(mode);
            }
            return;  // Don't call actual GL during recording
        }
        trySyncProgram();
        GL11.glBegin(mode);
    }

    public static void glEnd() {
        if (DisplayListManager.isRecording()) {
            // Record to immediate mode recorder and flush geometry immediately
            final ImmediateModeRecorder recorder = DisplayListManager.getImmediateModeRecorder();
            if (recorder != null) {
                // end() returns quads immediately (like tessellator callback)
                final ImmediateModeRecorder.Result result = recorder.end();
                if (result != null) {
                    // Add draw at current command position for correct interleaving
                    DisplayListManager.addImmediateModeDraw(result);
                }
            }
            return;  // Don't call actual GL during recording
        }
        GL11.glEnd();
    }

    // Vertex methods for display list recording
    private static boolean loggedVertex = false;
    public static void glVertex2f(float x, float y) {
        final boolean recording = DisplayListManager.isRecording();
        if (recording) {
            final ImmediateModeRecorder recorder = DisplayListManager.getImmediateModeRecorder();
            if (recorder != null) {
                recorder.vertex(x, y, 0.0f);
            }
            return;
        }
        GL11.glVertex2f(x, y);
    }

    public static void glVertex2d(double x, double y) {
        if (DisplayListManager.isRecording()) {
            final ImmediateModeRecorder recorder = DisplayListManager.getImmediateModeRecorder();
            if (recorder != null) {
                recorder.vertex((float) x, (float) y, 0.0f);
            }
            return;
        }
        GL11.glVertex2d(x, y);
    }

    public static void glVertex3f(float x, float y, float z) {
        if (DisplayListManager.isRecording()) {
            final ImmediateModeRecorder recorder = DisplayListManager.getImmediateModeRecorder();
            if (recorder != null) {
                recorder.vertex(x, y, z);
            }
            return;
        }
        GL11.glVertex3f(x, y, z);
    }

    public static void glVertex3d(double x, double y, double z) {
        if (DisplayListManager.isRecording()) {
            final ImmediateModeRecorder recorder = DisplayListManager.getImmediateModeRecorder();
            if (recorder != null) {
                recorder.vertex((float) x, (float) y, (float) z);
            }
            return;
        }
        GL11.glVertex3d(x, y, z);
    }
    public static void glDrawElements(int mode, ByteBuffer indices) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glDrawElements in display lists not yet implemented - if you see this, please report!");
        }
        trySyncProgram();
        GL11.glDrawElements(mode, indices);
    }

    public static void glDrawElements(int mode, IntBuffer indices) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glDrawElements in display lists not yet implemented - if you see this, please report!");
        }
        trySyncProgram();
        GL11.glDrawElements(mode, indices);
    }

    public static void glDrawElements(int mode, ShortBuffer indices) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glDrawElements in display lists not yet implemented - if you see this, please report!");
        }
        trySyncProgram();
        GL11.glDrawElements(mode, indices);
    }

    public static void glDrawElements(int mode, int indices_count, int type, long indices_buffer_offset) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glDrawElements in display lists not yet implemented - if you see this, please report!");
        }
        trySyncProgram();
        GL11.glDrawElements(mode, indices_count, type, indices_buffer_offset);
    }

    public static void glDrawElements(int mode, int count, int type, ByteBuffer indices) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glDrawElements in display lists not yet implemented - if you see this, please report!");
        }
        trySyncProgram();
        GL11.glDrawElements(mode, count, type, indices);
    }

    public static void glDrawBuffer(int mode) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordDrawBuffer(mode);
        }
        trySyncProgram();
        GL11.glDrawBuffer(mode);
    }

    public static void glDrawArrays(int mode, int first, int count) {
        if (DisplayListManager.isRecording()) {
            final ImmediateModeRecorder recorder = DisplayListManager.getImmediateModeRecorder();
            if (recorder != null) {
                final ImmediateModeRecorder.Result result = recorder.processDrawArrays(mode, first, count);
                if (result != null) {
                    DisplayListManager.addImmediateModeDraw(result);
                }
            }
            return;
        }
        trySyncProgram();
        GL11.glDrawArrays(mode, first, count);
    }

    // Client array state interception methods (for glDrawArrays conversion)
    // State is tracked globally in clientArrayState, which ImmediateModeRecorder reads from directly.

    public static void glVertexPointer(int size, int stride, FloatBuffer pointer) {
        clientArrayState.setVertexPointer(size, GL11.GL_FLOAT, stride, pointer);
        GL11.glVertexPointer(size, stride, pointer);
    }

    public static void glVertexPointer(int size, int type, int stride, ByteBuffer pointer) {
        clientArrayState.setVertexPointer(size, type, stride, pointer);
        GL11.glVertexPointer(size, type, stride, pointer);
    }

    public static void glVertexPointer(int size, int type, int stride, long pointer_buffer_offset) {
        // VBO offset - can't track buffer reference, clear it
        clientArrayState.setVertexPointer(size, type, stride, null);
        GL11.glVertexPointer(size, type, stride, pointer_buffer_offset);
    }

    public static void glColorPointer(int size, int stride, FloatBuffer pointer) {
        clientArrayState.setColorPointer(size, GL11.GL_FLOAT, stride, pointer);
        GL11.glColorPointer(size, stride, pointer);
    }

    public static void glColorPointer(int size, int type, int stride, ByteBuffer pointer) {
        clientArrayState.setColorPointer(size, type, stride, pointer);
        GL11.glColorPointer(size, type, stride, pointer);
    }

    public static void glColorPointer(int size, boolean unsigned, int stride, ByteBuffer pointer) {
        final int type = unsigned ? GL11.GL_UNSIGNED_BYTE : GL11.GL_BYTE;
        clientArrayState.setColorPointer(size, type, stride, pointer);
        GL11.glColorPointer(size, unsigned, stride, pointer);
    }

    public static void glColorPointer(int size, int type, int stride, long pointer_buffer_offset) {
        // VBO offset - can't track buffer reference, clear it
        clientArrayState.setColorPointer(size, type, stride, null);
        GL11.glColorPointer(size, type, stride, pointer_buffer_offset);
    }

    public static void glEnableClientState(int cap) {
        if (cap == GL11.GL_VERTEX_ARRAY) clientArrayState.setVertexArrayEnabled(true);
        else if (cap == GL11.GL_COLOR_ARRAY) clientArrayState.setColorArrayEnabled(true);
        GL11.glEnableClientState(cap);
    }

    public static void glDisableClientState(int cap) {
        if (cap == GL11.GL_VERTEX_ARRAY) clientArrayState.setVertexArrayEnabled(false);
        else if (cap == GL11.GL_COLOR_ARRAY) clientArrayState.setColorArrayEnabled(false);
        GL11.glDisableClientState(cap);
    }

    public static void glLogicOp(int opcode) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordLogicOp(opcode);
            return;
        }
        GL11.glLogicOp(opcode);
    }

    public static void defaultBlendFunc() {
        tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
    }

    public static void enableCull() {
        cullState.enable();
    }

    public static void disableCull() {
        cullState.disable();
    }

    public static void enableDepthTest() {
        depthTest.enable();
    }

    public static void disableDepthTest() {
        depthTest.disable();
    }

    public static void enableLighting() {
        lightingState.enable();
    }

    public static void enableLight(int light) {
        lightStates[light].enable();
    }

    public static void enableColorMaterial() {
        colorMaterial.enable();
        float r = getColor().getRed();
        float g = getColor().getGreen();
        float b = getColor().getBlue();
        float a = getColor().getAlpha();
        if (colorMaterialFace.getValue() == GL11.GL_FRONT || colorMaterialFace.getValue() == GL11.GL_FRONT_AND_BACK) {
            switch (colorMaterialParameter.getValue()) {
                case GL11.GL_AMBIENT_AND_DIFFUSE -> {
                    frontMaterial.ambient.set(r, g, b, a);
                    frontMaterial.diffuse.set(r, g, b, a);
                }
                case GL11.GL_AMBIENT -> frontMaterial.ambient.set(r, g, b, a);
                case GL11.GL_DIFFUSE -> frontMaterial.diffuse.set(r, g, b, a);
                case GL11.GL_SPECULAR -> frontMaterial.specular.set(r, g, b, a);
                case GL11.GL_EMISSION -> frontMaterial.emission.set(r, g, b, a);
            }
        }
        if (colorMaterialFace.getValue() == GL11.GL_BACK || colorMaterialFace.getValue() == GL11.GL_FRONT_AND_BACK) {
            switch (colorMaterialParameter.getValue()) {
                case GL11.GL_AMBIENT_AND_DIFFUSE -> {
                    backMaterial.ambient.set(r, g, b, a);
                    backMaterial.diffuse.set(r, g, b, a);
                }
                case GL11.GL_AMBIENT -> backMaterial.ambient.set(r, g, b, a);
                case GL11.GL_DIFFUSE -> backMaterial.diffuse.set(r, g, b, a);
                case GL11.GL_SPECULAR -> backMaterial.specular.set(r, g, b, a);
                case GL11.GL_EMISSION -> backMaterial.emission.set(r, g, b, a);
            }
        }
    }

    public static void disableColorMaterial() {
        colorMaterial.disable();
    }

    public static void disableLighting() {
        lightingState.disable();
    }

    public static void disableLight(int light) {
        lightStates[light].disable();
    }

    public static void enableRescaleNormal() {
        rescaleNormalState.enable();
    }

    public static void disableRescaleNormal() {
        rescaleNormalState.disable();
    }

    public static void enableFog() {
        fogMode.enable();
        if (fogToggleListener != null) {
            fogToggleListener.run();
        }
    }

    public static void disableFog() {
        fogMode.disable();
        if (fogToggleListener != null) {
            fogToggleListener.run();
        }
    }

    public static void glFog(int pname, FloatBuffer param) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordFog(pname, param);
        }
        // TODO: Iris Notifier
        if (HAS_MULTIPLE_SET.contains(pname)) {
            if (!isRecordingDisplayList()) {
                GL11.glFog(pname, param);
            }
            // Only update cached state on main thread
            if (pname == GL11.GL_FOG_COLOR && isCachingEnabled()) {
                final float red = param.get(0);
                final float green = param.get(1);
                final float blue = param.get(2);

                fogState.getFogColor().set(red, green, blue);
                fogState.setFogAlpha(param.get(3));
                fogState.getFogColorBuffer().clear();
                fogState.getFogColorBuffer().put((FloatBuffer) param.position(0)).flip();
            }
        } else {
            GLStateManager.glFogf(pname, param.get(0));
        }
    }

    public static Vector3d getFogColor() {
        return fogState.getFogColor();
    }

    private static final FloatBuffer fogColorTempBuffer = BufferUtils.createFloatBuffer(4);
    public static void fogColor(float red, float green, float blue, float alpha) {
        final boolean caching = isCachingEnabled();
        if (BYPASS_CACHE || !caching || red != fogState.getFogColor().x || green != fogState.getFogColor().y || blue != fogState.getFogColor().z || alpha != fogState.getFogAlpha()) {
            if (caching) {
                fogState.getFogColor().set(red, green, blue);
                fogState.setFogAlpha(alpha);
                fogState.getFogColorBuffer().clear();
                fogState.getFogColorBuffer().put(red).put(green).put(blue).put(alpha).flip();
                GL11.glFog(GL11.GL_FOG_COLOR, fogState.getFogColorBuffer());
            } else {
                // Use temp buffer for non-main thread to avoid corrupting shared state
                fogColorTempBuffer.clear();
                fogColorTempBuffer.put(red).put(green).put(blue).put(alpha).flip();
                GL11.glFog(GL11.GL_FOG_COLOR, fogColorTempBuffer);
            }
        }
    }

    public static void glFogf(int pname, float param) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordFogf(pname, param);
        } else {
            GL11.glFogf(pname, param);
        }
        // Note: Does not handle GL_FOG_INDEX
        // Only update cached state when caching is enabled
        if (isCachingEnabled()) {
            switch (pname) {
                case GL11.GL_FOG_DENSITY -> {
                    fogState.setDensity(param);
                    if (fogDensityListener != null) {
                        fogDensityListener.run();
                    }
                }
                case GL11.GL_FOG_START -> {
                    fogState.setStart(param);
                    if (fogStartListener != null) {
                        fogStartListener.run();
                    }
                }
                case GL11.GL_FOG_END -> {
                    fogState.setEnd(param);
                    if (fogEndListener != null) {
                        fogEndListener.run();
                    }
                }
            }
        }
    }

    public static void glFogi(int pname, int param) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordFogi(pname, param);
        }
        GL11.glFogi(pname, param);
        // Only update cached state when caching is enabled
        if (isCachingEnabled() && pname == GL11.GL_FOG_MODE) {
            fogState.setFogMode(param);
            if (fogModeListener != null) {
                fogModeListener.run();
            }
        }
    }

    public static void setFogBlack() {
        glFogf(GL11.GL_FOG_COLOR, 0.0F);
    }

    public static void glShadeModel(int mode) {
        final boolean recording = DisplayListManager.isRecording();
        if (recording) {
            DisplayListManager.recordShadeModel(mode);
        }
        final boolean caching = isCachingEnabled();
        final int oldValue = shadeModelState.getValue();
        final boolean needsUpdate = BYPASS_CACHE || !caching || oldValue != mode;

        if (needsUpdate) {
            if (caching) {
                shadeModelState.setValue(mode);
            }
            if (!isRecordingDisplayList()) {
                GL11.glShadeModel(mode);
            }
        }
    }

    // Iris Functions
    private static void onDeleteTexture(int id) {
        TextureTracker.INSTANCE.onDeleteTexture(id);
        TextureInfoCache.INSTANCE.onDeleteTexture(id);
        if (Iris.enabled) {
            PBRTextureManager.INSTANCE.onDeleteTexture(id);
        }

        // Only update cached texture bindings on main thread
        if (isCachingEnabled()) {
            for (int i = 0; i < GLStateManager.MAX_TEXTURE_UNITS; i++) {
                if (textures.getTextureUnitBindings(i).getBinding() == id) {
                    textures.getTextureUnitBindings(i).setBinding(0);
                }
            }
        }
    }

    public static void makeCurrent(Drawable drawable) throws LWJGLException {
        drawable.makeCurrent();
        CurrentThread = Thread.currentThread();

        // During splash, track which thread holds DrawableGL for caching
        if (!splashComplete && sharedDrawable != null) {
            if (drawable == sharedDrawable) {
                // Switching to SharedDrawable - disable caching for this thread
                if (drawableGLHolder == CurrentThread) {
                    drawableGLHolder = null;
                }
            } else {
                // Switching to DrawableGL - enable caching for this thread
                drawableGLHolder = CurrentThread;
            }
        }
    }

    /**
     * Set whether the current thread holds DrawableGL (the main display context).
     * Called by mixins at context switch points during splash.
     */
    public static void setCachingEnabled(boolean enabled) {
        final Thread current = Thread.currentThread();
        if (enabled) {
            drawableGLHolder = current;
        } else if (drawableGLHolder == current) {
            drawableGLHolder = null;
        }
    }

    /**
     * Mark splash as complete - enables fast path that always caches.
     * Called when finish() permanently switches to DrawableGL for the main game loop.
     */
    public static void markSplashComplete() {
        splashComplete = true;
        drawableGLHolder = null;
        sharedDrawable = null;
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

    /**
     * Delete display lists and free their VBO resources.
     */
    public static void glDeleteLists(int list, int range) {
        DisplayListManager.glDeleteLists(list, range);
    }

    /**
     * Get a compiled display list from the cache.
     * Used by CallListCmd to execute nested display lists with the unoptimized version.
     * @param list The display list ID
     * @return The CompiledDisplayList, or null if not found
     */
    public static CompiledDisplayList getDisplayList(int list) {
        return DisplayListManager.getDisplayList(list);
    }

    public static void glCallList(int list) {
        DisplayListManager.glCallList(list);
    }

    public static void pushState(int mask) {
        if (attribDepth >= MAX_ATTRIB_STACK_DEPTH) {
            throw new IllegalStateException("Attrib stack overflow: max depth " + MAX_ATTRIB_STACK_DEPTH + " reached");
        }
        attribs.push(mask);

        // Clear modified list for this depth level
        modifiedAtDepth[attribDepth].clear();
        attribDepth++;

        // Only iterate non-boolean stacks - BooleanStateStack uses global depth tracking
        // so pushDepth() is a no-op for them
        final IStateStack<?>[] nonBooleanStacks = Feature.maskToNonBooleanStacks(mask);
        for (IStateStack<?> stack : nonBooleanStacks) {
            stack.pushDepth();
        }
    }

    public static void popState() {
        final int mask = attribs.popInt();
        attribDepth--;

        // First: restore BooleanStateStack states that were actually modified (fast path)
        // These use lazy copy-on-write with global depth tracking
        final List<IStateStack<?>> modified = modifiedAtDepth[attribDepth];
        for (int i = 0; i < modified.size(); i++) {
            modified.get(i).popDepth();
        }
        modified.clear();

        // Second: restore non-boolean state stacks the traditional way
        final IStateStack<?>[] nonBooleanStacks = Feature.maskToNonBooleanStacks(mask);
        for (IStateStack<?> stack : nonBooleanStacks) {
            stack.popDepth();
        }
    }

    public static void glClear(int mask) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordClear(mask);
        }
        // Always execute - clears are immediate operations
        GL11.glClear(mask);
    }
    public static void glPushAttrib(int mask) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordPushAttrib(mask);
        } else {
            GL11.glPushAttrib(mask);
        }
        // Only track on main thread (splash thread has separate GL context)
        if (isCachingEnabled()) {
            pushState(mask);
        }
    }

    public static void glPopAttrib() {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordPopAttrib();
        } else {
            GL11.glPopAttrib();
        }
        // Only track on main thread (splash thread has separate GL context)
        if (isCachingEnabled()) {
            poppingAttributes = true;
            popState();
            poppingAttributes = false;
        }
    }

    // Matrix Operations
    public static void glMatrixMode(int mode) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordMatrixMode(mode);
        }
        matrixMode.setMode(mode);
    }

    /**
     * Check if the current matrix mode is MODELVIEW.
     * Used by DisplayListManager to determine which transforms to track.
     */
    public static boolean isModelViewMatrix() {
        return matrixMode.getMode() == GL11.GL_MODELVIEW;
    }

    public static void glLoadMatrix(FloatBuffer m) {
        if (DisplayListManager.isRecording()) {
            final Matrix4f matrix = new Matrix4f().set(m);
            m.rewind();
            DisplayListManager.recordLoadMatrix(matrixMode.getMode(), matrix);
            // Reset relative transform for MODELVIEW - subsequent transforms are relative to loaded matrix
            if (isModelViewMatrix()) {
                DisplayListManager.resetRelativeTransform();
            }
            return;
        }
        if (isCachingEnabled()) getMatrixStack().set(m);
        GL11.glLoadMatrix(m);
    }

    public static void glLoadMatrix(DoubleBuffer m) {
        if (DisplayListManager.isRecording()) {
            // Convert double buffer to float buffer for recording
            conversionMatrix4d.set(m);
            m.rewind();
            final Matrix4f floatMatrix = new Matrix4f();
            floatMatrix.set(conversionMatrix4d);
            DisplayListManager.recordLoadMatrix(matrixMode.getMode(), floatMatrix);
            // Reset relative transform for MODELVIEW - subsequent transforms are relative to loaded matrix
            if (isModelViewMatrix()) {
                DisplayListManager.resetRelativeTransform();
            }
            return;
        }
        if (isCachingEnabled()) {
            conversionMatrix4d.set(m);
            getMatrixStack().set(conversionMatrix4d);
        }
        GL11.glLoadMatrix(m);
    }

    public static Matrix4fStack getMatrixStack() {
        switch (matrixMode.getMode()) {
            case GL11.GL_MODELVIEW -> {
                return modelViewMatrix;
            }
            case GL11.GL_PROJECTION -> {
                return projectionMatrix;
            }
            case GL11.GL_TEXTURE -> {
                return textures.getTextureUnitMatrix(getActiveTextureUnit());
            }
            default -> throw new IllegalStateException("Unknown matrix mode: " + matrixMode.getMode());
        }
    }

    public static void glLoadIdentity() {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordLoadIdentity(matrixMode.getMode());
        } else {
            GL11.glLoadIdentity();
        }
        if (isCachingEnabled()) getMatrixStack().identity();
    }

    public static void glTranslatef(float x, float y, float z) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordTranslate(matrixMode.getMode(), x, y, z);
            DisplayListManager.updateRelativeTransform(x, y, z, DisplayListManager.TransformOp.TRANSLATE, null);
        } else {
            GL11.glTranslatef(x, y, z);
        }
        if (isCachingEnabled()) getMatrixStack().translate(x, y, z);
    }

    public static void glTranslated(double x, double y, double z) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordTranslate(matrixMode.getMode(), x, y, z);
            DisplayListManager.updateRelativeTransform((float) x, (float) y, (float) z, DisplayListManager.TransformOp.TRANSLATE, null);
        } else {
            GL11.glTranslated(x, y, z);
        }
        if (isCachingEnabled()) getMatrixStack().translate((float) x, (float) y, (float) z);
    }

    public static void glScalef(float x, float y, float z) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordScale(matrixMode.getMode(), x, y, z);
            DisplayListManager.updateRelativeTransform(x, y, z, DisplayListManager.TransformOp.SCALE, null);
        } else {
            GL11.glScalef(x, y, z);
        }
        if (isCachingEnabled()) getMatrixStack().scale(x, y, z);
    }

    public static void glScaled(double x, double y, double z) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordScale(matrixMode.getMode(), x, y, z);
            DisplayListManager.updateRelativeTransform((float) x, (float) y, (float) z, DisplayListManager.TransformOp.SCALE, null);
        } else {
            GL11.glScaled(x, y, z);
        }
        if (isCachingEnabled()) getMatrixStack().scale((float) x, (float) y, (float) z);
    }

    private static final Matrix4f multMatrix = new Matrix4f();
    public static void glMultMatrix(FloatBuffer floatBuffer) {
        multMatrix.set(floatBuffer);
        final int currentMode = matrixMode.getMode();

        if (DisplayListManager.isRecording()) {
            // Record with the mode it's being applied to
            DisplayListManager.recordMultMatrix(currentMode, multMatrix);

            // Only track if MODELVIEW (for baking into VBO vertices)
            if (currentMode == GL11.GL_MODELVIEW) {
                DisplayListManager.updateRelativeTransform(multMatrix);
            }
        } else {
            GL11.glMultMatrix(floatBuffer);
        }
        if (isCachingEnabled()) getMatrixStack().mul(multMatrix);
    }

    public static final Matrix4d conversionMatrix4d = new Matrix4d();
    public static final Matrix4f conversionMatrix4f = new Matrix4f();
    public static void glMultMatrix(DoubleBuffer matrix) {
        conversionMatrix4d.set(matrix);
        conversionMatrix4f.set(conversionMatrix4d);
        final int currentMode = matrixMode.getMode();

        if (DisplayListManager.isRecording()) {
            // Record with the mode it's being applied to
            DisplayListManager.recordMultMatrix(currentMode, conversionMatrix4f);

            // Only track if MODELVIEW (for baking into VBO vertices)
            if (currentMode == GL11.GL_MODELVIEW) {
                DisplayListManager.updateRelativeTransform(conversionMatrix4f);
            }
        } else {
            GL11.glMultMatrix(matrix);
        }
        if (isCachingEnabled()) getMatrixStack().mul(conversionMatrix4f);
    }

    private static final Vector3f rotation = new Vector3f();
    public static void glRotatef(float angle, float x, float y, float z) {
        final boolean recording = DisplayListManager.isRecording();
        final boolean caching = isCachingEnabled();

        if (recording) {
            DisplayListManager.recordRotate(matrixMode.getMode(), angle, x, y, z);
        } else {
            GL11.glRotatef(angle, x, y, z);
        }

        // Compute rotation vector once for relative transform tracking and/or matrix stack
        if (recording || caching) {
            rotation.set(x, y, z).normalize();
            if (recording) {
                DisplayListManager.updateRelativeTransform(angle, 0, 0, DisplayListManager.TransformOp.ROTATE, rotation);
            }
            if (caching) {
                getMatrixStack().rotate((float)Math.toRadians(angle), rotation);
            }
        }
    }

    public static void glRotated(double angle, double x, double y, double z) {
        final boolean recording = DisplayListManager.isRecording();
        final boolean caching = isCachingEnabled();

        if (recording) {
            DisplayListManager.recordRotate(matrixMode.getMode(), angle, x, y, z);
        } else {
            GL11.glRotated(angle, x, y, z);
        }

        // Compute rotation vector once for relative transform tracking and/or matrix stack
        if (recording || caching) {
            rotation.set((float) x, (float) y, (float) z).normalize();
            if (recording) {
                DisplayListManager.updateRelativeTransform((float) angle, 0, 0, DisplayListManager.TransformOp.ROTATE, rotation);
            }
            if (caching) {
                getMatrixStack().rotate((float)Math.toRadians(angle), rotation);
            }
        }
    }

    public static void glOrtho(double left, double right, double bottom, double top, double zNear, double zFar) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordOrtho(left, right, bottom, top, zNear, zFar);
        } else {
            GL11.glOrtho(left, right, bottom, top, zNear, zFar);
        }
        if (isCachingEnabled()) getMatrixStack().ortho((float)left, (float)right, (float)bottom, (float)top, (float)zNear, (float)zFar);
    }

    public static void glFrustum(double left, double right, double bottom, double top, double zNear, double zFar) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordFrustum(left, right, bottom, top, zNear, zFar);
        } else {
            GL11.glFrustum(left, right, bottom, top, zNear, zFar);
        }
        if (isCachingEnabled()) getMatrixStack().frustum((float)left, (float)right, (float)bottom, (float)top, (float)zNear, (float)zFar);
    }
    public static void glPushMatrix() {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordPushMatrix(matrixMode.getMode());
            DisplayListManager.pushRelativeTransform();  // Track MODELVIEW transform stack
        } else {
            GL11.glPushMatrix();
        }
        // Only track stack on main thread (splash thread has separate GL context)
        if (isCachingEnabled()) {
            try {
                getMatrixStack().pushMatrix();
            } catch(IllegalStateException ignored) {
                if(AngelicaMod.lwjglDebug)
                    AngelicaTweaker.LOGGER.warn("Matrix stack overflow ", new Throwable());
            }
        }
    }

    public static void glPopMatrix() {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordPopMatrix(matrixMode.getMode());
            DisplayListManager.popRelativeTransform();  // Track MODELVIEW transform stack
        } else {
            GL11.glPopMatrix();
        }
        // Only track stack on main thread (splash thread has separate GL context)
        if (isCachingEnabled()) {
            try {
                getMatrixStack().popMatrix();
            } catch(IllegalStateException ignored) {
                if(AngelicaMod.lwjglDebug)
                    AngelicaTweaker.LOGGER.warn("Matrix stack underflow ", new Throwable());
            }
        }
    }

    private static final Matrix4f perspectiveMatrix = new Matrix4f();
    private static final FloatBuffer perspectiveBuffer = BufferUtils.createFloatBuffer(16);
    public static void gluPerspective(float fovy, float aspect, float zNear, float zFar) {
        perspectiveMatrix.identity().perspective((float)Math.toRadians(fovy), aspect, zNear, zFar);

        perspectiveMatrix.get(0, perspectiveBuffer);
        GLStateManager.glMultMatrix(perspectiveBuffer);
    }

    public static void glViewport(int x, int y, int width, int height) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordViewport(x, y, width, height);
        } else {
            GL11.glViewport(x, y, width, height);
        }
        // Only update cached state when caching is enabled
        if (isCachingEnabled()) {
            viewportState.setViewPort(x, y, width, height);
        }
    }

    public static int getActiveTextureUnit() {
        return activeTextureUnit.getValue();
    }

    public static int getListMode() {
        return DisplayListManager.getListMode();
    }


    public static boolean updateTexParameteriCache(int target, int texture, int pname, int param) {
        if (target != GL11.GL_TEXTURE_2D) {
            return true;
        }
        final TextureInfo info = TextureInfoCache.INSTANCE.getInfo(texture);
        if (info == null) {
            return true;
        }
        switch (pname) {
            case GL11.GL_TEXTURE_MIN_FILTER -> {
                if(info.getMinFilter() == param && !shouldBypassCache()) return false;
                info.setMinFilter(param);
            }
            case GL11.GL_TEXTURE_MAG_FILTER -> {
                if(info.getMagFilter() == param && !shouldBypassCache()) return false;
                info.setMagFilter(param);
            }
            case GL11.GL_TEXTURE_WRAP_S -> {
                if(info.getWrapS() == param && !shouldBypassCache()) return false;
                info.setWrapS(param);
            }
            case GL11.GL_TEXTURE_WRAP_T -> {
                if(info.getWrapT() == param && !shouldBypassCache()) return false;
                info.setWrapT(param);
            }
            case GL12.GL_TEXTURE_MAX_LEVEL -> {
                if(info.getMaxLevel() == param && !shouldBypassCache()) return false;
                info.setMaxLevel(param);
            }
            case GL12.GL_TEXTURE_MIN_LOD -> {
                if(info.getMinLod() == param && !shouldBypassCache()) return false;
                info.setMinLod(param);
            }
            case GL12.GL_TEXTURE_MAX_LOD -> {
                if(info.getMaxLod() == param && !shouldBypassCache()) return false;
                info.setMaxLod(param);
            }
        }
        return true;
    }


    public static void glTexParameter(int target, int pname, IntBuffer params) {
        if (target != GL11.GL_TEXTURE_2D || params.remaining() != 1 ) {
            GL11.glTexParameter(target, pname, params);
            return;
        }
        if(!updateTexParameteriCache(target, getBoundTextureForServerState(), pname, params.get(0))) return;

        GL11.glTexParameter(target, pname, params);
    }

    public static void glTexParameter(int target, int pname, FloatBuffer params) {
        if (target != GL11.GL_TEXTURE_2D || params.remaining() != 1 ) {
            GL11.glTexParameter(target, pname, params);
            return;
        }
        if(!updateTexParameterfCache(target, getBoundTextureForServerState(), pname, params.get(0))) return;

        GL11.glTexParameter(target, pname, params);
    }

    public static void glTexParameteri(int target, int pname, int param) {
        // Recording mode: record command but continue to update state tracking
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordTexParameteri(target, pname, param);
            // Don't return - fall through to update cache for glGet queries
        }
        if (target != GL11.GL_TEXTURE_2D) {
            // Skip actual GL call during display list recording (state tracking only)
            if (!isRecordingDisplayList()) {
                GL11.glTexParameteri(target, pname, param);
            }
            return;
        }
        if (!updateTexParameteriCache(target, getBoundTextureForServerState(), pname, param)) return;

        // Skip actual GL call during display list recording (state tracking only)
        if (!isRecordingDisplayList()) {
            GL11.glTexParameteri(target, pname, param);
        }
    }


    public static boolean updateTexParameterfCache(int target, int texture, int pname, float param) {
        if (target != GL11.GL_TEXTURE_2D) {
            return true;
        }
        final TextureInfo info = TextureInfoCache.INSTANCE.getInfo(texture);
        if (info == null) {
            return true;
        }
        switch (pname) {
            case EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT -> {
                if(info.getMaxAnisotropy() == param && !shouldBypassCache()) return false;
                info.setMaxAnisotropy(param);
            }
            case GL14.GL_TEXTURE_LOD_BIAS -> {
                if(info.getLodBias() == param && !shouldBypassCache()) return false;
                info.setLodBias(param);
            }
        }
        return true;
    }

    public static void glTexParameterf(int target, int pname, float param) {
        // Recording mode: record command but continue to update state tracking
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordTexParameterf(target, pname, param);
            // Don't return - fall through to update cache for glGet queries
        }
        if (target != GL11.GL_TEXTURE_2D) {
            // Skip actual GL call during display list recording (state tracking only)
            if (!isRecordingDisplayList()) {
                GL11.glTexParameterf(target, pname, param);
            }
            return;
        }
        if (!updateTexParameterfCache(target, getBoundTextureForServerState(), pname, param)) return;

        // Skip actual GL call during display list recording (state tracking only)
        if (!isRecordingDisplayList()) {
            GL11.glTexParameterf(target, pname, param);
        }
    }

    public static int getTexParameterOrDefault(int texture, int pname, IntSupplier defaultSupplier) {
        final TextureInfo info = TextureInfoCache.INSTANCE.getInfo(texture);
        if (info == null) {
            if (isRecordingDisplayList()) {
                throw new IllegalStateException(String.format(
                    "glGetTexParameteri called during display list recording with no cached TextureInfo for texture %d. " +
                    "Cannot query OpenGL state during compilation!", texture));
            }
            return defaultSupplier.getAsInt();
        }
        return switch (pname) {
            case GL11.GL_TEXTURE_MIN_FILTER -> info.getMinFilter();
            case GL11.GL_TEXTURE_MAG_FILTER -> info.getMagFilter();
            case GL11.GL_TEXTURE_WRAP_S -> info.getWrapS();
            case GL11.GL_TEXTURE_WRAP_T -> info.getWrapT();
            case GL12.GL_TEXTURE_MAX_LEVEL -> info.getMaxLevel();
            case GL12.GL_TEXTURE_MIN_LOD -> info.getMinLod();
            case GL12.GL_TEXTURE_MAX_LOD -> info.getMaxLod();
            default -> {
                if (isRecordingDisplayList()) {
                    throw new IllegalStateException(String.format(
                        "glGetTexParameteri called during display list recording with uncached pname 0x%s for texture %d. " +
                        "Cannot query OpenGL state during compilation!", Integer.toHexString(pname), texture));
                }
                yield defaultSupplier.getAsInt();
            }
        };
    }
    public static int glGetTexParameteri(int target, int pname) {
        if (target != GL11.GL_TEXTURE_2D || shouldBypassCache()) {
            return GL11.glGetTexParameteri(target, pname);
        }
        return getTexParameterOrDefault(getBoundTextureForServerState(), pname, () -> GL11.glGetTexParameteri(target, pname));
    }

    public static float glGetTexParameterf(int target, int pname) {
        if (target != GL11.GL_TEXTURE_2D || shouldBypassCache()) {
            return GL11.glGetTexParameterf(target, pname);
        }
        final int boundTexture = getBoundTextureForServerState();
        final TextureInfo info = TextureInfoCache.INSTANCE.getInfo(boundTexture);
        if(info == null) {
            if (isRecordingDisplayList()) {
                throw new IllegalStateException(String.format(
                    "glGetTexParameterf called during display list recording with no cached TextureInfo for texture %d. " +
                    "Cannot query OpenGL state during compilation!", boundTexture));
            }
            return GL11.glGetTexParameterf(target, pname);
        }

        return switch (pname) {
            case EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT -> info.getMaxAnisotropy();
            case GL14.GL_TEXTURE_LOD_BIAS -> info.getLodBias();
            default -> {
                if (isRecordingDisplayList()) {
                    throw new IllegalStateException(String.format(
                        "glGetTexParameterf called during display list recording with uncached pname 0x%s for texture %d. " +
                        "Cannot query OpenGL state during compilation!", Integer.toHexString(pname), boundTexture));
                }
                yield GL11.glGetTexParameterf(target, pname);
            }
        };
    }

    public static int glGetTexLevelParameteri(int target, int level, int pname) {
        if (target != GL11.GL_TEXTURE_2D || shouldBypassCache()) {
            return GL11.glGetTexLevelParameteri(target, level, pname);
        }
        final TextureInfo info = TextureInfoCache.INSTANCE.getInfo(getBoundTextureForServerState());
        if (info == null) {
            if (isRecordingDisplayList()) {
                throw new IllegalStateException(String.format(
                    "glGetTexLevelParameteri called during display list recording with no cached TextureInfo for texture %d. " +
                    "Cannot query OpenGL state during compilation!", getBoundTextureForServerState()));
            }
            return GL11.glGetTexLevelParameteri(target, level, pname);
        }
        return switch (pname) {
            case GL11.GL_TEXTURE_WIDTH -> info.getWidth();
            case GL11.GL_TEXTURE_HEIGHT -> info.getHeight();
            case GL11.GL_TEXTURE_INTERNAL_FORMAT -> info.getInternalFormat();
            default -> {
                if (isRecordingDisplayList()) {
                    throw new IllegalStateException(String.format(
                        "glGetTexLevelParameteri called during display list recording with uncached pname 0x%s for texture %d. " +
                        "Cannot query OpenGL state during compilation!", Integer.toHexString(pname), getBoundTextureForServerState()));
                }
                yield GL11.glGetTexLevelParameteri(target, level, pname);
            }
        };
    }

    private static void glMaterialFront(int pname, FloatBuffer params) {
        switch (pname) {
            case GL11.GL_AMBIENT -> frontMaterial.setAmbient(params);
            case GL11.GL_DIFFUSE -> frontMaterial.setDiffuse(params);
            case GL11.GL_SPECULAR -> frontMaterial.setSpecular(params);
            case GL11.GL_EMISSION -> frontMaterial.setEmission(params);
            case GL11.GL_SHININESS -> frontMaterial.setShininess(params);
            case GL11.GL_AMBIENT_AND_DIFFUSE -> {
                frontMaterial.setAmbient(params);
                frontMaterial.setDiffuse(params);
            }
            case GL11.GL_COLOR_INDEXES -> frontMaterial.setColorIndexes(params);
        }
    }

    private static void glMaterialBack(int pname, FloatBuffer params) {
        switch (pname) {
            case GL11.GL_AMBIENT -> backMaterial.setAmbient(params);
            case GL11.GL_DIFFUSE -> backMaterial.setDiffuse(params);
            case GL11.GL_SPECULAR -> backMaterial.setSpecular(params);
            case GL11.GL_EMISSION -> backMaterial.setEmission(params);
            case GL11.GL_SHININESS -> backMaterial.setShininess(params);
            case GL11.GL_AMBIENT_AND_DIFFUSE -> {
                backMaterial.setAmbient(params);
                backMaterial.setDiffuse(params);
            }
            case GL11.GL_COLOR_INDEXES -> backMaterial.setColorIndexes(params);
        }
    }

    private static void glMaterialFront(int pname, IntBuffer params) {
        switch (pname) {
            case GL11.GL_AMBIENT -> frontMaterial.setAmbient(params);
            case GL11.GL_DIFFUSE -> frontMaterial.setDiffuse(params);
            case GL11.GL_SPECULAR -> frontMaterial.setSpecular(params);
            case GL11.GL_EMISSION -> frontMaterial.setEmission(params);
            case GL11.GL_SHININESS -> frontMaterial.setShininess(params);
            case GL11.GL_AMBIENT_AND_DIFFUSE -> {
                frontMaterial.setAmbient(params);
                frontMaterial.setDiffuse(params);
            }
            case GL11.GL_COLOR_INDEXES -> frontMaterial.setColorIndexes(params);
        }
    }

    private static void glMaterialBack(int pname, IntBuffer params) {
        switch (pname) {
            case GL11.GL_AMBIENT -> backMaterial.setAmbient(params);
            case GL11.GL_DIFFUSE -> backMaterial.setDiffuse(params);
            case GL11.GL_SPECULAR -> backMaterial.setSpecular(params);
            case GL11.GL_EMISSION -> backMaterial.setEmission(params);
            case GL11.GL_SHININESS -> backMaterial.setShininess(params);
            case GL11.GL_AMBIENT_AND_DIFFUSE -> {
                backMaterial.setAmbient(params);
                backMaterial.setDiffuse(params);
            }
            case GL11.GL_COLOR_INDEXES -> backMaterial.setColorIndexes(params);
        }
    }

    public static void glMaterial(int face, int pname, FloatBuffer params) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordMaterial(face, pname, params);
        }
        if (face == GL11.GL_FRONT) {
            glMaterialFront(pname, params);
        } else if (face == GL11.GL_BACK) {
            glMaterialBack(pname, params);
        } else if (face == GL11.GL_FRONT_AND_BACK) {
            glMaterialFront(pname, params);
            glMaterialBack(pname, params);
        } else {
            throw new RuntimeException("Unsupported face value for glMaterial: " + face);
        }
    }

    public static void glMaterial(int face, int pname, IntBuffer params) {
        if (face == GL11.GL_FRONT) {
            glMaterialFront(pname, params);
        } else if (face == GL11.GL_BACK) {
            glMaterialBack(pname, params);
        } else if (face == GL11.GL_FRONT_AND_BACK) {
            glMaterialFront(pname, params);
            glMaterialBack(pname, params);
        } else {
            throw new RuntimeException("Unsupported face value for glMaterial: " + face);
        }

        // Capture already-converted float values from state
        // For FRONT_AND_BACK, capture from frontMaterial (both should have same values)
        if (DisplayListManager.isRecording()) {
            final MaterialStateStack material = (face == GL11.GL_BACK) ? backMaterial : frontMaterial;
            final float[] floatParams;
            switch (pname) {
                case GL11.GL_AMBIENT -> floatParams = new float[] {material.ambient.x, material.ambient.y, material.ambient.z, material.ambient.w};
                case GL11.GL_DIFFUSE -> floatParams = new float[] {material.diffuse.x, material.diffuse.y, material.diffuse.z, material.diffuse.w};
                case GL11.GL_SPECULAR -> floatParams = new float[] {material.specular.x, material.specular.y, material.specular.z, material.specular.w};
                case GL11.GL_EMISSION -> floatParams = new float[] {material.emission.x, material.emission.y, material.emission.z, material.emission.w};
                case GL11.GL_SHININESS -> floatParams = new float[] {material.shininess};
                case GL11.GL_AMBIENT_AND_DIFFUSE -> // For AMBIENT_AND_DIFFUSE, capture ambient (could also use diffuse, they're the same)
                        floatParams = new float[] {material.ambient.x, material.ambient.y, material.ambient.z, material.ambient.w};
                case GL11.GL_COLOR_INDEXES -> floatParams = new float[] {material.colorIndexes.x, material.colorIndexes.y, material.colorIndexes.z};
                default -> {
                    // Fallback for unknown pname - do simple cast
                    floatParams = new float[params.remaining()];
                    params.mark();
                    for (int i = 0; i < floatParams.length; i++) {
                        floatParams[i] = params.get();
                    }
                    params.reset();
                }
            }
            DisplayListManager.recordMaterial(face, pname, java.nio.FloatBuffer.wrap(floatParams));
        }
    }

    public static void glMaterialf(int face, int pname, float val) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordMaterialf(face, pname, val);
        }
        if (pname != GL11.GL_SHININESS) {
            // it is only valid to call glMaterialf for the GL_SHININESS parameter
            return;
        }

        if (face == GL11.GL_FRONT) {
            frontMaterial.setShininess(val);
        } else if (face == GL11.GL_BACK) {
            backMaterial.setShininess(val);
        } else if (face == GL11.GL_FRONT_AND_BACK) {
            frontMaterial.setShininess(val);
            backMaterial.setShininess(val);
        } else {
            throw new RuntimeException("Unsupported face value for glMaterial: " + face);
        }
    }

    public static void glMateriali(int face, int pname, int val) {
        // This will end up no-opping if pname != GL_SHININESS, it is invalid to call this with another pname
        // Command recording happens in glMaterialf
        glMaterialf(face, pname, (float) val);
    }

    public static void glLight(int light, int pname, FloatBuffer params) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordLight(light, pname, params);
        }
        final LightStateStack lightState = lightDataStates[light - GL11.GL_LIGHT0];
        switch (pname) {
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
            default -> GL11.glLight(light, pname, params);
        }
    }

    public static void glLight(int light, int pname, IntBuffer params) {
        // Call state setter first to convert IntBuffer to float
        final LightStateStack lightState = lightDataStates[light - GL11.GL_LIGHT0];
        switch (pname) {
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
            default -> GL11.glLight(light, pname, params);
        }

        // Capture already-converted float values from state
        if (DisplayListManager.isRecording()) {
            final float[] floatParams;
            switch (pname) {
                case GL11.GL_AMBIENT -> floatParams = new float[] {lightState.ambient.x, lightState.ambient.y, lightState.ambient.z, lightState.ambient.w};
                case GL11.GL_DIFFUSE -> floatParams = new float[] {lightState.diffuse.x, lightState.diffuse.y, lightState.diffuse.z, lightState.diffuse.w};
                case GL11.GL_SPECULAR -> floatParams = new float[] {lightState.specular.x, lightState.specular.y, lightState.specular.z, lightState.specular.w};
                case GL11.GL_POSITION -> floatParams = new float[] {lightState.position.x, lightState.position.y, lightState.position.z, lightState.position.w};
                case GL11.GL_SPOT_DIRECTION -> floatParams = new float[] {lightState.spotDirection.x, lightState.spotDirection.y, lightState.spotDirection.z};
                case GL11.GL_SPOT_EXPONENT -> floatParams = new float[] {lightState.spotExponent};
                case GL11.GL_SPOT_CUTOFF -> floatParams = new float[] {lightState.spotCutoff};
                case GL11.GL_CONSTANT_ATTENUATION -> floatParams = new float[] {lightState.constantAttenuation};
                case GL11.GL_LINEAR_ATTENUATION -> floatParams = new float[] {lightState.linearAttenuation};
                case GL11.GL_QUADRATIC_ATTENUATION -> floatParams = new float[] {lightState.quadraticAttenuation};
                default -> {
                    // Fallback for unknown pname - do simple cast
                    floatParams = new float[params.remaining()];
                    params.mark();
                    for (int i = 0; i < floatParams.length; i++) {
                        floatParams[i] = params.get();
                    }
                    params.reset();
                }
            }
            DisplayListManager.recordLight(light, pname, java.nio.FloatBuffer.wrap(floatParams));
        }
    }

    public static void glLightf(int light, int pname, float param) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordLightf(light, pname, param);
        }
        final LightStateStack lightState = lightDataStates[light - GL11.GL_LIGHT0];
        switch (pname) {
            case GL11.GL_SPOT_EXPONENT -> lightState.setSpotExponent(param);
            case GL11.GL_SPOT_CUTOFF -> lightState.setSpotCutoff(param);
            case GL11.GL_CONSTANT_ATTENUATION -> lightState.setConstantAttenuation(param);
            case GL11.GL_LINEAR_ATTENUATION -> lightState.setLinearAttenuation(param);
            case GL11.GL_QUADRATIC_ATTENUATION -> lightState.setQuadraticAttenuation(param);
            default -> GL11.glLightf(light, pname, param);
        }
    }

    public static void glLighti(int light, int pname, int param) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordLighti(light, pname, param);
        }
        final LightStateStack lightState = lightDataStates[light - GL11.GL_LIGHT0];
        switch (pname) {
            case GL11.GL_SPOT_EXPONENT -> lightState.setSpotExponent(param);
            case GL11.GL_SPOT_CUTOFF -> lightState.setSpotCutoff(param);
            case GL11.GL_CONSTANT_ATTENUATION -> lightState.setConstantAttenuation(param);
            case GL11.GL_LINEAR_ATTENUATION -> lightState.setLinearAttenuation(param);
            case GL11.GL_QUADRATIC_ATTENUATION -> lightState.setQuadraticAttenuation(param);
            default -> GL11.glLighti(light, pname, param);
        }
    }

    public static void glLightModel(int pname, FloatBuffer params) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordLightModel(pname, params);
        }
        switch (pname) {
            case GL11.GL_LIGHT_MODEL_AMBIENT -> lightModel.setAmbient(params);
            case GL11.GL_LIGHT_MODEL_LOCAL_VIEWER -> lightModel.setLocalViewer(params);
            case GL11.GL_LIGHT_MODEL_TWO_SIDE -> lightModel.setTwoSide(params);
            default -> GL11.glLightModel(pname, params);
        }
    }
    public static void glLightModel(int pname, IntBuffer params) {
        // Call state setter first to convert IntBuffer to float
        switch (pname) {
            case GL11.GL_LIGHT_MODEL_AMBIENT -> lightModel.setAmbient(params);
            case GL12.GL_LIGHT_MODEL_COLOR_CONTROL -> lightModel.setColorControl(params);
            case GL11.GL_LIGHT_MODEL_LOCAL_VIEWER -> lightModel.setLocalViewer(params);
            case GL11.GL_LIGHT_MODEL_TWO_SIDE -> lightModel.setTwoSide(params);
            default -> GL11.glLightModel(pname, params);
        }

        // Capture already-converted float values from state
        if (DisplayListManager.isRecording()) {
            final float[] floatParams;
            switch (pname) {
                case GL11.GL_LIGHT_MODEL_AMBIENT -> floatParams = new float[] {lightModel.ambient.x, lightModel.ambient.y, lightModel.ambient.z, lightModel.ambient.w};
                case GL12.GL_LIGHT_MODEL_COLOR_CONTROL -> floatParams = new float[] {(float) lightModel.colorControl};
                case GL11.GL_LIGHT_MODEL_LOCAL_VIEWER -> floatParams = new float[] {lightModel.localViewer};
                case GL11.GL_LIGHT_MODEL_TWO_SIDE -> floatParams = new float[] {lightModel.twoSide};
                default -> {
                    // Fallback for unknown pname - do simple cast
                    floatParams = new float[params.remaining()];
                    params.mark();
                    for (int i = 0; i < floatParams.length; i++) {
                        floatParams[i] = params.get();
                    }
                    params.reset();
                }
            }
            DisplayListManager.recordLightModel(pname, java.nio.FloatBuffer.wrap(floatParams));
        }
    }
    public static void glLightModelf(int pname, float param) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordLightModelf(pname, param);
        }
        // Only update cached state on main thread
        if (isCachingEnabled()) {
            switch (pname) {
                case GL11.GL_LIGHT_MODEL_LOCAL_VIEWER -> lightModel.setLocalViewer(param);
                case GL11.GL_LIGHT_MODEL_TWO_SIDE -> lightModel.setTwoSide(param);
                default -> GL11.glLightModelf(pname, param);
            }
        } else {
            GL11.glLightModelf(pname, param);
        }
    }
    public static void glLightModeli(int pname, int param) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordLightModeli(pname, param);
        }
        // Only update cached state on main thread
        if (isCachingEnabled()) {
            switch (pname) {
                case GL12.GL_LIGHT_MODEL_COLOR_CONTROL -> lightModel.setColorControl(param);
                case GL11.GL_LIGHT_MODEL_LOCAL_VIEWER -> lightModel.setLocalViewer(param);
                case GL11.GL_LIGHT_MODEL_TWO_SIDE -> lightModel.setTwoSide(param);
                default -> GL11.glLightModeli(pname, param);
            }
        } else {
            GL11.glLightModeli(pname, param);
        }
    }

    public static void glColorMaterial(int face, int mode) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordColorMaterial(face, mode);
        }
        final boolean caching = isCachingEnabled();
        if (BYPASS_CACHE || !caching || colorMaterialFace.getValue() != face || colorMaterialParameter.getValue() != mode) {
            if (caching) {
                colorMaterialFace.setValue(face);
                colorMaterialParameter.setValue(mode);
            }
            GL11.glColorMaterial(face, mode);
        }
    }

    public static void glDepthRange(double near, double far) {
        GL11.glDepthRange(near, far);
    }

    public static void glUseProgram(int program) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordUseProgram(program);
            return;
        }
        final boolean caching = isCachingEnabled();
        if (BYPASS_CACHE || !caching || program != activeProgram) {
            if (caching) {
                activeProgram = program;
            }
            if (AngelicaMod.lwjglDebug) {
                final String programName = GLDebug.getObjectLabel(KHRDebug.GL_PROGRAM, program);
                GLDebug.debugMessage("Activating Program - " + program + ":" + programName);
            }
            GL20.glUseProgram(program);
        }
    }

    // Missing GL commands from Mesa cross-check
    public static void glTexImage1D(int target, int level, int internalformat, int width, int border, int format, int type, ByteBuffer pixels) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glTexImage1D in display lists not yet implemented");
        }
        GL11.glTexImage1D(target, level, internalformat, width, border, format, type, pixels);
    }

    public static void glTexImage3D(int target, int level, int internalformat, int width, int height, int depth, int border, int format, int type, ByteBuffer pixels) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glTexImage3D in display lists not yet implemented");
        }
        GL12.glTexImage3D(target, level, internalformat, width, height, depth, border, format, type, pixels);
    }

    public static void glTexSubImage1D(int target, int level, int xoffset, int width, int format, int type, ByteBuffer pixels) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glTexSubImage1D in display lists not yet implemented");
        }
        GL11.glTexSubImage1D(target, level, xoffset, width, format, type, pixels);
    }

    public static void glLineWidth(float width) {
        final boolean recording = DisplayListManager.isRecording();
        if (recording) {
            DisplayListManager.recordLineWidth(width);
        }
        final boolean caching = isCachingEnabled();
        if (BYPASS_CACHE || !caching || lineState.getWidth() != width) {
            if (caching) {
                lineState.setWidth(width);
            }
            if (!recording) {
                GL11.glLineWidth(width);
            }
        }
    }

    // Texture commands
    public static void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, ByteBuffer pixels) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordComplexCommand(TexSubImage2DCmd.fromByteBuffer(target, level, xoffset, yoffset, width, height, format, type, pixels));
        }
        if (shouldUseDSA(target)) {
            // Use DSA to upload directly to the texture - keeps GL binding state unchanged
            RenderSystem.textureSubImage2D(getBoundTextureForServerState(), target, level, xoffset, yoffset, width, height, format, type, pixels);
        } else {
            // Non-main thread or proxy texture - use direct GL call
            GL11.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels);
        }
    }

    public static void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, IntBuffer pixels) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordComplexCommand(TexSubImage2DCmd.fromIntBuffer(target, level, xoffset, yoffset, width, height, format, type, pixels));
        }
        if (shouldUseDSA(target)) {
            // Use DSA to upload directly to the texture
            RenderSystem.textureSubImage2D(getBoundTextureForServerState(), target, level, xoffset, yoffset, width, height, format, type, pixels);
        } else {
            // Non-main thread or proxy texture - use direct GL call
            GL11.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels);
        }
    }

    public static void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, long pixels_buffer_offset) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glTexSubImage2D with buffer offset in display lists not yet supported");
        }
        GL11.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels_buffer_offset);
    }

    public static void glTexSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset, int width, int height, int depth, int format, int type, ByteBuffer pixels) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glTexSubImage3D in display lists not yet implemented - if you see this, please report!");
        }
        GL12.glTexSubImage3D(target, level, xoffset, yoffset, zoffset, width, height, depth, format, type, pixels);
    }

    public static void glCopyTexImage1D(int target, int level, int internalFormat, int x, int y, int width, int border) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glCopyTexImage1D in display lists not yet implemented - if you see this, please report!");
        }
        GL11.glCopyTexImage1D(target, level, internalFormat, x, y, width, border);
    }

    public static void glCopyTexImage2D(int target, int level, int internalFormat, int x, int y, int width, int height, int border) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glCopyTexImage2D in display lists not yet implemented - if you see this, please report!");
        }
        GL11.glCopyTexImage2D(target, level, internalFormat, x, y, width, height, border);
    }

    public static void glCopyTexSubImage1D(int target, int level, int xoffset, int x, int y, int width) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glCopyTexSubImage1D in display lists not yet implemented - if you see this, please report!");
        }
        GL11.glCopyTexSubImage1D(target, level, xoffset, x, y, width);
    }

    public static void glCopyTexSubImage2D(int target, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glCopyTexSubImage2D in display lists not yet implemented - if you see this, please report!");
        }
        GL11.glCopyTexSubImage2D(target, level, xoffset, yoffset, x, y, width, height);
    }

    public static void glCopyTexSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset, int x, int y, int width, int height) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glCopyTexSubImage3D in display lists not yet implemented - if you see this, please report!");
        }
        GL12.glCopyTexSubImage3D(target, level, xoffset, yoffset, zoffset, x, y, width, height);
    }

    // State commands
    public static void glCullFace(int mode) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordCullFace(mode);
            return;
        }
        final boolean caching = isCachingEnabled();
        if (BYPASS_CACHE || !caching || polygonState.getCullFaceMode() != mode) {
            if (caching) {
                polygonState.setCullFaceMode(mode);
            }
            GL11.glCullFace(mode);
        }
    }

    public static void glFrontFace(int mode) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordFrontFace(mode);
            return;
        }
        final boolean caching = isCachingEnabled();
        if (BYPASS_CACHE || !caching || polygonState.getFrontFace() != mode) {
            if (caching) {
                polygonState.setFrontFace(mode);
            }
            GL11.glFrontFace(mode);
        }
    }

    public static void glHint(int target, int mode) {
        final boolean recording = DisplayListManager.isRecording();
        if (recording) {
            DisplayListManager.recordHint(target, mode);
            if (DisplayListManager.getListMode() == GL11.GL_COMPILE) {
                return;
            }
        }
        GL11.glHint(target, mode);
    }

    public static void glLineStipple(int factor, short pattern) {
        final boolean recording = DisplayListManager.isRecording();
        if (recording) {
            DisplayListManager.recordLineStipple(factor, pattern);
        }
        final boolean caching = isCachingEnabled();
        if (BYPASS_CACHE || !caching || lineState.getStippleFactor() != factor || lineState.getStipplePattern() != pattern) {
            if (caching) {
                lineState.setStippleFactor(factor);
                lineState.setStipplePattern(pattern);
            }
            if (!recording) {
                GL11.glLineStipple(factor, pattern);
            }
        }
    }

    public static void glPointSize(float size) {
        final boolean recording = DisplayListManager.isRecording();
        if (recording) {
            DisplayListManager.recordPointSize(size);
        }
        final boolean caching = isCachingEnabled();
        if (BYPASS_CACHE || !caching || pointState.getSize() != size) {
            if (caching) {
                pointState.setSize(size);
            }
            if (!recording) {
                GL11.glPointSize(size);
            }
        }
    }

    public static void glPolygonMode(int face, int mode) {
        final boolean recording = DisplayListManager.isRecording();
        if (recording) {
            DisplayListManager.recordPolygonMode(face, mode);
        }
        final boolean caching = isCachingEnabled();
        final boolean needsUpdate;
        if (face == GL11.GL_FRONT) {
            needsUpdate = BYPASS_CACHE || !caching || polygonState.getFrontMode() != mode;
            if (caching && needsUpdate) polygonState.setFrontMode(mode);
        } else if (face == GL11.GL_BACK) {
            needsUpdate = BYPASS_CACHE || !caching || polygonState.getBackMode() != mode;
            if (caching && needsUpdate) polygonState.setBackMode(mode);
        } else { // GL_FRONT_AND_BACK
            needsUpdate = BYPASS_CACHE || !caching || polygonState.getFrontMode() != mode || polygonState.getBackMode() != mode;
            if (caching && needsUpdate) {
                polygonState.setFrontMode(mode);
                polygonState.setBackMode(mode);
            }
        }
        if (needsUpdate && !recording) {
            GL11.glPolygonMode(face, mode);
        }
    }

    public static void glPolygonOffset(float factor, float units) {
        final boolean recording = DisplayListManager.isRecording();
        if (recording) {
            DisplayListManager.recordPolygonOffset(factor, units);
        }
        final boolean caching = isCachingEnabled();
        if (BYPASS_CACHE || !caching || polygonState.getOffsetFactor() != factor || polygonState.getOffsetUnits() != units) {
            if (caching) {
                polygonState.setOffsetFactor(factor);
                polygonState.setOffsetUnits(units);
            }
            if (!recording) {
                GL11.glPolygonOffset(factor, units);
            }
        }
    }

    public static void glReadBuffer(int mode) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glReadBuffer in display lists not yet implemented - if you see this, please report!");
        }
        GL11.glReadBuffer(mode);
    }

    public static void glScissor(int x, int y, int width, int height) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glScissor in display lists not yet implemented - if you see this, please report!");
        }
        GL11.glScissor(x, y, width, height);
    }

    public static void glStencilFunc(int func, int ref, int mask) {
        final boolean recording = DisplayListManager.isRecording();
        if (recording) {
            DisplayListManager.recordStencilFunc(func, ref, mask);
        }
        final boolean caching = isCachingEnabled();
        if (BYPASS_CACHE || !caching || stencilState.getFuncFront() != func || stencilState.getRefFront() != ref || stencilState.getValueMaskFront() != mask) {
            if (caching) {
                stencilState.setFunc(func, ref, mask);
            }
            if (!recording) {
                GL11.glStencilFunc(func, ref, mask);
            }
        }
    }

    public static void glStencilMask(int mask) {
        final boolean recording = DisplayListManager.isRecording();
        if (recording) {
            DisplayListManager.recordStencilMask(mask);
        }
        final boolean caching = isCachingEnabled();
        if (BYPASS_CACHE || !caching || stencilState.getWriteMaskFront() != mask) {
            if (caching) {
                stencilState.setWriteMask(mask);
            }
            if (!recording) {
                GL11.glStencilMask(mask);
            }
        }
    }

    public static void glStencilOp(int fail, int zfail, int zpass) {
        final boolean recording = DisplayListManager.isRecording();
        if (recording) {
            DisplayListManager.recordStencilOp(fail, zfail, zpass);
        }
        final boolean caching = isCachingEnabled();
        if (BYPASS_CACHE || !caching || stencilState.getFailOpFront() != fail || stencilState.getZFailOpFront() != zfail || stencilState.getZPassOpFront() != zpass) {
            if (caching) {
                stencilState.setOp(fail, zfail, zpass);
            }
            if (!recording) {
                GL11.glStencilOp(fail, zfail, zpass);
            }
        }
    }

    public static void glPixelStorei(int pname, int param) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glPixelStorei in display lists not yet implemented - if you see this, please report!");
        }
        GL11.glPixelStorei(pname, param);
    }

    public static void glPixelStoref(int pname, float param) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glPixelStoref in display lists not yet implemented - if you see this, please report!");
        }
        GL11.glPixelStoref(pname, param);
    }

    // Display List Commands
    public static void glCallLists(IntBuffer lists) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glCallLists in display lists not yet implemented - if you see this, please report!");
        }
        GL11.glCallLists(lists);
    }

    public static void glCallLists(ByteBuffer lists) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glCallLists in display lists not yet implemented - if you see this, please report!");
        }
        GL11.glCallLists(lists);
    }

    public static void glListBase(int base) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glListBase in display lists not yet implemented - if you see this, please report!");
        }
        GL11.glListBase(base);
    }

    // Clip Plane Commands
    public static void glClipPlane(int plane, DoubleBuffer equation) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glClipPlane in display lists not yet implemented - if you see this, please report!");
        }
        GL11.glClipPlane(plane, equation);
    }

    // Clear Commands
    public static void glClearStencil(int s) {
        final boolean recording = DisplayListManager.isRecording();
        if (recording) {
            DisplayListManager.recordClearStencil(s);
        }
        final boolean caching = isCachingEnabled();
        if (BYPASS_CACHE || !caching || stencilState.getClearValue() != s) {
            if (caching) {
                stencilState.setClearValue(s);
            }
            if (!recording) {
                GL11.glClearStencil(s);
            }
        }
    }

    // Draw Buffer Commands (GL 2.0+)
    public static void glDrawBuffers(int buffer) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordDrawBuffers(1, buffer);
        }
        GL20.glDrawBuffers(buffer);
    }

    public static void glDrawBuffers(IntBuffer bufs) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordDrawBuffers(bufs.remaining(), bufs);
        }
        GL20.glDrawBuffers(bufs);
    }

    // Multisample Commands
    public static void glSampleCoverage(float value, boolean invert) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glSampleCoverage in display lists not yet implemented - if you see this, please report!");
        }
        GL13.glSampleCoverage(value, invert);
    }

    // Stencil Separate Functions (GL 2.0+)
    public static void glStencilFuncSeparate(int face, int func, int ref, int mask) {
        final boolean recording = DisplayListManager.isRecording();
        if (recording) {
            DisplayListManager.recordStencilFuncSeparate(face, func, ref, mask);
        }
        final boolean caching = isCachingEnabled();
        boolean needsUpdate = BYPASS_CACHE || !caching;
        if (!needsUpdate) {
            if (face == GL11.GL_FRONT || face == GL11.GL_FRONT_AND_BACK) {
                needsUpdate = stencilState.getFuncFront() != func || stencilState.getRefFront() != ref || stencilState.getValueMaskFront() != mask;
            }
            if (!needsUpdate && (face == GL11.GL_BACK || face == GL11.GL_FRONT_AND_BACK)) {
                needsUpdate = stencilState.getFuncBack() != func || stencilState.getRefBack() != ref || stencilState.getValueMaskBack() != mask;
            }
        }
        if (needsUpdate) {
            if (caching) {
                if (face == GL11.GL_FRONT || face == GL11.GL_FRONT_AND_BACK) {
                    stencilState.setFuncFront(func);
                    stencilState.setRefFront(ref);
                    stencilState.setValueMaskFront(mask);
                }
                if (face == GL11.GL_BACK || face == GL11.GL_FRONT_AND_BACK) {
                    stencilState.setFuncBack(func);
                    stencilState.setRefBack(ref);
                    stencilState.setValueMaskBack(mask);
                }
            }
            if (!recording) {
                GL20.glStencilFuncSeparate(face, func, ref, mask);
            }
        }
    }

    public static void glStencilMaskSeparate(int face, int mask) {
        final boolean recording = DisplayListManager.isRecording();
        if (recording) {
            DisplayListManager.recordStencilMaskSeparate(face, mask);
        }
        final boolean caching = isCachingEnabled();
        boolean needsUpdate = BYPASS_CACHE || !caching;
        if (!needsUpdate) {
            if (face == GL11.GL_FRONT || face == GL11.GL_FRONT_AND_BACK) {
                needsUpdate = stencilState.getWriteMaskFront() != mask;
            }
            if (!needsUpdate && (face == GL11.GL_BACK || face == GL11.GL_FRONT_AND_BACK)) {
                needsUpdate = stencilState.getWriteMaskBack() != mask;
            }
        }
        if (needsUpdate) {
            if (caching) {
                if (face == GL11.GL_FRONT || face == GL11.GL_FRONT_AND_BACK) {
                    stencilState.setWriteMaskFront(mask);
                }
                if (face == GL11.GL_BACK || face == GL11.GL_FRONT_AND_BACK) {
                    stencilState.setWriteMaskBack(mask);
                }
            }
            if (!recording) {
                GL20.glStencilMaskSeparate(face, mask);
            }
        }
    }

    public static void glStencilOpSeparate(int face, int sfail, int dpfail, int dppass) {
        final boolean recording = DisplayListManager.isRecording();
        if (recording) {
            DisplayListManager.recordStencilOpSeparate(face, sfail, dpfail, dppass);
        }
        final boolean caching = isCachingEnabled();
        boolean needsUpdate = BYPASS_CACHE || !caching;
        if (!needsUpdate) {
            if (face == GL11.GL_FRONT || face == GL11.GL_FRONT_AND_BACK) {
                needsUpdate = stencilState.getFailOpFront() != sfail || stencilState.getZFailOpFront() != dpfail || stencilState.getZPassOpFront() != dppass;
            }
            if (!needsUpdate && (face == GL11.GL_BACK || face == GL11.GL_FRONT_AND_BACK)) {
                needsUpdate = stencilState.getFailOpBack() != sfail || stencilState.getZFailOpBack() != dpfail || stencilState.getZPassOpBack() != dppass;
            }
        }
        if (needsUpdate) {
            if (caching) {
                if (face == GL11.GL_FRONT || face == GL11.GL_FRONT_AND_BACK) {
                    stencilState.setFailOpFront(sfail);
                    stencilState.setZFailOpFront(dpfail);
                    stencilState.setZPassOpFront(dppass);
                }
                if (face == GL11.GL_BACK || face == GL11.GL_FRONT_AND_BACK) {
                    stencilState.setFailOpBack(sfail);
                    stencilState.setZFailOpBack(dpfail);
                    stencilState.setZPassOpBack(dppass);
                }
            }
            if (!recording) {
                GL20.glStencilOpSeparate(face, sfail, dpfail, dppass);
            }
        }
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

}
