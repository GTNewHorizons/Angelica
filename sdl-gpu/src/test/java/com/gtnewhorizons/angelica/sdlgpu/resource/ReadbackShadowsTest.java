package com.gtnewhorizons.angelica.sdlgpu.resource;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadbackShadowsTest {

    private static final long SENTINEL_HANDLE = 0xBEEFL;

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
}
