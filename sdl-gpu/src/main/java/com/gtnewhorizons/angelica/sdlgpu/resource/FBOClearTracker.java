package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import com.gtnewhorizons.angelica.sdlgpu.sampler.SamplerLookup;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import com.gtnewhorizons.angelica.sdlgpu.util.MemoryAccess;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.lwjgl.sdl.SDL_FColor;
import org.lwjgl.sdl.SDL_GPUColorTargetInfo;
import org.lwjgl.sdl.SDL_GPUDepthStencilTargetInfo;
import org.lwjgl.system.MemoryStack;

import java.util.Arrays;

import static org.lwjgl.sdl.SDLGPU.*;

public final class FBOClearTracker {
    private final FrameManager frameManager;
    private final ResourceManager resourceManager;
    private final ShaderManager shaderManager;

    public FBOClearTracker(FrameManager frameManager, ResourceManager resourceManager, ShaderManager shaderManager) {
        this.frameManager = frameManager;
        this.resourceManager = resourceManager;
        this.shaderManager = shaderManager;
    }

    public static void recordPendingColorClear(ContextState st, long tex, float r, float g, float b, float a) {
        float[] cur = st.pendingColorValues.get(tex);
        if (cur == null) {
            cur = new float[4];
            st.pendingColorValues.put(tex, cur);
        }
        cur[0] = r; cur[1] = g; cur[2] = b; cur[3] = a;
        if (st.pendingColorTextures.add(tex)) {
            st.pendingMutationGen++;
        }
    }

    public static void recordPendingDepthClear(ContextState st, long tex, float value) {
        st.pendingDepthValues.put(tex, value);
        if (st.pendingDepthTextures.add(tex)) {
            st.pendingMutationGen++;
        }
    }

    public static void recordPendingStencilClear(ContextState st, long tex, int value) {
        st.pendingStencilValues.put(tex, value);
        if (st.pendingStencilTextures.add(tex)) {
            st.pendingMutationGen++;
        }
    }

    public static boolean fboHasPendingClear(ContextState st, FboState fbo) {
        if (fbo.depthTexture != 0
            && (st.pendingDepthTextures.contains(fbo.depthTexture) || st.pendingStencilTextures.contains(fbo.depthTexture))) {
            return true;
        }
        if (st.pendingColorTextures.isEmpty()) return false;
        for (int db : fbo.drawBuffers) {
            if (db < 0 || db >= ContextState.MAX_COLOR_ATTACHMENTS) continue;
            final long tex = fbo.colorTextures[db];
            if (tex != 0 && st.pendingColorTextures.contains(tex)) return true;
        }
        return false;
    }

    public static void snapshotFlushGenerations(ContextState st) {
        st.lastFlushedSamplerBindGen = st.samplerBindGen;
        st.lastFlushedProgram = st.boundProgram;
        st.lastFlushedPendingMutationGen = st.pendingMutationGen;
    }

    public void flushPendingClearsForBoundSamplers(ContextState st) {
        if (st.pendingColorTextures.isEmpty() && st.pendingDepthTextures.isEmpty() && st.pendingStencilTextures.isEmpty()) return;

        if (st.lastFlushedSamplerBindGen == st.samplerBindGen && st.lastFlushedProgram == st.boundProgram && st.lastFlushedPendingMutationGen == st.pendingMutationGen) {
            return;
        }

        if (st.boundProgram == 0) {
            snapshotFlushGenerations(st);
            return;
        }
        final ShaderManager.ProgramObject prog = shaderManager.getProgram(st.boundProgram);
        if (prog == null || !prog.linked) {
            snapshotFlushGenerations(st);
            return;
        }

        final LongArrayList colorHandles = st.samplerFlushColorHandles;
        final IntArrayList colorGlIds = st.samplerFlushColorGlIds;
        final LongArrayList depthHandles = st.samplerFlushDepthHandles;
        final IntArrayList depthGlIds = st.samplerFlushDepthGlIds;
        colorHandles.clear();
        colorGlIds.clear();
        depthHandles.clear();
        depthGlIds.clear();

        final int fragSamplers = Math.min(prog.fragmentResources.numSamplers(), ContextState.MAX_SAMPLERS);
        collectSamplerFlush(st, SamplerLookup.resolvedUnits(prog, true), fragSamplers, colorHandles, colorGlIds, depthHandles, depthGlIds);

        final int vertSamplers = Math.min(prog.vertexResources.numSamplers(), ContextState.MAX_SAMPLERS);
        collectSamplerFlush(st, SamplerLookup.resolvedUnits(prog, false), vertSamplers, colorHandles, colorGlIds, depthHandles, depthGlIds);

        if (!colorHandles.isEmpty() || !depthHandles.isEmpty()) {
            materializeFlush(st, colorHandles, colorGlIds, depthHandles, depthGlIds);
        }
        snapshotFlushGenerations(st);
    }

    private void collectSamplerFlush(ContextState st, int[] samplerUnits, int samplerCount, LongArrayList colorHandles, IntArrayList colorGlIds, LongArrayList depthHandles, IntArrayList depthGlIds) {
        for (int i = 0; i < samplerCount; i++) {
            final int glUnit = samplerUnits[i];
            if (glUnit < 0 || glUnit >= st.boundTextures.length) continue;
            final int glTexId = st.boundTextures[glUnit];
            if (glTexId == 0) continue;
            final long handle = resourceManager.getTextureHandle(glTexId);
            if (handle == 0) continue;
            if (st.pendingColorTextures.remove(handle)) {
                colorHandles.add(handle);
                colorGlIds.add(glTexId);
            } else if (st.pendingDepthTextures.contains(handle) || st.pendingStencilTextures.contains(handle)) {
                depthHandles.add(handle);
                depthGlIds.add(glTexId);
            }
        }
    }

    private void materializeFlush(ContextState st, LongArrayList colorHandles, IntArrayList colorGlIds, LongArrayList depthHandles, IntArrayList depthGlIds) {
        frameManager.endRenderPassIfActive(FrameManager.PASS_END_CLEAR);

        final int n = colorHandles.size();
        boolean[] consumed = null;
        if (n > 0) {
            if (st.materializeFlushConsumed.length < n) st.materializeFlushConsumed = new boolean[Math.max(n, st.materializeFlushConsumed.length * 2)];
            consumed = st.materializeFlushConsumed;
            Arrays.fill(consumed, 0, n, false);
        }
        final int[] batchIdx = st.materializeFlushBatchIdx;
        for (int i = 0; i < n; i++) {
            if (consumed[i]) continue;
            final ResourceManager.TextureMeta metaI = resourceManager.getTextureMeta(colorGlIds.getInt(i));
            if (metaI == null) {
                consumed[i] = true;
                continue;
            }
            final int fmt = metaI.sdlFormat();
            final int w = metaI.width();
            final int h = metaI.height();
            int batchCount = 0;
            for (int j = i; j < n && batchCount < ContextState.MAX_COLOR_ATTACHMENTS; j++) {
                if (consumed[j]) continue;
                final ResourceManager.TextureMeta mj = (j == i) ? metaI : resourceManager.getTextureMeta(colorGlIds.getInt(j));
                if (mj == null) { consumed[j] = true; continue; }
                if (mj.sdlFormat() == fmt && mj.width() == w && mj.height() == h) {
                    batchIdx[batchCount++] = j;
                }
            }
            if (batchCount == 0) continue;
            try (var stack = MemoryStack.stackPush()) {
                final SDL_GPUColorTargetInfo.Buffer targets = SDL_GPUColorTargetInfo.calloc(batchCount, stack);
                for (int k = 0; k < batchCount; k++) {
                    final int idx = batchIdx[k];
                    final long handle = colorHandles.getLong(idx);
                    final float[] color = st.pendingColorValues.get(handle);
                    putColorClear(targets.get(k).address(), handle, 0, color[0], color[1], color[2], color[3]);
                    resourceManager.markTextureContentDefined(handle);
                    consumed[idx] = true;
                }
                frameManager.noteClearPass();
                frameManager.beginRenderPass(targets, null);
                frameManager.endRenderPassIfActive(FrameManager.PASS_END_CLEAR);
            }
        }

        final int dn = depthHandles.size();
        for (int i = 0; i < dn; i++) {
            final long handle = depthHandles.getLong(i);
            emitDepthStencilClearPass(st, handle, st.pendingDepthTextures.remove(handle), st.pendingStencilTextures.remove(handle), false);
        }
    }

    private void emitDepthStencilClearPass(ContextState st, long handle, boolean clearDepth, boolean clearStencil, boolean materialized) {
        if (!clearDepth && !clearStencil) return;
        if (materialized) frameManager.noteMaterializedClearPass();
        depthStencilClearPass(handle, 0, clearDepth, clearDepth ? st.pendingDepthValues.get(handle) : 0f, clearStencil, clearStencil ? st.pendingStencilValues.get(handle) : 0);
        if (clearDepth) st.pendingDepthValues.remove(handle);
        if (clearStencil) st.pendingStencilValues.remove(handle);
        if (clearDepth && clearStencil) resourceManager.markTextureContentDefined(handle);
    }

    public boolean discardPendingClearIfFullyCovered(ContextState st, long handle, int dstX, int dstY, int dstLevel, int width, int height, ResourceManager.TextureMeta meta, boolean allAspects) {
        if (handle == 0 || meta == null) return false;
        if (dstLevel != 0 || dstX != 0 || dstY != 0) return false;
        if (width != meta.width() || height != meta.height() || meta.depth() > 1) return false;
        if (st.pendingDepthTextures.contains(handle) || st.pendingStencilTextures.contains(handle)) {
            if (!allAspects) return false;
            st.pendingDepthTextures.remove(handle);
            st.pendingDepthValues.remove(handle);
            st.pendingStencilTextures.remove(handle);
            st.pendingStencilValues.remove(handle);
        } else if (st.pendingColorTextures.remove(handle)) {
            st.pendingColorValues.remove(handle);
        } else {
            return false;
        }
        st.pendingMutationGen++;
        return true;
    }

    public void resolveDestinationForWrite(ContextState st, long destTex, ResourceManager.TextureMeta destMeta, int level, int dx, int dy, int dz, int w, int h, boolean allAspects) {
        if (dz != 0 || !discardPendingClearIfFullyCovered(st, destTex, dx, dy, level, w, h, destMeta, allAspects)) {
            materializePendingClearForTexture(st, destTex);
        }
        resourceManager.markTextureContentDefined(destTex);
    }

    public void materializePendingClearForRead(ContextState st, FrameManager.FrameState f, long texture) {
        if (texture == 0L || !f.frameActive) return;
        if (texture == frameManager.getFbo0Texture()) {
            if (st.pendingSwapchainClear || !f.clearedThisFrame) frameManager.ensureFbo0RenderPass(f, st);
        } else if (texture == resourceManager.getSwapchainDepthStencil()) {
            if (st.pendingSwapchainDepthClear || st.pendingSwapchainStencilClear || !f.depthClearedThisFrame) frameManager.ensureFbo0RenderPass(f, st);
        } else {
            materializePendingClearForTexture(st, texture);
        }
    }

    public void materializePendingClearForTexture(ContextState st, long handle) {
        if (handle == 0) return;
        final boolean depthPending = st.pendingDepthTextures.remove(handle);
        final boolean stencilPending = st.pendingStencilTextures.remove(handle);
        if (depthPending || stencilPending) {
            frameManager.endRenderPassIfActive(FrameManager.PASS_END_CLEAR);
            emitDepthStencilClearPass(st, handle, depthPending, stencilPending, true);
            st.pendingMutationGen++;
            return;
        }
        if (st.pendingColorTextures.remove(handle)) {
            final float[] color = st.pendingColorValues.remove(handle);
            frameManager.endRenderPassIfActive(FrameManager.PASS_END_CLEAR);
            try (var stack = MemoryStack.stackPush()) {
                final SDL_GPUColorTargetInfo.Buffer targets = SDL_GPUColorTargetInfo.calloc(1, stack);
                putColorClear(targets.get(0).address(), handle, 0, color != null ? color[0] : 0f, color != null ? color[1] : 0f, color != null ? color[2] : 0f, color != null ? color[3] : 0f);
                frameManager.noteClearPass();
                frameManager.noteMaterializedClearPass();
                frameManager.beginRenderPass(targets, null);
                frameManager.endRenderPassIfActive(FrameManager.PASS_END_CLEAR);
                resourceManager.markTextureContentDefined(handle);
            }
            st.pendingMutationGen++;
        }
    }

    public void materializePendingClearsForMipTargets(ContextState st, FboState fbo) {
        for (int db : fbo.drawBuffers) {
            if (db < 0 || db >= ContextState.MAX_COLOR_ATTACHMENTS || fbo.colorLevels[db] == 0) continue;
            final long tex = fbo.colorTextures[db];
            if (tex != 0 && st.pendingColorTextures.contains(tex)) materializePendingClearForTexture(st, tex);
        }
        if (fbo.depthLevel != 0 && fbo.depthTexture != 0 && (st.pendingDepthTextures.contains(fbo.depthTexture) || st.pendingStencilTextures.contains(fbo.depthTexture))) {
            materializePendingClearForTexture(st, fbo.depthTexture);
        }
    }

    public void clearColorLevel(long handle, int level, float r, float g, float b, float a) {
        frameManager.endRenderPassIfActive(FrameManager.PASS_END_CLEAR);
        try (var stack = MemoryStack.stackPush()) {
            final SDL_GPUColorTargetInfo.Buffer targets = SDL_GPUColorTargetInfo.calloc(1, stack);
            putColorClear(targets.get(0).address(), handle, level, r, g, b, a);
            frameManager.noteClearPass();
            frameManager.beginRenderPass(targets, null);
            frameManager.endRenderPassIfActive(FrameManager.PASS_END_CLEAR);
        }
    }

    public void clearDepthStencilLevel(long handle, int level, boolean clearDepth, float depth, boolean clearStencil, int stencil) {
        if (!clearDepth && !clearStencil) return;
        frameManager.endRenderPassIfActive(FrameManager.PASS_END_CLEAR);
        depthStencilClearPass(handle, level, clearDepth, depth, clearStencil, stencil);
    }

    private void depthStencilClearPass(long handle, int level, boolean clearDepth, float depth, boolean clearStencil, int stencil) {
        try (var stack = MemoryStack.stackPush()) {
            final SDL_GPUDepthStencilTargetInfo dt = SDL_GPUDepthStencilTargetInfo.calloc(stack);
            final long addr = dt.address();
            MemoryAccess.putAddress(addr + SDL_GPUDepthStencilTargetInfo.TEXTURE, handle);
            MemoryAccess.putByte(addr + SDL_GPUDepthStencilTargetInfo.MIP_LEVEL, (byte) level);
            MemoryAccess.putInt(addr + SDL_GPUDepthStencilTargetInfo.LOAD_OP, clearDepth ? SDL_GPU_LOADOP_CLEAR : SDL_GPU_LOADOP_LOAD);
            MemoryAccess.putInt(addr + SDL_GPUDepthStencilTargetInfo.STORE_OP, SDL_GPU_STOREOP_STORE);
            if (clearDepth) {
                MemoryAccess.putFloat(addr + SDL_GPUDepthStencilTargetInfo.CLEAR_DEPTH, depth);
            }
            if (clearStencil) {
                MemoryAccess.putInt(addr + SDL_GPUDepthStencilTargetInfo.STENCIL_LOAD_OP, SDL_GPU_LOADOP_CLEAR);
                MemoryAccess.putInt(addr + SDL_GPUDepthStencilTargetInfo.STENCIL_STORE_OP, SDL_GPU_STOREOP_STORE);
                MemoryAccess.putByte(addr + SDL_GPUDepthStencilTargetInfo.CLEAR_STENCIL, (byte) stencil);
            }
            frameManager.noteClearPass();
            frameManager.beginRenderPass(null, dt);
            frameManager.endRenderPassIfActive(FrameManager.PASS_END_CLEAR);
        }
    }

    private static void putColorClear(long addr, long handle, int level, float r, float g, float b, float a) {
        MemoryAccess.putAddress(addr + SDL_GPUColorTargetInfo.TEXTURE, handle);
        MemoryAccess.putInt(addr + SDL_GPUColorTargetInfo.MIP_LEVEL, level);
        MemoryAccess.putInt(addr + SDL_GPUColorTargetInfo.LOAD_OP, SDL_GPU_LOADOP_CLEAR);
        MemoryAccess.putInt(addr + SDL_GPUColorTargetInfo.STORE_OP, SDL_GPU_STOREOP_STORE);
        final long ccAddr = addr + SDL_GPUColorTargetInfo.CLEAR_COLOR;
        MemoryAccess.putFloat(ccAddr + SDL_FColor.R, r);
        MemoryAccess.putFloat(ccAddr + SDL_FColor.G, g);
        MemoryAccess.putFloat(ccAddr + SDL_FColor.B, b);
        MemoryAccess.putFloat(ccAddr + SDL_FColor.A, a);
    }

    public void materializeAllPendingClears(ContextState st) {
        if (st.pendingColorTextures.isEmpty() && st.pendingDepthTextures.isEmpty() && st.pendingStencilTextures.isEmpty()) return;
        final LongArrayList handles = st.samplerFlushColorHandles;
        handles.clear();
        handles.addAll(st.pendingColorTextures);
        handles.addAll(st.pendingDepthTextures);
        handles.addAll(st.pendingStencilTextures);
        for (int i = 0; i < handles.size(); i++) {
            materializePendingClearForTexture(st, handles.getLong(i));
        }
        handles.clear();
    }

    public void scrubPendingClearsForTexture(ContextState st, int glId) {
        final long handle = resourceManager.getTextureHandle(glId);
        if (handle == 0) return;
        st.pendingColorTextures.remove(handle);
        st.pendingColorValues.remove(handle);
        st.pendingDepthTextures.remove(handle);
        st.pendingDepthValues.remove(handle);
        st.pendingStencilTextures.remove(handle);
        st.pendingStencilValues.remove(handle);
    }

    public boolean fbosHaveSameAttachments(ContextState st, int a, int b) {
        final FboState fa = resourceManager.getFbo(a);
        final FboState fb = resourceManager.getFbo(b);
        if (fa == null || fb == null) return false;
        if (fa.depthTexture != fb.depthTexture) return false;
        if (fa.depthFormat != fb.depthFormat) return false;
        if (fa.depthLevel != fb.depthLevel) return false;
        if (fa.colorAttachmentCount != fb.colorAttachmentCount) return false;
        if (fa.drawBuffers.length != fb.drawBuffers.length) return false;
        for (int i = 0; i < fa.drawBuffers.length; i++) {
            if (fa.drawBuffers[i] != fb.drawBuffers[i]) return false;
        }
        for (int i = 0; i < ContextState.MAX_COLOR_ATTACHMENTS; i++) {
            if (fa.colorTextures[i] != fb.colorTextures[i]) return false;
            if (fa.colorFormats[i] != fb.colorFormats[i]) return false;
            if (fa.colorLevels[i] != fb.colorLevels[i]) return false;
        }
        if (fboHasPendingClear(st, fb)) return false;
        return true;
    }

    public void updatePipelineCacheColorFormats(ContextState st, FboState fbo) {
        final boolean reshape = fbo.cachedColorFormats == null || fbo.cachedColorFormats.length != fbo.drawBuffers.length;
        if (fbo.cachedFormatsDirty || reshape) {
            if (reshape) fbo.cachedColorFormats = new int[fbo.drawBuffers.length];
            final int[] formats = fbo.cachedColorFormats;
            for (int i = 0; i < formats.length; i++) {
                final int db = fbo.drawBuffers[i];
                formats[i] = (db >= 0 && db < ContextState.MAX_COLOR_ATTACHMENTS) ? fbo.colorFormats[db] : SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM;
            }
            fbo.cachedFormatsDirty = false;
        }
        st.pipeline.setColorTargetFormats(fbo.cachedColorFormats);
        st.pipeline.setDrawBuffers(fbo.drawBuffers);
        st.pipeline.setFboDebug(st.boundFboId, fbo.colorAttachmentCount);
    }
}
