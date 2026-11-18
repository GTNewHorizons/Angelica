package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.sdlgpu.SdlAsserts;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadbackShadowsTest {

    private static final long SENTINEL_HANDLE = 0xBEEFL;
    private static final int SLOTS_PER_BUFFER = 32;
    private static final int SLOT_STRIDE = 64;

    private static ReadbackShadows shadows() {
        return new ReadbackShadows(null, null, null);
    }

    private static ByteBuffer out(int bytes) {
        return ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
    }

    @Test
    void serveRefusesAnUnknownSlot() {
        final ReadbackShadows shadows = shadows();
        SdlReflect.putReadbackSlot(shadows, 7, 0, SENTINEL_HANDLE, 64, true);

        assertFalse(shadows.serve(7, 64, out(64), 64), "a different ring offset is a different slot");
        assertFalse(shadows.serve(8, 0, out(64), 64), "a different buffer is a different slot");
    }

    @Test
    void invalidateAllTouchesOnlyItsBuffer() {
        final ReadbackShadows shadows = shadows();
        SdlReflect.putReadbackSlot(shadows, 7, 0, SENTINEL_HANDLE, 64, true);
        SdlReflect.putReadbackSlot(shadows, 7, 64, SENTINEL_HANDLE, 64, true);
        SdlReflect.putReadbackSlot(shadows, 8, 0, SENTINEL_HANDLE, 64, true);

        shadows.invalidateAll(7);

        assertFalse(shadows.serve(7, 0, out(64), 64), "an invalidated slot is still being served");
        assertFalse(SdlReflect.readbackSlotValid(shadows, 7, 64), "every slot of the written buffer must be invalidated");
        assertTrue(SdlReflect.readbackSlotValid(shadows, 8, 0), "a write to one buffer must not invalidate another's slots");
    }

    @Test
    void invalidateAllOfAnUnshadowedBufferDoesNotAllocate() {
        final ReadbackShadows shadows = shadows();
        SdlReflect.putReadbackSlot(shadows, 8, 0, SENTINEL_HANDLE, 64, true);
        SdlAsserts.assertAllocationFree(() -> shadows.invalidateAll(7), "buffer writes while another buffer is shadowed");
    }

    @Test
    void releaseDropsOnlyTheNamedBuffer() {
        final ReadbackShadows shadows = shadows();
        SdlReflect.putReadbackSlot(shadows, 7, 0, 0L, 64, true);
        SdlReflect.putReadbackSlot(shadows, 7, 64, 0L, 64, true);
        SdlReflect.putReadbackSlot(shadows, 8, 0, 0L, 64, true);

        shadows.release(7);

        assertFalse(shadows.hasSlotsFor(7), "deleting a buffer must drop all of its ring slots");
        assertTrue(shadows.hasSlotsFor(8), "deleting one buffer must not drop another's slots");
    }

    private static ReadbackShadows ringShadows(int... glIds) {
        final ReadbackShadows shadows = shadows();
        for (int glId : glIds) {
            for (int i = 0; i < SLOTS_PER_BUFFER; i++) {
                SdlReflect.putReadbackSlot(shadows, glId, (long) i * SLOT_STRIDE, 0L, SLOT_STRIDE, true);
            }
        }
        return shadows;
    }

    @Test
    void releaseRemovesExactlyTheSlotsOfItsBuffer() {
        final ReadbackShadows shadows = ringShadows(1, 2, 3);

        shadows.release(2);

        assertFalse(shadows.hasSlotsFor(2));
        assertEquals(2 * SLOTS_PER_BUFFER, SdlReflect.readbackSlotCount(shadows));
        for (int glId = 1; glId <= 3; glId += 2) {
            for (int i = 0; i < SLOTS_PER_BUFFER; i++) {
                assertTrue(SdlReflect.readbackSlotValid(shadows, glId, (long) i * SLOT_STRIDE));
            }
        }
    }

    @Test
    void releaseWithNoSlotsDoesNotAllocate() {
        final ReadbackShadows shadows = shadows();
        SdlAsserts.assertAllocationFree(() -> shadows.release(7), "releases with no readback slots");
    }
}
