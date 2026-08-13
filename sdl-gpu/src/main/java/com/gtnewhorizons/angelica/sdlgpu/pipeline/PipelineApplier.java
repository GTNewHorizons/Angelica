package com.gtnewhorizons.angelica.sdlgpu.pipeline;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.sdl.SDL_FColor;
import org.lwjgl.sdl.SDL_GPUBufferBinding;
import org.lwjgl.sdl.SDL_GPUColorTargetInfo;
import org.lwjgl.sdl.SDL_GPUDepthStencilTargetInfo;
import org.lwjgl.sdl.SDL_GPUViewport;
import org.lwjgl.sdl.SDL_Rect;
import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager.FrameState;
import com.gtnewhorizons.angelica.sdlgpu.frame.StateSyncVerifier;
import com.gtnewhorizons.angelica.sdlgpu.pipeline.PipelineCache;
import com.gtnewhorizons.angelica.sdlgpu.resource.FBOClearTracker;
import com.gtnewhorizons.angelica.sdlgpu.resource.FboState;
import com.gtnewhorizons.angelica.sdlgpu.resource.PersistentBufferSync;
import com.gtnewhorizons.angelica.sdlgpu.resource.PersistentMapping;
import com.gtnewhorizons.angelica.sdlgpu.resource.PixelOps;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import com.gtnewhorizons.angelica.sdlgpu.sampler.SamplerBinder;
import com.gtnewhorizons.angelica.sdlgpu.sampler.StorageBufferBinder;
import com.gtnewhorizons.angelica.sdlgpu.sampler.StorageTextureBinder;
import com.gtnewhorizons.angelica.sdlgpu.shader.MatrixMarshal;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import com.gtnewhorizons.angelica.sdlgpu.shader.Std140Writer;
import com.gtnewhorizons.angelica.sdlgpu.shader.UniformStaging;
import com.gtnewhorizons.angelica.sdlgpu.util.MemoryAccess;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.HashSet;

import static org.lwjgl.sdl.SDLGPU.*;

public final class PipelineApplier {
    private static final Logger LOG = LogManager.getLogger("Angelica-SDLGPU");

    private static final int PERSISTENT_DRAIN_INTERVAL = 16;

    private static final Tracy.ZoneId Z_SDL_APPLY_STATE = Tracy.zoneId("sdlApplyState", Tracy.COLOR_CLIENT);
    private static final Tracy.ZoneId Z_SDL_PUSH_UNIFORMS = Tracy.zoneId("sdlPushUniforms", Tracy.COLOR_CLIENT);

    private final FrameManager frameManager;
    private final ResourceManager resourceManager;
    private final ShaderManager shaderManager;
    private final PipelineStore pipelineStore;
    private final FBOClearTracker fboClearTracker;
    private final PersistentBufferSync persistentSync;
    private final SamplerBinder samplerBinder;
    private final StorageTextureBinder storageTextureBinder;
    private final StorageBufferBinder storageBufferBinder;

    private DrawDispatch.FanUploadSink deferredUploadSink;
    public void setDeferredUploadSink(DrawDispatch.FanUploadSink sink) { this.deferredUploadSink = sink; }
    private boolean ringOverflowWarned;

    private final IntOpenHashSet pipelineZeroWarned        = new IntOpenHashSet();
    private final IntOpenHashSet noRpWarned                = new IntOpenHashSet();
    private final IntOpenHashSet cullAllWarned             = new IntOpenHashSet();
    private final IntOpenHashSet dummyVboZeroWarned        = new IntOpenHashSet();
    private final IntOpenHashSet staleFboWarned            = new IntOpenHashSet();
    private final LongOpenHashSet bogusScissorWarned       = new LongOpenHashSet();
    private final IntOpenHashSet pushUniformsNullCbWarned  = new IntOpenHashSet();

    public PipelineApplier(FrameManager frameManager, ResourceManager resourceManager, ShaderManager shaderManager, PipelineStore pipelineStore, FBOClearTracker fboClearTracker, PersistentBufferSync persistentSync, SamplerBinder samplerBinder, StorageTextureBinder storageTextureBinder, StorageBufferBinder storageBufferBinder) {
        this.frameManager = frameManager;
        this.resourceManager = resourceManager;
        this.shaderManager = shaderManager;
        this.pipelineStore = pipelineStore;
        this.fboClearTracker = fboClearTracker;
        this.persistentSync = persistentSync;
        this.samplerBinder = samplerBinder;
        this.storageTextureBinder = storageTextureBinder;
        this.storageBufferBinder = storageBufferBinder;
    }

    public void ensureRenderPass(ContextState st) {
        ensureRenderPass(st, frameManager.frame());
    }

    public void ensureRenderPass(ContextState st, FrameState f) {
        if (!f.frameActive) return;

        fboClearTracker.flushPendingClearsForBoundSamplers(st);

        if (!st.deferUploads) {
            persistentSync.uploadDirtyPersistentRegion();
        } else if (++st.drawsSincePersistentDrain >= PERSISTENT_DRAIN_INTERVAL) {
            st.drawsSincePersistentDrain = 0;
            persistentSync.enqueueDirtyPersistentRegions();
        }

        if (!st.deferUploads && f.renderPass != 0 && st.attribDefaultsDirtyMask != 0 && (st.attribDefaultsDirtyMask & ~st.currentVao.attribEnabledMask) != 0) {
            frameManager.endRenderPassIfActive(f);
        }

        if (f.renderPass != 0 && st.anyUniformBlockDirty()) {
            frameManager.endRenderPassIfActive(f);
            if (Tracy.ENABLED) frameManager.notePerFrameBlockPassBreak();
        }

        if (st.boundFboId == 0) {
            if (frameManager.ensureFbo0RenderPass(f, st)) {
                st.pipeline.setDepthTargetFormat(resourceManager.getSwapchainDepthStencilFormat());
            }
            return;
        }

        final FboState fbo = resourceManager.getFbo(st.boundFboId);
        if (fbo == null) {
            LOG.error("ensureRenderPass: FBO {} not found", st.boundFboId);
            return;
        }

        if (fbo.targetsDirty) fbo.recomputeTargets();
        if (!fbo.hasAnyColor && fbo.depthTexture == 0) return;

        final long primaryTarget = fbo.primaryTarget;
        if (f.renderPass != 0 && f.activeLayoutHash == fbo.structuralLayoutHash && f.currentColorTarget == primaryTarget && f.currentDepthTarget == fbo.depthTexture && !FBOClearTracker.fboHasPendingClear(st, fbo)) {
            return;
        }

        final int totalTargets = fbo.drawBuffers.length;
        final long dummyTex = totalTargets > 0 ? resourceManager.getOrCreateDummyColorTarget() : 0L;

        int proposedClearOps = 0;
        long layoutHash = Hashing.fmix64(0xA5A5A5A5A5A5A5A5L, totalTargets);
        for (int i = 0; i < totalTargets; i++) {
            final int db = fbo.drawBuffers[i];
            final long tex;
            if (db >= 0 && db < ContextState.MAX_COLOR_ATTACHMENTS && fbo.colorTextures[db] != 0) {
                tex = fbo.colorTextures[db];
            } else {
                tex = dummyTex;
            }
            final boolean pendingClear = tex != dummyTex && st.pendingColorTextures.contains(tex);
            final boolean defined = tex != dummyTex && resourceManager.isTextureContentDefined(tex);
            final boolean firstUse = !pendingClear && tex != dummyTex && !defined;
            if (pendingClear || firstUse) proposedClearOps |= 1 << i;
            layoutHash = Hashing.fmix64(layoutHash, tex);
            layoutHash = Hashing.fmix64(layoutHash, db);
            if (pendingClear || firstUse) {
                final float[] c = pendingClear ? st.pendingColorValues.get(tex) : null;
                layoutHash = foldClearColor(layoutHash, c != null ? c[0] : st.clearR, c != null ? c[1] : st.clearG, c != null ? c[2] : st.clearB, c != null ? c[3] : st.clearA);
            }
        }
        final boolean proposedDepthClear;
        final boolean proposedStencilClear;
        final boolean depthHasStencil = fbo.depthTexture != 0 && PixelOps.isDepthStencilFormat(fbo.depthFormat);
        if (fbo.depthTexture != 0) {
            final boolean pendingClear = st.pendingDepthTextures.contains(fbo.depthTexture);
            final boolean firstUse = !pendingClear && !st.clearedTexturesThisFrame.contains(fbo.depthTexture);
            proposedDepthClear = pendingClear || firstUse;
            final boolean pendingStencil = depthHasStencil && st.pendingStencilTextures.contains(fbo.depthTexture);
            if (depthHasStencil) {
                final boolean firstStencilUse = !pendingStencil && !st.clearedStencilTexturesThisFrame.contains(fbo.depthTexture);
                proposedStencilClear = pendingStencil || firstStencilUse;
            } else {
                proposedStencilClear = false;
            }
            layoutHash = Hashing.fmix64(layoutHash, fbo.depthTexture);
            if (proposedDepthClear) {
                layoutHash = foldClearDepth(layoutHash, pendingClear ? st.pendingDepthValues.get(fbo.depthTexture) : st.depthClearValue);
            }
            if (proposedStencilClear) {
                layoutHash = foldClearStencil(layoutHash, pendingStencil ? st.pendingStencilValues.get(fbo.depthTexture) : st.stencilClearValue);
            }
        } else {
            proposedDepthClear = false;
            proposedStencilClear = false;
        }

        frameManager.endRenderPassIfActive(f);

        final boolean reuse = fbo.cachedTargetsValid && fbo.cachedTargetsLayoutHash == layoutHash && fbo.cachedTargetsCount == totalTargets && fbo.cachedClearOpFlags == proposedClearOps && fbo.cachedDepthClearLast == proposedDepthClear && fbo.cachedStencilClearLast == proposedStencilClear;

        SDL_GPUColorTargetInfo.Buffer colorTargets = null;
        SDL_GPUDepthStencilTargetInfo depthTarget = null;

        if (reuse) {
            if (totalTargets > 0) colorTargets = fbo.cachedColorTargets.position(0).limit(totalTargets);
            if (fbo.depthTexture != 0) depthTarget = fbo.cachedDepthTarget;
        } else {
            if (totalTargets > 0) {
                colorTargets = fbo.cachedColorTargets.position(0).limit(totalTargets);
                for (int i = 0; i < totalTargets; i++) {
                    final int db = fbo.drawBuffers[i];
                    final long tex;
                    if (db >= 0 && db < ContextState.MAX_COLOR_ATTACHMENTS && fbo.colorTextures[db] != 0) {
                        tex = fbo.colorTextures[db];
                    } else {
                        tex = dummyTex;
                    }
                    final boolean pendingClear = tex != dummyTex && st.pendingColorTextures.contains(tex);
                    final boolean clearThis = (proposedClearOps & (1 << i)) != 0;
                    final long ctAddr = colorTargets.address() + (long) i * SDL_GPUColorTargetInfo.SIZEOF;
                    MemoryAccess.putAddress(ctAddr + SDL_GPUColorTargetInfo.TEXTURE, tex);
                    MemoryAccess.putInt(ctAddr + SDL_GPUColorTargetInfo.LOAD_OP, clearThis ? SDL_GPU_LOADOP_CLEAR : SDL_GPU_LOADOP_LOAD);
                    MemoryAccess.putInt(ctAddr + SDL_GPUColorTargetInfo.STORE_OP, db >= 0 ? SDL_GPU_STOREOP_STORE : SDL_GPU_STOREOP_DONT_CARE);
                    if (clearThis) {
                        final long ccAddr = ctAddr + SDL_GPUColorTargetInfo.CLEAR_COLOR;
                        if (pendingClear) {
                            final float[] color = st.pendingColorValues.get(tex);
                            MemoryAccess.putFloat(ccAddr + SDL_FColor.R, color[0]);
                            MemoryAccess.putFloat(ccAddr + SDL_FColor.G, color[1]);
                            MemoryAccess.putFloat(ccAddr + SDL_FColor.B, color[2]);
                            MemoryAccess.putFloat(ccAddr + SDL_FColor.A, color[3]);
                        } else {
                            MemoryAccess.putFloat(ccAddr + SDL_FColor.R, st.clearR);
                            MemoryAccess.putFloat(ccAddr + SDL_FColor.G, st.clearG);
                            MemoryAccess.putFloat(ccAddr + SDL_FColor.B, st.clearB);
                            MemoryAccess.putFloat(ccAddr + SDL_FColor.A, st.clearA);
                        }
                    }
                }
            }
            if (fbo.depthTexture != 0) {
                final boolean pendingClear = st.pendingDepthTextures.contains(fbo.depthTexture);
                depthTarget = fbo.cachedDepthTarget;
                final long dtAddr = depthTarget.address();
                MemoryAccess.putAddress(dtAddr + SDL_GPUDepthStencilTargetInfo.TEXTURE, fbo.depthTexture);
                MemoryAccess.putInt(dtAddr + SDL_GPUDepthStencilTargetInfo.LOAD_OP, proposedDepthClear ? SDL_GPU_LOADOP_CLEAR : SDL_GPU_LOADOP_LOAD);
                MemoryAccess.putInt(dtAddr + SDL_GPUDepthStencilTargetInfo.STORE_OP, SDL_GPU_STOREOP_STORE);
                if (proposedDepthClear) {
                    final float dv = pendingClear ? st.pendingDepthValues.get(fbo.depthTexture) : st.depthClearValue;
                    MemoryAccess.putFloat(dtAddr + SDL_GPUDepthStencilTargetInfo.CLEAR_DEPTH, dv);
                }
                if (depthHasStencil) {
                    final boolean pendingStencil = st.pendingStencilTextures.contains(fbo.depthTexture);
                    MemoryAccess.putInt(dtAddr + SDL_GPUDepthStencilTargetInfo.STENCIL_LOAD_OP, proposedStencilClear ? SDL_GPU_LOADOP_CLEAR : SDL_GPU_LOADOP_LOAD);
                    MemoryAccess.putInt(dtAddr + SDL_GPUDepthStencilTargetInfo.STENCIL_STORE_OP, SDL_GPU_STOREOP_STORE);
                    if (proposedStencilClear) {
                        final int sv = pendingStencil ? st.pendingStencilValues.get(fbo.depthTexture) : st.stencilClearValue;
                        MemoryAccess.putByte(dtAddr + SDL_GPUDepthStencilTargetInfo.CLEAR_STENCIL, (byte) sv);
                    }
                } else {
                    MemoryAccess.putInt(dtAddr + SDL_GPUDepthStencilTargetInfo.STENCIL_LOAD_OP, SDL_GPU_LOADOP_LOAD);
                    MemoryAccess.putInt(dtAddr + SDL_GPUDepthStencilTargetInfo.STENCIL_STORE_OP, SDL_GPU_STOREOP_STORE);
                }
            }
            fbo.cachedTargetsLayoutHash = layoutHash;
            fbo.cachedTargetsCount = totalTargets;
            fbo.cachedClearOpFlags = proposedClearOps;
            fbo.cachedDepthClearLast = proposedDepthClear;
            fbo.cachedStencilClearLast = proposedStencilClear;
            fbo.cachedTargetsValid = true;
        }

        consumeClears(resourceManager, st, fbo, dummyTex, proposedClearOps, proposedDepthClear, proposedStencilClear, depthHasStencil);

        frameManager.beginRenderPass(colorTargets, depthTarget);
        frameManager.setActiveLayoutHash(fbo.structuralLayoutHash);
    }

    static void consumeClears(ResourceManager rm, ContextState st, FboState fbo, long dummyTex, int clearOps, boolean depthClear, boolean stencilClear, boolean depthHasStencil) {
        for (int i = 0, n = fbo.drawBuffers.length; i < n; i++) {
            final int db = fbo.drawBuffers[i];
            final long tex = (db >= 0 && db < ContextState.MAX_COLOR_ATTACHMENTS) ? fbo.colorTextures[db] : 0L;
            if (tex == 0 || tex == dummyTex) continue;
            st.pendingColorTextures.remove(tex);
            if ((clearOps & (1 << i)) != 0) rm.markTextureContentDefined(tex);
        }
        if (fbo.depthTexture == 0) return;
        st.pendingDepthTextures.remove(fbo.depthTexture);
        if (depthClear) st.clearedTexturesThisFrame.add(fbo.depthTexture);
        if (depthHasStencil) {
            st.pendingStencilTextures.remove(fbo.depthTexture);
            if (stencilClear) st.clearedStencilTexturesThisFrame.add(fbo.depthTexture);
        }
    }

    static long foldClearColor(long h, float r, float g, float b, float a) {
        h = Hashing.fmix64(h, Float.floatToRawIntBits(r));
        h = Hashing.fmix64(h, Float.floatToRawIntBits(g));
        h = Hashing.fmix64(h, Float.floatToRawIntBits(b));
        return Hashing.fmix64(h, Float.floatToRawIntBits(a));
    }

    static long foldClearDepth(long h, float depth) {
        return Hashing.fmix64(h, Float.floatToRawIntBits(depth));
    }

    static long foldClearStencil(long h, int stencil) {
        return Hashing.fmix64(h, stencil);
    }

    private static final int RING_BLOCK_BYTES = ContextState.MAX_VERTEX_ATTRIBS * 16;
    private static final int RING_CHUNK_BYTES = ResourceManager.ATTRIB_RING_CHUNK_BLOCKS * RING_BLOCK_BYTES;

    private void advanceAttribDefaultsRing(ContextState st) {
        final long dummyVBO = resourceManager.getOrCreateDummyVertexBuffer();
        if (dummyVBO == 0) return;
        if (st.ringChunkStaging == null) {
            st.ringChunkStaging = MemoryUtil.memAlloc(RING_CHUNK_BYTES);
        }
        if (st.ringChunkBaseOffset == 0) {
            st.ringChunkBaseOffset = resourceManager.nextAttribRingChunkOffset();
            st.ringChunkUsedBlocks = 0;
        }
        final int blockInChunk = st.ringChunkUsedBlocks * RING_BLOCK_BYTES;
        st.ringChunkStaging.position(blockInChunk);
        st.ringChunkStaging.asFloatBuffer().put(st.attribDefaults, 0, ContextState.MAX_VERTEX_ATTRIBS * 4);
        st.attribDefaultsRingBase = st.ringChunkBaseOffset + blockInChunk;
        st.ringChunkUsedBlocks++;
        if (st.ringChunkUsedBlocks == ResourceManager.ATTRIB_RING_CHUNK_BLOCKS) {
            flushAttribRingChunk(st);
        }
        st.attribDefaultsDirtyMask = 0;
        st.bumpAttribStateGen();
        if (++st.ringAdvancesThisFrame >= ResourceManager.ATTRIB_RING_BLOCKS - 2 * ResourceManager.ATTRIB_RING_CHUNK_BLOCKS && !ringOverflowWarned) {
            ringOverflowWarned = true;
            LOG.warn("Attrib-defaults ring wrapped within one frame ({} advances); earlier draws may read overwritten blocks", st.ringAdvancesThisFrame);
        }
    }

    public void flushAttribRingChunk(ContextState st) {
        if (st.ringChunkBaseOffset == 0 || st.ringChunkUsedBlocks == 0) {
            st.ringChunkBaseOffset = 0;
            return;
        }
        final long dummyVBO = resourceManager.getOrCreateDummyVertexBuffer();
        if (dummyVBO != 0 && deferredUploadSink != null) {
            st.ringChunkStaging.position(0).limit(st.ringChunkUsedBlocks * RING_BLOCK_BYTES);
            deferredUploadSink.enqueuePreCopied(st.ringChunkStaging, dummyVBO, st.ringChunkBaseOffset, false);
            st.ringChunkStaging.clear();
        }
        st.ringChunkBaseOffset = 0;
        st.ringChunkUsedBlocks = 0;
    }

    public boolean applyPipelineAndState(ContextState st) {
        return applyPipelineAndState(st, frameManager.frame());
    }

    public boolean applyPipelineAndState(ContextState st, FrameState f) {
        if (!Tracy.FINE_ZONES) {
            return applyPipelineAndStateImpl(st, f);
        }
        Tracy.beginZone(Z_SDL_APPLY_STATE);
        try {
            return applyPipelineAndStateImpl(st, f);
        } finally {
            Tracy.endZone();
        }
    }

    private boolean applyPipelineAndStateImpl(ContextState st, FrameState f) {
        if (Tracy.ENABLED) frameManager.noteStateApply();
        if (SystemProperties.SDL_VERIFY_STATE_SYNC && Thread.currentThread() == GLStateManager.getMainThread()) {
            StateSyncVerifier.verify(
                GLStateManager.getBoundVAO(), st.boundVAO,
                GLStateManager.getDrawFramebuffer(), st.boundFboId,
                GLStateManager.getReadFramebuffer(), st.boundReadFboId,
                GLStateManager.getActiveTextureUnit(), st.activeTextureUnit);
        }
        final long rp = f.renderPass;
        final long rpGeneration = f.renderPassGeneration;
        if (rp == 0) {
            if (noRpWarned.add(st.boundProgram)) {
                LOG.warn("applyPipelineAndState: rp==0 for boundProgram={} boundFbo={}; draw skipped", st.boundProgram, st.boundFboId);
            }
            return false;
        }
        if (st.pipeline.cullEnabled && st.pipeline.cullAll) {
            if (cullAllWarned.add(st.boundProgram)) {
                LOG.warn("applyPipelineAndState: cullAll for boundProgram={} cullFaceMode=0x{}; draw skipped", st.boundProgram, Integer.toHexString(st.pipeline.cullFaceMode));
            }
            return false;
        }

        if (st.attribDefaultsDirtyMask != 0 && st.deferUploads && deferredUploadSink != null) {
            advanceAttribDefaultsRing(st);
        }

        final long rpGen = f.renderPassGeneration;
        if (rpGen != st.lastAppliedRenderPassGen) {
            st.lastAppliedRenderPassGen = rpGen;
            st.viewportDirty = true;
            st.scissorDirty = true;
            st.blendColorDirty = true;
            st.lastBoundPipeline = 0;
            st.lastAppliedStencilRef = Integer.MIN_VALUE;
            st.lastBoundEboHandle = 0;
            st.lastBoundEboIndexSize = -1;
            st.lastAppliedSamplerBindGen = -1;
            st.lastAppliedSamplerProgram = 0;
            st.lastFragSamplerProgram = 0;
            st.lastVertSamplerProgram = 0;
            st.lastAppliedStorageTexBindGen = -1;
            st.lastAppliedStorageTexProgram = 0;
            st.lastFragStorageTexProgram = 0;
            st.lastVertStorageTexProgram = 0;
            st.lastAppliedStorageBufBindGen = -1;
            st.lastAppliedStorageBufProgram = 0;
            st.lastFragStorageBufProgram = 0;
            st.lastVertStorageBufProgram = 0;
            st.lastAppliedVboBindGen = -1;
            st.lastAppliedVboBindProgram = 0;
            st.lastAppliedVboBindCb = 0;
        }

        final boolean renderingToFbo = st.boundFboId != 0;
        final FboState fboState = renderingToFbo ? resourceManager.getFbo(st.boundFboId) : null;
        if (renderingToFbo && fboState == null && staleFboWarned.add(st.boundFboId)) {
            LOG.warn("applyPipelineAndState: boundFboId={} but FboState is null - bound FBO was deleted; draws will use viewport-derived fallback height", st.boundFboId);
        }
        final long pipeline = st.pipeline.getOrCreatePipeline(pipelineStore, st, renderingToFbo);
        if (pipeline == 0) {
            if (pipelineZeroWarned.add(st.boundProgram)) {
                LOG.warn("applyPipelineAndState: pipeline==0 for boundProgram={} vs={} fs={} inputMask=0x{}; draw skipped",
                    st.boundProgram, st.pipeline.vertexShader, st.pipeline.fragmentShader,
                    Integer.toHexString(st.pipeline.shaderInputMask));
            }
            return false;
        }
        if (pipeline != st.lastBoundPipeline) {
            if (Tracy.ENABLED) frameManager.noteBatchBreakPipeline();
            SDL_BindGPUGraphicsPipeline(rp, pipeline);
            st.lastBoundPipeline = pipeline;
            st.lastAppliedSamplerProgram = 0;
            st.lastAppliedSamplerCb = 0;
            st.lastAppliedStorageTexProgram = 0;
            st.lastAppliedStorageTexCb = 0;
            st.lastAppliedStorageBufProgram = 0;
            st.lastAppliedStorageBufCb = 0;
        }

        if (st.viewportDirty) {
            float vpY = st.viewportY;
            float vpH = st.viewportH;
            if (renderingToFbo) {
                vpY = st.viewportY + st.viewportH;
                vpH = -st.viewportH;
            }
            final long addr = st.cachedViewport.address();
            MemoryAccess.putFloat(addr + SDL_GPUViewport.X, st.viewportX);
            MemoryAccess.putFloat(addr + SDL_GPUViewport.Y, vpY);
            MemoryAccess.putFloat(addr + SDL_GPUViewport.W, st.viewportW);
            MemoryAccess.putFloat(addr + SDL_GPUViewport.H, vpH);
            MemoryAccess.putFloat(addr + SDL_GPUViewport.MIN_DEPTH, st.viewportDepthNear);
            MemoryAccess.putFloat(addr + SDL_GPUViewport.MAX_DEPTH, st.viewportDepthFar);
            SDL_SetGPUViewport(rp, st.cachedViewport);
            st.viewportDirty = false;
        }

        if (st.scissorDirty) {
            final long addr = st.cachedScissor.address();
            final int sx, sy, sw, sh;
            final int fbHeight;
            if (st.scissorEnabled) {
                if (renderingToFbo) {
                    fbHeight = (fboState != null && fboState.height > 0) ? fboState.height : (int)(st.viewportY + st.viewportH);
                } else {
                    fbHeight = (int)(st.viewportY + st.viewportH);
                }
                sx = st.scissorX;
                sy = fbHeight - st.scissorY - st.scissorH;
                sw = st.scissorW;
                sh = st.scissorH;
                MemoryAccess.putInt(addr + SDL_Rect.X, sx);
                MemoryAccess.putInt(addr + SDL_Rect.Y, sy);
                MemoryAccess.putInt(addr + SDL_Rect.W, sw);
                MemoryAccess.putInt(addr + SDL_Rect.H, sh);
                if ((sw <= 0 || sh <= 0) && bogusScissorWarned.add(Hashing.packHiLo(sw, sh))) {
                    LOG.warn("applyPipelineAndState: scissor degenerate (x={} y={} w={} h={}) - all fragments clipped; boundProgram={} boundFbo={} fbHeight={} scissorEn={} src=[{},{},{},{}]", sx, sy, sw, sh, st.boundProgram, st.boundFboId, fbHeight, st.scissorEnabled, st.scissorX, st.scissorY, st.scissorW, st.scissorH);
                }
            } else {
                MemoryAccess.putInt(addr + SDL_Rect.X, (int) st.viewportX);
                MemoryAccess.putInt(addr + SDL_Rect.Y, (int) st.viewportY);
                MemoryAccess.putInt(addr + SDL_Rect.W, (int) st.viewportW);
                MemoryAccess.putInt(addr + SDL_Rect.H, (int) st.viewportH);
            }
            SDL_SetGPUScissor(rp, st.cachedScissor);
            st.scissorDirty = false;
        }

        if (st.blendColorDirty) {
            final long addr = st.cachedBlendColor.address();
            MemoryAccess.putFloat(addr + SDL_FColor.R, st.blendColorR);
            MemoryAccess.putFloat(addr + SDL_FColor.G, st.blendColorG);
            MemoryAccess.putFloat(addr + SDL_FColor.B, st.blendColorB);
            MemoryAccess.putFloat(addr + SDL_FColor.A, st.blendColorA);
            SDL_SetGPUBlendConstants(rp, st.cachedBlendColor);
            st.blendColorDirty = false;
        }

        if (st.pipeline.effectiveStencilTestEnabled() && st.stencilRef != st.lastAppliedStencilRef) {
            SDL_SetGPUStencilReference(rp, (byte) st.stencilRef);
            st.lastAppliedStencilRef = st.stencilRef;
        }

        final int shaderMask = st.pipeline.shaderInputMask;
        final ContextState.VAOState vao = st.currentVao;
        final int usedMask = shaderMask | vao.attribEnabledMask;
        final long applyCb = frameManager.getCommandBuffer(f);
        if (usedMask != 0 && (st.attribStateGen != st.lastAppliedVboBindGen || st.boundProgram != st.lastAppliedVboBindProgram || applyCb != st.lastAppliedVboBindCb)) {
            final int maxSlot = 31 - Integer.numberOfLeadingZeros(usedMask);
            final long dummyVBO = resourceManager.getOrCreateDummyVertexBuffer();
            if (dummyVBO == 0) {
                if (dummyVboZeroWarned.add(st.boundProgram)) {
                    LOG.warn("applyPipelineAndState: getOrCreateDummyVertexBuffer()==0 for boundProgram={}; draw skipped", st.boundProgram);
                }
                return false;
            }
            final int bindingSize = SDL_GPUBufferBinding.SIZEOF;
            final int validRange = (1 << (maxSlot + 1)) - 1;

            int rem = ~vao.attribEnabledMask & validRange;
            while (rem != 0) {
                final int i = Integer.numberOfTrailingZeros(rem);
                rem &= rem - 1;
                final long elemAddr = st.vboBindingsAddr + (long) i * bindingSize;
                MemoryAccess.putAddress(elemAddr + SDL_GPUBufferBinding.BUFFER, dummyVBO);
                MemoryAccess.putInt(elemAddr + SDL_GPUBufferBinding.OFFSET, st.attribDefaultsRingBase + i * 16);
            }

            int enabled = vao.attribEnabledMask & validRange;
            while (enabled != 0) {
                final int i = Integer.numberOfTrailingZeros(enabled);
                enabled &= enabled - 1;
                final int b = vao.attribBinding[i];
                final long vboHandle = resourceManager.getBufferHandle(vao.bindingBuffer[b]);
                final long elemAddr = st.vboBindingsAddr + (long) i * bindingSize;
                if (vboHandle != 0) {
                    MemoryAccess.putAddress(elemAddr + SDL_GPUBufferBinding.BUFFER, vboHandle);
                    MemoryAccess.putInt(elemAddr + SDL_GPUBufferBinding.OFFSET, (int) vao.bindingOffset[b]);
                } else {
                    MemoryAccess.putAddress(elemAddr + SDL_GPUBufferBinding.BUFFER, dummyVBO);
                    MemoryAccess.putInt(elemAddr + SDL_GPUBufferBinding.OFFSET, i * 16);
                }
            }

            nSDL_BindGPUVertexBuffers(rp, 0, st.vboBindingsAddr, maxSlot + 1);
            st.lastAppliedVboBindGen = st.attribStateGen;
            st.lastAppliedVboBindProgram = st.boundProgram;
            st.lastAppliedVboBindCb = applyCb;
        }

        if (Tracy.ENABLED && st.boundProgram != 0
            && (st.boundProgram != st.lastAppliedSamplerProgram || st.samplerBindGen != st.lastAppliedSamplerBindGen || applyCb != st.lastAppliedSamplerCb)) {
            frameManager.noteBatchBreakSampler();
        }
        samplerBinder.bindSamplers(rp, st, applyCb);
        storageTextureBinder.bindStorageTextures(rp, st, applyCb);
        storageBufferBinder.bindStorageBuffers(rp, st, applyCb);

        if (st.boundProgram != 0) pushUniforms(rp, st, f);

        if (SystemProperties.SDL_ENCODER_ASSERTIONS && f.renderPassGeneration != rpGeneration) {
            final String msg = "render pass ended during applyPipelineAndState (generation " + rpGeneration + " -> " + f.renderPassGeneration + "); the captured pass handle is dead";
            if (SystemProperties.SDL_ENCODER_ASSERTIONS_FATAL) throw new IllegalStateException(msg);
            LOG.error(msg);
        }

        return true;
    }

    public void pushUniforms(long renderPass, ContextState st) {
        pushUniforms(renderPass, st, frameManager.frame());
    }

    public void pushUniforms(long renderPass, ContextState st, FrameState f) {
        if (!Tracy.FINE_ZONES) {
            pushUniformsImpl(renderPass, st, f);
            return;
        }
        Tracy.beginZone(Z_SDL_PUSH_UNIFORMS);
        try {
            pushUniformsImpl(renderPass, st, f);
        } finally {
            Tracy.endZone();
        }
    }

    private void pushUniformsImpl(long renderPass, ContextState st, FrameState f) {
        final ShaderManager.ProgramObject prog = st.boundProgramObj;
        if (prog == null) return;
        final long cb = frameManager.getCommandBuffer(f);
        if (cb == 0) {
            if (pushUniformsNullCbWarned.add(st.boundProgram)) {
                LOG.warn("pushUniforms: getCommandBuffer()==0 for boundProgram={}; uniforms not pushed", st.boundProgram);
            }
            return;
        }
        final int program = st.boundProgram;
        if (prog.externalUboBinding >= 0) {
            pushExternalUboBlock(cb, st, prog, program);
            return;
        }
        final UniformStaging us = st.uniformStaging(prog);
        final boolean vsNeeds = us.vsUniformBuf != null && (us.vsUniformDirty || cb != st.lastPushedCbVs || program != st.lastPushedProgramVs);
        final boolean fsNeeds = us.fsUniformBuf != null && (us.fsUniformDirty || cb != st.lastPushedCbFs || program != st.lastPushedProgramFs);
        if (!vsNeeds && !fsNeeds) {
            if (Tracy.ENABLED) frameManager.noteUniformPushSkipped();
            return;
        }
        if (vsNeeds) {
            us.vsUniformBuf.position(0).limit(prog.vertexUboSize);
            SDL_PushGPUVertexUniformData(cb, 0, us.vsUniformBuf);
            us.vsUniformDirty = false;
            st.lastPushedCbVs = cb;
            st.lastPushedProgramVs = program;
            if (Tracy.ENABLED) frameManager.noteUniformPushBytes(prog.vertexUboSize);
        }
        if (fsNeeds) {
            us.fsUniformBuf.position(0).limit(prog.fragmentUboSize);
            SDL_PushGPUFragmentUniformData(cb, 0, us.fsUniformBuf);
            us.fsUniformDirty = false;
            st.lastPushedCbFs = cb;
            st.lastPushedProgramFs = program;
            if (Tracy.ENABLED) frameManager.noteUniformPushBytes(prog.fragmentUboSize);
        }
    }

    private void pushExternalUboBlock(long cb, ContextState st, ShaderManager.ProgramObject prog, int program) {
        final int binding = prog.externalUboBinding;
        if (binding < 0 || binding >= ContextState.MAX_INDEXED_BUFFERS) return;
        final int gen = st.uboRangeGen;
        final boolean vsNeeds = prog.vertexUboSize > 0 && (gen != st.lastPushedUboGenVs || cb != st.lastPushedCbVs || program != st.lastPushedProgramVs);
        final boolean fsNeeds = prog.fragmentUboSize > 0 && (gen != st.lastPushedUboGenFs || cb != st.lastPushedCbFs || program != st.lastPushedProgramFs);
        if (!vsNeeds && !fsNeeds) {
            if (Tracy.ENABLED) frameManager.noteExternalUboPushSkipped();
            return;
        }
        if (vsNeeds) {
            final long addr = uboRangeAddress(st, binding, prog.vertexUboSize);
            if (addr != 0L) {
                if (Tracy.ENABLED) frameManager.noteExternalUboPush();
                nSDL_PushGPUVertexUniformData(cb, 0, addr, prog.vertexUboSize);
                st.lastPushedCbVs = cb;
                st.lastPushedProgramVs = program;
                st.lastPushedUboGenVs = gen;
                if (Tracy.ENABLED) frameManager.noteUniformPushBytes(prog.vertexUboSize);
            }
        }
        if (fsNeeds) {
            final long addr = uboRangeAddress(st, binding, prog.fragmentUboSize);
            if (addr != 0L) {
                if (Tracy.ENABLED) frameManager.noteExternalUboPush();
                nSDL_PushGPUFragmentUniformData(cb, 0, addr, prog.fragmentUboSize);
                st.lastPushedCbFs = cb;
                st.lastPushedProgramFs = program;
                st.lastPushedUboGenFs = gen;
                if (Tracy.ENABLED) frameManager.noteUniformPushBytes(prog.fragmentUboSize);
            }
        }
    }

    private long uboRangeAddress(ContextState st, int binding, int size) {
        final int glId = st.boundUboByIndex[binding];
        if (glId == 0) return 0L;
        final int offset = st.uboRangeOffset[binding];
        final PersistentMapping pm = resourceManager.getPersistentMapping(glId);
        if (pm != null) {
            if (offset < 0 || offset + size > pm.staging.capacity()) return 0L;
            return MemoryUtil.memAddress0(pm.staging) + offset;
        }
        final ByteBuffer shadow = resourceManager.getUboShadow(glId);
        if (shadow != null) {
            if (offset < 0 || offset + size > shadow.capacity()) return 0L;
            return MemoryUtil.memAddress0(shadow) + offset;
        }
        return 0L;
    }

    public float[] reuseOrAlloc(ContextState st, int location, int length) {
        final ShaderManager.ProgramObject prog = st.boundProgramObj;
        if (prog != null && location >= 0 && location < prog.uniformSlotCount) {
            final float[] existing = st.uniformStaging(prog).uniformDataBySlot[location];
            if (existing != null && existing.length == length) return existing;
        }
        return new float[length];
    }

    public void putUniformFv(ContextState st, int location, FloatBuffer value) {
        final ShaderManager.ProgramObject prog = st.boundProgramObj;
        if (prog == null || location < 0 || location >= prog.uniformSlotCount) return;
        final int n = value.remaining();
        final int pos = value.position();
        final float[] existing = st.uniformStaging(prog).uniformDataBySlot[location];
        if (existing != null && existing.length == n) {
            if (contentEquals(existing, value, pos, n)) {
                return;
            }
            value.get(pos, existing);
            putUniform(st, location, existing);
            return;
        }
        final float[] v = new float[n];
        value.get(pos, v);
        putUniform(st, location, v);
    }

    static boolean contentEquals(float[] existing, FloatBuffer value, int pos, int n) {
        for (int i = 0; i < n; i++) {
            if (Float.floatToRawIntBits(existing[i]) != Float.floatToRawIntBits(value.get(pos + i))) return false;
        }
        return true;
    }

    public void flushUniformBlocks(ContextState st) {
        for (int b = 0; b < ShaderManager.BLOCK_COUNT; b++) {
            flushUniformBlock(st, b);
        }
    }

    private void flushUniformBlock(ContextState st, int b) {
        final ContextState.UniformBlockState block = st.uniformBlocks[b];
        final int size = block.size;
        if (size == 0) return;
        if (block.glId == 0 || block.allocSize < size) {
            block.glId = resourceManager.genBuffer();
            resourceManager.createBuffer(block.glId, SDL_GPU_BUFFERUSAGE_GRAPHICS_STORAGE_READ, size);
            block.allocSize = size;

            st.boundSsboByIndex[ShaderManager.BLOCK_BINDINGS[b]] = block.glId;
            st.ssboBindGen++;

            block.staging(size);
            block.dirty = true;
        }

        if (!block.dirty) return;
        final ByteBuffer bytes = block.bytes();
        if (bytes == null) return;
        final long handle = resourceManager.getBufferHandle(block.glId);
        if (handle == 0) return;
        final long copyPass = frameManager.ensureCopyPass();
        if (copyPass == 0) return;

        bytes.position(0).limit(Math.min(size, bytes.capacity()));

        resourceManager.uploadToBuffer(copyPass, bytes, handle, 0, true);
        frameManager.endCopyPassIfActive();
        block.dirty = false;
        block.flushedThisFrame = true;
        if (Tracy.ENABLED) frameManager.noteUniformBlockFlush(b, size);
    }

    private static final boolean VERIFY_PER_FRAME_BLOCK = SystemProperties.SDL_VERIFY_PER_FRAME_UNIFORM_BLOCK;
    private final HashSet<String> perFrameBlockDriftWarned = new HashSet<>();

    private void notePerFrameBlockDrift(ShaderManager.ProgramObject prog, int location) {
        final String name = prog.locationToName.getOrDefault(location, "?");
        if (perFrameBlockDriftWarned.add(name)) {
            LOG.warn("per-frame block member '{}' changed after this frame's upload; it is not frame-global and must be excluded from the block", name);
        }
    }

    public void putUniform(ContextState st, int location, float[] data) {
        final ShaderManager.ProgramObject prog = st.boundProgramObj;
        if (prog == null || location < 0 || location >= prog.uniformSlotCount) return;
        final UniformStaging us = st.uniformStaging(prog);
        final int len = data.length;
        final long valueKey;
        if (len == 1) {
            valueKey = Float.floatToRawIntBits(data[0]) & 0xFFFFFFFFL;
        } else if (len == 2) {
            valueKey = Hashing.packHiLo(Float.floatToRawIntBits(data[1]), Float.floatToRawIntBits(data[0]));
        } else {
            valueKey = Hashing.hashFloats(data);
        }
        if (Tracy.ENABLED) st.slotWrites++;
        if (us.uniformValueHashBySlot[location] == valueKey) {
            final float[] existing = us.uniformDataBySlot[location];
            if (existing == data) {
                if (Tracy.ENABLED) st.slotWritesElided++;
                return;
            }
            if (existing != null && existing.length == len && Arrays.equals(existing, data)) {
                us.uniformDataBySlot[location] = data;
                if (Tracy.ENABLED) st.slotWritesElided++;
                return;
            }
        }
        us.uniformDataBySlot[location] = data;
        us.uniformValueHashBySlot[location] = valueKey;
        for (int b = 0; b < ShaderManager.BLOCK_COUNT; b++) {
            final ShaderManager.UniformMemberInfo info = prog.blockInfoBySlot[b][location];
            if (info == null) continue;
            final ContextState.UniformBlockState block = st.uniformBlocks[b];
            if (prog.blockSize[b] > block.size) block.size = prog.blockSize[b];
            final FloatBuffer blockFb = block.staging(prog.blockSize[b]);

            final int hashSlot = block.hashSlot(info.offset());
            if (hashSlot >= 0) {
                if (block.hash(hashSlot) == valueKey) {
                    if (Tracy.ENABLED) st.slotWritesElided++;
                    return;
                }
                block.setHash(hashSlot, valueKey);
            }
            writeMember(st, prog, blockFb, prog.blockSize[b], info, data, location, false);
            block.dirty = true;
            if (VERIFY_PER_FRAME_BLOCK && b == ShaderManager.BLOCK_PER_FRAME && block.flushedThisFrame) {
                notePerFrameBlockDrift(prog, location);
            }
            return;
        }
        final ShaderManager.UniformMemberInfo vs = prog.vsInfoBySlot[location];
        if (vs != null && us.vsUniformFb != null) {
            writeMember(st, prog, us.vsUniformFb, prog.vertexUboSize, vs, data, location, true);
            us.vsUniformDirty = true;
        }
        final ShaderManager.UniformMemberInfo fs = prog.fsInfoBySlot[location];
        if (fs != null && us.fsUniformFb != null) {
            writeMember(st, prog, us.fsUniformFb, prog.fragmentUboSize, fs, data, location, false);
            us.fsUniformDirty = true;
        }
    }

    public void storeMatrix(ContextState st, int location, boolean transpose, FloatBuffer value, int size) {
        if (location < 0) return;
        final int n = value.remaining();
        final int floatsPerMatrix = size * size;
        final int count = n / floatsPerMatrix;
        if (count == 0) {
            final float[] v = reuseOrAlloc(st, location, n);
            value.get(value.position(), v);
            putUniform(st, location, v);
            return;
        }
        final float[] out = reuseOrAlloc(st, location, count * floatsPerMatrix);
        MatrixMarshal.marshalMatrixToColumnMajor(value, value.position(), count, size, transpose, out, 0);
        putUniform(st, location, out);
    }

    private void writeMember(ContextState st, ShaderManager.ProgramObject prog, FloatBuffer ubo, int uboSize, ShaderManager.UniformMemberInfo info, float[] data, int loc, boolean isVs) {
        final int floatsPerElement = info.vectorSize() * info.columns();
        if (floatsPerElement <= 0) return;
        final int srcCount = data.length / floatsPerElement;
        if (srcCount <= 0) return;
        final int dataBytes = srcCount * floatsPerElement * 4;
        final int avail = uboSize - info.offset();
        if (dataBytes > avail) {
            throw new IllegalStateException("Uniform overflows UBO: prog=" + st.boundProgram + " loc=" + loc + " name='" + prog.locationToName.getOrDefault(loc, "?") + "' dataBytes=" + dataBytes + " availFromOffset=" + avail + " stage=" + (isVs ? "VS" : "FS"));
        }
        Std140Writer.write(ubo, info, data, srcCount);
    }
}
