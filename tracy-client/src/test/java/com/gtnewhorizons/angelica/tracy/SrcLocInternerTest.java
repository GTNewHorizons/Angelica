package com.gtnewhorizons.angelica.tracy;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SrcLocInternerTest {

    static final class RecordingOps implements SrcLocInterner.NativeOps {
        final ConcurrentHashMap<String, Integer> allocCounts = new ConcurrentHashMap<>();
        final AtomicLong nextAddress = new AtomicLong(0x1000);

        @Override
        public long allocSrcLoc(String name, int color) {
            allocCounts.merge(name, 1, Integer::sum);
            return nextAddress.getAndAdd(32);
        }
    }

    @Test
    void internDedupes() {
        final RecordingOps ops = new RecordingOps();
        final SrcLocInterner interner = new SrcLocInterner(ops, 16);
        final long a = interner.intern("root", 0);
        final long b = interner.intern("root", 0);
        assertEquals(a, b);
        assertEquals(1, ops.allocCounts.get("root"));
        assertNotEquals(a, interner.dynamicSrcLoc());
    }

    @Test
    void capFallsBackToDynamicSrcLoc() {
        final RecordingOps ops = new RecordingOps();
        final SrcLocInterner interner = new SrcLocInterner(ops, 4);
        for (int i = 0; i < 4; i++) {
            assertNotEquals(interner.dynamicSrcLoc(), interner.intern("zone" + i, 0));
        }
        assertEquals(interner.dynamicSrcLoc(), interner.intern("overflow", 0));
        assertEquals(4, interner.size());
        assertEquals(interner.intern("zone2", 0), interner.intern("zone2", 0));
    }

    @Test
    void concurrentInternAllocatesOncePerName() throws Exception {
        final RecordingOps ops = new RecordingOps();
        final SrcLocInterner interner = new SrcLocInterner(ops, 1024);
        final int threads = 8;
        final CountDownLatch start = new CountDownLatch(1);
        final List<Thread> workers = new ArrayList<>();
        final long[][] results = new long[threads][64];
        for (int t = 0; t < threads; t++) {
            final int ti = t;
            final Thread worker = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                for (int i = 0; i < 64; i++) {
                    results[ti][i] = interner.intern("zone" + i, 0);
                }
            });
            worker.start();
            workers.add(worker);
        }
        start.countDown();
        for (Thread worker : workers) worker.join(10_000);

        for (int i = 0; i < 64; i++) {
            for (int t = 1; t < threads; t++) {
                assertEquals(results[0][i], results[t][i], "zone" + i);
            }
            assertEquals(1, ops.allocCounts.get("zone" + i), "zone" + i);
        }
        assertTrue(interner.size() <= 64);
    }
}
