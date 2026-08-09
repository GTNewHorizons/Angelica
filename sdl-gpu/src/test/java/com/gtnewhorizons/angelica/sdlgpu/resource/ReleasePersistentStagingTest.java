package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;

import com.gtnewhorizons.angelica.sdlgpu.device.Device;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReleasePersistentStagingTest {

    @Test
    void nullPmAndNullStagingNoOp() {
        final ResourceManager rm = SdlTestRig.resourceManager();
        rm.releasePersistentStaging(null);
        rm.releasePersistentStaging(new PersistentMapping(null, 0L, 0L, 0));
    }

    @Test
    void noTransferThreadFreesInline() {
        final ResourceManager rm = SdlTestRig.resourceManager();
        rm.releasePersistentStaging(new PersistentMapping(MemoryUtil.memAlloc(24), 0L, 24L, 0));
    }

    @Test
    void seqAlreadySubmittedFreesInline() {
        final SdlTestRig rig = SdlTestRig.create();
        final Device dev = rig.device;
        final FrameManager fm = rig.frameManager;
        final ResourceManager rm = rig.resourceManager;
        final TransferThread tt = new TransferThread(dev, rm);
        rm.setTransferThread(tt);
        try {
            final PersistentMapping pm = new PersistentMapping(MemoryUtil.memAlloc(24), 0L, 24L, 0);
            pm.lastEnqueuedSeq = 0L;
            rm.releasePersistentStaging(pm);
            assertEquals(0, SdlReflect.pendingFreeCount(tt));
        } finally { shutdown(tt); }
    }

    @Test
    void inFlightSeqRoutesToFreeAfterSeq() {
        final SdlTestRig rig = SdlTestRig.create();
        final Device dev = rig.device;
        final FrameManager fm = rig.frameManager;
        final ResourceManager rm = rig.resourceManager;
        final TransferThread tt = new TransferThread(dev, rm);
        rm.setTransferThread(tt);
        try {
            final PersistentMapping pm = new PersistentMapping(MemoryUtil.memAlloc(24), 0L, 24L, 0);
            pm.lastEnqueuedSeq = 50L;
            rm.releasePersistentStaging(pm);
            assertEquals(1, SdlReflect.pendingFreeCount(tt));
        } finally { shutdown(tt); }
    }

    @Test
    void deleteBufferReleasesPersistentStaging() {
        final ResourceManager rm = SdlTestRig.resourceManager();
        SdlReflect.recordBuffer(rm, 7, 0xDEADBEEFL, 64, 0);
        rm.putPersistentMapping(7, new PersistentMapping(MemoryUtil.memCalloc(64), 0L, 64L, 0));

        rm.deleteBuffer(7);

        assertNull(rm.getPersistentMapping(7));
    }

    @Test
    void deleteBufferDefersFreeWhenSeqInFlight() {
        final SdlTestRig rig = SdlTestRig.create();
        final Device dev = rig.device;
        final FrameManager fm = rig.frameManager;
        final ResourceManager rm = rig.resourceManager;
        final TransferThread tt = new TransferThread(dev, rm);
        rm.setTransferThread(tt);
        try {
            final ByteBuffer staging = MemoryUtil.memCalloc(64);
            final PersistentMapping pm = new PersistentMapping(staging, 0L, staging.capacity(), 0);
            pm.lastEnqueuedSeq = 99L;
            SdlReflect.recordBuffer(rm, 7, 0xDEADBEEFL, 64, 0);
            rm.putPersistentMapping(7, pm);

            rm.deleteBuffer(7);

            assertNull(rm.getPersistentMapping(7));
            assertEquals(1, SdlReflect.pendingFreeCount(tt));
        } finally { shutdown(tt); }
    }

    private static void shutdown(TransferThread tt) {
        tt.shutdown();
        try { tt.getThread().join(2000); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
