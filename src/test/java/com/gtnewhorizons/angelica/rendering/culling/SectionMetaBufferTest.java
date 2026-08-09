package com.gtnewhorizons.angelica.rendering.culling;

import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataUnsafe;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SectionMetaBufferTest {
    @Test
    void slotLayout_originAndFencePosts() {
        final SectionMetaBuffer meta = new SectionMetaBuffer();
        final int[] vo = { 100, 200, 300, 400, 500, 600, 700 };
        final int[] ec = { 12, 24, 36, 48, 60, 72, 84 };
        final int[] io = { 4, 16, 32, 64, 128, 256, 512 };
        final int sliceMaskLow = 0x7F;
        final ByteBuffer src = SyntheticSectionData.row(sliceMaskLow, 0xDEADBEEF, vo, ec, io);

        try {
            meta.update(0, 16, 32, 64, 0, MemoryUtilities.memAddress(src), SectionRenderDataUnsafe.Strategy.FULL, QuadPrimitiveType.TRIANGULATED);

            final int slot = meta.lookupSlot(0, 16, 32, 64);
            assertEquals(0, slot, "first allocated slot should be 0");

            final ByteBuffer mirror = meta.getMirrorForReadOnly();
            assertNotNull(mirror);

            assertEquals(16, mirror.getInt(0),  "origin.x");
            assertEquals(32, mirror.getInt(4),  "origin.y");
            assertEquals(64, mirror.getInt(8),  "origin.z");
            assertEquals(sliceMaskLow, mirror.getInt(SectionMetaBuffer.OFFSET_SLICE_MASK), "origin.w (sliceMask low)");

            for (int facing = 0; facing < 7; facing++) {
                assertEquals(vo[facing], mirror.getInt(SectionMetaBuffer.OFFSET_POSTS + facing * 4), "post[" + facing + "] is facing " + facing + "'s first vertex");
            }
            assertEquals(vo[6] + (ec[6] / 6) * 4, mirror.getInt(SectionMetaBuffer.OFFSET_POSTS + 7 * 4), "post[7] closes the last facing's vertex range");
            assertEquals(io[0] / 4, mirror.getInt(SectionMetaBuffer.OFFSET_FIRST_INDEX_BASE), "firstIndexBase is the section's index base in elements");
        } finally {
            MemoryUtilities.memFree(src);
            meta.shutdown();
        }
    }

    @Test
    void slotAllocation_isStablePerSection() {
        final SectionMetaBuffer meta = new SectionMetaBuffer();
        final int[] vo = { 0, 0, 0, 0, 0, 0, 0 };
        final int[] ec = { 0, 0, 0, 0, 0, 0, 0 };
        final int[] io = { 0, 0, 0, 0, 0, 0, 0 };
        final ByteBuffer src = SyntheticSectionData.row(0, 0, vo, ec, io);
        try {
            meta.update(0, 16, 32, 64, 0, MemoryUtilities.memAddress(src), SectionRenderDataUnsafe.Strategy.FULL, QuadPrimitiveType.TRIANGULATED);
            final int firstSlot = meta.lookupSlot(0, 16, 32, 64);
            meta.update(0, 16, 32, 64, 0, MemoryUtilities.memAddress(src), SectionRenderDataUnsafe.Strategy.FULL, QuadPrimitiveType.TRIANGULATED);
            assertEquals(firstSlot, meta.lookupSlot(0, 16, 32, 64), "slot must be stable across updates");

            meta.update(0, 0, 0, 0, 0, MemoryUtilities.memAddress(src), SectionRenderDataUnsafe.Strategy.FULL, QuadPrimitiveType.TRIANGULATED);
            final int otherSlot = meta.lookupSlot(0, 0, 0, 0);
            assertEquals(firstSlot + 1, otherSlot, "fresh section -> next high-water slot");

            meta.remove(0, 16, 32, 64, 0);
            assertEquals(-1, meta.lookupSlot(0, 16, 32, 64), "removed slot should not resolve");
            meta.update(0, 16, 32, 64, 0, MemoryUtilities.memAddress(src), SectionRenderDataUnsafe.Strategy.FULL, QuadPrimitiveType.TRIANGULATED);
            assertEquals(firstSlot, meta.lookupSlot(0, 16, 32, 64), "free-list reuse should reclaim the slot");
        } finally {
            MemoryUtilities.memFree(src);
            meta.shutdown();
        }
    }

    @Test
    void localSectionIndex_unpacking() {
        final SectionMetaBuffer meta = new SectionMetaBuffer();
        final int[] vo = { 0, 0, 0, 0, 0, 0, 0 };
        final int[] ec = { 0, 0, 0, 0, 0, 0, 0 };
        final int[] io = { 0, 0, 0, 0, 0, 0, 0 };
        final ByteBuffer src = SyntheticSectionData.row(0, 0, vo, ec, io);
        try {
            final int x = 3, y = 2, z = 5;
            final int localIdx = (x << 5) | (z << 2) | y;
            meta.update(0, 0, 0, 0, localIdx, MemoryUtilities.memAddress(src), SectionRenderDataUnsafe.Strategy.FULL, QuadPrimitiveType.TRIANGULATED);
            final ByteBuffer mirror = meta.getMirrorForReadOnly();
            assertEquals(x << 4, mirror.getInt(0), "origin.x = sectionX << 4");
            assertEquals(y << 4, mirror.getInt(4), "origin.y = sectionY << 4");
            assertEquals(z << 4, mirror.getInt(8), "origin.z = sectionZ << 4");
        } finally {
            MemoryUtilities.memFree(src);
            meta.shutdown();
        }
    }

    @Test
    void distinctPasses_sameCoords_distinctSlots() {
        final SectionMetaBuffer meta = new SectionMetaBuffer();
        final int[] voA = { 1000, 0, 0, 0, 0, 0, 0 };
        final int[] voB = { 2000, 0, 0, 0, 0, 0, 0 };
        final int[] ecA = { 100, 0, 0, 0, 0, 0, 0 };
        final int[] ecB = { 0, 0, 0, 0, 0, 0, 200 }; // different facing pattern
        final int[] io = { 0, 0, 0, 0, 0, 0, 0 };
        final ByteBuffer srcA = SyntheticSectionData.row(0x33, 0, voA, ecA, io);
        final ByteBuffer srcB = SyntheticSectionData.row(0x40, 0, voB, ecB, io);
        try {
            meta.update(0, 16, 32, 64, 0, MemoryUtilities.memAddress(srcA), SectionRenderDataUnsafe.Strategy.FULL, QuadPrimitiveType.TRIANGULATED);
            meta.update(1, 16, 32, 64, 0, MemoryUtilities.memAddress(srcB), SectionRenderDataUnsafe.Strategy.FULL, QuadPrimitiveType.TRIANGULATED);
            final int slotA = meta.lookupSlot(0, 16, 32, 64);
            final int slotB = meta.lookupSlot(1, 16, 32, 64);
            assertEquals(0, slotA, "pass 0 -> slot 0");
            assertEquals(1, slotB, "pass 1 -> slot 1, distinct from pass 0's slot");

            final ByteBuffer mirror = meta.getMirrorForReadOnly();
            final int stride = SectionMetaBuffer.BYTES_PER_SECTION;
            assertEquals(0x33, mirror.getInt(0 * stride + SectionMetaBuffer.OFFSET_SLICE_MASK), "slot 0 sliceMask comes from passA");
            assertEquals(0x40, mirror.getInt(1 * stride + SectionMetaBuffer.OFFSET_SLICE_MASK), "slot 1 sliceMask comes from passB");
            assertEquals(1000, mirror.getInt(0 * stride + SectionMetaBuffer.OFFSET_POSTS), "slot 0 post[0] from passA");
            assertEquals(2000, mirror.getInt(1 * stride + SectionMetaBuffer.OFFSET_POSTS), "slot 1 post[0] from passB");
        } finally {
            MemoryUtilities.memFree(srcA);
            MemoryUtilities.memFree(srcB);
            meta.shutdown();
        }
    }

    @Test
    void update_returnsAllocatedSlot() {
        final SectionMetaBuffer meta = new SectionMetaBuffer();
        final int[] vo = { 0, 0, 0, 0, 0, 0, 0 };
        final int[] ec = { 0, 0, 0, 0, 0, 0, 0 };
        final int[] io = { 0, 0, 0, 0, 0, 0, 0 };
        final ByteBuffer src = SyntheticSectionData.row(0, 0, vo, ec, io);
        try {
            final int slotA = meta.update(0, 16, 32, 64, 0, MemoryUtilities.memAddress(src), SectionRenderDataUnsafe.Strategy.FULL, QuadPrimitiveType.TRIANGULATED);
            final int slotB = meta.update(0, 0, 0, 0, 0, MemoryUtilities.memAddress(src), SectionRenderDataUnsafe.Strategy.FULL, QuadPrimitiveType.TRIANGULATED);
            assertEquals(0, slotA, "first allocation -> slot 0");
            assertEquals(1, slotB, "second distinct section -> slot 1");
            assertEquals(slotA, meta.update(0, 16, 32, 64, 0, MemoryUtilities.memAddress(src), SectionRenderDataUnsafe.Strategy.FULL, QuadPrimitiveType.TRIANGULATED), "re-update returns the stable slot");
            assertEquals(-1, meta.update(0, 0, 0, 0, 0, 0L, SectionRenderDataUnsafe.Strategy.FULL, QuadPrimitiveType.TRIANGULATED), "null dataPtr -> -1");
        } finally {
            MemoryUtilities.memFree(src);
            meta.shutdown();
        }
    }

    @Test
    void syncIfDirty_positionsAndClearsDirty() {
        final SectionMetaBuffer meta = new SectionMetaBuffer();
        final int[] zeros = { 0, 0, 0, 0, 0, 0, 0 };
        final ByteBuffer src = SyntheticSectionData.row(0, 0, zeros, zeros, zeros);
        try {
            meta.update(0, 16, 32, 64, 0, MemoryUtilities.memAddress(src), SectionRenderDataUnsafe.Strategy.FULL, QuadPrimitiveType.TRIANGULATED);
            meta.update(0, 0, 0, 0, 0, MemoryUtilities.memAddress(src), SectionRenderDataUnsafe.Strategy.FULL, QuadPrimitiveType.TRIANGULATED);
            assertTrue(meta.isDirty(), "dirty after update");
            final AtomicReference<ByteBuffer> seen = new AtomicReference<>();
            final boolean ran = meta.syncIfDirty(b -> { seen.set(b); return true; });
            assertTrue(ran, "sink must run when dirty + non-empty");
            assertFalse(meta.isDirty(), "dirty cleared by syncIfDirty");
            assertNotNull(seen.get());
            assertEquals(0, seen.get().position(), "position is zeroed for sink");
            assertEquals(2 * SectionMetaBuffer.BYTES_PER_SECTION, seen.get().limit(), "limit is hwm * BYTES_PER_SECTION");
            assertEquals(0, seen.get().position(), "position is zero");
            assertFalse(meta.syncIfDirty(_ -> { throw new AssertionError("must not run"); }), "second call with no updates must short-circuit");
        } finally {
            MemoryUtilities.memFree(src);
            meta.reset();
        }
    }

    @Test
    void syncIfDirty_refusedSinkKeepsDataPending() {
        final SectionMetaBuffer meta = new SectionMetaBuffer();
        final int[] zeros = { 0, 0, 0, 0, 0, 0, 0 };
        final ByteBuffer src = SyntheticSectionData.row(0, 0, zeros, zeros, zeros);
        try {
            meta.update(0, 16, 32, 64, 0, MemoryUtilities.memAddress(src), SectionRenderDataUnsafe.Strategy.FULL, QuadPrimitiveType.TRIANGULATED);
            final AtomicInteger offers = new AtomicInteger();

            assertFalse(meta.syncIfDirty(b -> { offers.incrementAndGet(); return false; }), "a refused sink reports no sync");
            assertTrue(meta.isDirty(), "refused data stays pending");

            final AtomicReference<ByteBuffer> seen = new AtomicReference<>();
            assertTrue(meta.syncIfDirty(b -> { offers.incrementAndGet(); seen.set(b); return true; }), "the same data is re-offered once the sink accepts");
            assertFalse(meta.isDirty(), "dirty clears only after the sink consumes");
            assertEquals(2, offers.get());
            assertEquals(SectionMetaBuffer.BYTES_PER_SECTION, seen.get().limit());
        } finally {
            MemoryUtilities.memFree(src);
            meta.reset();
        }
    }

    @Test
    void syncIfDirty_emptyMirrorStillClearsDirty() {
        final SectionMetaBuffer meta = new SectionMetaBuffer();
        try {
            assertFalse(meta.isDirty(), "fresh buffer is not dirty");
            assertFalse(meta.syncIfDirty(_ -> { throw new AssertionError("must not run"); }), "no updates -> false");
        } finally {
            meta.reset();
        }
    }

    @Test
    void reset_isIdempotent() {
        final SectionMetaBuffer meta = new SectionMetaBuffer();
        final int[] zeros = { 0, 0, 0, 0, 0, 0, 0 };
        final ByteBuffer src = SyntheticSectionData.row(0, 0, zeros, zeros, zeros);
        try {
            meta.update(0, 16, 32, 64, 0, MemoryUtilities.memAddress(src), SectionRenderDataUnsafe.Strategy.FULL, QuadPrimitiveType.TRIANGULATED);
            meta.reset();
            assertNull(meta.getMirrorForReadOnly(), "mirror gone after reset");
            assertEquals(-1, meta.lookupSlot(0, 16, 32, 64), "slot map cleared");
            meta.reset();
            meta.reset();
            meta.update(0, 16, 32, 64, 0, MemoryUtilities.memAddress(src), SectionRenderDataUnsafe.Strategy.FULL, QuadPrimitiveType.TRIANGULATED);
            assertEquals(0, meta.lookupSlot(0, 16, 32, 64), "reset is reusable, allocates fresh slot");
        } finally {
            MemoryUtilities.memFree(src);
            meta.reset();
        }
    }

    @Test
    void concurrent_updates_and_syncs_doNotCrash() throws InterruptedException {
        final SectionMetaBuffer meta = new SectionMetaBuffer();
        final int[] zeros = { 0, 0, 0, 0, 0, 0, 0 };
        final ByteBuffer src = SyntheticSectionData.row(0, 0, zeros, zeros, zeros);
        final AtomicBoolean stop = new AtomicBoolean(false);
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final long srcAddr = MemoryUtilities.memAddress(src);

        final Thread writer = new Thread(() -> {
            try {
                int x = 0, y = 0, z = 0;
                while (!stop.get()) {
                    meta.update(0, x << 4, y << 4, z << 4, 0, srcAddr, SectionRenderDataUnsafe.Strategy.FULL, QuadPrimitiveType.TRIANGULATED);
                    x++;
                    if (x > 63) { x = 0; y++; }
                    if (y > 63) { y = 0; z++; }
                    if (z > 63) z = 0;
                }
            } catch (Throwable t) { error.set(t); }
        }, "section-writer");

        final Thread reader = new Thread(() -> {
            try {
                byte[] sink = new byte[1024 * 1024];
                while (!stop.get()) {
                    meta.syncIfDirty(b -> {
                        final int n = Math.min(b.remaining(), sink.length);
                        for (int i = 0; i < n; i++) sink[i] = b.get(i);
                        return true;
                    });
                }
            } catch (Throwable t) { error.set(t); }
        }, "section-reader");

        writer.start();
        reader.start();
        Thread.sleep(2000);
        stop.set(true);
        writer.join(5000);
        reader.join(5000);

        try {
            if (error.get() != null) throw new AssertionError("worker crashed", error.get());
        } finally {
            MemoryUtilities.memFree(src);
            meta.reset();
        }
    }

    @Test
    void perPassRemove_leavesSiblingPasses() {
        final SectionMetaBuffer meta = new SectionMetaBuffer();
        final int[] vo = { 0, 0, 0, 0, 0, 0, 0 };
        final int[] ec = { 0, 0, 0, 0, 0, 0, 0 };
        final int[] io = { 0, 0, 0, 0, 0, 0, 0 };
        final ByteBuffer src = SyntheticSectionData.row(0, 0, vo, ec, io);
        try {
            meta.update(0, 16, 32, 64, 0, MemoryUtilities.memAddress(src), SectionRenderDataUnsafe.Strategy.FULL, QuadPrimitiveType.TRIANGULATED);
            meta.update(1, 16, 32, 64, 0, MemoryUtilities.memAddress(src), SectionRenderDataUnsafe.Strategy.FULL, QuadPrimitiveType.TRIANGULATED);
            final int slotA = meta.lookupSlot(0, 16, 32, 64);
            final int slotB = meta.lookupSlot(1, 16, 32, 64);

            meta.remove(0, 16, 32, 64, 0);
            assertEquals(-1, meta.lookupSlot(0, 16, 32, 64), "pass 0 slot must be gone");
            assertEquals(slotB, meta.lookupSlot(1, 16, 32, 64), "pass 1 slot must survive pass 0 removal");

            meta.update(0, 0, 0, 0, 0, MemoryUtilities.memAddress(src), SectionRenderDataUnsafe.Strategy.FULL, QuadPrimitiveType.TRIANGULATED);
            assertEquals(slotA, meta.lookupSlot(0, 0, 0, 0), "free-list reuse claims pass 0's vacated slot");
        } finally {
            MemoryUtilities.memFree(src);
            meta.shutdown();
        }
    }
}
