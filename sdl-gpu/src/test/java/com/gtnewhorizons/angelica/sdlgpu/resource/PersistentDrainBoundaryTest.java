package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PersistentDrainBoundaryTest {

    private static final class RecordingSink implements PersistentBufferSync.UploadSink {
        final LongArrayList offsets = new LongArrayList();
        final LongArrayList sizes = new LongArrayList();
        long seq;

        @Override public void enqueue(TransferThread.DeferredUpload upload) {
            final TransferThread.StagingReadUpload u = (TransferThread.StagingReadUpload) upload;
            offsets.add(u.srcOffset);
            sizes.add(u.size);
        }

        @Override public long nextSeq() { return ++seq; }

        long totalBytes() {
            long sum = 0;
            for (int i = 0; i < sizes.size(); i++) sum += sizes.getLong(i);
            return sum;
        }
    }

    private static final class Fixture implements AutoCloseable {
        final ResourceManager rm;
        final PersistentBufferSync sync;
        final RecordingSink sink = new RecordingSink();
        final ByteBuffer staging = MemoryUtil.memCalloc(4096);

        Fixture() {
            final SdlTestRig rig = SdlTestRig.create();
            rm = rig.resourceManager;
            sync = new PersistentBufferSync(rig.frameManager, rm, sink);
            rm.putPersistentMapping(1, new PersistentMapping(staging, 0L, staging.capacity(), 0));
            SdlReflect.recordBuffer(rm, 1, 0xCAFEBABEL, staging.capacity(), 0);
        }

        @Override public void close() {
            rm.removePersistentMapping(1);
            MemoryUtil.memFree(staging);
        }
    }

    @Test
    void oneDrainCoversEveryRangeDirtiedSinceTheLastDrain() {
        try (Fixture f = new Fixture()) {
            for (int i = 0; i < 64; i++) f.sync.onPersistentBufferWrite(1, i * 188L, 188L);
            f.sync.enqueueDirtyPersistentRegions();

            assertEquals(1, f.sink.offsets.size(), "contiguous writes drain as a single upload");
            assertEquals(0L, f.sink.offsets.getLong(0));
            assertEquals(64L * 188L, f.sink.sizes.getLong(0), "the union spans every byte written since the last drain");
            assertFalse(f.rm.hasDirtyPersistentRegions());
        }
    }

    @Test
    void theAccumulatedUnionMatchesThePerDrawSum() {
        final long batched;
        try (Fixture f = new Fixture()) {
            for (int i = 0; i < 64; i++) f.sync.onPersistentBufferWrite(1, i * 188L, 188L);
            f.sync.enqueueDirtyPersistentRegions();
            batched = f.sink.totalBytes();
        }
        try (Fixture f = new Fixture()) {
            for (int i = 0; i < 64; i++) {
                f.sync.onPersistentBufferWrite(1, i * 188L, 188L);
                f.sync.enqueueDirtyPersistentRegions();
            }
            assertEquals(64, f.sink.offsets.size(), "the per-draw drain enqueues once per write");
            assertEquals(f.sink.totalBytes(), batched, "batching changes the enqueue count, not the bytes");
        }
    }

    @Test
    void aLaterDrainPicksUpWritesMadeSinceTheBoundary() {
        try (Fixture f = new Fixture()) {
            f.sync.onPersistentBufferWrite(1, 0L, 16L);
            f.sync.enqueueDirtyPersistentRegions();
            assertEquals(1, f.sink.offsets.size());

            f.sync.onPersistentBufferWrite(1, 512L, 16L);
            f.sync.onPersistentBufferWrite(1, 1024L, 16L);
            f.sync.enqueueDirtyPersistentRegions();

            assertEquals(2, f.sink.offsets.size());
            assertEquals(512L, f.sink.offsets.getLong(1), "the union starts at the first byte dirtied after the boundary");
            assertEquals(528L, f.sink.sizes.getLong(1), "and spans through the last one");
            assertFalse(f.rm.hasDirtyPersistentRegions(), "no dirty bytes survive a drain");
        }
    }
}
