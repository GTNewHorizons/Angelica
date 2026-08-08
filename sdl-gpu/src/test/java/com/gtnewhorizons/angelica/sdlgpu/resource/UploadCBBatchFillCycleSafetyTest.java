package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;

import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager.MAX_PENDING_UPLOAD_BYTES;
import static com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager.MAX_PENDING_UPLOAD_COMMANDS;
import static org.junit.jupiter.api.Assertions.*;

class UploadCBBatchFillCycleSafetyTest {

    private static FrameManager newFM() {
        return SdlTestRig.frameManager();
    }

    @Test
    void thresholdPredicate_dependsOnlyOnCounters() {
        final FrameManager fm = newFM();
        final FrameManager.FrameState f = fm.frame();
        fm.recordUploadCommands(MAX_PENDING_UPLOAD_BYTES + 1, 1);
        f.copyPass = 0;
        assertTrue(fm.shouldAutoSubmitPendingUpload());
        f.copyPass = 0xCAFEBABEL;
        assertTrue(fm.shouldAutoSubmitPendingUpload());
    }

    @Test
    void thresholdPredicate_commandsThresholdTrips() {
        final FrameManager fm = newFM();
        for (int i = 0; i < MAX_PENDING_UPLOAD_COMMANDS; i++) fm.recordUploadCommands(1, 1);
        assertTrue(fm.shouldAutoSubmitPendingUpload(), "command-count threshold trips at MAX_PENDING_UPLOAD_COMMANDS");
    }

    @Test
    void flushRequestedFlag_isPerThread() throws Exception {
        final FrameManager fm = newFM();
        final FrameManager.FrameState mine = fm.frame();
        assertFalse(mine.flushRequested, "test thread's flag starts false");

        final AtomicReference<Boolean> workerInitial = new AtomicReference<>();
        final AtomicReference<Boolean> workerAfterSet = new AtomicReference<>();
        final AtomicBoolean workerWasIndependentObject = new AtomicBoolean();
        final CountDownLatch done = new CountDownLatch(1);

        final Thread worker = new Thread(() -> {
            try {
                final FrameManager.FrameState theirs = fm.frame();
                workerInitial.set(theirs.flushRequested);
                workerWasIndependentObject.set(theirs != mine);
                theirs.flushRequested = true;
                workerAfterSet.set(theirs.flushRequested);
            } finally {
                done.countDown();
            }
        }, "flush-flag-worker");
        worker.start();
        done.await();

        assertEquals(Boolean.FALSE, workerInitial.get());
        assertTrue(workerWasIndependentObject.get(), "worker has its own FrameManager.FrameState");
        assertEquals(Boolean.TRUE, workerAfterSet.get());
        assertFalse(mine.flushRequested, "test thread's flag stays false");
    }

    @Test
    void requestFlushOnAllRegisteredFrames_flagsAllIncludingCaller() throws Exception {
        final FrameManager fm = newFM();
        final FrameManager.FrameState mine = fm.frame();

        final AtomicReference<FrameManager.FrameState> wA = new AtomicReference<>();
        final AtomicReference<FrameManager.FrameState> wB = new AtomicReference<>();
        final CountDownLatch registered = new CountDownLatch(2);
        final CountDownLatch keepAlive = new CountDownLatch(1);

        final Thread tA = new Thread(() -> {
            wA.set(fm.frame());
            registered.countDown();
            try { keepAlive.await(); } catch (InterruptedException ignored) {}
        }, "worker-A");
        final Thread tB = new Thread(() -> {
            wB.set(fm.frame());
            registered.countDown();
            try { keepAlive.await(); } catch (InterruptedException ignored) {}
        }, "worker-B");
        tA.start();
        tB.start();
        registered.await();

        assertFalse(mine.flushRequested);
        assertFalse(wA.get().flushRequested);
        assertFalse(wB.get().flushRequested);

        fm.requestFlushOnAllRegisteredFrames();

        assertTrue(mine.flushRequested, "caller's flag MUST be raised");
        assertTrue(wA.get().flushRequested);
        assertTrue(wB.get().flushRequested);

        keepAlive.countDown();
        tA.join();
        tB.join();
    }

    @Test
    void releaseThreadState_unregistersFromRegistry() throws Exception {
        final FrameManager fm = newFM();
        fm.frame();

        final AtomicReference<FrameManager.FrameState> beforeRelease = new AtomicReference<>();
        final CountDownLatch released = new CountDownLatch(1);

        final Thread w = new Thread(() -> {
            try {
                beforeRelease.set(fm.frame());
                fm.releaseThreadState();
            } finally {
                released.countDown();
            }
        }, "release-worker");
        w.start();
        released.await();
        w.join();

        final FrameManager.FrameState dead = beforeRelease.get();
        assertNotNull(dead);
        dead.flushRequested = false; // baseline
        fm.requestFlushOnAllRegisteredFrames();
        assertFalse(dead.flushRequested, "released worker's FrameManager.FrameState must not be flagged by triggers (out of registry)");
    }
}
