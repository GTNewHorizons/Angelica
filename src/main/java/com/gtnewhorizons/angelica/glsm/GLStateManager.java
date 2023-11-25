package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.states.AlphaState;
import com.gtnewhorizons.angelica.glsm.states.BlendState;
import com.gtnewhorizons.angelica.glsm.states.BooleanState;
import com.gtnewhorizons.angelica.glsm.states.Color4;
import com.gtnewhorizons.angelica.glsm.states.DepthState;
import com.gtnewhorizons.angelica.glsm.states.FogState;
import com.gtnewhorizons.angelica.glsm.states.GLColorMask;
import com.gtnewhorizons.angelica.glsm.states.TextureState;
import lombok.Getter;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gbuffer_overrides.state.StateTracker;
import net.coderbot.iris.gl.blending.AlphaTestStorage;
import net.coderbot.iris.gl.blending.BlendModeStorage;
import net.coderbot.iris.gl.blending.DepthColorStorage;
import net.coderbot.iris.gl.sampler.SamplerLimits;
import net.coderbot.iris.gl.state.StateUpdateNotifiers;
import net.coderbot.iris.samplers.IrisSamplers;
import net.coderbot.iris.texture.TextureInfoCache;
import net.coderbot.iris.texture.TextureTracker;
import net.coderbot.iris.texture.pbr.PBRTextureManager;
import org.joml.Vector3d;
import org.lwjgl.opengl.ARBMultitexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.stream.IntStream;

@SuppressWarnings("unused") // Used in ASM
public class GLStateManager {
    // GLStateManager State Trackers
    @Getter
    private static int activeTexture;

    @Getter
    private static final BlendState Blend = new BlendState();
    @Getter
    private static final DepthState Depth = new DepthState();
    @Getter
    private static final FogState Fog = new FogState();
    @Getter
    private static final Color4 Color = new Color4();
    @Getter
    private static final Color4 ClearColor = new Color4();
    @Getter
    private static final GLColorMask ColorMask = new GLColorMask();
    @Getter
    private static final BooleanState Cull = new BooleanState(GL11.GL_CULL_FACE);
    @Getter
    private static final AlphaState Alpha = new AlphaState();

    private static int modelShadeMode;

    // TODO: Maybe inject the iris stuff via mixins....
    @Getter
    private static final TextureState[] Textures;

    // Iris Listeners
    private static Runnable blendFuncListener = null;

    private static Runnable fogToggleListener = null;
    private static Runnable fogModeListener = null;
    private static Runnable fogStartListener = null;
    private static Runnable fogEndListener = null;
    private static Runnable fogDensityListener = null;

    // Thread Checking
    private static Thread MainThread;

    static {
        if(AngelicaConfig.enableIris) {
            StateUpdateNotifiers.blendFuncNotifier = listener -> blendFuncListener = listener;
            StateUpdateNotifiers.fogToggleNotifier = listener -> fogToggleListener = listener;
            StateUpdateNotifiers.fogModeNotifier = listener -> fogModeListener = listener;
            StateUpdateNotifiers.fogStartNotifier = listener -> fogStartListener = listener;
            StateUpdateNotifiers.fogEndNotifier = listener -> fogEndListener = listener;
            StateUpdateNotifiers.fogDensityNotifier = listener -> fogDensityListener = listener;
        }
        Textures = (TextureState[]) IntStream.range(0, SamplerLimits.get().getMaxTextureUnits()).mapToObj(i -> new TextureState()).toArray(TextureState[]::new);
        MainThread = Thread.currentThread();
    }

    public static void assertMainThread() {
        if (Thread.currentThread() != MainThread) {
            throw new IllegalStateException("Not on the main thread!");
        }
    }


    // LWJGL Overrides
    public static void glEnable(int cap) {
        switch(cap) {
            case GL11.GL_ALPHA_TEST -> enableAlphaTest();
            case GL11.GL_BLEND -> enableBlend();
            case GL11.GL_DEPTH_TEST -> enableDepthTest();
            case GL11.GL_CULL_FACE -> enableCull();
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
        Blend.mode.enable();
    }

    public static void disableBlend() {
        if (AngelicaConfig.enableIris) {
            if (BlendModeStorage.isBlendLocked()) {
                BlendModeStorage.deferBlendModeToggle(false);
                return;
            }
        }
        Blend.mode.disable();
    }

    public static void glBlendFunc(int srcFactor, int dstFactor) {
        if (AngelicaConfig.enableIris) {
            if (BlendModeStorage.isBlendLocked()) {
                BlendModeStorage.deferBlendFunc(srcFactor, dstFactor, srcFactor, dstFactor);
                return;
            }
        }
        Blend.srcRgb = srcFactor;
        Blend.dstRgb = dstFactor;
        GL11.glBlendFunc(srcFactor, dstFactor);

        // Iris
        if (blendFuncListener != null) blendFuncListener.run();
    }

    public static void glBlendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        if (AngelicaConfig.enableIris) {
            if (BlendModeStorage.isBlendLocked()) {
                BlendModeStorage.deferBlendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha);
                return;
            }
        }
        Blend.srcRgb = srcRgb;
        Blend.dstRgb = dstRgb;
        Blend.srcAlpha = srcAlpha;
        Blend.dstAlpha = dstAlpha;
        GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);

        // Iris
        if (blendFuncListener != null) blendFuncListener.run();
    }

    public static void glDepthFunc(int func) {
        if(func != Depth.func) {
            Depth.func = func;
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

        if(mask != Depth.mask) {
            Depth.mask = mask;
            GL11.glDepthMask(mask);
        }
    }
    public static void glColor4f(float red, float green, float blue, float alpha) {
        if(red != Color.red || green != Color.green || blue != Color.blue || alpha != Color.alpha) {
            Color.red = red;
            Color.green = green;
            Color.blue = blue;
            Color.alpha = alpha;
            GL11.glColor4f(red, green, blue, alpha);
        }
    }

    public static void clearCurrentColor() {
        Color.red = -1.0F;
        Color.green = -1.0F;
        Color.blue = -1.0F;
        Color.alpha = -1.0F;
    }

    public static void glColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        if (AngelicaConfig.enableIris) {
            if (DepthColorStorage.isDepthColorLocked()) {
                DepthColorStorage.deferColorMask(red, green, blue, alpha);
                return;
            }
        }
        if(red != ColorMask.red || green != ColorMask.green || blue != ColorMask.blue || alpha != ColorMask.alpha) {
            ColorMask.red = red;
            ColorMask.green = green;
            ColorMask.blue = blue;
            ColorMask.alpha = alpha;
            GL11.glColorMask(red, green, blue, alpha);
        }
    }

    // Clear Color
    public static void glClearColor(float red, float green, float blue, float alpha) {
        if(red != ClearColor.red || green != ClearColor.green || blue != ClearColor.blue || alpha != ClearColor.alpha) {
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
        Alpha.mode.enable();
    }

    public static void disableAlphaTest() {
        if (AngelicaConfig.enableIris) {
            if (AlphaTestStorage.isAlphaTestLocked()) {
                AlphaTestStorage.deferAlphaTestToggle(false);
                return;
            }
        }
        Alpha.mode.disable();
    }

    public static void glAlphaFunc(int function, float reference) {
        if (AngelicaConfig.enableIris) {
            if (AlphaTestStorage.isAlphaTestLocked()) {
                AlphaTestStorage.deferAlphaFunc(function, reference);
                return;
            }
        }
        Alpha.function = function;
        Alpha.reference = reference;
        GL11.glAlphaFunc(function, reference);
    }

    // Textures
    public static void glActiveTexture(int texture) {
        final int newTexture = texture - GL13.GL_TEXTURE0;
        if (activeTexture != newTexture) {
            activeTexture = newTexture;
            GL13.glActiveTexture(texture);
        }
    }
    public static void glActiveTextureARB(int texture) {
        final int newTexture = texture - GL13.GL_TEXTURE0;
        if (activeTexture != newTexture) {
            activeTexture = newTexture;
            ARBMultitexture.glActiveTextureARB(texture);
        }
    }

    public static void glBindTexture(int target, int texture) {
        if(Textures[activeTexture].binding != texture) {
            Textures[activeTexture].binding = texture;
            GL11.glBindTexture(target, texture);
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

    public static void glDeleteTextures(int id) {
        if (AngelicaConfig.enableIris) {
            iris$onDeleteTexture(id);
        }
        GL11.glDeleteTextures(id);
    }
    public static void glDeleteTextures(IntBuffer ids) {
        if (AngelicaConfig.enableIris) {
            for (int id : ids.array()) {
                iris$onDeleteTexture(id);
            }
        }
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
        int j;
        int i;
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
//        Iris.getPipelineManager().getPipeline().ifPresent(WorldRenderingPipeline::syncProgram);

        GL11.glDrawArrays(mode, first, count);
    }


    public static void defaultBlendFunc() {
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
    }

    public static void enableCull() {
        Cull.enable();
    }

    public static void disableCull() {
        Cull.disable();
    }

    public static void enableDepthTest() {
        Depth.mode.enable();
    }

    public static void disableDepthTest() {
        Depth.mode.disable();
    }

    public static void enableFog() {
        Fog.mode.enable();
        if (fogToggleListener != null) {
            fogToggleListener.run();
        }
    }

    public static void disableFog() {
        Fog.mode.disable();
        if (fogToggleListener != null) {
            fogToggleListener.run();
        }
    }

    public static void glFog(int pname, FloatBuffer param) {
        // TODO: Iris Notifier
        GL11.glFog(pname, param);
        if(pname == GL11.GL_FOG_COLOR) {
            final float red = param.get(0);
            final float green = param.get(1);
            final float blue = param.get(2);

            Fog.fogColor.set(red, green, blue);
            Fog.fogAlpha = param.get(3);
            Fog.fogColorBuffer.clear();
            Fog.fogColorBuffer.put((FloatBuffer) param.position(0)).flip();
        }
    }

    public static Vector3d getFogColor() {
        return Fog.fogColor;
    }


    public static void fogColor(float red, float green, float blue, float alpha) {
        if(red != Fog.fogColor.x || green != Fog.fogColor.y || blue != Fog.fogColor.z || alpha != Fog.fogAlpha) {
            Fog.fogColor.set(red, green, blue);
            Fog.fogAlpha = alpha;
            Fog.fogColorBuffer.clear();
            Fog.fogColorBuffer.put(red).put(green).put(blue).put(alpha).flip();
            GL11.glFog(GL11.GL_FOG_COLOR, Fog.fogColorBuffer);
        }
    }
    public static void glFogf(int pname, float param) {
        GL11.glFogf(pname, param);
        switch(pname) {
            case GL11.GL_FOG_DENSITY -> {
                Fog.density = param;
                if (fogDensityListener != null) {
                    fogDensityListener.run();
                }
            }
            case GL11.GL_FOG_START -> {
                Fog.start = param;
                if (fogStartListener != null) {
                    fogStartListener.run();
                }
            }
            case GL11.GL_FOG_END -> {
                Fog.end = param;
                if (fogEndListener != null) {
                    fogEndListener.run();
                }
            }
        }
    }
    public static void glFogi(int pname, int param) {
        GL11.glFogi(pname, param);
        if(pname == GL11.GL_FOG_MODE) {
            Fog.fogMode = param;
            if (fogModeListener != null) {
                fogModeListener.run();
            }
        }
    }

    public static void setFogBlack() {
        glFogf(GL11.GL_FOG_COLOR, 0.0F);

    }

    public static void glShadeModel(int mode) {
        if (modelShadeMode != mode) {
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

}
