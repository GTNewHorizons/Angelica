package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizon.gtnhlib.client.renderer.stacks.IStateStack;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.VBOManager;
import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.managers.GLAttribManager;
import com.gtnewhorizons.angelica.glsm.managers.GLFogManager;
import com.gtnewhorizons.angelica.glsm.managers.GLLightingManager;
import com.gtnewhorizons.angelica.glsm.managers.GLMatrixManager;
import com.gtnewhorizons.angelica.glsm.managers.GLShaderManager;
import com.gtnewhorizons.angelica.glsm.managers.GLTextureManager;
import com.gtnewhorizons.angelica.glsm.stacks.BooleanStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.ScissorStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.ViewPortStateStack;
import com.gtnewhorizons.angelica.glsm.states.ISettableState;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import lombok.Getter;
import lombok.Setter;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gbuffer_overrides.state.StateTracker;
import net.coderbot.iris.gl.blending.AlphaTestStorage;
import net.coderbot.iris.gl.blending.BlendModeStorage;
import net.coderbot.iris.gl.state.StateUpdateNotifiers;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.samplers.IrisSamplers;
import org.lwjgl.opengl.Drawable;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjglx.LWJGLException;

import java.io.Serial;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.gtnewhorizons.angelica.loading.AngelicaTweaker.LOGGER;
import static org.lwjgl.opengl.ARBImaging.GL_BLEND_COLOR;

@SuppressWarnings("unused") // Used in ASM
public class GLStateManager {
    public static GLCapabilities capabilities;


    public static boolean BYPASS_CACHE = Boolean.parseBoolean(System.getProperty("angelica.disableGlCache", "false"));

    public static final GLFeatureSet HAS_MULTIPLE_SET = new GLFeatureSet();

    @Getter protected static boolean NVIDIA;
    @Getter protected static boolean AMD;
    @Getter protected static boolean INTEL;
    @Getter protected static boolean MESA;

    // NOTE: These are set as part of static initialization and require a valid active OpenGL context
    @Getter protected static final BooleanStateStack scissorTest = new BooleanStateStack(GL11.GL_SCISSOR_TEST);
    @Getter protected static final ViewPortStateStack viewportState = new ViewPortStateStack();
    @Getter protected static final ScissorStateStack scissorState = new ScissorStateStack();

    public static void reset() {
        runningSplash = true;
        while(!GLAttribManager.attribs.isEmpty()) {
            GLAttribManager.attribs.popInt();
        }

        final List<IStateStack<?>> stacks = Feature.maskToFeatures(GL11.GL_ALL_ATTRIB_BITS);
        final int size = stacks.size();

        for(int i = 0; i < size; i++) {
            final IStateStack<?> stack = stacks.get(i);

            while(!stack.isEmpty()) {
                stack.pop();
            }
        }

        GLTextureManager.reset();
        GLMatrixManager.reset();
        GLLightingManager.reset();


    }

    // Thread Checking
    @Getter private static final Thread MainThread = Thread.currentThread();
    private static Thread CurrentThread = MainThread;
    @Setter @Getter private static boolean runningSplash = true;

    private static int glListMode = 0;
    private static int glListNesting = 0;
    private static int glListId = -1;
    private static final Map<IStateStack<?>, ISettableState<?>> glListStates = new Object2ObjectArrayMap<>();
    private static final Int2ObjectMap<Set<Map.Entry<IStateStack<?>, ISettableState<?>>>> glListChanges = new Int2ObjectOpenHashMap<>();

    // ALPHA
    public static void enableAlphaTest() {
        if (AngelicaConfig.enableIris) {
            if (AlphaTestStorage.isAlphaTestLocked()) {
                AlphaTestStorage.deferAlphaTestToggle(true);
                return;
            }
        }
        GLLightingManager.alphaTest.enable();
    }

    public static void disableAlphaTest() {
        if (AngelicaConfig.enableIris) {
            if (AlphaTestStorage.isAlphaTestLocked()) {
                AlphaTestStorage.deferAlphaTestToggle(false);
                return;
            }
        }
        GLLightingManager.alphaTest.disable();
    }

    public static void enableCullFace() {
        GLLightingManager.cullState.enable();
    }

    public static void disableCullFace() {
        GLLightingManager.cullState.disable();
    }

    public static void enableDepthTest() {
        GLLightingManager.depthTest.enable();
    }

    public static void disableDepthTest() {
        GLLightingManager.depthTest.disable();
    }

    public static void enableLighting() {
        GLLightingManager.lightingState.enable();
    }

    public static void enableLight(int light) {
        GLLightingManager.lightStates[light].enable();
    }

    public static void enableColorMaterial() {
        GLLightingManager.colorMaterial.enable();
        final float r = GLLightingManager.color.getRed();
        final float g = GLLightingManager.color.getGreen();
        final float b = GLLightingManager.color.getBlue();
        final float a = GLLightingManager.color.getAlpha();
        if (GLLightingManager.colorMaterialFace.getValue() == GL11.GL_FRONT || GLLightingManager.colorMaterialFace.getValue() == GL11.GL_FRONT_AND_BACK) {
            switch (GLLightingManager.colorMaterialParameter.getValue()) {
                case GL11.GL_AMBIENT_AND_DIFFUSE -> {
                    GLLightingManager.frontMaterial.ambient.set(r, g, b, a);
                    GLLightingManager.frontMaterial.diffuse.set(r, g, b, a);
                }
                case GL11.GL_AMBIENT -> GLLightingManager.frontMaterial.ambient.set(r, g, b, a);
                case GL11.GL_DIFFUSE -> GLLightingManager.frontMaterial.diffuse.set(r, g, b, a);
                case GL11.GL_SPECULAR -> GLLightingManager.frontMaterial.specular.set(r, g, b, a);
                case GL11.GL_EMISSION -> GLLightingManager.frontMaterial.emission.set(r, g, b, a);
            }
        }
        if (GLLightingManager.colorMaterialFace.getValue() == GL11.GL_BACK || GLLightingManager.colorMaterialFace.getValue() == GL11.GL_FRONT_AND_BACK) {
            switch (GLLightingManager.colorMaterialParameter.getValue()) {
                case GL11.GL_AMBIENT_AND_DIFFUSE -> {
                    GLLightingManager.backMaterial.ambient.set(r, g, b, a);
                    GLLightingManager.backMaterial.diffuse.set(r, g, b, a);
                }
                case GL11.GL_AMBIENT -> GLLightingManager.backMaterial.ambient.set(r, g, b, a);
                case GL11.GL_DIFFUSE -> GLLightingManager.backMaterial.diffuse.set(r, g, b, a);
                case GL11.GL_SPECULAR -> GLLightingManager.backMaterial.specular.set(r, g, b, a);
                case GL11.GL_EMISSION -> GLLightingManager.backMaterial.emission.set(r, g, b, a);
            }
        }
    }

    public static void disableColorMaterial() {
        GLLightingManager.colorMaterial.disable();
    }

    public static void disableLighting() {
        GLLightingManager.lightingState.disable();
    }

    public static void disableLight(int light) {
        GLLightingManager.lightStates[light].disable();
    }

    public static void enableBlend() {
        if (AngelicaConfig.enableIris) {
            if (BlendModeStorage.isBlendLocked()) {
                BlendModeStorage.deferBlendModeToggle(true);
                return;
            }
        }
        GLLightingManager.blendMode.enable();
    }

    public static void disableBlend() {
        if (AngelicaConfig.enableIris) {
            if (BlendModeStorage.isBlendLocked()) {
                BlendModeStorage.deferBlendModeToggle(false);
                return;
            }
        }
        GLLightingManager.blendMode.disable();
    }

    public static void enableTexture2D() {
        final int textureUnit = getActiveTextureUnit();
        if (AngelicaConfig.enableIris) {
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
        GLTextureManager.textures.getTextureUnitStates(textureUnit).enable();
    }

    public static void disableTexture2D() {
        final int textureUnit = getActiveTextureUnit();
        if (AngelicaConfig.enableIris) {
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
        GLTextureManager.textures.getTextureUnitStates(textureUnit).disable();
    }

    public static class GLFeatureSet extends IntOpenHashSet {

        @Serial private static final long serialVersionUID = 8558779940775721010L;

        public GLFeatureSet addFeature(int feature) {
            super.add(feature);
            return this;
        }

    }

    public static void preInit() {
        capabilities = GL.getCapabilities();
        HAS_MULTIPLE_SET
            .addFeature(GL11.GL_ACCUM_CLEAR_VALUE)
            .addFeature(GL_BLEND_COLOR)
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

        final String glVendor = GL11.glGetString(GL11.GL_VENDOR).toLowerCase();
        NVIDIA = glVendor.contains("nvidia");
        AMD = glVendor.contains("ati") || glVendor.toLowerCase().contains("amd");
        INTEL = glVendor.contains("intel");
        MESA = glVendor.contains("mesa");

        if(AMD) {
            // AMD Drivers seem to default to 0 for the matrix mode, so we need to set it to the default
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
        }

        RenderSystem.initRenderer();

        if(BYPASS_CACHE) {
            LOGGER.info("GLStateManager cache bypassed");
        }
    }

    public static void minecraftInit() {
        LOGGER.info("Initializing GLStateManager");


        if (AngelicaConfig.enableIris) {
            StateUpdateNotifiers.blendFuncNotifier = listener -> GLLightingManager.blendFuncListener = listener;
        }

        GLFogManager.minecraftInit();

        if(AngelicaMod.lwjglDebug) {
            LOGGER.info("Enabling additional LWJGL debug output");

            GLDebug.setupDebugMessageCallback();
            GLDebug.initDebugState();

            GLDebug.debugMessage("Angelica Debug Annotator Initialized");
        }

    }

    public static boolean isMainThread() {
        return Thread.currentThread() == MainThread;
    }

    public static void assertMainThread() {
        if (Thread.currentThread() != CurrentThread && !runningSplash) {
            LOGGER.info("Call from not the Current Thread! - " + Thread.currentThread().getName() + " Current thread: " + CurrentThread.getName());
        }
    }

    public static boolean shouldBypassCache() {
        return BYPASS_CACHE || runningSplash;
    }

    // LWJGL Overrides
    public static void glEnable(int cap) {
        switch (cap) {
            case GL11.GL_ALPHA_TEST -> enableAlphaTest();
            case GL11.GL_BLEND -> enableBlend();
            case GL11.GL_CULL_FACE -> enableCullFace();
            case GL11.GL_DEPTH_TEST -> enableDepthTest();
            case GL11.GL_FOG -> enableFog();
            case GL11.GL_LIGHTING -> enableLighting();
            case GL11.GL_LIGHT0 -> enableLight(0);
            case GL11.GL_LIGHT1 -> enableLight(1);
            case GL11.GL_LIGHT2 -> enableLight(2);
            case GL11.GL_LIGHT3 -> enableLight(3);
            case GL11.GL_LIGHT4 -> enableLight(4);
            case GL11.GL_LIGHT5 -> enableLight(5);
            case GL11.GL_LIGHT6 -> enableLight(6);
            case GL11.GL_LIGHT7 -> enableLight(7);
            case GL11.GL_COLOR_MATERIAL -> enableColorMaterial();
            case GL11.GL_SCISSOR_TEST -> enableScissorTest();
            case GL11.GL_TEXTURE_2D -> enableTexture2D();
            case GL12.GL_RESCALE_NORMAL -> enableRescaleNormal();
            default -> GL11.glEnable(cap);
        }
    }

    public static void glDisable(int cap) {
        switch (cap) {
            case GL11.GL_ALPHA_TEST -> disableAlphaTest();
            case GL11.GL_BLEND -> disableBlend();
            case GL11.GL_CULL_FACE -> disableCullFace();
            case GL11.GL_DEPTH_TEST -> disableDepthTest();
            case GL11.GL_FOG -> disableFog();
            case GL11.GL_LIGHTING -> disableLighting();
            case GL11.GL_LIGHT0 -> disableLight(0);
            case GL11.GL_LIGHT1 -> disableLight(1);
            case GL11.GL_LIGHT2 -> disableLight(2);
            case GL11.GL_LIGHT3 -> disableLight(3);
            case GL11.GL_LIGHT4 -> disableLight(4);
            case GL11.GL_LIGHT5 -> disableLight(5);
            case GL11.GL_LIGHT6 -> disableLight(6);
            case GL11.GL_LIGHT7 -> disableLight(7);
            case GL11.GL_COLOR_MATERIAL -> disableColorMaterial();
            case GL11.GL_SCISSOR_TEST -> disableScissorTest();
            case GL11.GL_TEXTURE_2D -> disableTexture2D();
            case GL12.GL_RESCALE_NORMAL -> disableRescaleNormal();
            default -> GL11.glDisable(cap);
        }
    }

    public static boolean glIsEnabled(int cap) {
        if(shouldBypassCache()) {
            return GL11.glIsEnabled(cap);
        }
        return switch (cap) {
            case GL11.GL_ALPHA_TEST -> GLLightingManager.alphaTest.isEnabled();
            case GL11.GL_BLEND -> GLLightingManager.blendMode.isEnabled();
            case GL11.GL_CULL_FACE -> GLLightingManager.cullState.isEnabled();
            case GL11.GL_DEPTH_TEST -> GLLightingManager.depthTest.isEnabled();
            case GL11.GL_FOG -> GLFogManager.fogMode.isEnabled();
            case GL11.GL_LIGHTING -> GLLightingManager.lightingState.isEnabled();
            case GL11.GL_LIGHT0 -> GLLightingManager.lightStates[0].isEnabled();
            case GL11.GL_LIGHT1 -> GLLightingManager.lightStates[1].isEnabled();
            case GL11.GL_LIGHT2 -> GLLightingManager.lightStates[2].isEnabled();
            case GL11.GL_LIGHT3 -> GLLightingManager.lightStates[3].isEnabled();
            case GL11.GL_LIGHT4 -> GLLightingManager.lightStates[4].isEnabled();
            case GL11.GL_LIGHT5 -> GLLightingManager.lightStates[5].isEnabled();
            case GL11.GL_LIGHT6 -> GLLightingManager.lightStates[6].isEnabled();
            case GL11.GL_LIGHT7 -> GLLightingManager.lightStates[7].isEnabled();
            case GL11.GL_COLOR_MATERIAL -> GLLightingManager.colorMaterial.isEnabled();
            case GL11.GL_SCISSOR_TEST -> scissorTest.isEnabled();
            case GL11.GL_TEXTURE_2D -> GLTextureManager.textures.getTextureUnitStates(GLTextureManager.activeTextureUnit.getValue()).isEnabled();
            case GL12.GL_RESCALE_NORMAL -> GLLightingManager.rescaleNormalState.isEnabled();
            default -> GL11.glIsEnabled(cap);
        };
    }

    public static boolean glGetBoolean(int pname) {
        if(shouldBypassCache()) {
            return GL11.glGetBoolean(pname);
        }
        return switch (pname) {
            case GL11.GL_ALPHA_TEST -> GLLightingManager.alphaTest.isEnabled();
            case GL11.GL_BLEND -> GLLightingManager.blendMode.isEnabled();
            case GL11.GL_CULL_FACE -> GLLightingManager.cullState.isEnabled();
            case GL11.GL_DEPTH_TEST -> GLLightingManager.depthTest.isEnabled();
            case GL11.GL_DEPTH_WRITEMASK -> GLLightingManager.depthState.isEnabled();
            case GL11.GL_FOG -> GLFogManager.fogMode.isEnabled();
            case GL11.GL_LIGHTING -> GLLightingManager.lightingState.isEnabled();
            case GL11.GL_LIGHT0 -> GLLightingManager.lightStates[0].isEnabled();
            case GL11.GL_LIGHT1 -> GLLightingManager.lightStates[1].isEnabled();
            case GL11.GL_LIGHT2 -> GLLightingManager.lightStates[2].isEnabled();
            case GL11.GL_LIGHT3 -> GLLightingManager.lightStates[3].isEnabled();
            case GL11.GL_LIGHT4 -> GLLightingManager.lightStates[4].isEnabled();
            case GL11.GL_LIGHT5 -> GLLightingManager.lightStates[5].isEnabled();
            case GL11.GL_LIGHT6 -> GLLightingManager.lightStates[6].isEnabled();
            case GL11.GL_LIGHT7 -> GLLightingManager.lightStates[7].isEnabled();
            case GL11.GL_COLOR_MATERIAL -> GLLightingManager.colorMaterial.isEnabled();
            case GL11.GL_SCISSOR_TEST -> scissorTest.isEnabled();
            case GL11.GL_TEXTURE_2D -> GLTextureManager.textures.getTextureUnitStates(GLTextureManager.activeTextureUnit.getValue()).isEnabled();
            case GL12.GL_RESCALE_NORMAL -> GLLightingManager.rescaleNormalState.isEnabled();
            default -> GL11.glGetBoolean(pname);
        };
    }

    public static void glGetBoolean(int pname, ByteBuffer params) {
        glGetBooleanv(pname, params);
    }

    public static void glGetBooleanv(int pname, ByteBuffer params) {
        if(shouldBypassCache()) {
            GL11.glGetBooleanv(pname, params);
            return;
        }

        switch (pname) {
            case GL11.GL_COLOR_WRITEMASK -> {
                params.put((byte) (GLLightingManager.colorMask.red ? GL11.GL_TRUE : GL11.GL_FALSE));
                params.put((byte) (GLLightingManager.colorMask.green ? GL11.GL_TRUE : GL11.GL_FALSE));
                params.put((byte) (GLLightingManager.colorMask.blue ? GL11.GL_TRUE : GL11.GL_FALSE));
                params.put((byte) (GLLightingManager.colorMask.alpha ? GL11.GL_TRUE : GL11.GL_FALSE));
            }
            default -> {
                if (!HAS_MULTIPLE_SET.contains(pname)) {
                    params.put(0, (byte) (glGetBoolean(pname) ? GL11.GL_TRUE : GL11.GL_FALSE));
                } else {
                    GL11.glGetBooleanv(pname, params);
                }
            }
        }
    }

    public static int glGetInteger(int pname) {
        if(shouldBypassCache()) {
            return GL11.glGetInteger(pname);
        }

        return switch (pname) {
            case GL11.GL_ALPHA_TEST_FUNC -> GLLightingManager.alphaState.getFunction();
            case GL11.GL_DEPTH_FUNC -> GLLightingManager.depthState.getFunc();
            case GL11.GL_LIST_MODE -> glListMode;
            case GL11.GL_MATRIX_MODE -> GLMatrixManager.matrixMode.getMode();
            case GL11.GL_SHADE_MODEL -> GLLightingManager.shadeModelState.getValue();
            case GL11.GL_TEXTURE_BINDING_2D -> GLTextureManager.getBoundTexture();
            case GL14.GL_BLEND_DST_ALPHA -> GLLightingManager.blendState.getDstAlpha();
            case GL14.GL_BLEND_DST_RGB -> GLLightingManager.blendState.getDstRgb();
            case GL14.GL_BLEND_SRC_ALPHA -> GLLightingManager.blendState.getSrcAlpha();
            case GL14.GL_BLEND_SRC_RGB -> GLLightingManager.blendState.getSrcRgb();
            case GL11.GL_COLOR_MATERIAL_FACE -> GLLightingManager.colorMaterialFace.getValue();
            case GL11.GL_COLOR_MATERIAL_PARAMETER -> GLLightingManager.colorMaterialParameter.getValue();
            case GL20.GL_CURRENT_PROGRAM -> GLShaderManager.getActiveProgram();
            case GL11.GL_MODELVIEW_STACK_DEPTH -> GLMatrixManager.getMatrixStackDepth(GLMatrixManager.modelViewMatrix);
            case GL11.GL_PROJECTION_STACK_DEPTH -> GLMatrixManager.getMatrixStackDepth(GLMatrixManager.projectionMatrix);

            default -> GL11.glGetInteger(pname);
        };
    }

    public static void glGetInteger(int pname, IntBuffer params) {
        glGetIntegerv(pname, params);
    }

    public static void glGetIntegerv(int pname, IntBuffer params) {
        if(shouldBypassCache()) {
            GL11.glGetIntegerv(pname, params);
            return;
        }

        switch (pname) {
            case GL11.GL_VIEWPORT -> viewportState.get(params);
            default -> {
                if(!HAS_MULTIPLE_SET.contains(pname)) {
                    params.put(0, glGetInteger(pname));
                } else {
                    GL11.glGetIntegerv(pname, params);
                }
            }
        }
    }

    public static void glGetFloat(int pname, FloatBuffer params) {
        glGetFloatv(pname, params);
    }

    public static void glGetFloatv(int pname, FloatBuffer params) {
        if(shouldBypassCache()) {
            GL11.glGetFloatv(pname, params);
            return;
        }

        switch (pname) {
            case GL11.GL_MODELVIEW_MATRIX -> GLMatrixManager.modelViewMatrix.get(0, params);
            case GL11.GL_PROJECTION_MATRIX -> GLMatrixManager.projectionMatrix.get(0, params);
//            case GL11.GL_TEXTURE_MATRIX -> textures.getTextureUnitMatrix(getActiveTextureUnit()).get(0, params);
            case GL11.GL_COLOR_CLEAR_VALUE -> GLLightingManager.clearColor.get(params);
            case GL11.GL_CURRENT_COLOR -> GLLightingManager.color.get(params);
            default -> {
                if(!HAS_MULTIPLE_SET.contains(pname)) {
                    params.put(0, glGetFloat(pname));
                } else {
                    GL11.glGetFloatv(pname, params);
                }
            }
        }
    }

    public static float glGetFloat(int pname) {
        return GL11.glGetFloat(pname);
    }

    // GLStateManager Functions

    public static void enableScissorTest() {
        scissorTest.enable();
    }

    public static void disableScissorTest() {
        scissorTest.disable();
    }

    public static void glNormal3b(byte nx, byte ny, byte nz) {
        GL11.glNormal3b(nx, ny, nz);
    }
    public static void glNormal3d(double nx, double ny, double nz) {
        GL11.glNormal3d(nx, ny, nz);
    }
    public static void glNormal3f(float nx, float ny, float nz) {
        GL11.glNormal3f(nx, ny, nz);
    }
    public static void glNormal3i(int nx, int ny, int nz) {
        GL11.glNormal3i(nx, ny, nz);
    }

    public static void glEdgeFlag(boolean flag) {
        GL11.glEdgeFlag(flag);
    }

    public static void glAlphaFunc(int function, float reference) {
        if (AngelicaConfig.enableIris) {
            if (AlphaTestStorage.isAlphaTestLocked()) {
                AlphaTestStorage.deferAlphaFunc(function, reference);
                return;
            }
        }
        GLLightingManager.alphaState.setFunction(function);
        GLLightingManager.alphaState.setReference(reference);
        GL11.glAlphaFunc(function, reference);
    }

    public static void glRasterPos2f(float x, float y) {
        GL11.glRasterPos2f(x, y);
    }
    public static void glRasterPos2d(double x, double y) {
        GL11.glRasterPos2d(x, y);
    }
    public static void glRasterPos2i(int x, int y) {
        GL11.glRasterPos2i(x, y);
    }
    public static void glRasterPos3f(float x, float y, float z) {
        GL11.glRasterPos3f(x, y, z);
    }
    public static void glRasterPos3d(double x, double y, double z) {
        GL11.glRasterPos3d(x, y, z);
    }
    public static void glRasterPos3i(int x, int y, int z) {
        GL11.glRasterPos3i(x, y, z);
    }
    public static void glRasterPos4f(float x, float y, float z, float w) {
        GL11.glRasterPos4f(x, y, z, w);
    }
    public static void glRasterPos4d(double x, double y, double z, double w) {
        GL11.glRasterPos4d(x, y, z, w);
    }
    public static void glRasterPos4i(int x, int y, int z, int w) {
        GL11.glRasterPos4i(x, y, z, w);
    }


    public static void setFilter(boolean bilinear, boolean mipmap) {
        int i, j;
        if (bilinear) {
            i = mipmap ? GL11.GL_LINEAR_MIPMAP_LINEAR : GL11.GL_LINEAR;
            j = GL11.GL_LINEAR;
        } else {
            i = mipmap ? GL11.GL_NEAREST_MIPMAP_LINEAR : GL11.GL_NEAREST;
            j = GL11.GL_NEAREST;
        }
        GLTextureManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, i);
        GLTextureManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, j);
    }

    public static void trySyncProgram() {
        if (AngelicaConfig.enableIris) {
            Iris.getPipelineManager().getPipeline().ifPresent(WorldRenderingPipeline::syncProgram);
        }
    }

    public static void glBegin(int mode) {
        trySyncProgram();
        GL11.glBegin(mode);
    }

    public static void glDrawElements(int mode, ByteBuffer indices) {
        trySyncProgram();
        GL11.glDrawElements(mode, indices);
    }

    public static void glDrawElements(int mode, IntBuffer indices) {
        trySyncProgram();
        GL11.glDrawElements(mode, indices);
    }

    public static void glDrawElements(int mode, ShortBuffer indices) {
        trySyncProgram();
        GL11.glDrawElements(mode, indices);
    }

    public static void glDrawElements(int mode, int indices_count, int type, long indices_buffer_offset) {
        trySyncProgram();
        GL11.glDrawElements(mode, indices_count, type, indices_buffer_offset);
    }

    public static void glDrawElements(int mode, int count, int type, ByteBuffer indices) {
        trySyncProgram();
        GL11.glDrawElements(mode, type, indices);
    }

    public static void glDrawElements(int mode, int type, ByteBuffer indices) {
        trySyncProgram();
        GL11.glDrawElements(mode, type, indices);
    }

    public static void glDrawBuffer(int mode) {
        trySyncProgram();
        GL11.glDrawBuffer(mode);
    }

    public static void glDrawArrays(int mode, int first, int count) {
        trySyncProgram();
        GL11.glDrawArrays(mode, first, count);
    }

    public static void glLogicOp(int opcode) {
        GL11.glLogicOp(opcode);
    }

    public static void enableRescaleNormal() {
        GLLightingManager.rescaleNormalState.enable();
    }

    public static void disableRescaleNormal() {
        GLLightingManager.rescaleNormalState.disable();
    }

    public static void enableFog() {
        GLFogManager.enableFog();
    }

    public static void disableFog() {
        GLFogManager.disableFog();
    }

    public static void makeCurrent(Drawable drawable) throws LWJGLException {
        drawable.makeCurrent();
        final Thread currentThread = Thread.currentThread();

        CurrentThread = currentThread;
        LOGGER.info("Current thread: {}", currentThread.getName());
    }

    public static void glNewList(int list, int mode) {
        if(glListMode > 0) {
            glListNesting += 1;
            return;
        }

        glListId = list;
        glListMode = mode;
        GL11.glNewList(list, mode);

        List<IStateStack<?>> stacks = Feature.maskToFeatures(GL11.GL_ALL_ATTRIB_BITS);
        int size = stacks.size();

        for(int i = 0; i < size; i++) {
            IStateStack<?> stack = stacks.get(i);

            // Feature Stack, copy of current feature state
            glListStates.put(stack, (ISettableState<?>) ((ISettableState<?>)stack).copy());
        }

        if(glListMode == GL11.GL_COMPILE) {
            pushState(GL11.GL_ALL_ATTRIB_BITS);
        }
    }

    public static void glEndList() {
        if (glListNesting > 0) {
            glListNesting -= 1;
            return;
        }

        if(glListMode == 0) {
            throw new RuntimeException("glEndList called outside of a display list!");
        }

        final Set<Map.Entry<IStateStack<?>, ISettableState<?>>> changedStates = new ObjectArraySet<>();
        for(Map.Entry<IStateStack<?>, ISettableState<?>> entry : glListStates.entrySet()) {
            // If the current stack state is different than the copy of the state at the start
            if(!((ISettableState<?>)entry.getKey()).sameAs(entry.getValue())) {
                // Then we want to put into the change set the stack and the copy of the state now
                changedStates.add(new AbstractMap.SimpleEntry<>(entry.getKey(), (ISettableState<?>) ((ISettableState<?>) entry.getKey()).copy()));
            }
        }
        if(changedStates.size() != 0) {
            glListChanges.put(glListId, changedStates);
        }
        if(glListMode == GL11.GL_COMPILE) {
            // GL_COMPILE doesn't actually apply the state, just stores it for replay in glCallList, so we'll
            // roll back any changes that we tracked
            popState();
        }
        glListId = -1;
        glListStates.clear();
        glListMode = 0;
        GL11.glEndList();

    }

    public static void glCallList(int list) {
        if(list < 0) {
            VBOManager.get(list).render();
        } else {
            trySyncProgram();
            GL11.glCallList(list);
            if(glListChanges.containsKey(list)) {
                for(Map.Entry<IStateStack<?>, ISettableState<?>> entry : glListChanges.get(list)) {
                    // Set the stack to the cached state at the end of the call list compilation
                    ((ISettableState<?>)entry.getKey()).set(entry.getValue());
                }
            }
        }
    }

    public static void pushState(int mask) {
        GLAttribManager.attribs.push(mask);

        List<IStateStack<?>> stacks = Feature.maskToFeatures(mask);
        int size = stacks.size();

        for(int i = 0; i < size; i++) {
            stacks.get(i).push();
        }
    }

    public static void popState() {
        final int mask = GLAttribManager.attribs.popInt();

        List<IStateStack<?>> stacks = Feature.maskToFeatures(mask);
        int size = stacks.size();

        for(int i = 0; i < size; i++) {
            stacks.get(i).pop();
        }
    }

    public static void glClear(int mask) {
        // TODO: Implement
        GL11.glClear(mask);
    }

    public static void glViewport(int x, int y, int width, int height) {
        GL11.glViewport(x, y, width, height);
        viewportState.setViewPort(x, y, width, height);
    }

    public static int getActiveTextureUnit() {
        return GLTextureManager.activeTextureUnit.getValue();
    }

    public static int getListMode() {
        return glListMode;
    }

    public static void glDepthRange(double near, double far) {
        GL11.glDepthRange(near, far);
    }

    public static void glVertex4i(int x, int y, int z, int w) {
        GL11.glVertex4i(x, y, z, w);
    }

    public static void glVertexPointer(int size, int type, int stride, long pointer_buffer_offset) {
        GL11.glVertexPointer(size, type, stride, pointer_buffer_offset);
    }

    public static void glVertexPointer(int size, int type, int stride, ByteBuffer pointer) {
        GL11.glVertexPointer(size, type, stride, pointer);
    }

    public static void glVertexPointer(int size, int type, int stride, FloatBuffer pointer) {
        GL11.glVertexPointer(size, type, stride, pointer);
    }

    public static void glVertexPointer(int size, int type, int stride, IntBuffer pointer) {
        GL11.glVertexPointer(size, type, stride, pointer);
    }

    public static void glVertexPointer(int size, int type, int stride, ShortBuffer pointer) {
        GL11.glVertexPointer(size, type, stride, pointer);
    }

    public static void glNormalPointer(int type, int stride, long pointer_buffer_offset) {
        GL11.glNormalPointer(type, stride, pointer_buffer_offset);
    }

    public static void glNormalPointer(int type, int stride, ByteBuffer pointer) {
        GL11.glNormalPointer(type, stride, pointer);
    }

    public static void glNormalPointer(int type, int stride, FloatBuffer pointer) {
        GL11.glNormalPointer(type, stride, pointer);
    }

    public static void glNormalPointer(int type, int stride, IntBuffer pointer) {
        GL11.glNormalPointer(type, stride, pointer);
    }

    public static void glScissor(int x, int y, int width, int height) {
        scissorState.setScissor(x, y, width, height);
    }
}
