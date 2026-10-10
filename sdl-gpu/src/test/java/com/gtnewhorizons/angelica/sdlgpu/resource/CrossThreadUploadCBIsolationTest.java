package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.glsm.testutil.TestThreads;
import java.util.concurrent.atomic.AtomicReference;
import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;

import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import org.junit.jupiter.api.Test;

import static com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager.MAX_PENDING_UPLOAD_BYTES;
import static org.junit.jupiter.api.Assertions.*;

class CrossThreadUploadCBIsolationTest {

    @Test
    void differentThreadsHaveDistinctFrameStates() throws Exception {
        final FrameManager fm = SdlTestRig.frameManager();
        final FrameManager.FrameState testThreadFs = fm.frame();

        final AtomicReference<FrameManager.FrameState> workerFs = new AtomicReference<>();
        TestThreads.run("worker-thread", () -> workerFs.set(fm.frame()));

        assertNotNull(workerFs.get());
        assertNotSame(testThreadFs, workerFs.get(), "each thread must get its own FrameManager.FrameState instance from the FrameManager ThreadLocal");
    }

    @Test
    void clientThreadsRecordingsDoNotAppearInSplashThreadsState() throws Exception {
        final FrameManager fm = SdlTestRig.frameManager();

        TestThreads.run("client-thread-stand-in", () -> {
            fm.recordUploadCommands(MAX_PENDING_UPLOAD_BYTES + 64, 200);
            assertTrue(fm.frame().pendingUploadBytes > MAX_PENDING_UPLOAD_BYTES);
            assertTrue(fm.shouldAutoSubmitPendingUpload());
        });

        TestThreads.run("splash-thread-stand-in", () -> {
            assertEquals(0L, fm.frame().pendingUploadBytes, "splash thread's pendingUploadBytes must be 0 even though client recorded over threshold");
            assertEquals(0, fm.frame().pendingUploadCommands);
            assertFalse(fm.shouldAutoSubmitPendingUpload(), "splash thread's shouldAutoSubmitPendingUpload must be false");
        });
    }

    @Test
    void perThreadFrameStateBatchFieldsAreIndependent() throws Exception {
        final FrameManager fm = SdlTestRig.frameManager();

        TestThreads.run("worker", () -> {
            final FrameManager.FrameState f = fm.frame();
            f.pendingTexHandles.add(1L);
            f.pendingTexX.add(0);
            f.pendingTexY.add(0);
            f.pendingTexW.add(16);
            f.pendingTexH.add(16);
            f.pendingTexLevels.add(0);
            f.pendingTexOffsets.add(0);
            f.batchOffset = 1024;
            assertEquals(1, f.pendingTexHandles.size(), "worker added one entry to its own list");
        });

        final FrameManager.FrameState mine = fm.frame();
        assertTrue(mine.pendingTexHandles.isEmpty(), "test thread's batched-upload list is independent of worker's");
        assertEquals(0, mine.batchOffset);
    }
}
