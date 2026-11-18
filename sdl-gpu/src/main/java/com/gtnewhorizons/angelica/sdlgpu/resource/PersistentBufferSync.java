package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import it.unimi.dsi.fastutil.ints.IntArrayList;

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

    public boolean onPersistentBufferWrite(int glId, long offset, long size) {
        final PersistentMapping pm = resourceManager.getPersistentMapping(glId);
        if (pm == null || size == 0) return true;
        if (offset < 0 || size < 0 || offset > pm.length || size > pm.length - offset) return false;
        if (pm.markDirty(offset, size)) {
            resourceManager.trackPersistentDirty();
        }
        return true;
    }

    public void uploadDirtyPersistentRegion()   { processDirtyPersistentRegions(false); }
    public int enqueueDirtyPersistentRegions()  { return processDirtyPersistentRegions(true); }

    private int processDirtyPersistentRegions(boolean defer) {
        if (!resourceManager.hasDirtyPersistentRegions()) return 0;
        if (!defer && frameManager.getCommandBuffer() == 0) return 0;
        final Snapshot snap = snapshotTL.get();
        final IntArrayList keys = snap.keys;
        final ArrayList<PersistentMapping> vals = snap.vals;
        final int version = resourceManager.getMappingsVersion();
        if (snap.version != version) {
            resourceManager.snapshotPersistentMappingsInto(keys, vals);
            snap.version = version;
        }
        final int n = keys.size();
        int enqueued = 0;
        for (int i = 0; i < n; i++) {
            final PersistentMapping pm = vals.get(i);
            if (!pm.isDirty()) continue;
            final int glId = keys.getInt(i);
            long copyPass = 0L;
            if (!defer) {
                if (resourceManager.getBufferHandle(glId) == 0) continue;
                copyPass = frameManager.ensureCopyPass();
            }
            resourceManager.beginPersistentDrain();
            try {
                if (resourceManager.getPersistentMapping(glId) != pm) continue;
                final long gpuHandle = resourceManager.getBufferHandle(glId);
                if (gpuHandle == 0) continue;
                final long claimed = pm.claimDirty();
                if (PersistentMapping.isClean(claimed)) continue;
                resourceManager.clearPersistentDirty();
                final long off = PersistentMapping.rangeOffset(claimed);
                final long size = PersistentMapping.rangeSize(claimed);
                if (Tracy.ENABLED) frameManager.notePersistentDrain();
                if (defer) {
                    final long seq = sink.nextSeq();
                    pm.lastEnqueuedSeq = seq;
                    sink.enqueue(TransferThread.StagingReadUpload.acquire(pm.staging, off, size, gpuHandle, pm.offset + off, seq, false));
                    enqueued++;
                } else {
                    resourceManager.uploadRangeToBuffer(copyPass, pm.staging, off, size, gpuHandle, pm.offset + off, false);
                }
            } finally {
                resourceManager.endPersistentDrain();
            }
        }
        return enqueued;
    }

    public static void mirrorPersistentCopy(PersistentMapping src, long readOffset, PersistentMapping dst, long writeOffset, long size) {
        if (!src.covers(readOffset, size) || !dst.covers(writeOffset, size)) return;
        ByteRegionCopy.copyByteRegion(src.staging, (int) src.stagingIndex(readOffset), dst.staging, (int) dst.stagingIndex(writeOffset), (int) size);
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
        ByteRegionCopy.copyByteRegion(src, src.position(), shadow, dstOffset, len);
        shadow.position(0);
        resourceManager.bumpEboShadowVersion(glId);
        resourceManager.invalidateSplitCacheFor(glId);
    }
}
