package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.sdlgpu.SdlAsserts;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadbackShadowsTest {

    private static final long SENTINEL_HANDLE = 0xBEEFL;
    private static final int SLOTS_PER_BUFFER = 32;
    private static final int SLOT_STRIDE = 64;

    private static ReadbackShadows shadows() {
        return new ReadbackShadows(null);
    }

    private static ByteBuffer out(int bytes) {
        return ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
    }

    @Test
    void serveRefusesAStaleSlot() {
        final ReadbackShadows shadows = shadows();
        SdlReflect.putReadbackSlot(shadows, 7, 0, SENTINEL_HANDLE, 64, false);

        assertFalse(shadows.serve(7, 0, out(64), 64),
            "a slot invalidated by a GPU-to-GPU write must not be served as if it were downloaded");
    }

    @Test
    void serveRefusesAnUnknownSlot() {
        final ReadbackShadows shadows = shadows();
        SdlReflect.putReadbackSlot(shadows, 7, 0, SENTINEL_HANDLE, 64, true);

        assertFalse(shadows.serve(7, 64, out(64), 64), "a different ring offset is a different slot");
        assertFalse(shadows.serve(8, 0, out(64), 64), "a different buffer is a different slot");
    }

    @Test
    void invalidateTouchesOnlyItsOwnSlot() {
        final ReadbackShadows shadows = shadows();
        SdlReflect.putReadbackSlot(shadows, 7, 0, SENTINEL_HANDLE, 64, true);
        SdlReflect.putReadbackSlot(shadows, 7, 64, SENTINEL_HANDLE, 64, true);

        shadows.invalidate(7, 0);

        assertFalse(shadows.serve(7, 0, out(64), 64), "the invalidated slot is still being served");
        assertTrue(SdlReflect.readbackSlotValid(shadows, 7, 64), "invalidating one ring slot must not disturb the others");
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

    private static int slotCount(ReadbackShadows shadows) {
        return Reflect.<Map<Long, Object>>get(shadows, "slots").size();
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
        assertEquals(2 * SLOTS_PER_BUFFER, slotCount(shadows));
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
