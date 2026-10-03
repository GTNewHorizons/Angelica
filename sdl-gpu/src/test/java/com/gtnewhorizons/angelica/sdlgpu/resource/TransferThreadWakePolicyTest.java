package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;
import org.junit.jupiter.api.Test;

import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferThreadWakePolicyTest {

    @Test
    void wakesOnceEveryCoalesceWindow() {
        int woken = 0;
        for (long seq = 1; seq <= 1600; seq++) {
            if (TransferThread.shouldWakeOnEnqueue(seq)) woken++;
        }
        assertEquals(100, woken, "1600 enqueues must produce 100 unparks, not 1600");
    }

    @Test
    void windowIsEvenlySpaced() {
        long previous = -1;
        for (long seq = 1; seq <= 512; seq++) {
            if (!TransferThread.shouldWakeOnEnqueue(seq)) continue;
            if (previous >= 0) assertEquals(16, seq - previous, "gaps between unparks must be uniform");
            previous = seq;
        }
    }

    @Test
    void neighboursOfAWakeDoNotWake() {
        assertTrue(TransferThread.shouldWakeOnEnqueue(16));
        assertFalse(TransferThread.shouldWakeOnEnqueue(15));
        assertFalse(TransferThread.shouldWakeOnEnqueue(17));
    }

    @Test
    void policyHoldsAtLargeSequenceNumbers() {
        final long base = 1L << 40;
        int woken = 0;
        for (long seq = base; seq < base + 1600; seq++) {
            if (TransferThread.shouldWakeOnEnqueue(seq)) woken++;
        }
        assertEquals(100, woken);
    }

    private static TransferThread stoppedThread(long submittedSeq) {
        final SdlTestRig rig = SdlTestRig.create();
        final TransferThread tt = new TransferThread(rig.device, rig.resourceManager);
        tt.shutdown();
        try {
            tt.getThread().join(5_000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
        assertFalse(tt.getThread().isAlive());
        Reflect.set(tt, "submittedSeq", submittedSeq);
        return tt;
    }

    @Test
    void awaitFallsBackToTheLockWhenSpinExpires() throws InterruptedException {
        final TransferThread tt = stoppedThread(5L);
        final Thread waiter = new Thread(() -> tt.awaitSubmittedUpTo(10L));
        waiter.start();
        LockSupport.parkNanos(20_000_000L);
        assertTrue(waiter.isAlive(), "must still be blocked; nothing published");
        Reflect.set(tt, "openHighestSeq", 10L);
        Reflect.invoke(tt, "publishSubmittedSeq", new Class<?>[0]);
        waiter.join(5_000L);
        assertFalse(waiter.isAlive(), "publish must release a waiter parked on submittedLock");
    }

    @Test
    void releasedUploadIsReusedByTheNextAcquire() {
        final TransferThread.StagingReadUpload a = TransferThread.StagingReadUpload.acquire(null, 0L, 0L, 0L, 0L, 1L, false);
        TransferThread.StagingReadUpload.release(a);
        final TransferThread.StagingReadUpload b = TransferThread.StagingReadUpload.acquire(null, 0L, 0L, 0L, 0L, 2L, false);
        assertSame(a, b);
        TransferThread.StagingReadUpload.release(b);
    }
}
