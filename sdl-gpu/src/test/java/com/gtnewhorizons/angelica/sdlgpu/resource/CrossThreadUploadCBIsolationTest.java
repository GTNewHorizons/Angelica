package com.gtnewhorizons.angelica.sdlgpu.resource;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;


import com.gtnewhorizons.angelica.sdlgpu.device.Device;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

import static com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager.MAX_PENDING_UPLOAD_BYTES;
import static org.junit.jupiter.api.Assertions.*;

class CrossThreadUploadCBIsolationTest {

    private static FrameManager newFM() {
        return SdlTestRig.frameManager();
    }

    @Test
    void differentThreadsHaveDistinctFrameStates() throws Exception {
        final FrameManager fm = newFM();
        final FrameManager.FrameState testThreadFs = fm.frame();
        final AtomicReference<FrameManager.FrameState> workerFs =
            new AtomicReference<>();
        final Thread t = new Thread(() -> workerFs.set(fm.frame()), "worker-thread");
        t.start();
        t.join();
        assertNotNull(workerFs.get());
        assertNotSame(testThreadFs, workerFs.get(), "each thread must get its own FrameManager.FrameState instance from the FrameManager ThreadLocal");
    }

    @Test
    void clientThreadsRecordingsDoNotAppearInSplashThreadsState() throws Exception {
        final FrameManager fm = newFM();

        final CountDownLatch clientRecorded = new CountDownLatch(1);
        final AtomicLong clientBytes = new AtomicLong();
        final AtomicBoolean clientShouldFlush =
            new AtomicBoolean();

        final Thread client = new Thread(() -> {
            try {
                fm.recordUploadCommands(MAX_PENDING_UPLOAD_BYTES + 64, 200);
                clientBytes.set(fm.frame().pendingUploadBytes);
                clientShouldFlush.set(fm.shouldAutoSubmitPendingUpload());
            } finally {
                clientRecorded.countDown();
            }
        }, "client-thread-stand-in");
        client.start();
        clientRecorded.await();

        final AtomicLong splashBytes = new AtomicLong();
        final AtomicInteger splashCommands =
            new AtomicInteger();
        final AtomicBoolean splashShouldFlush =
            new AtomicBoolean();
        final Thread splash = new Thread(() -> {
            splashBytes.set(fm.frame().pendingUploadBytes);
            splashCommands.set(fm.frame().pendingUploadCommands);
            splashShouldFlush.set(fm.shouldAutoSubmitPendingUpload());
        }, "splash-thread-stand-in");
        splash.start();
        splash.join();

        assertTrue(clientBytes.get() > MAX_PENDING_UPLOAD_BYTES);
        assertTrue(clientShouldFlush.get());

        assertEquals(0L, splashBytes.get(), "splash thread's pendingUploadBytes must be 0 even though client recorded over threshold");
        assertEquals(0, splashCommands.get());
        assertFalse(splashShouldFlush.get(), "splash thread's shouldAutoSubmitPendingUpload must be false");
    }

    @Test
    void perThreadFrameStateBatchFieldsAreIndependent() throws Exception {
        final FrameManager fm = newFM();

        final CountDownLatch workerSeeded = new CountDownLatch(1);
        final AtomicInteger workerListSize =
            new AtomicInteger();
        final Thread worker = new Thread(() -> {
            try {
                final FrameManager.FrameState f = fm.frame();
                f.pendingTexHandles.add(1L);
                f.pendingTexX.add(0);
                f.pendingTexY.add(0);
                f.pendingTexW.add(16);
                f.pendingTexH.add(16);
                f.pendingTexLevels.add(0);
                f.pendingTexOffsets.add(0);
                f.batchOffset = 1024;
                workerListSize.set(f.pendingTexHandles.size());
            } finally {
                workerSeeded.countDown();
            }
        }, "worker");
        worker.start();
        workerSeeded.await();

        final FrameManager.FrameState mine = fm.frame();
        assertEquals(1, workerListSize.get(), "worker added one entry to its own list");
        assertTrue(mine.pendingTexHandles.isEmpty(), "test thread's batched-upload list is independent of worker's");
        assertEquals(0, mine.batchOffset);
    }
}
