package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
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

    @Test
    void putPlainMappingIfAbsent_oneWinnerAcrossThreads() throws Exception {
        final ResourceManager rm = SdlTestRig.resourceManager();
        final int glId = 9001;
        final int n = 8;
        final AtomicInteger winners = new AtomicInteger();

        race(n, slot -> {
            final ByteBuffer staging = MemoryUtil.memAlloc(16);
            if (rm.putPlainMappingIfAbsent(glId, staging, 0L, 16L, false, slot)) winners.incrementAndGet();
            else MemoryUtil.memFree(staging);
        });

        assertEquals(1, winners.get(), "exactly one thread may map the buffer");
        final ByteBuffer staging = rm.takePlainStaging(glId);
        assertTrue(staging != null, "the winning mapping must still be registered");
        MemoryUtil.memFree(staging);
        assertNull(rm.takePlainStaging(glId));
    }

    @Test
    void plainMappingTakenFromAnotherThread() throws Exception {
        final ResourceManager rm = SdlTestRig.resourceManager();
        final int glId = 9002;
        final ByteBuffer staging = MemoryUtil.memAlloc(32);
        try {
            assertTrue(rm.putPlainMappingIfAbsent(glId, staging, 8L, 32L, true, 0x2A));
            final MappedRange peeked = new MappedRange();
            final MappedRange taken = new MappedRange();
            final AtomicBoolean peekedOk = new AtomicBoolean();
            final AtomicBoolean takenOk = new AtomicBoolean();
            final AtomicBoolean secondTake = new AtomicBoolean(true);

            race(1, slot -> {
                peekedOk.set(rm.peekPlainMapping(glId, peeked));
                takenOk.set(rm.takePlainMapping(glId, taken));
                secondTake.set(rm.takePlainMapping(glId, new MappedRange()));
            });

            assertTrue(peekedOk.get(), "peek from another thread must see the mapping");
            assertSame(staging, peeked.staging);
            assertTrue(takenOk.get(), "take from another thread must succeed");
            assertSame(staging, taken.staging);
            assertEquals(glId, taken.glId);
            assertEquals(8L, taken.offset);
            assertEquals(32L, taken.length);
            assertTrue(taken.invalidate);
            assertEquals(0x2A, taken.accessFlags);
            assertFalse(secondTake.get(), "a taken mapping must be gone");
            assertFalse(rm.peekPlainMapping(glId, new MappedRange()));
        } finally {
            MemoryUtil.memFree(staging);
        }
    }

    @Test
    void deleteBufferFromAnotherThreadDropsPlainMapping() throws Exception {
        final ResourceManager rm = SdlTestRig.resourceManager();
        final int glId = 9003;
        assertTrue(rm.putPlainMappingIfAbsent(glId, MemoryUtil.memAlloc(16), 0L, 16L, false, 0));

        race(1, slot -> rm.deleteBuffer(glId));

        assertFalse(rm.peekPlainMapping(glId, new MappedRange()));
        assertFalse(rm.takePlainMapping(glId, new MappedRange()));
        final ByteBuffer again = MemoryUtil.memAlloc(16);
        assertTrue(rm.putPlainMappingIfAbsent(glId, again, 0L, 16L, false, 0), "a deleted buffer's id must be mappable again");
        MemoryUtil.memFree(rm.takePlainStaging(glId));
    }

    @Test
    void persistentAndPlainMappingsExcludeEachOther() {
        final ResourceManager rm = SdlTestRig.resourceManager();
        final int plainFirst = 9004;
        final int persistentFirst = 9005;
        final ByteBuffer plainStaging = MemoryUtil.memAlloc(16);
        final ByteBuffer persistentStaging = MemoryUtil.memAlloc(16);
        final ByteBuffer refused = MemoryUtil.memAlloc(16);
        try {
            assertTrue(rm.putPlainMappingIfAbsent(plainFirst, plainStaging, 0L, 16L, false, 0));
            assertFalse(rm.putPersistentMappingIfAbsent(plainFirst, new PersistentMapping(refused, 0L, 16L, 0)), "persistent map of a plain-mapped buffer must be refused");
            assertNull(rm.getPersistentMapping(plainFirst));

            assertTrue(rm.putPersistentMappingIfAbsent(persistentFirst, new PersistentMapping(persistentStaging, 0L, 16L, 0)));
            assertFalse(rm.putPlainMappingIfAbsent(persistentFirst, refused, 0L, 16L, false, 0), "plain map of a persistently mapped buffer must be refused");
            assertFalse(rm.peekPlainMapping(persistentFirst, new MappedRange()));
        } finally {
            rm.takePlainStaging(plainFirst);
            rm.removePersistentMapping(persistentFirst);
            MemoryUtil.memFree(plainStaging);
            MemoryUtil.memFree(persistentStaging);
            MemoryUtil.memFree(refused);
        }
    }
}
