package com.gtnewhorizons.angelica.rendering.culling;

import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataUnsafe;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SectionMetaSlotLifecycleTest {

    @Test
    void freedSlotsAreReusedInsteadOfGrowingTheHighWaterMark() {
        final SectionMetaBuffer meta = new SectionMetaBuffer();
        final ByteBuffer src = SyntheticSectionData.sliceMaskOnly(0x7F);
        final long ptr = MemoryUtilities.memAddress(src);
        try {
            for (int i = 0; i < 8; i++) meta.update(0, i * 64, 0, 0, 0, ptr, SectionRenderDataUnsafe.Strategy.FULL, QuadPrimitiveType.TRIANGULATED);
            assertEquals(8, meta.getHighWaterMark());

            for (int i = 0; i < 8; i++) meta.remove(0, i * 64, 0, 0, 0);
            assertEquals(8, meta.getHighWaterMark(), "high-water mark is monotonic within a session");

            for (int i = 0; i < 8; i++) meta.update(0, (100 + i) * 64, 0, 0, 0, ptr, SectionRenderDataUnsafe.Strategy.FULL, QuadPrimitiveType.TRIANGULATED);
            assertEquals(8, meta.getHighWaterMark(), "eight fresh sections must reuse the eight freed slots");
        } finally {
            MemoryUtilities.memFree(src);
            meta.reset();
        }
    }

    @Test
    void everySlotIsDistinctWhileLive() {
        final SectionMetaBuffer meta = new SectionMetaBuffer();
        final ByteBuffer src = SyntheticSectionData.sliceMaskOnly(0x7F);
        final long ptr = MemoryUtilities.memAddress(src);
        try {
            final int a = meta.update(0, 0, 0, 0, 0, ptr, SectionRenderDataUnsafe.Strategy.FULL, QuadPrimitiveType.TRIANGULATED);
            final int b = meta.update(0, 64, 0, 0, 0, ptr, SectionRenderDataUnsafe.Strategy.FULL, QuadPrimitiveType.TRIANGULATED);
            assertNotEquals(a, b);

            meta.remove(0, 0, 0, 0, 0);
            final int c = meta.update(0, 128, 0, 0, 0, ptr, SectionRenderDataUnsafe.Strategy.FULL, QuadPrimitiveType.TRIANGULATED);
            assertEquals(a, c, "the freed slot is handed to the next section");
            assertNotEquals(b, c, "a live slot is never handed out twice");
        } finally {
            MemoryUtilities.memFree(src);
            meta.reset();
        }
    }

    @Test
    void resetReturnsTheBufferToEmpty() {
        final SectionMetaBuffer meta = new SectionMetaBuffer();
        final ByteBuffer src = SyntheticSectionData.sliceMaskOnly(0x7F);
        final long ptr = MemoryUtilities.memAddress(src);
        try {
            for (int i = 0; i < 5; i++) meta.update(0, i * 64, 0, 0, 0, ptr, SectionRenderDataUnsafe.Strategy.FULL, QuadPrimitiveType.TRIANGULATED);
            assertTrue(meta.getHighWaterMark() > 0);

            meta.reset();
            assertEquals(0, meta.getHighWaterMark());
            assertEquals(-1, meta.lookupSlot(0, 0, 0, 0), "slots do not survive a reset");

            assertEquals(0, meta.update(0, 0, 0, 0, 0, ptr, SectionRenderDataUnsafe.Strategy.FULL, QuadPrimitiveType.TRIANGULATED), "allocation restarts from slot 0");
        } finally {
            MemoryUtilities.memFree(src);
            meta.reset();
        }
    }

    @Test
    void passesDoNotShareSlots() {
        final SectionMetaBuffer meta = new SectionMetaBuffer();
        final ByteBuffer src = SyntheticSectionData.sliceMaskOnly(0x7F);
        final long ptr = MemoryUtilities.memAddress(src);
        try {
            final int solid = meta.update(0, 0, 0, 0, 0, ptr, SectionRenderDataUnsafe.Strategy.FULL, QuadPrimitiveType.TRIANGULATED);
            final int cutout = meta.update(1, 0, 0, 0, 0, ptr, SectionRenderDataUnsafe.Strategy.FULL, QuadPrimitiveType.TRIANGULATED);
            final int translucent = meta.update(2, 0, 0, 0, 0, ptr, SectionRenderDataUnsafe.Strategy.FULL, QuadPrimitiveType.TRIANGULATED);
            assertNotEquals(solid, cutout);
            assertNotEquals(cutout, translucent);
            assertEquals(3, meta.getHighWaterMark());

            meta.remove(1, 0, 0, 0, 0);
            assertEquals(-1, meta.lookupSlot(1, 0, 0, 0));
            assertEquals(solid, meta.lookupSlot(0, 0, 0, 0), "removing one pass leaves its siblings alone");
            assertEquals(translucent, meta.lookupSlot(2, 0, 0, 0));
        } finally {
            MemoryUtilities.memFree(src);
            meta.reset();
        }
    }

    @Test
    void aNullDataPointerAllocatesNothing() {
        final SectionMetaBuffer meta = new SectionMetaBuffer();
        try {
            assertEquals(-1, meta.update(0, 0, 0, 0, 0, 0L, SectionRenderDataUnsafe.Strategy.FULL, QuadPrimitiveType.TRIANGULATED));
            assertEquals(0, meta.getHighWaterMark());
        } finally {
            meta.reset();
        }
    }
}
