// Code written by daxnitro modified by id_miner and karyonix.
// Do what you want with it but give us some credit if you use it in whole or in part.

package com.gtnewhorizons.angelica.client;

import static org.lwjgl.opengl.ARBFragmentShader.GL_FRAGMENT_SHADER_ARB;
import static org.lwjgl.opengl.ARBShaderObjects.GL_OBJECT_INFO_LOG_LENGTH_ARB;
import static org.lwjgl.opengl.ARBShaderObjects.glAttachObjectARB;
import static org.lwjgl.opengl.ARBShaderObjects.glCompileShaderARB;
import static org.lwjgl.opengl.ARBShaderObjects.glCreateProgramObjectARB;
import static org.lwjgl.opengl.ARBShaderObjects.glCreateShaderObjectARB;
import static org.lwjgl.opengl.ARBShaderObjects.glDeleteObjectARB;
import static org.lwjgl.opengl.ARBShaderObjects.glDetachObjectARB;
import static org.lwjgl.opengl.ARBShaderObjects.glGetInfoLogARB;
import static org.lwjgl.opengl.ARBShaderObjects.glGetObjectParameterARB;
import static org.lwjgl.opengl.ARBShaderObjects.glGetUniformLocationARB;
import static org.lwjgl.opengl.ARBShaderObjects.glLinkProgramARB;
import static org.lwjgl.opengl.ARBShaderObjects.glShaderSourceARB;
import static org.lwjgl.opengl.ARBShaderObjects.glUniform1fARB;
import static org.lwjgl.opengl.ARBShaderObjects.glUniform1iARB;
import static org.lwjgl.opengl.ARBShaderObjects.glUniform2iARB;
import static org.lwjgl.opengl.ARBShaderObjects.glUniform3fARB;
import static org.lwjgl.opengl.ARBShaderObjects.glUniformMatrix4ARB;
import static org.lwjgl.opengl.ARBShaderObjects.glUseProgramObjectARB;
import static org.lwjgl.opengl.ARBShaderObjects.glValidateProgramARB;
import static org.lwjgl.opengl.ARBVertexShader.GL_VERTEX_SHADER_ARB;
import static org.lwjgl.opengl.ARBVertexShader.glBindAttribLocationARB;
import static org.lwjgl.opengl.EXTFramebufferObject.GL_COLOR_ATTACHMENT0_EXT;
import static org.lwjgl.opengl.EXTFramebufferObject.GL_COLOR_ATTACHMENT1_EXT;
import static org.lwjgl.opengl.EXTFramebufferObject.GL_DEPTH_ATTACHMENT_EXT;
import static org.lwjgl.opengl.EXTFramebufferObject.GL_FRAMEBUFFER_COMPLETE_EXT;
import static org.lwjgl.opengl.EXTFramebufferObject.GL_FRAMEBUFFER_EXT;
import static org.lwjgl.opengl.EXTFramebufferObject.GL_FRAMEBUFFER_INCOMPLETE_FORMATS_EXT;
import static org.lwjgl.opengl.EXTFramebufferObject.GL_INVALID_FRAMEBUFFER_OPERATION_EXT;
import static org.lwjgl.opengl.EXTFramebufferObject.GL_MAX_COLOR_ATTACHMENTS_EXT;
import static org.lwjgl.opengl.EXTFramebufferObject.glBindFramebufferEXT;
import static org.lwjgl.opengl.EXTFramebufferObject.glCheckFramebufferStatusEXT;
import static org.lwjgl.opengl.EXTFramebufferObject.glDeleteFramebuffersEXT;
import static org.lwjgl.opengl.EXTFramebufferObject.glFramebufferTexture2DEXT;
import static org.lwjgl.opengl.EXTFramebufferObject.glGenFramebuffersEXT;
import static org.lwjgl.opengl.GL11.GL_ALPHA_TEST;
import static org.lwjgl.opengl.GL11.GL_ALWAYS;
import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_CLAMP;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_COMPONENT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_FOG;
import static org.lwjgl.opengl.GL11.GL_LEQUAL;
import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_LINEAR_MIPMAP_LINEAR;
import static org.lwjgl.opengl.GL11.GL_LUMINANCE;
import static org.lwjgl.opengl.GL11.GL_MODELVIEW;
import static org.lwjgl.opengl.GL11.GL_MODELVIEW_MATRIX;
import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL11.GL_NEAREST_MIPMAP_NEAREST;
import static org.lwjgl.opengl.GL11.GL_NONE;
import static org.lwjgl.opengl.GL11.GL_NO_ERROR;
import static org.lwjgl.opengl.GL11.GL_PROJECTION;
import static org.lwjgl.opengl.GL11.GL_PROJECTION_MATRIX;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_RGB16;
import static org.lwjgl.opengl.GL11.GL_RGB8;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_RGBA16;
import static org.lwjgl.opengl.GL11.GL_RGBA8;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11.GL_TRUE;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glColor4f;
import static org.lwjgl.opengl.GL11.glColorMask;
import static org.lwjgl.opengl.GL11.glCopyTexSubImage2D;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glDepthFunc;
import static org.lwjgl.opengl.GL11.glDepthMask;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glFlush;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glGetError;
import static org.lwjgl.opengl.GL11.glGetFloat;
import static org.lwjgl.opengl.GL11.glGetInteger;
import static org.lwjgl.opengl.GL11.glLoadIdentity;
import static org.lwjgl.opengl.GL11.glMatrixMode;
import static org.lwjgl.opengl.GL11.glOrtho;
import static org.lwjgl.opengl.GL11.glPopMatrix;
import static org.lwjgl.opengl.GL11.glPushMatrix;
import static org.lwjgl.opengl.GL11.glReadBuffer;
import static org.lwjgl.opengl.GL11.glReadPixels;
import static org.lwjgl.opengl.GL11.glRotatef;
import static org.lwjgl.opengl.GL11.glTexCoord2f;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameterf;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL11.glTranslatef;
import static org.lwjgl.opengl.GL11.glVertex3f;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.GL_TEXTURE1;
import static org.lwjgl.opengl.GL13.GL_TEXTURE11;
import static org.lwjgl.opengl.GL13.GL_TEXTURE12;
import static org.lwjgl.opengl.GL13.GL_TEXTURE13;
import static org.lwjgl.opengl.GL13.GL_TEXTURE14;
import static org.lwjgl.opengl.GL13.GL_TEXTURE2;
import static org.lwjgl.opengl.GL13.GL_TEXTURE3;
import static org.lwjgl.opengl.GL13.GL_TEXTURE4;
import static org.lwjgl.opengl.GL13.GL_TEXTURE5;
import static org.lwjgl.opengl.GL13.GL_TEXTURE6;
import static org.lwjgl.opengl.GL13.GL_TEXTURE7;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL14.GL_DEPTH_TEXTURE_MODE;
import static org.lwjgl.opengl.GL14.GL_TEXTURE_COMPARE_MODE;
import static org.lwjgl.opengl.GL20.GL_MAX_DRAW_BUFFERS;
import static org.lwjgl.opengl.GL20.GL_MAX_TEXTURE_IMAGE_UNITS;
import static org.lwjgl.opengl.GL20.GL_VALIDATE_STATUS;
import static org.lwjgl.opengl.GL20.glDrawBuffers;
import static org.lwjgl.opengl.GL20.glGetProgrami;
import static org.lwjgl.opengl.GL30.GL_COMPARE_REF_TO_TEXTURE;
import static org.lwjgl.opengl.GL30.GL_R16;
import static org.lwjgl.opengl.GL30.GL_R32F;
import static org.lwjgl.opengl.GL30.GL_R8;
import static org.lwjgl.opengl.GL30.GL_RG16;
import static org.lwjgl.opengl.GL30.GL_RG32F;
import static org.lwjgl.opengl.GL30.GL_RG8;
import static org.lwjgl.opengl.GL30.GL_RGB32F;
import static org.lwjgl.opengl.GL30.GL_RGBA32F;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;
import static org.lwjgl.util.glu.GLU.gluErrorString;
import static org.lwjgl.util.glu.GLU.gluPerspective;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.Vec3;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GLContext;
// import org.lwjgl.opengl.ARBVertexProgram;

public class Shaders {

    public static final String versionString = "2.3.18";
    public static final int versionNumber = 0x020312;
    public static final int buildNumber = 53;

    private static Minecraft mc;

    public static boolean isInitialized = false;
    private static boolean notFirstInit = false;
    public static ContextCapabilities capabilities;
    public static boolean hasGlGenMipmap = false;
    public static int numberResetDisplayList = 0;

    private static int renderDisplayWidth = 0;
    private static int renderDisplayHeight = 0;
    public static int renderWidth = 0;
    public static int renderHeight = 0;

    public static boolean isRenderingWorld = false;
    public static boolean isRenderingSky = false;
    public static boolean isCompositeRendered = false;
    public static boolean isRenderingDfb = false;
    public static boolean isShadowPass = false;
    public static int activeTexUnit = 0;

    public static boolean isHandRendered;
    public static ItemStack itemToRender;

    private static float[] sunPosition = new float[4];
    private static float[] moonPosition = new float[4];
    private static float[] upPosition = new float[4];

    private static float[] upPosModelView = new float[] {0.0F, 100.0F, 0.0F, 0.0F};
    private static float[] sunPosModelView = new float[] {0.0F, 100.0F, 0.0F, 0.0F};
    private static float[] moonPosModelView = new float[] {0.0F, -100.0F, 0.0F, 0.0F};
    private static float[] tempMat = new float[16];

    private static float clearColorR;
    private static float clearColorG;
    private static float clearColorB;

    private static float skyColorR;
    private static float skyColorG;
    private static float skyColorB;

    private static long worldTime = 0;
    private static long lastWorldTime = 0;
    private static long diffWorldTime = 0;
    private static float sunAngle = 0;
    private static float shadowAngle = 0;
    private static int moonPhase = 0;

    private static long systemTime = 0;
    private static long lastSystemTime = 0;
    private static long diffSystemTime = 0;

    private static int frameCounter = 0;
    private static float frameTimeCounter = 0.0f;

    private static int systemTimeInt32 = 0;

    private static float rainStrength = 0.0f;
    private static float wetness = 0.0f;
    public static float wetnessHalfLife = 30 * 20;
    public static float drynessHalfLife = 10 * 20;
    public static float eyeBrightnessHalflife = 10.0f;
    private static boolean usewetness = false;
    private static int isEyeInWater = 0;
    private static int eyeBrightness = 0;
    private static float eyeBrightnessFadeX = 0;
    private static float eyeBrightnessFadeY = 0;
    private static float eyePosY = 0;
    private static float centerDepth = 0;
    private static float centerDepthSmooth = 0;
    private static float centerDepthSmoothHalflife = 1.0f;
    private static boolean centerDepthSmoothEnabled = false;
    private static int superSamplingLevel = 1;

    private static boolean updateChunksErrorRecorded = false;

    private static boolean lightmapEnabled = false;
    private static boolean fogEnabled = true;

    public static int entityAttrib = 10;
    public static int midTexCoordAttrib = 11;
    public static boolean useEntityAttrib = false;
    public static boolean useMidTexCoordAttrib = false;
    public static boolean useMultiTexCoord3Attrib = false;
    public static boolean progUseEntityAttrib = false;
    public static boolean progUseMidTexCoordAttrib = false;

    public static int atlasSizeX = 0, atlasSizeY = 0;
    public static int uniformEntityHurt = -1;
    public static int uniformEntityFlash = -1;
    public static boolean useEntityHurtFlash;

    private static double[] previousCameraPosition = new double[3];
    private static double[] cameraPosition = new double[3];

    // Shadow stuff

    // configuration
    private static int shadowPassInterval = 0;
    public static boolean needResizeShadow = false;
    private static int shadowMapWidth = 1024;
    private static int shadowMapHeight = 1024;
    private static int spShadowMapWidth = 1024;
    private static int spShadowMapHeight = 1024;
    private static float shadowMapFOV = 90.0f;
    private static float shadowMapHalfPlane = 160.0f;
    private static boolean shadowMapIsOrtho = true;

    private static int shadowPassCounter = 0;

    private static int preShadowPassThirdPersonView;

    public static boolean shouldSkipDefaultShadow = false;

    private static boolean waterShadowEnabled = false;

    // Color attachment stuff

    private static final int MaxDrawBuffers = 8;
    private static final int MaxColorBuffers = 8;
    private static final int MaxDepthBuffers = 3;
    private static final int MaxShadowColorBuffers = 8;
    private static final int MaxShadowDepthBuffers = 2;
    private static int usedColorBuffers = 0;
    private static int usedDepthBuffers = 0;
    private static int usedShadowColorBuffers = 0;
    private static int usedShadowDepthBuffers = 0;
    private static int usedColorAttachs = 0;
    private static int usedDrawBuffers = 0;

    private static int dfb = 0;
    private static int sfb = 0;

    private static int[] gbuffersFormat = new int[MaxColorBuffers];

    // Program stuff

    public static int activeProgram = 0;

    public static final int ProgramNone = 0;
    public static final int ProgramBasic = 1;
    public static final int ProgramTextured = 2;
    public static final int ProgramTexturedLit = 3;
    public static final int ProgramSkyBasic = 4;
    public static final int ProgramSkyTextured = 5;
    public static final int ProgramTerrain = 6;
    public static final int ProgramWater = 7;
    public static final int ProgramEntities = 8;
    public static final int ProgramSpiderEyes = 9;
    public static final int ProgramHand = 10;
    public static final int ProgramWeather = 11;
    public static final int ProgramComposite = 12;
    public static final int ProgramComposite1 = 13;
    public static final int ProgramComposite2 = 14;
    public static final int ProgramComposite3 = 15;
    public static final int ProgramComposite4 = 16;
    public static final int ProgramComposite5 = 17;
    public static final int ProgramComposite6 = 18;
    public static final int ProgramComposite7 = 19;
    public static final int ProgramFinal = 20;
    public static final int ProgramShadow = 21;
    public static final int ProgramCount = 22;
    public static final int MaxCompositePasses = 8;

    private static final String[] programNames = new String[] {
        "",
        "gbuffers_basic",
        "gbuffers_textured",
        "gbuffers_textured_lit",
        "gbuffers_skybasic",
        "gbuffers_skytextured",
        "gbuffers_terrain",
        "gbuffers_water",
        "gbuffers_entities",
        "gbuffers_spidereyes",
        "gbuffers_hand",
        "gbuffers_weather",
        "composite",
        "composite1",
        "composite2",
        "composite3",
        "composite4",
        "composite5",
        "composite6",
        "composite7",
        "final",
        "shadow",
    };

    private static final int[] programBackups = new int[] {
        ProgramNone, // none
        ProgramNone, // basic
        ProgramBasic, // textured
        ProgramTextured, // textured/lit
        ProgramBasic, // skybasic
        ProgramTextured, // skytextured
        ProgramTexturedLit, // terrain
        ProgramTerrain, // water
        ProgramTexturedLit, // entities
        ProgramTextured, // spidereyes
        ProgramTexturedLit, // hand
        ProgramTexturedLit, // weather
        ProgramNone, // composite
        ProgramNone, // composite1
        ProgramNone, // composite2
        ProgramNone, // composite3
        ProgramNone, // composite4
        ProgramNone, // composite5
        ProgramNone, // composite6
        ProgramNone, // composite7
        ProgramNone, // final
        ProgramNone, // shadow
    };

    private static int[] programsID = new int[ProgramCount];
    private static int[] programsRef = new int[ProgramCount];
    private static int programIDCopyDepth = 0;

    private static String[] programsDrawBufSettings = new String[ProgramCount];
    private static String newDrawBufSetting = null;
    private static IntBuffer[] programsDrawBuffers = new IntBuffer[ProgramCount];
    static IntBuffer activeDrawBuffers = null;

    private static String[] programsColorAtmSettings = new String[ProgramCount];
    private static String newColorAtmSetting = null;
    private static String activeColorAtmSettings = null;

    private static int[] programsCompositeMipmapSetting = new int[ProgramCount];
    private static int newCompositeMipmapSetting = 0;
    private static int activeCompositeMipmapSetting = 0;

    public static Properties loadedShaders = null;
    public static Properties shadersConfig = null;

    // public static TextureManager textureManager = null;
    public static ITextureObject defaultTexture = null;
    public static boolean normalMapEnabled = false;
    public static boolean[] shadowHardwareFilteringEnabled = new boolean[MaxShadowDepthBuffers];
    public static boolean[] shadowMipmapEnabled = new boolean[MaxShadowDepthBuffers];
    public static boolean[] shadowFilterNearest = new boolean[MaxShadowDepthBuffers];
    public static boolean[] shadowColorMipmapEnabled = new boolean[MaxShadowColorBuffers];
    public static boolean[] shadowColorFilterNearest = new boolean[MaxShadowColorBuffers];

    // config
    public static boolean configTweakBlockDamage = false;
    public static boolean configCloudShadow = true;
    public static float configHandDepthMul = 0.125f;
    public static float configRenderResMul = 1.0f;
    public static float configShadowResMul = 1.0f;
    public static int configTexMinFilB = 0;
    public static int configTexMinFilN = 0;
    public static int configTexMinFilS = 0;
    public static int configTexMagFilB = 0;
    public static int configTexMagFilN = 0;
    public static int configTexMagFilS = 0;
    public static boolean configShadowClipFrustrum = true;
    public static boolean configNormalMap = true;
    public static boolean configSpecularMap = true;
    public static boolean configOldLighting = false;

    // related to config
    public static final int texMinFilRange = 3;
    public static final int texMagFilRange = 2;
    public static final String[] texMinFilDesc = {"Nearest", "Nearest-Nearest", "Nearest-Linear"};
    public static final String[] texMagFilDesc = {"Nearest", "Linear"};
    public static final int[] texMinFilValue = {
        GL11.GL_NEAREST, GL11.GL_NEAREST_MIPMAP_NEAREST, GL11.GL_NEAREST_MIPMAP_LINEAR
    };
    public static final int[] texMagFilValue = {GL11.GL_NEAREST, GL11.GL_LINEAR};

    // shaderpack
    static IShaderPack shaderPack = null;
    static File currentshader;
    static String currentshadername;
    static String packNameNone = "(none)";
    static String packNameDefault = "(internal)";
    static String shaderpacksdirname = "shaderpacks";
    static String optionsfilename = "optionsshaders.txt";
    static File shadersdir = new File(Minecraft.getMinecraft().mcDataDir, "shaders");
    static File shaderpacksdir = new File(Minecraft.getMinecraft().mcDataDir, shaderpacksdirname);
    static File configFile = new File(Minecraft.getMinecraft().mcDataDir, optionsfilename);

    public static final boolean enableShadersOption = true;
    private static final boolean enableShadersDebug = true;

    public static float blockLightLevel05 = 0.5f;
    public static float blockLightLevel06 = 0.6f;
    public static float blockLightLevel08 = 0.8f;

    public static float aoLevel = 0.8f;
    public static float blockAoLight = 1.0f - aoLevel;

    public static float sunPathRotation = 0.0f;
    public static float shadowAngleInterval = 0.0f;
    public static int fogMode = 0;
    public static float fogColorR, fogColorG, fogColorB;
    public static float shadowIntervalSize = 2.0f;
    public static int terrainIconSize = 16;
    public static int[] terrainTextureSize = new int[2];

    private static HFNoiseTexture noiseTexture;
    private static boolean noiseTextureEnabled = false;
    private static int noiseTextureResolution = 256;

    // direct buffers
    private static final int bigBufferSize = (16 * 12
                    + MaxColorBuffers
                    + MaxDepthBuffers
                    + MaxShadowColorBuffers
                    + MaxShadowDepthBuffers
                    + MaxDrawBuffers * 8
                    + MaxDrawBuffers * ProgramCount)
            * 4;
    private static final ByteBuffer bigBuffer =
            (ByteBuffer) BufferUtils.createByteBuffer(bigBufferSize).limit(0);

    private static final FloatBuffer previousProjection = nextFloatBuffer(16);
    private static final FloatBuffer projection = nextFloatBuffer(16);
    private static final FloatBuffer projectionInverse = nextFloatBuffer(16);
    private static final FloatBuffer previousModelView = nextFloatBuffer(16);
    private static final FloatBuffer modelView = nextFloatBuffer(16);
    private static final FloatBuffer modelViewInverse = nextFloatBuffer(16);
    private static final FloatBuffer shadowProjection = nextFloatBuffer(16);
    private static final FloatBuffer shadowProjectionInverse = nextFloatBuffer(16);
    private static final FloatBuffer shadowModelView = nextFloatBuffer(16);
    private static final FloatBuffer shadowModelViewInverse = nextFloatBuffer(16);
    private static final FloatBuffer tempMatrixDirectBuffer = nextFloatBuffer(16);
    private static final FloatBuffer tempDirectFloatBuffer = nextFloatBuffer(16);

    private static final IntBuffer dfbColorTextures = nextIntBuffer(MaxColorBuffers);
    private static final IntBuffer dfbDepthTextures = nextIntBuffer(MaxDepthBuffers);
    private static final IntBuffer sfbColorTextures = nextIntBuffer(MaxShadowColorBuffers);
    private static final IntBuffer sfbDepthTextures = nextIntBuffer(MaxShadowDepthBuffers);

    static final IntBuffer dfbDrawBuffers = nextIntBuffer(MaxDrawBuffers);
    static final IntBuffer sfbDrawBuffers = nextIntBuffer(MaxDrawBuffers);
    static final IntBuffer drawBuffersNone = nextIntBuffer(MaxDrawBuffers);
    static final IntBuffer drawBuffersAll = nextIntBuffer(MaxDrawBuffers);
    static final IntBuffer drawBuffersClear0 = nextIntBuffer(MaxDrawBuffers);
    static final IntBuffer drawBuffersClear1 = nextIntBuffer(MaxDrawBuffers);
    static final IntBuffer drawBuffersClearColor = nextIntBuffer(MaxDrawBuffers);
    static final IntBuffer drawBuffersColorAtt0 = nextIntBuffer(MaxDrawBuffers);

    static final IntBuffer[] drawBuffersBuffer = nextIntBufferArray(ProgramCount, MaxDrawBuffers);

    // static final String ofVersion = "OptiFine_x.x.x_HD_U_xx";
    // static final String ofVersion = Config.VERSION;

    // static int lastversion = version;
    // static int updateinterval = 48;
    // public static final String siteurl = "http://glslshadersof.no-ip.org";
    // public static final String updatecheckurl = "http:....../lastversion145.html";
    // static JFrame frame;

    static {
        drawBuffersNone.limit(0);
        drawBuffersColorAtt0.put(GL_COLOR_ATTACHMENT0_EXT).position(0).limit(1);
    }

    private Shaders() {}

    /** set position to limit and advance limit by size */
    private static ByteBuffer nextByteBuffer(int size) {
        ByteBuffer buffer = bigBuffer;
        int pos = buffer.limit();
        buffer.position(pos).limit(pos + size);
        return buffer.slice();
    }

    private static IntBuffer nextIntBuffer(int size) {
        ByteBuffer buffer = bigBuffer;
        int pos = buffer.limit();
        buffer.position(pos).limit(pos + size * 4);
        return buffer.asIntBuffer();
    }

    private static FloatBuffer nextFloatBuffer(int size) {
        ByteBuffer buffer = bigBuffer;
        int pos = buffer.limit();
        buffer.position(pos).limit(pos + size * 4);
        return buffer.asFloatBuffer();
    }

    private static IntBuffer[] nextIntBufferArray(int count, int size) {
        IntBuffer[] aib = new IntBuffer[count];
        for (int i = 0; i < count; ++i) aib[i] = nextIntBuffer(size);
        return aib;
    }

    /*
    	static void checkForUpdate()
    	{
    		if (!shadersdir.exists()) shadersdir.mkdir();
    		lastversion = version;
    		int lastcheck = 0;
    		File updatecheck = new File(shadersdir,"updatecheck.cfg");
    		try
    		{
    			if (updatecheck.exists())
    			{
    				BufferedReader bufferedreader2 = new BufferedReader(new FileReader(updatecheck));

    				for (String s = ""; (s = bufferedreader2.readLine()) != null;)
    				{
    					try
    					{
    						String as[] = s.split(":");
    						int vv;
    						if (as[0].equals("updateinterval"))
    						{
    							vv = Integer.parseInt(as[1]);
    							if (vv < 0 || ( vv > 0 && vv < 24 ) || vv > 736)
    							{
    								System.out.println("[Shaders] Skipping bad option: updateinterval, range is: 0, 24-736");
    							}
    							else
    							{
    								updateinterval = vv;
    							}
    						}
    						else if (as[0].equals("lastcheck"))
    						{
    							lastcheck = Integer.parseInt(as[1]);
    						}
    						else if (as[0].equals("lastversion"))
    						{
    							lastversion = Integer.parseInt(as[1]);
    						}
    					}
    					catch (Exception exception1)
    					{
    						System.out.println("[Shaders] "+(new StringBuilder()).append("Skipping bad option: ").append(s).toString());
    					}
    				}
    				bufferedreader2.close();
    			}
    		}
    		catch(Exception ex){}

    		if (updateinterval > 0)
    		{
    			try
    			{
    				if (lastversion > version)
    				{
    					updatenotify(versiontostring(lastversion));
    					System.out.println("[Shaders] New version "+versiontostring(lastversion)+" of the GLSL Shaders OF mod is available, go to "+ siteurl +" to download.");
    				}
    				else
    				{
    					int currenthours = (int) (System.currentTimeMillis() / 3600000);
    					if (currenthours - lastcheck > updateinterval )
    					{
    						System.out.println("[Shaders] Checking for updates. Last checked "+Integer.toString(currenthours - lastcheck)+" hours ago.");
    						lastcheck = currenthours;
    						try
    						{
    							URL url = new URL( updatecheckurl );
    							HttpURLConnection.setFollowRedirects(true);
    							HttpURLConnection uconn;
    							uconn = (HttpURLConnection) url.openConnection();
    							uconn.setConnectTimeout(2500);
    							uconn.setReadTimeout(1500);
    							try
    							{
    								uconn.connect();
    							}
    							catch (SocketTimeoutException excep)
    							{
    								System.out.println("[Shaders] Failed to connect to " + updatecheckurl);
    								return;
    							}

    							BufferedReader bufferedreader2 = new BufferedReader(new InputStreamReader( uconn.getInputStream()));

    							for (String s = ""; (s = bufferedreader2.readLine()) != null;)
    							{
    								try
    								{
    									String as[] = s.split(":");
    									if (as[1].equals("lastversion"))
    									{
    										lastversion = Integer.parseInt(as[2]);
    										break;
    									}
    								}
    								catch (Exception exception1){}
    							}
    							bufferedreader2.close();
    						}
    						catch (Exception exception)
    						{
    							System.out.println("[Shaders] Failed to download " + updatecheckurl);
    							return;
    						}

    						BufferedWriter bufferedwriter2 = new BufferedWriter(new FileWriter(updatecheck));
    						bufferedwriter2.write("updateinterval:"+updateinterval);
    						bufferedwriter2.newLine();
    						bufferedwriter2.write("lastcheck:"+Integer.toString(lastcheck));
    						bufferedwriter2.newLine();
    						bufferedwriter2.write("lastversion:"+Integer.toString(lastversion));
    						bufferedwriter2.newLine();
    						bufferedwriter2.close();

    						if (lastversion > version)
    						{
    							updatenotify(versiontostring(lastversion));
    							System.out.println("[Shaders] New version "+versiontostring(lastversion)+" of the GLSL Shaders OF mod is available, go to "+ siteurl +" to download.");
    						}
    					}
    				}
    			}
    			catch (Exception exception)
    			{
    				System.out.println("[Shaders] Failed to load GLSL Shaders update file.");
    				exception.printStackTrace();
    			}
    		}
    	}

    	public static void updatenotify(String lastversion)
    	{
    		try
    		{
    			frame = new JFrame("GLSL Shaders OF mod update");
    		}
    		catch(Exception e)
    		{
    		return;
    		}

    		Color bgcolor=new Color(194,205,234);
    		Color txcolor=new Color(50,50,50);
    		frame.setLocation(20,20);
    		JLabel label=new JLabel("New version of the GLSL Shaders OF mod is available! (v"+lastversion+") ");
    		JPanel panel = new JPanel();
    		panel.setBackground(bgcolor);
    		label.setForeground(txcolor);
    		label.setFont(new Font("Dialog", 1, 14));
    		panel.add(label);

    		if( java.awt.Desktop.isDesktopSupported() )
    		{
    			JButton button;
    			final java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
    			if( desktop.isSupported( java.awt.Desktop.Action.BROWSE ) )
    			{
    				button = new JButton("Download");
    				button.setActionCommand("BPUPD");

    			}
    			else
    			{
    				button = new JButton("Copy URL to Clipboard");
    				button.setActionCommand("CPURL");
    			}
    			button.setFont(new Font("Dialog", 1, 14));
    			button.addActionListener(new ButtonListener());
    			panel.add(button);
    		}
    		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    		frame.getContentPane().add(panel);
    		frame.pack();
    		frame.setFocusableWindowState(false);
    		frame.setVisible(true);
    		frame.setFocusableWindowState(true);
    	}

    	static class ButtonListener implements ActionListener
    	{
    		ButtonListener()
    		{
    		}
    		public void actionPerformed(ActionEvent e)
    		{
    			String cmd = e.getActionCommand();
    			if (cmd == "BPUPD")
    			{
    				try
    				{
    					java.net.URI uri = new java.net.URI( siteurl );
    					java.awt.Desktop.getDesktop().browse( uri );
    					frame.dispose();
    				}
    				catch ( Exception exx ) {}
    			}
    			else if (cmd == "CPURL")
    			{
    				StringSelection ss = new StringSelection(siteurl);
    				Toolkit.getDefaultToolkit().getSystemClipboard().setContents(ss, null);
    			}

    		}
    	}
    */

    public static void loadConfig() {
        System.out.println("[Shaders] Loading configuration.");
        try {
            if (!shaderpacksdir.exists()) shaderpacksdir.mkdir();
        } catch (Exception e) {
            System.err.println("[Shaders] Failed openning shaderpacks directory.");
        }

        shadersConfig = new Properties();
        shadersConfig.setProperty("shaderPack", "");
        if (configFile.exists()) {
            try {
                FileReader reader = new FileReader(configFile);
                shadersConfig.load(reader);
                reader.close();
            } catch (Exception e) {
            }
        }

        if (!configFile.exists()) {
            try {
                storeConfig();
            } catch (Exception e) {
            }
        }
        String value = null;
        configNormalMap = Boolean.parseBoolean(shadersConfig.getProperty("normalMapEnabled", "true"));
        configSpecularMap = Boolean.parseBoolean(shadersConfig.getProperty("specularMapEnabled", "true"));
        configTweakBlockDamage = Boolean.parseBoolean(
                shadersConfig.getProperty("tweakBlockDamage", shadersConfig.getProperty("dtweak", "false")));
        configCloudShadow = Boolean.parseBoolean(shadersConfig.getProperty("cloudShadow", "true"));
        configHandDepthMul = Float.parseFloat(shadersConfig.getProperty("handDepthMul", "0.125"));
        configRenderResMul = Float.parseFloat(shadersConfig.getProperty("renderResMul", "1.0"));
        configShadowResMul = Float.parseFloat(shadersConfig.getProperty("shadowResMul", "1.0"));
        configShadowClipFrustrum = Boolean.parseBoolean(shadersConfig.getProperty("shadowClipFrustrum", "true"));
        configOldLighting = Boolean.parseBoolean(shadersConfig.getProperty("oldLighting", "false"));
        configTexMinFilB = Integer.parseInt(shadersConfig.getProperty("TexMinFilB", "0")) % texMinFilRange;
        configTexMinFilN = Integer.parseInt(shadersConfig.getProperty("TexMinFilN", Integer.toString(configTexMinFilB)))
                % texMinFilRange;
        configTexMinFilS = Integer.parseInt(shadersConfig.getProperty("TexMinFilS", Integer.toString(configTexMinFilB)))
                % texMinFilRange;
        configTexMagFilB = Integer.parseInt(shadersConfig.getProperty("TexMagFilB", "0")) % texMagFilRange;
        configTexMagFilN = Integer.parseInt(shadersConfig.getProperty("TexMagFilN", "0")) % texMagFilRange;
        configTexMagFilS = Integer.parseInt(shadersConfig.getProperty("TexMagFilS", "0")) % texMagFilRange;
        currentshadername = shadersConfig.getProperty("shaderPack", packNameDefault);
        loadShaderPack();
    }

    public static void storeConfig() {
        System.out.println("[Shaders] Save configuration.");
        shadersConfig.setProperty("normalMapEnabled", Boolean.toString(configNormalMap));
        shadersConfig.setProperty("specularMapEnabled", Boolean.toString(configSpecularMap));
        shadersConfig.setProperty("tweakBlockDamage", Boolean.toString(configTweakBlockDamage));
        shadersConfig.setProperty("cloudShadow", Boolean.toString(configCloudShadow));
        shadersConfig.setProperty("handDepthMul", Float.toString(configHandDepthMul));
        shadersConfig.setProperty("renderResMul", Float.toString(configRenderResMul));
        shadersConfig.setProperty("shadowResMul", Float.toString(configShadowResMul));
        shadersConfig.setProperty("shadowClipFrustrum", Boolean.toString(configShadowClipFrustrum));
        shadersConfig.setProperty("oldLighting", Boolean.toString(configOldLighting));
        shadersConfig.setProperty("TexMinFilB", Integer.toString(configTexMinFilB));
        shadersConfig.setProperty("TexMinFilN", Integer.toString(configTexMinFilN));
        shadersConfig.setProperty("TexMinFilS", Integer.toString(configTexMinFilS));
        shadersConfig.setProperty("TexMagFilB", Integer.toString(configTexMagFilB));
        shadersConfig.setProperty("TexMagFilN", Integer.toString(configTexMagFilN));
        shadersConfig.setProperty("TexMagFilS", Integer.toString(configTexMagFilS));
        try {
            FileWriter writer = new FileWriter(configFile);
            shadersConfig.store(writer, null);
            writer.close();
        } catch (Exception ex) {
        }
    }

    public static void setShaderPack(String par1name) {
        currentshadername = par1name;
        shadersConfig.setProperty("shaderPack", par1name);
        // loadShaderPack();
    }

    public static void loadShaderPack() {
        if (shaderPack != null) {
            shaderPack.close();
            shaderPack = null;
        }
        String packName = shadersConfig.getProperty("shaderPack", packNameDefault);
        if (!packName.isEmpty() && !packName.equals(packNameNone)) {
            if (packName.equals(packNameDefault)) {
                shaderPack = new ShaderPackDefault();
            } else {
                try {
                    File packFile = new File(shaderpacksdir, packName);
                    if (packFile.isDirectory()) {
                        shaderPack = new ShaderPackFolder(packName, packFile);
                    } else if (packFile.isFile() && packName.toLowerCase().endsWith(".zip")) {
                        shaderPack = new ShaderPackZip(packName, packFile);
                    }
                } catch (Exception e) {
                }
            }
        }
        if (shaderPack != null) {
            System.out.println("[Shaders] Loaded shaderpack.");
        } else {
            System.out.println("[Shaders] Did not load shaderpack.");
            shaderPack = new ShaderPackNone();
        }
    }

    //	public static void loadShader0()
    //	{
    //		Set<String> shaderfiles = new HashSet<String>();
    //		for (int si = 1; si < 10; si++)
    //		{
    //			shaderfiles.add(programNames[si]+".fsh");
    //			shaderfiles.add(programNames[si]+".vsh");
    //		}
    //
    //		try
    //		{
    //
    //			ZipInputStream inp = new ZipInputStream(new FileInputStream(currentshader));
    //			System.out.println("[Shaders] Shader selected: " + currentshadername);
    //			boolean found = false;
    //
    //			ZipEntry entry = inp.getNextEntry();
    //			while(entry != null )
    //			{
    //				if (entry.isDirectory())
    //				{
    //					if (entry.getName().toLowerCase().contains("shaders/contents/files/shaders/"))
    //					{
    //						found = true;
    //						break;
    //					}
    //				}
    //				entry = inp.getNextEntry();
    //			}
    //
    //			if (!found)
    //			{
    //				inp.close();
    //				inp = new ZipInputStream(new FileInputStream(currentshader));
    //				entry = inp.getNextEntry();
    //				while(entry != null )
    //				{
    //					if (entry.isDirectory())
    //					{
    //						if (entry.getName().toLowerCase().contains("shaders/"))
    //						{
    //							found = true;
    //							break;
    //						}
    //					}
    //					entry = inp.getNextEntry();
    //				}
    //			}
    //
    //			if (!found)
    //			{
    //				inp.close();
    //				inp = new ZipInputStream(new FileInputStream(currentshader));
    //				entry = inp.getNextEntry();
    //			}
    //
    //			byte[] buf = new byte[1024];
    //			int n;
    //			String zipfile;
    //			String zippath = "";
    //			String zippathfile;
    //
    //			FileOutputStream fileoutputstream, fileoutputstream2;
    //			boolean seusrc5tweak = false;
    //
    //			while(entry != null && shaderfiles.size() > 0)
    //			{
    //				zipfile = new File(entry.getName()).getName();
    //				if (shaderfiles.contains(zipfile) && !entry.isDirectory())
    //				{
    //					zippathfile = new File(entry.getName()).getPath();
    //					if (shaderfiles.size() == 18)
    //					{
    //						zippath = zippathfile.substring(0,zippathfile.length()-zipfile.length());
    //					}
    //					if (zippathfile.equals(zippath+zipfile))
    //					{
    //						shaderfiles.remove(zipfile);
    //						if (zipfile.equals("gbuffers_water.vsh") && entry.getCrc() == 2530345120L ) seusrc5tweak = true;
    //
    //						fileoutputstream = new FileOutputStream(shadersdir + File.separator + "temp" + File.separator + zipfile);
    //						while ((n = inp.read(buf, 0, 1024)) > -1) fileoutputstream.write(buf, 0, n);
    //						fileoutputstream.close();
    //						System.out.println("[Shaders] Shader loaded: " + zippathfile);
    //					}
    //				}
    //				entry = inp.getNextEntry();
    //			}
    //			inp.close();
    //
    //			if (seusrc5tweak)
    //			{
    //				new File(shadersdir + File.separator + "temp" + File.separator + "gbuffers_water.fsh").delete();
    //				new File(shadersdir + File.separator + "temp" + File.separator + "gbuffers_water.vsh").delete();
    //				FileInputStream file = new FileInputStream(shadersdir + File.separator + "temp" + File.separator +
    // "gbuffers_textured_lit.fsh");
    //				FileOutputStream file2 = new FileOutputStream(shadersdir + File.separator + "temp" + File.separator +
    // "gbuffers_water.fsh",false);
    //				while ((n = file.read(buf, 0, 1024)) > -1) file2.write(buf, 0, n);
    //				file.close();
    //				file2.close();
    //				file = new FileInputStream(shadersdir + File.separator + "temp" + File.separator +
    // "gbuffers_textured_lit.vsh");
    //				file2 = new FileOutputStream(shadersdir + File.separator + "temp" + File.separator +
    // "gbuffers_water.vsh",false);
    //				while ((n = file.read(buf, 0, 1024)) > -1) file2.write(buf, 0, n);
    //				file.close();
    //				file2.close();
    //				System.out.println("[Shaders] Detected SEUS v10 RC5, copied gbuffers_textured_lit. to gbuffers_water.");
    //			}
    //		}
    //		catch (Exception exc)
    //		{}
    //
    //		isInitialized = false;
    //		if (shaderfiles.size() == 18 ) System.out.println("[Shaders] No Shader found in: " + currentshadername);
    //		else System.out.println("[Shaders] Shader loaded: " + currentshadername);
    //	}

    static ArrayList listofShaders() {
        ArrayList<String> list = new ArrayList();
        list.add(packNameNone);
        list.add(packNameDefault);
        try {
            if (!shaderpacksdir.exists()) shaderpacksdir.mkdir();
            File[] listOfFiles = shaderpacksdir.listFiles();
            for (int i = 0; i < listOfFiles.length; i++) {
                File file = listOfFiles[i];
                String name = file.getName();
                if (file.isDirectory() || file.isFile() && name.toLowerCase().endsWith(".zip")) {
                    list.add(name);
                }
            }
        } catch (Exception e) {
        }
        return list;
    }

    static String versiontostring(int vv) {
        String vs = Integer.toString(vv);
        return Integer.toString(Integer.parseInt(vs.substring(1, 3))) + "."
                + Integer.toString(Integer.parseInt(vs.substring(3, 5))) + "."
                + Integer.toString(Integer.parseInt(vs.substring(5)));
    }

    static void checkOptifine() {
        /*
        try
        {
        	System.out.println("[Shaders] Required OptiFine version : " + ofVersion);
        	String configVersion = (String)(Config.class.getDeclaredField("VERSION").get(null));
        	System.out.println("[Shaders] Detected OptiFine version : "+ configVersion);
        	if (configVersion.equals(ofVersion) )
        	{
        		System.out.println("[Shaders] ShadersMod loaded. version: " + versiontostring(version));
        	}
        	else
        	{
        		System.err.println("[Shaders] Wrong OptiFine version!");
        		System.exit(-1);
        	}
        }
        catch(Exception e)
        {
        	System.err.println("[Shaders] OptiFine missing or wrong version! Install OptiFine "+ofVersion+" first and then the ShadersMod!");
        	System.exit(-1);
        }
        //Tessellator.shaders = true;
        */
    }

    public static int checkFramebufferStatus(String location) {
        int status = glCheckFramebufferStatusEXT(GL_FRAMEBUFFER_EXT);
        if (status != GL_FRAMEBUFFER_COMPLETE_EXT)
            System.err.format("FramebufferStatus 0x%04X at %s\n", status, location);
        return status;
    }

    public static int checkGLError(String location) {
        int errorCode = glGetError();
        if (errorCode != GL_NO_ERROR) {
            boolean skipPrint = false;
            // if (location.equals("updatechunks")) {
            //	if (updateChunksErrorRecorded) {
            //		skipPrint = true;
            //	} else {
            //		updateChunksErrorRecorded = true;
            //	}
            // }
            if (!skipPrint) {
                if (errorCode == GL_INVALID_FRAMEBUFFER_OPERATION_EXT) {
                    int status = glCheckFramebufferStatusEXT(GL_FRAMEBUFFER_EXT);
                    System.err.format(
                            "GL error 0x%04X: %s (Fb status 0x%04X) at %s\n",
                            errorCode, gluErrorString(errorCode), status, location);
                } else {
                    System.err.format("GL error 0x%04X: %s at %s\n", errorCode, gluErrorString(errorCode), location);
                }
            }
        }
        return errorCode;
    }

    public static int checkGLError(String location, String info) {
        int errorCode = glGetError();
        if (errorCode != GL_NO_ERROR) {
            System.err.format("GL error 0x%04x: %s at %s %s\n", errorCode, gluErrorString(errorCode), location, info);
        }
        return errorCode;
    }

    public static int checkGLError(String location, String info1, String info2) {
        int errorCode = glGetError();
        if (errorCode != GL_NO_ERROR) {
            System.err.format(
                    "GL error 0x%04x: %s at %s %s %s\n", errorCode, gluErrorString(errorCode), location, info1, info2);
        }
        return errorCode;
    }

    private static String printChatAndLogError(String str) {
        mc.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText(str));
        System.err.println(str);
        return str;
    }

    public static void printIntBuffer(String title, IntBuffer buf) {
        StringBuilder sb = new StringBuilder(128);
        sb.append(title)
                .append(" [pos ")
                .append(buf.position())
                .append(" lim ")
                .append(buf.limit())
                .append(" cap ")
                .append(buf.capacity())
                .append(" :");
        for (int lim = buf.limit(), i = 0; i < lim; ++i) sb.append(" ").append(buf.get(i));
        sb.append("]");
        System.out.println(sb);
    }

    public static void startup(Minecraft mc) {
        Shaders.mc = mc;
        System.out.println("ShadersMod version " + versionString);
        Shaders.loadConfig();
    }

    private static String toStringYN(boolean b) {
        return b ? "Y" : "N";
    }

    public static void updateBlockLightLevel() {
        if (configOldLighting) {
            blockLightLevel05 = 0.5f;
            blockLightLevel06 = 0.6f;
            blockLightLevel08 = 0.8f;
        } else {
            blockLightLevel05 = 1.0f;
            blockLightLevel06 = 1.0f;
            blockLightLevel08 = 1.0f;
        }
    }

    public static void init() {
        if (!isInitialized) {
            mc = Minecraft.getMinecraft();
            checkGLError("Shaders.init pre");
            capabilities = GLContext.getCapabilities();
            System.out.println("[Shaders] OpenGL 2.0 = " + toStringYN(capabilities.OpenGL20) + "    2.1 = "
                    + toStringYN(capabilities.OpenGL21) + "    3.0 = "
                    + toStringYN(capabilities.OpenGL30) + "    3.2 = "
                    + toStringYN(capabilities.OpenGL32));
            if (!capabilities.OpenGL20) {
                printChatAndLogError("[Shaders] No OpenGL 2.0.");
            }
            if (!capabilities.OpenGL21) {
                printChatAndLogError("[Shaders] No OpenGL 2.1.");
            }
            if (!capabilities.GL_EXT_framebuffer_object) {
                printChatAndLogError("[Shaders] No EXT_framebuffer_object.");
            }
            if (!capabilities.OpenGL20 || !capabilities.GL_EXT_framebuffer_object) {
                System.out.println("[Shaders] Your GPU is not compatible with the Shaders mod.");
                System.exit(-1);
            }

            hasGlGenMipmap = capabilities.OpenGL30;
            dfbDrawBuffers.position(0).limit(MaxDrawBuffers);
            dfbColorTextures.position(0).limit(MaxColorBuffers);
            dfbDepthTextures.position(0).limit(MaxDepthBuffers);
            sfbDrawBuffers.position(0).limit(MaxDrawBuffers);
            sfbDepthTextures.position(0).limit(MaxShadowDepthBuffers);
            sfbColorTextures.position(0).limit(MaxShadowColorBuffers);

            int maxDrawBuffers = glGetInteger(GL_MAX_DRAW_BUFFERS);
            int maxColorAttach = glGetInteger(GL_MAX_COLOR_ATTACHMENTS_EXT);
            System.out.println("[Shaders] GL_MAX_DRAW_BUFFERS = " + maxDrawBuffers);
            System.out.println("[Shaders] GL_MAX_COLOR_ATTACHMENTS_EXT = " + maxColorAttach);
            System.out.println("[Shaders] GL_MAX_TEXTURE_IMAGE_UNITS = " + glGetInteger(GL_MAX_TEXTURE_IMAGE_UNITS));

            usedColorBuffers = 4;
            usedDepthBuffers = 1;
            usedShadowColorBuffers = 0;
            usedShadowDepthBuffers = 0;
            usedColorAttachs = 1;
            usedDrawBuffers = 1;
            Arrays.fill(gbuffersFormat, GL_RGBA);
            Arrays.fill(shadowHardwareFilteringEnabled, false);
            Arrays.fill(shadowMipmapEnabled, false);
            Arrays.fill(shadowFilterNearest, false);
            Arrays.fill(shadowColorMipmapEnabled, false);
            Arrays.fill(shadowColorFilterNearest, false);
            centerDepthSmoothEnabled = false;
            noiseTextureEnabled = false;
            sunPathRotation = 0f;
            shadowIntervalSize = 2.0f;
            aoLevel = 0.8f;
            blockAoLight = 1.0f - aoLevel;
            useEntityAttrib = false;
            useMidTexCoordAttrib = false;
            useMultiTexCoord3Attrib = false;
            waterShadowEnabled = false;
            updateChunksErrorRecorded = false;
            updateBlockLightLevel();

            for (int i = 0; i < ProgramCount; ++i) {
                if (programNames[i] == "") {
                    programsID[i] = programsRef[i] = 0;
                    programsDrawBufSettings[i] = null;
                    programsColorAtmSettings[i] = null;
                    programsCompositeMipmapSetting[i] = 0;
                } else {
                    newDrawBufSetting = null;
                    newColorAtmSetting = null;
                    newCompositeMipmapSetting = 0;
                    int pr = setupProgram(
                            i, "/shaders/" + programNames[i] + ".vsh", "/shaders/" + programNames[i] + ".fsh");
                    programsID[i] = programsRef[i] = pr;
                    programsDrawBufSettings[i] = (pr != 0) ? newDrawBufSetting : null;
                    programsColorAtmSettings[i] = (pr != 0) ? newColorAtmSetting : null;
                    programsCompositeMipmapSetting[i] = (pr != 0) ? newCompositeMipmapSetting : 0;
                }
            }

            Map<String, IntBuffer> drawBuffersMap = new HashMap();
            for (int p = 0; p < ProgramCount; ++p) {
                if (p == ProgramFinal) {
                    programsDrawBuffers[p] = null;
                } else if (programsID[p] == 0) {
                    if (p == ProgramShadow) {
                        programsDrawBuffers[p] = drawBuffersNone;
                    } else {
                        programsDrawBuffers[p] = drawBuffersColorAtt0;
                    }
                } else {
                    String str = programsDrawBufSettings[p];
                    if (str != null) {
                        IntBuffer intbuf = drawBuffersBuffer[p];
                        int numDB = str.length();
                        if (numDB > usedDrawBuffers) {
                            usedDrawBuffers = numDB;
                        }
                        if (numDB > maxDrawBuffers) {
                            numDB = maxDrawBuffers;
                        }
                        programsDrawBuffers[p] = intbuf;
                        intbuf.limit(numDB);
                        for (int i = 0; i < numDB; ++i) {
                            int d = GL_NONE;
                            if (str.length() > i) {
                                int ca = str.charAt(i) - '0';
                                if (p != ProgramShadow) {
                                    if (ca >= 0 && ca <= 7) {
                                        d = ca + GL_COLOR_ATTACHMENT0_EXT;
                                        if (ca > usedColorAttachs) {
                                            usedColorAttachs = ca;
                                        }
                                        if (ca > usedColorBuffers) {
                                            usedColorBuffers = ca;
                                        }
                                    }
                                } else {
                                    if (ca >= 0 && ca <= 1) {
                                        d = ca + GL_COLOR_ATTACHMENT0_EXT;
                                        if (ca > usedShadowColorBuffers) {
                                            usedShadowColorBuffers = ca;
                                        }
                                    }
                                }
                            }
                            intbuf.put(i, d);
                        }
                    } else {
                        if (p != ProgramShadow) {
                            programsDrawBuffers[p] = dfbDrawBuffers;
                            usedDrawBuffers = usedColorBuffers;
                        } else {
                            programsDrawBuffers[p] = sfbDrawBuffers;
                        }
                    }
                }
            }

            // if (programIDCopyDepth==0)
            //	programIDCopyDepth = setupProgramCopyDepth();

            // TO DO: add color attachment option
            usedColorAttachs = usedColorBuffers;

            shadowPassInterval = (usedShadowDepthBuffers > 0) ? 1 : 0;
            shouldSkipDefaultShadow = (usedShadowDepthBuffers > 0);

            dfbDrawBuffers.position(0).limit(usedDrawBuffers);
            dfbColorTextures.position(0).limit(usedColorBuffers);
            // dfbRenderBuffers.limit(colorAttachments);

            for (int i = 0; i < usedDrawBuffers; ++i) {
                dfbDrawBuffers.put(i, GL_COLOR_ATTACHMENT0_EXT + i);
            }

            if (usedDrawBuffers > maxDrawBuffers) {
                printChatAndLogError("[Shaders] Not enough draw buffers! Requires " + usedDrawBuffers + ".  Has "
                        + maxDrawBuffers + ".");
            }
            // if (usedColorBuffers > maxColorAttach) {
            //	printChatAndLogError("[Shaders] Not enough color attachment! Requires "+ usedColorBuffers + ".  Has " +
            // maxColorAttach + ".");
            // }

            sfbDrawBuffers.position(0).limit(usedShadowColorBuffers);
            for (int i = 0; i < usedShadowColorBuffers; ++i) {
                sfbDrawBuffers.put(i, GL_COLOR_ATTACHMENT0_EXT + i);
            }

            // Use programBackups for missing programs
            for (int i = 0; i < ProgramCount; ++i) {
                int n = i;
                while (programsID[n] == 0 && programBackups[n] != n) n = programBackups[n];
                if (n != i && i != ProgramShadow) {
                    programsID[i] = programsID[n];
                    programsDrawBufSettings[i] = programsDrawBufSettings[n];
                    programsDrawBuffers[i] = programsDrawBuffers[n];
                }
            }

            // printIntBuffer("dfb drawbuffers",dfbDrawBuffers);
            // printIntBuffer("sfb drawbuffers",sfbDrawBuffers);
            // printIntBuffer("pgshadow drawbf",programsDrawBuffers[ProgramShadow]);

            // setup scene frame buffer and textures
            resize();

            // setup shadow frame buffer and texture
            resizeShadow();

            if (noiseTextureEnabled) setupNoiseTexture();

            if (defaultTexture == null) defaultTexture = ShadersTex.createDefaultTexture();
            // TextureNM texBlocks = (TextureNM)textureManager.textureMapBlocks.getTexture();
            // TextureNM texItems = (TextureNM)textureManager.textureMapBlocks.getTexture();
            // terrainTextureIdArray = new int[] { texBlocks.getGlTextureId(), texBlocks.normalMap.getGlTextureId(),
            // texBlocks.specularMap.getGlTextureId() };
            // textureIdMap.put(terrainTextureIdArray[0], terrainTextureIdArray);
            // int[] itemsTextureIdArray;
            // itemsTextureIdArray = new int[] { texItems.getGlTextureId(), texItems.normalMap.getGlTextureId(),
            // texItems.specularMap.getGlTextureId() };
            // textureIdMap.put(itemsTextureIdArray[0], itemsTextureIdArray);

            isInitialized = true;

            resetDisplayList();
            if (notFirstInit) {
                mc.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText("Shaders initialized."));
            }
            checkGLError("Shaders.init");
        }
    }

    public static void resetDisplayList() {
        ++numberResetDisplayList;
        System.out.println("Reset model renderers");
        if (Shaders.useMidTexCoordAttrib || Shaders.useMultiTexCoord3Attrib) {
            Iterator<Render> it =
                    RenderManager.instance.entityRenderMap.values().iterator();
            while (it.hasNext()) {
                Render ren = it.next();
                if (ren instanceof RendererLivingEntity) {
                    RendererLivingEntity rle = (RendererLivingEntity) ren;
                    // System.out.format("Reset %s\n", rle.toString());
                    resetDisplayListModel(rle.mainModel);
                    resetDisplayListModel(rle.renderPassModel);
                }
            }
        }
        System.out.println("Reset world renderers");
        mc.renderGlobal.loadRenderers();
    }

    public static void resetDisplayListModel(ModelBase mbase) {
        if (mbase != null) {
            Iterator it = mbase.boxList.iterator();
            while (it.hasNext()) {
                Object obj = it.next();
                if (obj instanceof ModelRenderer) {
                    resetDisplayListModelRenderer((ModelRenderer) obj);
                }
            }
        }
    }

    public static void resetDisplayListModelRenderer(ModelRenderer mrr) {
        mrr.resetDisplayList();
        //		if (mrr.compiled)
        //		{
        //			GLAllocation.deleteDisplayLists(mrr.displayList);
        //			mrr.displayList = 0;
        //			mrr.compiled = false;
        //			if (mrr instanceof ModelRotationRenderer) {
        //				ModelRotationRenderer mrt = (ModelRotationRenderer)mrr;
        //				mrt.compiled = false;
        //			}
        //		}
        if (mrr.childModels != null) {
            for (int i = 0, n = mrr.childModels.size(); i < n; ++i) {
                resetDisplayListModelRenderer((ModelRenderer) mrr.childModels.get(i));
            }
        }
    }

    // ----------------------------------------

    //	private static final String shaderCodeCopyDepthV =
    //			"#version 120\n"+
    //			"varying vec4 texcoord;\n"+
    //			"void main()\n"+
    //			"{\n"+
    //			"    texcoord = gl_MultiTexCoord0;\n"+
    //			"    gl_Position = ftransform();\n"+
    //			"}\n"
    //			;
    //	private static final String shaderCodeCopyDepthF =
    //	"#version 120\n"+
    //	"varying vec4 texcoord;\n"+
    //	"uniform sampler2D depthtex0;\n"+
    //	"void main()\n"+
    //	"{\n"+
    //	"    gl_FragDepth = texture2D(depthtex0,texcoord.st).r;\n"+
    //	"    gl_FragColor = texture2D(depthtex0,texcoord.st);\n"+
    //	"}\n"
    //	;
    //
    //	private static int setupProgramCopyDepth() {
    //		int program = glCreateProgramObjectARB();
    //		if (program !=0) {
    //			int vShader = createVertShaderFromCode(shaderCodeCopyDepthV);
    //			int fShader = createFragShaderFromCode(shaderCodeCopyDepthF);
    //			if (vShader != 0 || fShader != 0) {
    //				if (vShader != 0) {
    //					glAttachObjectARB(program, vShader);
    //				}
    //				if (fShader != 0) {
    //					glAttachObjectARB(program, fShader);
    //				}
    //				glLinkProgramARB(program);
    //				if (vShader != 0) {
    //					glDetachObjectARB(program, vShader);
    //					glDeleteObjectARB(vShader);
    //				}
    //				if (fShader != 0) {
    //					glDetachObjectARB(program, fShader);
    //					glDeleteObjectARB(fShader);
    //				}
    //				int valid = glGetProgrami(program, GL_VALIDATE_STATUS); // glGetProgrami in LWJGL 3.0+
    //				if (valid == GL_TRUE) {
    //					System.out.println("Program copydepth created");
    //				} else {
    //					printChatAndLogError("[Shaders] Error : Invalid program copydepth");
    //					glDeleteObjectARB(program);
    //					program = 0;
    //				}
    //			} else {
    //				glDeleteObjectARB(program);
    //				program = 0;
    //			}
    //		}
    //		return program;
    //	}
    //
    //
    //	private static int createVertShaderFromCode(String code) {
    //		int vertShader = glCreateShaderObjectARB(GL_VERTEX_SHADER_ARB);
    //		if (vertShader == 0) {
    //			return 0;
    //		}
    //		glShaderSourceARB(vertShader, code);
    //		glCompileShaderARB(vertShader);
    //		printLogInfo(vertShader,"copydepth vert");
    //		return vertShader;
    //	}
    //
    //
    //	private static int createFragShaderFromCode(String code) {
    //		int fragShader = glCreateShaderObjectARB(GL_FRAGMENT_SHADER_ARB);
    //		if (fragShader == 0) {
    //			return 0;
    //		}
    //		glShaderSourceARB(fragShader, code);
    //		glCompileShaderARB(fragShader);
    //		printLogInfo(fragShader,"copydepth frag");
    //		return fragShader;
    //	}

    private static int setupProgram(int program, String vShaderPath, String fShaderPath) {
        checkGLError("pre setupProgram");
        int programid = glCreateProgramObjectARB();
        checkGLError("create");
        if (programid != 0) {
            progUseEntityAttrib = false;
            progUseMidTexCoordAttrib = false;
            int vShader = createVertShader(vShaderPath);
            int fShader = createFragShader(fShaderPath);
            checkGLError("create");
            if (vShader != 0 || fShader != 0) {
                if (vShader != 0) {
                    glAttachObjectARB(programid, vShader);
                    checkGLError("attach");
                }
                if (fShader != 0) {
                    glAttachObjectARB(programid, fShader);
                    checkGLError("attach");
                }
                if (progUseEntityAttrib) {
                    glBindAttribLocationARB(programid, entityAttrib, "mc_Entity");
                    checkGLError("mc_Entity");
                }
                if (progUseMidTexCoordAttrib) {
                    glBindAttribLocationARB(programid, midTexCoordAttrib, "mc_midTexCoord");
                    checkGLError("mc_midTexCoord");
                }
                glLinkProgramARB(programid);
                if (vShader != 0) {
                    glDetachObjectARB(programid, vShader);
                    glDeleteObjectARB(vShader);
                }
                if (fShader != 0) {
                    glDetachObjectARB(programid, fShader);
                    glDeleteObjectARB(fShader);
                }
                programsID[program] = programid;
                useProgram(program);
                glValidateProgramARB(programid);
                useProgram(ProgramNone);
                printLogInfo(programid, vShaderPath + "," + fShaderPath);
                int valid = glGetProgrami(programid, GL_VALIDATE_STATUS);
                if (valid == GL_TRUE) {
                    System.out.println("Program " + programNames[program] + " loaded");
                } else {
                    printChatAndLogError("[Shaders] Error : Invalid program " + programNames[program]);
                    glDeleteObjectARB(programid);
                    programid = 0;
                }
            } else {
                glDeleteObjectARB(programid);
                programid = 0;
            }
        }
        return programid;
    }

    private static int createVertShader(String filename) {
        int vertShader = glCreateShaderObjectARB(GL_VERTEX_SHADER_ARB);
        if (vertShader == 0) {
            return 0;
        }
        String vertexCode = "";
        String line;

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(shaderPack.getResourceAsStream(filename)));
        } catch (Exception e) {
            try {
                reader = new BufferedReader(new FileReader(new File(filename)));
            } catch (Exception e2) {
                // System.out.println("Couldn't open " + filename);
                glDeleteObjectARB(vertShader);
                return 0;
            }
        }

        if (reader != null)
            try {
                while ((line = reader.readLine()) != null) {
                    vertexCode += line + "\n";
                    if (line.matches("attribute [_a-zA-Z0-9]+ mc_Entity.*")) {
                        useEntityAttrib = true;
                        progUseEntityAttrib = true;
                    } else if (line.matches("attribute [_a-zA-Z0-9]+ mc_midTexCoord.*")) {
                        useMidTexCoordAttrib = true;
                        progUseMidTexCoordAttrib = true;
                    } else if (line.matches(".*gl_MultiTexCoord3.*")) {
                        useMultiTexCoord3Attrib = true;
                    }
                }
            } catch (Exception e) {
                System.out.println("Couldn't read " + filename + "!");
                e.printStackTrace();
                glDeleteObjectARB(vertShader);
                return 0;
            }

        if (reader != null)
            try {
                reader.close();
            } catch (Exception e) {
                System.out.println("Couldn't close " + filename + "!");
            }

        glShaderSourceARB(vertShader, vertexCode);
        glCompileShaderARB(vertShader);
        printLogInfo(vertShader, filename);
        return vertShader;
    }

    private static Pattern gbufferFormatPattern =
            Pattern.compile("[ \t]*const[ \t]*int[ \t]*(\\w+)Format[ \t]*=[ \t]*([RGBA81632F]*)[ \t]*;.*");
    private static Pattern gbufferMipmapEnabledPattern =
            Pattern.compile("[ \t]*const[ \t]*bool[ \t]*(\\w+)MipmapEnabled[ \t]*=[ \t]*true[ \t]*;.*");

    private static int createFragShader(String filename) {
        int fragShader = glCreateShaderObjectARB(GL_FRAGMENT_SHADER_ARB);
        if (fragShader == 0) {
            return 0;
        }
        StringBuilder fragCode = new StringBuilder(1048576);
        String line;

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(shaderPack.getResourceAsStream(filename)));
        } catch (Exception e) {
            try {
                reader = new BufferedReader(new FileReader(new File(filename)));
            } catch (Exception e2) {
                // System.out.println("Couldn't open " + filename);
                glDeleteObjectARB(fragShader);
                return 0;
            }
        }

        if (reader != null)
            try {
                while ((line = reader.readLine()) != null) {
                    fragCode.append(line).append('\n');
                    if (line.matches("#version .*")) {

                    } else if (line.matches("uniform [ _a-zA-Z0-9]+ shadow;.*")) {
                        if (usedShadowDepthBuffers < 1) usedShadowDepthBuffers = 1;
                    } else if (line.matches("uniform [ _a-zA-Z0-9]+ watershadow;.*")) {
                        waterShadowEnabled = true;
                        if (usedShadowDepthBuffers < 2) usedShadowDepthBuffers = 2;
                    } else if (line.matches("uniform [ _a-zA-Z0-9]+ shadowtex0;.*")) {
                        if (usedShadowDepthBuffers < 1) usedShadowDepthBuffers = 1;
                    } else if (line.matches("uniform [ _a-zA-Z0-9]+ shadowtex1;.*")) {
                        if (usedShadowDepthBuffers < 2) usedShadowDepthBuffers = 2;
                    } else if (line.matches("uniform [ _a-zA-Z0-9]+ shadowcolor;.*")) {
                        if (usedShadowColorBuffers < 1) usedShadowColorBuffers = 1;
                    } else if (line.matches("uniform [ _a-zA-Z0-9]+ shadowcolor0;.*")) {
                        if (usedShadowColorBuffers < 1) usedShadowColorBuffers = 1;
                    } else if (line.matches("uniform [ _a-zA-Z0-9]+ shadowcolor1;.*")) {
                        if (usedShadowColorBuffers < 2) usedShadowColorBuffers = 2;
                    } else if (line.matches("uniform [ _a-zA-Z0-9]+ depthtex0;.*")) {
                        if (usedDepthBuffers < 1) usedDepthBuffers = 1;
                    } else if (line.matches("uniform [ _a-zA-Z0-9]+ depthtex1;.*")) {
                        if (usedDepthBuffers < 2) usedDepthBuffers = 2;
                    } else if (line.matches("uniform [ _a-zA-Z0-9]+ depthtex2;.*")) {
                        if (usedDepthBuffers < 3) usedDepthBuffers = 3;
                    } else if (line.matches("uniform [ _a-zA-Z0-9]+ gdepth;.*")) {
                        if (gbuffersFormat[1] == GL_RGBA) gbuffersFormat[1] = GL_RGBA32F;
                    } else if (usedColorBuffers < 5 && line.matches("uniform [ _a-zA-Z0-9]+ gaux1;.*")) {
                        usedColorBuffers = 5;
                    } else if (usedColorBuffers < 6 && line.matches("uniform [ _a-zA-Z0-9]+ gaux2;.*")) {
                        usedColorBuffers = 6;
                    } else if (usedColorBuffers < 7 && line.matches("uniform [ _a-zA-Z0-9]+ gaux3;.*")) {
                        usedColorBuffers = 7;
                    } else if (usedColorBuffers < 8 && line.matches("uniform [ _a-zA-Z0-9]+ gaux4;.*")) {
                        usedColorBuffers = 8;
                    } else if (usedColorBuffers < 5 && line.matches("uniform [ _a-zA-Z0-9]+ colortex4;.*")) {
                        usedColorBuffers = 5;
                    } else if (usedColorBuffers < 6 && line.matches("uniform [ _a-zA-Z0-9]+ colortex5;.*")) {
                        usedColorBuffers = 6;
                    } else if (usedColorBuffers < 7 && line.matches("uniform [ _a-zA-Z0-9]+ colortex6;.*")) {
                        usedColorBuffers = 7;
                    } else if (usedColorBuffers < 8 && line.matches("uniform [ _a-zA-Z0-9]+ colortex7;.*")) {
                        usedColorBuffers = 8;

                    } else if (usedColorBuffers < 8 && line.matches("uniform [ _a-zA-Z0-9]+ centerDepthSmooth;.*")) {
                        centerDepthSmoothEnabled = true;

                        // Shadow resolution
                    } else if (line.matches("/\\* SHADOWRES:[0-9]+ \\*/.*")) {
                        String[] parts = line.split("(:| )", 4);
                        System.out.println("Shadow map resolution: " + parts[2]);
                        spShadowMapWidth = spShadowMapHeight = Integer.parseInt(parts[2]);
                        shadowMapWidth = shadowMapHeight = Math.round(spShadowMapWidth * configShadowResMul);

                    } else if (line.matches(
                            "[ \t]*const[ \t]*int[ \t]*shadowMapResolution[ \t]*=[ \t]*-?[0-9.]+f?;.*")) {
                        String[] parts = line.split("(=[ \t]*|;)");
                        System.out.println("Shadow map resolution: " + parts[1]);
                        spShadowMapWidth = spShadowMapHeight = Integer.parseInt(parts[1]);
                        shadowMapWidth = shadowMapHeight = Math.round(spShadowMapWidth * configShadowResMul);

                    } else if (line.matches("/\\* SHADOWFOV:[0-9\\.]+ \\*/.*")) {
                        String[] parts = line.split("(:| )", 4);
                        System.out.println("Shadow map field of view: " + parts[2]);
                        shadowMapFOV = Float.parseFloat(parts[2]);
                        shadowMapIsOrtho = false;

                        // Shadow distance
                    } else if (line.matches("/\\* SHADOWHPL:[0-9\\.]+ \\*/.*")) {
                        String[] parts = line.split("(:| )", 4);
                        System.out.println("Shadow map half-plane: " + parts[2]);
                        shadowMapHalfPlane = Float.parseFloat(parts[2]);
                        shadowMapIsOrtho = true;

                    } else if (line.matches("[ \t]*const[ \t]*float[ \t]*shadowDistance[ \t]*=[ \t]*-?[0-9.]+f?;.*")) {
                        String[] parts = line.split("(=[ \t]*|;)");
                        System.out.println("Shadow map distance: " + parts[1]);
                        shadowMapHalfPlane = Float.parseFloat(parts[1]);
                        shadowMapIsOrtho = true;

                    } else if (line.matches(
                            "[ \t]*const[ \t]*float[ \t]*shadowIntervalSize[ \t]*=[ \t]*-?[0-9.]+f?;.*")) {
                        String[] parts = line.split("(=[ \t]*|;)");
                        System.out.println("Shadow map interval size: " + parts[1]);
                        shadowIntervalSize = Float.parseFloat(parts[1]);

                    } else if (line.matches(
                            "[ \t]*const[ \t]*bool[ \t]*generateShadowMipmap[ \t]*=[ \t]*true[ \t]*;.*")) {
                        System.out.println("Generate shadow mipmap");
                        Arrays.fill(shadowMipmapEnabled, true);

                    } else if (line.matches(
                            "[ \t]*const[ \t]*bool[ \t]*generateShadowColorMipmap[ \t]*=[ \t]*true[ \t]*;.*")) {
                        System.out.println("Generate shadow color mipmap");
                        Arrays.fill(shadowColorMipmapEnabled, true);

                    } else if (line.matches(
                            "[ \t]*const[ \t]*bool[ \t]*shadowHardwareFiltering[ \t]*=[ \t]*true[ \t]*;.*")) {
                        System.out.println("Hardware shadow filtering enabled.");
                        Arrays.fill(shadowHardwareFilteringEnabled, true);

                    } else if (line.matches(
                            "[ \t]*const[ \t]*bool[ \t]*shadowHardwareFiltering0[ \t]*=[ \t]*true[ \t]*;.*")) {
                        System.out.println("shadowHardwareFiltering0");
                        shadowHardwareFilteringEnabled[0] = true;

                    } else if (line.matches(
                            "[ \t]*const[ \t]*bool[ \t]*shadowHardwareFiltering1[ \t]*=[ \t]*true[ \t]*;.*")) {
                        System.out.println("shadowHardwareFiltering1");
                        shadowHardwareFilteringEnabled[1] = true;

                    } else if (line.matches(
                            "[ \t]*const[ \t]*bool[ \t]*(shadowtex0Mipmap|shadowtexMipmap)[ \t]*=[ \t]*true[ \t]*;.*")) {
                        System.out.println("shadowtex0Mipmap");
                        shadowMipmapEnabled[0] = true;

                    } else if (line.matches(
                            "[ \t]*const[ \t]*bool[ \t]*(shadowtex1Mipmap)[ \t]*=[ \t]*true[ \t]*;.*")) {
                        System.out.println("shadowtex1Mipmap");
                        shadowMipmapEnabled[1] = true;

                    } else if (line.matches(
                            "[ \t]*const[ \t]*bool[ \t]*(shadowcolor0Mipmap|shadowColor0Mipmap)[ \t]*=[ \t]*true[ \t]*;.*")) {
                        System.out.println("shadowcolor0Mipmap");
                        shadowColorMipmapEnabled[0] = true;

                    } else if (line.matches(
                            "[ \t]*const[ \t]*bool[ \t]*(shadowcolor1Mipmap|shadowColor1Mipmap)[ \t]*=[ \t]*true[ \t]*;.*")) {
                        System.out.println("shadowcolor1Mipmap");
                        shadowColorMipmapEnabled[1] = true;

                    } else if (line.matches(
                            "[ \t]*const[ \t]*bool[ \t]*(shadowtex0Nearest|shadowtexNearest|shadow0MinMagNearest)[ \t]*=[ \t]*true[ \t]*;.*")) {
                        System.out.println("shadowtex0Nearest");
                        shadowFilterNearest[0] = true;

                    } else if (line.matches(
                            "[ \t]*const[ \t]*bool[ \t]*(shadowtex1Nearest|shadow1MinMagNearest)[ \t]*=[ \t]*true[ \t]*;.*")) {
                        System.out.println("shadowtex1Nearest");
                        shadowFilterNearest[1] = true;

                    } else if (line.matches(
                            "[ \t]*const[ \t]*bool[ \t]*(shadowcolor0Nearest|shadowColor0Nearest|shadowColor0MinMagNearest)[ \t]*=[ \t]*true[ \t]*;.*")) {
                        System.out.println("shadowcolor0Nearest");
                        shadowColorFilterNearest[0] = true;

                    } else if (line.matches(
                            "[ \t]*const[ \t]*bool[ \t]*(shadowcolor1Nearest|shadowColor1Nearest|shadowColor1MinMagNearest)[ \t]*=[ \t]*true[ \t]*;.*")) {
                        System.out.println("shadowcolor1Nearest");
                        shadowColorFilterNearest[1] = true;

                        // Wetness half life
                    } else if (line.matches("/\\* WETNESSHL:[0-9\\.]+ \\*/.*")) {
                        String[] parts = line.split("(:| )", 4);
                        System.out.println("Wetness halflife: " + parts[2]);
                        wetnessHalfLife = Float.parseFloat(parts[2]);

                    } else if (line.matches("[ \t]*const[ \t]*float[ \t]*wetnessHalflife[ \t]*=[ \t]*-?[0-9.]+f?;.*")) {
                        String[] parts = line.split("(=[ \t]*|;)");
                        System.out.println("Wetness halflife: " + parts[1]);
                        wetnessHalfLife = Float.parseFloat(parts[1]);

                        // Dryness halflife
                    } else if (line.matches("/\\* DRYNESSHL:[0-9\\.]+ \\*/.*")) {
                        String[] parts = line.split("(:| )", 4);
                        System.out.println("Dryness halflife: " + parts[2]);
                        drynessHalfLife = Float.parseFloat(parts[2]);

                    } else if (line.matches("[ \t]*const[ \t]*float[ \t]*drynessHalflife[ \t]*=[ \t]*-?[0-9.]+f?;.*")) {
                        String[] parts = line.split("(=[ \t]*|;)");
                        System.out.println("Dryness halflife: " + parts[1]);
                        drynessHalfLife = Float.parseFloat(parts[1]);

                        // Eye brightness halflife
                    } else if (line.matches(
                            "[ \t]*const[ \t]*float[ \t]*eyeBrightnessHalflife[ \t]*=[ \t]*-?[0-9.]+f?;.*")) {
                        String[] parts = line.split("(=[ \t]*|;)");
                        System.out.println("Eye brightness halflife: " + parts[1]);
                        eyeBrightnessHalflife = Float.parseFloat(parts[1]);

                        // Center depth halflife
                    } else if (line.matches(
                            "[ \t]*const[ \t]*float[ \t]*centerDepthHalflife[ \t]*=[ \t]*-?[0-9.]+f?;.*")) {
                        String[] parts = line.split("(=[ \t]*|;)");
                        System.out.println("Center depth halflife: " + parts[1]);
                        centerDepthSmoothHalflife = Float.parseFloat(parts[1]);

                        // Sun path rotation
                    } else if (line.matches("[ \t]*const[ \t]*float[ \t]*sunPathRotation[ \t]*=[ \t]*-?[0-9.]+f?;.*")) {
                        String[] parts = line.split("(=[ \t]*|;)");
                        System.out.println("Sun path rotation: " + parts[1]);
                        sunPathRotation = Float.parseFloat(parts[1]);

                        // Ambient occlusion level
                    } else if (line.matches(
                            "[ \t]*const[ \t]*float[ \t]*ambientOcclusionLevel[ \t]*=[ \t]*-?[0-9.]+f?;.*")) {
                        String[] parts = line.split("(=[ \t]*|;)");
                        System.out.println("AO Level: " + parts[1]);
                        aoLevel = Float.parseFloat(parts[1]);
                        blockAoLight = 1.0f - aoLevel;

                        // super sampling
                    } else if (line.matches(
                            "[ \t]*const[ \t]*int[ \t]*superSamplingLevel[ \t]*=[ \t]*-?[0-9.]+f?;.*")) {
                        String[] parts = line.split("(=[ \t]*|;)");
                        int ssaa = Integer.parseInt(parts[1]);
                        if (ssaa > 1) {
                            System.out.println("Super sampling level: " + ssaa + "x");
                            superSamplingLevel = ssaa;
                        } else {
                            superSamplingLevel = 1;
                        }

                        // noise texture
                    } else if (line.matches(
                            "[ \t]*const[ \t]*int[ \t]*noiseTextureResolution[ \t]*=[ \t]*-?[0-9.]+f?;.*")) {
                        String[] parts = line.split("(=[ \t]*|;)");
                        System.out.println("Noise texture enabled");
                        System.out.println("Noise texture resolution: " + parts[1]);
                        noiseTextureResolution = Integer.parseInt(parts[1]);
                        noiseTextureEnabled = true;

                    } else if (line.matches(
                            "[ \t]*const[ \t]*int[ \t]*\\w+Format[ \t]*=[ \t]*[RGBA81632F]*[ \t]*;.*")) {
                        Matcher m = gbufferFormatPattern.matcher(line);
                        m.matches();
                        String name = m.group(1);
                        String value = m.group(2);
                        int bufferindex = getBufferIndexFromString(name);
                        int format = getTextureFormatFromString(value);
                        if (bufferindex >= 0 && format != 0) {
                            gbuffersFormat[bufferindex] = format;
                            System.out.format("%s format: %s\n", name, value);
                        }
                        // gaux4
                    } else if (line.matches("/\\* GAUX4FORMAT:RGBA32F \\*/.*")) {
                        System.out.println("gaux4 format : RGB32AF");
                        gbuffersFormat[7] = GL_RGBA32F;
                    } else if (line.matches("/\\* GAUX4FORMAT:RGB32F \\*/.*")) {
                        System.out.println("gaux4 format : RGB32F");
                        gbuffersFormat[7] = GL_RGB32F;
                    } else if (line.matches("/\\* GAUX4FORMAT:RGB16 \\*/.*")) {
                        System.out.println("gaux4 format : RGB16");
                        gbuffersFormat[7] = GL_RGB16;

                        // Mipmap stuff
                    } else if (line.matches("[ \t]*const[ \t]*bool[ \t]*\\w+MipmapEnabled[ \t]*=[ \t]*true[ \t]*;.*")) {
                        if (filename.matches(".*composite[0-9]?.fsh") || filename.matches(".*final.fsh")) {
                            Matcher m = gbufferMipmapEnabledPattern.matcher(line);
                            m.matches();
                            String name = m.group(1);
                            // String value =m.group(2);
                            int bufferindex = getBufferIndexFromString(name);
                            if (bufferindex >= 0) {
                                newCompositeMipmapSetting |= (1 << bufferindex);
                                System.out.format("%s mipmap enabled for %s\n", name, filename);
                            }
                        }
                    } else if (line.matches("/\\* DRAWBUFFERS:[0-7N]* \\*/.*")) {
                        String[] parts = line.split("(:| )", 4);
                        newDrawBufSetting = parts[2];
                    }
                }
            } catch (Exception e) {
                System.out.println("Couldn't read " + filename + "!");
                e.printStackTrace();
                glDeleteObjectARB(fragShader);
                return 0;
            }

        if (reader != null)
            try {
                reader.close();
            } catch (Exception e) {
                System.out.println("Couldn't close " + filename + "!");
            }

        glShaderSourceARB(fragShader, fragCode);
        glCompileShaderARB(fragShader);
        printLogInfo(fragShader, filename);
        return fragShader;
    }

    private static boolean printLogInfo(int obj, String name) {
        IntBuffer iVal = BufferUtils.createIntBuffer(1);
        glGetObjectParameterARB(obj, GL_OBJECT_INFO_LOG_LENGTH_ARB, iVal);

        int length = iVal.get();
        if (length > 1) {
            ByteBuffer infoLog = BufferUtils.createByteBuffer(length);
            iVal.flip();
            glGetInfoLogARB(obj, iVal, infoLog);
            byte[] infoBytes = new byte[length];
            infoLog.get(infoBytes);
            if (infoBytes[length - 1] == 0) infoBytes[length - 1] = 10;
            String out = new String(infoBytes);
            System.out.println("Info log: " + name + "\n" + out);
            return false;
        }
        return true;
    }

    public static void setDrawBuffers(IntBuffer drawBuffers) {
        if (activeDrawBuffers != drawBuffers) {
            // printIntBuffer("setDrawBuffers", drawBuffers);
            activeDrawBuffers = drawBuffers;
            glDrawBuffers(drawBuffers);
        } else {
            // printIntBuffer("setDrawBf skip", drawBuffers);
        }
    }

    public static void useProgram(int program) {
        // System.out.println((new StringBuilder(32)).append("useProgram ").append(programNames[program]));
        Shaders.checkGLError("pre-useProgram");
        if (isShadowPass) {
            program = ProgramShadow;
            if (programsID[ProgramShadow] == 0) {
                normalMapEnabled = false;
                return;
            }
        }
        if (activeProgram == program) {
            return;
        }
        activeProgram = program;
        glUseProgramObjectARB(programsID[program]);
        if (programsID[program] == 0) {
            normalMapEnabled = false;
            return;
        }
        if (checkGLError("useProgram ", programNames[program]) != 0) {
            programsID[program] = 0;
        }
        IntBuffer drawBuffers = programsDrawBuffers[program];
        if (isRenderingDfb) {
            setDrawBuffers(drawBuffers);
            checkGLError(programNames[program], " draw buffers = ", programsDrawBufSettings[program]);
            // SMCLog.info("%s",programNames[program] + " draw buffers = " + programsDrawBufSettings[program]);
        }
        activeCompositeMipmapSetting = programsCompositeMipmapSetting[program];
        uniformEntityHurt = glGetUniformLocationARB(programsID[activeProgram], "entityHurt");
        uniformEntityFlash = glGetUniformLocationARB(programsID[activeProgram], "entityFlash");
        switch (program) {
            case ProgramBasic:
            case ProgramTextured:
            case ProgramTexturedLit:
            case ProgramSkyBasic:
            case ProgramSkyTextured:
            case ProgramTerrain:
            case ProgramWater:
            case ProgramEntities:
            case ProgramHand:
            case ProgramWeather:
                normalMapEnabled = true;
                setProgramUniform1i("texture", 0);
                setProgramUniform1i("lightmap", 1);
                setProgramUniform1i("normals", 2);
                setProgramUniform1i("specular", 3);
                setProgramUniform1i("shadow", waterShadowEnabled ? 5 : 4);
                setProgramUniform1i("watershadow", 4);
                setProgramUniform1i("shadowtex0", 4);
                setProgramUniform1i("shadowtex1", 5);
                setProgramUniform1i("depthtex0", 6);
                setProgramUniform1i("depthtex1", 12);
                setProgramUniform1i("shadowcolor", 13);
                setProgramUniform1i("shadowcolor0", 13);
                setProgramUniform1i("shadowcolor1", 14);
                setProgramUniform1i("noisetex", 15);
                break;
            case ProgramComposite:
            case ProgramComposite1:
            case ProgramComposite2:
            case ProgramComposite3:
            case ProgramComposite4:
            case ProgramComposite5:
            case ProgramComposite6:
            case ProgramComposite7:
            case ProgramFinal:
                normalMapEnabled = false;
                setProgramUniform1i("gcolor", 0);
                setProgramUniform1i("gdepth", 1);
                setProgramUniform1i("gnormal", 2);
                setProgramUniform1i("composite", 3);
                setProgramUniform1i("gaux1", 7);
                setProgramUniform1i("gaux2", 8);
                setProgramUniform1i("gaux3", 9);
                setProgramUniform1i("gaux4", 10);
                setProgramUniform1i("colortex0", 0);
                setProgramUniform1i("colortex1", 1);
                setProgramUniform1i("colortex2", 2);
                setProgramUniform1i("colortex3", 3);
                setProgramUniform1i("colortex4", 7);
                setProgramUniform1i("colortex5", 8);
                setProgramUniform1i("colortex6", 9);
                setProgramUniform1i("colortex7", 10);
                setProgramUniform1i("shadow", waterShadowEnabled ? 5 : 4);
                setProgramUniform1i("watershadow", 4);
                setProgramUniform1i("shadowtex0", 4);
                setProgramUniform1i("shadowtex1", 5);
                setProgramUniform1i("gdepthtex", 6);
                setProgramUniform1i("depthtex0", 6);
                setProgramUniform1i("depthtex1", 11);
                setProgramUniform1i("depthtex2", 12);
                setProgramUniform1i("shadowcolor", 13);
                setProgramUniform1i("shadowcolor0", 13);
                setProgramUniform1i("shadowcolor1", 14);
                setProgramUniform1i("noisetex", 15);
                break;
            case ProgramShadow:
                setProgramUniform1i("tex", 0);
                setProgramUniform1i("texture", 0);
                setProgramUniform1i("lightmap", 1);
                setProgramUniform1i("normals", 2);
                setProgramUniform1i("specular", 3);
                setProgramUniform1i("shadow", waterShadowEnabled ? 5 : 4);
                setProgramUniform1i("watershadow", 4);
                setProgramUniform1i("shadowtex0", 4);
                setProgramUniform1i("shadowtex1", 5);
                setProgramUniform1i("shadowcolor", 13);
                setProgramUniform1i("shadowcolor0", 13);
                setProgramUniform1i("shadowcolor1", 14);
                setProgramUniform1i("noisetex", 15);
                break;
            default:
                normalMapEnabled = false;
                break;
        }
        ItemStack stack = mc.thePlayer.getCurrentEquippedItem();
        Item item = (stack != null) ? stack.getItem() : null;
        int itemID;
        Block block;
        if (item != null) {
            itemID = Item.itemRegistry.getIDForObject(item);
            block = (Block) Block.blockRegistry.getObjectById(itemID);
        } else {
            itemID = -1;
            block = null;
        }
        setProgramUniform1i("heldItemId", (itemID));
        setProgramUniform1i("heldBlockLightValue", (block != null) ? block.getLightValue() : 0);
        setProgramUniform1i("fogMode", (fogEnabled ? fogMode : 0));
        setProgramUniform3f("fogColor", fogColorR, fogColorG, fogColorB);
        setProgramUniform3f("skyColor", skyColorR, skyColorG, skyColorB);
        setProgramUniform1i("worldTime", (int) worldTime % 24000);
        setProgramUniform1i("moonPhase", moonPhase);
        setProgramUniform1f("frameTimeCounter", frameTimeCounter);
        setProgramUniform1f("sunAngle", sunAngle);
        setProgramUniform1f("shadowAngle", shadowAngle);
        setProgramUniform1f("rainStrength", rainStrength);
        setProgramUniform1f("aspectRatio", (float) renderWidth / (float) renderHeight);
        setProgramUniform1f("viewWidth", (float) renderWidth);
        setProgramUniform1f("viewHeight", (float) renderHeight);
        setProgramUniform1f("near", 0.05F);
        setProgramUniform1f("far", mc.gameSettings.renderDistanceChunks * 16);
        setProgramUniform3f("sunPosition", sunPosition[0], sunPosition[1], sunPosition[2]);
        setProgramUniform3f("moonPosition", moonPosition[0], moonPosition[1], moonPosition[2]);
        setProgramUniform3f("upPosition", upPosition[0], upPosition[1], upPosition[2]);
        setProgramUniform3f(
                "previousCameraPosition", (float) previousCameraPosition[0], (float) previousCameraPosition[1], (float)
                        previousCameraPosition[2]);
        setProgramUniform3f(
                "cameraPosition", (float) cameraPosition[0], (float) cameraPosition[1], (float) cameraPosition[2]);
        setProgramUniformMatrix4ARB("gbufferModelView", false, modelView);
        setProgramUniformMatrix4ARB("gbufferModelViewInverse", false, modelViewInverse);
        setProgramUniformMatrix4ARB("gbufferPreviousProjection", false, previousProjection);
        setProgramUniformMatrix4ARB("gbufferProjection", false, projection);
        setProgramUniformMatrix4ARB("gbufferProjectionInverse", false, projectionInverse);
        setProgramUniformMatrix4ARB("gbufferPreviousModelView", false, previousModelView);
        if (usedShadowDepthBuffers > 0) {
            setProgramUniformMatrix4ARB("shadowProjection", false, shadowProjection);
            setProgramUniformMatrix4ARB("shadowProjectionInverse", false, shadowProjectionInverse);
            setProgramUniformMatrix4ARB("shadowModelView", false, shadowModelView);
            setProgramUniformMatrix4ARB("shadowModelViewInverse", false, shadowModelViewInverse);
        }
        setProgramUniform1f("wetness", wetness);
        setProgramUniform1f("eyeAltitude", eyePosY);
        // .x = block brightness .y = sky brightness
        // value : 0,16,32,64,96,112,128,160,192,208,224,240 for light level 0 to 15
        setProgramUniform2i("eyeBrightness", (eyeBrightness & 0xffff), (eyeBrightness >> 16));
        setProgramUniform2i("eyeBrightnessSmooth", Math.round(eyeBrightnessFadeX), Math.round(eyeBrightnessFadeY));
        setProgramUniform2i("terrainTextureSize", terrainTextureSize[0], terrainTextureSize[1]);
        setProgramUniform1i("terrainIconSize", terrainIconSize);
        setProgramUniform1i("isEyeInWater", isEyeInWater);
        setProgramUniform1i("hideGUI", mc.gameSettings.hideGUI ? 1 : 0);
        setProgramUniform1f("centerDepthSmooth", centerDepthSmooth);
        setProgramUniform2i("atlasSize", atlasSizeX, atlasSizeY);
        Shaders.checkGLError("useProgram ", programNames[program]);
    }

    public static void setProgramUniform1i(String name, int x) {
        int gp = programsID[activeProgram];
        if (gp != GL_NONE) {
            int uniform = glGetUniformLocationARB(gp, name);
            glUniform1iARB(uniform, x);
            if (enableShadersDebug) checkGLError(programNames[activeProgram], name);
        }
    }

    public static void setProgramUniform2i(String name, int x, int y) {
        int gp = programsID[activeProgram];
        if (gp != GL_NONE) {
            int uniform = glGetUniformLocationARB(gp, name);
            glUniform2iARB(uniform, x, y);
            if (enableShadersDebug) checkGLError(programNames[activeProgram], name);
        }
    }

    public static void setProgramUniform1f(String name, float x) {
        int gp = programsID[activeProgram];
        if (gp != GL_NONE) {
            int uniform = glGetUniformLocationARB(gp, name);
            glUniform1fARB(uniform, x);
            if (enableShadersDebug) checkGLError(programNames[activeProgram], name);
        }
    }

    public static void setProgramUniform3f(String name, float x, float y, float z) {
        int gp = programsID[activeProgram];
        if (gp != GL_NONE) {
            int uniform = glGetUniformLocationARB(gp, name);
            glUniform3fARB(uniform, x, y, z);
            if (enableShadersDebug) checkGLError(programNames[activeProgram], name);
        }
    }

    public static void setProgramUniformMatrix4ARB(String name, boolean transpose, FloatBuffer matrix) {
        int gp = programsID[activeProgram];
        if (gp != GL_NONE && matrix != null) {
            int uniform = glGetUniformLocationARB(gp, name);
            glUniformMatrix4ARB(uniform, transpose, matrix);
            if (enableShadersDebug) checkGLError(programNames[activeProgram], name);
        }
    }

    private static int getBufferIndexFromString(String name) {
        if (name.equals("colortex0") || name.equals("gcolor")) return 0;
        else if (name.equals("colortex1") || name.equals("gdepth")) return 1;
        else if (name.equals("colortex2") || name.equals("gnormal")) return 2;
        else if (name.equals("colortex3") || name.equals("composite")) return 3;
        else if (name.equals("colortex4") || name.equals("gaux1")) return 4;
        else if (name.equals("colortex5") || name.equals("gaux2")) return 5;
        else if (name.equals("colortex6") || name.equals("gaux3")) return 6;
        else if (name.equals("colortex7") || name.equals("gaux4")) return 7;
        else return -1;
    }

    private static int getTextureFormatFromString(String par) {
        if (par.matches("[ \t]*R8[ \t]*")) return GL_R8;
        else if (par.matches("[ \t]*RG8[ \t]*")) return GL_RG8;
        else if (par.matches("[ \t]*RGB8[ \t]*")) return GL_RGB8;
        else if (par.matches("[ \t]*RGBA8[ \t]*")) return GL_RGBA8;
        else if (par.matches("[ \t]*R16[ \t]*")) return GL_R16;
        else if (par.matches("[ \t]*RG16[ \t]*")) return GL_RG16;
        else if (par.matches("[ \t]*RGB16[ \t]*")) return GL_RGB16;
        else if (par.matches("[ \t]*RGBA16[ \t]*")) return GL_RGBA16;
        else if (par.matches("[ \t]*R32F[ \t]*")) return GL_R32F;
        else if (par.matches("[ \t]*RG32F[ \t]*")) return GL_RG32F;
        else if (par.matches("[ \t]*RGB32F[ \t]*")) return GL_RGB32F;
        else if (par.matches("[ \t]*RGBA32F[ \t]*")) return GL_RGBA32F;
        else return 0;
    }

    private static void setupNoiseTexture() {
        if (noiseTexture == null) noiseTexture = new HFNoiseTexture(noiseTextureResolution, noiseTextureResolution);
    }

    private static IntBuffer fillIntBufferZero(IntBuffer buf) {
        int limit = buf.limit();
        for (int i = buf.position(); i < limit; ++i) buf.put(i, 0);
        return buf;
    }

    public static void uninit() {
        if (isInitialized) {
            checkGLError("Shaders.uninit pre");
            for (int i = 0; i < ProgramCount; ++i) {
                if (programsRef[i] != 0) {
                    glDeleteObjectARB(programsRef[i]);
                    checkGLError("del programRef");
                }
                programsRef[i] = 0;
                programsID[i] = 0;
                programsDrawBufSettings[i] = null;
                programsDrawBuffers[i] = null;
                programsCompositeMipmapSetting[i] = 0;
            }
            if (dfb != 0) {
                glDeleteFramebuffersEXT(dfb);
                dfb = 0;
                checkGLError("del dfb");
            }
            if (sfb != 0) {
                glDeleteFramebuffersEXT(sfb);
                sfb = 0;
                checkGLError("del sfb");
            }
            // glDeleteRenderbuffersEXT(dfbDepthBuffer); dfbDepthBuffer = 0;
            // if (dfbRenderBuffers != null) {
            //	glDeleteRenderbuffersEXT(dfbRenderBuffers);
            //	fillIntBufferZero(dfbRenderBuffers);
            // }
            if (dfbDepthTextures != null) {
                glDeleteTextures(dfbDepthTextures);
                fillIntBufferZero(dfbDepthTextures);
                checkGLError("del dfbDepthTextures");
            }
            if (dfbColorTextures != null) {
                glDeleteTextures(dfbColorTextures);
                fillIntBufferZero(dfbColorTextures);
                checkGLError("del dfbTextures");
            }
            if (sfbDepthTextures != null) {
                glDeleteTextures(sfbDepthTextures);
                fillIntBufferZero(sfbDepthTextures);
                checkGLError("del shadow depth");
            }
            if (sfbColorTextures != null) {
                glDeleteTextures(sfbColorTextures);
                fillIntBufferZero(sfbColorTextures);
                checkGLError("del shadow color");
            }
            if (dfbDrawBuffers != null) {
                fillIntBufferZero(dfbDrawBuffers);
            }
            if (noiseTexture != null) {
                noiseTexture.destroy();
                noiseTexture = null;
            }

            System.out.println("UNINIT");

            shadowPassInterval = 0;
            shouldSkipDefaultShadow = false;
            isInitialized = false;
            notFirstInit = true;
            checkGLError("Shaders.uninit");
        }
    }

    public static void scheduleResize() {
        renderDisplayHeight = 0;
    }

    public static void scheduleResizeShadow() {
        needResizeShadow = true;
    }

    private static void resize() {
        renderDisplayWidth = mc.displayWidth;
        renderDisplayHeight = mc.displayHeight;
        // renderWidth  = mc.displayWidth * superSamplingLevel;
        // renderHeight = mc.displayHeight * superSamplingLevel;
        renderWidth = Math.round(renderDisplayWidth * configRenderResMul);
        renderHeight = Math.round(renderDisplayHeight * configRenderResMul);
        setupFrameBuffer();
    }

    private static void resizeShadow() {
        needResizeShadow = false;
        shadowMapWidth = Math.round(spShadowMapWidth * configShadowResMul);
        shadowMapHeight = Math.round(spShadowMapHeight * configShadowResMul);
        setupShadowFrameBuffer();
    }

    private static void setupFrameBuffer() {
        if (dfb != 0) {
            glDeleteFramebuffersEXT(dfb);
            glDeleteTextures(dfbDepthTextures);
            glDeleteTextures(dfbColorTextures);
        }

        dfb = glGenFramebuffersEXT();
        glGenTextures((IntBuffer) dfbDepthTextures.clear().limit(usedDepthBuffers));
        glGenTextures((IntBuffer) dfbColorTextures.clear().limit(usedColorBuffers));
        dfbDepthTextures.position(0);
        dfbColorTextures.position(0);

        glBindFramebufferEXT(GL_FRAMEBUFFER_EXT, dfb);
        glDrawBuffers(GL_NONE);
        glReadBuffer(GL_NONE);

        for (int i = 0; i < usedDepthBuffers; ++i) {
            glBindTexture(GL_TEXTURE_2D, dfbDepthTextures.get(i));
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_DEPTH_TEXTURE_MODE, GL_LUMINANCE);
            glTexImage2D(
                    GL_TEXTURE_2D,
                    0,
                    GL_DEPTH_COMPONENT,
                    renderWidth,
                    renderHeight,
                    0,
                    GL_DEPTH_COMPONENT,
                    GL11.GL_FLOAT,
                    (ByteBuffer) null);
        }

        glFramebufferTexture2DEXT(
                GL_FRAMEBUFFER_EXT, GL_DEPTH_ATTACHMENT_EXT, GL_TEXTURE_2D, dfbDepthTextures.get(0), 0);
        glDrawBuffers(dfbDrawBuffers);
        glReadBuffer(GL_NONE);
        checkGLError("FT d");

        for (int i = 0; i < usedColorBuffers; ++i) {
            glBindTexture(GL_TEXTURE_2D, dfbColorTextures.get(i));
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexImage2D(
                    GL_TEXTURE_2D,
                    0,
                    gbuffersFormat[i],
                    renderWidth,
                    renderHeight,
                    0,
                    GL12.GL_BGRA,
                    GL12.GL_UNSIGNED_INT_8_8_8_8_REV,
                    (ByteBuffer) null);
            glFramebufferTexture2DEXT(
                    GL_FRAMEBUFFER_EXT, GL_COLOR_ATTACHMENT0_EXT + i, GL_TEXTURE_2D, dfbColorTextures.get(i), 0);
            checkGLError("FT c");
        }

        int status = glCheckFramebufferStatusEXT(GL_FRAMEBUFFER_EXT);

        if (status == GL_FRAMEBUFFER_INCOMPLETE_FORMATS_EXT) {
            printChatAndLogError("Failed using multiple internal formats in frame buffer.");
            for (int i = 0; i < usedColorBuffers; ++i) {
                glBindTexture(GL_TEXTURE_2D, dfbColorTextures.get(i));
                glTexImage2D(
                        GL_TEXTURE_2D,
                        0,
                        GL_RGBA,
                        renderWidth,
                        renderHeight,
                        0,
                        GL12.GL_BGRA,
                        GL12.GL_UNSIGNED_INT_8_8_8_8_REV,
                        (ByteBuffer) null);
                glFramebufferTexture2DEXT(
                        GL_FRAMEBUFFER_EXT, GL_COLOR_ATTACHMENT0_EXT + i, GL_TEXTURE_2D, dfbColorTextures.get(i), 0);
                checkGLError("FT c");
            }
            status = glCheckFramebufferStatusEXT(GL_FRAMEBUFFER_EXT);
            if (status == GL_FRAMEBUFFER_COMPLETE_EXT) {
                printChatAndLogError("Please update graphics driver.");
            }
        }

        glBindTexture(GL_TEXTURE_2D, 0);

        if (status != GL_FRAMEBUFFER_COMPLETE_EXT) {
            printChatAndLogError("Failed creating framebuffer! (Status " + status + ")");
        } else {
            System.out.println("Framebuffer created.");
        }
    }

    private static void setupShadowFrameBuffer() {
        if (usedShadowDepthBuffers == 0) {
            return;
        }

        if (sfb != 0) {
            glDeleteFramebuffersEXT(sfb);
            glDeleteTextures(sfbDepthTextures);
            glDeleteTextures(sfbColorTextures);
        }

        sfb = glGenFramebuffersEXT();
        glBindFramebufferEXT(GL_FRAMEBUFFER_EXT, sfb);
        glDrawBuffers(GL_NONE);
        glReadBuffer(GL_NONE);

        glGenTextures((IntBuffer) sfbDepthTextures.clear().limit(usedShadowDepthBuffers));
        glGenTextures((IntBuffer) sfbColorTextures.clear().limit(usedShadowColorBuffers));
        // printIntBuffer(sfbDepthTextures);
        // printIntBuffer(sfbColorTextures);
        sfbDepthTextures.position(0);
        sfbColorTextures.position(0);

        // depth
        for (int i = 0; i < usedShadowDepthBuffers; ++i) {
            glBindTexture(GL_TEXTURE_2D, sfbDepthTextures.get(i));
            glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP);
            glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP);
            int filter = shadowFilterNearest[i] ? GL_NEAREST : GL_LINEAR;
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
            if (shadowHardwareFilteringEnabled[i])
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_COMPARE_REF_TO_TEXTURE);
            glTexImage2D(
                    GL_TEXTURE_2D,
                    0,
                    GL_DEPTH_COMPONENT,
                    shadowMapWidth,
                    shadowMapHeight,
                    0,
                    GL_DEPTH_COMPONENT,
                    GL11.GL_FLOAT,
                    (FloatBuffer) null);
        }
        glFramebufferTexture2DEXT(
                GL_FRAMEBUFFER_EXT, GL_DEPTH_ATTACHMENT_EXT, GL_TEXTURE_2D, sfbDepthTextures.get(0), 0);
        checkGLError("FT sd");

        // color shadow
        for (int i = 0; i < usedShadowColorBuffers; ++i) {
            glBindTexture(GL_TEXTURE_2D, sfbColorTextures.get(i));
            glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP);
            glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP);
            int filter = shadowColorFilterNearest[i] ? GL_NEAREST : GL_LINEAR;
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
            glTexImage2D(
                    GL_TEXTURE_2D,
                    0,
                    GL_RGBA,
                    shadowMapWidth,
                    shadowMapHeight,
                    0,
                    GL12.GL_BGRA,
                    GL12.GL_UNSIGNED_INT_8_8_8_8_REV,
                    (ByteBuffer) null);
            glFramebufferTexture2DEXT(
                    GL_FRAMEBUFFER_EXT, GL_COLOR_ATTACHMENT0_EXT + i, GL_TEXTURE_2D, sfbColorTextures.get(i), 0);
            checkGLError("FT sc");
        }

        glBindTexture(GL_TEXTURE_2D, 0);

        int status = glCheckFramebufferStatusEXT(GL_FRAMEBUFFER_EXT);
        if (status != GL_FRAMEBUFFER_COMPLETE_EXT) {
            printChatAndLogError("Failed creating shadow framebuffer! (Status " + status + ")");
        } else {
            System.out.println("Shadow framebuffer created.");
        }
    }

    /*public static void linkTextureNormalMap(int tex0, int tex2, int tex3)
    {
    	textureIdMap_n.put(tex0, tex2);
    	textureIdMap_s.put(tex0, tex3);
    	int[] ast0,ast2,ast3;
    	if (tex0 < Tessellator.atlasSubTextures.length && (ast0 = Tessellator.atlasSubTextures[tex0]) != null)
    	{
    		if (tex2 < Tessellator.atlasSubTextures.length && (ast2 = Tessellator.atlasSubTextures[tex2]) != null)
    			for (int it = 0, size = Math.min(ast0.length,ast2.length); it < size; ++it)
    				textureIdMap_n.put(ast0[it], ast2[it]);
    		if (tex3 < Tessellator.atlasSubTextures.length && (ast3 = Tessellator.atlasSubTextures[tex3]) != null)
    			for (int it = 0, size = Math.min(ast0.length,ast3.length); it < size; ++it)
    				textureIdMap_s.put(ast0[it], ast3[it]);
    	}
    }*/

    public static void beginRender(Minecraft minecraft, float f, long l) {
        if (isShadowPass) {
            return;
        }
        checkGLError("pre beginRender");

        mc = minecraft;
        mc.mcProfiler.startSection("init");

        if (!isInitialized) {
            init();
        }
        if (mc.displayWidth != renderDisplayWidth || mc.displayHeight != renderDisplayHeight) {
            resize();
        }
        if (needResizeShadow) {
            resizeShadow();
        }

        worldTime = mc.theWorld.getWorldTime();
        diffWorldTime = (worldTime - lastWorldTime) % 24000;
        if (diffWorldTime < 0) diffWorldTime += 24000;
        lastWorldTime = worldTime;
        moonPhase = mc.theWorld.getMoonPhase();

        systemTime = System.currentTimeMillis();
        if (lastSystemTime == 0) {
            lastSystemTime =
                    systemTime; // Initialize lastSystemTime on the first tick so that it is equal to current system
            // time
        }
        diffSystemTime = systemTime - lastSystemTime;
        lastSystemTime = systemTime;

        frameTimeCounter += diffSystemTime * 0.001f;
        frameTimeCounter = frameTimeCounter % 100000.0f;

        rainStrength = minecraft.theWorld.getRainStrength(f);
        {
            float fadeScalar = diffSystemTime * 0.01f;
            // float temp1 = (float)Math.exp(Math.log(0.5)*diffWorldTime/((wetness < rainStrength)? drynessHalfLife :
            // wetnessHalfLife));
            float temp1 = (float) Math.exp(
                    Math.log(0.5) * fadeScalar / ((wetness < rainStrength) ? drynessHalfLife : wetnessHalfLife));
            wetness = wetness * (temp1) + rainStrength * (1 - temp1);
        }

        EntityLivingBase eye = mc.renderViewEntity;
        eyePosY = (float) eye.posY * f + (float) eye.lastTickPosY * (1 - f);
        eyeBrightness = eye.getBrightnessForRender(f);
        {
            // float temp2 = (float)Math.exp(Math.log(0.5)*1.0f/eyeBrightnessHalfLife);
            float fadeScalar = diffSystemTime * 0.01f;
            float temp2 = (float) Math.exp(Math.log(0.5) * fadeScalar / eyeBrightnessHalflife);
            eyeBrightnessFadeX = eyeBrightnessFadeX * temp2 + (eyeBrightness & 0xffff) * (1 - temp2);
            eyeBrightnessFadeY = eyeBrightnessFadeY * temp2 + (eyeBrightness >> 16) * (1 - temp2);
        }

        isEyeInWater = (mc.gameSettings.thirdPersonView == 0
                        && !mc.renderViewEntity.isPlayerSleeping()
                        && mc.thePlayer.isInsideOfMaterial(Material.water))
                ? 1
                : 0;

        {
            Vec3 skyColorV = mc.theWorld.getSkyColor(mc.renderViewEntity, f);
            skyColorR = (float) skyColorV.xCoord;
            skyColorG = (float) skyColorV.yCoord;
            skyColorB = (float) skyColorV.zCoord;
        }

        // Determine average color
        //		{
        //			int searchWidth = renderWidth / 2;
        //			int searchHeight = renderHeight / 2;
        //
        //			ByteBuffer colorByteBuffer = ByteBuffer.allocateDirect(searchWidth * searchHeight * 4 * 3);
        //			glReadPixels((renderWidth / 2) - (searchWidth / 2), (renderHeight / 2) - (searchHeight / 2), searchWidth,
        // searchHeight, GL11.GL_RGB, GL11.GL_FLOAT, colorByteBuffer);
        //
        //			colorByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        //
        //
        //			float averageColor = 0.0f;
        //			for (int i = 0; i < searchWidth; i++) {
        //				for (int j = 0; j < searchHeight; j++) {
        //					averageColor += colorByteBuffer.getFloat(i * 4 + (searchWidth * 4 * 3 * j));
        //				}
        //			}
        //
        //			System.out.println("Color: " + averageColor / (searchWidth * searchHeight));
        //
        //		}

        isRenderingWorld = true;
        isCompositeRendered = false;
        isHandRendered = false;

        if (usedShadowDepthBuffers >= 1) {
            glActiveTexture(GL_TEXTURE4);
            glBindTexture(GL_TEXTURE_2D, sfbDepthTextures.get(0));
            if (usedShadowDepthBuffers >= 2) {
                glActiveTexture(GL_TEXTURE5);
                glBindTexture(GL_TEXTURE_2D, sfbDepthTextures.get(1));
            }
        }

        for (int i = 0; i < 4 && 4 + i < usedColorBuffers; ++i) {
            glActiveTexture(GL_TEXTURE7 + i);
            glBindTexture(GL_TEXTURE_2D, dfbColorTextures.get(4 + i));
        }

        glActiveTexture(GL_TEXTURE6);
        glBindTexture(GL_TEXTURE_2D, dfbDepthTextures.get(0));

        if (usedDepthBuffers >= 2) {
            glActiveTexture(GL_TEXTURE11);
            glBindTexture(GL_TEXTURE_2D, dfbDepthTextures.get(1));

            if (usedDepthBuffers >= 3) {
                glActiveTexture(GL_TEXTURE12);
                glBindTexture(GL_TEXTURE_2D, dfbDepthTextures.get(2));
            }
        }

        for (int i = 0; i < usedShadowColorBuffers; ++i) {
            glActiveTexture(GL_TEXTURE13 + i);
            glBindTexture(GL_TEXTURE_2D, sfbColorTextures.get(i));
        }

        if (noiseTextureEnabled) {
            glActiveTexture(GL_TEXTURE0 + noiseTexture.textureUnit);
            glBindTexture(GL_TEXTURE_2D, noiseTexture.getID());
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        }

        glActiveTexture(GL_TEXTURE0);

        previousCameraPosition[0] = cameraPosition[0];
        previousCameraPosition[1] = cameraPosition[1];
        previousCameraPosition[2] = cameraPosition[2];

        previousProjection.position(0);
        projection.position(0);
        previousProjection.put(projection);
        previousProjection.position(0);
        projection.position(0);
        previousModelView.position(0);
        modelView.position(0);
        previousModelView.put(modelView);
        previousModelView.position(0);
        modelView.position(0);

        EntityRenderer.anaglyphField = 0;

        if (usedShadowDepthBuffers > 0 && --shadowPassCounter <= 0) {
            // do shadow pass
            // System.out.println("shadow pass start");
            mc.mcProfiler.endStartSection("shadow pass");
            preShadowPassThirdPersonView = mc.gameSettings.thirdPersonView;
            boolean preShadowPassAdvancedOpengl = mc.gameSettings.advancedOpengl;
            mc.gameSettings.advancedOpengl = false;

            // moved to setCamera
            // mc.gameSettings.thirdPersonView = 1;

            isShadowPass = true;
            shadowPassCounter = shadowPassInterval;

            glBindFramebufferEXT(GL_FRAMEBUFFER_EXT, sfb);
            glDrawBuffers(programsDrawBuffers[ProgramShadow]);

            useProgram(ProgramShadow);

            mc.entityRenderer.renderWorld(f, l);

            glFlush();

            isShadowPass = false;

            mc.gameSettings.advancedOpengl = preShadowPassAdvancedOpengl;
            mc.gameSettings.thirdPersonView = preShadowPassThirdPersonView;

            if (hasGlGenMipmap) {
                if (usedShadowDepthBuffers >= 1) {
                    if (shadowMipmapEnabled[0]) {
                        glActiveTexture(GL_TEXTURE4);
                        glGenerateMipmap(GL_TEXTURE_2D);
                        glTexParameteri(
                                GL_TEXTURE_2D,
                                GL_TEXTURE_MIN_FILTER,
                                shadowFilterNearest[0] ? GL_NEAREST_MIPMAP_NEAREST : GL_LINEAR_MIPMAP_LINEAR);
                    }
                    if (usedShadowDepthBuffers >= 2) {
                        if (shadowMipmapEnabled[1]) {
                            glActiveTexture(GL_TEXTURE5);
                            glGenerateMipmap(GL_TEXTURE_2D);
                            glTexParameteri(
                                    GL_TEXTURE_2D,
                                    GL_TEXTURE_MIN_FILTER,
                                    shadowFilterNearest[1] ? GL_NEAREST_MIPMAP_NEAREST : GL_LINEAR_MIPMAP_LINEAR);
                        }
                    }
                    glActiveTexture(GL_TEXTURE0);
                }
                if (usedShadowColorBuffers >= 1) {
                    if (shadowColorMipmapEnabled[0]) {
                        glActiveTexture(GL_TEXTURE13);
                        glGenerateMipmap(GL_TEXTURE_2D);
                        glTexParameteri(
                                GL_TEXTURE_2D,
                                GL_TEXTURE_MIN_FILTER,
                                shadowColorFilterNearest[0] ? GL_NEAREST_MIPMAP_NEAREST : GL_LINEAR_MIPMAP_LINEAR);
                    }
                    if (usedShadowColorBuffers >= 2) {
                        if (shadowColorMipmapEnabled[1]) {
                            glActiveTexture(GL_TEXTURE14);
                            glGenerateMipmap(GL_TEXTURE_2D);
                            glTexParameteri(
                                    GL_TEXTURE_2D,
                                    GL_TEXTURE_MIN_FILTER,
                                    shadowColorFilterNearest[1] ? GL_NEAREST_MIPMAP_NEAREST : GL_LINEAR_MIPMAP_LINEAR);
                        }
                    }
                    glActiveTexture(GL_TEXTURE0);
                }
            }

            // System.out.println("shadow pass end");
        }
        mc.mcProfiler.endSection();

        glBindFramebufferEXT(GL_FRAMEBUFFER_EXT, dfb);
        GL11.glViewport(0, 0, Shaders.renderWidth, Shaders.renderHeight);
        activeDrawBuffers = null;
        ShadersTex.bindNSTextures(defaultTexture.getMultiTexID());
        useProgram(ProgramTextured);

        checkGLError("end beginRender");
    }

    public static void setViewport(int vx, int vy, int vw, int vh) {
        GL11.glColorMask(true, true, true, true);
        if (isShadowPass) {
            GL11.glViewport(0, 0, shadowMapWidth, shadowMapHeight);
        } else {
            GL11.glViewport(0, 0, Shaders.renderWidth, Shaders.renderHeight);
            glBindFramebufferEXT(GL_FRAMEBUFFER_EXT, dfb);
            isRenderingDfb = true;
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            setDrawBuffers(drawBuffersNone);
            useProgram(ProgramTextured);
            checkGLError("beginRenderPass");
        }
    }

    public static int setFogMode(int val) {
        return fogMode = val;
    }

    public static void setFogColor(float r, float g, float b) {
        fogColorR = r;
        fogColorG = g;
        fogColorB = b;
    }

    public static void setClearColor(float red, float green, float blue, float alpha) {
        GL11.glClearColor(red, green, blue, alpha);
        clearColorR = red;
        clearColorG = green;
        clearColorB = blue;
    }

    public static void clearRenderBuffer() {
        // clearColorR = fogColorR;
        // clearColorG = fogColorG;
        // clearColorB = fogColorB;

        if (isShadowPass) {
            // No need to clear shadow depth 1. It will be overwritten.
            // if (waterShadowEnabled) {
            //	glFramebufferTexture2DEXT(GL_FRAMEBUFFER_EXT, GL_DEPTH_ATTACHMENT_EXT, GL_TEXTURE_2D,
            // sfbDepthTextures.get(1), 0);
            //	glClear(GL_DEPTH_BUFFER_BIT);
            // }
            checkGLError("shadow clear pre");
            glFramebufferTexture2DEXT(
                    GL_FRAMEBUFFER_EXT, GL_DEPTH_ATTACHMENT_EXT, GL_TEXTURE_2D, sfbDepthTextures.get(0), 0);
            glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
            // printIntBuffer(programsDrawBuffers[ProgramShadow]);
            glDrawBuffers(programsDrawBuffers[ProgramShadow]);
            checkFramebufferStatus("shadow clear");
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            checkGLError("shadow clear");
            return;
        }
        checkGLError("clear pre");

        /*
        glDrawBuffers(dfbDrawBuffers);
        glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        */

        glDrawBuffers(GL_COLOR_ATTACHMENT0_EXT);
        // glClearColor(clearColorR, clearColorG, clearColorB, 1.0f);
        // glClearColor(1f, 0f, 0f, 1.0f); // for debug
        glClear(GL_COLOR_BUFFER_BIT);

        glDrawBuffers(GL_COLOR_ATTACHMENT1_EXT);
        glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);

        for (int i = 2; i < usedColorBuffers; ++i) {
            glDrawBuffers(GL_COLOR_ATTACHMENT0_EXT + i);
            glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            glClear(GL_COLOR_BUFFER_BIT);
        }

        setDrawBuffers(dfbDrawBuffers);
        checkFramebufferStatus("clear");
        checkGLError("clear");
    }

    public static void setCamera(float f) {
        EntityLivingBase viewEntity = mc.renderViewEntity;

        double x = viewEntity.lastTickPosX + (viewEntity.posX - viewEntity.lastTickPosX) * f;
        double y = viewEntity.lastTickPosY + (viewEntity.posY - viewEntity.lastTickPosY) * f;
        double z = viewEntity.lastTickPosZ + (viewEntity.posZ - viewEntity.lastTickPosZ) * f;

        cameraPosition[0] = x;
        cameraPosition[1] = y;
        cameraPosition[2] = z;

        glGetFloat(GL_PROJECTION_MATRIX, (FloatBuffer) projection.position(0));
        invertMat4x((FloatBuffer) projection.position(0), (FloatBuffer) projectionInverse.position(0));
        projection.position(0);
        projectionInverse.position(0);

        glGetFloat(GL_MODELVIEW_MATRIX, (FloatBuffer) modelView.position(0));
        invertMat4x((FloatBuffer) modelView.position(0), (FloatBuffer) modelViewInverse.position(0));
        modelView.position(0);
        modelViewInverse.position(0);

        if (isShadowPass) {
            glViewport(0, 0, shadowMapWidth, shadowMapHeight);

            glMatrixMode(GL_PROJECTION);
            glLoadIdentity();

            if (shadowMapIsOrtho) {
                glOrtho(
                        -shadowMapHalfPlane,
                        shadowMapHalfPlane,
                        -shadowMapHalfPlane,
                        shadowMapHalfPlane,
                        0.05f,
                        256.0f);
            } else {
                // just backwards compatibility. it's only used when SHADOWFOV is set in the shaders.
                gluPerspective(shadowMapFOV, (float) shadowMapWidth / (float) shadowMapHeight, 0.05f, 256.0f);
            }

            glMatrixMode(GL_MODELVIEW);
            glLoadIdentity();
            glTranslatef(0.0f, 0.0f, -100.0f);
            glRotatef(90.0f, 1.0f, 0.0f, 0.0f);
            float celestialAngle = mc.theWorld.getCelestialAngle(f);
            sunAngle = (celestialAngle < 0.75f) ? celestialAngle + 0.25f : celestialAngle - 0.75f;
            float angle = celestialAngle * (-360.0f);
            float angleInterval =
                    shadowAngleInterval > 0.0f ? (angle % shadowAngleInterval - (shadowAngleInterval * 0.5f)) : 0.0f;
            if (sunAngle <= 0.5) {
                // day time
                glRotatef(angle - angleInterval, 0.0f, 0.0f, 1.0f);
                glRotatef(sunPathRotation, 1.0f, 0.0f, 0.0f); // rotate
                shadowAngle = sunAngle;
            } else {
                // night time
                glRotatef(angle + 180.0f - angleInterval, 0.0f, 0.0f, 1.0f);
                glRotatef(sunPathRotation, 1.0f, 0.0f, 0.0f); // rotate
                shadowAngle = sunAngle - 0.5f;
            }
            if (shadowMapIsOrtho) {
                // reduces jitter
                float trans = shadowIntervalSize;
                float trans2 = trans / 2.0f;
                glTranslatef((float) x % trans - trans2, (float) y % trans - trans2, (float) z % trans - trans2);
            }

            glGetFloat(GL_PROJECTION_MATRIX, (FloatBuffer) shadowProjection.position(0));
            invertMat4x((FloatBuffer) shadowProjection.position(0), (FloatBuffer) shadowProjectionInverse.position(0));
            shadowProjection.position(0);
            shadowProjectionInverse.position(0);

            glGetFloat(GL_MODELVIEW_MATRIX, (FloatBuffer) shadowModelView.position(0));
            invertMat4x((FloatBuffer) shadowModelView.position(0), (FloatBuffer) shadowModelViewInverse.position(0));
            shadowModelView.position(0);
            shadowModelViewInverse.position(0);

            setProgramUniformMatrix4ARB("gbufferProjection", false, projection);
            setProgramUniformMatrix4ARB("gbufferProjectionInverse", false, projectionInverse);
            setProgramUniformMatrix4ARB("gbufferPreviousProjection", false, previousProjection);
            setProgramUniformMatrix4ARB("gbufferModelView", false, modelView);
            setProgramUniformMatrix4ARB("gbufferModelViewInverse", false, modelViewInverse);
            setProgramUniformMatrix4ARB("gbufferPreviousModelView", false, previousModelView);
            setProgramUniformMatrix4ARB("shadowProjection", false, shadowProjection);
            setProgramUniformMatrix4ARB("shadowProjectionInverse", false, shadowProjectionInverse);
            setProgramUniformMatrix4ARB("shadowModelView", false, shadowModelView);
            setProgramUniformMatrix4ARB("shadowModelViewInverse", false, shadowModelViewInverse);

            // Also render player shadow
            mc.gameSettings.thirdPersonView = 1;
            checkGLError("setCamera");
            return;
        }
        checkGLError("setCamera");
    }

    public static void preCelestialRotate() {
        Shaders.setUpPosition();
        GL11.glRotatef(Shaders.sunPathRotation * 1.0f, 0.0f, 0.0f, 1.0f);
        checkGLError("preCelestialRotate");
    }

    public static void postCelestialRotate() {
        // This is called when the current matrix is the modelview matrix based on the celestial angle.
        // The sun is at (0, 100, 0), and the moon is at (0, -100, 0).
        FloatBuffer modelView = tempMatrixDirectBuffer;
        modelView.clear();
        glGetFloat(GL_MODELVIEW_MATRIX, modelView);
        modelView.get(tempMat, 0, 16);
        multiplyMat4xVec4(tempMat, sunPosModelView, sunPosition);
        multiplyMat4xVec4(tempMat, moonPosModelView, moonPosition);
        checkGLError("postCelestialRotate");
    }

    public static void setUpPosition() {
        // Up direction in model view while rendering sky.
        FloatBuffer modelView = tempMatrixDirectBuffer;
        modelView.clear();
        glGetFloat(GL_MODELVIEW_MATRIX, modelView);
        modelView.get(tempMat, 0, 16);
        multiplyMat4xVec4(tempMat, upPosModelView, upPosition);
    }

    private static float[] multiplyMat4xVec4(float[] matA, float[] vecB, float[] vecOut) {
        vecOut[0] = matA[0] * vecB[0] + matA[4] * vecB[1] + matA[8] * vecB[2] + matA[12] * vecB[3];
        vecOut[1] = matA[1] * vecB[0] + matA[5] * vecB[1] + matA[9] * vecB[2] + matA[13] * vecB[3];
        vecOut[2] = matA[2] * vecB[0] + matA[6] * vecB[1] + matA[10] * vecB[2] + matA[14] * vecB[3];
        vecOut[3] = matA[3] * vecB[0] + matA[7] * vecB[1] + matA[11] * vecB[2] + matA[15] * vecB[3];
        return vecOut;
    }

    static float[] invertMat4x_m = new float[16];
    static float[] invertMat4x_inv = new float[16];

    private static FloatBuffer invertMat4x(FloatBuffer matIn, FloatBuffer invMatOut) {
        float[] m = invertMat4x_m;
        float[] inv = invertMat4x_inv;
        float det;
        int i;

        matIn.get(m);

        inv[0] = m[5] * m[10] * m[15]
                - m[5] * m[11] * m[14]
                - m[9] * m[6] * m[15]
                + m[9] * m[7] * m[14]
                + m[13] * m[6] * m[11]
                - m[13] * m[7] * m[10];
        inv[4] = -m[4] * m[10] * m[15]
                + m[4] * m[11] * m[14]
                + m[8] * m[6] * m[15]
                - m[8] * m[7] * m[14]
                - m[12] * m[6] * m[11]
                + m[12] * m[7] * m[10];
        inv[8] = m[4] * m[9] * m[15]
                - m[4] * m[11] * m[13]
                - m[8] * m[5] * m[15]
                + m[8] * m[7] * m[13]
                + m[12] * m[5] * m[11]
                - m[12] * m[7] * m[9];
        inv[12] = -m[4] * m[9] * m[14]
                + m[4] * m[10] * m[13]
                + m[8] * m[5] * m[14]
                - m[8] * m[6] * m[13]
                - m[12] * m[5] * m[10]
                + m[12] * m[6] * m[9];
        inv[1] = -m[1] * m[10] * m[15]
                + m[1] * m[11] * m[14]
                + m[9] * m[2] * m[15]
                - m[9] * m[3] * m[14]
                - m[13] * m[2] * m[11]
                + m[13] * m[3] * m[10];
        inv[5] = m[0] * m[10] * m[15]
                - m[0] * m[11] * m[14]
                - m[8] * m[2] * m[15]
                + m[8] * m[3] * m[14]
                + m[12] * m[2] * m[11]
                - m[12] * m[3] * m[10];
        inv[9] = -m[0] * m[9] * m[15]
                + m[0] * m[11] * m[13]
                + m[8] * m[1] * m[15]
                - m[8] * m[3] * m[13]
                - m[12] * m[1] * m[11]
                + m[12] * m[3] * m[9];
        inv[13] = m[0] * m[9] * m[14]
                - m[0] * m[10] * m[13]
                - m[8] * m[1] * m[14]
                + m[8] * m[2] * m[13]
                + m[12] * m[1] * m[10]
                - m[12] * m[2] * m[9];
        inv[2] = m[1] * m[6] * m[15]
                - m[1] * m[7] * m[14]
                - m[5] * m[2] * m[15]
                + m[5] * m[3] * m[14]
                + m[13] * m[2] * m[7]
                - m[13] * m[3] * m[6];
        inv[6] = -m[0] * m[6] * m[15]
                + m[0] * m[7] * m[14]
                + m[4] * m[2] * m[15]
                - m[4] * m[3] * m[14]
                - m[12] * m[2] * m[7]
                + m[12] * m[3] * m[6];
        inv[10] = m[0] * m[5] * m[15]
                - m[0] * m[7] * m[13]
                - m[4] * m[1] * m[15]
                + m[4] * m[3] * m[13]
                + m[12] * m[1] * m[7]
                - m[12] * m[3] * m[5];
        inv[14] = -m[0] * m[5] * m[14]
                + m[0] * m[6] * m[13]
                + m[4] * m[1] * m[14]
                - m[4] * m[2] * m[13]
                - m[12] * m[1] * m[6]
                + m[12] * m[2] * m[5];
        inv[3] = -m[1] * m[6] * m[11]
                + m[1] * m[7] * m[10]
                + m[5] * m[2] * m[11]
                - m[5] * m[3] * m[10]
                - m[9] * m[2] * m[7]
                + m[9] * m[3] * m[6];
        inv[7] = m[0] * m[6] * m[11]
                - m[0] * m[7] * m[10]
                - m[4] * m[2] * m[11]
                + m[4] * m[3] * m[10]
                + m[8] * m[2] * m[7]
                - m[8] * m[3] * m[6];
        inv[11] = -m[0] * m[5] * m[11]
                + m[0] * m[7] * m[9]
                + m[4] * m[1] * m[11]
                - m[4] * m[3] * m[9]
                - m[8] * m[1] * m[7]
                + m[8] * m[3] * m[5];
        inv[15] = m[0] * m[5] * m[10]
                - m[0] * m[6] * m[9]
                - m[4] * m[1] * m[10]
                + m[4] * m[2] * m[9]
                + m[8] * m[1] * m[6]
                - m[8] * m[2] * m[5];

        det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12];

        if (det != 0.0) {
            for (i = 0; i < 16; ++i) inv[i] /= det;
        } else {
            // no inverse
            Arrays.fill(inv, 0);
        }

        invMatOut.put(inv);

        return invMatOut;
    }

    public static void genCompositeMipmap() {
        if (hasGlGenMipmap) {
            if ((activeCompositeMipmapSetting & (1 << 0)) != 0) {
                glActiveTexture(GL_TEXTURE0);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
                glGenerateMipmap(GL_TEXTURE_2D);
            }

            if ((activeCompositeMipmapSetting & (1 << 1)) != 0) {
                glActiveTexture(GL_TEXTURE1);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
                glGenerateMipmap(GL_TEXTURE_2D);
            }

            if ((activeCompositeMipmapSetting & (1 << 2)) != 0) {
                glActiveTexture(GL_TEXTURE2);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
                glGenerateMipmap(GL_TEXTURE_2D);
            }

            if ((activeCompositeMipmapSetting & (1 << 3)) != 0) {
                glActiveTexture(GL_TEXTURE3);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
                glGenerateMipmap(GL_TEXTURE_2D);
            }

            for (int i = 0; i < 4 && 4 + i < usedColorBuffers; ++i) {
                if ((activeCompositeMipmapSetting & ((1 << 4) << i)) != 0) {
                    glActiveTexture(GL_TEXTURE7 + i);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
                    glGenerateMipmap(GL_TEXTURE_2D);
                }
            }

            glActiveTexture(GL_TEXTURE0);
        }
    }

    public static void drawComposite() {
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        glBegin(GL_QUADS);
        glTexCoord2f(0.0f, 0.0f);
        glVertex3f(0.0f, 0.0f, 0.0f);
        glTexCoord2f(1.0f, 0.0f);
        glVertex3f(1.0f, 0.0f, 0.0f);
        glTexCoord2f(1.0f, 1.0f);
        glVertex3f(1.0f, 1.0f, 0.0f);
        glTexCoord2f(0.0f, 1.0f);
        glVertex3f(0.0f, 1.0f, 0.0f);
        glEnd();
    }

    public static void renderCompositeFinal() {
        if (isShadowPass) {
            // useProgram(ProgramNone);
            return;
        }

        checkGLError("pre-renderCompositeFinal");
        // fogColorR = clearColorR;
        // fogColorG = clearColorG;
        // fogColorB = clearColorB;

        // glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f);

        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        glEnable(GL_TEXTURE_2D);
        glDisable(GL_ALPHA_TEST);
        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_ALWAYS);
        glDepthMask(false);

        // textures
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, dfbColorTextures.get(0));

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, dfbColorTextures.get(1));

        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, dfbColorTextures.get(2));

        glActiveTexture(GL_TEXTURE3);
        glBindTexture(GL_TEXTURE_2D, dfbColorTextures.get(3));

        if (usedShadowDepthBuffers >= 1) {
            glActiveTexture(GL_TEXTURE4);
            glBindTexture(GL_TEXTURE_2D, sfbDepthTextures.get(0));
            if (usedShadowDepthBuffers >= 2) {
                glActiveTexture(GL_TEXTURE5);
                glBindTexture(GL_TEXTURE_2D, sfbDepthTextures.get(1));
            }
        }

        for (int i = 0; i < 4 && 4 + i < usedColorBuffers; ++i) {
            glActiveTexture(GL_TEXTURE7 + i);
            glBindTexture(GL_TEXTURE_2D, dfbColorTextures.get(4 + i));
        }

        glActiveTexture(GL_TEXTURE6);
        glBindTexture(GL_TEXTURE_2D, dfbDepthTextures.get(0));

        if (usedDepthBuffers >= 2) {
            glActiveTexture(GL_TEXTURE11);
            glBindTexture(GL_TEXTURE_2D, dfbDepthTextures.get(1));

            if (usedDepthBuffers >= 3) {
                glActiveTexture(GL_TEXTURE12);
                glBindTexture(GL_TEXTURE_2D, dfbDepthTextures.get(2));
            }
        }

        for (int i = 0; i < usedShadowColorBuffers; ++i) {
            glActiveTexture(GL_TEXTURE13 + i);
            glBindTexture(GL_TEXTURE_2D, sfbColorTextures.get(i));
        }

        if (noiseTextureEnabled) {
            glActiveTexture(GL_TEXTURE0 + noiseTexture.textureUnit);
            glBindTexture(GL_TEXTURE_2D, noiseTexture.getID());
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        }

        glActiveTexture(GL_TEXTURE0);

        // set depth buffer
        glFramebufferTexture2DEXT(
                GL_FRAMEBUFFER_EXT, GL_DEPTH_ATTACHMENT_EXT, GL_TEXTURE_2D, dfbDepthTextures.get(0), 0);
        // detach depth buffer
        // glFramebufferTexture2DEXT(GL_FRAMEBUFFER_EXT, GL_DEPTH_ATTACHMENT_EXT, GL_TEXTURE_2D, 0, 0);

        // composite
        glDrawBuffers(dfbDrawBuffers);
        checkGLError("pre-composite");

        for (int i = 0; i < MaxCompositePasses; ++i) {
            if (programsID[ProgramComposite + i] != 0) {
                useProgram(ProgramComposite + i);
                checkGLError(programNames[ProgramComposite + i]);
                if (activeCompositeMipmapSetting != 0) genCompositeMipmap();
                drawComposite();
            }
        }

        // reattach depth buffer
        // glFramebufferTexture2DEXT(GL_FRAMEBUFFER_EXT, GL_DEPTH_ATTACHMENT_EXT, GL_TEXTURE_2D, dfbDepthTexture, 0);

        // final render target
        isRenderingDfb = false;
        // glBindFramebufferEXT(GL_FRAMEBUFFER_EXT, 0);
        // GL11.glViewport(0, 0, mc.displayWidth, mc.displayHeight);
        mc.getFramebuffer().bindFramebuffer(true);
        if (EntityRenderer.anaglyphEnable) {
            boolean maskR = (EntityRenderer.anaglyphField != 0);
            glColorMask(maskR, !maskR, !maskR, true);
        }
        glDepthMask(true);
        glClearColor(clearColorR, clearColorG, clearColorB, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        glEnable(GL_TEXTURE_2D);
        glDisable(GL_ALPHA_TEST);
        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_ALWAYS);
        glDepthMask(false);

        // final
        checkGLError("pre-final");
        useProgram(ProgramFinal);
        checkGLError("final");
        if (activeCompositeMipmapSetting != 0) genCompositeMipmap();
        drawComposite();

        // Read gl_FragColor
        //		ByteBuffer centerPixelColor = ByteBuffer.allocateDirect(3);
        //		glReadPixels(renderWidth / 2, renderHeight / 2, 1, 1, GL11.GL_RGB, GL11.GL_BYTE, centerPixelColor);
        //		System.out.println(centerPixelColor.get(0));

        // end
        checkGLError("renderCompositeFinal");
        isCompositeRendered = true;
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_ALPHA_TEST);
        glEnable(GL_BLEND);
        glDepthFunc(GL_LEQUAL);
        glDepthMask(true);

        // glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();

        useProgram(ProgramNone);
    }

    public static void endRender() {
        if (isShadowPass) {
            // useProgram(ProgramNone);
            checkGLError("shadow endRender");
            return;
        }

        if (!isCompositeRendered) renderCompositeFinal();
        isRenderingWorld = false;

        glColorMask(true, true, true, true);
        glEnable(GL_BLEND);
        useProgram(ProgramNone);
        checkGLError("endRender end");

        // defaultTexture.bind();
    }

    public static void beginSky() {
        isRenderingSky = true;
        fogEnabled = true;
        setDrawBuffers(dfbDrawBuffers);
        useProgram(ProgramSkyTextured);
        pushEntity(-2, 0);
    }

    public static void setSkyColor(Vec3 v3color) {
        skyColorR = (float) v3color.xCoord;
        skyColorG = (float) v3color.yCoord;
        skyColorB = (float) v3color.zCoord;
        setProgramUniform3f("skyColor", skyColorR, skyColorG, skyColorB);
    }

    public static void drawHorizon() {
        Tessellator tess = Tessellator.instance;
        float farDistance = mc.gameSettings.renderDistanceChunks * 16;
        double xzq = farDistance * 0.9238;
        double xzp = farDistance * 0.3826;
        double xzn = -xzp;
        double xzm = -xzq;
        double top = 16f; // 256f-cameraPosition[1];
        double bot = -cameraPosition[1];

        tess.startDrawingQuads();
        // horizon
        tess.addVertex(xzn, bot, xzm);
        tess.addVertex(xzn, top, xzm);
        tess.addVertex(xzm, top, xzn);
        tess.addVertex(xzm, bot, xzn);

        tess.addVertex(xzm, bot, xzn);
        tess.addVertex(xzm, top, xzn);
        tess.addVertex(xzm, top, xzp);
        tess.addVertex(xzm, bot, xzp);

        tess.addVertex(xzm, bot, xzp);
        tess.addVertex(xzm, top, xzp);
        tess.addVertex(xzn, top, xzp);
        tess.addVertex(xzn, bot, xzp);

        tess.addVertex(xzn, bot, xzp);
        tess.addVertex(xzn, top, xzp);
        tess.addVertex(xzp, top, xzq);
        tess.addVertex(xzp, bot, xzq);

        tess.addVertex(xzp, bot, xzq);
        tess.addVertex(xzp, top, xzq);
        tess.addVertex(xzq, top, xzp);
        tess.addVertex(xzq, bot, xzp);

        tess.addVertex(xzq, bot, xzp);
        tess.addVertex(xzq, top, xzp);
        tess.addVertex(xzq, top, xzn);
        tess.addVertex(xzq, bot, xzn);

        tess.addVertex(xzq, bot, xzn);
        tess.addVertex(xzq, top, xzn);
        tess.addVertex(xzp, top, xzm);
        tess.addVertex(xzp, bot, xzm);

        tess.addVertex(xzp, bot, xzm);
        tess.addVertex(xzp, top, xzm);
        tess.addVertex(xzn, top, xzm);
        tess.addVertex(xzn, bot, xzm);
        // bottom
        // tess.addVertex(xzm, bot, xzm);
        // tess.addVertex(xzm, bot, xzq);
        // tess.addVertex(xzq, bot, xzq);
        // tess.addVertex(xzq, bot, xzm);

        tess.draw();
    }

    public static void preSkyList() {
        GL11.glColor3f(fogColorR, fogColorG, fogColorB);
        // GL11.glColor3f(0f, 1f, 0f);
        // glDisable(GL_FOG);

        drawHorizon();

        // glEnable(GL_FOG);
        GL11.glColor3f(skyColorR, skyColorG, skyColorB);
    }

    public static void endSky() {
        isRenderingSky = false;
        setDrawBuffers(dfbDrawBuffers);
        useProgram(lightmapEnabled ? ProgramTexturedLit : ProgramTextured);
        popEntity();
    }

    //	public static void beginSunMoon() {
    //		useProgram(ProgramNone);
    //	}

    //	public static void endSunMoon() {
    //		useProgram(lightmapEnabled ? ProgramTexturedLit : ProgramTextured);
    //	}
    public static void beginUpdateChunks() {
        // System.out.println("beginUpdateChunks");
        checkGLError("beginUpdateChunks1");
        checkFramebufferStatus("beginUpdateChunks1");
        if (!isShadowPass) {
            useProgram(ProgramTerrain);
        }
        checkGLError("beginUpdateChunks2");
        checkFramebufferStatus("beginUpdateChunks2");
    }

    public static void endUpdateChunks() {
        // System.out.println("endUpdateChunks");
        checkGLError("endUpdateChunks1");
        checkFramebufferStatus("endUpdateChunks1");
        if (!isShadowPass) {
            useProgram(ProgramTerrain);
        }
        checkGLError("endUpdateChunks2");
        checkFramebufferStatus("endUpdateChunks2");
    }

    public static boolean shouldRenderClouds(GameSettings gs) {
        checkGLError("shouldRenderClouds");
        return isShadowPass ? configCloudShadow : gs.clouds;
    }

    public static void beginClouds() {
        fogEnabled = true;
        pushEntity(-3, 0);
        useProgram(ProgramTextured);
    }

    public static void endClouds() {
        disableFog();
        popEntity();
    }

    public static void beginTerrain() {
        if (isRenderingWorld) {
            if (isShadowPass) {
                glDisable(GL11.GL_CULL_FACE);
            }
            fogEnabled = true;
            useProgram(Shaders.ProgramTerrain);
            // ShadersTex.bindNSTextures(defaultTexture.getMultiTexID()); // flat
        }
    }

    public static void endTerrain() {
        if (isRenderingWorld) {
            if (isShadowPass) {
                glEnable(GL11.GL_CULL_FACE);
            }
            useProgram(lightmapEnabled ? ProgramTexturedLit : ProgramTextured);
            // ShadersTex.bindNSTextures(defaultTexture.getMultiTexID()); // flat
        }
    }

    public static void beginBlockEntities() {
        if (isRenderingWorld) {
            checkGLError("beginBlockEntities");
            useProgram(Shaders.ProgramTerrain);
        }
    }

    public static void endBlockEntities() {
        if (isRenderingWorld) {
            checkGLError("endBlockEntities");
            useProgram(lightmapEnabled ? ProgramTexturedLit : ProgramTextured);
            ShadersTex.bindNSTextures(defaultTexture.getMultiTexID());
        }
    }

    public static void beginBlockDestroyProgress() {
        if (isRenderingWorld) {
            useProgram(ProgramTerrain);
            if (Shaders.configTweakBlockDamage) {
                setDrawBuffers(drawBuffersColorAtt0);
                glDepthMask(false);
                // GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ZERO);
            }
        }
    }

    public static void endBlockDestroyProgress() {
        if (isRenderingWorld) {
            glDepthMask(true);
            useProgram(ProgramTexturedLit);
        }
    }

    public static void beginEntities() {
        if (isRenderingWorld) {
            useProgram(ProgramEntities);
            if (programsID[activeProgram] != 0) {
                useEntityHurtFlash = (uniformEntityHurt != -1) || (uniformEntityFlash != -1);
                if (uniformEntityHurt != -1) glUniform1iARB(uniformEntityHurt, 0);
                if (uniformEntityHurt != -1) glUniform1iARB(uniformEntityFlash, 0);
            } else {
                useEntityHurtFlash = false;
            }
        }
    }

    public static void nextEntity() {
        if (isRenderingWorld) {
            useProgram(ProgramEntities);
        }
    }

    public static void beginSpiderEyes() {
        if (isRenderingWorld) {
            useProgram(ProgramSpiderEyes);
            if (programsID[ProgramSpiderEyes] == programsID[ProgramTextured]) {
                GL11.glEnable(GL11.GL_ALPHA_TEST);
                GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
            }
        }
    }

    public static void endEntities() {
        if (isRenderingWorld) {
            useProgram(lightmapEnabled ? ProgramTexturedLit : ProgramTextured);
        }
    }

    public static void setEntityHurtFlash(int hurt, int flash) {
        if (useEntityHurtFlash && isRenderingWorld && !isShadowPass) {
            if (uniformEntityHurt != -1) glUniform1iARB(uniformEntityHurt, hurt);
            if (uniformEntityFlash != -1) glUniform1iARB(uniformEntityFlash, flash >> 24);
            checkGLError("setEntityHurtFlash");
        }
    }

    public static void resetEntityHurtFlash() {
        setEntityHurtFlash(0, 0);
    }

    public static void beginLivingDamage() {
        if (isRenderingWorld) {
            ShadersTex.bindTexture(defaultTexture);
            if (!isShadowPass) {
                // useProgram(ProgramBasic);
                setDrawBuffers(drawBuffersColorAtt0);
            }
        }
    }

    public static void endLivingDamage() {
        if (isRenderingWorld) {
            if (!isShadowPass) {
                // useProgram(ProgramEntities);
                setDrawBuffers(programsDrawBuffers[ProgramEntities]);
            }
        }
    }

    public static void beginLitParticles() {
        // GL11.glDepthMask(false);
        Tessellator.instance.setNormal(0f, 0f, 0f);
        useProgram(ProgramTexturedLit);
    }

    public static void beginParticles() {
        // GL11.glDepthMask(false);
        Tessellator.instance.setNormal(0f, 0f, 0f);
        useProgram(ProgramTextured);
    }

    public static void endParticles() {
        Tessellator.instance.setNormal(0f, 0f, 0f);
        useProgram(ProgramTexturedLit);
    }

    public static void preWater() {
        if (isShadowPass) {
            if (usedShadowDepthBuffers >= 2) {
                // copy depth buffer to shadowtex1
                glActiveTexture(GL_TEXTURE5);
                checkGLError("pre copy shadow depth");
                glCopyTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 0, 0, shadowMapWidth, shadowMapHeight);
                checkGLError("copy shadow depth");
                glActiveTexture(GL_TEXTURE0);
            }
        } else {
            if (usedDepthBuffers >= 2) {
                // copy depth buffer to depthtex1
                glActiveTexture(GL_TEXTURE11);
                checkGLError("pre copy depth");
                glCopyTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 0, 0, renderWidth, renderHeight);
                checkGLError("copy depth");
                glActiveTexture(GL_TEXTURE0);
            }
            ShadersTex.bindNSTextures(defaultTexture.getMultiTexID()); // flat
        }
    }

    //	public static void beginWaterFancy() {
    //		if (isRenderingWorld)
    //		{
    //			if (!isShadowPass) {
    //				// program water
    //				useProgram(Shaders.ProgramWater);
    //				GL11.glEnable(GL11.GL_BLEND);
    //				//setDrawBuffers(drawBuffersNone);
    //			}
    //		}
    //	}
    //
    //	public static void midWaterFancy() {
    //		if (isRenderingWorld)
    //		{
    //			if (!isShadowPass) {
    //				setDrawBuffers(programsDrawBuffers[Shaders.ProgramWater]);
    //			}
    //		}
    //	}

    public static void beginWater() {
        if (isRenderingWorld) {
            if (!isShadowPass) {
                // program water
                useProgram(Shaders.ProgramWater);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glDepthMask(true);
            } else {
                GL11.glDepthMask(true);
            }
        }
    }

    public static void endWater() {
        if (isRenderingWorld) {
            if (isShadowPass) {
                // glFramebufferTexture2DEXT(GL_FRAMEBUFFER_EXT, GL_DEPTH_ATTACHMENT_EXT, GL_TEXTURE_2D,
                // sfbDepthTexture, 0);
            } else {
                // glFramebufferTexture2DEXT(GL_FRAMEBUFFER_EXT, GL_DEPTH_ATTACHMENT_EXT, GL_TEXTURE_2D,
                // dfbDepthTexture, 0);
            }
            useProgram(lightmapEnabled ? ProgramTexturedLit : ProgramTextured);
        }
    }

    public static void readCenterDepth() {
        if (!isShadowPass) {
            // Read depth buffer at center of screen for DOF
            if (centerDepthSmoothEnabled) {
                tempDirectFloatBuffer.clear();
                glReadPixels(
                        renderWidth / 2,
                        renderHeight / 2,
                        1,
                        1,
                        GL11.GL_DEPTH_COMPONENT,
                        GL11.GL_FLOAT,
                        tempDirectFloatBuffer);
                centerDepth = tempDirectFloatBuffer.get(0);

                // Smooth depth value
                float fadeScalar = diffSystemTime * 0.01f;
                float fadeFactor = (float) Math.exp(Math.log(0.5) * fadeScalar / centerDepthSmoothHalflife);
                centerDepthSmooth = centerDepthSmooth * (fadeFactor) + centerDepth * (1 - fadeFactor);
            }
        }
    }

    public static void beginWeather() {
        if (!isShadowPass) {
            if (usedDepthBuffers >= 3) {
                // copy depth buffer to depthtex2
                glActiveTexture(GL_TEXTURE12);
                glCopyTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 0, 0, renderWidth, renderHeight);
                glActiveTexture(GL_TEXTURE0);
            }
            // GL11.glDepthMask(false);
            glEnable(GL_DEPTH_TEST);
            glEnable(GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            glEnable(GL_ALPHA_TEST);
            useProgram(Shaders.ProgramWeather);
            // glFramebufferTexture2DEXT(GL_FRAMEBUFFER_EXT, GL_DEPTH_ATTACHMENT_EXT, GL_TEXTURE_2D,
            // dfbWaterDepthTexture, 0);
        }

        // if (isShadowPass) {
        //	glBindFramebufferEXT(GL_FRAMEBUFFER_EXT, 0); // will be set to sbf in endHand()
        // }
    }

    public static void endWeather() {
        // GL11.glDepthMask(true);
        glDisable(GL_BLEND);
        useProgram(ProgramTexturedLit);
        // glFramebufferTexture2DEXT(GL_FRAMEBUFFER_EXT, GL_DEPTH_ATTACHMENT_EXT, GL_TEXTURE_2D, dfbDepthTexture, 0);

        // if (isShadowPass) {
        //	glBindFramebufferEXT(GL_FRAMEBUFFER_EXT, sfb); // was set to 0 in beginWeather()
        // }
    }

    public static void beginProjectRedHalo() {
        useProgram(ProgramBasic);
    }

    public static void endProjectRedHalo() {
        useProgram(ProgramTexturedLit);
    }

    public static void applyHandDepth() {
        if (Shaders.configHandDepthMul != 1.0) {
            GL11.glScaled(1.0, 1.0, Shaders.configHandDepthMul);
        }
    }

    public static void beginHand() {
        // glEnable(GL_BLEND);
        // glDisable(GL_BLEND);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glMatrixMode(GL_MODELVIEW);
        useProgram(Shaders.ProgramHand);
        checkGLError("beginHand");
        checkFramebufferStatus("beginHand");
    }

    public static void endHand() {
        // glDisable(GL_BLEND);
        checkGLError("pre endHand");
        checkFramebufferStatus("pre endHand");
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        // useProgram(lightmapEnabled ? ProgramTexturedLit : ProgramTextured);
        checkGLError("endHand");
    }

    public static void beginFPOverlay() {
        // GL11.glDisable(GL11.GL_BLEND);
        // GL11.glEnable(GL11.GL_BLEND);
        // GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    public static void endFPOverlay() {
        // GL11.glDisable(GL11.GL_BLEND);
    }

    // ----------------------------------------

    public static void glEnableWrapper(int cap) {
        glEnable(cap);
        if (cap == GL_TEXTURE_2D) enableTexture2D();
        else if (cap == GL_FOG) enableFog();
    }

    public static void glDisableWrapper(int cap) {
        glDisable(cap);
        if (cap == GL_TEXTURE_2D) disableTexture2D();
        else if (cap == GL_FOG) disableFog();
    }

    public static void sglEnableT2D(int cap) {
        glEnable(cap); // GL_TEXTURE_2D
        enableTexture2D();
    }

    public static void sglDisableT2D(int cap) {
        glDisable(cap); // GL_TEXTURE_2D
        disableTexture2D();
    }

    public static void sglEnableFog(int cap) {
        glEnable(cap); // GL_FOG
        enableFog();
    }

    public static void sglDisableFog(int cap) {
        glDisable(cap); // GL_FOG
        disableFog();
    }

    public static void enableTexture2D() {
        if (isRenderingSky) {
            useProgram(ProgramSkyTextured);
        } else if (activeProgram == ProgramBasic) {
            useProgram(lightmapEnabled ? ProgramTexturedLit : ProgramTextured);
        }
    }

    public static void disableTexture2D() {
        if (isRenderingSky) {
            useProgram(ProgramSkyBasic);
        } else if (activeProgram == ProgramTextured || activeProgram == ProgramTexturedLit) {
            useProgram(ProgramBasic);
        }
    }

    public static void enableFog() {
        fogEnabled = true;
        setProgramUniform1i("fogMode", fogMode);
    }

    public static void disableFog() {
        fogEnabled = false;
        setProgramUniform1i("fogMode", 0);
    }

    public static void sglFogi(int pname, int param) {
        GL11.glFogi(pname, param);
        if (pname == GL11.GL_FOG_MODE) {
            fogMode = param;
            if (fogEnabled) setProgramUniform1i("fogMode", fogMode);
        }
    }

    public static void enableLightmap() {
        lightmapEnabled = true;
        if (activeProgram == ProgramTextured) {
            useProgram(ProgramTexturedLit);
        }
    }

    public static void disableLightmap() {
        lightmapEnabled = false;
        if (activeProgram == ProgramTexturedLit) {
            useProgram(ProgramTextured);
        }
    }

    public static int entityData[] = new int[32];
    public static int entityDataIndex = 0;

    public static int getEntityData() {
        return entityData[entityDataIndex * 2];
    }

    public static int getEntityData2() {
        return entityData[entityDataIndex * 2 + 1];
    }

    public static int setEntityData1(int data1) {
        entityData[entityDataIndex * 2] = (entityData[entityDataIndex * 2] & 0xFFFF) | (data1 << 16);
        return data1;
    }

    public static int setEntityData2(int data2) {
        entityData[entityDataIndex * 2 + 1] = (entityData[entityDataIndex * 2 + 1] & 0xFFFF0000) | (data2 & 0xFFFF);
        return data2;
    }

    public static void pushEntity(int data0, int data1) {
        entityDataIndex++;
        entityData[entityDataIndex * 2] = (data0 & 0xFFFF) | (data1 << 16);
        entityData[entityDataIndex * 2 + 1] = 0;
    }

    public static void pushEntity(int data0) {
        entityDataIndex++;
        entityData[entityDataIndex * 2] = (data0 & 0xFFFF);
        entityData[entityDataIndex * 2 + 1] = 0;
    }

    public static void pushEntity(Block block) {
        entityDataIndex++;
        entityData[entityDataIndex * 2] =
                (block.blockRegistry.getIDForObject(block) & 0xFFFF) | (block.getRenderType() << 16);
        entityData[entityDataIndex * 2 + 1] = 0;
    }

    public static void pushEntity(RenderBlocks rb, Block block, int x, int y, int z) {
        entityDataIndex++;
        entityData[entityDataIndex * 2] =
                (block.blockRegistry.getIDForObject(block) & 0xFFFF) | (block.getRenderType() << 16);
        entityData[entityDataIndex * 2 + 1] = rb.blockAccess.getBlockMetadata(x, y, z);
    }

    public static void popEntity() {
        entityData[entityDataIndex * 2] = 0;
        entityData[entityDataIndex * 2 + 1] = 0;
        entityDataIndex--;
    }

    // ----------------------------------------

}
