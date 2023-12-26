package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.states.AlphaState;
import com.gtnewhorizons.angelica.glsm.states.BlendState;
import com.gtnewhorizons.angelica.glsm.states.BooleanState;
import com.gtnewhorizons.angelica.glsm.states.Color4;
import com.gtnewhorizons.angelica.glsm.states.DepthState;
import com.gtnewhorizons.angelica.glsm.states.FogState;
import com.gtnewhorizons.angelica.glsm.states.GLColorMask;
import com.gtnewhorizons.angelica.glsm.states.MatrixState;
import com.gtnewhorizons.angelica.glsm.states.TextureState;
import com.gtnewhorizons.angelica.glsm.states.ViewportState;
import com.gtnewhorizons.angelica.hudcaching.HUDCaching;
import lombok.Getter;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gbuffer_overrides.state.StateTracker;
import net.coderbot.iris.gl.blending.AlphaTestStorage;
import net.coderbot.iris.gl.blending.BlendModeStorage;
import net.coderbot.iris.gl.blending.DepthColorStorage;
import net.coderbot.iris.gl.sampler.SamplerLimits;
import net.coderbot.iris.gl.state.StateUpdateNotifiers;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.samplers.IrisSamplers;
import net.coderbot.iris.texture.TextureInfoCache;
import net.coderbot.iris.texture.TextureTracker;
import net.coderbot.iris.texture.pbr.PBRTextureManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.ARBMultitexture;
import org.lwjgl.opengl.Drawable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.stream.IntStream;

import static com.gtnewhorizons.angelica.loading.AngelicaTweaker.LOGGER;

@SuppressWarnings("unused") // Used in ASM
public class GLStateManager {
    public static final boolean BYPASS_CACHE = Boolean.parseBoolean(System.getProperty("angelica.disableGlCache", "false"));

    // GLStateManager State Trackers
    @Getter private static int activeTexture;
    @Getter private static final BlendState blendState = new BlendState();
    @Getter private static final DepthState depthState = new DepthState();
    @Getter private static final FogState fogState = new FogState();
    @Getter private static final Color4 Color = new Color4();
    @Getter private static final Color4 ClearColor = new Color4();
    @Getter private static final GLColorMask ColorMask = new GLColorMask();
    @Getter private static final BooleanState cullState = new BooleanState(GL11.GL_CULL_FACE);
    @Getter private static final AlphaState alphaState = new AlphaState();
    @Getter private static final BooleanState lightingState = new BooleanState(GL11.GL_LIGHTING);
    @Getter private static final BooleanState rescaleNormalState = new BooleanState(GL12.GL_RESCALE_NORMAL);
    @Getter private static final MatrixState matrixState = new MatrixState();
    @Getter private static final ViewportState viewportState = new ViewportState();

    private static long dirty = 0;

    private static int modelShadeMode;

    @Getter
    private static TextureState[] Textures;

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

    private static boolean inGLNewList = false;

    private static boolean hudCaching$blendEnabled;

    public static void init() {
        if (AngelicaConfig.enableIris) {
            StateUpdateNotifiers.blendFuncNotifier = listener -> blendFuncListener = listener;
            StateUpdateNotifiers.fogToggleNotifier = listener -> fogToggleListener = listener;
            StateUpdateNotifiers.fogModeNotifier = listener -> fogModeListener = listener;
            StateUpdateNotifiers.fogStartNotifier = listener -> fogStartListener = listener;
            StateUpdateNotifiers.fogEndNotifier = listener -> fogEndListener = listener;
            StateUpdateNotifiers.fogDensityNotifier = listener -> fogDensityListener = listener;
        }
        // We want textures regardless of Iris being initialized, and using SamplerLimits is isolated enough
        Textures = IntStream.range(0, SamplerLimits.get().getMaxTextureUnits()).mapToObj(i -> new TextureState()).toArray(TextureState[]::new);
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
            default -> GL11.glDisable(cap);
        }
    }

    // GLStateManager Functions

    public static void enableBlend() {
        if (AngelicaConfig.enableIris) {
            if (BlendModeStorage.isBlendLocked()) {
                BlendModeStorage.deferBlendModeToggle(true);
                return;
            }
        }
        hudCaching$blendEnabled = true;
        blendState.mode.enable();
    }

    public static void disableBlend() {
        if (AngelicaConfig.enableIris) {
            if (BlendModeStorage.isBlendLocked()) {
                BlendModeStorage.deferBlendModeToggle(false);
                return;
            }
        }
        hudCaching$blendEnabled = false;
        blendState.mode.disable();
    }

    public static void glBlendFunc(int srcFactor, int dstFactor) {
        if (AngelicaConfig.enableIris) {
            if (BlendModeStorage.isBlendLocked()) {
                BlendModeStorage.deferBlendFunc(srcFactor, dstFactor, srcFactor, dstFactor);
                return;
            }
        }
        if (HUDCaching.renderingCacheOverride) {
            blendState.srcRgb = srcFactor;
            blendState.dstRgb = dstFactor;
            blendState.srcAlpha = GL11.GL_ONE;
            blendState.dstAlpha = GL11.GL_ONE_MINUS_SRC_ALPHA;
            OpenGlHelper.glBlendFunc(srcFactor, dstFactor, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            clean(Dirty.BLEND_STATE);
            return;
        }
        if (GLStateManager.BYPASS_CACHE || blendState.srcRgb != srcFactor || blendState.dstRgb != dstFactor || checkDirty(Dirty.BLEND_STATE)) {
            blendState.srcRgb = srcFactor;
            blendState.dstRgb = dstFactor;
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
        if (GLStateManager.BYPASS_CACHE || blendState.srcRgb != srcRgb || blendState.dstRgb != dstRgb || blendState.srcAlpha != srcAlpha || blendState.dstAlpha != dstAlpha || checkDirty(Dirty.BLEND_STATE)) {
            blendState.srcRgb = srcRgb;
            blendState.dstRgb = dstRgb;
            blendState.srcAlpha = srcAlpha;
            blendState.dstAlpha = dstAlpha;
            OpenGlHelper.glBlendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha);
        }

        // Iris
        if (blendFuncListener != null) blendFuncListener.run();
    }

    public static void glDepthFunc(int func) {
        // Hacky workaround for now, need to figure out why this isn't being applied...
        if (GLStateManager.BYPASS_CACHE || func != depthState.func || GLStateManager.runningSplash || checkDirty(Dirty.DEPTH_FUNC)) {
            depthState.func = func;
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

        if (mask != depthState.mask || checkDirty(Dirty.DEPTH_MASK)) {
            depthState.mask = mask;
            GL11.glDepthMask(mask);
        }
    }

    public static void glColor4f(float red, float green, float blue, float alpha) {
        if (!hudCaching$blendEnabled && HUDCaching.renderingCacheOverride && alpha < 1f) {
            alpha = 1f;
        }
        if (changeColor(red, green, blue, alpha)) {
            GL11.glColor4f(red, green, blue, alpha);
        }
    }

    public static void glColor4d(double red, double green, double blue, double alpha) {
        if (!hudCaching$blendEnabled && HUDCaching.renderingCacheOverride && alpha < 1d) {
            alpha = 1d;
        }
        if (changeColor((float) red, (float) green, (float) blue, (float) alpha)) {
            GL11.glColor4d(red, green, blue, alpha);
        }
    }

    public static void glColor4b(byte red, byte green, byte blue, byte alpha) {
        if (!hudCaching$blendEnabled && HUDCaching.renderingCacheOverride && alpha < Byte.MAX_VALUE) {
            alpha = Byte.MAX_VALUE;
        }
        if (changeColor(b2f(red), b2f(green), b2f(blue), b2f(alpha))) {
            GL11.glColor4b(red, green, blue, alpha);
        }
    }

    public static void glColor4ub(byte red, byte green, byte blue, byte alpha) {
        if (!hudCaching$blendEnabled && HUDCaching.renderingCacheOverride && alpha < Byte.MAX_VALUE) {
            alpha = Byte.MAX_VALUE;
        }
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
        if (GLStateManager.BYPASS_CACHE || red != Color.red || green != Color.green || blue != Color.blue || alpha != Color.alpha || checkDirty(Dirty.COLOR)) {
            Color.red = red;
            Color.green = green;
            Color.blue = blue;
            Color.alpha = alpha;
            return true;
        }
        return false;
    }

    public static void clearCurrentColor() {
        // Marks the cache dirty, doesn't actually reset the color
        dirty(Dirty.COLOR);
    }

    public static void glColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        if (AngelicaConfig.enableIris) {
            if (DepthColorStorage.isDepthColorLocked()) {
                DepthColorStorage.deferColorMask(red, green, blue, alpha);
                return;
            }
        }
        if (GLStateManager.BYPASS_CACHE || red != ColorMask.red || green != ColorMask.green || blue != ColorMask.blue || alpha != ColorMask.alpha || checkDirty(Dirty.COLOR_MASK)) {
            ColorMask.red = red;
            ColorMask.green = green;
            ColorMask.blue = blue;
            ColorMask.alpha = alpha;
            GL11.glColorMask(red, green, blue, alpha);
        }
    }

    // Clear Color
    public static void glClearColor(float red, float green, float blue, float alpha) {
        if (GLStateManager.BYPASS_CACHE || red != ClearColor.red || green != ClearColor.green || blue != ClearColor.blue || alpha != ClearColor.alpha || checkDirty(Dirty.CLEAR_COLOR)) {
            ClearColor.red = red;
            ClearColor.green = green;
            ClearColor.blue = blue;
            ClearColor.alpha = alpha;
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
        alphaState.mode.enable();
    }

    public static void disableAlphaTest() {
        if (AngelicaConfig.enableIris) {
            if (AlphaTestStorage.isAlphaTestLocked()) {
                AlphaTestStorage.deferAlphaTestToggle(false);
                return;
            }
        }
        alphaState.mode.disable();
    }

    public static void glAlphaFunc(int function, float reference) {
        if (AngelicaConfig.enableIris) {
            if (AlphaTestStorage.isAlphaTestLocked()) {
                AlphaTestStorage.deferAlphaFunc(function, reference);
                return;
            }
        }
        alphaState.function = function;
        alphaState.reference = reference;
        GL11.glAlphaFunc(function, reference);
    }

    // Textures
    public static void glActiveTexture(int texture) {
        final int newTexture = texture - GL13.GL_TEXTURE0;
        if (GLStateManager.BYPASS_CACHE || activeTexture != newTexture || checkDirty(Dirty.ACTIVE_TEXTURE)) {
            activeTexture = newTexture;
            GL13.glActiveTexture(texture);
        }
    }

    public static void glActiveTextureARB(int texture) {
        final int newTexture = texture - GL13.GL_TEXTURE0;
        if (GLStateManager.BYPASS_CACHE || activeTexture != newTexture || checkDirty(Dirty.ACTIVE_TEXTURE)) {
            activeTexture = newTexture;
            ARBMultitexture.glActiveTextureARB(texture);
        }
    }

    public static int getBoundTexture() {
        return Textures[activeTexture].binding;
    }

    public static void glBindTexture(int target, int texture) {
        if(inGLNewList) {
            // Binding a texture, while building a list, is not allowed and is a silent noop
            Throwable throwable = new Throwable();
            LOGGER.info("Naughty naughty, someone's making a texture binding in a display list!", throwable);
            return;
        }

        // TODO: Do we need to do the check dirty for each bound texture?
        if (GLStateManager.BYPASS_CACHE || Textures[activeTexture].binding != texture || runningSplash || checkDirty(Dirty.BOUND_TEXTURE)) {
            GL11.glBindTexture(target, texture);

            Textures[activeTexture].binding = texture;
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
        Textures[activeTexture].binding = -1;
        GL11.glDeleteTextures(id);
    }

    public static void glDeleteTextures(IntBuffer ids) {
        if (AngelicaConfig.enableIris) {
            for(int i = 0; i < ids.capacity(); i++) {
                iris$onDeleteTexture(ids.get(i));
            }
        }
        Textures[activeTexture].binding = -1;
        GL11.glDeleteTextures(ids);
    }

    public static void enableTexture() {
        if (AngelicaConfig.enableIris) {
            // Iris
            boolean updatePipeline = false;
            if (activeTexture == IrisSamplers.ALBEDO_TEXTURE_UNIT) {
                StateTracker.INSTANCE.albedoSampler = true;
                updatePipeline = true;
            } else if (activeTexture == IrisSamplers.LIGHTMAP_TEXTURE_UNIT) {
                StateTracker.INSTANCE.lightmapSampler = true;
                updatePipeline = true;
            } else if (activeTexture == IrisSamplers.OVERLAY_TEXTURE_UNIT) {
                StateTracker.INSTANCE.overlaySampler = true;
                updatePipeline = true;
            }

            if (updatePipeline) {
                Iris.getPipelineManager().getPipeline().ifPresent(p -> p.setInputs(StateTracker.INSTANCE.getInputs()));
            }
        }
        Textures[activeTexture].mode.enable();
    }

    public static void disableTexture() {
        if (AngelicaConfig.enableIris) {
            // Iris
            boolean updatePipeline = false;
            if (activeTexture == IrisSamplers.ALBEDO_TEXTURE_UNIT) {
                StateTracker.INSTANCE.albedoSampler = false;
                updatePipeline = true;
            } else if (activeTexture == IrisSamplers.LIGHTMAP_TEXTURE_UNIT) {
                StateTracker.INSTANCE.lightmapSampler = false;
                updatePipeline = true;
            } else if (activeTexture == IrisSamplers.OVERLAY_TEXTURE_UNIT) {
                StateTracker.INSTANCE.overlaySampler = false;
                updatePipeline = true;
            }

            if (updatePipeline) {
                Iris.getPipelineManager().getPipeline().ifPresent(p -> p.setInputs(StateTracker.INSTANCE.getInputs()));
            }
        }
        Textures[activeTexture].mode.disable();
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
        depthState.mode.enable();
    }

    public static void disableDepthTest() {
        depthState.mode.disable();
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
        fogState.mode.enable();
        if (fogToggleListener != null) {
            fogToggleListener.run();
        }
    }

    public static void disableFog() {
        fogState.mode.disable();
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

            fogState.fogColor.set(red, green, blue);
            fogState.fogAlpha = param.get(3);
            fogState.fogColorBuffer.clear();
            fogState.fogColorBuffer.put((FloatBuffer) param.position(0)).flip();
        }
    }

    public static Vector3d getFogColor() {
        return fogState.fogColor;
    }

    public static void fogColor(float red, float green, float blue, float alpha) {
        if (GLStateManager.BYPASS_CACHE || red != fogState.fogColor.x || green != fogState.fogColor.y || blue != fogState.fogColor.z || alpha != fogState.fogAlpha) {
            fogState.fogColor.set(red, green, blue);
            fogState.fogAlpha = alpha;
            fogState.fogColorBuffer.clear();
            fogState.fogColorBuffer.put(red).put(green).put(blue).put(alpha).flip();
            GL11.glFog(GL11.GL_FOG_COLOR, fogState.fogColorBuffer);
        }
    }

    public static void glFogf(int pname, float param) {
        GL11.glFogf(pname, param);
        switch (pname) {
            case GL11.GL_FOG_DENSITY -> {
                fogState.density = param;
                if (fogDensityListener != null) {
                    fogDensityListener.run();
                }
            }
            case GL11.GL_FOG_START -> {
                fogState.start = param;
                if (fogStartListener != null) {
                    fogStartListener.run();
                }
            }
            case GL11.GL_FOG_END -> {
                fogState.end = param;
                if (fogEndListener != null) {
                    fogEndListener.run();
                }
            }
        }
    }

    public static void glFogi(int pname, int param) {
        GL11.glFogi(pname, param);
        if (pname == GL11.GL_FOG_MODE) {
            fogState.fogMode = param;
            if (fogModeListener != null) {
                fogModeListener.run();
            }
        }
    }

    public static void setFogBlack() {
        glFogf(GL11.GL_FOG_COLOR, 0.0F);
    }

    public static void glShadeModel(int mode) {
        if (GLStateManager.BYPASS_CACHE || modelShadeMode != mode || checkDirty(Dirty.SHADE_MODEL)) {
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
        if(inGLNewList) {
            throw new RuntimeException("glNewList called inside of a display list!");
        }
        inGLNewList = true;
        GL11.glNewList(list, mode);
    }

    public static void glEndList() {
        if(!inGLNewList) {
            throw new RuntimeException("glEndList called outside of a display list!");
        }
        inGLNewList = false;
        GL11.glEndList();
    }

    public static void glPushAttrib(int mask) {
        // TODO: Proper state tracking; but at least for now the current cases of this didn't do anything related to textures and
        // just overly broadly set ALL_BITS :facepalm:
        GL11.glPushAttrib(GLStateManager.BYPASS_CACHE ? mask : (mask & ~(GL11.GL_TEXTURE_BIT)));
    }

    public static void glPopAttrib() {
        dirty = Dirty.ALL;
        GL11.glPopAttrib();
    }

    public static boolean checkDirty(long flag) {
        if((dirty & flag ) == flag) {
            dirty &= ~flag;
            return true;
        }
        return false;
    }
    public static void clean(long dirtyFlag) {
        dirty &= ~dirtyFlag;
    }

    public static void dirty(long dirtyFlag) {
        dirty |= dirtyFlag;
    }

    // Matrix Operations
    public static void glMatrixMode(int mode) {
        matrixState.setMode(mode);
        GL11.glMatrixMode(mode);
    }
    public static void glLoadIdentity() {
        GL11.glLoadIdentity();
        matrixState.identity();
    }

    public static void glTranslatef(float x, float y, float z) {
        GL11.glTranslatef(x, y, z);
        matrixState.translate(x, y, z);
    }
    public static void glTranslated(double x, double y, double z) {
        GL11.glTranslated(x, y, z);
        matrixState.translate((float) x, (float) y, (float) z);
    }

    public static void glScalef(float x, float y, float z) {
        GL11.glScalef(x, y, z);
        matrixState.scale(x, y, z);
    }

    public static void glScaled(double x, double y, double z) {
        GL11.glScaled(x, y, z);
        matrixState.scale((float) x, (float) y, (float) z);
    }

    public static void glMultMatrix(FloatBuffer matrix) {
        GL11.glMultMatrix(matrix);
        matrixState.multiply(matrix);
    }

    public static final Matrix4d conersionMatrix4d = new Matrix4d();
    public static final Matrix4f conersionMatrix4f = new Matrix4f();
    public static void glMultMatrix(DoubleBuffer matrix) {
        GL11.glMultMatrix(matrix);
        conersionMatrix4d.set(matrix);
        conersionMatrix4f.set(conersionMatrix4d);
        matrixState.multiply(conersionMatrix4f);
    }

    public static void glRotatef(float angle, float x, float y, float z) {
        GL11.glRotatef(angle, x, y, z);
        matrixState.rotate((float)Math.toRadians(angle), x, y, z);
    }

    public static void glRotated(double angle, double x, double y, double z) {
        GL11.glRotated(angle, x, y, z);
        matrixState.rotate((float)Math.toRadians(angle), (float) x, (float) y, (float) z);
    }

    public static void glOrtho(double left, double right, double bottom, double top, double zNear, double zFar) {
        GL11.glOrtho(left, right, bottom, top, zNear, zFar);
        matrixState.ortho(left, right, bottom, top, zNear, zFar);
    }

    public static void glFrustum(double left, double right, double bottom, double top, double zNear, double zFar) {
        GL11.glFrustum(left, right, bottom, top, zNear, zFar);
        matrixState.frustum(left, right, bottom, top, zNear, zFar);
    }
    public static void glPushMatrix() {
        GL11.glPushMatrix();
        matrixState.push();
    }

    public static void glPopMatrix() {
        GL11.glPopMatrix();
        matrixState.pop();
    }

    private static final Matrix4f perspectiveMatrix = new Matrix4f();
    private static final FloatBuffer perspectiveBuffer = BufferUtils.createFloatBuffer(16);
    public static void gluPerspective(float fovy, float aspect, float zNear, float zFar) {
        perspectiveMatrix.identity().perspective((float)Math.toRadians(fovy), aspect, zNear, zFar);

        perspectiveMatrix.get(0, perspectiveBuffer);
        GL11.glMultMatrix(perspectiveBuffer);

        matrixState.multiply(perspectiveMatrix);

    }

    public static void glViewport(int x, int y, int width, int height) {
        GL11.glViewport(x, y, width, height);
        viewportState.set(x, y, width, height);
    }

}
