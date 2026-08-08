package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransferThreadDeferFreeTest {

    private static TransferThread newThread() {
        final SdlTestRig rig = SdlTestRig.create();
        return new TransferThread(rig.device, rig.resourceManager);
    }

    @Test
    void seqAlreadySubmittedFreesInline() {
        final TransferThread tt = newThread();
        try {
            tt.freeAfterSeq(MemoryUtil.memAlloc(24), 0L);
            assertEquals(0, SdlReflect.pendingFreeCount(tt));
        } finally { shutdown(tt); }
    }

    @Test
    void futureSeqDefers() {
        final TransferThread tt = newThread();
        try {
            tt.freeAfterSeq(MemoryUtil.memAlloc(24), 100L);
            assertEquals(1, SdlReflect.pendingFreeCount(tt));
        } finally { shutdown(tt); }
    }

    @Test
    void shutdownDrainsRegardlessOfSeq() {
        final TransferThread tt = newThread();
        final ByteBuffer buf = MemoryUtil.memAlloc(24);
        tt.freeAfterSeq(buf, Long.MAX_VALUE);
        assertEquals(1, SdlReflect.pendingFreeCount(tt));

        shutdown(tt);
        assertEquals(0, SdlReflect.pendingFreeCount(tt));
    }

    @Test
    void nullBufferIsNoOp() {
        final TransferThread tt = newThread();
        try {
            tt.freeAfterSeq(null, 0L);
            tt.freeAfterSeq(null, 999L);
            assertEquals(0, SdlReflect.pendingFreeCount(tt));
        } finally { shutdown(tt); }
    }

    private static void shutdown(TransferThread tt) {
        tt.shutdown();
        try { tt.getThread().join(2000); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
