package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReleasePersistentStagingTest {

    @Test
    void nullPmAndNullStagingNoOp() {
        final ResourceManager rm = SdlTestRig.resourceManager();
        assertDoesNotThrow(() -> rm.releasePersistentStaging(null, 7, "unmap"));
        assertDoesNotThrow(() -> rm.releasePersistentStaging(new PersistentMapping(null, 0L, 0L, 0), 7, "unmap"));
    }

    @Test
    void noTransferThreadFreesInlineEvenWithAnInFlightSeq() {
        final ResourceManager rm = SdlTestRig.resourceManager();
        final PersistentMapping pm = new PersistentMapping(MemoryUtil.memAlloc(24), 0L, 24L, 0);
        pm.lastEnqueuedSeq = 50L;
        assertDoesNotThrow(() -> rm.releasePersistentStaging(pm, 7, "unmap"));
    }

    @ParameterizedTest(name = "lastEnqueuedSeq {0} leaves {1} pending")
    @CsvSource({"0, 0", "50, 1"})
    void stagingFreeIsDeferredOnlyWhileItsSeqIsInFlight(long lastEnqueuedSeq, int expectedPending) {
        final SdlTestRig rig = SdlTestRig.create();
        final ResourceManager rm = rig.resourceManager;
        final TransferThread tt = rig.installTransferThread();
        try {
            final PersistentMapping pm = new PersistentMapping(MemoryUtil.memAlloc(24), 0L, 24L, 0);
            pm.lastEnqueuedSeq = lastEnqueuedSeq;
            rm.releasePersistentStaging(pm, 7, "unmap");
            assertEquals(expectedPending, SdlReflect.pendingFreeCount(tt));
        } finally { SdlTestRig.shutdown(tt); }
    }

    @Test
    void deleteBufferReleasesPersistentStaging() {
        final ResourceManager rm = SdlTestRig.resourceManager();
        SdlReflect.recordBuffer(rm, 7, 0xDEADBEEFL, 64, 0);
        rm.putPersistentMappingIfAbsent(7, new PersistentMapping(MemoryUtil.memCalloc(64), 0L, 64L, 0));

        rm.deleteBuffer(7);

        assertNull(rm.getPersistentMapping(7));
    }

    @Test
    void deleteBufferDefersFreeWhenSeqInFlight() {
        final SdlTestRig rig = SdlTestRig.create();
        final ResourceManager rm = rig.resourceManager;
        final TransferThread tt = rig.installTransferThread();
        try {
            final ByteBuffer staging = MemoryUtil.memCalloc(64);
            final PersistentMapping pm = new PersistentMapping(staging, 0L, staging.capacity(), 0);
            pm.lastEnqueuedSeq = 99L;
            SdlReflect.recordBuffer(rm, 7, 0xDEADBEEFL, 64, 0);
            rm.putPersistentMappingIfAbsent(7, pm);

            rm.deleteBuffer(7);

            assertNull(rm.getPersistentMapping(7));
            assertEquals(1, SdlReflect.pendingFreeCount(tt));
        } finally { SdlTestRig.shutdown(tt); }
    }
}
