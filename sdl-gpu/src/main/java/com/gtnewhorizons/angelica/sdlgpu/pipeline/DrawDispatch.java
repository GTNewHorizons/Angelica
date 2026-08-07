package com.gtnewhorizons.angelica.sdlgpu.pipeline;

import com.gtnewhorizons.angelica.sdlgpu.device.Device;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager.FrameState;
import com.gtnewhorizons.angelica.sdlgpu.resource.FormatMap;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import com.gtnewhorizons.angelica.sdlgpu.util.MemoryAccess;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.sdl.SDL_GPUBufferBinding;
import org.lwjgl.sdl.SDL_GPUBufferCreateInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.sdl.SDLGPU.*;

public final class DrawDispatch {
    private static final Logger LOG = LogManager.getLogger("Angelica-SDLGPU");
    private static final int FAN_INDEX_BUFFER_INITIAL = 64 * 1024;

    public interface FanUploadSink {
        public void enqueuePreCopied(ByteBuffer data, long dstHandle, long dstOffset, boolean cycle);
    }

    private final Device device;
    private final FrameManager frameManager;
    private final ResourceManager resourceManager;
    private final PipelineApplier pipelineApplier;
    private final FanUploadSink fanUploadSink;

    private final IntOpenHashSet restartShadowMissingWarned = new IntOpenHashSet();
    public boolean restartMultiDrawWarned;
    private boolean unsignedByteIndicesWarned;
    private boolean prepareFrameInactiveWarned;
    private boolean prepareRpInactiveWarned;
    private boolean drawArraysRpInactiveWarned;

    public DrawDispatch(Device device, FrameManager frameManager, ResourceManager resourceManager, PipelineApplier pipelineApplier, FanUploadSink fanUploadSink) {
        this.device = device;
        this.frameManager = frameManager;
        this.resourceManager = resourceManager;
        this.pipelineApplier = pipelineApplier;
        this.fanUploadSink = fanUploadSink;
    }

    public void setPrimitiveTypeForDraw(ContextState st, int newType) {
        if (st.pipeline.primitiveType != newType) {
            st.pipeline.primitiveType = newType;
            st.pipeline.markOutputDirty();
        }
    }

    public void bindIndexBufferIfChanged(ContextState st, long renderPass, long eboHandle, int indexSize, int offset) {
        if (st.lastBoundEboHandle == eboHandle && st.lastBoundEboIndexSize == indexSize && st.lastBoundEboOffset == offset) return;
        final long addr = st.eboBinding.address();
        MemoryAccess.putAddress(addr + SDL_GPUBufferBinding.BUFFER, eboHandle);
        MemoryAccess.putInt(addr + SDL_GPUBufferBinding.OFFSET, offset);
        SDL_BindGPUIndexBuffer(renderPass, st.eboBinding, indexSize);
        st.lastBoundEboHandle = eboHandle;
        st.lastBoundEboIndexSize = indexSize;
        st.lastBoundEboOffset = offset;
    }

    public void warnDrawArraysNoRenderPass(int mode, int boundFbo) {
        if (drawArraysRpInactiveWarned) return;
        drawArraysRpInactiveWarned = true;
        LOG.warn("drawArrays: render pass not active after ensureRenderPass; draw skipped (mode=0x{} boundFbo={}). Further drops counted in sdl.droppedDraws.", Integer.toHexString(mode), boundFbo);
    }

    public boolean prepareIndexedDraw(ContextState st, int mode, int type) {
        final FrameState f = frameManager.frame();
        if (type == GL11.GL_UNSIGNED_BYTE) {
            f.droppedDrawsThisFrame++;
            if (!unsignedByteIndicesWarned) {
                LOG.error("SDL backend: drawElements with GL_UNSIGNED_BYTE indices is unsupported; skipping draw. Use SHORT or INT indices.");
                unsignedByteIndicesWarned = true;
            }
            return false;
        }
        if (!frameManager.isFrameActive(f)) {
            f.droppedDrawsThisFrame++;
            if (!prepareFrameInactiveWarned) {
                prepareFrameInactiveWarned = true;
                LOG.warn("prepareIndexedDraw: frame not active; draw skipped (mode=0x{} type=0x{})", Integer.toHexString(mode), Integer.toHexString(type));
            }
            return false;
        }
        setPrimitiveTypeForDraw(st, FormatMap.mapPrimitiveType(mode));
        pipelineApplier.ensureRenderPass(st, f);
        if (!frameManager.isRenderPassActive(f)) {
            f.droppedDrawsThisFrame++;
            if (!prepareRpInactiveWarned) {
                prepareRpInactiveWarned = true;
                LOG.warn("prepareIndexedDraw: render pass not active after ensureRenderPass; draw skipped (mode=0x{} type=0x{} boundFbo={})", Integer.toHexString(mode), Integer.toHexString(type), st.boundFboId);
            }
            return false;
        }
        if (!pipelineApplier.applyPipelineAndState(st, f)) {
            f.droppedDrawsThisFrame++;
            return false;
        }
        return true;
    }

    public void issueIndexedDraw(ContextState st, long rp, int ebo, int indexType, int count, int instances, int firstIndex, int baseVertex) {
        final EBOSplitScanner.EboSplit[] full;
        if (st.primitiveRestartEnabled) {
            full = resourceManager.getOrScanSplits(ebo, indexType, st.primitiveRestartSentinel);
            if (full == null && restartShadowMissingWarned.add(ebo)) {
                LOG.warn("primitive restart enabled but EBO {} was uploaded earlier; cannot CPU-scan. Enable GL_PRIMITIVE_RESTART before uploading the EBO to fix.", ebo);
            }
        } else {
            full = null;
        }
        if (full == null) {
            SDL_DrawGPUIndexedPrimitives(rp, count, instances, firstIndex, baseVertex, 0);
            return;
        }
        st.ensureEboSplitScratch(full.length);
        final int n = EBOSplitScanner.sliceSplits(full, firstIndex, count, st.eboSplitFirsts, st.eboSplitCounts);
        for (int i = 0; i < n; i++) {
            final int sCount = st.eboSplitCounts[i];
            if (sCount <= 0) continue;
            SDL_DrawGPUIndexedPrimitives(rp, sCount, instances, firstIndex + st.eboSplitFirsts[i], baseVertex, 0);
        }
    }

    public void drawTriangleFanAsTriangleList(ContextState st, int first, int count) {
        final int numTriangles = count - 2;
        final boolean use32bit = (first + count - 1) > 65535;
        final int bytesPerIndex = use32bit ? 4 : 2;
        final int indexDataSize = numTriangles * 3 * bytesPerIndex;

        if (st.fanIndexBuffer == 0) {
            st.fanIndexBufferCapacity = Math.max(indexDataSize, FAN_INDEX_BUFFER_INITIAL);
            st.fanIndexBuffer = createFanIndexBuffer(st.fanIndexBufferCapacity);
            st.fanIndexBufferOffset = 0;
        } else if (st.fanIndexBufferOffset + indexDataSize > st.fanIndexBufferCapacity) {
            frameManager.endRenderPassIfActive();
            resourceManager.releaseBufferDeferred(st.fanIndexBuffer);
            st.fanIndexBufferCapacity = Math.max(indexDataSize, st.fanIndexBufferCapacity * 2);
            st.fanIndexBuffer = createFanIndexBuffer(st.fanIndexBufferCapacity);
            st.fanIndexBufferOffset = 0;
        }

        final int uploadOffset = st.fanIndexBufferOffset;
        try (var stack = MemoryStack.stackPush()) {
            final ByteBuffer indexBytes = stack.malloc(indexDataSize);
            final long base = MemoryUtil.memAddress(indexBytes);
            if (use32bit) {
                for (int i = 0; i < numTriangles; i++) {
                    final long off = base + (long) i * 12L;
                    MemoryAccess.putInt(off,     first);
                    MemoryAccess.putInt(off + 4, first + i + 1);
                    MemoryAccess.putInt(off + 8, first + i + 2);
                }
            } else {
                for (int i = 0; i < numTriangles; i++) {
                    final long off = base + (long) i * 6L;
                    MemoryAccess.putShort(off,     (short) first);
                    MemoryAccess.putShort(off + 2, (short) (first + i + 1));
                    MemoryAccess.putShort(off + 4, (short) (first + i + 2));
                }
            }
            indexBytes.position(0).limit(indexDataSize);

            if (st.deferUploads) {
                fanUploadSink.enqueuePreCopied(indexBytes, st.fanIndexBuffer, uploadOffset, false);
            } else if (frameManager.getCommandBuffer() != 0) {
                resourceManager.uploadToBuffer(frameManager.ensureCopyPass(), indexBytes, st.fanIndexBuffer, uploadOffset, false);
            }
        }
        st.fanIndexBufferOffset += indexDataSize;

        setPrimitiveTypeForDraw(st, SDL_GPU_PRIMITIVETYPE_TRIANGLELIST);
        pipelineApplier.ensureRenderPass(st);
        if (!frameManager.isRenderPassActive()) return;
        if (!pipelineApplier.applyPipelineAndState(st)) return;

        final int elementSize = use32bit ? SDL_GPU_INDEXELEMENTSIZE_32BIT : SDL_GPU_INDEXELEMENTSIZE_16BIT;
        final long rp = frameManager.getRenderPass();
        bindIndexBufferIfChanged(st, rp, st.fanIndexBuffer, elementSize, uploadOffset);
        SDL_DrawGPUIndexedPrimitives(rp, numTriangles * 3, 1, 0, 0, 0);
    }

    private long createFanIndexBuffer(int capacity) {
        try (var s = MemoryStack.stackPush()) {
            final long handle = SDL_CreateGPUBuffer(device.getDevice(), SDL_GPUBufferCreateInfo.calloc(s).usage(SDL_GPU_BUFFERUSAGE_INDEX).size(capacity));
            resourceManager.trackBufferHandle(handle);
            return handle;
        }
    }

    public void clearWarnedState() {
        restartShadowMissingWarned.clear();
        restartMultiDrawWarned = false;
        unsignedByteIndicesWarned = false;
    }
}
