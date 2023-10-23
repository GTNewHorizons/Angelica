// Code written by daxnitro modified by id_miner and karyonix.
// Do what you want with it but give us some credit if you use it in whole or in part.

package com.gtnewhorizons.angelica.client;

import com.gtnewhorizons.angelica.Tags;
import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import com.gtnewhorizons.angelica.mixins.interfaces.IModelRenderer;
import lombok.Getter;
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
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.joml.Vector3d;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import static org.lwjgl.util.glu.GLU.gluErrorString;

public class Shaders {

    public static final String versionString = Tags.VERSION;
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

    private static float[] upPosModelView = new float[] { 0.0F, 100.0F, 0.0F, 0.0F };
    private static float[] sunPosModelView = new float[] { 0.0F, 100.0F, 0.0F, 0.0F };
    private static float[] moonPosModelView = new float[] { 0.0F, -100.0F, 0.0F, 0.0F };
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
    @Getter
    private static float eyePosY = 0;
    private static float centerDepth = 0;
    private static float centerDepthSmooth = 0;
    private static float centerDepthSmoothHalflife = 1.0f;
    private static boolean centerDepthSmoothEnabled = false;
    private static int superSamplingLevel = 1;

    private static boolean updateChunksErrorRecorded = false;

    private static boolean lightmapEnabled = false;
    @Getter
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

    private static final double[] previousCameraPosition = new double[3];
    private static final double[] cameraPosition = new double[3];


    public static Vector3d getCameraPosition() {
        return new Vector3d(cameraPosition[0], cameraPosition[1], cameraPosition[2]);
    }

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

    private static final int[] gbuffersFormat = new int[MaxColorBuffers];

    // Program stuff

    public static int activeProgram = 0;

    public static final int ProgramNone = 0;
    public static final int ProgramBasic = 1;
    public static final int ProgramTextured = 2;
    public static final int ProgramTexturedLit = 3;
    public static final int ProgramSkyBasic = 4;
    public static final int ProgramSkyTextured = 5;
    public static final int ProgramClouds = 6;
    public static final int ProgramTerrain = 7;
    public static final int ProgramTerrainSolid = 8;
    public static final int ProgramTerrainCutoutMip = 9;
    public static final int ProgramTerrainCutout = 10;
    public static final int ProgramDamagedBlock = 11;
    public static final int ProgramWater = 12;
    public static final int ProgramBlock = 13;
    public static final int ProgramBeaconBeam = 14;
    public static final int ProgramItem = 15;
    public static final int ProgramEntities = 16;
    public static final int ProgramArmorGlint = 17;
    public static final int ProgramSpiderEyes = 18;
    public static final int ProgramHand = 19;
    public static final int ProgramWeather = 20;
    public static final int ProgramComposite = 21;
    public static final int ProgramComposite1 = 22;
    public static final int ProgramComposite2 = 23;
    public static final int ProgramComposite3 = 24;
    public static final int ProgramComposite4 = 25;
    public static final int ProgramComposite5 = 26;
    public static final int ProgramComposite6 = 27;
    public static final int ProgramComposite7 = 28;
    public static final int ProgramFinal = 29;
    public static final int ProgramShadow = 30;
    public static final int ProgramShadowSolid = 31;
    public static final int ProgramShadowCutout = 32;
    public static final int ProgramDeferred = 33;
    public static final int ProgramDeferred1 = 34;
    public static final int ProgramDeferred2 = 35;
    public static final int ProgramDeferred3 = 36;
    public static final int ProgramDeferred4 = 37;
    public static final int ProgramDeferred5 = 38;
    public static final int ProgramDeferred6 = 39;
    public static final int ProgramDeferred7 = 40;
    public static final int ProgramHandWater = 41;
    public static final int ProgramDeferredLast = 42;
    public static final int ProgramCompositeLast = 43;
    public static final int ProgramCount = 44;
    public static final int MaxCompositePasses = 8;
    public static final int MaxDeferredPasses = 8;

    private static final String[] programNames = new String[] { "", "gbuffers_basic", "gbuffers_textured",
            "gbuffers_textured_lit", "gbuffers_skybasic", "gbuffers_skytextured", "gbuffers_clouds", "gbuffers_terrain",
            "gbuffers_terrain_solid", "gbuffers_terrain_cutout_mip", "gbuffers_terrain_cutout", "gbuffers_damagedblock", "gbuffers_water",
            "gbuffers_block", "gbuffers_beaconbeam", "gbuffers_item", "gbuffers_entities", "gbuffers_armor_glint", "gbuffers_spidereyes",
            "gbuffers_hand", "gbuffers_weather", "composite", "composite1", "composite2", "composite3", "composite4", "composite5",
            "composite6", "composite7", "final", "shadow", "shadow_solid", "shadow_cutout", "deferred", "deferred1", "deferred2", "deferred3",
            "deferred4", "deferred5", "deferred6", "deferred7", "gbuffers_hand_water", "deferred_last", "composite_last"
            };

    private static final int[] programBackups = new int[] {
            ProgramNone, // none
            ProgramNone, // basic
            ProgramBasic, // textured
            ProgramTextured, // textured/lit
            ProgramBasic, // skybasic
            ProgramTextured, // skytextured
            ProgramTextured, // clouds
            ProgramTexturedLit, // terrain
            ProgramTerrain, // terrain solid
            ProgramTerrain, // terrain solid cutout mip
            ProgramTerrain, // terrain solid cutout
            ProgramTerrain, // block damage
            ProgramTerrain, // water
            ProgramTerrain, // block
            ProgramTextured, // beacon beam
            ProgramTexturedLit, // item
            ProgramTexturedLit, // entities
            ProgramTextured, // armor glint
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
            ProgramShadow, // shadow solid
            ProgramShadow, // shadow solid cutout
            ProgramNone, // deferred
            ProgramNone, // deferred1
            ProgramNone, // deferred2
            ProgramNone, // deferred3
            ProgramNone, // deferred4
            ProgramNone, // deferred5
            ProgramNone, // deferred6
            ProgramNone, // deferred7
            ProgramHand, // hand water
            ProgramNone, // deferred last
            ProgramNone, // composite last

    };

    private static final int[] programsID = new int[ProgramCount];
    private static final int[] programsRef = new int[ProgramCount];
    private static int programIDCopyDepth = 0;

    private static final String[] programsDrawBufSettings = new String[ProgramCount];
    private static String newDrawBufSetting = null;
    private static final IntBuffer[] programsDrawBuffers = new IntBuffer[ProgramCount];
    static IntBuffer activeDrawBuffers = null;

    private static final String[] programsColorAtmSettings = new String[ProgramCount];
    private static String newColorAtmSetting = null;
    private static String activeColorAtmSettings = null;

    private static final int[] programsCompositeMipmapSetting = new int[ProgramCount];
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
    public static final String[] texMinFilDesc = { "Nearest", "Nearest-Nearest", "Nearest-Linear" };
    public static final String[] texMagFilDesc = { "Nearest", "Linear" };
    public static final int[] texMinFilValue = { GL11.GL_NEAREST, GL11.GL_NEAREST_MIPMAP_NEAREST,
            GL11.GL_NEAREST_MIPMAP_LINEAR };
    public static final int[] texMagFilValue = { GL11.GL_NEAREST, GL11.GL_LINEAR };

    // shaderpack
    public static IShaderPack shaderPack = null;
    public static File currentshader;
    public static String currentshadername;
    public static String packNameNone = "(none)";
    public static String packNameDefault = "(internal)";
    public static String shaderpacksdirname = "shaderpacks";
    public static String optionsfilename = "optionsshaders.txt";
    public static File shadersdir = new File(Minecraft.getMinecraft().mcDataDir, "shaders");
    public static File shaderpacksdir = new File(Minecraft.getMinecraft().mcDataDir, shaderpacksdirname);
    public static File configFile = new File(Minecraft.getMinecraft().mcDataDir, optionsfilename);

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
    public static float fogDensity = 0;
    public static float fogStart = 0;
    public static float fogEnd = 0;

    public static float fogColorR, fogColorG, fogColorB;
    public static float shadowIntervalSize = 2.0f;
    public static int terrainIconSize = 16;
    public static int[] terrainTextureSize = new int[2];

    private static HFNoiseTexture noiseTexture;
    private static boolean noiseTextureEnabled = false;
    private static int noiseTextureResolution = 256;

    // direct buffers
    private static final int bigBufferSize = (16 * 12 + MaxColorBuffers + MaxDepthBuffers + MaxShadowColorBuffers + MaxShadowDepthBuffers + MaxDrawBuffers * 8 + MaxDrawBuffers * ProgramCount) * 4;
    private static final ByteBuffer bigBuffer = (ByteBuffer) BufferUtils.createByteBuffer(bigBufferSize).limit(0);

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

    public static void loadConfig() {
        AngelicaTweaker.LOGGER.info("[Shaders] Loading configuration.");
            try {
            if (!shaderpacksdir.exists()) shaderpacksdir.mkdir();
        } catch (Exception e) {
            AngelicaTweaker.LOGGER.warn("[Shaders] Failed openning shaderpacks directory.");
        }

        shadersConfig = new Properties();
        shadersConfig.setProperty("shaderPack", "");
        if (configFile.exists()) {
            try {
                FileReader reader = new FileReader(configFile);
                shadersConfig.load(reader);
                reader.close();
            } catch (Exception ignored) {}
        }

        if (!configFile.exists()) {
            try {
                storeConfig();
            } catch (Exception ignored) {}
        }
        configNormalMap = Boolean.parseBoolean(shadersConfig.getProperty("normalMapEnabled", "true"));
        configSpecularMap = Boolean.parseBoolean(shadersConfig.getProperty("specularMapEnabled", "true"));
        configTweakBlockDamage = Boolean.parseBoolean(shadersConfig.getProperty("tweakBlockDamage", shadersConfig.getProperty("dtweak", "false")));
        configCloudShadow = Boolean.parseBoolean(shadersConfig.getProperty("cloudShadow", "true"));
        configHandDepthMul = Float.parseFloat(shadersConfig.getProperty("handDepthMul", "0.125"));
        configRenderResMul = Float.parseFloat(shadersConfig.getProperty("renderResMul", "1.0"));
        configShadowResMul = Float.parseFloat(shadersConfig.getProperty("shadowResMul", "1.0"));
        configShadowClipFrustrum = Boolean.parseBoolean(shadersConfig.getProperty("shadowClipFrustrum", "true"));
        configOldLighting = Boolean.parseBoolean(shadersConfig.getProperty("oldLighting", "false"));
        configTexMinFilB = Integer.parseInt(shadersConfig.getProperty("TexMinFilB", "0")) % texMinFilRange;
        configTexMinFilN = Integer.parseInt(shadersConfig.getProperty("TexMinFilN", Integer.toString(configTexMinFilB))) % texMinFilRange;
        configTexMinFilS = Integer.parseInt(shadersConfig.getProperty("TexMinFilS", Integer.toString(configTexMinFilB))) % texMinFilRange;
        configTexMagFilB = Integer.parseInt(shadersConfig.getProperty("TexMagFilB", "0")) % texMagFilRange;
        configTexMagFilN = Integer.parseInt(shadersConfig.getProperty("TexMagFilN", "0")) % texMagFilRange;
        configTexMagFilS = Integer.parseInt(shadersConfig.getProperty("TexMagFilS", "0")) % texMagFilRange;
        currentshadername = shadersConfig.getProperty("shaderPack", packNameDefault);
        loadShaderPack();
    }

    public static void storeConfig() {
        AngelicaTweaker.LOGGER.info("[Shaders] Save configuration.");
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
        } catch (Exception ignored) {}
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
                } catch (Exception e) {}
            }
        }
        if (shaderPack != null) {
            AngelicaTweaker.LOGGER.info("[Shaders] Loaded shaderpack.");
        } else {
            AngelicaTweaker.LOGGER.error("[Shaders] Did not load shaderpack.");
            shaderPack = new ShaderPackNone();
        }
    }

    public static List<String> listofShaders() {
        List<String> list = new ArrayList<>();
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
        } catch (Exception ignored) {}
        return list;
    }

    static String versiontostring(int vv) {
        String vs = Integer.toString(vv);
        return Integer.toString(Integer.parseInt(vs.substring(1, 3))) + "."
                + Integer.toString(Integer.parseInt(vs.substring(3, 5)))
                + "."
                + Integer.toString(Integer.parseInt(vs.substring(5)));
    }


    public static int checkFramebufferStatus(String location) {
        int status = glCheckFramebufferStatusEXT(GL_FRAMEBUFFER_EXT);
        if (status != GL_FRAMEBUFFER_COMPLETE_EXT)
            AngelicaTweaker.LOGGER.error(String.format("FramebufferStatus 0x%04X at %s", status, location));
        return status;
    }

    public static int checkGLError(String location) {
        if (true) return 0;
        int errorCode = GL11.glGetError();
        if (errorCode != GL11.GL_NO_ERROR) {
            boolean skipPrint = false;
            if (!skipPrint) {
                if (errorCode == GL_INVALID_FRAMEBUFFER_OPERATION_EXT) {
                    int status = glCheckFramebufferStatusEXT(GL_FRAMEBUFFER_EXT);
                    AngelicaTweaker.LOGGER.error(String.format("GL error 0x%04X: %s (Fb status 0x%04X) at %s", errorCode, gluErrorString(errorCode), status, location));
                } else {
                    AngelicaTweaker.LOGGER.error(String.format("GL error 0x%04X: %s at %s", errorCode, gluErrorString(errorCode), location));
                }
            }
        }
        return errorCode;
    }

    public static int checkGLError(String location, String info) {
        int errorCode = GL11.glGetError();
        if (errorCode != GL11.GL_NO_ERROR) {
            AngelicaTweaker.LOGGER.error(String.format("GL error 0x%04x: %s at %s %s", errorCode, gluErrorString(errorCode), location, info));
        }
        return errorCode;
    }

    public static int checkGLError(String location, String info1, String info2) {
        int errorCode = GL11.glGetError();
        if (errorCode != GL11.GL_NO_ERROR) {
            AngelicaTweaker.LOGGER.error(String.format("GL error 0x%04x: %s at %s %s %s", errorCode, gluErrorString(errorCode), location, info1, info2));
        }
        return errorCode;
    }

    private static String printChatAndLogError(String str) {
        mc.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText(str));
        AngelicaTweaker.LOGGER.error(str);
        return str;
    }

    public static void printIntBuffer(String title, IntBuffer buf) {
        StringBuilder sb = new StringBuilder(128);
        sb.append(title).append(" [pos ").append(buf.position()).append(" lim ").append(buf.limit()).append(" cap ")
                .append(buf.capacity()).append(" :");
        for (int lim = buf.limit(), i = 0; i < lim; ++i) sb.append(" ").append(buf.get(i));
        sb.append("]");
        AngelicaTweaker.LOGGER.debug(sb.toString());
    }

    public static void startup(Minecraft mc) {
        Shaders.mc = mc;
        AngelicaTweaker.LOGGER.info("Angelica version " + versionString);
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
        if (true) return;
        if (isInitialized) {
            return;
        }
        mc = Minecraft.getMinecraft();
        checkGLError("Shaders.init pre");
        capabilities = GLContext.getCapabilities();
        AngelicaTweaker.LOGGER.debug(
                "[Shaders] OpenGL 2.0 = " + toStringYN(capabilities.OpenGL20)
                        + "    2.1 = " + toStringYN(capabilities.OpenGL21)
                        + "    3.0 = " + toStringYN(capabilities.OpenGL30)
                        + "    3.2 = " + toStringYN(capabilities.OpenGL32));
        if (!capabilities.OpenGL21) {
            printChatAndLogError("[Shaders] No OpenGL 2.1.");
        }
        if (!capabilities.GL_EXT_framebuffer_object) {
            printChatAndLogError("[Shaders] No EXT_framebuffer_object.");
        }
        if (!capabilities.OpenGL20 || !capabilities.GL_EXT_framebuffer_object) {
            printChatAndLogError("[Shaders] Your GPU is not compatible with the Shaders mod.");
        }

        hasGlGenMipmap = capabilities.OpenGL30;
        dfbDrawBuffers.position(0).limit(MaxDrawBuffers);
        dfbColorTextures.position(0).limit(MaxColorBuffers);
        dfbDepthTextures.position(0).limit(MaxDepthBuffers);
        sfbDrawBuffers.position(0).limit(MaxDrawBuffers);
        sfbDepthTextures.position(0).limit(MaxShadowDepthBuffers);
        sfbColorTextures.position(0).limit(MaxShadowColorBuffers);

        int maxDrawBuffers = GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS);
        int maxColorAttach = GL11.glGetInteger(GL_MAX_COLOR_ATTACHMENTS_EXT);
        AngelicaTweaker.LOGGER.debug("[Shaders] GL_MAX_DRAW_BUFFERS = " + maxDrawBuffers);
        AngelicaTweaker.LOGGER.debug("[Shaders] GL_MAX_COLOR_ATTACHMENTS_EXT = " + maxColorAttach);
        AngelicaTweaker.LOGGER.debug("[Shaders] GL_MAX_TEXTURE_IMAGE_UNITS = " + GL11.glGetInteger(GL20.GL_MAX_TEXTURE_IMAGE_UNITS));

        usedColorBuffers = 4;
        usedDepthBuffers = 1;
        usedShadowColorBuffers = 0;
        usedShadowDepthBuffers = 0;
        usedColorAttachs = 1;
        usedDrawBuffers = 1;
        Arrays.fill(gbuffersFormat, GL11.GL_RGBA);
        // Arrays.fill(gbuffersClear, true);
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
            if (programNames[i].isEmpty()) {
                programsID[i] = programsRef[i] = 0;
                programsDrawBufSettings[i] = null;
                programsColorAtmSettings[i] = null;
                programsCompositeMipmapSetting[i] = 0;
            } else {
                newDrawBufSetting = null;
                newColorAtmSetting = null;
                newCompositeMipmapSetting = 0;
                final String str = "/shaders/" + programNames[i];
                int pr = setupProgram( i, str + ".vsh", str + ".fsh");
                programsID[i] = programsRef[i] = pr;
                programsDrawBufSettings[i] = (pr != 0) ? newDrawBufSetting : null;
                programsColorAtmSettings[i] = (pr != 0) ? newColorAtmSetting : null;
                programsCompositeMipmapSetting[i] = (pr != 0) ? newCompositeMipmapSetting : 0;
            }
        }

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
                        int d = GL11.GL_NONE;
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
            printChatAndLogError(
                    "[Shaders] Not enough draw buffers! Requires " + usedDrawBuffers
                            + ".  Has "
                            + maxDrawBuffers
                            + ".");
        }

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

    public static void resetDisplayList() {
        if (true) return;
        ++numberResetDisplayList;
        AngelicaTweaker.LOGGER.debug("Reset model renderers");
        if (Shaders.useMidTexCoordAttrib || Shaders.useMultiTexCoord3Attrib) {
            for (Render ren : RenderManager.instance.entityRenderMap.values()) {
                if (ren instanceof RendererLivingEntity rle) {
                    // System.out.format("Reset %s\n", rle.toString());
                    resetDisplayListModel(rle.mainModel);
                    resetDisplayListModel(rle.renderPassModel);
                }
            }
        }
        AngelicaTweaker.LOGGER.debug("Reset world renderers");
        mc.renderGlobal.loadRenderers();
    }

    public static void resetDisplayListModel(ModelBase mbase) {
        if (true) return;
        if (mbase != null) {
            for (ModelRenderer obj : mbase.boxList) {
                if (obj != null) {
                    resetDisplayListModelRenderer(obj);
                }
            }
        }
    }

    public static void resetDisplayListModelRenderer(ModelRenderer mrr) {
        if (true) return;
        ((IModelRenderer) mrr).angelica$resetDisplayList();

        if (mrr.childModels != null) {
            for (int i = 0, n = mrr.childModels.size(); i < n; ++i) {
                resetDisplayListModelRenderer((ModelRenderer) mrr.childModels.get(i));
            }
        }
    }

    // ----------------------------------------

      private static int setupProgram(int program, String vShaderPath, String fShaderPath) {
      if (true) return 0;
        checkGLError("pre setupProgram");
        int programid = glCreateProgramObjectARB();
        checkGLError("create");
          if (programid == 0) {
              return programid;
          }
          progUseEntityAttrib = false;
          progUseMidTexCoordAttrib = false;
          int vShader = createVertShader(vShaderPath);
          int fShader = createFragShader(fShaderPath);
          checkGLError("create");
          if (vShader == 0 && fShader == 0) {
              glDeleteObjectARB(programid);
              programid = 0;
              return programid;
          }

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
          int valid = GL20.glGetProgrami(programid, GL20.GL_VALIDATE_STATUS);
          if (valid == GL11.GL_TRUE) {
              AngelicaTweaker.LOGGER.debug("Program " + programNames[program] + " loaded");
          } else {
              printChatAndLogError("[Shaders] Error : Invalid program " + programNames[program]);
              glDeleteObjectARB(programid);
              programid = 0;
          }

          return programid;
    }

    private static InputStream locateShaderStream(String path) {
        try {
            return shaderPack.getResourceAsStream(path);
        } catch (Exception e) {
            try {
                return FileUtils.openInputStream(new File(path));
            } catch (Exception e2) {
                throw new RuntimeException("Could not locate shader input " + path, e);
            }
        }
    }

    private static String getPreprocessedShaderSources(String filename) {
        filename = filename.replace('\\', '/');
        int lastSlash = filename.lastIndexOf('/');
        final String basename = (lastSlash == -1) ? "/" : filename.substring(0, lastSlash + 1);
        try (InputStream is = locateShaderStream(filename)) {
            if (is == null) {
                AngelicaTweaker.LOGGER.debug("PreprocessedShader not found: " + filename);
                throw new FileNotFoundException(filename);
            }
            String source = IOUtils.toString(is, StandardCharsets.UTF_8).replace("\r\n", "\n");
            StringBuffer output = new StringBuffer(2 * source.length() + 64);

            final Matcher includes = INCLUDE_PATTERN.matcher(source);
            while (includes.find()) {
                final String relPath = includes.group(1).replace('\\', '/');
                final String path = relPath.startsWith("/") ? ("/shaders" + relPath) : (basename + relPath);
                final String src = getPreprocessedShaderSources(path);
                includes.appendReplacement(output, src);
            }
            includes.appendTail(output);
            return output.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static int createVertShader(String filename) {
        if (true) return 0;
        int vertShader = glCreateShaderObjectARB(GL_VERTEX_SHADER_ARB);
        if (vertShader == 0) {
            return 0;
        }
        String shaderSrc;
        try {
            shaderSrc = getPreprocessedShaderSources(filename);

            for (String line : StringUtils.split(shaderSrc, '\n')) {
                processVertShaderLine(line);
            }
        } catch (Exception e) {
            glDeleteObjectARB(vertShader);
            return 0;
        }

        glShaderSourceARB(vertShader, shaderSrc);
        glCompileShaderARB(vertShader);
        printLogInfo(vertShader, filename);
        return vertShader;
    }

    private static void processVertShaderLine(String line) {
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

    private static final Pattern gbufferFormatPattern = Pattern
            .compile("[ \t]*const[ \t]*int[ \t]*(\\w+)Format[ \t]*=[ \t]*([RGBA81632F]*)[ \t]*;.*");
    private static final Pattern gbufferMipmapEnabledPattern = Pattern
            .compile("[ \t]*const[ \t]*bool[ \t]*(\\w+)MipmapEnabled[ \t]*=[ \t]*true[ \t]*;.*");

    private static final Pattern INCLUDE_PATTERN = Pattern
            .compile("^\\s*#include\\s+\"([A-Za-z0-9_\\/\\.]+)\".*$", Pattern.MULTILINE | Pattern.UNIX_LINES);

    private static int createFragShader(String filename) {
        if (true) return 0;
        int fragShader = glCreateShaderObjectARB(GL_FRAGMENT_SHADER_ARB);
        if (fragShader == 0) {
            return 0;
        }
        String shaderSrc;
        try {
            shaderSrc = getPreprocessedShaderSources(filename);

            for (String line : StringUtils.split(shaderSrc, '\n')) {
                processFragShaderLine(line, filename);
            }
        } catch (Exception e) {
            glDeleteObjectARB(fragShader);
            return 0;
        }

        glShaderSourceARB(fragShader, shaderSrc);
        glCompileShaderARB(fragShader);
        printLogInfo(fragShader, filename);
        return fragShader;
    }

    // TODO: refactor this mess
    private static void processFragShaderLine(String line, String filename) {
        if (line.matches("#version .*")) {
            // Do nothing
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
            if (gbuffersFormat[1] == GL11.GL_RGBA) gbuffersFormat[1] = GL30.GL_RGBA32F;
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
            AngelicaTweaker.LOGGER.debug("Shadow map resolution: " + parts[2]);
            spShadowMapWidth = spShadowMapHeight = Integer.parseInt(parts[2]);
            shadowMapWidth = shadowMapHeight = Math.round(spShadowMapWidth * configShadowResMul);

        } else if (line.matches("[ \t]*const[ \t]*int[ \t]*shadowMapResolution[ \t]*=[ \t]*-?[0-9.]+f?;.*")) {
            String[] parts = line.split("(=[ \t]*|;)");
            AngelicaTweaker.LOGGER.debug("Shadow map resolution: " + parts[1]);
            spShadowMapWidth = spShadowMapHeight = Integer.parseInt(parts[1]);
            shadowMapWidth = shadowMapHeight = Math.round(spShadowMapWidth * configShadowResMul);

        } else if (line.matches("/\\* SHADOWFOV:[0-9\\.]+ \\*/.*")) {
            String[] parts = line.split("(:| )", 4);
            AngelicaTweaker.LOGGER.debug("Shadow map field of view: " + parts[2]);
            shadowMapFOV = Float.parseFloat(parts[2]);
            shadowMapIsOrtho = false;

            // Shadow distance
        } else if (line.matches("/\\* SHADOWHPL:[0-9\\.]+ \\*/.*")) {
            String[] parts = line.split("(:| )", 4);
            AngelicaTweaker.LOGGER.debug("Shadow map half-plane: " + parts[2]);
            shadowMapHalfPlane = Float.parseFloat(parts[2]);
            shadowMapIsOrtho = true;

        } else if (line.matches("[ \t]*const[ \t]*float[ \t]*shadowDistance[ \t]*=[ \t]*-?[0-9.]+f?;.*")) {
            String[] parts = line.split("(=[ \t]*|;)");
            AngelicaTweaker.LOGGER.debug("Shadow map distance: " + parts[1]);
            shadowMapHalfPlane = Float.parseFloat(parts[1]);
            shadowMapIsOrtho = true;

        } else if (line.matches("[ \t]*const[ \t]*float[ \t]*shadowIntervalSize[ \t]*=[ \t]*-?[0-9.]+f?;.*")) {
            String[] parts = line.split("(=[ \t]*|;)");
            AngelicaTweaker.LOGGER.debug("Shadow map interval size: " + parts[1]);
            shadowIntervalSize = Float.parseFloat(parts[1]);

        } else if (line.matches("[ \t]*const[ \t]*bool[ \t]*generateShadowMipmap[ \t]*=[ \t]*true[ \t]*;.*")) {
            AngelicaTweaker.LOGGER.debug("Generate shadow mipmap");
            Arrays.fill(shadowMipmapEnabled, true);

        } else if (line.matches("[ \t]*const[ \t]*bool[ \t]*generateShadowColorMipmap[ \t]*=[ \t]*true[ \t]*;.*")) {
            AngelicaTweaker.LOGGER.debug("Generate shadow color mipmap");
            Arrays.fill(shadowColorMipmapEnabled, true);

        } else if (line.matches("[ \t]*const[ \t]*bool[ \t]*shadowHardwareFiltering[ \t]*=[ \t]*true[ \t]*;.*")) {
            AngelicaTweaker.LOGGER.debug("Hardware shadow filtering enabled.");
            Arrays.fill(shadowHardwareFilteringEnabled, true);

        } else if (line.matches("[ \t]*const[ \t]*bool[ \t]*shadowHardwareFiltering0[ \t]*=[ \t]*true[ \t]*;.*")) {
            AngelicaTweaker.LOGGER.debug("shadowHardwareFiltering0");
            shadowHardwareFilteringEnabled[0] = true;

        } else if (line.matches("[ \t]*const[ \t]*bool[ \t]*shadowHardwareFiltering1[ \t]*=[ \t]*true[ \t]*;.*")) {
            AngelicaTweaker.LOGGER.debug("shadowHardwareFiltering1");
            shadowHardwareFilteringEnabled[1] = true;

        } else if (line.matches(
            "[ \t]*const[ \t]*bool[ \t]*(shadowtex0Mipmap|shadowtexMipmap)[ \t]*=[ \t]*true[ \t]*;.*")) {
            AngelicaTweaker.LOGGER.debug("shadowtex0Mipmap");
            shadowMipmapEnabled[0] = true;

        } else if (line.matches("[ \t]*const[ \t]*bool[ \t]*(shadowtex1Mipmap)[ \t]*=[ \t]*true[ \t]*;.*")) {
            AngelicaTweaker.LOGGER.debug("shadowtex1Mipmap");
            shadowMipmapEnabled[1] = true;

        } else if (line.matches(
            "[ \t]*const[ \t]*bool[ \t]*(shadowcolor0Mipmap|shadowColor0Mipmap)[ \t]*=[ \t]*true[ \t]*;.*")) {
            AngelicaTweaker.LOGGER.debug("shadowcolor0Mipmap");
            shadowColorMipmapEnabled[0] = true;

        } else if (line.matches(
            "[ \t]*const[ \t]*bool[ \t]*(shadowcolor1Mipmap|shadowColor1Mipmap)[ \t]*=[ \t]*true[ \t]*;.*")) {
            AngelicaTweaker.LOGGER.debug("shadowcolor1Mipmap");
            shadowColorMipmapEnabled[1] = true;

        } else if (line.matches(
            "[ \t]*const[ \t]*bool[ \t]*(shadowtex0Nearest|shadowtexNearest|shadow0MinMagNearest)[ \t]*=[ \t]*true[ \t]*;.*")) {
            AngelicaTweaker.LOGGER.debug("shadowtex0Nearest");
            shadowFilterNearest[0] = true;

        } else if (line.matches(
            "[ \t]*const[ \t]*bool[ \t]*(shadowtex1Nearest|shadow1MinMagNearest)[ \t]*=[ \t]*true[ \t]*;.*")) {
            AngelicaTweaker.LOGGER.debug("shadowtex1Nearest");
            shadowFilterNearest[1] = true;

        } else if (line.matches(
            "[ \t]*const[ \t]*bool[ \t]*(shadowcolor0Nearest|shadowColor0Nearest|shadowColor0MinMagNearest)[ \t]*=[ \t]*true[ \t]*;.*")) {
            AngelicaTweaker.LOGGER.debug("shadowcolor0Nearest");
            shadowColorFilterNearest[0] = true;

        } else if (line.matches(
            "[ \t]*const[ \t]*bool[ \t]*(shadowcolor1Nearest|shadowColor1Nearest|shadowColor1MinMagNearest)[ \t]*=[ \t]*true[ \t]*;.*")) {
            AngelicaTweaker.LOGGER.debug("shadowcolor1Nearest");
            shadowColorFilterNearest[1] = true;

            // Wetness half life
        } else if (line.matches("/\\* WETNESSHL:[0-9\\.]+ \\*/.*")) {
            String[] parts = line.split("(:| )", 4);
            AngelicaTweaker.LOGGER.debug("Wetness halflife: " + parts[2]);
            wetnessHalfLife = Float.parseFloat(parts[2]);

        } else if (line.matches("[ \t]*const[ \t]*float[ \t]*wetnessHalflife[ \t]*=[ \t]*-?[0-9.]+f?;.*")) {
            String[] parts = line.split("(=[ \t]*|;)");
            AngelicaTweaker.LOGGER.debug("Wetness halflife: " + parts[1]);
            wetnessHalfLife = Float.parseFloat(parts[1]);

            // Dryness halflife
        } else if (line.matches("/\\* DRYNESSHL:[0-9\\.]+ \\*/.*")) {
            String[] parts = line.split("(:| )", 4);
            AngelicaTweaker.LOGGER.debug("Dryness halflife: " + parts[2]);
            drynessHalfLife = Float.parseFloat(parts[2]);

        } else if (line.matches("[ \t]*const[ \t]*float[ \t]*drynessHalflife[ \t]*=[ \t]*-?[0-9.]+f?;.*")) {
            String[] parts = line.split("(=[ \t]*|;)");
            AngelicaTweaker.LOGGER.debug("Dryness halflife: " + parts[1]);
            drynessHalfLife = Float.parseFloat(parts[1]);

            // Eye brightness halflife
        } else if (line.matches("[ \t]*const[ \t]*float[ \t]*eyeBrightnessHalflife[ \t]*=[ \t]*-?[0-9.]+f?;.*")) {
            String[] parts = line.split("(=[ \t]*|;)");
            AngelicaTweaker.LOGGER.debug("Eye brightness halflife: " + parts[1]);
            eyeBrightnessHalflife = Float.parseFloat(parts[1]);

            // Center depth halflife
        } else if (line.matches("[ \t]*const[ \t]*float[ \t]*centerDepthHalflife[ \t]*=[ \t]*-?[0-9.]+f?;.*")) {
            String[] parts = line.split("(=[ \t]*|;)");
            AngelicaTweaker.LOGGER.debug("Center depth halflife: " + parts[1]);
            centerDepthSmoothHalflife = Float.parseFloat(parts[1]);

            // Sun path rotation
        } else if (line.matches("[ \t]*const[ \t]*float[ \t]*sunPathRotation[ \t]*=[ \t]*-?[0-9.]+f?;.*")) {
            String[] parts = line.split("(=[ \t]*|;)");
            AngelicaTweaker.LOGGER.debug("Sun path rotation: " + parts[1]);
            sunPathRotation = Float.parseFloat(parts[1]);

            // Ambient occlusion level
        } else if (line.matches("[ \t]*const[ \t]*float[ \t]*ambientOcclusionLevel[ \t]*=[ \t]*-?[0-9.]+f?;.*")) {
            String[] parts = line.split("(=[ \t]*|;)");
            AngelicaTweaker.LOGGER.debug("AO Level: " + parts[1]);
            aoLevel = Float.parseFloat(parts[1]);
            blockAoLight = 1.0f - aoLevel;

            // super sampling
        } else if (line.matches("[ \t]*const[ \t]*int[ \t]*superSamplingLevel[ \t]*=[ \t]*-?[0-9.]+f?;.*")) {
            String[] parts = line.split("(=[ \t]*|;)");
            int ssaa = Integer.parseInt(parts[1]);
            if (ssaa > 1) {
                AngelicaTweaker.LOGGER.debug("Super sampling level: " + ssaa + "x");
                superSamplingLevel = ssaa;
            } else {
                superSamplingLevel = 1;
            }

            // noise texture
        } else if (line.matches("[ \t]*const[ \t]*int[ \t]*noiseTextureResolution[ \t]*=[ \t]*-?[0-9.]+f?;.*")) {
            String[] parts = line.split("(=[ \t]*|;)");
            AngelicaTweaker.LOGGER.debug("Noise texture enabled");
            AngelicaTweaker.LOGGER.debug("Noise texture resolution: " + parts[1]);
            noiseTextureResolution = Integer.parseInt(parts[1]);
            noiseTextureEnabled = true;

        } else if (line.matches("[ \t]*const[ \t]*int[ \t]*\\w+Format[ \t]*=[ \t]*[RGBA81632F]*[ \t]*;.*")) {
            Matcher m = gbufferFormatPattern.matcher(line);
            m.matches();
            String name = m.group(1);
            String value = m.group(2);
            int bufferindex = getBufferIndexFromString(name);
            int format = getTextureFormatFromString(value);
            if (bufferindex >= 0 && format != 0) {
                gbuffersFormat[bufferindex] = format;
                AngelicaTweaker.LOGGER.debug("{} format: {}", name, value);
            }
            // gaux4
        } else if (line.matches("/\\* GAUX4FORMAT:RGBA32F \\*/.*")) {
            AngelicaTweaker.LOGGER.debug("gaux4 format : RGB32AF");
            gbuffersFormat[7] = GL30.GL_RGBA32F;
        } else if (line.matches("/\\* GAUX4FORMAT:RGB32F \\*/.*")) {
            AngelicaTweaker.LOGGER.debug("gaux4 format : RGB32F");
            gbuffersFormat[7] = GL30.GL_RGB32F;
        } else if (line.matches("/\\* GAUX4FORMAT:RGB16 \\*/.*")) {
            AngelicaTweaker.LOGGER.debug("gaux4 format : RGB16");
            gbuffersFormat[7] = GL11.GL_RGB16;

            // Mipmap stuff
        } else if (line.matches("[ \t]*const[ \t]*bool[ \t]*\\w+MipmapEnabled[ \t]*=[ \t]*true[ \t]*;.*")) {
            if (filename.matches(".*composite[0-9]?.fsh") || filename.matches(".*final.fsh")) {
                Matcher m = gbufferMipmapEnabledPattern.matcher(line);
                m.matches();
                String name = m.group(1);
                // String value
                // =m.group(2);
                int bufferindex = getBufferIndexFromString(name);
                if (bufferindex >= 0) {
                    newCompositeMipmapSetting |= (1 << bufferindex);
                    AngelicaTweaker.LOGGER.debug("{} mipmap enabled for {}", name, filename);
                }
            }
        } else if (line.matches("/\\* DRAWBUFFERS:[0-7N]* \\*/.*")) {
            String[] parts = line.split("(:| )", 4);
            newDrawBufSetting = parts[2];
        }
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
            AngelicaTweaker.LOGGER.info("Info log: " + name + "\n" + out);
            return false;
        }
        return true;
    }

    public static void setDrawBuffers(IntBuffer drawBuffers) {
        if (true) return;
        if (activeDrawBuffers != drawBuffers) {
            // printIntBuffer("setDrawBuffers", drawBuffers);
            activeDrawBuffers = drawBuffers;
            GL20.glDrawBuffers(drawBuffers);
        } else {
            // printIntBuffer("setDrawBf skip", drawBuffers);
        }
    }

    public static void useProgram(int program) {
        if (true) return;
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
            // ALog.info("%s",programNames[program] + " draw buffers = " + programsDrawBufSettings[program]);
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
            case ProgramClouds:
            case ProgramTerrain:
            case ProgramTerrainSolid:
            case ProgramTerrainCutoutMip:
            case ProgramTerrainCutout:
            case ProgramDamagedBlock:
            case ProgramWater:
            case ProgramBlock:
            case ProgramEntities:
            case ProgramSpiderEyes:
            case ProgramHand:
            case ProgramWeather:
            case ProgramHandWater:
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
            case ProgramDeferred:
            case ProgramDeferred1:
            case ProgramDeferred2:
            case ProgramDeferred3:
            case ProgramDeferred4:
            case ProgramDeferred5:
            case ProgramDeferred6:
            case ProgramDeferred7:
            case ProgramDeferredLast:
            case ProgramCompositeLast:
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
            case ProgramShadowSolid:
            case ProgramShadowCutout:
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
        setProgramUniform3f("previousCameraPosition", (float) previousCameraPosition[0], (float) previousCameraPosition[1], (float) previousCameraPosition[2]);
        setProgramUniform3f("cameraPosition", (float) cameraPosition[0], (float) cameraPosition[1], (float) cameraPosition[2]);
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
        if (true) return;
        int gp = programsID[activeProgram];
        if (gp != GL11.GL_NONE) {
            int uniform = glGetUniformLocationARB(gp, name);
            glUniform1iARB(uniform, x);
            if (enableShadersDebug) checkGLError(programNames[activeProgram], name);
        }
    }

    public static void setProgramUniform2i(String name, int x, int y) {
        if (true) return;
        int gp = programsID[activeProgram];
        if (gp != GL11.GL_NONE) {
            int uniform = glGetUniformLocationARB(gp, name);
            glUniform2iARB(uniform, x, y);
            if (enableShadersDebug) checkGLError(programNames[activeProgram], name);
        }
    }

    public static void setProgramUniform1f(String name, float x) {
        if (true) return;
        int gp = programsID[activeProgram];
        if (gp != GL11.GL_NONE) {
            int uniform = glGetUniformLocationARB(gp, name);
            glUniform1fARB(uniform, x);
            if (enableShadersDebug) checkGLError(programNames[activeProgram], name);
        }
    }

    public static void setProgramUniform3f(String name, float x, float y, float z) {
        if (true) return;
        int gp = programsID[activeProgram];
        if (gp != GL11.GL_NONE) {
            int uniform = glGetUniformLocationARB(gp, name);
            glUniform3fARB(uniform, x, y, z);
            if (enableShadersDebug) checkGLError(programNames[activeProgram], name);
        }
    }

    public static void setProgramUniformMatrix4ARB(String name, boolean transpose, FloatBuffer matrix) {
        if (true) return;
        int gp = programsID[activeProgram];
        if (gp != GL11.GL_NONE && matrix != null) {
            int uniform = glGetUniformLocationARB(gp, name);
            glUniformMatrix4ARB(uniform, transpose, matrix);
            if (enableShadersDebug) checkGLError(programNames[activeProgram], name);
        }
    }

    private static int getBufferIndexFromString(String name) {
        return switch (name) {
            case "colortex0", "gcolor" -> 0;
            case "colortex1", "gdepth" -> 1;
            case "colortex2", "gnormal" -> 2;
            case "colortex3", "composite" -> 3;
            case "colortex4", "gaux1" -> 4;
            case "colortex5", "gaux2" -> 5;
            case "colortex6", "gaux3" -> 6;
            case "colortex7", "gaux4" -> 7;
            default -> -1;
        };
    }

    private static int getTextureFormatFromString(String par) {
        if (par.matches("[ \t]*R8[ \t]*")) return GL30.GL_R8;
        else if (par.matches("[ \t]*RG8[ \t]*")) return GL30.GL_RG8;
        else if (par.matches("[ \t]*RGB8[ \t]*")) return GL11.GL_RGB8;
        else if (par.matches("[ \t]*RGBA8[ \t]*")) return GL11.GL_RGBA8;
        else if (par.matches("[ \t]*R16[ \t]*")) return GL30.GL_R16;
        else if (par.matches("[ \t]*RG16[ \t]*")) return GL30.GL_RG16;
        else if (par.matches("[ \t]*RGB16[ \t]*")) return GL11.GL_RGB16;
        else if (par.matches("[ \t]*RGBA16[ \t]*")) return GL11.GL_RGBA16;
        else if (par.matches("[ \t]*R32F[ \t]*")) return GL30.GL_R32F;
        else if (par.matches("[ \t]*RG32F[ \t]*")) return GL30.GL_RG32F;
        else if (par.matches("[ \t]*RGB32F[ \t]*")) return GL30.GL_RGB32F;
        else if (par.matches("[ \t]*RGBA32F[ \t]*")) return GL30.GL_RGBA32F;
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
        if (true) return;
        if (!isInitialized) {
            return;
        }
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
        if (dfbDepthTextures != null) {
            GL11.glDeleteTextures(dfbDepthTextures);
            fillIntBufferZero(dfbDepthTextures);
            checkGLError("del dfbDepthTextures");
        }
        if (dfbColorTextures != null) {
            GL11.glDeleteTextures(dfbColorTextures);
            fillIntBufferZero(dfbColorTextures);
            checkGLError("del dfbTextures");
        }
        if (sfbDepthTextures != null) {
            GL11.glDeleteTextures(sfbDepthTextures);
            fillIntBufferZero(sfbDepthTextures);
            checkGLError("del shadow depth");
        }
        if (sfbColorTextures != null) {
            GL11.glDeleteTextures(sfbColorTextures);
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

        AngelicaTweaker.LOGGER.trace("UNINIT");

        shadowPassInterval = 0;
        shouldSkipDefaultShadow = false;
        isInitialized = false;
        notFirstInit = true;
        checkGLError("Shaders.uninit");
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
        // renderWidth = mc.displayWidth * superSamplingLevel;
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
        if (true) return;
        if (dfb != 0) {
            glDeleteFramebuffersEXT(dfb);
            GL11.glDeleteTextures(dfbDepthTextures);
            GL11.glDeleteTextures(dfbColorTextures);
        }

        dfb = glGenFramebuffersEXT();
        GL11.glGenTextures((IntBuffer) dfbDepthTextures.clear().limit(usedDepthBuffers));
        GL11.glGenTextures((IntBuffer) dfbColorTextures.clear().limit(usedColorBuffers));
        dfbDepthTextures.position(0);
        dfbColorTextures.position(0);

        glBindFramebufferEXT(GL_FRAMEBUFFER_EXT, dfb);
        GL20.glDrawBuffers(GL11.GL_NONE);
        GL11.glReadBuffer(GL11.GL_NONE);

        for (int i = 0; i < usedDepthBuffers; ++i) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, dfbDepthTextures.get(i));
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_DEPTH_TEXTURE_MODE, GL11.GL_LUMINANCE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_DEPTH_COMPONENT, renderWidth, renderHeight, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (ByteBuffer) null);
        }

        glFramebufferTexture2DEXT(GL_FRAMEBUFFER_EXT, GL_DEPTH_ATTACHMENT_EXT, GL11.GL_TEXTURE_2D, dfbDepthTextures.get(0), 0);
        GL20.glDrawBuffers(dfbDrawBuffers);
        GL11.glReadBuffer(GL11.GL_NONE);
        checkGLError("FT d");

        for (int i = 0; i < usedColorBuffers; ++i) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, dfbColorTextures.get(i));
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexImage2D( GL11.GL_TEXTURE_2D, 0, gbuffersFormat[i], renderWidth, renderHeight, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, (ByteBuffer) null);
            glFramebufferTexture2DEXT( GL_FRAMEBUFFER_EXT, GL_COLOR_ATTACHMENT0_EXT + i, GL11.GL_TEXTURE_2D, dfbColorTextures.get(i), 0);
            checkGLError("FT c");
        }

        int status = glCheckFramebufferStatusEXT(GL_FRAMEBUFFER_EXT);

        if (status == GL_FRAMEBUFFER_INCOMPLETE_FORMATS_EXT) {
            printChatAndLogError("Failed using multiple internal formats in frame buffer.");
            for (int i = 0; i < usedColorBuffers; ++i) {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, dfbColorTextures.get(i));
                GL11.glTexImage2D( GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, renderWidth, renderHeight, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, (ByteBuffer) null);
                glFramebufferTexture2DEXT( GL_FRAMEBUFFER_EXT, GL_COLOR_ATTACHMENT0_EXT + i, GL11.GL_TEXTURE_2D, dfbColorTextures.get(i), 0);
                checkGLError("FT c");
            }
            status = glCheckFramebufferStatusEXT(GL_FRAMEBUFFER_EXT);
            if (status == GL_FRAMEBUFFER_COMPLETE_EXT) {
                printChatAndLogError("Please update graphics driver.");
            }
        }

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        if (status != GL_FRAMEBUFFER_COMPLETE_EXT) {
            printChatAndLogError("Failed creating framebuffer! (Status " + status + ")");
        } else {
            AngelicaTweaker.LOGGER.debug("Framebuffer created.");
        }
    }

    private static void setupShadowFrameBuffer() {
        if (true) return;
        if (usedShadowDepthBuffers == 0) {
            return;
        }

        if (sfb != 0) {
            glDeleteFramebuffersEXT(sfb);
            GL11.glDeleteTextures(sfbDepthTextures);
            GL11.glDeleteTextures(sfbColorTextures);
        }

        sfb = glGenFramebuffersEXT();
        glBindFramebufferEXT(GL_FRAMEBUFFER_EXT, sfb);
        GL20.glDrawBuffers(GL11.GL_NONE);
        GL11.glReadBuffer(GL11.GL_NONE);

        GL11.glGenTextures((IntBuffer) sfbDepthTextures.clear().limit(usedShadowDepthBuffers));
        GL11.glGenTextures((IntBuffer) sfbColorTextures.clear().limit(usedShadowColorBuffers));
        // printIntBuffer(sfbDepthTextures);
        // printIntBuffer(sfbColorTextures);
        sfbDepthTextures.position(0);
        sfbColorTextures.position(0);

        // depth
        for (int i = 0; i < usedShadowDepthBuffers; ++i) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, sfbDepthTextures.get(i));
            GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
            GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
            int filter = shadowFilterNearest[i] ? GL11.GL_NEAREST : GL11.GL_LINEAR;
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
            if (shadowHardwareFilteringEnabled[i])
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL30.GL_COMPARE_REF_TO_TEXTURE);
            GL11.glTexImage2D( GL11.GL_TEXTURE_2D, 0, GL11.GL_DEPTH_COMPONENT, shadowMapWidth, shadowMapHeight, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (FloatBuffer) null);
        }
        glFramebufferTexture2DEXT( GL_FRAMEBUFFER_EXT, GL_DEPTH_ATTACHMENT_EXT, GL11.GL_TEXTURE_2D, sfbDepthTextures.get(0), 0);
        checkGLError("FT sd");

        // color shadow
        for (int i = 0; i < usedShadowColorBuffers; ++i) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, sfbColorTextures.get(i));
            GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
            GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
            int filter = shadowColorFilterNearest[i] ? GL11.GL_NEAREST : GL11.GL_LINEAR;
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
            GL11.glTexImage2D( GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, shadowMapWidth, shadowMapHeight, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, (ByteBuffer) null);
            glFramebufferTexture2DEXT( GL_FRAMEBUFFER_EXT, GL_COLOR_ATTACHMENT0_EXT + i, GL11.GL_TEXTURE_2D, sfbColorTextures.get(i), 0);
            checkGLError("FT sc");
        }

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        int status = glCheckFramebufferStatusEXT(GL_FRAMEBUFFER_EXT);
        if (status != GL_FRAMEBUFFER_COMPLETE_EXT) {
            printChatAndLogError("Failed creating shadow framebuffer! (Status " + status + ")");
        } else {
            AngelicaTweaker.LOGGER.debug("Shadow framebuffer created.");
        }
    }

    public static void beginRender(Minecraft minecraft, float f, long l) {
        if (true)        return;

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
            lastSystemTime = systemTime; // Initialize lastSystemTime on the first tick so that it is equal to current system time
        }
        diffSystemTime = systemTime - lastSystemTime;
        lastSystemTime = systemTime;

        frameTimeCounter += diffSystemTime * 0.001f;
        frameTimeCounter = frameTimeCounter % 100000.0f;

        rainStrength = minecraft.theWorld.getRainStrength(f);
        {
            float fadeScalar = diffSystemTime * 0.01f;
            // float temp1 = (float)Math.exp(Math.log(0.5)*diffWorldTime/((wetness < rainStrength)? drynessHalfLife : wetnessHalfLife));
            float temp1 = (float) Math.exp(Math.log(0.5) * fadeScalar / ((wetness < rainStrength) ? drynessHalfLife : wetnessHalfLife));
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

        isEyeInWater = (mc.gameSettings.thirdPersonView == 0 && !mc.renderViewEntity.isPlayerSleeping()
                && mc.thePlayer.isInsideOfMaterial(Material.water)) ? 1 : 0;

        {
            Vec3 skyColorV = mc.theWorld.getSkyColor(mc.renderViewEntity, f);
            skyColorR = (float) skyColorV.xCoord;
            skyColorG = (float) skyColorV.yCoord;
            skyColorB = (float) skyColorV.zCoord;
        }


        isRenderingWorld = true;
        isCompositeRendered = false;
        isHandRendered = false;

        if (usedShadowDepthBuffers >= 1) {
            GL13.glActiveTexture(GL13.GL_TEXTURE4);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, sfbDepthTextures.get(0));
            if (usedShadowDepthBuffers >= 2) {
                GL13.glActiveTexture(GL13.GL_TEXTURE5);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, sfbDepthTextures.get(1));
            }
        }

        for (int i = 0; i < 4 && 4 + i < usedColorBuffers; ++i) {
            GL13.glActiveTexture(GL13.GL_TEXTURE7 + i);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, dfbColorTextures.get(4 + i));
        }

        GL13.glActiveTexture(GL13.GL_TEXTURE6);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, dfbDepthTextures.get(0));

        if (usedDepthBuffers >= 2) {
            GL13.glActiveTexture(GL13.GL_TEXTURE11);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, dfbDepthTextures.get(1));

            if (usedDepthBuffers >= 3) {
                GL13.glActiveTexture(GL13.GL_TEXTURE12);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, dfbDepthTextures.get(2));
            }
        }

        for (int i = 0; i < usedShadowColorBuffers; ++i) {
            GL13.glActiveTexture(GL13.GL_TEXTURE13 + i);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, sfbColorTextures.get(i));
        }

        if (noiseTextureEnabled) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + noiseTexture.textureUnit);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, noiseTexture.getID());
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        }

        GL13.glActiveTexture(GL13.GL_TEXTURE0);

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
            GL20.glDrawBuffers(programsDrawBuffers[ProgramShadow]);

            useProgram(ProgramShadow);

            mc.entityRenderer.renderWorld(f, l);

            GL11.glFlush();

            isShadowPass = false;

            mc.gameSettings.advancedOpengl = preShadowPassAdvancedOpengl;
            mc.gameSettings.thirdPersonView = preShadowPassThirdPersonView;

            if (hasGlGenMipmap) {
                if (usedShadowDepthBuffers >= 1) {
                    if (shadowMipmapEnabled[0]) {
                        GL13.glActiveTexture(GL13.GL_TEXTURE4);
                        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
                        GL11.glTexParameteri(
                                GL11.GL_TEXTURE_2D,
                                GL11.GL_TEXTURE_MIN_FILTER,
                                shadowFilterNearest[0] ? GL11.GL_NEAREST_MIPMAP_NEAREST : GL11.GL_LINEAR_MIPMAP_LINEAR);
                    }
                    if (usedShadowDepthBuffers >= 2) {
                        if (shadowMipmapEnabled[1]) {
                            GL13.glActiveTexture(GL13.GL_TEXTURE5);
                            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
                            GL11.glTexParameteri(
                                    GL11.GL_TEXTURE_2D,
                                    GL11.GL_TEXTURE_MIN_FILTER,
                                    shadowFilterNearest[1] ? GL11.GL_NEAREST_MIPMAP_NEAREST : GL11.GL_LINEAR_MIPMAP_LINEAR);
                        }
                    }
                    GL13.glActiveTexture(GL13.GL_TEXTURE0);
                }
                if (usedShadowColorBuffers >= 1) {
                    if (shadowColorMipmapEnabled[0]) {
                        GL13.glActiveTexture(GL13.GL_TEXTURE13);
                        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
                        GL11.glTexParameteri(
                                GL11.GL_TEXTURE_2D,
                                GL11.GL_TEXTURE_MIN_FILTER,
                                shadowColorFilterNearest[0] ? GL11.GL_NEAREST_MIPMAP_NEAREST : GL11.GL_LINEAR_MIPMAP_LINEAR);
                    }
                    if (usedShadowColorBuffers >= 2) {
                        if (shadowColorMipmapEnabled[1]) {
                            GL13.glActiveTexture(GL13.GL_TEXTURE14);
                            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
                            GL11.glTexParameteri(
                                    GL11.GL_TEXTURE_2D,
                                    GL11.GL_TEXTURE_MIN_FILTER,
                                    shadowColorFilterNearest[1] ? GL11.GL_NEAREST_MIPMAP_NEAREST : GL11.GL_LINEAR_MIPMAP_LINEAR);
                        }
                    }
                    GL13.glActiveTexture(GL13.GL_TEXTURE0);
                }
            }

            // System.out.println("shadow pass end");
        }
        mc.mcProfiler.endSection();

        glBindFramebufferEXT(GL_FRAMEBUFFER_EXT, dfb);
        GL11.glViewport(0, 0, Shaders.renderWidth, Shaders.renderHeight);
        activeDrawBuffers = null;
        ShadersTex.bindNSTextures(defaultTexture.angelica$getMultiTexID());
        useProgram(ProgramTextured);

        checkGLError("end beginRender");
    }

    public static void setViewport(int vx, int vy, int vw, int vh) {
        GL11.glViewport(vx, vy, vw, vh);
        if (true) return;
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
        if (true) return;
        if (isShadowPass) {
            checkGLError("shadow clear pre");
            glFramebufferTexture2DEXT( GL_FRAMEBUFFER_EXT, GL_DEPTH_ATTACHMENT_EXT, GL11.GL_TEXTURE_2D, sfbDepthTextures.get(0), 0); GL11.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
            // printIntBuffer(programsDrawBuffers[ProgramShadow]);
            GL20.glDrawBuffers(programsDrawBuffers[ProgramShadow]);
            checkFramebufferStatus("shadow clear");
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            checkGLError("shadow clear");
            return;
        }
        checkGLError("clear pre");

        /*
         * glDrawBuffers(dfbDrawBuffers); GL11.glClearColor(0.0f, 0.0f, 0.0f, 0.0f); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT |
         * GL11.GL_DEPTH_BUFFER_BIT);
         */

        GL20.glDrawBuffers(GL_COLOR_ATTACHMENT0_EXT);
        // GL11.glClearColor(clearColorR, clearColorG, clearColorB, 1.0f);
        // GL11.glClearColor(1f, 0f, 0f, 1.0f); // for debug
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

        GL20.glDrawBuffers(GL_COLOR_ATTACHMENT1_EXT);
        GL11.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

        for (int i = 2; i < usedColorBuffers; ++i) {
            GL20.glDrawBuffers(GL_COLOR_ATTACHMENT0_EXT + i);
            GL11.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
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

        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, (FloatBuffer) projection.position(0));
        invertMat4x((FloatBuffer) projection.position(0), (FloatBuffer) projectionInverse.position(0));
        projection.position(0);
        projectionInverse.position(0);

        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, (FloatBuffer) modelView.position(0));
        invertMat4x((FloatBuffer) modelView.position(0), (FloatBuffer) modelViewInverse.position(0));
        modelView.position(0);
        modelViewInverse.position(0);

        if (isShadowPass) {
//            GL11.glViewport(0, 0, shadowMapWidth, shadowMapHeight);
//
//            GL11.glMatrixMode(GL11.GL_PROJECTION);
//            GL11.glLoadIdentity();
//
//            if (shadowMapIsOrtho) {
//                GL11.glOrtho( -shadowMapHalfPlane, shadowMapHalfPlane, -shadowMapHalfPlane, shadowMapHalfPlane, 0.05f, 256.0f);
//            } else {
//                // just backwards compatibility. it's only used when SHADOWFOV is set in the shaders.
//                gluPerspective(shadowMapFOV, (float) shadowMapWidth / (float) shadowMapHeight, 0.05f, 256.0f);
//            }
//
//            GL11.glMatrixMode(GL11.GL_MODELVIEW);
//            GL11.glLoadIdentity();
//            GL11.glTranslatef(0.0f, 0.0f, -100.0f);
//            GL11.glRotatef(90.0f, 1.0f, 0.0f, 0.0f);
            float celestialAngle = mc.theWorld.getCelestialAngle(f);
            sunAngle = (celestialAngle < 0.75f) ? celestialAngle + 0.25f : celestialAngle - 0.75f;
            float angle = celestialAngle * (-360.0f);
            float angleInterval = shadowAngleInterval > 0.0f
                    ? (angle % shadowAngleInterval - (shadowAngleInterval * 0.5f))
                    : 0.0f;
            if (sunAngle <= 0.5) {
                // day time
//                GL11.glRotatef(angle - angleInterval, 0.0f, 0.0f, 1.0f);
//                GL11.glRotatef(sunPathRotation, 1.0f, 0.0f, 0.0f); // rotate
                shadowAngle = sunAngle;
            } else {
                // night time
//                GL11.glRotatef(angle + 180.0f - angleInterval, 0.0f, 0.0f, 1.0f);
//                GL11.glRotatef(sunPathRotation, 1.0f, 0.0f, 0.0f); // rotate
                shadowAngle = sunAngle - 0.5f;
            }
//            if (shadowMapIsOrtho) {
//                // reduces jitter
//                float trans = shadowIntervalSize;
//                float trans2 = trans / 2.0f;
//                GL11.glTranslatef((float) x % trans - trans2, (float) y % trans - trans2, (float) z % trans - trans2);
//            }

            GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, (FloatBuffer) shadowProjection.position(0));
            invertMat4x((FloatBuffer) shadowProjection.position(0), (FloatBuffer) shadowProjectionInverse.position(0));
            shadowProjection.position(0);
            shadowProjectionInverse.position(0);

            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, (FloatBuffer) shadowModelView.position(0));
            invertMat4x((FloatBuffer) shadowModelView.position(0), (FloatBuffer) shadowModelViewInverse.position(0));
            shadowModelView.position(0);
            shadowModelViewInverse.position(0);

//            setProgramUniformMatrix4ARB("gbufferProjection", false, projection);
//            setProgramUniformMatrix4ARB("gbufferProjectionInverse", false, projectionInverse);
//            setProgramUniformMatrix4ARB("gbufferPreviousProjection", false, previousProjection);
//            setProgramUniformMatrix4ARB("gbufferModelView", false, modelView);
//            setProgramUniformMatrix4ARB("gbufferModelViewInverse", false, modelViewInverse);
//            setProgramUniformMatrix4ARB("gbufferPreviousModelView", false, previousModelView);
//            setProgramUniformMatrix4ARB("shadowProjection", false, shadowProjection);
//            setProgramUniformMatrix4ARB("shadowProjectionInverse", false, shadowProjectionInverse);
//            setProgramUniformMatrix4ARB("shadowModelView", false, shadowModelView);
//            setProgramUniformMatrix4ARB("shadowModelViewInverse", false, shadowModelViewInverse);

            // Also render player shadow
            mc.gameSettings.thirdPersonView = 1;
            checkGLError("setCamera");
            return;
        }
        checkGLError("setCamera");
    }

    public static void preCelestialRotate() {
        Shaders.setUpPosition();
//        GL11.glRotatef(Shaders.sunPathRotation * 1.0f, 0.0f, 0.0f, 1.0f);
        checkGLError("preCelestialRotate");
    }

    public static void postCelestialRotate() {
        // This is called when the current matrix is the modelview matrix based on the celestial angle.
        // The sun is at (0, 100, 0), and the moon is at (0, -100, 0).
        FloatBuffer modelView = tempMatrixDirectBuffer;
        modelView.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelView);
        modelView.get(tempMat, 0, 16);
        multiplyMat4xVec4(tempMat, sunPosModelView, sunPosition);
        multiplyMat4xVec4(tempMat, moonPosModelView, moonPosition);
        checkGLError("postCelestialRotate");
    }

    public static void setUpPosition() {
        // Up direction in model view while rendering sky.
        FloatBuffer modelView = tempMatrixDirectBuffer;
        modelView.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelView);
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

        inv[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15] + m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10];
        inv[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15] - m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10];
        inv[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15] + m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9];
        inv[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14] - m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9];
        inv[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15] - m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10];
        inv[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15] + m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10];
        inv[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15] - m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9];
        inv[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14] + m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9];
        inv[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15] + m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6];
        inv[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15] - m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6];
        inv[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15] + m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5];
        inv[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14] - m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5];
        inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11] - m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6];
        inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11] + m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6];
        inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11] - m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5];
        inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10] + m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5];

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
        if (true) return;
        if (hasGlGenMipmap) {
            if ((activeCompositeMipmapSetting & (1 << 0)) != 0) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
                GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            }

            if ((activeCompositeMipmapSetting & (1 << 1)) != 0) {
                GL13.glActiveTexture(GL13.GL_TEXTURE1);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
                GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            }

            if ((activeCompositeMipmapSetting & (1 << 2)) != 0) {
                GL13.glActiveTexture(GL13.GL_TEXTURE2);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
                GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            }

            if ((activeCompositeMipmapSetting & (1 << 3)) != 0) {
                GL13.glActiveTexture(GL13.GL_TEXTURE3);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
                GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            }

            for (int i = 0; i < 4 && 4 + i < usedColorBuffers; ++i) {
                if ((activeCompositeMipmapSetting & ((1 << 4) << i)) != 0) {
                    GL13.glActiveTexture(GL13.GL_TEXTURE7 + i);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
                    GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
                }
            }

            GL13.glActiveTexture(GL13.GL_TEXTURE0);
        }
    }

    public static void drawComposite() {
        if (true) return;
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0f, 0.0f);
        GL11.glVertex3f(0.0f, 0.0f, 0.0f);
        GL11.glTexCoord2f(1.0f, 0.0f);
        GL11.glVertex3f(1.0f, 0.0f, 0.0f);
        GL11.glTexCoord2f(1.0f, 1.0f);
        GL11.glVertex3f(1.0f, 1.0f, 0.0f);
        GL11.glTexCoord2f(0.0f, 1.0f);
        GL11.glVertex3f(0.0f, 1.0f, 0.0f);
        GL11.glEnd();
    }

    public static void renderDeferred() {
        checkGLError("pre-renderDeferred");
        if (true) return;
        renderComposites(ProgramDeferred, 8, false);
        //OF: mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
    }

    public static void renderCompositeFinal() {
        if (true) return;
        checkGLError("pre-renderCompositeFinal");
        renderComposites(ProgramComposite, 8, true);
    }

    public static void renderComposites(int programBase, int programCount, boolean renderFinal) {
        if (true) return;
        if (isShadowPass) {
            // useProgram(ProgramNone);
            return;
        }


        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f);

        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_ALWAYS);
        GL11.glDepthMask(false);

        // textures
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, dfbColorTextures.get(0));

        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, dfbColorTextures.get(1));

        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, dfbColorTextures.get(2));

        GL13.glActiveTexture(GL13.GL_TEXTURE3);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, dfbColorTextures.get(3));

        if (usedShadowDepthBuffers >= 1) {
            GL13.glActiveTexture(GL13.GL_TEXTURE4);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, sfbDepthTextures.get(0));
            if (usedShadowDepthBuffers >= 2) {
                GL13.glActiveTexture(GL13.GL_TEXTURE5);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, sfbDepthTextures.get(1));
            }
        }

        for (int i = 0; i < 4 && 4 + i < usedColorBuffers; ++i) {
            GL13.glActiveTexture(GL13.GL_TEXTURE7 + i);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, dfbColorTextures.get(4 + i));
        }

        GL13.glActiveTexture(GL13.GL_TEXTURE6);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, dfbDepthTextures.get(0));

        if (usedDepthBuffers >= 2) {
            GL13.glActiveTexture(GL13.GL_TEXTURE11);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, dfbDepthTextures.get(1));

            if (usedDepthBuffers >= 3) {
                GL13.glActiveTexture(GL13.GL_TEXTURE12);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, dfbDepthTextures.get(2));
            }
        }

        for (int i = 0; i < usedShadowColorBuffers; ++i) {
            GL13.glActiveTexture(GL13.GL_TEXTURE13 + i);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, sfbColorTextures.get(i));
        }

        if (noiseTextureEnabled) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + noiseTexture.textureUnit);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, noiseTexture.getID());
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        }

        GL13.glActiveTexture(GL13.GL_TEXTURE0);

        // set depth buffer
        glFramebufferTexture2DEXT( GL_FRAMEBUFFER_EXT, GL_DEPTH_ATTACHMENT_EXT, GL11.GL_TEXTURE_2D, dfbDepthTextures.get(0), 0);
        // detach depth buffer
        // glFramebufferTexture2DEXT(GL_FRAMEBUFFER_EXT, GL_DEPTH_ATTACHMENT_EXT, GL11.GL_TEXTURE_2D, 0, 0);

        // composite
        GL20.glDrawBuffers(dfbDrawBuffers);
        checkGLError("pre-composite");

        for (int i = 0; i < programCount; ++i) {
            if (programsID[programBase + i] != 0) {
                useProgram(programBase + i);
                checkGLError(programNames[programBase + i]);
                if (activeCompositeMipmapSetting != 0) genCompositeMipmap();
                drawComposite();
            }
        }

        // reattach depth buffer
        // glFramebufferTexture2DEXT(GL_FRAMEBUFFER_EXT, GL_DEPTH_ATTACHMENT_EXT, GL11.GL_TEXTURE_2D, dfbDepthTexture, 0);

        // final
        if(renderFinal) {
            renderFinal();
        }

        isCompositeRendered = true;
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthMask(true);

        // GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();

        useProgram(ProgramNone);
    }

    private static void renderFinal() {
        // final render target
        isRenderingDfb = false;
        if (true) return;
        mc.getFramebuffer().bindFramebuffer(true);
        // glBindFramebufferEXT(GL_FRAMEBUFFER_EXT, 0);
        // GL11.glViewport(0, 0, mc.displayWidth, mc.displayHeight);
        if (EntityRenderer.anaglyphEnable) {
            boolean maskR = (EntityRenderer.anaglyphField != 0);
            GL11.glColorMask(maskR, !maskR, !maskR, true);
        }
        GL11.glDepthMask(true);
        GL11.glClearColor(clearColorR, clearColorG, clearColorB, 1.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_ALWAYS);
        GL11.glDepthMask(false);

        checkGLError("pre-final");
        useProgram(ProgramFinal);
        checkGLError("final");
        if (activeCompositeMipmapSetting != 0) genCompositeMipmap();
        drawComposite();

        // Read gl_FragColor
        // ByteBuffer centerPixelColor = ByteBuffer.allocateDirect(3);
        // glReadPixels(renderWidth / 2, renderHeight / 2, 1, 1, GL11.GL_RGB, GL11.GL_BYTE, centerPixelColor);
        // System.out.println(centerPixelColor.get(0));

        // end
        checkGLError("renderCompositeFinal");
    }


    public static void endRender() {
        if (true) return;
        if (isShadowPass) {
            // useProgram(ProgramNone);
            checkGLError("shadow endRender");
            return;
        }

        if (!isCompositeRendered) renderCompositeFinal();
        isRenderingWorld = false;

        GL11.glColorMask(true, true, true, true);
        GL11.glEnable(GL11.GL_BLEND);
        useProgram(ProgramNone);
        checkGLError("endRender end");

        // defaultTexture.bind();
    }

    public static void beginSky() {
        isRenderingSky = true;
        fogEnabled = true;
        if (true) return;
        setDrawBuffers(dfbDrawBuffers);
        useProgram(ProgramSkyTextured);
        pushEntity(-2, 0);
    }

    public static void setSkyColor(Vec3 v3color) {
        skyColorR = (float) v3color.xCoord;
        skyColorG = (float) v3color.yCoord;
        skyColorB = (float) v3color.zCoord;
        if (true) return;
        setProgramUniform3f("skyColor", skyColorR, skyColorG, skyColorB);
    }

    public static void drawHorizon() {
        if (true) return;
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
        if (true) return;
        GL11.glColor3f(fogColorR, fogColorG, fogColorB);
        // GL11.glColor3f(0f, 1f, 0f);
        // GL11.glDisable(GL11.GL_FOG);

        drawHorizon();

        // GL11.glEnable(GL11.GL_FOG);
        GL11.glColor3f(skyColorR, skyColorG, skyColorB);
    }

    public static void endSky() {
        isRenderingSky = false;
        if (true) return;
        setDrawBuffers(dfbDrawBuffers);
        useProgram(lightmapEnabled ? ProgramTexturedLit : ProgramTextured);
        popEntity();
    }

    // public static void beginSunMoon() {
    // useProgram(ProgramNone);
    // }

    // public static void endSunMoon() {
    // useProgram(lightmapEnabled ? ProgramTexturedLit : ProgramTextured);
    // }
    public static void beginUpdateChunks() {
        if (true) return;
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
        if (true) return;
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
        if (true) return;
        fogEnabled = true;
        pushEntity(-3, 0);
        useProgram(ProgramTextured);
    }

    public static void endClouds() {
        if (true) return;
        disableFog();
        popEntity();
    }

    public static void beginTerrain() {
        if (true) return;
        if (isRenderingWorld) {
            if (isShadowPass) {
                GL11.glDisable(GL11.GL_CULL_FACE);
            }
            fogEnabled = true;
            useProgram(Shaders.ProgramTerrain);
            // ShadersTex.bindNSTextures(defaultTexture.getMultiTexID()); // flat
        }
    }

    public static void endTerrain() {
        if (true) return;
        if (isRenderingWorld) {
            if (isShadowPass) {
                GL11.glEnable(GL11.GL_CULL_FACE);
            }
            useProgram(lightmapEnabled ? ProgramTexturedLit : ProgramTextured);
            // ShadersTex.bindNSTextures(defaultTexture.getMultiTexID()); // flat
        }
    }

    public static void beginBlockEntities() {
        if (true) return;
        if (isRenderingWorld) {
            checkGLError("beginBlockEntities");
            useProgram(Shaders.ProgramTerrain);
        }
    }

    public static void endBlockEntities() {
        if (true) return;
        if (isRenderingWorld) {
            checkGLError("endBlockEntities");
            useProgram(lightmapEnabled ? ProgramTexturedLit : ProgramTextured);
            ShadersTex.bindNSTextures(defaultTexture.angelica$getMultiTexID());
        }
    }

    public static void beginBlockDestroyProgress() {
        if (true) return;
        if (isRenderingWorld) {
            useProgram(ProgramTerrain);
            if (Shaders.configTweakBlockDamage) {
                setDrawBuffers(drawBuffersColorAtt0);
                GL11.glDepthMask(false);
                // GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ZERO);
            }
        }
    }

    public static void endBlockDestroyProgress() {
        if (true) return;
        if (isRenderingWorld) {
            GL11.glDepthMask(true);
            useProgram(ProgramTexturedLit);
        }
    }

    public static void beginEntities() {
        if (true) return;
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
        if (true) return;
        if (isRenderingWorld) {
            useProgram(ProgramEntities);
        }
    }

    public static void beginSpiderEyes() {
        if (true) return;
        if (isRenderingWorld) {
            useProgram(ProgramSpiderEyes);
            if (programsID[ProgramSpiderEyes] == programsID[ProgramTextured]) {
                GL11.glEnable(GL11.GL_ALPHA_TEST);
                GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
            }
        }
    }

    public static void endEntities() {
        if (true) return;
        if (isRenderingWorld) {
            useProgram(lightmapEnabled ? ProgramTexturedLit : ProgramTextured);
        }
    }

    public static void setEntityHurtFlash(int hurt, int flash) {
        if (true) return;
        if (useEntityHurtFlash && isRenderingWorld && !isShadowPass) {
            if (uniformEntityHurt != -1) glUniform1iARB(uniformEntityHurt, hurt);
            if (uniformEntityFlash != -1) glUniform1iARB(uniformEntityFlash, flash >> 24);
            checkGLError("setEntityHurtFlash");
        }
    }

    public static void resetEntityHurtFlash() {
        if (true) return;
        setEntityHurtFlash(0, 0);
    }

    public static void beginLivingDamage() {
        if (true) return;
        if (isRenderingWorld) {
            ShadersTex.bindTexture(defaultTexture);
            if (!isShadowPass) {
                // useProgram(ProgramBasic);
                setDrawBuffers(drawBuffersColorAtt0);
            }
        }
    }

    public static void endLivingDamage() {
        if (true) return;
        if (isRenderingWorld) {
            if (!isShadowPass) {
                // useProgram(ProgramEntities);
                setDrawBuffers(programsDrawBuffers[ProgramEntities]);
            }
        }
    }

    public static void beginLitParticles() {
        if (true) return;
        // GL11.glDepthMask(false);
        Tessellator.instance.setNormal(0f, 0f, 0f);
        useProgram(ProgramTexturedLit);
    }

    public static void beginParticles() {
        if (true) return;
        // GL11.glDepthMask(false);
        Tessellator.instance.setNormal(0f, 0f, 0f);
        useProgram(ProgramTextured);
    }

    public static void endParticles() {
        if (true) return;
        Tessellator.instance.setNormal(0f, 0f, 0f);
        useProgram(ProgramTexturedLit);
    }

    public static void preWater() {
        if (true) return;
        if (isShadowPass) {
            if (usedShadowDepthBuffers >= 2) {
                // copy depth buffer to shadowtex1
                GL13.glActiveTexture(GL13.GL_TEXTURE5);
                checkGLError("pre copy shadow depth");
                GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, shadowMapWidth, shadowMapHeight);
                checkGLError("copy shadow depth");
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
            }
        } else {
            if (usedDepthBuffers >= 2) {
                // copy depth buffer to depthtex1
                GL13.glActiveTexture(GL13.GL_TEXTURE11);
                checkGLError("pre copy depth");
                GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, renderWidth, renderHeight);
                checkGLError("copy depth");
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
            }
            ShadersTex.bindNSTextures(defaultTexture.angelica$getMultiTexID()); // flat
        }
    }

    public static void beginWater() {
        if (true) return;
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
        if (true) return;
        if (isRenderingWorld) {
            if (isShadowPass) {
                // glFramebufferTexture2DEXT(GL_FRAMEBUFFER_EXT, GL_DEPTH_ATTACHMENT_EXT, GL11.GL_TEXTURE_2D,
                // sfbDepthTexture, 0);
            } else {
                // glFramebufferTexture2DEXT(GL_FRAMEBUFFER_EXT, GL_DEPTH_ATTACHMENT_EXT, GL11.GL_TEXTURE_2D,
                // dfbDepthTexture, 0);
            }
            useProgram(lightmapEnabled ? ProgramTexturedLit : ProgramTextured);
        }
    }

    public static void readCenterDepth() {
        if (true) return;
        if (!isShadowPass) {
            // Read depth buffer at center of screen for DOF
            if (centerDepthSmoothEnabled) {
                tempDirectFloatBuffer.clear();
                GL11.glReadPixels( renderWidth / 2, renderHeight / 2, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, tempDirectFloatBuffer);
                centerDepth = tempDirectFloatBuffer.get(0);

                // Smooth depth value
                float fadeScalar = diffSystemTime * 0.01f;
                float fadeFactor = (float) Math.exp(Math.log(0.5) * fadeScalar / centerDepthSmoothHalflife);
                centerDepthSmooth = centerDepthSmooth * (fadeFactor) + centerDepth * (1 - fadeFactor);
            }
        }
    }

    public static void beginWeather() {
        if (true) return;
        if (!isShadowPass) {
            if (usedDepthBuffers >= 3) {
                // copy depth buffer to depthtex2
                GL13.glActiveTexture(GL13.GL_TEXTURE12);
                GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, renderWidth, renderHeight);
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
            }
            // GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            useProgram(Shaders.ProgramWeather);
            // glFramebufferTexture2DEXT(GL_FRAMEBUFFER_EXT, GL_DEPTH_ATTACHMENT_EXT, GL11.GL_TEXTURE_2D,
            // dfbWaterDepthTexture, 0);
        }

        // if (isShadowPass) {
        // glBindFramebufferEXT(GL_FRAMEBUFFER_EXT, 0); // will be set to sbf in endHand()
        // }
    }

    public static void endWeather() {
        if (true) return;
        // GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_BLEND);
        useProgram(ProgramTexturedLit);
        // glFramebufferTexture2DEXT(GL_FRAMEBUFFER_EXT, GL_DEPTH_ATTACHMENT_EXT, GL11.GL_TEXTURE_2D, dfbDepthTexture, 0);

        // if (isShadowPass) {
        // glBindFramebufferEXT(GL_FRAMEBUFFER_EXT, sfb); // was set to 0 in beginWeather()
        // }
    }

    public static void beginProjectRedHalo() {
        if (true) return;
        useProgram(ProgramBasic);
    }

    public static void endProjectRedHalo() {
        if (true) return;
        useProgram(ProgramTexturedLit);
    }

    public static void applyHandDepth() {
        if (true) return;
        if (Shaders.configHandDepthMul != 1.0) {
            GL11.glScaled(1.0, 1.0, Shaders.configHandDepthMul);
        }
    }

    public static void beginHand() {
        if (true) return;
        // GL11.glEnable(GL11.GL_BLEND);
        // GL11.glDisable(GL11.GL_BLEND);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        useProgram(Shaders.ProgramHand);
        checkGLError("beginHand");
        checkFramebufferStatus("beginHand");
    }

    public static void endHand() {
        if (true) return;
        // GL11.glDisable(GL11.GL_BLEND);
        checkGLError("pre endHand");
        checkFramebufferStatus("pre endHand");
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        // useProgram(lightmapEnabled ? ProgramTexturedLit : ProgramTextured);
        checkGLError("endHand");
    }

    public static void beginFPOverlay() {
        if (true) return;
        // GL11.glDisable(GL11.GL_BLEND);
        // GL11.glEnable(GL11.GL_BLEND);
        // GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    public static void endFPOverlay() {
        if (true) return;
        // GL11.glDisable(GL11.GL_BLEND);
    }

    // ----------------------------------------

    public static void glEnableWrapper(int cap) {
        GL11.glEnable(cap);
        if (cap == GL11.GL_TEXTURE_2D) enableTexture2D();
        else if (cap == GL11.GL_FOG) enableFog();
    }

    public static void glDisableWrapper(int cap) {
        GL11.glDisable(cap);
        if (cap == GL11.GL_TEXTURE_2D) disableTexture2D();
        else if (cap == GL11.GL_FOG) disableFog();
    }

    public static void glEnableT2D(int cap) {
        GL11.glEnable(cap); // GL11.GL_TEXTURE_2D
        enableTexture2D();
    }

    public static void glDisableT2D(int cap) {
        GL11.glDisable(cap); // GL11.GL_TEXTURE_2D
        disableTexture2D();
    }

    public static void glEnableFog(int cap) {
        GL11.glEnable(cap); // GL11.GL_FOG
        enableFog();
    }

    public static void glDisableFog(int cap) {
        GL11.glDisable(cap); // GL11.GL_FOG
        disableFog();
    }

    public static void enableTexture2D() {
        if (true) return;
        if (isRenderingSky) {
            useProgram(ProgramSkyTextured);
        } else if (activeProgram == ProgramBasic) {
            useProgram(lightmapEnabled ? ProgramTexturedLit : ProgramTextured);
        }
    }

    public static void disableTexture2D() {
        if (true) return;
        if (isRenderingSky) {
            useProgram(ProgramSkyBasic);
        } else if (activeProgram == ProgramTextured || activeProgram == ProgramTexturedLit) {
            useProgram(ProgramBasic);
        }
    }

    public static void enableFog() {
        if (true) return;
        fogEnabled = true;
        setProgramUniform1i("fogMode", fogMode);
    }

    public static void disableFog() {
        if (true) return;
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

    public static void sglFogf(int pname, float param) {
        GL11.glFogf(pname, param);
        switch (pname) {
            case GL11.GL_FOG_DENSITY:
                fogDensity = param;
                break;
            case GL11.GL_FOG_START:
                fogStart = param;
                break;
            case GL11.GL_FOG_END:
                fogEnd = param;
                break;
        }
    }

    public static void enableLightmap() {
        lightmapEnabled = true;
        if (true) return;
        if (activeProgram == ProgramTextured) {
            useProgram(ProgramTexturedLit);
        }
    }

    public static void disableLightmap() {
        lightmapEnabled = false;
        if (true) return;
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
        final int metadata = 0;
        // BlockAliases.getMappedBlockId
        final int blockId = Block.blockRegistry.getIDForObject(block);
        entityDataIndex++;
        entityData[entityDataIndex * 2] = (blockId & 0xFFFF) | (block.getRenderType() << 16);
        entityData[entityDataIndex * 2 + 1] = metadata;
    }

    public static void pushEntity(RenderBlocks rb, Block block, int x, int y, int z) {
        final int metadata = rb.blockAccess.getBlockMetadata(x, y, z);
        // BlockAliases.getMappedBlockId
        final int blockId = Block.blockRegistry.getIDForObject(block);
        entityDataIndex++;
        entityData[entityDataIndex * 2] = (blockId & 0xFFFF) | (block.getRenderType() << 16);
        entityData[entityDataIndex * 2 + 1] = metadata;
    }

    public static void popEntity() {
        entityData[entityDataIndex * 2] = 0;
        entityData[entityDataIndex * 2 + 1] = 0;
        entityDataIndex--;
    }

    // ----------------------------------------

}
