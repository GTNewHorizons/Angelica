package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.states.AlphaState;
import com.gtnewhorizons.angelica.glsm.states.BlendState;
import com.gtnewhorizons.angelica.glsm.states.BooleanState;
import com.gtnewhorizons.angelica.glsm.states.DepthState;
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
import org.lwjgl.opengl.ARBMultitexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

import java.nio.ByteBuffer;
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
    private static final GLColorMask ColorMask = new GLColorMask();
    @Getter
    private static final BooleanState Cull = new BooleanState(GL11.GL_CULL_FACE);
    @Getter
    private static final AlphaState Alpha = new AlphaState();
    @Getter
    private static final TextureState[] Textures;

    // Iris Listeners
    private static Runnable blendFuncListener;

    static {
        StateUpdateNotifiers.blendFuncNotifier = listener -> blendFuncListener = listener;
        Textures = (TextureState[]) IntStream.range(0, SamplerLimits.get().getMaxTextureUnits()).mapToObj(i -> new TextureState()).toArray(TextureState[]::new);
    }


    // LWJGL Overrides
    public static void glEnable(int cap) {
        switch(cap) {
            case GL11.GL_ALPHA_TEST -> enableAlphaTest();
            case GL11.GL_BLEND -> enableBlend();
            case GL11.GL_DEPTH_TEST -> Depth.mode.enable();
            case GL11.GL_CULL_FACE -> Cull.enable();
            case GL11.GL_TEXTURE_2D -> enableTexture();
            default -> GL11.glEnable(cap);
        }
    }


    public static void glDisable(int cap) {
        switch (cap) {
            case GL11.GL_ALPHA_TEST -> disableAlphaTest();
            case GL11.GL_BLEND -> disableBlend();
            case GL11.GL_DEPTH_TEST -> Depth.mode.disable();
            case GL11.GL_CULL_FACE -> Cull.disable();
            case GL11.GL_TEXTURE_2D -> disableTexture();
            default -> GL11.glDisable(cap);
        }
    }

    // GLStateManager Functions

    private static void enableBlend() {
        // Iris
        if(BlendModeStorage.isBlendLocked()) {
            BlendModeStorage.deferBlendModeToggle(true);
            return;
        }
        Blend.mode.enable();
    }

    public static void disableBlend() {
        // Iris
        if (BlendModeStorage.isBlendLocked()) {
            BlendModeStorage.deferBlendModeToggle(false);
            return;
        }
        Blend.mode.disable();
    }

    public static void glBlendFunc(int srcFactor, int dstFactor) {
        // Iris
        if(BlendModeStorage.isBlendLocked()) {
            BlendModeStorage.deferBlendFunc(srcFactor, dstFactor, srcFactor, dstFactor);
            return;
        }
        Blend.srcRgb = srcFactor;
        Blend.dstRgb = dstFactor;
        GL11.glBlendFunc(srcFactor, dstFactor);

        // Iris
        if (blendFuncListener != null) blendFuncListener.run();
    }

    public static void glBlendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        // Iris
        if(BlendModeStorage.isBlendLocked()) {
            BlendModeStorage.deferBlendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha);
            return;
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
        // Iris
        if (DepthColorStorage.isDepthColorLocked()) {
            DepthColorStorage.deferDepthEnable(mask);
            return;
        }

        if(mask != Depth.mask) {
            Depth.mask = mask;
            GL11.glDepthMask(mask);
        }
    }

    public static void glColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        // Iris
        if (DepthColorStorage.isDepthColorLocked()) {
            DepthColorStorage.deferColorMask(red, green, blue, alpha);
            return;
        }
        if(red != ColorMask.red || green != ColorMask.green || blue != ColorMask.blue || alpha != ColorMask.alpha) {
            ColorMask.red = red;
            ColorMask.green = green;
            ColorMask.blue = blue;
            ColorMask.alpha = alpha;
            GL11.glColorMask(red, green, blue, alpha);
        }
    }

    // ALPHA
    public static void enableAlphaTest() {
        // Iris
        if (AlphaTestStorage.isAlphaTestLocked()) {
            AlphaTestStorage.deferAlphaTestToggle(true);
            return;
        }
        Alpha.mode.enable();
    }

    public static void disableAlphaTest() {
        // Iris
        if (AlphaTestStorage.isAlphaTestLocked()) {
            AlphaTestStorage.deferAlphaTestToggle(false);
            return;
        }
        Alpha.mode.disable();
    }

    public static void glAlphaFunc(int function, float reference) {
        // Iris
        if (AlphaTestStorage.isAlphaTestLocked()) {
            AlphaTestStorage.deferAlphaFunc(function, reference);
            return;
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
            TextureTracker.INSTANCE.onBindTexture(texture);
        }
    }

    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, IntBuffer pixels) {
        // Iris
        TextureInfoCache.INSTANCE.onTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
        GL11.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
    }
    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, ByteBuffer pixels) {
        // Iris
        TextureInfoCache.INSTANCE.onTexImage2D(
            target, level, internalformat, width, height, border, format, type,
            pixels != null ? pixels.asIntBuffer() : (IntBuffer) null
        );
        GL11.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
    }

    public static void glDeleteTextures(int id) {
        // Iris
        iris$onDeleteTexture(id);
        GL11.glDeleteTextures(id);
    }
    public static void glDeleteTextures(IntBuffer ids) {
        // Iris
        for(int id: ids.array()) {
            iris$onDeleteTexture(id);
        }
        GL11.glDeleteTextures(ids);
    }

    public static void enableTexture() {
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

        if(updatePipeline) {
            Iris.getPipelineManager().getPipeline().ifPresent(p -> p.setInputs(StateTracker.INSTANCE.getInputs()));
        }

        Textures[activeTexture].mode.enable();
    }

    public static void disableTexture() {
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

        if(updatePipeline) {
            Iris.getPipelineManager().getPipeline().ifPresent(p -> p.setInputs(StateTracker.INSTANCE.getInputs()));
        }

        Textures[activeTexture].mode.disable();
    }

    public static void glDrawArrays(int mode, int first, int count) {
        // Iris -- TODO: This doesn't seem to work and is related to matchPass()
//        Iris.getPipelineManager().getPipeline().ifPresent(WorldRenderingPipeline::syncProgram);

        GL11.glDrawArrays(mode, first, count);
    }


    // Iris Functions

    private static void iris$onDeleteTexture(int id) {
        TextureTracker.INSTANCE.onDeleteTexture(id);
        TextureInfoCache.INSTANCE.onDeleteTexture(id);
        PBRTextureManager.INSTANCE.onDeleteTexture(id);
    }

}
