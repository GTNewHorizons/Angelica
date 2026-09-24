package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.sdlgpu.SdlAsserts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AbsoluteCopyRegionTest {

    private static ByteBuffer buffer(boolean direct, boolean sliced) {
        final ByteBuffer buffer = direct ? ByteBuffer.allocateDirect(64) : ByteBuffer.allocate(64);
        if (!sliced) return buffer;
        buffer.position(7).limit(55);
        return buffer.slice();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15})
    void copiesAbsoluteIndicesAcrossBufferKinds(int flags) {
        ByteBuffer src = buffer((flags & 1) != 0, (flags & 4) != 0);
        final ByteBuffer dst = buffer((flags & 2) != 0, (flags & 4) != 0);
        for (int i = 0; i < src.capacity(); i++) src.put(i, (byte) (i * 3 + 1));
        for (int i = 0; i < dst.capacity(); i++) dst.put(i, (byte) 0x7F);
        if ((flags & 8) != 0) src = src.asReadOnlyBuffer();
        src.position(13).limit(22);
        dst.position(23).limit(28);

        ByteRegionCopy.copyByteRegion(src, 8, dst, 16, 6);

        for (int i = 0; i < dst.limit(); i++) {
            assertEquals(i >= 16 && i < 22 ? src.get(i - 8) : (byte) 0x7F, dst.get(i));
        }
        assertEquals(13, src.position());
        assertEquals(22, src.limit());
        assertEquals(23, dst.position());
        assertEquals(28, dst.limit());
        ByteRegionCopy.copyByteRegion(src, src.limit(), dst, dst.limit(), 0);
        assertEquals(13, src.position());
        assertEquals(22, src.limit());
        assertEquals(23, dst.position());
        assertEquals(28, dst.limit());
    }

    @Test
    void persistentCopyUsesAbsoluteOffsetsWithPositionedStaging() {
        final ByteBuffer src = ByteBuffer.allocateDirect(32);
        final ByteBuffer dst = ByteBuffer.allocateDirect(32);
        for (int i = 0; i < 32; i++) src.put(i, (byte) i);
        src.position(7).limit(24);
        dst.position(9).limit(28);
        PersistentBufferSync.mirrorPersistentCopy(src, 4, dst, 16, 8);
        for (int i = 0; i < 8; i++) assertEquals((byte) (4 + i), dst.get(16 + i));
        assertEquals(7, src.position());
        assertEquals(24, src.limit());
        assertEquals(9, dst.position());
        assertEquals(28, dst.limit());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 8, 9, 10, 11})
    void warmedCopiesDoNotAllocate(int flags) {
        final ByteBuffer writable = buffer((flags & 1) != 0, true);
        final ByteBuffer src = (flags & 8) != 0 ? writable.asReadOnlyBuffer() : writable;
        final ByteBuffer dst = buffer((flags & 2) != 0, true);
        src.position(5).limit(40);
        dst.position(7).limit(40);
        SdlAsserts.assertAllocationFree(() -> ByteRegionCopy.copyByteRegion(src, 2, dst, 4, 32), "absolute buffer copy");
    }
}
