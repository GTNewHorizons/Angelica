package com.gtnewhorizons.angelica.rendering.culling;

import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataUnsafe;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SectionMetaSlotLifecycleTest {

    @Test
    void freedSlotsAreReusedInsteadOfGrowingTheHighWaterMark() {
        final SectionMetaBuffer meta = new SectionMetaBuffer();
        final ByteBuffer src = SyntheticSectionData.sliceMaskOnly(0x7F);
        final long ptr = MemoryUtilities.memAddress(src);
        try {
            for (int i = 0; i < 8; i++) meta.update(0, i * 64, 0, 0, 0, ptr, 0x7F, SectionRenderDataUnsafe.Strategy.FULL, QuadPrimitiveType.TRIANGULATED);
            assertEquals(8, meta.getHighWaterMark());

            for (int i = 0; i < 8; i++) meta.remove(0, i * 64, 0, 0, 0);
            assertEquals(8, meta.getHighWaterMark(), "high-water mark is monotonic within a session");

            for (int i = 0; i < 8; i++) meta.update(0, (100 + i) * 64, 0, 0, 0, ptr, 0x7F, SectionRenderDataUnsafe.Strategy.FULL, QuadPrimitiveType.TRIANGULATED);
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
            final int a = meta.update(0, 0, 0, 0, 0, ptr, 0x7F, SectionRenderDataUnsafe.Strategy.FULL, QuadPrimitiveType.TRIANGULATED);
            final int b = meta.update(0, 64, 0, 0, 0, ptr, 0x7F, SectionRenderDataUnsafe.Strategy.FULL, QuadPrimitiveType.TRIANGULATED);
            assertNotEquals(a, b);

            meta.remove(0, 0, 0, 0, 0);
            final int c = meta.update(0, 128, 0, 0, 0, ptr, 0x7F, SectionRenderDataUnsafe.Strategy.FULL, QuadPrimitiveType.TRIANGULATED);
            assertEquals(a, c, "the freed slot is handed to the next section");
            assertNotEquals(b, c, "a live slot is never handed out twice");
        } finally {
            MemoryUtilities.memFree(src);
            meta.reset();
        }
    }
}
