package com.gtnewhorizons.angelica.iris;

import com.gtnewhorizons.angelica.client.rendering.TextureTracker;
import com.gtnewhorizons.angelica.glsm.hooks.DeferredAlphaHandler;
import com.gtnewhorizons.angelica.glsm.hooks.DeferredBlendHandler;
import com.gtnewhorizons.angelica.glsm.hooks.DeferredDepthColorHandler;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gbuffer_overrides.state.StateTracker;
import net.coderbot.iris.gl.blending.AlphaTestStorage;
import net.coderbot.iris.gl.blending.BlendModeStorage;
import net.coderbot.iris.gl.blending.DepthColorStorage;
import net.coderbot.iris.gl.program.ProgramUniforms;
import net.coderbot.iris.gl.state.StateUpdateNotifiers;
import net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.samplers.IrisSamplers;
import net.coderbot.iris.texture.pbr.PBRTextureManager;
import net.coderbot.iris.vertices.ImmediateState;

public class IrisGLSMBridge {
    
    private static Runnable blendFuncListener = null;
    private static Runnable fogModeListener = null;
    private static Runnable fogStartListener = null;
    private static Runnable fogEndListener = null;
    private static Runnable fogDensityListener = null;
    
    static {
        StateUpdateNotifiers.blendFuncNotifier = listener -> blendFuncListener = listener;
        StateUpdateNotifiers.fogModeNotifier = listener -> fogModeListener = listener;
        StateUpdateNotifiers.fogStartNotifier = listener -> fogStartListener = listener;
        StateUpdateNotifiers.fogEndNotifier = listener -> fogEndListener = listener;
        StateUpdateNotifiers.fogDensityNotifier = listener -> fogDensityListener = listener;
    }
    
    public static void register() {
        IrisSamplers.initRenderer();
        GLSMHooks.blendHandler = new DeferredBlendHandler() {
            @Override
            public boolean isBlendLocked() {
                return Iris.enabled && BlendModeStorage.isBlendLocked();
            }

            @Override
            public void deferBlendModeToggle(boolean enabled) {
                BlendModeStorage.deferBlendModeToggle(enabled);
            }

            @Override
            public void deferBlendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
                BlendModeStorage.deferBlendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha);
            }

            @Override
            public void flushDeferredBlend() {
                BlendModeStorage.flushDeferredBlend();
            }
        };

        GLSMHooks.alphaHandler = new DeferredAlphaHandler() {
            @Override
            public boolean isAlphaTestLocked() {
                return Iris.enabled && AlphaTestStorage.isAlphaTestLocked();
            }

            @Override
            public void deferAlphaTestToggle(boolean enabled) {
                AlphaTestStorage.deferAlphaTestToggle(enabled);
            }

            @Override
            public void deferAlphaFunc(int function, float reference) {
                AlphaTestStorage.deferAlphaFunc(function, reference);
            }
        };

        GLSMHooks.depthColorHandler = new DeferredDepthColorHandler() {
            @Override
            public boolean isDepthColorLocked() {
                return Iris.enabled && DepthColorStorage.isDepthColorLocked();
            }

            @Override
            public void deferDepthEnable(boolean enabled) {
                DepthColorStorage.deferDepthEnable(enabled);
            }

            @Override
            public void deferColorMask(boolean r, boolean g, boolean b, boolean a) {
                DepthColorStorage.deferColorMask(r, g, b, a);
            }
        };
        
        GLSMHooks.BLEND_FUNC_CHANGE.addListener(event -> {
            if (Iris.enabled) {
                if (blendFuncListener != null) blendFuncListener.run();
            }
        });
        
        GLSMHooks.FOG_STATE_CHANGE.addListener(event -> {
            if (Iris.enabled) {
                if (fogModeListener != null) fogModeListener.run();
                if (fogStartListener != null) fogStartListener.run();
                if (fogEndListener != null) fogEndListener.run();
                if (fogDensityListener != null) fogDensityListener.run();
            }
        });
        
        GLSMHooks.TEXTURE_BIND.addListener(event -> {
            if (Iris.enabled) {
                TextureTracker.INSTANCE.onBindTexture();
                final WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
                if (pipeline != null) {
                    pipeline.onBindTexture(event.textureId);
                }
            }
        });

        GLSMHooks.TEXTURE_DELETE.addListener(event -> {
            if (Iris.enabled) {
                PBRTextureManager.INSTANCE.onDeleteTexture(event.textureId);
            }
        });

        GLSMHooks.TEXTURE_UNIT_STATE.addListener(event -> {
            if (!Iris.enabled) return;
            boolean updatePipeline = false;
            if (event.unit == IrisSamplers.ALBEDO_TEXTURE_UNIT) {
                StateTracker.INSTANCE.albedoSampler = event.enabled;
                updatePipeline = true;
            } else if (event.unit == IrisSamplers.LIGHTMAP_TEXTURE_UNIT) {
                StateTracker.INSTANCE.lightmapSampler = event.enabled;
                updatePipeline = true;
            }
            if (updatePipeline) {
                Iris.getPipelineManager().getPipeline().ifPresent(p -> p.setInputs(StateTracker.INSTANCE.getInputs()));
            }
        });

        GLSMHooks.PROGRAM_CHANGE.addListener(event -> ProgramUniforms.clearActiveUniforms());

        GLSMHooks.PROGRAM_CHANGE.addListener(event -> {
            if (!Iris.enabled) return;
            final WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
            if (!(pipeline instanceof DeferredWorldRenderingPipeline drp)) return;
            if (!drp.shouldOverrideShaders()) return;
            if (drp.getActivePassProgramId() == -1) return;

            if (!ImmediateState.isRenderingLevel || DepthColorStorage.isOwnedProgram(event.newProgram)) {
                DepthColorStorage.unlockDepthColor();
            } else {
                drp.onModProgramOverride();
            }
        });
    }
}
