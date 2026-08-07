package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceManagerConcurrencyTest {

    private interface Body {
        void run(int slot) throws Exception;
    }

    private static void race(int threads, Body body) throws Exception {
        final CountDownLatch start = new CountDownLatch(1);
        final AtomicInteger ready = new AtomicInteger();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final List<Thread> pool = new ArrayList<>(threads);
        for (int t = 0; t < threads; t++) {
            final int slot = t;
            final Thread th = new Thread(() -> {
                ready.incrementAndGet();
                try {
                    start.await();
                    body.run(slot);
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                }
            }, "rm-race-" + t);
            pool.add(th);
            th.start();
        }
        while (ready.get() < threads) Thread.onSpinWait();
        start.countDown();
        for (Thread th : pool) th.join(TimeUnit.SECONDS.toMillis(30));
        for (Thread th : pool) assertFalse(th.isAlive(), "thread " + th.getName() + " still running");
        assertNull(failure.get(), () -> "race or exception: " + failure.get());
    }

    @Test
    void genFboId_isUniqueAcrossThreads() throws Exception {
        final ResourceManager rm = SdlTestRig.resourceManager();
        final int perThread = 1000;
        final int threads = 2;
        final int[][] ids = new int[threads][perThread];

        race(threads, slot -> {
            for (int i = 0; i < perThread; i++) ids[slot][i] = rm.genFboId();
        });

        final Set<Integer> all = new HashSet<>(threads * perThread);
        for (int t = 0; t < threads; t++) for (int id : ids[t]) all.add(id);
        assertEquals(threads * perThread, all.size(), "FBO IDs must be globally unique across threads");
    }

    @Test
    void getOrCreateTexSamplerState_singleInstanceForRacingCallers() throws Exception {
        final ResourceManager rm = SdlTestRig.resourceManager();
        final int n = 8;
        final TextureSamplerState[] results = new TextureSamplerState[n];

        race(n, slot -> results[slot] = rm.getOrCreateTexSamplerState(7));

        for (int i = 1; i < n; i++) {
            assertSame(results[0], results[i], "double-create at slot " + i);
        }
    }

    @Test
    void textureDeleteAndFboCleanupAreAtomic() throws Exception {
        final ResourceManager rm = SdlTestRig.resourceManager();
        final int textureId = 42;
        final int fboId = rm.genFboId();
        final FboState fbo = rm.createFbo(fboId);
        fbo.colorGlIds[0] = textureId;
        fbo.colorTextures[0] = 0xDEADBEEFL;
        fbo.colorFormats[0] = 1;

        final AtomicBoolean done = new AtomicBoolean(false);
        final AtomicBoolean violation = new AtomicBoolean(false);
        final CountDownLatch readerStarted = new CountDownLatch(1);

        final Thread reader = new Thread(() -> {
            readerStarted.countDown();
            int iters = 0;
            while (!done.get() && iters < 5_000_000) {
                final FboState f = rm.getFbo(fboId);
                if (f != null) {
                    final boolean idSet = f.colorGlIds[0] == textureId;
                    final boolean handleSet = f.colorTextures[0] != 0 || f.colorFormats[0] != 0;
                    if (idSet != handleSet) {
                        violation.set(true);
                        return;
                    }
                }
                iters++;
            }
        }, "reader");
        reader.start();
        readerStarted.await();

        Thread.sleep(10);
        rm.deleteTexture(textureId);
        Thread.sleep(20);
        done.set(true);
        reader.join(2000);

        assertFalse(violation.get(), "Reader observed half-cleaned FBO state");

        final FboState f = rm.getFbo(fboId);
        assertEquals(0, f.colorGlIds[0]);
        assertEquals(0L, f.colorTextures[0]);
        assertEquals(0, f.colorFormats[0]);
    }

    @Test
    void concurrentCreateLookupDelete_noRaces() throws Exception {
        final ResourceManager rm = SdlTestRig.resourceManager();
        final int threads = 8;
        final int opsPerThread = 5_000;
        final AtomicInteger created = new AtomicInteger();

        race(threads, slot -> {
            final Random rng = new Random(slot);
            final List<Integer> mine = new ArrayList<>();
            for (int i = 0; i < opsPerThread; i++) {
                switch (rng.nextInt(3)) {
                    case 0 -> {
                        final int id = rm.genFboId();
                        rm.createFbo(id);
                        mine.add(id);
                        created.incrementAndGet();
                    }
                    case 1 -> {
                        if (!mine.isEmpty()) rm.getFbo(mine.get(rng.nextInt(mine.size())));
                    }
                    default -> {
                        if (!mine.isEmpty()) rm.deleteFbo(mine.remove(mine.size() - 1));
                    }
                }
            }
        });

        assertTrue(created.get() >= threads, "each thread should have created at least one FBO");
    }
}
