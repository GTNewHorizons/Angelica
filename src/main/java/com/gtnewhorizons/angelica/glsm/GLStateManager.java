package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizon.gtnhlib.client.opengl.UniversalVAO;
import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement.Usage;
import com.gtnewhorizon.gtnhlib.client.renderer.DirectTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.stacks.IStateStack;
import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.glsm.DisplayListManager.RecordMode;
import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import com.gtnewhorizons.angelica.glsm.ffp.TessellatorStreamingDrawer;
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
import net.coderbot.iris.samplers.IrisSamplers;
import net.coderbot.iris.texture.pbr.PBRTextureManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.ARBMultitexture;
import org.lwjgl.opengl.ARBShaderObjects;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.Drawable;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    // Software stack depths for FFP emulation
    public static final int MAX_ATTRIB_STACK_DEPTH = 16 + 2;
    public static final int MAX_MODELVIEW_STACK_DEPTH = 32 + 2;
    public static final int MAX_PROJECTION_STACK_DEPTH = 4;
    public static final int MAX_TEXTURE_STACK_DEPTH = 4;
    public static final int MAX_CLIP_PLANES = 8;
    public static final int MAX_TEXTURE_UNITS = GL11.glGetInteger(GL20.GL_MAX_TEXTURE_IMAGE_UNITS);

    public static final GLFeatureSet HAS_MULTIPLE_SET = new GLFeatureSet();

    // Generation counters for FFP uniform dirty tracking. Bumped when the corresponding GLSM state changes.
    // Per-matrix-mode generation counters — avoids re-uploading all matrices when only one mode changed
    public static int mvGeneration;    // modelview matrix changes
    public static int projGeneration;  // projection matrix changes
    public static int texMatrixGeneration; // texture matrix changes
    public static int lightingGeneration;
    public static int fragmentGeneration; // fog + alpha ref
    public static int colorGeneration;    // current vertex color

    // Deferred vertex attribute upload flags — set when state changes, flushed before draw
    private static boolean dirtyColorAttrib;
    private static boolean dirtyNormalAttrib;
    private static boolean dirtyTexCoordAttrib;
    private static boolean dirtyLightmapAttrib;

    /** Flush deferred vertex attribute uploads. Called before draw to ensure default attrib values are current. */
    public static void flushDeferredVertexAttribs() {
        if (dirtyColorAttrib) {
            GL20.glVertexAttrib4f(Usage.COLOR.getAttributeLocation(), color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
            dirtyColorAttrib = false;
        }
        if (dirtyNormalAttrib) {
            final var n = ShaderManager.getCurrentNormal();
            GL20.glVertexAttrib3f(Usage.NORMAL.getAttributeLocation(), n.x, n.y, n.z);
            dirtyNormalAttrib = false;
        }
        if (dirtyTexCoordAttrib) {
            final var tc = ShaderManager.getCurrentTexCoord();
            GL20.glVertexAttrib4f(Usage.PRIMARY_UV.getAttributeLocation(), tc.x, tc.y, tc.z, tc.w);
            dirtyTexCoordAttrib = false;
        }
        if (dirtyLightmapAttrib) {
            GL20.glVertexAttrib4f(Usage.SECONDARY_UV.getAttributeLocation(), OpenGlHelper.lastBrightnessX, OpenGlHelper.lastBrightnessY, 0.0f, 1.0f);
            dirtyLightmapAttrib = false;
        }
    }

    // Highest texture unit index that has ever had a non-zero binding; limits onDeleteTexture scan range
    private static int maxBoundTextureUnit = 0;

    @Getter private static Vendor VENDOR;

    // This setting varies depending on driver, so it gets queried at runtime
    public static final int DEFAULT_DRAW_BUFFER = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);


    private static Thread CurrentThread = MainThread;
    @Setter @Getter private static boolean runningSplash = true;

    private static volatile boolean splashComplete = false;
    @Getter @Setter private static Thread drawableGLHolder = MainThread;
    // Reference to DrawableGL (main display context) - works with both FML and BLS splash
    @Setter private static Drawable drawableGL = null;

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

    private static final String TEXTURE = "texture";
    private static final String TEXTURE_RENAMED = "gtexture";

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

    // Saved generation counters at push time — used to detect whether state actually changed during push/pop scope
    private static final int[] savedMvGen = new int[MAX_ATTRIB_STACK_DEPTH];
    private static final int[] savedProjGen = new int[MAX_ATTRIB_STACK_DEPTH];
    private static final int[] savedTexMatGen = new int[MAX_ATTRIB_STACK_DEPTH];
    private static final int[] savedLightingGen = new int[MAX_ATTRIB_STACK_DEPTH];
    private static final int[] savedFragmentGen = new int[MAX_ATTRIB_STACK_DEPTH];
    private static final int[] savedColorGen = new int[MAX_ATTRIB_STACK_DEPTH];
    private static final int[] savedNormalGen = new int[MAX_ATTRIB_STACK_DEPTH];
    private static final int[] savedTexCoordGen = new int[MAX_ATTRIB_STACK_DEPTH];

    /** Register a state stack as modified at the current depth (called from beforeModify). */
    public static void registerModifiedState(IStateStack<?> stack) {
        if (attribDepth > 0) {
            modifiedAtDepth[attribDepth - 1].add(stack);
        }
    }
    protected static final IntegerStateStack activeTextureUnit = new IntegerStateStack(0);
    private static int clientActiveTextureUnit = 0;
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
    @Getter protected static final BooleanStateStack fogMode = new BooleanStateStack(GL11.GL_FOG, false, true);
    @Getter protected static final Color4Stack color = new Color4Stack();
    @Getter protected static final Color4Stack clearColor = new Color4Stack(new Color4(0.0F, 0.0F, 0.0F, 0.0F));
    @Getter protected static final ColorMaskStack colorMask = new ColorMaskStack();
    @Getter protected static final IntegerStateStack drawBuffer = new IntegerStateStack(DEFAULT_DRAW_BUFFER);
    @Getter protected static final IntegerStateStack logicOpMode = new IntegerStateStack(GL11.GL_COPY);
    @Getter protected static final BooleanStateStack cullState = new BooleanStateStack(GL11.GL_CULL_FACE);
    @Getter protected static final AlphaStateStack alphaState = new AlphaStateStack();
    @Getter protected static final BooleanStateStack alphaTest = new BooleanStateStack(GL11.GL_ALPHA_TEST, false, true);

    // Texture environment mode (GL_MODULATE default per OpenGL spec)
    @Getter protected static final IntegerStateStack texEnvMode = new IntegerStateStack(GL11.GL_MODULATE);
    // Texture environment color (used by GL_BLEND mode)
    @Getter protected static final Color4Stack texEnvColor = new Color4Stack(new Color4(0.0f, 0.0f, 0.0f, 0.0f));

    @Getter protected static final BooleanStateStack lightingState = new BooleanStateStack(GL11.GL_LIGHTING, false, true);
    @Getter protected static final BooleanStateStack rescaleNormalState = new BooleanStateStack(GL12.GL_RESCALE_NORMAL, false, true);
    @Getter protected static final BooleanStateStack normalizeState = new BooleanStateStack(GL11.GL_NORMALIZE, false, true);

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

    // Line width range queried at init from GL_ALIASED_LINE_WIDTH_RANGE
    static float lineWidthMin = 1.0f;
    static float lineWidthMax = 1.0f;

    // Point state (GL_POINT_BIT)
    @Getter protected static final PointStateStack pointState = new PointStateStack();

    // Polygon state (GL_POLYGON_BIT) - mode, offset values, cull face mode, front face
    @Getter protected static final PolygonStateStack polygonState = new PolygonStateStack();

    // Stencil state (GL_STENCIL_BUFFER_BIT)
    @Getter protected static final StencilStateStack stencilState = new StencilStateStack();
    private static int stencilBitMask = 0xFFFFFFFF;

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
    @Getter protected static final BooleanStateStack colorMaterial = new BooleanStateStack(GL11.GL_COLOR_MATERIAL, false, true);
    @Getter protected static final IntegerStateStack colorMaterialFace = new IntegerStateStack(GL11.GL_FRONT_AND_BACK);
    @Getter protected static final IntegerStateStack colorMaterialParameter = new IntegerStateStack(GL11.GL_AMBIENT_AND_DIFFUSE);
    @Getter protected static final LightModelStateStack lightModel = new LightModelStateStack();

    @Getter protected static final MaterialStateStack frontMaterial = new MaterialStateStack(GL11.GL_FRONT);
    @Getter protected static final MaterialStateStack backMaterial = new MaterialStateStack(GL11.GL_BACK);

    private static final MethodHandle MAT4_STACK_CURR_DEPTH;

    static {
        for (int i = 0; i < lightStates.length; i ++) {
            lightStates[i] = new BooleanStateStack(GL11.GL_LIGHT0 + i, false, true);
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
    @Getter protected static int listBase = 0;

    @Getter protected static int boundVBO;
    @Getter protected static int boundEBO;
    @Getter protected static int boundVAO;
    @Getter private static int defaultVAO; // Non-zero on core profile

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

        // Compute stencil bit mask — driver clamps stencil masks to buffer depth
        // GL_STENCIL_BITS was removed in core profile; query via default FBO attachment
        final int stencilBits = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, GL11.GL_STENCIL, GL30.GL_FRAMEBUFFER_ATTACHMENT_STENCIL_SIZE);
        stencilBitMask = stencilBits >= 32 ? 0xFFFFFFFF : (1 << stencilBits) - 1;

        // Initialize stencil masks from computed bit mask
        stencilState.setValueMaskFront(stencilBitMask);
        stencilState.setValueMaskBack(stencilBitMask);
        stencilState.setWriteMaskFront(stencilBitMask);
        stencilState.setWriteMaskBack(stencilBitMask);

        final FloatBuffer lwRange = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL12.GL_ALIASED_LINE_WIDTH_RANGE, lwRange);
        lineWidthMin = lwRange.get(0);
        lineWidthMax = lwRange.get(1);

        // Sync default vertex attribs with GLSM cache initial values.
        GL20.glVertexAttrib4f(1, 1.0f, 1.0f, 1.0f, 1.0f);    // COLOR = white
        GL20.glVertexAttrib3f(4, 0.0f, 0.0f, 1.0f);          // NORMAL = +Z

        final Minecraft mc = Minecraft.getMinecraft();
        if (mc != null) {
            // Initialize viewport state from display dimensions.
            // After Display.create(), viewport is (0, 0, width, height)
            viewportState.setViewPort(0, 0, mc.displayWidth, mc.displayHeight);
        }
    }

    public static void init() {
        RenderSystem.initRenderer();
        IrisSamplers.initRenderer();

        if(BYPASS_CACHE) {
            LOGGER.info("GLStateManager cache bypassed");
        }
        if(AngelicaMod.lwjglDebug) {
            LOGGER.info("Enabling additional LWJGL debug output");

            GLDebug.setupDebugMessageCallback();
            GLDebug.initDebugState();

            GLDebug.debugMessage("Angelica Debug Annotator Initialized");
        }

        defaultVAO = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(defaultVAO);
        boundVAO = defaultVAO;

        // Drain any pending GL errors from initialization. In core profile, some legacy queries may generate GL_INVALID_ENUM. The splash thread inherits the
        // DrawableGL context and its error state, so stale errors here would cause SplashProgress.checkGLError() to fail.
        int err;
        while ((err = GL11.glGetError()) != 0) {
            LOGGER.debug("Drained GL error 0x{} during init", Integer.toHexString(err));
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
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordEnable(cap);
            if (mode == RecordMode.COMPILE) {
                return;
            }
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
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDisable(cap);
            if (mode == RecordMode.COMPILE) {
                return;
            }
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
            case GL11.GL_LIGHT_MODEL_LOCAL_VIEWER -> lightModel.localViewer != 0.0f;
            case GL11.GL_LIGHT_MODEL_TWO_SIDE -> lightModel.twoSide != 0.0f;
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
            case GL11.GL_LIST_BASE -> listBase;
            case GL11.GL_LIST_MODE -> DisplayListManager.getListMode();
            case GL11.GL_MATRIX_MODE -> matrixMode.getMode();
            case GL11.GL_SHADE_MODEL -> shadeModelState.getValue();
            case GL11.GL_TEXTURE_BINDING_2D -> getBoundTextureForServerState();
            case GL11.GL_COLOR_MATERIAL_FACE -> colorMaterialFace.getValue();
            case GL11.GL_COLOR_MATERIAL_PARAMETER -> colorMaterialParameter.getValue();
            case GL11.GL_MODELVIEW_STACK_DEPTH -> getMatrixStackDepth(modelViewMatrix);
            case GL11.GL_PROJECTION_STACK_DEPTH -> getMatrixStackDepth(projectionMatrix);

            case GL12.GL_LIGHT_MODEL_COLOR_CONTROL -> lightModel.colorControl;

            case GL14.GL_BLEND_DST_ALPHA -> blendState.getDstAlpha();
            case GL14.GL_BLEND_DST_RGB -> blendState.getDstRgb();
            case GL14.GL_BLEND_SRC_ALPHA -> blendState.getSrcAlpha();
            case GL14.GL_BLEND_SRC_RGB -> blendState.getSrcRgb();
            case GL14.GL_BLEND_EQUATION -> blendState.getEquationRgb();
            case GL20.GL_BLEND_EQUATION_ALPHA -> blendState.getEquationAlpha();

            case GL11.GL_LOGIC_OP_MODE -> logicOpMode.getValue();
            case GL11.GL_DRAW_BUFFER -> drawBuffer.getValue();

            case GL11.GL_STENCIL_FUNC -> stencilState.getFuncFront();
            case GL11.GL_STENCIL_REF -> stencilState.getRefFront();
            case GL11.GL_STENCIL_VALUE_MASK -> stencilState.getValueMaskFront();
            case GL11.GL_STENCIL_FAIL -> stencilState.getFailOpFront();
            case GL11.GL_STENCIL_PASS_DEPTH_FAIL -> stencilState.getZFailOpFront();
            case GL11.GL_STENCIL_PASS_DEPTH_PASS -> stencilState.getZPassOpFront();
            case GL11.GL_STENCIL_WRITEMASK -> stencilState.getWriteMaskFront();
            case GL11.GL_STENCIL_CLEAR_VALUE -> stencilState.getClearValue();

            case GL15.GL_ARRAY_BUFFER_BINDING -> boundVBO;
            case GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING -> boundEBO;

            case GL20.GL_CURRENT_PROGRAM -> activeProgram;

            case GL30.GL_VERTEX_ARRAY_BINDING -> boundVAO;

            case GL30.GL_DRAW_FRAMEBUFFER_BINDING -> drawFramebuffer;
            case GL30.GL_READ_FRAMEBUFFER_BINDING -> readFramebuffer;

            default -> {
                yield switch (pname) {
                    case GL11.GL_FOG_MODE -> fogState.getFogMode();
                    case GL11.GL_LINE_STIPPLE_PATTERN -> lineState.getStipplePattern() & 0xFFFF;
                    case GL11.GL_LINE_STIPPLE_REPEAT -> lineState.getStippleFactor();
                    default -> GL11.glGetInteger(pname);
                };
            }
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
            default -> {}
        }
    }

    public static void glGetLight(int light, int pname, FloatBuffer params) {
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
            default -> {}
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
            case GL11.GL_TEXTURE_MATRIX -> textures.getTextureUnitMatrix(getActiveTextureUnit()).get(0, params);
            case GL11.GL_COLOR_CLEAR_VALUE -> clearColor.get(params);
            case GL11.GL_CURRENT_COLOR -> color.get(params);
            case GL11.GL_DEPTH_RANGE -> {
                params.put((float) viewportState.depthRangeNear);
                params.put((float) viewportState.depthRangeFar);
            }
            case GL14.GL_BLEND_COLOR -> {
                params.put(blendState.getBlendColorR()).put(blendState.getBlendColorG())
                    .put(blendState.getBlendColorB()).put(blendState.getBlendColorA());
            }
            case GL11.GL_CURRENT_NORMAL -> {
                final Vector3f normal = ShaderManager.getCurrentNormal();
                params.put(normal.x).put(normal.y).put(normal.z);
            }
            case GL11.GL_CURRENT_TEXTURE_COORDS -> {
                final Vector4f tc = ShaderManager.getCurrentTexCoord();
                params.put(tc.x).put(tc.y).put(tc.z).put(tc.w);
            }
            case GL11.GL_FOG_COLOR -> {
                final FloatBuffer fogBuf = fogState.getFogColorBuffer();
                params.put(fogBuf.get(0)).put(fogBuf.get(1)).put(fogBuf.get(2)).put(fogBuf.get(3));
            }
            case GL11.GL_LIGHT_MODEL_AMBIENT -> lightModel.ambient.get(0, params);
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
        return switch (pname) {
            case GL11.GL_ALPHA_TEST_REF -> alphaState.getReference();
            case GL11.GL_FOG_DENSITY -> fogState.getDensity();
            case GL11.GL_FOG_START -> fogState.getStart();
            case GL11.GL_FOG_END -> fogState.getEnd();
            case GL11.GL_DEPTH_CLEAR_VALUE -> (float) depthState.getClearValue();
            case GL11.GL_LINE_WIDTH -> lineState.getWidth();
            case GL11.GL_POINT_SIZE -> pointState.getSize();
            default -> GL11.glGetFloat(pname);
        };
    }

    // GLStateManager Functions

    public static void glBlendColor(float red, float green, float blue, float alpha) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordBlendColor(red, green, blue, alpha);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();
        final boolean bypass = BYPASS_CACHE || !caching;
        if (bypass || red != blendState.getBlendColorR() || green != blendState.getBlendColorG()
            || blue != blendState.getBlendColorB() || alpha != blendState.getBlendColorA()) {
            if (caching) {
                blendState.setBlendColorR(red);
                blendState.setBlendColorG(green);
                blendState.setBlendColorB(blue);
                blendState.setBlendColorA(alpha);
            }
            GL14.glBlendColor(red, green, blue, alpha);
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
        if (Iris.enabled) {
            if (BlendModeStorage.isBlendLocked()) {
                BlendModeStorage.deferBlendModeToggle(true);
                return;
            }
        }
        blendMode.enable();
    }

    public static void disableBlend() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDisable(GL11.GL_BLEND);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        if (Iris.enabled) {
            if (BlendModeStorage.isBlendLocked()) {
                BlendModeStorage.deferBlendModeToggle(false);
                return;
            }
        }
        blendMode.disable();
    }

    public static void enableScissorTest() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordEnable(GL11.GL_SCISSOR_TEST);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        scissorTest.enable();
    }

    public static void disableScissorTest() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDisable(GL11.GL_SCISSOR_TEST);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        scissorTest.disable();
    }

    public static void glBlendFunc(int srcFactor, int dstFactor) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordBlendFunc(srcFactor, dstFactor, srcFactor, dstFactor);
            if (mode == RecordMode.COMPILE) {
                return;
            }
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
            OpenGlHelper.glBlendFunc(srcFactor, dstFactor, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            return;
        }
        final boolean bypass = BYPASS_CACHE || !caching;
        if (bypass || blendState.getSrcRgb() != srcFactor || blendState.getDstRgb() != dstFactor
            || blendState.getSrcAlpha() != srcFactor || blendState.getDstAlpha() != dstFactor) {
            if (caching) blendState.setAll(srcFactor, dstFactor, srcFactor, dstFactor);
            GL11.glBlendFunc(srcFactor, dstFactor);
        }
    }

    public static void glBlendEquation(int mode) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glBlendEquation in display lists not yet implemented - if you see this, please report!");
        }
        final boolean caching = isCachingEnabled();
        final boolean bypass = BYPASS_CACHE || !caching;
        if (bypass || blendState.getEquationRgb() != mode || blendState.getEquationAlpha() != mode) {
            if (caching) {
                blendState.setEquationRgb(mode);
                blendState.setEquationAlpha(mode);
            }
            GL14.glBlendEquation(mode);
        }
    }

    public static void glBlendEquationSeparate(int modeRGB, int modeAlpha) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glBlendEquationSeparate in display lists not yet implemented - if you see this, please report!");
        }
        final boolean caching = isCachingEnabled();
        final boolean bypass = BYPASS_CACHE || !caching;
        if (bypass || blendState.getEquationRgb() != modeRGB || blendState.getEquationAlpha() != modeAlpha) {
            if (caching) {
                blendState.setEquationRgb(modeRGB);
                blendState.setEquationAlpha(modeAlpha);
            }
            GL20.glBlendEquationSeparate(modeRGB, modeAlpha);
        }
    }

    public static void tryBlendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordBlendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (mode == RecordMode.COMPILE) {
                return;
            }
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
            OpenGlHelper.glBlendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha);
        }
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
        dirtyNormalAttrib = true;
    }
    public static void glNormal3d(double nx, double ny, double nz) {
        final float fnx = (float) nx, fny = (float) ny, fnz = (float) nz;
        ShaderManager.setCurrentNormal(fnx, fny, fnz);
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.setNormal(fnx, fny, fnz);
            return;
        }
        dirtyNormalAttrib = true;
    }
    public static void glNormal3f(float nx, float ny, float nz) {
        ShaderManager.setCurrentNormal(nx, ny, nz);
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.setNormal(nx, ny, nz);
            return;
        }
        dirtyNormalAttrib = true;
    }
    public static void glNormal3i(int nx, int ny, int nz) {
        final float fnx = nx / 2147483647.0f, fny = ny / 2147483647.0f, fnz = nz / 2147483647.0f;
        ShaderManager.setCurrentNormal(fnx, fny, fnz);
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.setNormal(fnx, fny, fnz);
            return;
        }
        dirtyNormalAttrib = true;
    }

    public static void glDepthFunc(int func) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDepthFunc(func);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();
        if (BYPASS_CACHE || !caching || func != depthState.getFunc()) {
            if (caching) depthState.setFunc(func);
            GL11.glDepthFunc(func);
        }
    }

    public static void glDepthMask(boolean mask) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDepthMask(mask);
            if (mode == RecordMode.COMPILE) {
                return;
            }
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
            GL11.glDepthMask(mask);
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
                colorGeneration++;
            }
            if (ImmediateModeRecorder.isDrawing()) {
                ImmediateModeRecorder.setColor(red, green, blue, alpha);
            }
            dirtyColorAttrib = true;
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
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordColorMask(red, green, blue, alpha);
            if (mode == RecordMode.COMPILE) {
                return;
            }
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
            GL11.glColorMask(red, green, blue, alpha);
        }
    }

    // Clear Color
    public static void glClearColor(float red, float green, float blue, float alpha) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordClearColor(red, green, blue, alpha);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
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

    public static void glClearDepth(double depth) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordClearDepth(depth);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        if (isCachingEnabled()) depthState.setClearValue(depth);
        GL11.glClearDepth(depth);
    }

    // ALPHA
    public static void enableAlphaTest() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordEnable(GL11.GL_ALPHA_TEST);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        if (Iris.enabled) {
            if (AlphaTestStorage.isAlphaTestLocked()) {
                AlphaTestStorage.deferAlphaTestToggle(true);
                return;
            }
        }
        alphaTest.enable();
    }

    public static void disableAlphaTest() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDisable(GL11.GL_ALPHA_TEST);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        if (Iris.enabled) {
            if (AlphaTestStorage.isAlphaTestLocked()) {
                AlphaTestStorage.deferAlphaTestToggle(false);
                return;
            }
        }
        alphaTest.disable();
    }

    public static void glAlphaFunc(int function, float reference) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordAlphaFunc(function, reference);
            if (mode == RecordMode.COMPILE) {
                return;
            }
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
            fragmentGeneration++;
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
        final boolean caching = isCachingEnabled();
        if (BYPASS_CACHE || !caching || getActiveTextureUnit() != newTexture) {
            if (caching) activeTextureUnit.setValue(newTexture);
            GL13.glActiveTexture(texture);
        }
    }

    public static void glActiveTextureARB(int texture) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordActiveTexture(texture);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final int newTexture = texture - GL13.GL_TEXTURE0;
        final boolean caching = isCachingEnabled();
        if (BYPASS_CACHE || !caching || getActiveTextureUnit() != newTexture) {
            if (caching) activeTextureUnit.setValue(newTexture);
            ARBMultitexture.glActiveTextureARB(texture);
        }
    }

    public static void glClientActiveTexture(int texture) {
        clientActiveTextureUnit = texture - GL13.GL_TEXTURE0;
    }

    public static int getClientActiveTextureUnit() {
        return clientActiveTextureUnit;
    }

    private static int texCoordAttributeLocation() {
        return switch (clientActiveTextureUnit) {
            case 0 -> Usage.PRIMARY_UV.getAttributeLocation();
            case 1 -> Usage.SECONDARY_UV.getAttributeLocation();
            default -> -1;
        };
    }

    private static int getBoundTexture() {
        return getBoundTexture(activeTextureUnit.getValue());
    }

    private static int getBoundTexture(int unit) {
        return textures.getTextureUnitBindings(unit).getBinding();
    }

    public static void glBindTexture(int target, int texture) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordBindTexture(target, texture);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        if (target != GL11.GL_TEXTURE_2D) {
            // We're only supporting 2D textures for now
            GL11.glBindTexture(target, texture);
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
            GL11.glBindTexture(target, texture);
            textureUnit.setBinding(texture);
            if (texture != 0 && activeUnit > maxBoundTextureUnit) {
                maxBoundTextureUnit = activeUnit;
            }
            TextureTracker.INSTANCE.onBindTexture(texture);
        }
    }

    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, ShortBuffer pixels) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordComplexCommand(TexImage2DCmd.fromShortBuffer(target, level, internalformat, width, height, border, format, type, pixels));
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final int bound_texture = getBoundTextureForServerState();
        TextureInfoCache.INSTANCE.onTexImage2D(bound_texture, target, level, internalformat, width, height);
        final long pixels_buffer_offset = pixels != null ? MemoryUtilities.memAddress(pixels) : 0L;
        if (shouldUseDSA(target)) {
            // Use DSA to upload directly to the texture
            RenderSystem.textureImage2D(bound_texture, target, level, internalformat, width, height, border, format, type, pixels_buffer_offset);
        } else {
            // Non-main thread or proxy texture - use direct GL call
            GL11.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels_buffer_offset);
        }
    }

    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, IntBuffer pixels) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordComplexCommand(TexImage2DCmd.fromIntBuffer(target, level, internalformat, width, height, border, format, type, pixels));
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final int bound_texture = getBoundTextureForServerState();
        TextureInfoCache.INSTANCE.onTexImage2D(bound_texture, target, level, internalformat, width, height);
        final long pixels_buffer_offset = pixels != null ? MemoryUtilities.memAddress(pixels) : 0L;
        if (shouldUseDSA(target)) {
            // Use DSA to upload directly to the texture
            RenderSystem.textureImage2D(bound_texture, target, level, internalformat, width, height, border, format, type, pixels_buffer_offset);
        } else {
            // Non-main thread or proxy texture - use direct GL call
            GL11.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels_buffer_offset);
        }
    }

    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, FloatBuffer pixels) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordComplexCommand(TexImage2DCmd.fromFloatBuffer(target, level, internalformat, width, height, border, format, type, pixels));
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final int bound_texture = getBoundTextureForServerState();
        TextureInfoCache.INSTANCE.onTexImage2D(bound_texture, target, level, internalformat, width, height);
        final long pixels_buffer_offset = pixels != null ? MemoryUtilities.memAddress(pixels) : 0L;
        if (shouldUseDSA(target)) {
            // Use DSA to upload directly to the texture
            RenderSystem.textureImage2D(bound_texture, target, level, internalformat, width, height, border, format, type, pixels_buffer_offset);
        } else {
            // Non-main thread or proxy texture - use direct GL call
            GL11.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels_buffer_offset);
        }
    }

    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, DoubleBuffer pixels) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordComplexCommand(TexImage2DCmd.fromDoubleBuffer(target, level, internalformat, width, height, border, format, type, pixels));
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final int bound_texture = getBoundTextureForServerState();
        TextureInfoCache.INSTANCE.onTexImage2D(bound_texture, target, level, internalformat, width, height);
        final long pixels_buffer_offset = pixels != null ? MemoryUtilities.memAddress(pixels) : 0L;
        if (shouldUseDSA(target)) {
            // Use DSA to upload directly to the texture
            RenderSystem.textureImage2D(bound_texture, target, level, internalformat, width, height, border, format, type, pixels_buffer_offset);
        } else {
            // Non-main thread or proxy texture - use direct GL call
            GL11.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels_buffer_offset);
        }
    }

    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, ByteBuffer pixels) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordComplexCommand(TexImage2DCmd.fromByteBuffer(target, level, internalformat, width, height, border, format, type, pixels));
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final int bound_texture = getBoundTextureForServerState();
        TextureInfoCache.INSTANCE.onTexImage2D(bound_texture, target, level, internalformat, width, height);
        final long pixels_buffer_offset = pixels != null ? MemoryUtilities.memAddress(pixels) : 0L;
        if (shouldUseDSA(target)) {
            // Use DSA to upload directly to the texture
            RenderSystem.textureImage2D(bound_texture, target, level, internalformat, width, height, border, format, type, pixels_buffer_offset);
        } else {
            // Non-main thread or proxy texture - use direct GL call
            GL11.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels_buffer_offset);
        }
    }

    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, long pixels_buffer_offset) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glTexImage2D with buffer offset in display lists not yet supported");
        }
        final int bound_texture = getBoundTextureForServerState();
        TextureInfoCache.INSTANCE.onTexImage2D(bound_texture, target, level, internalformat, width, height);
        if (shouldUseDSA(target)) {
            // Use DSA to upload directly to the texture - keeps GL binding state unchanged
            RenderSystem.textureImage2D(bound_texture, target, level, internalformat, width, height, border, format, type, pixels_buffer_offset);
        } else {
            // Non-main thread or proxy texture - use direct GL call
            GL11.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels_buffer_offset);
        }
    }

    public static void glTexCoord1f(float s) {
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.setTexCoord(s, 0.0f);
            return;
        }
        ShaderManager.setCurrentTexCoord(s, 0.0f, 0.0f, 1.0f);
        dirtyTexCoordAttrib = true;
    }
    public static void glTexCoord1d(double s) {
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.setTexCoord((float) s, 0.0f);
            return;
        }
        ShaderManager.setCurrentTexCoord((float) s, 0.0f, 0.0f, 1.0f);
        dirtyTexCoordAttrib = true;
    }
    public static void glTexCoord2f(float s, float t) {
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.setTexCoord(s, t);
            return;
        }
        ShaderManager.setCurrentTexCoord(s, t, 0.0f, 1.0f);
        dirtyTexCoordAttrib = true;
    }
    public static void glTexCoord2d(double s, double t) {
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.setTexCoord((float) s, (float) t);
            return;
        }
        ShaderManager.setCurrentTexCoord((float) s, (float) t, 0.0f, 1.0f);
        dirtyTexCoordAttrib = true;
    }
    public static void glTexCoord3f(float s, float t, float r) {
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.setTexCoord(s, t);  // Only track s,t for 2D textures
            return;
        }
        ShaderManager.setCurrentTexCoord(s, t, r, 1.0f);
        dirtyTexCoordAttrib = true;
    }
    public static void glTexCoord3d(double s, double t, double r) {
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.setTexCoord((float) s, (float) t);
            return;
        }
        ShaderManager.setCurrentTexCoord((float) s, (float) t, (float) r, 1.0f);
        dirtyTexCoordAttrib = true;
    }
    public static void glTexCoord4f(float s, float t, float r, float q) {
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.setTexCoord(s, t);
            return;
        }
        ShaderManager.setCurrentTexCoord(s, t, r, q);
        dirtyTexCoordAttrib = true;
    }
    public static void glTexCoord4d(double s, double t, double r, double q) {
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.setTexCoord((float) s, (float) t);
            return;
        }
        ShaderManager.setCurrentTexCoord((float) s, (float) t, (float) r, (float) q);
        dirtyTexCoordAttrib = true;
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
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordEnable(GL11.GL_TEXTURE_2D);
            if (mode == RecordMode.COMPILE) {
                return;
            }
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
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDisable(GL11.GL_TEXTURE_2D);
            if (mode == RecordMode.COMPILE) {
                return;
            }
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
        if (DisplayListManager.isRecording()) {
            ImmediateModeRecorder.begin(mode);
            return;
        }
        ImmediateModeRecorder.beginLive(mode);
    }

    public static void glEnd() {
        if (DisplayListManager.isRecording()) {
            final DirectTessellator result = ImmediateModeRecorder.end();
            if (result != null) {
                DisplayListManager.addImmediateModeDraw(result);
            }
            return;
        }
        final DirectTessellator result = ImmediateModeRecorder.end();
        if (result != null) {
            TessellatorStreamingDrawer.drawDirect(result);
        }
    }

    // Vertex methods for display list recording and live immediate mode
    public static void glVertex2f(float x, float y) {
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.vertex(x, y, 0.0f);
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
    public static void glDrawElements(int mode, ByteBuffer indices) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glDrawElements in display lists not yet implemented - if you see this, please report!");
        }
        ShaderManager.getInstance().preDraw();
        GL11.glDrawElements(mode, indices);
    }

    public static void glDrawElements(int mode, IntBuffer indices) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glDrawElements in display lists not yet implemented - if you see this, please report!");
        }
        ShaderManager.getInstance().preDraw();
        if (mode == GL11.GL_QUADS) {
            QuadConverter.drawQuadElementsAsTriangles(indices);
        } else {
            GL11.glDrawElements(mode, indices);
        }
    }

    public static void glDrawElements(int mode, ShortBuffer indices) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glDrawElements in display lists not yet implemented - if you see this, please report!");
        }
        ShaderManager.getInstance().preDraw();
        if (mode == GL11.GL_QUADS) {
            QuadConverter.drawQuadElementsAsTriangles(indices);
        } else {
            GL11.glDrawElements(mode, indices);
        }
    }

    public static void glDrawElements(int mode, int count, int type, ByteBuffer indices) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glDrawElements in display lists not yet implemented - if you see this, please report!");
        }
        ShaderManager.getInstance().preDraw();
        if (mode == GL11.GL_QUADS) {
            QuadConverter.drawQuadElementsAsTriangles(count, type, indices);
        } else {
            GL11.glDrawElements(mode, count, type, indices);
        }
    }

    public static void glDrawElements(int mode, int indices_count, int type, long indices_buffer_offset) {
        final RecordMode recordMode = DisplayListManager.getRecordMode();
        if (recordMode != RecordMode.NONE) {
            if (isVAOBound()) {
                DisplayListManager.recordDrawElements(mode, indices_count, type, indices_buffer_offset);
            } else {
                throw new UnsupportedOperationException("glDrawElements in display lists not yet implemented - if you see this, please report!");
            }
            if (recordMode == RecordMode.COMPILE) {
                return;
            }
        }
        ShaderManager.getInstance().preDraw();
        if (mode == GL11.GL_QUADS) {
            QuadConverter.drawQuadElementsAsTriangles(indices_count, type, indices_buffer_offset);
        } else {
            GL11.glDrawElements(mode, indices_count, type, indices_buffer_offset);
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
        if (isCachingEnabled()) drawBuffer.setValue(mode);
        GL11.glDrawBuffer(mode);
    }

    public static void glDrawArrays(int mode, int first, int count) {
        if (DisplayListManager.isRecording()) {
            if (isVAOBound() || isVBOBound()) { // TODO VBO rendering needs to be replaced with proper VAO compilation
                DisplayListManager.recordDrawArrays(mode, first, count);
                return;
            }
            final DirectTessellator result = ImmediateModeRecorder.processDrawArrays(mode, first, count);
            if (result != null) {
                DisplayListManager.addImmediateModeDraw(result);
            }
            return;
        }
        ShaderManager.getInstance().preDraw();
        if (mode == GL11.GL_QUADS) {
            QuadConverter.drawQuadsAsTriangles(first, count);
        } else {
            GL11.glDrawArrays(mode, first, count);
        }
    }

    // Client array state interception methods (for glDrawArrays conversion)
    // State is tracked globally in clientArrayState, which ImmediateModeRecorder reads from directly.

    public static void glVertexPointer(int size, int stride, IntBuffer pointer) {
        glVertexPointer(size, GL11.GL_INT, stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glVertexPointer(int size, int stride, ShortBuffer pointer) {
        glVertexPointer(size, GL11.GL_SHORT, stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glVertexPointer(int size, int stride, FloatBuffer pointer) {
        if (!ShaderManager.getInstance().isActive()) return;
        GL20.glVertexAttribPointer(Usage.POSITION.getAttributeLocation(), size, GL11.GL_FLOAT, false, stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glVertexPointer(int size, int type, int stride, ByteBuffer pointer) {
        if (!ShaderManager.getInstance().isActive()) return;
        GL20.glVertexAttribPointer(Usage.POSITION.getAttributeLocation(), size, type, false, stride, pointer);
    }

    public static void glVertexPointer(int size, int type, int stride, long pointer_buffer_offset) {
        if (!ShaderManager.getInstance().isActive()) return;
        GL20.glVertexAttribPointer(Usage.POSITION.getAttributeLocation(), size, type, false, stride, pointer_buffer_offset);
    }

    public static void glColorPointer(int size, int stride, FloatBuffer pointer) {
        if (!ShaderManager.getInstance().isActive()) return;
        GL20.glVertexAttribPointer(Usage.COLOR.getAttributeLocation(), size, GL11.GL_FLOAT, Usage.COLOR.isNormalized(), stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glColorPointer(int size, int type, int stride, ByteBuffer pointer) {
        if (!ShaderManager.getInstance().isActive()) return;
        GL20.glVertexAttribPointer(Usage.COLOR.getAttributeLocation(), size, type, Usage.COLOR.isNormalized(), stride, pointer);
    }

    public static void glColorPointer(int size, boolean unsigned, int stride, ByteBuffer pointer) {
        if (!ShaderManager.getInstance().isActive()) return;
        final int type = unsigned ? GL11.GL_UNSIGNED_BYTE : GL11.GL_BYTE;
        GL20.glVertexAttribPointer(Usage.COLOR.getAttributeLocation(), size, type, Usage.COLOR.isNormalized(), stride, pointer);
    }

    public static void glColorPointer(int size, int type, int stride, long pointer_buffer_offset) {
        if (!ShaderManager.getInstance().isActive()) return;
        GL20.glVertexAttribPointer(Usage.COLOR.getAttributeLocation(), size, type, Usage.COLOR.isNormalized(), stride, pointer_buffer_offset);
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

    public static void glNormalPointer(int type, int stride, ByteBuffer pointer) {
        if (!ShaderManager.getInstance().isActive()) return;
        GL20.glVertexAttribPointer(Usage.NORMAL.getAttributeLocation(), 3, type, Usage.NORMAL.isNormalized(), stride, pointer);
    }

    public static void glNormalPointer(int type, int stride, FloatBuffer pointer) {
        if (!ShaderManager.getInstance().isActive()) return;
        GL20.glVertexAttribPointer(Usage.NORMAL.getAttributeLocation(), 3, GL11.GL_FLOAT, Usage.NORMAL.isNormalized(), stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glNormalPointer(int type, int stride, ShortBuffer pointer) {
        if (!ShaderManager.getInstance().isActive()) return;
        GL20.glVertexAttribPointer(Usage.NORMAL.getAttributeLocation(), 3, GL11.GL_SHORT, Usage.NORMAL.isNormalized(), stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glNormalPointer(int type, int stride, IntBuffer pointer) {
        if (!ShaderManager.getInstance().isActive()) return;
        GL20.glVertexAttribPointer(Usage.NORMAL.getAttributeLocation(), 3, GL11.GL_INT, Usage.NORMAL.isNormalized(), stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glNormalPointer(int type, int stride, long pointer_buffer_offset) {
        if (!ShaderManager.getInstance().isActive()) return;
        GL20.glVertexAttribPointer(Usage.NORMAL.getAttributeLocation(), 3, type, Usage.NORMAL.isNormalized(), stride, pointer_buffer_offset);
    }

    public static void glTexCoordPointer(int size, int type, int stride, ByteBuffer pointer) {
        if (!ShaderManager.getInstance().isActive()) return;
        final int loc = texCoordAttributeLocation();
        if (loc < 0) return;
        GL20.glVertexAttribPointer(loc, size, type, false, stride, pointer);
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

    public static void glTexCoordPointer(int size, int type, int stride, FloatBuffer pointer) {
        if (!ShaderManager.getInstance().isActive()) return;
        final int loc = texCoordAttributeLocation();
        if (loc < 0) return;
        GL20.glVertexAttribPointer(loc, size, GL11.GL_FLOAT, false, stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glTexCoordPointer(int size, int type, int stride, ShortBuffer pointer) {
        if (!ShaderManager.getInstance().isActive()) return;
        final int loc = texCoordAttributeLocation();
        if (loc < 0) return;
        GL20.glVertexAttribPointer(loc, size, GL11.GL_SHORT, false, stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glTexCoordPointer(int size, int type, int stride, IntBuffer pointer) {
        if (!ShaderManager.getInstance().isActive()) return;
        final int loc = texCoordAttributeLocation();
        if (loc < 0) return;
        GL20.glVertexAttribPointer(loc, size, GL11.GL_INT, false, stride, MemoryUtilities.memByteBuffer(pointer));
    }

    public static void glTexCoordPointer(int size, int type, int stride, long pointer_buffer_offset) {
        if (!ShaderManager.getInstance().isActive()) return;
        final int loc = texCoordAttributeLocation();
        if (loc < 0) return;
        GL20.glVertexAttribPointer(loc, size, type, false, stride, pointer_buffer_offset);
    }

    public static void glEnableClientState(int cap) {
        if (!ShaderManager.getInstance().isActive()) return;
        final int location = clientStateToAttributeLocation(cap);
        if (location >= 0) GL20.glEnableVertexAttribArray(location);
        final int flag = clientStateToVertexFlag(cap);
        if (flag != 0) ShaderManager.getInstance().enableClientVertexFlag(flag);
    }

    public static void glDisableClientState(int cap) {
        if (!ShaderManager.getInstance().isActive()) return;
        final int location = clientStateToAttributeLocation(cap);
        if (location >= 0) GL20.glDisableVertexAttribArray(location);
        final int flag = clientStateToVertexFlag(cap);
        if (flag != 0) ShaderManager.getInstance().disableClientVertexFlag(flag);
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
            case GL11.GL_TEXTURE_COORD_ARRAY -> switch (clientActiveTextureUnit) {
                case 0 -> VertexFlags.TEXTURE_BIT;
                case 1 -> VertexFlags.BRIGHTNESS_BIT;
                default -> 0;
            };
            default -> 0; // GL_VERTEX_ARRAY — position is implicit
        };
    }

    private static final int CLIENT_ATTRIB_STACK_DEPTH = 16;
    private static final int[] clientAttribSavedTextureUnit = new int[CLIENT_ATTRIB_STACK_DEPTH];
    private static final int[] clientAttribSavedVertexFlags = new int[CLIENT_ATTRIB_STACK_DEPTH];
    private static int clientAttribStackPointer = 0;

    public static void glPushClientAttrib(int mask) {
        if (clientAttribStackPointer < CLIENT_ATTRIB_STACK_DEPTH) {
            clientAttribSavedTextureUnit[clientAttribStackPointer] = clientActiveTextureUnit;
            clientAttribSavedVertexFlags[clientAttribStackPointer] = ShaderManager.getInstance().getCurrentVertexFlags();
            clientAttribStackPointer++;
        }
    }

    public static void glPopClientAttrib() {
        if (clientAttribStackPointer > 0) {
            clientAttribStackPointer--;
            clientActiveTextureUnit = clientAttribSavedTextureUnit[clientAttribStackPointer];
            ShaderManager.getInstance().setCurrentVertexFlags(clientAttribSavedVertexFlags[clientAttribStackPointer]);
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
        if (isCachingEnabled()) logicOpMode.setValue(opcode);
        GL11.glLogicOp(opcode);
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
        cullState.enable();
    }

    public static void disableCull() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDisable(GL11.GL_CULL_FACE);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        cullState.disable();
    }

    public static void enableDepthTest() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordEnable(GL11.GL_DEPTH_TEST);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        depthTest.enable();
    }

    public static void disableDepthTest() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDisable(GL11.GL_DEPTH_TEST);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        depthTest.disable();
    }

    public static void enableLighting() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordEnable(GL11.GL_LIGHTING);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        lightingState.enable();
    }

    public static void enableLight(int light) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordEnable(GL11.GL_LIGHT0 + light);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        lightStates[light].enable();
    }

    public static void enableColorMaterial() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordEnable(GL11.GL_COLOR_MATERIAL);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        colorMaterial.enable();
        final float r = getColor().getRed();
        final float g = getColor().getGreen();
        final float b = getColor().getBlue();
        final float a = getColor().getAlpha();
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
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDisable(GL11.GL_COLOR_MATERIAL);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        colorMaterial.disable();
    }

    public static void disableLighting() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDisable(GL11.GL_LIGHTING);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        lightingState.disable();
    }

    public static void disableLight(int light) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDisable(GL11.GL_LIGHT0 + light);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        lightStates[light].disable();
    }

    public static void enableRescaleNormal() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordEnable(GL12.GL_RESCALE_NORMAL);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        rescaleNormalState.enable();
    }

    public static void disableRescaleNormal() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDisable(GL12.GL_RESCALE_NORMAL);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        rescaleNormalState.disable();
    }

    public static void enableFog() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordEnable(GL11.GL_FOG);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        fogMode.enable();
    }

    public static void disableFog() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDisable(GL11.GL_FOG);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        fogMode.disable();
    }

    public static void glFog(int pname, FloatBuffer param) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordFog(pname, param);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        // TODO: Iris Notifier
        if (HAS_MULTIPLE_SET.contains(pname)) {
            if (pname == GL11.GL_FOG_COLOR && isCachingEnabled()) {
                final float red = param.get(0);
                final float green = param.get(1);
                final float blue = param.get(2);

                fogState.getFogColor().set(red, green, blue);
                fogState.setFogAlpha(param.get(3));
                fogState.getFogColorBuffer().clear();
                fogState.getFogColorBuffer().put((FloatBuffer) param.position(0)).flip();
                fragmentGeneration++;
            }
        } else {
            GLStateManager.glFogf(pname, param.get(0));
        }
    }

    public static Vector3d getFogColor() {
        return fogState.getFogColor();
    }

    public static void fogColor(float red, float green, float blue, float alpha) {
        final boolean caching = isCachingEnabled();
        if (BYPASS_CACHE || !caching || red != fogState.getFogColor().x || green != fogState.getFogColor().y || blue != fogState.getFogColor().z || alpha != fogState.getFogAlpha()) {
            if (caching) {
                fogState.getFogColor().set(red, green, blue);
                fogState.setFogAlpha(alpha);
                fogState.getFogColorBuffer().clear();
                fogState.getFogColorBuffer().put(red).put(green).put(blue).put(alpha).flip();
                fragmentGeneration++;
            }
        }
    }

    public static void glFogf(int pname, float param) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordFogf(pname, param);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        // Update cached state (FFP shader reads from cache)
        if (isCachingEnabled()) {
            switch (pname) {
                case GL11.GL_FOG_DENSITY -> { fogState.setDensity(param); fragmentGeneration++; }
                case GL11.GL_FOG_START -> { fogState.setStart(param); fragmentGeneration++; }
                case GL11.GL_FOG_END -> { fogState.setEnd(param); fragmentGeneration++; }
                case GL11.GL_FOG_MODE -> { fogState.setFogMode((int) param); fragmentGeneration++; }
            }
        }
    }

    public static void glFogi(int pname, int param) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordFogi(pname, param);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        // Update cached state (FFP shader reads from cache)
        if (isCachingEnabled() && pname == GL11.GL_FOG_MODE) {
            fogState.setFogMode(param);
            fragmentGeneration++;
        }
    }

    public static void setFogBlack() {
        glFogf(GL11.GL_FOG_COLOR, 0.0F);
    }

    public static void glShadeModel(int mode) {
        final RecordMode recordMode = DisplayListManager.getRecordMode();
        if (recordMode != RecordMode.NONE) {
            DisplayListManager.recordShadeModel(mode);
            if (recordMode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();
        final int oldValue = shadeModelState.getValue();
        final boolean needsUpdate = BYPASS_CACHE || !caching || oldValue != mode;

        if (needsUpdate) {
            if (caching) {
                shadeModelState.setValue(mode);
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
            for (int i = 0; i <= maxBoundTextureUnit; i++) {
                if (textures.getTextureUnitBindings(i).getBinding() == id) {
                    textures.getTextureUnitBindings(i).setBinding(0);
                }
            }
        }
    }

    public static void makeCurrent(Drawable drawable) throws LWJGLException {
        drawable.makeCurrent();
        CurrentThread = Thread.currentThread();

        // Post-splash: single context, always cache
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
        }
        else if (drawableGLHolder == CurrentThread) {
            // This thread held DrawableGL but is switching AWAY - disable caching
            drawableGLHolder = null;
        }
        // else: Thread switching to non-DrawableGL, wasn't holder - no change needed
    }

    /**
     * Mark splash as complete - enables fast path that always caches.
     * Called when finish() permanently switches to DrawableGL for the main game loop.
     */
    public static void markSplashComplete() {
        splashComplete = true;
        drawableGLHolder = null;
        drawableGL = null;
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
     * Get a compiled display list from the cache.
     * Used for executing nested display lists.
     * @param list The display list ID
     * @return The CompiledDisplayList, or null if not found
     */
    public static CompiledDisplayList getDisplayList(int list) {
        return DisplayListManager.getDisplayList(list);
    }

    public static void glCallList(int list) {
        GLDebug.pushGroup("glCallList " + list);
        DisplayListManager.glCallList(list);
        GLDebug.popGroup();
    }

    public static void pushState(int mask) {
        if (attribDepth >= MAX_ATTRIB_STACK_DEPTH) {
            throw new IllegalStateException("Attrib stack overflow: max depth " + MAX_ATTRIB_STACK_DEPTH + " reached");
        }
        attribs.push(mask);

        // Snapshot generation counters so we can detect actual changes at pop time
        savedMvGen[attribDepth] = mvGeneration;
        savedProjGen[attribDepth] = projGeneration;
        savedTexMatGen[attribDepth] = texMatrixGeneration;
        savedLightingGen[attribDepth] = lightingGeneration;
        savedFragmentGen[attribDepth] = fragmentGeneration;
        savedColorGen[attribDepth] = colorGeneration;
        savedNormalGen[attribDepth] = ShaderManager.getNormalGeneration();
        savedTexCoordGen[attribDepth] = ShaderManager.getTexCoordGeneration();

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

        // Third: apply restored state to the GL driver. BooleanStateStacks already issue GL calls via setEnabled(); non-boolean stacks are pure data
        // containers, so we must explicitly drive GL here.
        applyRestoredState(mask);
    }

    /**
     * After popping GLSM stacks, apply restored state to the GL driver.
     */
    private static void applyRestoredState(int mask) {
        if ((mask & GL11.GL_DEPTH_BUFFER_BIT) != 0) {
            GL11.glDepthFunc(depthState.getFunc());
            GL11.glDepthMask(depthState.isEnabled());
            GL11.glClearDepth(depthState.getClearValue());
        }
        if ((mask & GL11.GL_COLOR_BUFFER_BIT) != 0) {
            GL14.glBlendFuncSeparate(blendState.getSrcRgb(), blendState.getDstRgb(), blendState.getSrcAlpha(), blendState.getDstAlpha());
            GL20.glBlendEquationSeparate(blendState.getEquationRgb(), blendState.getEquationAlpha());
            GL14.glBlendColor(blendState.getBlendColorR(), blendState.getBlendColorG(), blendState.getBlendColorB(), blendState.getBlendColorA());
            GL11.glColorMask(colorMask.red, colorMask.green, colorMask.blue, colorMask.alpha);
            GL11.glClearColor(clearColor.getRed(), clearColor.getGreen(), clearColor.getBlue(), clearColor.getAlpha());
            // Draw buffer is per-framebuffer state; only restore on the default framebuffer
            if (drawFramebuffer == 0) {
                GL11.glDrawBuffer(drawBuffer.getValue());
            }
            GL11.glLogicOp(logicOpMode.getValue());
        }
        if ((mask & GL11.GL_STENCIL_BUFFER_BIT) != 0) {
            GL20.glStencilFuncSeparate(GL11.GL_FRONT, stencilState.getFuncFront(), stencilState.getRefFront(), stencilState.getValueMaskFront());
            GL20.glStencilFuncSeparate(GL11.GL_BACK, stencilState.getFuncBack(), stencilState.getRefBack(), stencilState.getValueMaskBack());
            GL20.glStencilOpSeparate(GL11.GL_FRONT, stencilState.getFailOpFront(), stencilState.getZFailOpFront(), stencilState.getZPassOpFront());
            GL20.glStencilOpSeparate(GL11.GL_BACK, stencilState.getFailOpBack(), stencilState.getZFailOpBack(), stencilState.getZPassOpBack());
            GL20.glStencilMaskSeparate(GL11.GL_FRONT, stencilState.getWriteMaskFront());
            GL20.glStencilMaskSeparate(GL11.GL_BACK, stencilState.getWriteMaskBack());
            GL11.glClearStencil(stencilState.getClearValue());
        }
        if ((mask & GL11.GL_VIEWPORT_BIT) != 0) {
            GL11.glViewport(viewportState.x, viewportState.y, viewportState.width, viewportState.height);
            GL11.glDepthRange(viewportState.depthRangeNear, viewportState.depthRangeFar);
        }
        if ((mask & GL11.GL_LINE_BIT) != 0) {
            GL11.glLineWidth(lineState.getWidth());
        }
        if ((mask & GL11.GL_POINT_BIT) != 0) {
            GL11.glPointSize(pointState.getSize());
        }
        if ((mask & GL11.GL_POLYGON_BIT) != 0) {
            // Core profile only supports GL_FRONT_AND_BACK; use frontMode (front/back are always kept in sync since glPolygonMode also forces GL_FRONT_AND_BACK)
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, polygonState.getFrontMode());
            GL11.glPolygonOffset(polygonState.getOffsetFactor(), polygonState.getOffsetUnits());
            GL11.glCullFace(polygonState.getCullFaceMode());
            GL11.glFrontFace(polygonState.getFrontFace());
        }
        if ((mask & GL11.GL_TEXTURE_BIT) != 0) {
            // Restore texture bindings for all units, then restore active unit. activeTextureUnit stores the 0-based index, glActiveTexture needs GL_TEXTURE0 + index.
            for (int i = 0; i < MAX_TEXTURE_UNITS; i++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures.getTextureUnitBindings(i).getBinding());
            }
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + activeTextureUnit.getValue());
        }

        // Bump generation counters only if state actually changed during this push/pop scope.
        // If nothing changed, the shader already has the right uniforms — no need to re-upload.
        final int depth = attribDepth; // already decremented by popState()
        if ((mask & GL11.GL_LIGHTING_BIT) != 0 && lightingGeneration != savedLightingGen[depth]) lightingGeneration++;
        if ((mask & GL11.GL_FOG_BIT) != 0 && fragmentGeneration != savedFragmentGen[depth]) fragmentGeneration++;
        if ((mask & GL11.GL_COLOR_BUFFER_BIT) != 0 && fragmentGeneration != savedFragmentGen[depth]) fragmentGeneration++; // alpha ref
        if ((mask & GL11.GL_TRANSFORM_BIT) != 0) {
            if (mvGeneration != savedMvGen[depth]) mvGeneration++;
            if (projGeneration != savedProjGen[depth]) projGeneration++;
            if (texMatrixGeneration != savedTexMatGen[depth]) texMatrixGeneration++;
        }
        if ((mask & GL11.GL_CURRENT_BIT) != 0) {
            if (colorGeneration != savedColorGen[depth]) colorGeneration++;
            if (ShaderManager.getNormalGeneration() != savedNormalGen[depth]) ShaderManager.bumpNormalGeneration();
            if (ShaderManager.getTexCoordGeneration() != savedTexCoordGen[depth]) ShaderManager.bumpTexCoordGeneration();
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
        GL11.glClear(mask);
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
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordPopAttrib();
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        GLDebug.popGroup();
        poppingAttributes = true;
        GLDebug.pushGroup("popState");
        popState();
        GLDebug.popGroup();
        poppingAttributes = false;
    }

    // Matrix Operations
    public static void glMatrixMode(int mode) {
        final RecordMode recordMode = DisplayListManager.getRecordMode();
        if (recordMode != RecordMode.NONE) {
            DisplayListManager.recordMatrixMode(mode);
            DisplayListManager.resetRelativeTransform();  // Mode switch is a barrier - reset delta tracking
            if (recordMode == RecordMode.COMPILE) {
                return;
            }
        }
        matrixMode.setMode(mode);
    }

    public static void glLoadMatrix(FloatBuffer m) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            final Matrix4f matrix = new Matrix4f().set(m);
            m.rewind();
            DisplayListManager.recordLoadMatrix(matrix);
            // Reset relative transform - subsequent transforms are relative to loaded matrix
            DisplayListManager.resetRelativeTransform();
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        if (isCachingEnabled()) { getMatrixStack().set(m); bumpMatrixGeneration(); }
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
            // Reset relative transform - subsequent transforms are relative to loaded matrix
            DisplayListManager.resetRelativeTransform();
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

    /** Bump the generation counter for the currently active matrix mode. */
    private static void bumpMatrixGeneration() {
        switch (matrixMode.getMode()) {
            case GL11.GL_MODELVIEW -> mvGeneration++;
            case GL11.GL_PROJECTION -> projGeneration++;
            case GL11.GL_TEXTURE -> texMatrixGeneration++;
        }
    }

    public static void glLoadIdentity() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordLoadIdentity();
            // Reset relative transform - accumulated transforms are discarded (overwritten by load)
            DisplayListManager.resetRelativeTransform();
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        if (isCachingEnabled()) { getMatrixStack().identity(); bumpMatrixGeneration(); }
    }

    public static void glTranslatef(float x, float y, float z) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.updateRelativeTransform(x, y, z, DisplayListManager.TransformOp.TRANSLATE, null);
            return;
        }
        if (isCachingEnabled()) { getMatrixStack().translate(x, y, z); bumpMatrixGeneration(); }
    }

    public static void glTranslated(double x, double y, double z) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.updateRelativeTransform((float) x, (float) y, (float) z, DisplayListManager.TransformOp.TRANSLATE, null);
            return;
        }
        if (isCachingEnabled()) { getMatrixStack().translate((float) x, (float) y, (float) z); bumpMatrixGeneration(); }
    }

    public static void glScalef(float x, float y, float z) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.updateRelativeTransform(x, y, z, DisplayListManager.TransformOp.SCALE, null);
            return;
        }
        if (isCachingEnabled()) { getMatrixStack().scale(x, y, z); bumpMatrixGeneration(); }
    }

    public static void glScaled(double x, double y, double z) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.updateRelativeTransform((float) x, (float) y, (float) z, DisplayListManager.TransformOp.SCALE, null);
            return;
        }
        if (isCachingEnabled()) { getMatrixStack().scale((float) x, (float) y, (float) z); bumpMatrixGeneration(); }
    }

    private static final Matrix4f multMatrix = new Matrix4f();
    public static void glMultMatrix(FloatBuffer floatBuffer) {
        multMatrix.set(floatBuffer);
        final int currentMode = matrixMode.getMode();

        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.updateRelativeTransform(multMatrix);
            return;
        }
        if (isCachingEnabled()) { getMatrixStack().mul(multMatrix); bumpMatrixGeneration(); }
    }

    public static final Matrix4d conversionMatrix4d = new Matrix4d();
    public static final Matrix4f conversionMatrix4f = new Matrix4f();
    public static void glMultMatrix(DoubleBuffer matrix) {
        conversionMatrix4d.set(matrix);
        conversionMatrix4f.set(conversionMatrix4d);

        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.updateRelativeTransform(conversionMatrix4f);
            return;
        }
        if (isCachingEnabled()) { getMatrixStack().mul(conversionMatrix4f); bumpMatrixGeneration(); }
    }

    private static final Vector3f rotation = new Vector3f();
    public static void glRotatef(float angle, float x, float y, float z) {
        final float lenSq = x * x + y * y + z * z;
        if (lenSq == 0.0f) return;
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            rotation.set(x, y, z).normalize();
            DisplayListManager.updateRelativeTransform(angle, 0, 0, DisplayListManager.TransformOp.ROTATE, rotation);
            return;
        }
        if (isCachingEnabled()) {
            rotation.set(x, y, z).normalize();
            getMatrixStack().rotate((float)Math.toRadians(angle), rotation);
            bumpMatrixGeneration();
        }
    }

    public static void glRotated(double angle, double x, double y, double z) {
        final double lenSq = x * x + y * y + z * z;
        if (lenSq == 0.0) return;
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            rotation.set((float) x, (float) y, (float) z).normalize();
            DisplayListManager.updateRelativeTransform((float) angle, 0, 0, DisplayListManager.TransformOp.ROTATE, rotation);
            return;
        }
        if (isCachingEnabled()) {
            rotation.set((float) x, (float) y, (float) z).normalize();
            getMatrixStack().rotate((float)Math.toRadians(angle), rotation);
            bumpMatrixGeneration();
        }
    }

    public static void glOrtho(double left, double right, double bottom, double top, double zNear, double zFar) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.updateRelativeTransformOrtho(left, right, bottom, top, zNear, zFar);
            return;  // Transform accumulated, will be emitted at barriers
        }
        if (isCachingEnabled()) { getMatrixStack().ortho((float)left, (float)right, (float)bottom, (float)top, (float)zNear, (float)zFar); bumpMatrixGeneration(); }
    }

    public static void glFrustum(double left, double right, double bottom, double top, double zNear, double zFar) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.updateRelativeTransformFrustum(left, right, bottom, top, zNear, zFar);
            return;  // Transform accumulated, will be emitted at barriers
        }
        if (isCachingEnabled()) { getMatrixStack().frustum((float)left, (float)right, (float)bottom, (float)top, (float)zNear, (float)zFar); bumpMatrixGeneration(); }
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
            } catch(IllegalStateException ignored) {
                if(AngelicaMod.lwjglDebug)
                    AngelicaTweaker.LOGGER.warn("Matrix stack overflow ", new Throwable());
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
            } catch(IllegalStateException ignored) {
                if(AngelicaMod.lwjglDebug)
                    AngelicaTweaker.LOGGER.warn("Matrix stack underflow ", new Throwable());
            }
        }
    }

    private static final Matrix4f gluMatrix = new Matrix4f();
    private static final FloatBuffer gluBuffer = BufferUtils.createFloatBuffer(16);

    public static void gluPerspective(float fovy, float aspect, float zNear, float zFar) {
        gluMatrix.identity().perspective((float)Math.toRadians(fovy), aspect, zNear, zFar);
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
        GLStateManager.glScalef(
            viewport.get(viewport.position() + 2) / deltaX,
            viewport.get(viewport.position() + 3) / deltaY,
            1.0f);
    }

    public static void glViewport(int x, int y, int width, int height) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordViewport(x, y, width, height);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        GL11.glViewport(x, y, width, height);
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
        if (params.remaining() >= 1) {
            final int val = params.get(params.position());
            final int remapped = remapTexClamp(pname, val);
            if (remapped != val) params.put(params.position(), remapped);
        }
        if (target != GL11.GL_TEXTURE_2D || params.remaining() != 1 ) {
            GL11.glTexParameter(target, pname, params);
            return;
        }
        if(!updateTexParameteriCache(target, getBoundTextureForServerState(), pname, params.get(0))) return;

        GL11.glTexParameter(target, pname, params);
    }

    public static void glTexParameter(int target, int pname, FloatBuffer params) {
        if (params.remaining() >= 1) {
            final float val = params.get(params.position());
            final float remapped = remapTexClamp(pname, val);
            if (remapped != val) params.put(params.position(), remapped);
        }
        if (target != GL11.GL_TEXTURE_2D || params.remaining() != 1 ) {
            GL11.glTexParameter(target, pname, params);
            return;
        }
        if(!updateTexParameterfCache(target, getBoundTextureForServerState(), pname, params.get(0))) return;

        GL11.glTexParameter(target, pname, params);
    }

    public static void glTexParameteri(int target, int pname, int param) {
        param = remapTexClamp(pname, param);
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordTexParameteri(target, pname, param);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        if (target != GL11.GL_TEXTURE_2D) {
            GL11.glTexParameteri(target, pname, param);
            return;
        }
        if (!updateTexParameteriCache(target, getBoundTextureForServerState(), pname, param)) return;

        GL11.glTexParameteri(target, pname, param);
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
        param = remapTexClamp(pname, param);
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordTexParameterf(target, pname, param);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        if (target != GL11.GL_TEXTURE_2D) {
            GL11.glTexParameterf(target, pname, param);
            return;
        }
        if (!updateTexParameterfCache(target, getBoundTextureForServerState(), pname, param)) return;

        GL11.glTexParameterf(target, pname, param);
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
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordMaterial(face, pname, params);
            if (mode == RecordMode.COMPILE) {
                return;
            }
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
        lightingGeneration++;
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
        lightingGeneration++;
    }

    public static void glMaterialf(int face, int pname, float val) {
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
        lightingGeneration++;
    }

    public static void glMateriali(int face, int pname, int val) {
        // This will end up no-opping if pname != GL_SHININESS, it is invalid to call this with another pname
        // Command recording happens in glMaterialf
        glMaterialf(face, pname, (float) val);
    }

    public static void glLight(int light, int pname, FloatBuffer params) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordLight(light, pname, params);
            if (mode == RecordMode.COMPILE) {
                return;
            }
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
            default -> {}
        }
        lightingGeneration++;
    }

    public static void glLight(int light, int pname, IntBuffer params) {
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
            default -> {}
        }
        lightingGeneration++;
    }

    public static void glLightf(int light, int pname, float param) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordLightf(light, pname, param);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final LightStateStack lightState = lightDataStates[light - GL11.GL_LIGHT0];
        switch (pname) {
            case GL11.GL_SPOT_EXPONENT -> lightState.setSpotExponent(param);
            case GL11.GL_SPOT_CUTOFF -> lightState.setSpotCutoff(param);
            case GL11.GL_CONSTANT_ATTENUATION -> lightState.setConstantAttenuation(param);
            case GL11.GL_LINEAR_ATTENUATION -> lightState.setLinearAttenuation(param);
            case GL11.GL_QUADRATIC_ATTENUATION -> lightState.setQuadraticAttenuation(param);
            default -> {}
        }
        lightingGeneration++;
    }

    public static void glLighti(int light, int pname, int param) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordLighti(light, pname, param);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final LightStateStack lightState = lightDataStates[light - GL11.GL_LIGHT0];
        switch (pname) {
            case GL11.GL_SPOT_EXPONENT -> lightState.setSpotExponent(param);
            case GL11.GL_SPOT_CUTOFF -> lightState.setSpotCutoff(param);
            case GL11.GL_CONSTANT_ATTENUATION -> lightState.setConstantAttenuation(param);
            case GL11.GL_LINEAR_ATTENUATION -> lightState.setLinearAttenuation(param);
            case GL11.GL_QUADRATIC_ATTENUATION -> lightState.setQuadraticAttenuation(param);
            default -> {}
        }
        lightingGeneration++;
    }

    public static void glLightModel(int pname, FloatBuffer params) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordLightModel(pname, params);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        switch (pname) {
            case GL11.GL_LIGHT_MODEL_AMBIENT -> lightModel.setAmbient(params);
            case GL11.GL_LIGHT_MODEL_LOCAL_VIEWER -> lightModel.setLocalViewer(params);
            case GL11.GL_LIGHT_MODEL_TWO_SIDE -> lightModel.setTwoSide(params);
            default -> {}
        }
        lightingGeneration++;
    }
    public static void glLightModel(int pname, IntBuffer params) {
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
        switch (pname) {
            case GL11.GL_LIGHT_MODEL_AMBIENT -> lightModel.setAmbient(params);
            case GL12.GL_LIGHT_MODEL_COLOR_CONTROL -> lightModel.setColorControl(params);
            case GL11.GL_LIGHT_MODEL_LOCAL_VIEWER -> lightModel.setLocalViewer(params);
            case GL11.GL_LIGHT_MODEL_TWO_SIDE -> lightModel.setTwoSide(params);
            default -> {}
        }
        lightingGeneration++;
    }
    public static void glLightModelf(int pname, float param) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordLightModelf(pname, param);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }

        if (isCachingEnabled()) {
            switch (pname) {
                case GL11.GL_LIGHT_MODEL_LOCAL_VIEWER -> lightModel.setLocalViewer(param);
                case GL11.GL_LIGHT_MODEL_TWO_SIDE -> lightModel.setTwoSide(param);
                default -> {}
            }
            lightingGeneration++;
        }
    }
    public static void glLightModeli(int pname, int param) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordLightModeli(pname, param);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }

        if (isCachingEnabled()) {
            switch (pname) {
                case GL12.GL_LIGHT_MODEL_COLOR_CONTROL -> lightModel.setColorControl(param);
                case GL11.GL_LIGHT_MODEL_LOCAL_VIEWER -> lightModel.setLocalViewer(param);
                case GL11.GL_LIGHT_MODEL_TWO_SIDE -> lightModel.setTwoSide(param);
                default -> {}
            }
            lightingGeneration++;
        }
    }

    public static void glColorMaterial(int face, int mode) {
        final RecordMode recordMode = DisplayListManager.getRecordMode();
        if (recordMode != RecordMode.NONE) {
            DisplayListManager.recordColorMaterial(face, mode);
            if (recordMode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();
        if (BYPASS_CACHE || !caching || colorMaterialFace.getValue() != face || colorMaterialParameter.getValue() != mode) {
            if (caching) {
                colorMaterialFace.setValue(face);
                colorMaterialParameter.setValue(mode);
                lightingGeneration++;
            }
        }
    }

    public static void glDepthRange(double near, double far) {
        if (isCachingEnabled()) viewportState.setDepthRange(near, far);
        GL11.glDepthRange(near, far);
    }

    public static void glUseProgram(int program) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordUseProgram(program);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }

        final ShaderManager ffp = ShaderManager.getInstance();
        if (program == 0 && ffp.isEnabled()) {
            final boolean caching = isCachingEnabled();
            if (caching) {
                activeProgram = 0; // Track that FFP was requested
            }
            ffp.activate();
            return;
        }

        // Non-zero program or FFP emulation not enabled
        if (ffp.isActive()) {
            ffp.deactivate();
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
            CompatUniformManager.onUseProgram(program);
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
        width = Math.max(lineWidthMin, Math.min(lineWidthMax, width));
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordLineWidth(width);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();
        if (BYPASS_CACHE || !caching || lineState.getWidth() != width) {
            if (caching) {
                lineState.setWidth(width);
            }
            GL11.glLineWidth(width);
        }
    }

    public static void glShaderSource(int shader, CharSequence source) {
        String src = source.toString();
        if (ShaderManager.getInstance().isEnabled()) {
            src = CompatShaderTransformer.transform(src, isFragmentShader(shader));
        }
        GL20.glShaderSource(shader, src);
    }

    public static void glShaderSource(int shader, ByteBuffer source) {
        final byte[] bytes = new byte[source.remaining()];
        final int pos = source.position();
        source.get(bytes);
        source.position(pos);
        glShaderSource(shader, new String(bytes, StandardCharsets.UTF_8));
    }

    public static void glShaderSource(int shader, CharSequence[] sources) {
        if (ShaderManager.getInstance().isEnabled()) {
            int totalLen = 0;
            for (CharSequence s : sources) totalLen += s.length();
            final StringBuilder sb = new StringBuilder(totalLen);
            for (CharSequence s : sources) sb.append(s);
            GL20.glShaderSource(shader, CompatShaderTransformer.transform(sb.toString(), isFragmentShader(shader)));
            return;
        }
        GL20.glShaderSource(shader, sources);
    }

    /** Check shader type to determine if fragment output transformation is needed. */
    private static boolean isFragmentShader(int shader) {
        return GL20.glGetShaderi(shader, GL20.GL_SHADER_TYPE) == GL20.GL_FRAGMENT_SHADER;
    }

    // Texture commands
    public static void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, ByteBuffer pixels) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordComplexCommand(TexSubImage2DCmd.fromByteBuffer(target, level, xoffset, yoffset, width, height, format, type, pixels));
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        if (shouldUseDSA(target)) {
            // Use DSA to upload directly to the texture - keeps GL binding state unchanged
            RenderSystem.textureSubImage2D(getBoundTextureForServerState(), target, level, xoffset, yoffset, width, height, format, type, pixels != null ? MemoryUtilities.memAddress(pixels) : 0L);
        } else {
            // Non-main thread or proxy texture - use direct GL call
            GL11.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels);
        }
    }

    public static void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, IntBuffer pixels) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordComplexCommand(TexSubImage2DCmd.fromIntBuffer(target, level, xoffset, yoffset, width, height, format, type, pixels));
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        if (shouldUseDSA(target)) {
            // Use DSA to upload directly to the texture
            RenderSystem.textureSubImage2D(getBoundTextureForServerState(), target, level, xoffset, yoffset, width, height, format, type, pixels != null ? MemoryUtilities.memAddress(pixels) : 0L);
        } else {
            // Non-main thread or proxy texture - use direct GL call
            GL11.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels);
        }
    }

    public static void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, long pixels_buffer_offset) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glTexSubImage2D with buffer offset in display lists not yet supported");
        }
        if (shouldUseDSA(target)) {
            // Use DSA to upload directly to the texture
            RenderSystem.textureSubImage2D(getBoundTextureForServerState(), target, level, xoffset, yoffset, width, height, format, type, pixels_buffer_offset);
        } else {
            // Non-main thread or proxy texture - use direct GL call
            GL11.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels_buffer_offset);
        }
    }

    public static void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, ShortBuffer pixels) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordComplexCommand(TexSubImage2DCmd.fromShortBuffer(target, level, xoffset, yoffset, width, height, format, type, pixels));
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        if (shouldUseDSA(target)) {
            // Use DSA to upload directly to the texture
            RenderSystem.textureSubImage2D(getBoundTextureForServerState(), target, level, xoffset, yoffset, width, height, format, type, pixels != null ? MemoryUtilities.memAddress(pixels) : 0L);
        } else {
            // Non-main thread or proxy texture - use direct GL call
            GL11.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels);
        }
    }

    public static void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, FloatBuffer pixels) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordComplexCommand(TexSubImage2DCmd.fromFloatBuffer(target, level, xoffset, yoffset, width, height, format, type, pixels));
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        if (shouldUseDSA(target)) {
            // Use DSA to upload directly to the texture
            RenderSystem.textureSubImage2D(getBoundTextureForServerState(), target, level, xoffset, yoffset, width, height, format, type, pixels != null ? MemoryUtilities.memAddress(pixels) : 0L);
        } else {
            // Non-main thread or proxy texture - use direct GL call
            GL11.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels);
        }
    }

    public static void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, DoubleBuffer pixels) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordComplexCommand(TexSubImage2DCmd.fromDoubleBuffer(target, level, xoffset, yoffset, width, height, format, type, pixels));
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        if (shouldUseDSA(target)) {
            // Use DSA to upload directly to the texture
            RenderSystem.textureSubImage2D(getBoundTextureForServerState(), target, level, xoffset, yoffset, width, height, format, type, pixels != null ? MemoryUtilities.memAddress(pixels) : 0L);
        } else {
            // Non-main thread or proxy texture - use direct GL call
            GL11.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels);
        }
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
        final RecordMode recordMode = DisplayListManager.getRecordMode();
        if (recordMode != RecordMode.NONE) {
            DisplayListManager.recordCullFace(mode);
            if (recordMode == RecordMode.COMPILE) {
                return;
            }
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
        final RecordMode recordMode = DisplayListManager.getRecordMode();
        if (recordMode != RecordMode.NONE) {
            DisplayListManager.recordFrontFace(mode);
            if (recordMode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();
        if (BYPASS_CACHE || !caching || polygonState.getFrontFace() != mode) {
            if (caching) {
                polygonState.setFrontFace(mode);
            }
            GL11.glFrontFace(mode);
        }
    }

    public static void glHint(int target, int hint) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordHint(target, hint);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        GL11.glHint(target, hint);
    }

    public static void glLineStipple(int factor, short pattern) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordLineStipple(factor, pattern);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        lineState.setStippleFactor(factor);
        lineState.setStipplePattern(pattern);
    }

    public static void glPointSize(float size) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordPointSize(size);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();
        if (BYPASS_CACHE || !caching || pointState.getSize() != size) {
            if (caching) {
                pointState.setSize(size);
            }
            GL11.glPointSize(size);
        }
    }

    public static void glPolygonMode(int face, int polygonMode) {
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
            needsUpdate = BYPASS_CACHE || !caching || polygonState.getFrontMode() != polygonMode;
            if (caching && needsUpdate) polygonState.setFrontMode(polygonMode);
        } else if (face == GL11.GL_BACK) {
            needsUpdate = BYPASS_CACHE || !caching || polygonState.getBackMode() != polygonMode;
            if (caching && needsUpdate) polygonState.setBackMode(polygonMode);
        } else { // GL_FRONT_AND_BACK
            needsUpdate = BYPASS_CACHE || !caching || polygonState.getFrontMode() != polygonMode || polygonState.getBackMode() != polygonMode;
            if (caching && needsUpdate) {
                polygonState.setFrontMode(polygonMode);
                polygonState.setBackMode(polygonMode);
            }
        }
        if (needsUpdate) {
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, polygonMode);
        }
    }

    public static void glPolygonOffset(float factor, float units) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordPolygonOffset(factor, units);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();
        if (BYPASS_CACHE || !caching || polygonState.getOffsetFactor() != factor || polygonState.getOffsetUnits() != units) {
            if (caching) {
                polygonState.setOffsetFactor(factor);
                polygonState.setOffsetUnits(units);
            }
            GL11.glPolygonOffset(factor, units);
        }
    }

    public static void glReadBuffer(int mode) {
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glReadBuffer in display lists not yet implemented - if you see this, please report!");
        }
        GL11.glReadBuffer(mode);
    }

    public static void glScissor(int x, int y, int width, int height) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordScissor(x, y, width, height);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        GL11.glScissor(x, y, width, height);
    }

    public static void glStencilFunc(int func, int ref, int mask) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordStencilFunc(func, ref, mask);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final int clampedMask = mask & stencilBitMask;
        final boolean caching = isCachingEnabled();
        if (BYPASS_CACHE || !caching || stencilState.getFuncFront() != func || stencilState.getRefFront() != ref || stencilState.getValueMaskFront() != clampedMask) {
            if (caching) {
                stencilState.setFunc(func, ref, clampedMask);
            }
            GL11.glStencilFunc(func, ref, clampedMask);
        }
    }

    public static void glStencilMask(int mask) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordStencilMask(mask);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final int clampedMask = mask & stencilBitMask;
        final boolean caching = isCachingEnabled();
        if (BYPASS_CACHE || !caching || stencilState.getWriteMaskFront() != clampedMask) {
            if (caching) {
                stencilState.setWriteMask(clampedMask);
            }
            GL11.glStencilMask(clampedMask);
        }
    }

    public static void glStencilOp(int fail, int zfail, int zpass) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordStencilOp(fail, zfail, zpass);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();
        if (BYPASS_CACHE || !caching || stencilState.getFailOpFront() != fail || stencilState.getZFailOpFront() != zfail || stencilState.getZPassOpFront() != zpass) {
            if (caching) {
                stencilState.setOp(fail, zfail, zpass);
            }
            GL11.glStencilOp(fail, zfail, zpass);
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
        while (lists.hasRemaining()) {
            final int listId = lists.get() + listBase;
            glCallList(listId);
        }
    }

    public static void glCallLists(ShortBuffer lists) {
        while (lists.hasRemaining()) {
            final int listId = (lists.get() & 0xFFFF) + listBase;
            glCallList(listId);
        }
    }

    public static void glCallLists(ByteBuffer lists) {
        while (lists.hasRemaining()) {
            final int listId = (lists.get() & 0xFF) + listBase;
            glCallList(listId);
        }
    }

    public static void glListBase(int base) {
        listBase = base;
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
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordClearStencil(s);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();
        if (BYPASS_CACHE || !caching || stencilState.getClearValue() != s) {
            if (caching) {
                stencilState.setClearValue(s);
            }
            GL11.glClearStencil(s);
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
        GL20.glDrawBuffers(buffer);
    }

    public static void glDrawBuffers(IntBuffer bufs) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDrawBuffers(bufs.remaining(), bufs);
            if (mode == RecordMode.COMPILE) {
                return;
            }
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
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordStencilFuncSeparate(face, func, ref, mask);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final int clampedMask = mask & stencilBitMask;
        final boolean caching = isCachingEnabled();
        boolean needsUpdate = BYPASS_CACHE || !caching;
        if (!needsUpdate) {
            if (face == GL11.GL_FRONT || face == GL11.GL_FRONT_AND_BACK) {
                needsUpdate = stencilState.getFuncFront() != func || stencilState.getRefFront() != ref || stencilState.getValueMaskFront() != clampedMask;
            }
            if (!needsUpdate && (face == GL11.GL_BACK || face == GL11.GL_FRONT_AND_BACK)) {
                needsUpdate = stencilState.getFuncBack() != func || stencilState.getRefBack() != ref || stencilState.getValueMaskBack() != clampedMask;
            }
        }
        if (needsUpdate) {
            if (caching) {
                if (face == GL11.GL_FRONT || face == GL11.GL_FRONT_AND_BACK) {
                    stencilState.setFuncFront(func);
                    stencilState.setRefFront(ref);
                    stencilState.setValueMaskFront(clampedMask);
                }
                if (face == GL11.GL_BACK || face == GL11.GL_FRONT_AND_BACK) {
                    stencilState.setFuncBack(func);
                    stencilState.setRefBack(ref);
                    stencilState.setValueMaskBack(clampedMask);
                }
            }
            GL20.glStencilFuncSeparate(face, func, ref, clampedMask);
        }
    }

    public static void glStencilMaskSeparate(int face, int mask) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordStencilMaskSeparate(face, mask);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final int clampedMask = mask & stencilBitMask;
        final boolean caching = isCachingEnabled();
        boolean needsUpdate = BYPASS_CACHE || !caching;
        if (!needsUpdate) {
            if (face == GL11.GL_FRONT || face == GL11.GL_FRONT_AND_BACK) {
                needsUpdate = stencilState.getWriteMaskFront() != clampedMask;
            }
            if (!needsUpdate && (face == GL11.GL_BACK || face == GL11.GL_FRONT_AND_BACK)) {
                needsUpdate = stencilState.getWriteMaskBack() != clampedMask;
            }
        }
        if (needsUpdate) {
            if (caching) {
                if (face == GL11.GL_FRONT || face == GL11.GL_FRONT_AND_BACK) {
                    stencilState.setWriteMaskFront(clampedMask);
                }
                if (face == GL11.GL_BACK || face == GL11.GL_FRONT_AND_BACK) {
                    stencilState.setWriteMaskBack(clampedMask);
                }
            }
            GL20.glStencilMaskSeparate(face, clampedMask);
        }
    }

    public static void glStencilOpSeparate(int face, int sfail, int dpfail, int dppass) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordStencilOpSeparate(face, sfail, dpfail, dppass);
            if (mode == RecordMode.COMPILE) {
                return;
            }
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
            GL20.glStencilOpSeparate(face, sfail, dpfail, dppass);
        }
    }

    public static void glBindBuffer(int target, int buffer) {
        if (target == GL15.GL_ARRAY_BUFFER) {
            // if (boundVBO == buffer) return; TODO figure out why this breaks switching async occlusion mode
            boundVBO = buffer;
        } else if (target == GL15.GL_ELEMENT_ARRAY_BUFFER) {
            boundEBO = buffer;
        }
        GL15.glBindBuffer(target, buffer);
    }

    public static void glBindVertexArray(int array) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordBindVAO(array);
            // Since the vao needs to be bound to do stuff like state setup & data upload, it'll still execute the bind call.
            // This is technically wrong, but I'm not sure if there's a better solution here.
        }
        if (array == 0) {
            array = defaultVAO;
        }
        if (boundVAO != array) {
            boundVAO = array;
            UniversalVAO.bindVertexArray(array);
            if (ShaderManager.getInstance().isEnabled()) {
                ShaderManager.getInstance().onBindVertexArray(array);
            }
        }
    }

    public static void glDeleteVertexArrays(int array) {
        ShaderManager.getInstance().onDeleteVertexArray(array);
        if (array == boundVAO) {
            // Deleting the bound VAO implicitly unbinds it. Rebind the default VAO.
            boundVAO = defaultVAO;
            UniversalVAO.bindVertexArray(defaultVAO);
        }
        UniversalVAO.deleteVertexArrays(array);
    }

    public static void glBindVertexArrayAPPLE(int array) {
        glBindVertexArray(array);
    }

    public static boolean isVBOBound() {
        return boundVBO != 0;
    }

    public static boolean isVAOBound() {
        return boundVAO != 0;
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

    @Getter protected static int drawFramebuffer = 0;
    @Getter protected static int readFramebuffer = 0;

    public static void glBindFramebuffer(int target, int framebuffer) {
        if (target == GL30.GL_FRAMEBUFFER) {
            if (drawFramebuffer == framebuffer && readFramebuffer == framebuffer) return;
            drawFramebuffer = framebuffer;
            readFramebuffer = framebuffer;
        } else if (target == GL30.GL_DRAW_FRAMEBUFFER) {
            if (drawFramebuffer == framebuffer) return;
            drawFramebuffer = framebuffer;
        } else if (target == GL30.GL_READ_FRAMEBUFFER) {
            if (readFramebuffer == framebuffer) return;
            readFramebuffer = framebuffer;
        }
        GL30.glBindFramebuffer(target, framebuffer);
    }

    public static void glDeleteFramebuffers(int framebuffer) {
        if (drawFramebuffer == framebuffer) drawFramebuffer = 0;
        if (readFramebuffer == framebuffer) readFramebuffer = 0;
        GL30.glDeleteFramebuffers(framebuffer);
    }

    public static int glGenFramebuffers() {
        return GL30.glGenFramebuffers();
    }

    public static int glCheckFramebufferStatus(int target) {
        return GL30.glCheckFramebufferStatus(target);
    }

    public static void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        GL30.glFramebufferTexture2D(target, attachment, textarget, texture, level);
    }

    public static void setActiveTexture(int textureUnit) {
        glActiveTexture(textureUnit);
    }

    public static void glMultiTexCoord2f(int target, float s, float t) {
        if (target == GL13.GL_TEXTURE0) {
            ShaderManager.setCurrentTexCoord(s, t, 0.0f, 1.0f);
            dirtyTexCoordAttrib = true;
        } else if (target == GL13.GL_TEXTURE1) {
            setLightmapTextureCoords(target, s, t);
        }
    }

    public static void glMultiTexCoord2s(int target, short s, short t) {
        glMultiTexCoord2f(target, s, t);
    }

    public static void setLightmapTextureCoords(int unit, float x, float y) {
        if (unit == GL13.GL_TEXTURE1) {
            OpenGlHelper.lastBrightnessX = x;
            OpenGlHelper.lastBrightnessY = y;
            dirtyLightmapAttrib = true;
        }
    }

    private static void checkMismatch(int cap) {
        if (GLStateManager.glGetInteger(cap) != GL11.glGetInteger(cap)) {
            throw new IllegalStateException("GLSM Mismatch! Cached: " + GLStateManager.glGetInteger(cap) + ", actual: " + GL11.glGetInteger(cap));
        }
    }

    public static boolean isFramebufferEnabled() {
        return OpenGlHelper.framebufferSupported && Minecraft.getMinecraft().gameSettings.fboEnable;
    }

    private static final boolean FFP_WARN_ON_UNSUPPORTED = Boolean.parseBoolean(
        System.getProperty("angelica.ffp.warnOnUnsupported", "false"));

    private static final Set<String> warnedFFPFunctions = new HashSet<>();

    /**
     * Guards against FFP features not supported in core profile.
     * Default: throws UnsupportedOperationException.
     * With {@code -Dangelica.ffp.warnOnUnsupported=true}: warns once per function (diagnostic mode).
     * @return true (always — caller should skip the GL call)
     */
    private static boolean guardUnsupportedFFP(String function, String explanation) {
        if (!FFP_WARN_ON_UNSUPPORTED) {
            throw new UnsupportedOperationException(function + ": " + explanation);
        }
        if (warnedFFPFunctions.add(function)) {
            LOGGER.warn("{}: {}", function, explanation, new Throwable("Stack trace"));
        }
        return true;
    }

    /**
     * In core profile, GL_CLAMP (0x2900) is removed. Remap to GL_CLAMP_TO_EDGE for wrap modes.
     * Semantically equivalent when texture border is not used (border textures are also removed in core).
     */
    public static int remapTexClamp(int pname, int param) {
        if (param == GL11.GL_CLAMP && (pname == GL11.GL_TEXTURE_WRAP_S || pname == GL11.GL_TEXTURE_WRAP_T || pname == GL12.GL_TEXTURE_WRAP_R)) {
            return GL12.GL_CLAMP_TO_EDGE;
        }
        return param;
    }

    public static float remapTexClamp(int pname, float param) {
        if ((int) param == GL11.GL_CLAMP
            && (pname == GL11.GL_TEXTURE_WRAP_S || pname == GL11.GL_TEXTURE_WRAP_T || pname == GL12.GL_TEXTURE_WRAP_R)) {
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

    private static final String TEXENV_COMBINE_MSG = "Texture environment combine mode is not supported in FFP emulation.";

    public static void glTexEnvi(int target, int pname, int param) {
        handleTexEnvScalar(target, pname, param);
    }

    public static void glTexEnvf(int target, int pname, float param) {
        handleTexEnvScalar(target, pname, param);
    }

    public static void glTexEnv(int target, int pname, FloatBuffer params) {
        if (target == GL11.GL_TEXTURE_ENV && pname == GL11.GL_TEXTURE_ENV_COLOR && params.remaining() >= 4) {
            final int pos = params.position();
            texEnvColor.setRed(params.get(pos));
            texEnvColor.setGreen(params.get(pos + 1));
            texEnvColor.setBlue(params.get(pos + 2));
            texEnvColor.setAlpha(params.get(pos + 3));
            fragmentGeneration++;
            return;
        }
        guardTexEnvTarget(target, "glTexEnv");
    }

    public static void glTexEnv(int target, int pname, IntBuffer params) {
        guardTexEnvTarget(target, "glTexEnv");
    }

    /**
     * Scalar glTexEnvi/glTexEnvf dispatch. glTexEnv is removed in core profile.
     * GL_TEXTURE_FILTER_CONTROL (LOD bias) is remapped to glTexParameterf.
     * GL_TEXTURE_ENV_MODE values (MODULATE, REPLACE, ADD, DECAL, BLEND) are tracked in cache.
     */
    private static void handleTexEnvScalar(int target, int pname, float param) {
        if (target == GL14.GL_TEXTURE_FILTER_CONTROL) {
            // LOD bias via legacy glTexEnv path — remap to core-profile glTexParameterf
            glTexParameterf(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_LOD_BIAS, param);
            return;
        }
        if (target == GL11.GL_TEXTURE_ENV && pname == GL11.GL_TEXTURE_ENV_MODE) {
            final int mode = (int) param;
            if (mode == GL11.GL_MODULATE || mode == GL11.GL_REPLACE
                || mode == GL11.GL_ADD || mode == GL11.GL_DECAL || mode == GL11.GL_BLEND) {
                texEnvMode.setValue(mode);
                fragmentGeneration++;
                return;
            }
        }
        guardUnsupportedFFP("glTexEnv", TEXENV_COMBINE_MSG);
    }

    /**
     * Guard for buffer glTexEnv variants. glTexEnv is removed in core profile.
     */
    private static void guardTexEnvTarget(int target, String function) {
        if (target == GL14.GL_TEXTURE_FILTER_CONTROL) {
            guardUnsupportedFFP(function, "LOD bias via buffer glTexEnv is not supported; use glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_LOD_BIAS, value) instead.");
            return;
        }
        guardUnsupportedFFP(function, TEXENV_COMBINE_MSG);
    }

    // TexGen generation counter for FFP uniform dirty tracking
    public static int texGenGeneration;

    private static final float[] texGenTempPlane = new float[4];

    public static void glTexGeni(int coord, int pname, int param) {
        if (!isCachingEnabled()) return;
        if (pname == GL11.GL_TEXTURE_GEN_MODE) {
            final int unit = activeTextureUnit.getValue();
            textures.getTexGenState(unit).setMode(coord, param);
            texGenGeneration++;
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
        if (!isCachingEnabled()) return;
        final int unit = activeTextureUnit.getValue();
        final var texGenState = textures.getTexGenState(unit);
        final int pos = params.position();
        switch (pname) {
            case GL11.GL_TEXTURE_GEN_MODE -> {
                texGenState.setMode(coord, (int) params.get(pos));
                texGenGeneration++;
            }
            case GL11.GL_OBJECT_PLANE -> {
                texGenTempPlane[0] = params.get(pos);
                texGenTempPlane[1] = params.get(pos + 1);
                texGenTempPlane[2] = params.get(pos + 2);
                texGenTempPlane[3] = params.get(pos + 3);
                texGenState.setObjectPlane(coord, texGenTempPlane);
                texGenGeneration++;
            }
            case GL11.GL_EYE_PLANE -> {
                texGenTempPlane[0] = params.get(pos);
                texGenTempPlane[1] = params.get(pos + 1);
                texGenTempPlane[2] = params.get(pos + 2);
                texGenTempPlane[3] = params.get(pos + 3);
                texGenState.setEyePlane(coord, texGenTempPlane, modelViewMatrix);
                texGenGeneration++;
            }
        }
    }

    public static void glTexGen(int coord, int pname, DoubleBuffer params) {
        if (!isCachingEnabled()) return;
        final int pos = params.position();
        texGenTempPlane[0] = (float) params.get(pos);
        texGenTempPlane[1] = (float) params.get(pos + 1);
        texGenTempPlane[2] = (float) params.get(pos + 2);
        texGenTempPlane[3] = (float) params.get(pos + 3);
        final int unit = activeTextureUnit.getValue();
        final var texGenState = textures.getTexGenState(unit);
        switch (pname) {
            case GL11.GL_TEXTURE_GEN_MODE -> {
                texGenState.setMode(coord, (int) params.get(pos));
                texGenGeneration++;
            }
            case GL11.GL_OBJECT_PLANE -> {
                texGenState.setObjectPlane(coord, texGenTempPlane);
                texGenGeneration++;
            }
            case GL11.GL_EYE_PLANE -> {
                texGenState.setEyePlane(coord, texGenTempPlane, modelViewMatrix);
                texGenGeneration++;
            }
        }
    }

    public static void glTexGen(int coord, int pname, IntBuffer params) {
        if (!isCachingEnabled()) return;
        if (pname == GL11.GL_TEXTURE_GEN_MODE) {
            final int unit = activeTextureUnit.getValue();
            textures.getTexGenState(unit).setMode(coord, params.get(params.position()));
            texGenGeneration++;
        }
    }

    public static void glPolygonStipple(ByteBuffer pattern) {
        guardUnsupportedFFP("glPolygonStipple", "glPolygonStipple is not available in GL 3.3 core profile.");
    }

    public static void glAccum(int op, float value) {
        guardUnsupportedFFP("glAccum", "The accumulation buffer is not available in GL 3.3 core profile.");
    }

    public static void glLinkProgram(int program) {
        GL20.glLinkProgram(program);
        CompatUniformManager.onLinkProgram(program);
    }

    public static void glDeleteProgram(int program) {
        CompatUniformManager.onDeleteProgram(program);
        GL20.glDeleteProgram(program);
    }

    public static int glCreateShader(int type) {
        return GL20.glCreateShader(type);
    }

    public static void glCompileShader(int shader) {
        GL20.glCompileShader(shader);
    }

    public static int glCreateProgram() {
        return GL20.glCreateProgram();
    }

    public static void glAttachShader(int program, int shader) {
        GL20.glAttachShader(program, shader);
    }

    public static void glDetachShader(int program, int shader) {
        GL20.glDetachShader(program, shader);
    }

    public static void glValidateProgram(int program) {
        GL20.glValidateProgram(program);
    }

    public static int glGetUniformLocation(int program, CharSequence name) {
        int loc = GL20.glGetUniformLocation(program, name);
        if (loc == -1 && TEXTURE.contentEquals(name)) {
            loc = GL20.glGetUniformLocation(program, TEXTURE_RENAMED);
        }
        return loc;
    }

    public static int glGetUniformLocation(int program, ByteBuffer name) {
        int loc = GL20.glGetUniformLocation(program, name);
        if (loc == -1 && isTextureBuffer(name)) {
            loc = GL20.glGetUniformLocation(program, TEXTURE_RENAMED);
        }
        return loc;
    }

    public static int glGetAttribLocation(int program, CharSequence name) {
        int loc = GL20.glGetAttribLocation(program, name);
        if (loc == -1 && TEXTURE.contentEquals(name)) {
            loc = GL20.glGetAttribLocation(program, TEXTURE_RENAMED);
        }
        return loc;
    }

    public static int glGetAttribLocation(int program, ByteBuffer name) {
        int loc = GL20.glGetAttribLocation(program, name);
        if (loc == -1 && isTextureBuffer(name)) {
            loc = GL20.glGetAttribLocation(program, TEXTURE_RENAMED);
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
        if (GL20.glIsShader(obj)) {
            GL20.glDeleteShader(obj);
        } else {
            glDeleteProgram(obj);
        }
    }

    public static int glGetHandleARB(int pname) {
        return pname == ARBShaderObjects.GL_PROGRAM_OBJECT_ARB ? glGetInteger(GL20.GL_CURRENT_PROGRAM) : GL11.glGetInteger(pname);
    }

    public static void glGetObjectParameterARB(int obj, int pname, IntBuffer params) {
        if (GL20.glIsShader(obj)) {
            GL20.glGetShader(obj, pname, params);
        } else {
            GL20.glGetProgram(obj, pname, params);
        }
    }

    public static void glGetObjectParameterARB(int obj, int pname, FloatBuffer params) {
        params.put(params.position(), (float) (GL20.glIsShader(obj) ? GL20.glGetShaderi(obj, pname) : GL20.glGetProgrami(obj, pname)));
    }

    public static int glGetObjectParameteriARB(int obj, int pname) {
        return GL20.glIsShader(obj) ? GL20.glGetShaderi(obj, pname) : GL20.glGetProgrami(obj, pname);
    }

    public static String glGetInfoLogARB(int obj, int maxLength) {
        return GL20.glIsShader(obj) ? GL20.glGetShaderInfoLog(obj, maxLength) : GL20.glGetProgramInfoLog(obj, maxLength);
    }

    public static void glGetInfoLogARB(int obj, IntBuffer length, ByteBuffer infoLog) {
        if (GL20.glIsShader(obj)) {
            GL20.glGetShaderInfoLog(obj, length, infoLog);
        } else {
            GL20.glGetProgramInfoLog(obj, length, infoLog);
        }
    }

    public static void glUniform1f(int location, float v0) {
        GL20.glUniform1f(location, v0);
    }

    public static void glUniform2f(int location, float v0, float v1) {
        GL20.glUniform2f(location, v0, v1);
    }

    public static void glUniform3f(int location, float v0, float v1, float v2) {
        GL20.glUniform3f(location, v0, v1, v2);
    }

    public static void glUniform4f(int location, float v0, float v1, float v2, float v3) {
        GL20.glUniform4f(location, v0, v1, v2, v3);
    }

    public static void glUniform1i(int location, int v0) {
        GL20.glUniform1i(location, v0);
    }

    public static void glUniform2i(int location, int v0, int v1) {
        GL20.glUniform2i(location, v0, v1);
    }

    public static void glUniform3i(int location, int v0, int v1, int v2) {
        GL20.glUniform3i(location, v0, v1, v2);
    }

    public static void glUniform4i(int location, int v0, int v1, int v2, int v3) {
        GL20.glUniform4i(location, v0, v1, v2, v3);
    }

    public static void glUniform1(int location, FloatBuffer values) {
        GL20.glUniform1(location, values);
    }

    public static void glUniform1(int location, IntBuffer values) {
        GL20.glUniform1(location, values);
    }

    public static void glUniform2(int location, FloatBuffer values) {
        GL20.glUniform2(location, values);
    }

    public static void glUniform2(int location, IntBuffer values) {
        GL20.glUniform2(location, values);
    }

    public static void glUniform3(int location, FloatBuffer values) {
        GL20.glUniform3(location, values);
    }

    public static void glUniform3(int location, IntBuffer values) {
        GL20.glUniform3(location, values);
    }

    public static void glUniform4(int location, FloatBuffer values) {
        GL20.glUniform4(location, values);
    }

    public static void glUniform4(int location, IntBuffer values) {
        GL20.glUniform4(location, values);
    }

    public static void glUniformMatrix2(int location, boolean transpose, FloatBuffer matrices) {
        GL20.glUniformMatrix2(location, transpose, matrices);
    }

    public static void glUniformMatrix3(int location, boolean transpose, FloatBuffer matrices) {
        GL20.glUniformMatrix3(location, transpose, matrices);
    }

    public static void glUniformMatrix4(int location, boolean transpose, FloatBuffer matrices) {
        GL20.glUniformMatrix4(location, transpose, matrices);
    }

    public static void glGetActiveUniform(int program, int index, IntBuffer length, IntBuffer size, IntBuffer type, ByteBuffer name) {
        GL20.glGetActiveUniform(program, index, length, size, type, name);
    }

    public static void glGetAttachedShaders(int program, IntBuffer count, IntBuffer shaders) {
        GL20.glGetAttachedShaders(program, count, shaders);
    }

    public static String glGetShaderSource(int shader, int maxLength) {
        return GL20.glGetShaderSource(shader, maxLength);
    }

    public static void glGetShaderSource(int shader, IntBuffer length, ByteBuffer source) {
        GL20.glGetShaderSource(shader, length, source);
    }

    public static void glGetUniform(int program, int location, FloatBuffer params) {
        GL20.glGetUniform(program, location, params);
    }

    public static void glGetUniform(int program, int location, IntBuffer params) {
        GL20.glGetUniform(program, location, params);
    }

}
