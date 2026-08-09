package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public final class PersistentBufferSync {
    public interface UploadSink {
        public void enqueue(TransferThread.DeferredUpload upload);
        public long nextSeq();
    }

    private final FrameManager frameManager;
    private final ResourceManager resourceManager;
    private final UploadSink sink;

    private static final class Snapshot {
        final IntArrayList keys = new IntArrayList();
        final ArrayList<PersistentMapping> vals = new ArrayList<>();
        int version = -1;
    }

    private final ThreadLocal<Snapshot> snapshotTL = ThreadLocal.withInitial(Snapshot::new);

    public PersistentBufferSync(FrameManager frameManager, ResourceManager resourceManager, UploadSink sink) {
        this.frameManager = frameManager;
        this.resourceManager = resourceManager;
        this.sink = sink;
    }

    public void onPersistentBufferWrite(int glId, long offset, long size) {
        final PersistentMapping pm = resourceManager.getPersistentMapping(glId);
        if (pm != null && pm.markDirty(offset, size)) {
            resourceManager.trackPersistentDirty();
        }
    }

    public void uploadDirtyPersistentRegion()   { processDirtyPersistentRegions(false); }
    public void enqueueDirtyPersistentRegions() { processDirtyPersistentRegions(true); }

    private void processDirtyPersistentRegions(boolean defer) {
        if (!resourceManager.hasDirtyPersistentRegions()) return;
        if (!defer && frameManager.getCommandBuffer() == 0) return;
        final Snapshot snap = snapshotTL.get();
        final IntArrayList keys = snap.keys;
        final ArrayList<PersistentMapping> vals = snap.vals;
        final int version = resourceManager.getMappingsVersion();
        if (snap.version != version) {
            resourceManager.snapshotPersistentMappingsInto(keys, vals);
            snap.version = version;
        }
        final int n = keys.size();
        for (int i = 0; i < n; i++) {
            final PersistentMapping pm = vals.get(i);
            if (!pm.isDirty()) continue;
            final long gpuHandle = resourceManager.getBufferHandle(keys.getInt(i));
            if (gpuHandle == 0) continue;
            final long claimed = pm.claimDirty();
            if (PersistentMapping.isClean(claimed)) continue;
            final long off = PersistentMapping.rangeOffset(claimed);
            final long size = PersistentMapping.rangeSize(claimed);
            if (Tracy.ENABLED) frameManager.notePersistentDrain();
            if (defer) {
                final long seq = sink.nextSeq();
                pm.lastEnqueuedSeq = seq;
                sink.enqueue(TransferThread.StagingReadUpload.acquire(pm.staging, off, size, gpuHandle, off, seq, false));
            } else {
                pm.staging.position((int) off).limit((int) (off + size));
                resourceManager.uploadToBuffer(frameManager.ensureCopyPass(), pm.staging, gpuHandle, off, false);
                pm.staging.clear();
            }
            resourceManager.clearPersistentDirty();
        }
    }

    public static void mirrorPersistentCopy(ByteBuffer srcStaging, long srcOffset, ByteBuffer dstStaging, long dstOffset, long size) {
        copyByteRegion(srcStaging, (int) srcOffset, dstStaging, (int) dstOffset, (int) size);
    }

    public void mirrorEboShadow(int glId, ByteBuffer src, int dstOffset, int len) {
        ByteBuffer shadow = resourceManager.getEboShadow(glId);
        final int requiredCap = dstOffset + len;
        if (shadow == null || shadow.capacity() < requiredCap) {
            final int growHint;
            if (dstOffset == 0) growHint = len;
            else if (shadow == null) growHint = requiredCap;
            else growHint = shadow.capacity() * 2;
            shadow = resourceManager.getOrAllocEboShadow(glId, Math.max(requiredCap, growHint));
        }
        copyByteRegion(src, src.position(), shadow, dstOffset, len);
        shadow.position(0);
        resourceManager.bumpEboShadowVersion(glId);
        resourceManager.invalidateSplitCacheFor(glId);
    }

    private static void copyByteRegion(ByteBuffer src, int srcOff, ByteBuffer dst, int dstOff, int len) {
        if (src.isDirect() && dst.isDirect()) {
            MemoryUtil.memCopy(MemoryUtil.memAddress(src) + srcOff, MemoryUtil.memAddress(dst) + dstOff, len);
            return;
        }
        final ByteBuffer s = src.duplicate();
        s.position(srcOff).limit(srcOff + len);
        final ByteBuffer d = dst.duplicate();
        d.position(dstOff);
        d.put(s);
    }
}
