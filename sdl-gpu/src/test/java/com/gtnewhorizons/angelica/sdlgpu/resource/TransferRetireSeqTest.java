package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;
import org.junit.jupiter.api.Test;

import static com.gtnewhorizons.angelica.sdlgpu.resource.TransferThread.shouldPublishRetired;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferRetireSeqTest {

    private static TransferThread idleThread() {
        final SdlTestRig rig = SdlTestRig.create();
        final TransferThread tt = new TransferThread(rig.device, rig.resourceManager);
        tt.shutdown();
        try {
            tt.getThread().join(5_000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while stopping the transfer thread", e);
        }
        assertFalse(tt.getThread().isAlive(), "the worker must be stopped first: its loop zeroes openHighestSeq after publishing, so a live worker races this test");
        return tt;
    }

    @Test
    void anOpenCommandBufferIsNeverPublishedEarly() {assertFalse(shouldPublishRetired(true, 50L, 10L),
            "those copies are recorded but not submitted; a waiter must not be released");
    }

    @Test
    void retiredSeqsArePublishedWhenNothingIsOpen() {
        assertTrue(shouldPublishRetired(false, 50L, 10L));
    }

    @Test
    void anAlreadyPublishedSeqIsNotRepublished() {
        assertFalse(shouldPublishRetired(false, 10L, 10L));
        assertFalse(shouldPublishRetired(false, 0L, 10L));
    }

    @Test
    void aDroppedTextureUploadStillRetiresItsSeq() {
        final TransferThread tt = idleThread();
        Reflect.invoke(tt, "recordTextureUpload",
            new Class<?>[]{ long.class, long.class, int.class, int.class, int.class, int.class, int.class, long.class, long.class },
            0L, 0xBEEFL, 0, 0, 16, 16, 0, 1024L, 42L);

        assertEquals(42L, (long) Reflect.get(tt, "openHighestSeq"), "a dropped upload must still advance the watermark or awaitSubmittedUpTo blocks forever");
    }

    @Test
    void aDroppedBufferUploadStillRetiresItsSeq() {
        final TransferThread tt = idleThread();
        Reflect.invoke(tt, "recordBufferUpload", new Class<?>[]{ long.class, long.class, long.class, long.class, long.class, boolean.class }, 0L, 256L, 0xF00L, 0L, 7L, false);

        assertEquals(7L, (long) Reflect.get(tt, "openHighestSeq"));
    }
}
