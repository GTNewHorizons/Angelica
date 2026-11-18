package com.gtnewhorizons.angelica.sdlgpu.device;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FenceReleaserTest {

    private static final class FakeOps {
        final LongOpenHashSet signaled = new LongOpenHashSet();
        final LongArrayList released = new LongArrayList();

        FenceReleaser releaser() { return new FenceReleaser(signaled::contains, released::add); }
    }

    @Test
    void signaledFenceIsReleasedImmediately() {
        final FakeOps ops = new FakeOps();
        final FenceReleaser r = ops.releaser();
        ops.signaled.add(7L);
        r.release(7L);
        assertEquals(LongArrayList.of(7L), ops.released);
    }

    @Test
    void zeroFenceIsIgnored() {
        final FakeOps ops = new FakeOps();
        final FenceReleaser r = ops.releaser();
        r.release(0L);
        r.releaseAll();
        assertTrue(ops.released.isEmpty());
    }

    @Test
    void unsignaledFencePendsUntilDrainSeesItSignaled() {
        final FakeOps ops = new FakeOps();
        final FenceReleaser r = ops.releaser();
        r.release(1L);
        r.release(2L);
        r.release(3L);
        assertTrue(ops.released.isEmpty());
        r.drain();
        assertTrue(ops.released.isEmpty());
        ops.signaled.add(2L);
        r.drain();
        assertEquals(LongArrayList.of(2L), ops.released);
        r.drain();
        assertEquals(LongArrayList.of(2L), ops.released);
    }

    @Test
    void drainReleasesEverySignaledFenceExactlyOnce() {
        final FakeOps ops = new FakeOps();
        final FenceReleaser r = ops.releaser();
        for (long f = 1; f <= 5; f++) r.release(f);
        ops.signaled.add(1L);
        ops.signaled.add(3L);
        ops.signaled.add(5L);
        r.drain();
        assertEquals(new LongOpenHashSet(new long[]{1L, 3L, 5L}), new LongOpenHashSet(ops.released));
        assertEquals(3, ops.released.size());
        ops.signaled.add(2L);
        ops.signaled.add(4L);
        r.drain();
        r.drain();
        assertEquals(new LongOpenHashSet(new long[]{1L, 2L, 3L, 4L, 5L}), new LongOpenHashSet(ops.released));
        assertEquals(5, ops.released.size());
    }

    @Test
    void releaseAllEmptiesPendingWithoutDoubleRelease() {
        final FakeOps ops = new FakeOps();
        final FenceReleaser r = ops.releaser();
        r.release(1L);
        r.release(2L);
        ops.signaled.add(1L);
        r.drain();
        r.releaseAll();
        assertEquals(LongArrayList.of(1L, 2L), ops.released);
        ops.signaled.add(2L);
        r.drain();
        r.releaseAll();
        assertEquals(LongArrayList.of(1L, 2L), ops.released);
    }
}
