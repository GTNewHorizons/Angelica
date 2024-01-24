package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.stacks.AlphaStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.BlendStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.BooleanStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.Color4Stack;
import com.gtnewhorizons.angelica.glsm.stacks.ColorMaskStack;
import com.gtnewhorizons.angelica.glsm.stacks.DepthStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.FogStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.IStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.MatrixModeStack;
import com.gtnewhorizons.angelica.glsm.stacks.ViewPortStateStack;
import com.gtnewhorizons.angelica.glsm.states.Color4;
import com.gtnewhorizons.angelica.glsm.states.ISettableState;
import com.gtnewhorizons.angelica.glsm.states.TextureBinding;
import com.gtnewhorizons.angelica.glsm.states.TextureUnitArray;
import com.gtnewhorizons.angelica.hudcaching.HUDCaching;
import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntStack;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import lombok.Getter;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gbuffer_overrides.state.StateTracker;
import net.coderbot.iris.gl.blending.AlphaTestStorage;
import net.coderbot.iris.gl.blending.BlendModeStorage;
import net.coderbot.iris.gl.blending.DepthColorStorage;
import net.coderbot.iris.gl.state.StateUpdateNotifiers;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.samplers.IrisSamplers;
import net.coderbot.iris.texture.TextureInfoCache;
import net.coderbot.iris.texture.TextureTracker;
import net.coderbot.iris.texture.pbr.PBRTextureManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.ARBMultitexture;
import org.lwjgl.opengl.Drawable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Set;

import static com.gtnewhorizons.angelica.loading.AngelicaTweaker.LOGGER;

@SuppressWarnings("unused") // Used in ASM
public class GLStateManager {
    public static final boolean BYPASS_CACHE = Boolean.parseBoolean(System.getProperty("angelica.disableGlCache", "false"));
    public static final int MAX_ATTRIB_STACK_DEPTH = GL11.glGetInteger(GL11.GL_MAX_ATTRIB_STACK_DEPTH);
    public static final int MAX_MODELVIEW_STACK_DEPTH = GL11.glGetInteger(GL11.GL_MAX_MODELVIEW_STACK_DEPTH);
    public static final int MAX_PROJECTION_STACK_DEPTH = GL11.glGetInteger(GL11.GL_MAX_PROJECTION_STACK_DEPTH);
    public static final int MAX_TEXTURE_STACK_DEPTH = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_STACK_DEPTH);
    public static final int MAX_TEXTURE_UNITS = GL11.glGetInteger(GL20.GL_MAX_TEXTURE_IMAGE_UNITS);

    // GLStateManager State Trackers
    private static final IntStack attribs = new IntArrayList(MAX_ATTRIB_STACK_DEPTH);
    private static final IntStack activeTextureUnit = new IntArrayList(MAX_ATTRIB_STACK_DEPTH);
    static {
        activeTextureUnit.push(0); // GL_TEXTURE0
    }
    @Getter protected static final TextureUnitArray textures = new TextureUnitArray();
    @Getter protected static final BlendStateStack blendState = new BlendStateStack();
    @Getter protected static final BooleanStateStack blendMode = new BooleanStateStack(GL11.GL_BLEND);
    @Getter protected static final BooleanStateStack scissorTest = new BooleanStateStack(GL11.GL_SCISSOR_TEST);
    @Getter protected static final DepthStateStack depthState = new DepthStateStack();
    @Getter protected static final BooleanStateStack depthTest = new BooleanStateStack(GL11.GL_DEPTH_TEST);

//    @Getter private static final FogState fogState = new FogState();
    @Getter protected static final FogStateStack fogState = new FogStateStack();
    @Getter protected static final BooleanStateStack fogMode = new BooleanStateStack(GL11.GL_FOG);
    @Getter protected static final Color4Stack color = new Color4Stack();
    @Getter protected static final Color4Stack clearColor = new Color4Stack();
    @Getter protected static final ColorMaskStack colorMask = new ColorMaskStack();
    @Getter protected static final BooleanStateStack cullState = new BooleanStateStack(GL11.GL_CULL_FACE);
    @Getter protected static final AlphaStateStack alphaState = new AlphaStateStack();
    @Getter protected static final BooleanStateStack alphaTest = new BooleanStateStack(GL11.GL_ALPHA_TEST);

    @Getter protected static final BooleanStateStack lightingState = new BooleanStateStack(GL11.GL_LIGHTING);
    @Getter protected static final BooleanStateStack rescaleNormalState = new BooleanStateStack(GL12.GL_RESCALE_NORMAL);

    @Getter protected static final MatrixModeStack matrixMode = new MatrixModeStack();
    @Getter protected static final Matrix4fStack modelViewMatrix = new Matrix4fStack(MAX_MODELVIEW_STACK_DEPTH);
    @Getter protected static final Matrix4fStack projectionMatrix = new Matrix4fStack(MAX_PROJECTION_STACK_DEPTH);
    @Getter protected static final Matrix4fStack textureMatrix = new Matrix4fStack(MAX_TEXTURE_STACK_DEPTH);

    @Getter protected static final ViewPortStateStack viewportState = new ViewPortStateStack();

    private static int modelShadeMode;

    // Iris Listeners
    private static Runnable blendFuncListener = null;
    private static Runnable fogToggleListener = null;
    private static Runnable fogModeListener = null;
    private static Runnable fogStartListener = null;
    private static Runnable fogEndListener = null;
    private static Runnable fogDensityListener = null;

    // Thread Checking
    @Getter
    private static final Thread MainThread = Thread.currentThread();
    private static Thread CurrentThread = MainThread;
    private static boolean runningSplash = false;

    private static int glListMode = 0;
    private static int glListId = -1;
    private static final Map<IStateStack<?>, ISettableState<?>> glListStates = new Object2ObjectArrayMap<>();
    private static final Int2ObjectMap<Set<Map.Entry<IStateStack<?>, ISettableState<?>>>> glListChanges = new Int2ObjectOpenHashMap<>();

    public static void init() {
        if (AngelicaConfig.enableIris) {
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
    }

    public static void assertMainThread() {
        if (Thread.currentThread() != CurrentThread && !runningSplash) {
            LOGGER.info("Call from not the Current Thread! - " + Thread.currentThread().getName() + " Current thread: " + CurrentThread.getName());
        }
    }

    // LWJGL Overrides
    public static void glEnable(int cap) {
        switch (cap) {
            case GL11.GL_ALPHA_TEST -> enableAlphaTest();
            case GL11.GL_BLEND -> enableBlend();
            case GL11.GL_DEPTH_TEST -> enableDepthTest();
            case GL11.GL_CULL_FACE -> enableCull();
            case GL11.GL_LIGHTING -> enableLighting();
            case GL12.GL_RESCALE_NORMAL -> enableRescaleNormal();
            case GL11.GL_TEXTURE_2D -> enableTexture();
            case GL11.GL_FOG -> enableFog();
            case GL11.GL_SCISSOR_TEST -> enableScissorTest();
            default -> GL11.glEnable(cap);
        }
    }

    public static void glDisable(int cap) {
        switch (cap) {
            case GL11.GL_ALPHA_TEST -> disableAlphaTest();
            case GL11.GL_BLEND -> disableBlend();
            case GL11.GL_DEPTH_TEST -> disableDepthTest();
            case GL11.GL_CULL_FACE -> disableCull();
            case GL11.GL_LIGHTING -> disableLighting();
            case GL12.GL_RESCALE_NORMAL -> disableRescaleNormal();
            case GL11.GL_TEXTURE_2D -> disableTexture();
            case GL11.GL_FOG -> disableFog();
            case GL11.GL_SCISSOR_TEST -> disableScissorTest();
            default -> GL11.glDisable(cap);
        }
    }

    public static void glGetFloat(int pname, FloatBuffer params) {
        if(GLStateManager.BYPASS_CACHE) {
            GL11.glGetFloat(pname, params);
            return;
        }

        switch (pname) {
            case GL11.GL_MODELVIEW_MATRIX -> modelViewMatrix.get(params);
            case GL11.GL_PROJECTION_MATRIX -> projectionMatrix.get(params);
            case GL11.GL_TEXTURE_MATRIX -> textureMatrix.get(params);
            default -> GL11.glGetFloat(pname, params);
        }
    }

    public static float glGetFloat(int pname) {
        return GL11.glGetFloat(pname);
    }

    // GLStateManager Functions

    public static void enableBlend() {
        if (AngelicaConfig.enableIris) {
            if (BlendModeStorage.isBlendLocked()) {
                BlendModeStorage.deferBlendModeToggle(true);
                return;
            }
        }
        blendMode.enable();
    }

    public static void disableBlend() {
        if (AngelicaConfig.enableIris) {
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
        if (AngelicaConfig.enableIris) {
            if (BlendModeStorage.isBlendLocked()) {
                BlendModeStorage.deferBlendFunc(srcFactor, dstFactor, srcFactor, dstFactor);
                return;
            }
        }
        if (HUDCaching.renderingCacheOverride) {
            blendState.setSrcRgb(srcFactor);
            blendState.setDstRgb(dstFactor);
            blendState.setSrcAlpha(GL11.GL_ONE);
            blendState.setDstAlpha(GL11.GL_ONE_MINUS_SRC_ALPHA);
            OpenGlHelper.glBlendFunc(srcFactor, dstFactor, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            return;
        }
        if (GLStateManager.BYPASS_CACHE || blendState.getSrcRgb() != srcFactor || blendState.getDstRgb() != dstFactor) {
            blendState.setSrcRgb(srcFactor);
            blendState.setDstRgb(dstFactor);
            GL11.glBlendFunc(srcFactor, dstFactor);
        }

        // Iris
        if (blendFuncListener != null) blendFuncListener.run();
    }

    public static void tryBlendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        if (AngelicaConfig.enableIris) {
            if (BlendModeStorage.isBlendLocked()) {
                BlendModeStorage.deferBlendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha);
                return;
            }
        }
        if (HUDCaching.renderingCacheOverride && dstAlpha != GL11.GL_ONE_MINUS_SRC_ALPHA) {
            srcAlpha = GL11.GL_ONE;
            dstAlpha = GL11.GL_ONE_MINUS_SRC_ALPHA;
        }
        if (GLStateManager.BYPASS_CACHE || blendState.getSrcRgb() != srcRgb || blendState.getDstRgb() != dstRgb || blendState.getSrcAlpha()
            != srcAlpha || blendState.getDstAlpha() != dstAlpha) {
            blendState.setSrcRgb(srcRgb);
            blendState.setDstRgb(dstRgb);
            blendState.setSrcAlpha(srcAlpha);
            blendState.setDstAlpha(dstAlpha);
            OpenGlHelper.glBlendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha);
        }

        // Iris
        if (blendFuncListener != null) blendFuncListener.run();
    }

    public static void glDepthFunc(int func) {
        // Hacky workaround for now, need to figure out why this isn't being applied...
        if (GLStateManager.BYPASS_CACHE || func != depthState.getFunc() || GLStateManager.runningSplash) {
            depthState.setFunc(func);
            GL11.glDepthFunc(func);
        }
    }

    public static void glDepthMask(boolean mask) {
        if (AngelicaConfig.enableIris) {
            if (DepthColorStorage.isDepthColorLocked()) {
                DepthColorStorage.deferDepthEnable(mask);
                return;
            }
        }

        if (mask != depthState.isMask()) {
            depthState.setMask(mask);
            GL11.glDepthMask(mask);
        }
    }

    public static void glColor4f(float red, float green, float blue, float alpha) {
        if (changeColor(red, green, blue, alpha)) {
            GL11.glColor4f(red, green, blue, alpha);
        }
    }

    public static void glColor4d(double red, double green, double blue, double alpha) {
        if (changeColor((float) red, (float) green, (float) blue, (float) alpha)) {
            GL11.glColor4d(red, green, blue, alpha);
        }
    }

    public static void glColor4b(byte red, byte green, byte blue, byte alpha) {
        if (changeColor(b2f(red), b2f(green), b2f(blue), b2f(alpha))) {
            GL11.glColor4b(red, green, blue, alpha);
        }
    }

    public static void glColor4ub(byte red, byte green, byte blue, byte alpha) {
        if (changeColor(ub2f(red), ub2f(green), ub2f(blue), ub2f(alpha))) {
            GL11.glColor4ub(red, green, blue, alpha);
        }
    }

    public static void glColor3f(float red, float green, float blue) {
        if (changeColor(red, green, blue, 1.0F)) {
            GL11.glColor3f(red, green, blue);
        }
    }

    public static void glColor3d(double red, double green, double blue) {
        if (changeColor((float) red, (float) green, (float) blue, 1.0F)) {
            GL11.glColor3d(red, green, blue);
        }
    }

    public static void glColor3b(byte red, byte green, byte blue) {
        if (changeColor(b2f(red), b2f(green), b2f(blue), 1.0F)) {
            GL11.glColor3b(red, green, blue);
        }
    }

    public static void glColor3ub(byte red, byte green, byte blue) {
        if (changeColor(ub2f(red), ub2f(green), ub2f(blue), 1.0F)) {
            GL11.glColor3ub(red, green, blue);
        }
    }

    private static float ub2f(byte b) {
        return (b & 0xFF) / 255.0F;
    }

    private static float b2f(byte b) {
        return ((b - Byte.MIN_VALUE) & 0xFF) / 255.0F;
    }

    private static boolean changeColor(float red, float green, float blue, float alpha) {
        // Helper function for glColor*
        if (GLStateManager.BYPASS_CACHE || red != color.getRed() || green != color.getGreen() || blue != color.getBlue() || alpha != color.getAlpha()) {
            color.setRed(red);
            color.setGreen(green);
            color.setBlue(blue);
            color.setAlpha(alpha);
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
        if (AngelicaConfig.enableIris) {
            if (DepthColorStorage.isDepthColorLocked()) {
                DepthColorStorage.deferColorMask(red, green, blue, alpha);
                return;
            }
        }
        if (GLStateManager.BYPASS_CACHE || red != colorMask.red || green != colorMask.green || blue != colorMask.blue || alpha != colorMask.alpha) {
            colorMask.red = red;
            colorMask.green = green;
            colorMask.blue = blue;
            colorMask.alpha = alpha;
            GL11.glColorMask(red, green, blue, alpha);
        }
    }

    // Clear Color
    public static void glClearColor(float red, float green, float blue, float alpha) {
        if (GLStateManager.BYPASS_CACHE || red != clearColor.getRed() || green != clearColor.getGreen() || blue != clearColor.getBlue() || alpha != clearColor.getAlpha()) {
            clearColor.setRed(red);
            clearColor.setGreen(green);
            clearColor.setBlue(blue);
            clearColor.setAlpha(alpha);
            GL11.glClearColor(red, green, blue, alpha);
        }
    }

    // ALPHA
    public static void enableAlphaTest() {
        if (AngelicaConfig.enableIris) {
            if (AlphaTestStorage.isAlphaTestLocked()) {
                AlphaTestStorage.deferAlphaTestToggle(true);
                return;
            }
        }
        alphaTest.enable();
    }

    public static void disableAlphaTest() {
        if (AngelicaConfig.enableIris) {
            if (AlphaTestStorage.isAlphaTestLocked()) {
                AlphaTestStorage.deferAlphaTestToggle(false);
                return;
            }
        }
        alphaTest.disable();
    }

    public static void glAlphaFunc(int function, float reference) {
        if (AngelicaConfig.enableIris) {
            if (AlphaTestStorage.isAlphaTestLocked()) {
                AlphaTestStorage.deferAlphaFunc(function, reference);
                return;
            }
        }
        alphaState.setFunction(function);
        alphaState.setReference(reference);
        GL11.glAlphaFunc(function, reference);
    }

    // Textures
    public static void glActiveTexture(int texture) {
        final int newTexture = texture - GL13.GL_TEXTURE0;
        if (GLStateManager.BYPASS_CACHE || getActiveTextureUnit() != newTexture) {
            activeTextureUnit.popInt();
            activeTextureUnit.push(newTexture);
            GL13.glActiveTexture(texture);
        }
    }

    public static void glActiveTextureARB(int texture) {
        final int newTexture = texture - GL13.GL_TEXTURE0;
        if (GLStateManager.BYPASS_CACHE || getActiveTextureUnit() != newTexture) {
            activeTextureUnit.popInt();
            activeTextureUnit.push(newTexture);
            ARBMultitexture.glActiveTextureARB(texture);
        }
    }

    public static int getBoundTexture() {
        return textures.getTextureUnitBindings(activeTextureUnit.topInt()).getBinding();
    }

    public static void glBindTexture(int target, int texture) {
        if(target != GL11.GL_TEXTURE_2D) {
            // We're only supporting 2D textures for now
            GL11.glBindTexture(target, texture);
            return;
        }

        final TextureBinding textureUnit = textures.getTextureUnitBindings(GLStateManager.activeTextureUnit.topInt());

        if (GLStateManager.BYPASS_CACHE || textureUnit.getBinding() != texture || runningSplash) {
            GL11.glBindTexture(target, texture);
            textureUnit.setBinding(texture);
            if (AngelicaConfig.enableIris) {
                TextureTracker.INSTANCE.onBindTexture(texture);
            }
        }
    }

    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, IntBuffer pixels) {
        if (AngelicaConfig.enableIris) {
            // Iris
            TextureInfoCache.INSTANCE.onTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
        }
        GL11.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
    }

    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, ByteBuffer pixels) {
        if (AngelicaConfig.enableIris) {
            TextureInfoCache.INSTANCE.onTexImage2D(target, level, internalformat, width, height, border, format, type, pixels != null ? pixels.asIntBuffer() : (IntBuffer) null);
        }
        GL11.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
    }

    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, long pixels_buffer_offset) {
        if (AngelicaConfig.enableIris) {
            TextureInfoCache.INSTANCE.onTexImage2D(target, level, internalformat, width, height, border, format, type, pixels_buffer_offset);
        }
        GL11.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels_buffer_offset);
    }

    public static void glDeleteTextures(int id) {
        if (AngelicaConfig.enableIris) {
            iris$onDeleteTexture(id);
        }
        textures.getTextureUnitBindings(GLStateManager.activeTextureUnit.topInt()).setBinding(-1);
        GL11.glDeleteTextures(id);
    }

    public static void glDeleteTextures(IntBuffer ids) {
        if (AngelicaConfig.enableIris) {
            for(int i = 0; i < ids.capacity(); i++) {
                iris$onDeleteTexture(ids.get(i));
            }
        }
        textures.getTextureUnitBindings(GLStateManager.activeTextureUnit.topInt()).setBinding(-1);
        GL11.glDeleteTextures(ids);
    }

    public static void enableTexture() {
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
            } else if (textureUnit == IrisSamplers.OVERLAY_TEXTURE_UNIT) {
                StateTracker.INSTANCE.overlaySampler = true;
                updatePipeline = true;
            }

            if (updatePipeline) {
                Iris.getPipelineManager().getPipeline().ifPresent(p -> p.setInputs(StateTracker.INSTANCE.getInputs()));
            }
        }
        textures.getTextureUnitStates(textureUnit).enable();
    }

    public static void disableTexture() {
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
            } else if (textureUnit == IrisSamplers.OVERLAY_TEXTURE_UNIT) {
                StateTracker.INSTANCE.overlaySampler = false;
                updatePipeline = true;
            }

            if (updatePipeline) {
                Iris.getPipelineManager().getPipeline().ifPresent(p -> p.setInputs(StateTracker.INSTANCE.getInputs()));
            }
        }
        textures.getTextureUnitStates(textureUnit).disable();
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
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, i);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, j);
    }

    public static void glDrawArrays(int mode, int first, int count) {
        // Iris -- TODO: This doesn't seem to work and is related to matchPass()
        Iris.getPipelineManager().getPipeline().ifPresent(WorldRenderingPipeline::syncProgram);
        GL11.glDrawArrays(mode, first, count);
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

    public static void disableLighting() {
        lightingState.disable();
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
        // TODO: Iris Notifier
        GL11.glFog(pname, param);
        if (pname == GL11.GL_FOG_COLOR) {
            final float red = param.get(0);
            final float green = param.get(1);
            final float blue = param.get(2);

            fogState.getFogColor().set(red, green, blue);
            fogState.setFogAlpha(param.get(3));
            fogState.getFogColorBuffer().clear();
            fogState.getFogColorBuffer().put((FloatBuffer) param.position(0)).flip();
        }
    }

    public static Vector3d getFogColor() {
        return fogState.getFogColor();
    }

    public static void fogColor(float red, float green, float blue, float alpha) {
        if (GLStateManager.BYPASS_CACHE || red != fogState.getFogColor().x || green != fogState.getFogColor().y || blue != fogState.getFogColor().z || alpha != fogState.getFogAlpha()) {
            fogState.getFogColor().set(red, green, blue);
            fogState.setFogAlpha(alpha);
            fogState.getFogColorBuffer().clear();
            fogState.getFogColorBuffer().put(red).put(green).put(blue).put(alpha).flip();
            GL11.glFog(GL11.GL_FOG_COLOR, fogState.getFogColorBuffer());
        }
    }

    public static void glFogf(int pname, float param) {
        GL11.glFogf(pname, param);
        // Note: Does not handle GL_FOG_INDEX
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

    public static void glFogi(int pname, int param) {
        GL11.glFogi(pname, param);
        if (pname == GL11.GL_FOG_MODE) {
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
        if (GLStateManager.BYPASS_CACHE || modelShadeMode != mode) {
            modelShadeMode = mode;
            GL11.glShadeModel(mode);
        }
    }

    // Iris Functions
    private static void iris$onDeleteTexture(int id) {
        if (AngelicaConfig.enableIris) {
            TextureTracker.INSTANCE.onDeleteTexture(id);
            TextureInfoCache.INSTANCE.onDeleteTexture(id);
            PBRTextureManager.INSTANCE.onDeleteTexture(id);
        }
    }

    public static void setRunningSplash(boolean runningSplash) {
        GLStateManager.runningSplash = runningSplash;
    }

    public static void makeCurrent(Drawable drawable) throws LWJGLException {
        drawable.makeCurrent();
        final Thread currentThread = Thread.currentThread();

        CurrentThread = currentThread;
        LOGGER.info("Current thread: {}", currentThread.getName());
    }

    public static void glNewList(int list, int mode) {
        if(glListMode > 0) {
            throw new RuntimeException("glNewList called inside of a display list!");
        }
        glListId = list;
        glListMode = mode;
        GL11.glNewList(list, mode);
        for(IStateStack<?> stack : Feature.maskToFeatures(GL11.GL_ALL_ATTRIB_BITS)) {
            // Feature Stack, copy of current feature state
            glListStates.put(stack, (ISettableState<?>) ((ISettableState<?>)stack).copy());
        }
        if(glListMode == GL11.GL_COMPILE) {
            pushState(GL11.GL_ALL_ATTRIB_BITS);
        }
    }

    public static void glEndList() {
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
        attribs.push(mask);
        for(IStateStack<?> stack : Feature.maskToFeatures(mask)) {
            stack.push();
        }

    }
    public static void popState() {
        final int mask = attribs.popInt();
        for(IStateStack<?> stack : Feature.maskToFeatures(mask)) {
            stack.pop();
        }
    }

    public static void glPushAttrib(int mask) {
        pushState(mask);
        GL11.glPushAttrib(mask);
    }

    public static void glPopAttrib() {
        popState();
        GL11.glPopAttrib();
    }

    // Matrix Operations
    public static void glMatrixMode(int mode) {
        matrixMode.setMode(mode);
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
                return textureMatrix;
            }
            default -> throw new IllegalStateException("Unknown matrix mode: " + matrixMode.getMode());
        }
    }

    public static void glLoadIdentity() {
        GL11.glLoadIdentity();
        getMatrixStack().identity();
    }

    public static void glTranslatef(float x, float y, float z) {
        GL11.glTranslatef(x, y, z);
        getMatrixStack().translate(x, y, z);
    }
    public static void glTranslated(double x, double y, double z) {
        GL11.glTranslated(x, y, z);
        getMatrixStack().translate((float) x, (float) y, (float) z);
    }

    public static void glScalef(float x, float y, float z) {
        GL11.glScalef(x, y, z);
        getMatrixStack().scale(x, y, z);
    }

    public static void glScaled(double x, double y, double z) {
        GL11.glScaled(x, y, z);
        getMatrixStack().scale((float) x, (float) y, (float) z);
    }

    private static final Matrix4f tempMatrix4f = new Matrix4f();
    public static void glMultMatrix(FloatBuffer floatBuffer) {
        GL11.glMultMatrix(floatBuffer);
        tempMatrix4f.set(floatBuffer);
        getMatrixStack().mul(conersionMatrix4f);
    }

    public static final Matrix4d conersionMatrix4d = new Matrix4d();
    public static final Matrix4f conersionMatrix4f = new Matrix4f();
    public static void glMultMatrix(DoubleBuffer matrix) {
        GL11.glMultMatrix(matrix);
        conersionMatrix4d.set(matrix);
        conersionMatrix4f.set(conersionMatrix4d);
        getMatrixStack().mul(conersionMatrix4f);
    }

    private static final Vector3f rotation = new Vector3f();
    public static void glRotatef(float angle, float x, float y, float z) {
        GL11.glRotatef(angle, x, y, z);
        rotation.set(x, y, z).normalize();
        getMatrixStack().rotate((float)Math.toRadians(angle), rotation);
    }

    public static void glRotated(double angle, double x, double y, double z) {
        GL11.glRotated(angle, x, y, z);
        rotation.set(x, y, z).normalize();
        getMatrixStack().rotate((float)Math.toRadians(angle), rotation);
    }

    public static void glOrtho(double left, double right, double bottom, double top, double zNear, double zFar) {
        GL11.glOrtho(left, right, bottom, top, zNear, zFar);
        getMatrixStack().ortho((float)left, (float)right, (float)bottom, (float)top, (float)zNear, (float)zFar);
    }

    public static void glFrustum(double left, double right, double bottom, double top, double zNear, double zFar) {
        GL11.glFrustum(left, right, bottom, top, zNear, zFar);
        getMatrixStack().frustum((float)left, (float)right, (float)bottom, (float)top, (float)zNear, (float)zFar);
    }
    public static void glPushMatrix() {
        GL11.glPushMatrix();
        try {
            getMatrixStack().pushMatrix();
        } catch(IllegalStateException ignored) {
            // Ignore
            if(AngelicaMod.lwjglDebug)
                AngelicaTweaker.LOGGER.warn("Matrix stack overflow ", new Throwable());
        }
    }

    public static void glPopMatrix() {
        GL11.glPopMatrix();
        try {
            getMatrixStack().popMatrix();
        } catch(IllegalStateException ignored) {
            // Ignore
            if(AngelicaMod.lwjglDebug)
                AngelicaTweaker.LOGGER.warn("Matrix stack underflow ", new Throwable());
        }
    }

    private static final Matrix4f perspectiveMatrix = new Matrix4f();
    private static final FloatBuffer perspectiveBuffer = BufferUtils.createFloatBuffer(16);
    public static void gluPerspective(float fovy, float aspect, float zNear, float zFar) {
        perspectiveMatrix.identity().perspective((float)Math.toRadians(fovy), aspect, zNear, zFar);

        perspectiveMatrix.get(0, perspectiveBuffer);
        GL11.glMultMatrix(perspectiveBuffer);

        getMatrixStack().mul(perspectiveMatrix);

    }

    public static void glViewport(int x, int y, int width, int height) {
        GL11.glViewport(x, y, width, height);
        viewportState.setViewPort(x, y, width, height);
    }

    public static int getActiveTextureUnit() {
        return activeTextureUnit.topInt();
    }

    public static int getListMode() {
        return glListMode;
    }
}
