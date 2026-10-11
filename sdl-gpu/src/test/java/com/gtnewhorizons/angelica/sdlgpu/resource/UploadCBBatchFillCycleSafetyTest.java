package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.glsm.testutil.TestThreads;
import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;

import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager.MAX_PENDING_UPLOAD_BYTES;
import static org.junit.jupiter.api.Assertions.*;

class UploadCBBatchFillCycleSafetyTest {

    @Test
    void thresholdPredicate_dependsOnlyOnCounters() {
        final FrameManager fm = SdlTestRig.frameManager();
        final FrameManager.FrameState f = fm.frame();
        fm.recordUploadCommands(MAX_PENDING_UPLOAD_BYTES + 1, 1);
        f.copyPass = 0;
        assertTrue(fm.shouldAutoSubmitPendingUpload());
        f.copyPass = 0xCAFEBABEL;
        assertTrue(fm.shouldAutoSubmitPendingUpload());
    }

    @Test
    void requestFlushOnAllRegisteredFrames_flagsAllIncludingCaller() throws Exception {
        final FrameManager fm = SdlTestRig.frameManager();
        final FrameManager.FrameState mine = fm.frame();

        final AtomicReference<FrameManager.FrameState> wA = new AtomicReference<>();
        final AtomicReference<FrameManager.FrameState> wB = new AtomicReference<>();
        final CountDownLatch registered = new CountDownLatch(2);
        final CountDownLatch keepAlive = new CountDownLatch(1);

        final TestThreads.Worker tA = TestThreads.start("worker-A", () -> {
            wA.set(fm.frame());
            registered.countDown();
            keepAlive.await();
        });
        final TestThreads.Worker tB = TestThreads.start("worker-B", () -> {
            wB.set(fm.frame());
            registered.countDown();
            keepAlive.await();
        });
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
        final FrameManager fm = SdlTestRig.frameManager();
        fm.frame();

        final AtomicReference<FrameManager.FrameState> beforeRelease = new AtomicReference<>();
        TestThreads.run("release-worker", () -> {
            beforeRelease.set(fm.frame());
            fm.releaseThreadState();
        });

        final FrameManager.FrameState dead = beforeRelease.get();
        assertNotNull(dead);
        dead.flushRequested = false; // baseline
        fm.requestFlushOnAllRegisteredFrames();
        assertFalse(dead.flushRequested, "released worker's FrameManager.FrameState must not be flagged by triggers (out of registry)");
    }
}
