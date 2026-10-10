package com.gtnewhorizons.angelica.sdlgpu.threading;

import com.gtnewhorizons.angelica.glsm.testutil.TestThreads;
import com.gtnewhorizons.angelica.sdlgpu.pipeline.PipelineStore;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PipelineCacheConcurrentBuildTest {

    @Test
    void concurrentDistinctKeys_allBuiltOnce() throws Exception {
        final PipelineStore store = new PipelineStore(null);
        final AtomicInteger builds = new AtomicInteger();
        final int threads = 8;
        final int keysPerThread = 500;
        final CountDownLatch start = new CountDownLatch(1);
        final TestThreads.Worker[] all = new TestThreads.Worker[threads];
        for (int t = 0; t < threads; t++) {
            final int tid = t;
            all[t] = TestThreads.start("build-" + t, () -> {
                start.await();
                for (int i = 0; i < keysPerThread; i++) {
                    final long key = ((long) tid << 16) | i;
                    final long p = store.getOrBuild(key, () -> { builds.incrementAndGet(); return key * 1000L + 1; });
                    assertEquals(key * 1000L + 1, p);
                }
            });
        }
        start.countDown();
        for (TestThreads.Worker w : all) w.join();
        assertEquals(threads * keysPerThread, builds.get(), "Each distinct key should be built exactly once");
    }

    @Test
    void concurrentSameKey_builtOnce_allCallersGetSamePipeline() throws Exception {
        final PipelineStore store = new PipelineStore(null);
        final AtomicInteger builds = new AtomicInteger();
        final int threads = 16;
        final long key = 0xDEADBEEFL;
        final long[] results = new long[threads];
        final CountDownLatch start = new CountDownLatch(1);
        final TestThreads.Worker[] all = new TestThreads.Worker[threads];
        for (int t = 0; t < threads; t++) {
            final int tid = t;
            all[t] = TestThreads.start("race-" + t, () -> {
                start.await();
                results[tid] = store.getOrBuild(key, () -> { builds.incrementAndGet(); return key * 1000L + 1; });
            });
        }
        start.countDown();
        for (TestThreads.Worker w : all) w.join();
        assertEquals(1, builds.get(), "Same key must build exactly once across threads");
        for (int i = 0; i < threads; i++) {
            assertEquals(results[0], results[i], "All threads must see the same pipeline handle");
        }
    }

    @Test
    void badSentinelKey_cachedAsZero_notRebuilt() {
        final PipelineStore store = new PipelineStore(null);
        final AtomicInteger builds = new AtomicInteger();
        final long key = 7L;
        final long first = store.getOrBuild(key, () -> { builds.incrementAndGet(); return -1L; });
        final long second = store.getOrBuild(key, () -> { builds.incrementAndGet(); return 999L; });
        assertEquals(0L, first, "known-bad build reports 0");
        assertEquals(0L, second, "known-bad key returns 0 without rebuilding");
        assertEquals(1, builds.get(), "bad key builds once, then stays cached");
    }
}
