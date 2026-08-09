package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;

import com.gtnewhorizons.angelica.sdlgpu.device.Device;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistentBufferSyncDirtyGateTest {

    private static final class CountingSink implements PersistentBufferSync.UploadSink {
        int enqueued;
        long seq;
        @Override public void enqueue(TransferThread.DeferredUpload upload) { enqueued++; }
        @Override public long nextSeq() { return ++seq; }
    }

    @Test
    void gateStaysClosedUntilAWriteMarksARegionDirty() {
        final SdlTestRig rig = SdlTestRig.create();
        final Device device = rig.device;
        final FrameManager fm = rig.frameManager;
        final ResourceManager rm = rig.resourceManager;
        final CountingSink sink = new CountingSink();
        final PersistentBufferSync sync = new PersistentBufferSync(fm, rm, sink);

        final ByteBuffer staging = MemoryUtil.memCalloc(64);
        try {
            rm.putPersistentMapping(1, new PersistentMapping(staging, 0L, staging.capacity(), 0));
            SdlReflect.recordBuffer(rm, 1, 0xCAFEBABEL, 64, 0);

            assertFalse(rm.hasDirtyPersistentRegions(), "a mapping with no writes is not dirty");
            sync.enqueueDirtyPersistentRegions();
            assertEquals(0, sink.enqueued, "clean mappings must not be snapshotted or enqueued");

            sync.onPersistentBufferWrite(1, 0, 16);
            assertTrue(rm.hasDirtyPersistentRegions());
            sync.enqueueDirtyPersistentRegions();
            assertEquals(1, sink.enqueued);

            assertFalse(rm.hasDirtyPersistentRegions(), "the flush clears the gate");
            sync.enqueueDirtyPersistentRegions();
            assertEquals(1, sink.enqueued, "no second enqueue without a further write");
        } finally {
            MemoryUtil.memFree(staging);
        }
    }

    @Test
    void widenAndRemoveKeepTheCountExact() {
        final SdlTestRig rig = SdlTestRig.create();
        final Device device = rig.device;
        final FrameManager fm = rig.frameManager;
        final ResourceManager rm = rig.resourceManager;
        final PersistentBufferSync sync = new PersistentBufferSync(fm, rm, new CountingSink());

        final ByteBuffer staging = MemoryUtil.memCalloc(64);
        try {
            rm.putPersistentMapping(1, new PersistentMapping(staging, 0L, staging.capacity(), 0));
            SdlReflect.recordBuffer(rm, 1, 0xCAFEBABEL, 64, 0);

            sync.onPersistentBufferWrite(1, 0, 16);
            sync.onPersistentBufferWrite(1, 16, 16);
            assertTrue(rm.hasDirtyPersistentRegions());

            rm.removePersistentMapping(1);
            assertFalse(rm.hasDirtyPersistentRegions());
        } finally {
            MemoryUtil.memFree(staging);
        }
    }

    @Test
    void mappingsVersionMovesOnlyWhenTheMapChanges() {
        final SdlTestRig rig = SdlTestRig.create();
        final Device device = rig.device;
        final FrameManager fm = rig.frameManager;
        final ResourceManager rm = rig.resourceManager;

        final ByteBuffer staging = MemoryUtil.memCalloc(64);
        try {
            final int atStart = rm.getMappingsVersion();

            rm.putPersistentMapping(1, new PersistentMapping(staging, 0L, staging.capacity(), 0));
            final int afterPut = rm.getMappingsVersion();
            assertNotEquals(atStart, afterPut, "adding a mapping must move the version");

            SdlReflect.recordBuffer(rm, 1, 0xCAFEBABEL, 64, 0);
            assertEquals(afterPut, rm.getMappingsVersion(), "recording a buffer is not a mapping change");

            final PersistentBufferSync sync = new PersistentBufferSync(fm, rm, new CountingSink());
            sync.onPersistentBufferWrite(1, 0, 16);
            sync.enqueueDirtyPersistentRegions();
            assertEquals(afterPut, rm.getMappingsVersion(), "dirtying and draining is not a mapping change");

            rm.swapPersistentMapping(1, new PersistentMapping(staging, 0L, staging.capacity(), 0));
            final int afterSwap = rm.getMappingsVersion();
            assertNotEquals(afterPut, afterSwap, "swapping a mapping must move the version");

            rm.removePersistentMapping(1);
            assertNotEquals(afterSwap, rm.getMappingsVersion(), "removing a mapping must move the version");
        } finally {
            MemoryUtil.memFree(staging);
        }
    }

    @Test
    void repeatedDrainsKeepUploadingWhenTheMappingSetIsUnchanged() {
        final SdlTestRig rig = SdlTestRig.create();
        final Device device = rig.device;
        final FrameManager fm = rig.frameManager;
        final ResourceManager rm = rig.resourceManager;
        final CountingSink sink = new CountingSink();
        final PersistentBufferSync sync = new PersistentBufferSync(fm, rm, sink);

        final ByteBuffer staging = MemoryUtil.memCalloc(256);
        try {
            rm.putPersistentMapping(1, new PersistentMapping(staging, 0L, staging.capacity(), 0));
            SdlReflect.recordBuffer(rm, 1, 0xCAFEBABEL, 256, 0);

            for (int i = 0; i < 10; i++) {
                sync.onPersistentBufferWrite(1, i * 16, 16);
                sync.enqueueDirtyPersistentRegions();
                assertEquals(i + 1, sink.enqueued,
                    "every dirty region must upload, not just the one before the snapshot was cached");
            }
        } finally {
            rm.removePersistentMapping(1);
            MemoryUtil.memFree(staging);
        }
    }

    @Test
    void aReplacedMappingIsPickedUpByALaterDrain() {
        final SdlTestRig rig = SdlTestRig.create();
        final Device device = rig.device;
        final FrameManager fm = rig.frameManager;
        final ResourceManager rm = rig.resourceManager;
        final CountingSink sink = new CountingSink();
        final PersistentBufferSync sync = new PersistentBufferSync(fm, rm, sink);

        final ByteBuffer first = MemoryUtil.memCalloc(64);
        final ByteBuffer second = MemoryUtil.memCalloc(64);
        try {
            rm.putPersistentMapping(1, new PersistentMapping(first, 0L, first.capacity(), 0));
            SdlReflect.recordBuffer(rm, 1, 0xCAFEBABEL, 64, 0);
            sync.onPersistentBufferWrite(1, 0, 16);
            sync.enqueueDirtyPersistentRegions();
            assertEquals(1, sink.enqueued);

            rm.swapPersistentMapping(1, new PersistentMapping(second, 0L, second.capacity(), 0));
            sync.onPersistentBufferWrite(1, 0, 16);
            sync.enqueueDirtyPersistentRegions();
            assertEquals(2, sink.enqueued, "the drain must see the replacement, not a stale snapshot");
        } finally {
            rm.removePersistentMapping(1);
            MemoryUtil.memFree(first);
            MemoryUtil.memFree(second);
        }
    }

    @Test
    void concurrentWritesAndDrainsLeaveTheGateExact() throws Exception {
        final SdlTestRig rig = SdlTestRig.create();
        final Device device = rig.device;
        final FrameManager fm = rig.frameManager;
        final ResourceManager rm = rig.resourceManager;
        final CountingSink sink = new CountingSink();
        final PersistentBufferSync sync = new PersistentBufferSync(fm, rm, sink);

        final ByteBuffer staging = MemoryUtil.memCalloc(4096);
        try {
            rm.putPersistentMapping(1, new PersistentMapping(staging, 0L, staging.capacity(), 0));
            SdlReflect.recordBuffer(rm, 1, 0xCAFEBABEL, 4096, 0);

            final int writers = 6;
            final int rounds = 4000;
            final AtomicReference<Throwable> error = new AtomicReference<>();
            final CountDownLatch start = new CountDownLatch(1);
            final Thread[] threads = new Thread[writers + 1];
            final AtomicBoolean stop = new AtomicBoolean();

            for (int w = 0; w < writers; w++) {
                final int id = w;
                threads[w] = new Thread(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < rounds; i++) sync.onPersistentBufferWrite(1, (id * 32L + i) % 2048, 16);
                    } catch (Throwable t) { error.set(t); }
                }, "writer-" + id);
            }
            threads[writers] = new Thread(() -> {
                try {
                    start.await();
                    while (!stop.get()) sync.enqueueDirtyPersistentRegions();
                } catch (Throwable t) { error.set(t); }
            }, "drainer");

            for (Thread t : threads) t.start();
            start.countDown();
            for (int w = 0; w < writers; w++) threads[w].join(30_000);
            stop.set(true);
            threads[writers].join(30_000);

            if (error.get() != null) throw new AssertionError("worker crashed", error.get());

            sync.enqueueDirtyPersistentRegions();
            assertFalse(rm.hasDirtyPersistentRegions(), "gate must settle back to zero, not drift");
        } finally {
            rm.removePersistentMapping(1);
            MemoryUtil.memFree(staging);
        }
    }

    @Test
    void deferEnqueueStampsLastEnqueuedSeq() {
        final SdlTestRig rig = SdlTestRig.create();
        final FrameManager fm = rig.frameManager;
        final ResourceManager rm = rig.resourceManager;

        final ByteBuffer staging = MemoryUtil.memCalloc(64);
        try {
            final PersistentMapping pm = new PersistentMapping(staging, 0L, staging.capacity(), 0);
            rm.putPersistentMapping(1, pm);
            SdlReflect.recordBuffer(rm, 1, 0xCAFEBABEL, 64, 0);

            final long[] stampAtEnqueue = {-1L};
            final long[] uploadSeq = {-1L};
            final long[] nextSeq = {42L};
            final PersistentBufferSync sync = new PersistentBufferSync(fm, rm, new PersistentBufferSync.UploadSink() {
                @Override public void enqueue(TransferThread.DeferredUpload upload) {
                    uploadSeq[0] = upload.seq();
                    stampAtEnqueue[0] = pm.lastEnqueuedSeq;
                }
                @Override public long nextSeq() { return ++nextSeq[0]; }
            });

            sync.onPersistentBufferWrite(1, 0, 16);
            sync.enqueueDirtyPersistentRegions();

            assertEquals(43L, uploadSeq[0]);
            assertEquals(43L, stampAtEnqueue[0], "stamp must be visible at the moment of enqueue");
            assertEquals(43L, pm.lastEnqueuedSeq);
        } finally {
            MemoryUtil.memFree(staging);
        }
    }

    @Test
    void snapshotIterationIsStableUnderConcurrentMutation() throws Exception {
        final ResourceManager rm = SdlTestRig.resourceManager();

        final int seedCount = 100;
        for (int i = 0; i < seedCount; i++) {
            final ByteBuffer b = MemoryUtil.memAlloc(64);
            rm.putPersistentMapping(i, new PersistentMapping(b, 0L, b.capacity(), 0));
        }

        final AtomicBoolean stop = new AtomicBoolean(false);
        final AtomicBoolean violation = new AtomicBoolean(false);
        final CountDownLatch mutatorStarted = new CountDownLatch(1);

        final Thread mutator = new Thread(() -> {
            mutatorStarted.countDown();
            int next = seedCount;
            while (!stop.get()) {
                final ByteBuffer b = MemoryUtil.memAlloc(64);
                rm.putPersistentMapping(next, new PersistentMapping(b, 0L, b.capacity(), 0));
                rm.removePersistentMapping(next - 50);
                next++;
                if (next > seedCount + 10000) next = seedCount;
            }
        }, "mutator");
        mutator.start();
        mutatorStarted.await();

        final IntArrayList keys = new IntArrayList();
        final ArrayList<PersistentMapping> vals = new ArrayList<>();
        try {
            for (int round = 0; round < 200; round++) {
                rm.snapshotPersistentMappingsInto(keys, vals);
                final int n = keys.size();
                for (int i = 0; i < n; i++) {
                    if (vals.get(i) == null) violation.set(true);
                }
            }
        } finally {
            stop.set(true);
            mutator.join(2000);
        }

        assertFalse(violation.get(), "snapshot must contain only valid entries");

        for (int i = 0; i < seedCount; i++) {
            final PersistentMapping pm = rm.removePersistentMapping(i);
            if (pm != null) MemoryUtil.memFree(pm.staging);
        }
    }
}
