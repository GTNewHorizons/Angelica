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
        st.clearedTexturesThisFrame.remove(tex);
    }

    public static void recordPendingDepthClear(ContextState st, long tex, float value) {
        st.pendingDepthValues.put(tex, value);
        if (st.pendingDepthTextures.add(tex)) {
            st.pendingMutationGen++;
        }
        st.clearedTexturesThisFrame.remove(tex);
    }

    public static void recordPendingStencilClear(ContextState st, long tex, int value) {
        st.pendingStencilValues.put(tex, value);
        if (st.pendingStencilTextures.add(tex)) {
            st.pendingMutationGen++;
        }
        st.clearedStencilTexturesThisFrame.remove(tex);
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
        frameManager.endRenderPassIfActive();

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
                    final long addr = targets.get(k).address();
                    MemoryAccess.putAddress(addr + SDL_GPUColorTargetInfo.TEXTURE, handle);
                    MemoryAccess.putInt(addr + SDL_GPUColorTargetInfo.LOAD_OP, SDL_GPU_LOADOP_CLEAR);
                    MemoryAccess.putInt(addr + SDL_GPUColorTargetInfo.STORE_OP, SDL_GPU_STOREOP_STORE);
                    final long ccAddr = addr + SDL_GPUColorTargetInfo.CLEAR_COLOR;
                    MemoryAccess.putFloat(ccAddr + SDL_FColor.R, color[0]);
                    MemoryAccess.putFloat(ccAddr + SDL_FColor.G, color[1]);
                    MemoryAccess.putFloat(ccAddr + SDL_FColor.B, color[2]);
                    MemoryAccess.putFloat(ccAddr + SDL_FColor.A, color[3]);
                    st.clearedTexturesThisFrame.add(handle);
                    resourceManager.markTextureContentDefined(handle);
                    consumed[idx] = true;
                }
                frameManager.noteClearPass();
                frameManager.beginRenderPass(targets, null);
                frameManager.endRenderPassIfActive();
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
        try (var stack = MemoryStack.stackPush()) {
            final SDL_GPUDepthStencilTargetInfo dt = SDL_GPUDepthStencilTargetInfo.calloc(stack);
            final long addr = dt.address();
            MemoryAccess.putAddress(addr + SDL_GPUDepthStencilTargetInfo.TEXTURE, handle);
            MemoryAccess.putInt(addr + SDL_GPUDepthStencilTargetInfo.LOAD_OP, clearDepth ? SDL_GPU_LOADOP_CLEAR : SDL_GPU_LOADOP_LOAD);
            MemoryAccess.putInt(addr + SDL_GPUDepthStencilTargetInfo.STORE_OP, SDL_GPU_STOREOP_STORE);
            if (clearDepth) {
                MemoryAccess.putFloat(addr + SDL_GPUDepthStencilTargetInfo.CLEAR_DEPTH, st.pendingDepthValues.get(handle));
            }
            if (clearStencil) {
                MemoryAccess.putInt(addr + SDL_GPUDepthStencilTargetInfo.STENCIL_LOAD_OP, SDL_GPU_LOADOP_CLEAR);
                MemoryAccess.putInt(addr + SDL_GPUDepthStencilTargetInfo.STENCIL_STORE_OP, SDL_GPU_STOREOP_STORE);
                MemoryAccess.putByte(addr + SDL_GPUDepthStencilTargetInfo.CLEAR_STENCIL, (byte) st.pendingStencilValues.get(handle));
            }
            frameManager.noteClearPass();
            if (materialized) frameManager.noteMaterializedClearPass();
            frameManager.beginRenderPass(null, dt);
            frameManager.endRenderPassIfActive();
        }
        if (clearDepth) {
            st.pendingDepthValues.remove(handle);
            st.clearedTexturesThisFrame.add(handle);
        }
        if (clearStencil) {
            st.pendingStencilValues.remove(handle);
            st.clearedStencilTexturesThisFrame.add(handle);
        }
    }

    public boolean discardPendingClearIfFullyCovered(ContextState st, long handle, int dstX, int dstY, int dstLevel, int width, int height, ResourceManager.TextureMeta meta) {
        if (handle == 0 || meta == null) return false;
        if (dstLevel != 0 || dstX != 0 || dstY != 0) return false;
        if (width != meta.width() || height != meta.height() || meta.depth() > 1) return false;
        final boolean depth = st.pendingDepthTextures.remove(handle);
        final boolean color = !depth && st.pendingColorTextures.remove(handle);
        if (!depth && !color) return false;
        if (depth) st.pendingDepthValues.remove(handle);
        else st.pendingColorValues.remove(handle);
        st.clearedTexturesThisFrame.add(handle);
        resourceManager.markTextureContentDefined(handle);
        st.pendingMutationGen++;
        return true;
    }

    public void materializePendingClearForTexture(ContextState st, long handle) {
        if (handle == 0) return;
        final boolean depthPending = st.pendingDepthTextures.remove(handle);
        final boolean stencilPending = st.pendingStencilTextures.remove(handle);
        if (depthPending || stencilPending) {
            frameManager.endRenderPassIfActive();
            emitDepthStencilClearPass(st, handle, depthPending, stencilPending, true);
            st.pendingMutationGen++;
            return;
        }
        if (st.pendingColorTextures.remove(handle)) {
            final float[] color = st.pendingColorValues.remove(handle);
            frameManager.endRenderPassIfActive();
            try (var stack = MemoryStack.stackPush()) {
                final SDL_GPUColorTargetInfo.Buffer targets = SDL_GPUColorTargetInfo.calloc(1, stack);
                final long addr = targets.get(0).address();
                MemoryAccess.putAddress(addr + SDL_GPUColorTargetInfo.TEXTURE, handle);
                MemoryAccess.putInt(addr + SDL_GPUColorTargetInfo.LOAD_OP, SDL_GPU_LOADOP_CLEAR);
                MemoryAccess.putInt(addr + SDL_GPUColorTargetInfo.STORE_OP, SDL_GPU_STOREOP_STORE);
                final long ccAddr = addr + SDL_GPUColorTargetInfo.CLEAR_COLOR;
                MemoryAccess.putFloat(ccAddr + SDL_FColor.R, color != null ? color[0] : 0f);
                MemoryAccess.putFloat(ccAddr + SDL_FColor.G, color != null ? color[1] : 0f);
                MemoryAccess.putFloat(ccAddr + SDL_FColor.B, color != null ? color[2] : 0f);
                MemoryAccess.putFloat(ccAddr + SDL_FColor.A, color != null ? color[3] : 0f);
                frameManager.noteClearPass();
                frameManager.noteMaterializedClearPass();
                frameManager.beginRenderPass(targets, null);
                frameManager.endRenderPassIfActive();
                st.clearedTexturesThisFrame.add(handle);
                resourceManager.markTextureContentDefined(handle);
            }
            st.pendingMutationGen++;
        }
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
        st.clearedTexturesThisFrame.remove(handle);
        st.clearedStencilTexturesThisFrame.remove(handle);
    }

    public boolean fbosHaveSameAttachments(ContextState st, int a, int b) {
        final FboState fa = resourceManager.getFbo(a);
        final FboState fb = resourceManager.getFbo(b);
        if (fa == null || fb == null) return false;
        if (fa.depthTexture != fb.depthTexture) return false;
        if (fa.depthFormat != fb.depthFormat) return false;
        if (fa.colorAttachmentCount != fb.colorAttachmentCount) return false;
        if (fa.drawBuffers.length != fb.drawBuffers.length) return false;
        for (int i = 0; i < fa.drawBuffers.length; i++) {
            if (fa.drawBuffers[i] != fb.drawBuffers[i]) return false;
        }
        for (int i = 0; i < ContextState.MAX_COLOR_ATTACHMENTS; i++) {
            if (fa.colorTextures[i] != fb.colorTextures[i]) return false;
            if (fa.colorFormats[i] != fb.colorFormats[i]) return false;
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
