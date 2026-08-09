package com.gtnewhorizons.angelica.iris;

import com.gtnewhorizons.angelica.client.rendering.TextureTracker;
import com.gtnewhorizons.angelica.glsm.hooks.DeferredAlphaHandler;
import com.gtnewhorizons.angelica.glsm.hooks.DeferredBlendHandler;
import com.gtnewhorizons.angelica.glsm.hooks.DeferredDepthColorHandler;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMConfig;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.hooks.ShaderWorkSubmitter;
import com.gtnewhorizons.angelica.glsm.hooks.VanillaBooleanLayer;
import com.gtnewhorizons.angelica.glsm.hooks.VanillaStateLayer;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import net.coderbot.iris.Iris;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
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
import net.coderbot.iris.uniforms.SystemTimeUniforms;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

public class IrisGLSMBridge {

    private static Runnable alphaFuncListener = null;
    private static Runnable alphaTestListener = null;
    private static Runnable blendFuncListener = null;
    private static Runnable fogModeListener = null;
    private static Runnable fogStartListener = null;
    private static Runnable fogEndListener = null;
    private static Runnable fogDensityListener = null;
    private static Runnable colorModulatorListener = null;

    private static boolean inputsDeferred = false;

    private static boolean blendDeferred = false;

    private static final Int2IntOpenHashMap programLastUpdatedFrame = new Int2IntOpenHashMap();

    private static void refreshBlendCondition() {
        if (Iris.getPipelineManager().getPipelineNullable() instanceof DeferredWorldRenderingPipeline drp) {
            drp.onVanillaBlendChanged();
        }
    }

    static {
        programLastUpdatedFrame.defaultReturnValue(-1);
        StateUpdateNotifiers.alphaFuncNotifier = listener -> alphaFuncListener = listener;
        StateUpdateNotifiers.alphaTestNotifier = listener -> alphaTestListener = listener;
        StateUpdateNotifiers.blendFuncNotifier = listener -> blendFuncListener = listener;
        StateUpdateNotifiers.fogModeNotifier = listener -> fogModeListener = listener;
        StateUpdateNotifiers.fogStartNotifier = listener -> fogStartListener = listener;
        StateUpdateNotifiers.fogEndNotifier = listener -> fogEndListener = listener;
        StateUpdateNotifiers.fogDensityNotifier = listener -> fogDensityListener = listener;
        StateUpdateNotifiers.colorModulatorNotifier = listener -> colorModulatorListener = listener;
    }

    private static VanillaBooleanLayer gated(VanillaBooleanLayer layer) {
        return new VanillaBooleanLayer() {
            @Override
            public boolean isOverrideHeld() {
                return Iris.enabled && layer.isOverrideHeld();
            }

            @Override
            public boolean getVanilla() {
                return layer.getVanilla();
            }

            @Override
            public void setVanilla(boolean enabled) {
                layer.setVanilla(enabled);
            }
        };
    }

    private static <T> VanillaStateLayer<T> gated(VanillaStateLayer<T> layer) {
        return new VanillaStateLayer<>() {
            @Override
            public boolean isOverrideHeld() {
                return Iris.enabled && layer.isOverrideHeld();
            }

            @Override
            public void readVanilla(T into) {
                layer.readVanilla(into);
            }

            @Override
            public void writeVanilla(T from) {
                layer.writeVanilla(from);
            }
        };
    }

    public static void register() {
        GLSMConfig.expandVertexFormats = Iris.enabled;
        IrisSamplers.initRenderer();
        GLSMHooks.shaderWorkSubmitter = new ShaderWorkSubmitter() {
            @Override
            public <T> CompletableFuture<T> submit(Supplier<T> work) {
                return Iris.ShaderTransformExecutor.submitTracked(work);
            }
        };
        GLSMHooks.postTransformProcessor = (src, shaderType) -> {
            if (Iris.ShaderTransformExecutor.isOnWorker()) {
                ShaderManager.prewarmSpirv(src, shaderType.id);
            } else {
                Iris.ShaderTransformExecutor.submitTracked(() -> {
                    ShaderManager.prewarmSpirv(src, shaderType.id);
                });
            }
        };
        GLSMHooks.blendHandler = new DeferredBlendHandler() {
            @Override
            public boolean isBlendLocked() {
                return Iris.enabled && BlendModeStorage.isBlendLocked();
            }

            @Override
            public boolean isOverrideHeld() {
                return Iris.enabled && BlendModeStorage.isOverrideHeld();
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

        GLStateManager.getBlendMode().setVanillaLayer(gated(BlendModeStorage.ENABLE_LAYER));
        GLStateManager.getBlendState().setVanillaLayer(gated(BlendModeStorage.FUNC_LAYER));
        GLStateManager.getAlphaTest().setVanillaLayer(gated(AlphaTestStorage.ENABLE_LAYER));
        GLStateManager.getAlphaState().setVanillaLayer(gated(AlphaTestStorage.FUNC_LAYER));
        GLStateManager.getDepthState().setVanillaLayer(gated(DepthColorStorage.DEPTH_LAYER));
        GLStateManager.getColorMask().setVanillaLayer(gated(DepthColorStorage.COLOR_LAYER));

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
            public boolean isOverrideHeld() {
                return Iris.enabled && DepthColorStorage.isOverrideHeld();
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

        GLSMHooks.ALPHA_STATE_CHANGE.addListener(event -> {
            if (Iris.enabled) {
                if (alphaFuncListener != null) alphaFuncListener.run();
                if (alphaTestListener != null) alphaTestListener.run();
            }
        });

        GLSMHooks.BLEND_FUNC_CHANGE.addListener(event -> {
            if (Iris.enabled) {
                if (blendFuncListener != null) blendFuncListener.run();
            }
        });

        GLSMHooks.SHADER_COLOR_CHANGE.addListener(event -> {
            if (Iris.enabled && colorModulatorListener != null) colorModulatorListener.run();
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
            if (!updatePipeline) return;

            if (GLStateManager.isForeignDraw()) {
                inputsDeferred = true;
                return;
            }
            Iris.getPipelineManager().getPipeline().ifPresent(p -> p.setInputs(StateTracker.INSTANCE.getInputs()));
        });

        GLSMHooks.VANILLA_BLEND_CHANGE.addListener(event -> {
            if (!Iris.enabled) return;
            if (GLStateManager.isForeignDraw()) {
                blendDeferred = true;
                return;
            }
            refreshBlendCondition();
        });

        GLSMHooks.FOREIGN_DRAW_END.addListener(event -> {
            if (!Iris.enabled) return;
            if (inputsDeferred) {
                inputsDeferred = false;
                Iris.getPipelineManager().getPipeline().ifPresent(p -> p.setInputs(StateTracker.INSTANCE.getInputs()));
            }
            if (blendDeferred) {
                blendDeferred = false;
                refreshBlendCondition();
            }
        });

        GLSMHooks.PROGRAM_CHANGE.addListener(event -> {
            if (!Iris.enabled) return;
            if (event.postBind) return;
            ProgramUniforms.clearActiveUniforms();
        });

        GLSMHooks.PROGRAM_CHANGE.addListener(event -> {
            if (!Iris.enabled) return;
            if (event.postBind) return;

            final WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
            if (!(pipeline instanceof DeferredWorldRenderingPipeline drp)) return;
            if (!drp.shouldOverrideShaders()) return;
            DepthColorStorage.unlockDepthColor();

            if (event.newProgram != 0 && !DepthColorStorage.isOwnedProgram(event.newProgram)) {
                drp.onModProgramOverride();
            }
        });

        GLSMHooks.PROGRAM_CHANGE.addListener(event -> {
            if (!Iris.enabled) return;
            if (!event.postBind || event.newProgram != 0) return;

            final WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
            if (!(pipeline instanceof DeferredWorldRenderingPipeline drp)) return;
            if (!drp.shouldOverrideShaders()) return;

            drp.restorePassAfterModProgram();
        });

        GLSMHooks.PROGRAM_CHANGE.addListener(event -> {
            if (!Iris.enabled) return;
            if (!event.postBind) return;
            WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
            if (pipeline instanceof DeferredWorldRenderingPipeline drp) {
                DeferredWorldRenderingPipeline.Pass activePass = drp.getActivePassProgram();
                if (activePass != null && activePass.getProgram() != null && activePass.getProgram().getProgramId() == event.newProgram) {
                    final int frame = SystemTimeUniforms.COUNTER.getAsInt();
                    if (programLastUpdatedFrame.get(event.newProgram) != frame) {
                        activePass.getProgram().getUniforms().update();
                        programLastUpdatedFrame.put(event.newProgram, frame);
                    }
                }
            }
        });

        GLSMHooks.PROGRAM_DELETE.addListener(event -> programLastUpdatedFrame.remove(event.program));
    }
}
