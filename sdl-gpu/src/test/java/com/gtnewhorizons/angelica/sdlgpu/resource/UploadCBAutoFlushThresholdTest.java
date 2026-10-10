package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.glsm.testutil.TestThreads;
import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;

import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager.MAX_PENDING_UPLOAD_BYTES;
import static com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager.MAX_PENDING_UPLOAD_COMMANDS;
import static org.junit.jupiter.api.Assertions.*;

class UploadCBAutoFlushThresholdTest {

    static Stream<Arguments> byteCounts() {
        return Stream.of(
            Arguments.of("half the threshold", MAX_PENDING_UPLOAD_BYTES / 2, false),
            Arguments.of("at the threshold", MAX_PENDING_UPLOAD_BYTES, true),
            Arguments.of("one byte over the threshold", MAX_PENDING_UPLOAD_BYTES + 1, true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("byteCounts")
    void flushIsRequestedFromTheByteThreshold(String name, long bytes, boolean expectFlush) {
        final FrameManager fm = SdlTestRig.frameManager();
        fm.recordUploadCommands(bytes, 1);
        assertEquals(expectFlush, fm.shouldAutoSubmitPendingUpload());
    }

    @Test
    void manyTinyUploadsAccumulateBytes() {
        final FrameManager fm = SdlTestRig.frameManager();
        for (int i = 0; i < 255; i++) fm.recordUploadCommands(65536, 1);
        assertTrue(fm.frame().pendingUploadBytes < MAX_PENDING_UPLOAD_BYTES);
        fm.recordUploadCommands(65536, 1);
        assertTrue(fm.shouldAutoSubmitPendingUpload());
    }

    @Test
    void manyOneByteUploadsTripCommandThreshold() {
        final FrameManager fm = SdlTestRig.frameManager();
        for (int i = 0; i < MAX_PENDING_UPLOAD_COMMANDS - 1; i++) fm.recordUploadCommands(1, 1);
        assertFalse(fm.shouldAutoSubmitPendingUpload(), "below command threshold and far below byte threshold");
        fm.recordUploadCommands(1, 1);
        assertTrue(fm.shouldAutoSubmitPendingUpload(), "exactly MAX_PENDING_UPLOAD_COMMANDS recorded -- request flush");
    }

    @Test
    void recordUploadCommands_perThreadCountersDoNotInterfere() throws Exception {
        final FrameManager fm = SdlTestRig.frameManager();

        TestThreads.run("upload-thread-A", () -> {
            fm.recordUploadCommands(MAX_PENDING_UPLOAD_BYTES + 1, 17);
            assertEquals(MAX_PENDING_UPLOAD_BYTES + 1, fm.frame().pendingUploadBytes);
            assertTrue(fm.shouldAutoSubmitPendingUpload());
        });

        assertEquals(0L, fm.frame().pendingUploadBytes);
        assertEquals(0, fm.frame().pendingUploadCommands);
        assertFalse(fm.shouldAutoSubmitPendingUpload());
    }
}
