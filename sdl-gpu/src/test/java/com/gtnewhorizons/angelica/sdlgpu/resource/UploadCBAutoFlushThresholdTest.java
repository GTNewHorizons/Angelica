package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;


import com.gtnewhorizons.angelica.sdlgpu.device.Device;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager.MAX_PENDING_UPLOAD_BYTES;
import static com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager.MAX_PENDING_UPLOAD_COMMANDS;
import static org.junit.jupiter.api.Assertions.*;

class UploadCBAutoFlushThresholdTest {

    private static FrameManager newFM() {
        return SdlTestRig.frameManager();
    }

    @Test
    void bytesUnderThreshold_doesNotRequestFlush() {
        final FrameManager fm = newFM();
        fm.recordUploadCommands(MAX_PENDING_UPLOAD_BYTES / 2, 1);
        assertFalse(fm.shouldAutoSubmitPendingUpload());
    }

    @Test
    void bytesAtThreshold_requestsFlush() {
        final FrameManager fm = newFM();
        fm.recordUploadCommands(MAX_PENDING_UPLOAD_BYTES, 1);
        assertTrue(fm.shouldAutoSubmitPendingUpload());
    }

    @Test
    void bytesOverThreshold_requestsFlush() {
        final FrameManager fm = newFM();
        fm.recordUploadCommands(MAX_PENDING_UPLOAD_BYTES + 1, 1);
        assertTrue(fm.shouldAutoSubmitPendingUpload());
    }

    @Test
    void manyTinyUploadsAccumulateBytes() {
        final FrameManager fm = newFM();
        for (int i = 0; i < 255; i++) fm.recordUploadCommands(65536, 1);
        assertTrue(fm.frame().pendingUploadBytes < MAX_PENDING_UPLOAD_BYTES);
        fm.recordUploadCommands(65536, 1);
        assertTrue(fm.shouldAutoSubmitPendingUpload());
    }

    @Test
    void manyOneByteUploadsTripCommandThreshold() {
        final FrameManager fm = newFM();
        for (int i = 0; i < MAX_PENDING_UPLOAD_COMMANDS - 1; i++) fm.recordUploadCommands(1, 1);
        assertFalse(fm.shouldAutoSubmitPendingUpload(), "below command threshold and far below byte threshold");
        fm.recordUploadCommands(1, 1);
        assertTrue(fm.shouldAutoSubmitPendingUpload(), "exactly MAX_PENDING_UPLOAD_COMMANDS recorded -- request flush");
    }

    @Test
    void recordUploadCommands_perThreadCountersDoNotInterfere() throws Exception {
        final FrameManager fm = newFM();
        final AtomicLong threadABytes = new AtomicLong();
        final AtomicReference<Boolean> threadAShouldFlush = new AtomicReference<>();
        final CountDownLatch aDone = new CountDownLatch(1);
        final Thread tA = new Thread(() -> {
            try {
                fm.recordUploadCommands(MAX_PENDING_UPLOAD_BYTES + 1, 17);
                threadABytes.set(fm.frame().pendingUploadBytes);
                threadAShouldFlush.set(fm.shouldAutoSubmitPendingUpload());
            } finally {
                aDone.countDown();
            }
        }, "upload-thread-A");
        tA.start();
        aDone.await();

        assertEquals(MAX_PENDING_UPLOAD_BYTES + 1, threadABytes.get());
        assertTrue(threadAShouldFlush.get());
        assertEquals(0L, fm.frame().pendingUploadBytes);
        assertEquals(0, fm.frame().pendingUploadCommands);
        assertFalse(fm.shouldAutoSubmitPendingUpload());
    }
}
