package com.gtnewhorizons.angelica.sdlgpu.resource;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistentMappingDirtyRangeTest {

    private static PersistentMapping mapping(int capacity) {
        final ByteBuffer staging = MemoryUtil.memCalloc(capacity);
        return new PersistentMapping(staging, 0L, capacity, 0);
    }

    private static void free(PersistentMapping pm) {
        MemoryUtil.memFree(pm.staging);
    }

    @Test
    void firstWriteTransitionsAndReportsItsRange() {
        final PersistentMapping pm = mapping(256);
        try {
            assertFalse(pm.isDirty(), "a fresh mapping is clean");
            assertTrue(pm.markDirty(16, 32), "the clean -> dirty edge is reported once");
            assertTrue(pm.isDirty());

            final long claimed = pm.claimDirty();
            assertFalse(PersistentMapping.isClean(claimed));
            assertEquals(16, PersistentMapping.rangeOffset(claimed));
            assertEquals(32, PersistentMapping.rangeSize(claimed));
            assertFalse(pm.isDirty(), "claiming leaves the mapping clean");
        } finally {
            free(pm);
        }
    }

    @Test
    void widensWithoutRepeatingTheTransition() {
        final PersistentMapping pm = mapping(256);
        try {
            assertTrue(pm.markDirty(64, 16));
            assertFalse(pm.markDirty(16, 16), "already dirty, so no second transition");
            assertFalse(pm.markDirty(128, 32), "still no second transition");

            final long claimed = pm.claimDirty();
            assertEquals(16, PersistentMapping.rangeOffset(claimed), "union takes the lowest offset");
            assertEquals(144, PersistentMapping.rangeSize(claimed), "union runs to the highest end");
        } finally {
            free(pm);
        }
    }

    @Test
    void rewritingTheSameRangeIsNotATransition() {
        final PersistentMapping pm = mapping(256);
        try {
            assertTrue(pm.markDirty(0, 64));
            assertFalse(pm.markDirty(0, 64));
            assertFalse(pm.markDirty(8, 8), "a contained range does not widen the window");
            assertEquals(64, PersistentMapping.rangeSize(pm.claimDirty()));
        } finally {
            free(pm);
        }
    }

    @Test
    void zeroSizedWriteIsNotDirty() {
        final PersistentMapping pm = mapping(256);
        try {
            assertFalse(pm.markDirty(32, 0));
            assertFalse(pm.isDirty());
            assertTrue(PersistentMapping.isClean(pm.claimDirty()));
        } finally {
            free(pm);
        }
    }

    @Test
    void claimIsExclusive() {
        final PersistentMapping pm = mapping(256);
        try {
            pm.markDirty(0, 8);
            assertFalse(PersistentMapping.isClean(pm.claimDirty()), "first claim wins");
            assertTrue(PersistentMapping.isClean(pm.claimDirty()), "second claim gets nothing");
        } finally {
            free(pm);
        }
    }

    @Test
    void concurrentWritersProduceExactlyOneTransitionPerClaim() throws Exception {
        final PersistentMapping pm = mapping(4096);
        final int writers = 8;
        final int rounds = 5000;
        final AtomicInteger transitions = new AtomicInteger();
        final AtomicInteger claims = new AtomicInteger();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final CountDownLatch start = new CountDownLatch(1);
        final Thread[] threads = new Thread[writers + 1];

        try {
            for (int w = 0; w < writers; w++) {
                final int id = w;
                threads[w] = new Thread(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < rounds; i++) {
                            if (pm.markDirty((id * 64L + i) % 2048, 16)) transitions.incrementAndGet();
                        }
                    } catch (Throwable t) { error.set(t); }
                }, "writer-" + id);
            }
            threads[writers] = new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < rounds * writers; i++) {
                        if (!PersistentMapping.isClean(pm.claimDirty())) claims.incrementAndGet();
                    }
                } catch (Throwable t) { error.set(t); }
            }, "claimer");

            for (Thread t : threads) t.start();
            start.countDown();
            for (Thread t : threads) t.join(30_000);

            if (error.get() != null) throw new AssertionError("worker crashed", error.get());

            if (!PersistentMapping.isClean(pm.claimDirty())) claims.incrementAndGet();
            assertEquals(transitions.get(), claims.get(), "every clean -> dirty transition must be claimed exactly once");
        } finally {
            free(pm);
        }
    }

    @Test
    void rangeSurvivesOffsetsNearTheIntCeiling() {
        final PersistentMapping pm = mapping(64);
        try {
            assertTrue(pm.markDirty(0x7FFFFF00L, 0x40L));
            final long claimed = pm.claimDirty();
            assertEquals(0x7FFFFF00L, PersistentMapping.rangeOffset(claimed));
            assertEquals(0x40L, PersistentMapping.rangeSize(claimed));
        } finally {
            free(pm);
        }
    }
}
