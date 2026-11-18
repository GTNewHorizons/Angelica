package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransferThreadDeferFreeTest {

    private static TransferThread newThread() {
        return SdlTestRig.create().newTransferThread();
    }

    @ParameterizedTest(name = "seq {0} leaves {1} pending")
    @CsvSource({"0, 0", "100, 1"})
    void freeIsDeferredOnlyForAnUnsubmittedSeq(long seq, int expectedPending) {
        final TransferThread tt = newThread();
        try {
            tt.freeAfterSeq(MemoryUtil.memAlloc(24), seq);
            assertEquals(expectedPending, SdlReflect.pendingFreeCount(tt));
        } finally { SdlTestRig.shutdown(tt); }
    }

    @Test
    void shutdownDrainsRegardlessOfSeq() {
        final TransferThread tt = newThread();
        final ByteBuffer buf = MemoryUtil.memAlloc(24);
        tt.freeAfterSeq(buf, Long.MAX_VALUE);
        assertEquals(1, SdlReflect.pendingFreeCount(tt));

        SdlTestRig.shutdown(tt);
        assertEquals(0, SdlReflect.pendingFreeCount(tt));
    }

    @Test
    void nullBufferIsNoOp() {
        final TransferThread tt = newThread();
        try {
            tt.freeAfterSeq(null, 0L);
            tt.freeAfterSeq(null, 999L);
            assertEquals(0, SdlReflect.pendingFreeCount(tt));
        } finally { SdlTestRig.shutdown(tt); }
    }
}
