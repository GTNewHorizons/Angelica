package com.gtnewhorizons.angelica.sdlgpu.resource;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL44;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BufferParamsTest {

    private static int unmapped(int pname, long size, int glUsage, int storageFlags) {
        return BufferParams.resolve(pname, size, glUsage, storageFlags, false, 0, 0, 0);
    }

    @Test
    void untouchedBuffer_reportsGlInitialValues() {
        final int storage = BufferParams.MUTABLE_STORE;
        assertEquals(0, unmapped(GL15.GL_BUFFER_SIZE, 0, GL15.GL_STATIC_DRAW, storage));
        assertEquals(GL15.GL_STATIC_DRAW, unmapped(GL15.GL_BUFFER_USAGE, 0, GL15.GL_STATIC_DRAW, storage));
        assertEquals(GL11.GL_FALSE, unmapped(GL44.GL_BUFFER_IMMUTABLE_STORAGE, 0, GL15.GL_STATIC_DRAW, storage));
        assertEquals(0, unmapped(GL44.GL_BUFFER_STORAGE_FLAGS, 0, GL15.GL_STATIC_DRAW, storage));
        assertEquals(GL11.GL_FALSE, unmapped(GL15.GL_BUFFER_MAPPED, 0, GL15.GL_STATIC_DRAW, storage));
        assertEquals(GL15.GL_READ_WRITE, unmapped(GL15.GL_BUFFER_ACCESS, 0, GL15.GL_STATIC_DRAW, storage));
        assertEquals(0, unmapped(GL30.GL_BUFFER_ACCESS_FLAGS, 0, GL15.GL_STATIC_DRAW, storage));
        assertEquals(0, unmapped(GL30.GL_BUFFER_MAP_OFFSET, 0, GL15.GL_STATIC_DRAW, storage));
        assertEquals(0, unmapped(GL30.GL_BUFFER_MAP_LENGTH, 0, GL15.GL_STATIC_DRAW, storage));
    }

    @Test
    void bufferDataStore_reportsExactSizeAndUsageHint() {
        assertEquals(1000, unmapped(GL15.GL_BUFFER_SIZE, 1000, GL15.GL_STREAM_DRAW, BufferParams.MUTABLE_STORE));
        assertEquals(GL15.GL_STREAM_DRAW, unmapped(GL15.GL_BUFFER_USAGE, 1000, GL15.GL_STREAM_DRAW, BufferParams.MUTABLE_STORE));
    }

    @Test
    void bufferStorageStore_isImmutableWithFlagsAndDynamicDraw() {
        final int flags = GL44.GL_MAP_PERSISTENT_BIT | GL30.GL_MAP_WRITE_BIT | GL44.GL_MAP_COHERENT_BIT;
        assertEquals(GL11.GL_TRUE, unmapped(GL44.GL_BUFFER_IMMUTABLE_STORAGE, 64, GL15.GL_DYNAMIC_DRAW, flags));
        assertEquals(flags, unmapped(GL44.GL_BUFFER_STORAGE_FLAGS, 64, GL15.GL_DYNAMIC_DRAW, flags));
        assertEquals(GL15.GL_DYNAMIC_DRAW, unmapped(GL15.GL_BUFFER_USAGE, 64, GL15.GL_DYNAMIC_DRAW, flags));
    }

    @Test
    void bufferStorageWithZeroFlags_isStillImmutable() {
        assertEquals(GL11.GL_TRUE, unmapped(GL44.GL_BUFFER_IMMUTABLE_STORAGE, 64, GL15.GL_DYNAMIC_DRAW, 0));
        assertEquals(0, unmapped(GL44.GL_BUFFER_STORAGE_FLAGS, 64, GL15.GL_DYNAMIC_DRAW, 0));
    }

    @Test
    void mappedBuffer_reportsRangeAndAccess() {
        final int access = GL30.GL_MAP_WRITE_BIT | GL30.GL_MAP_FLUSH_EXPLICIT_BIT;
        assertEquals(GL11.GL_TRUE, BufferParams.resolve(GL15.GL_BUFFER_MAPPED, 512, GL15.GL_STATIC_DRAW, -1, true, access, 128, 64));
        assertEquals(access, BufferParams.resolve(GL30.GL_BUFFER_ACCESS_FLAGS, 512, GL15.GL_STATIC_DRAW, -1, true, access, 128, 64));
        assertEquals(128, BufferParams.resolve(GL30.GL_BUFFER_MAP_OFFSET, 512, GL15.GL_STATIC_DRAW, -1, true, access, 128, 64));
        assertEquals(64, BufferParams.resolve(GL30.GL_BUFFER_MAP_LENGTH, 512, GL15.GL_STATIC_DRAW, -1, true, access, 128, 64));
        assertEquals(GL15.GL_WRITE_ONLY, BufferParams.resolve(GL15.GL_BUFFER_ACCESS, 512, GL15.GL_STATIC_DRAW, -1, true, access, 128, 64));
    }

    @Test
    void unmappedBuffer_ignoresLeftoverMapFields() {
        final int access = GL30.GL_MAP_READ_BIT;
        assertEquals(0, BufferParams.resolve(GL30.GL_BUFFER_ACCESS_FLAGS, 512, GL15.GL_STATIC_DRAW, -1, false, access, 128, 64));
        assertEquals(0, BufferParams.resolve(GL30.GL_BUFFER_MAP_OFFSET, 512, GL15.GL_STATIC_DRAW, -1, false, access, 128, 64));
        assertEquals(0, BufferParams.resolve(GL30.GL_BUFFER_MAP_LENGTH, 512, GL15.GL_STATIC_DRAW, -1, false, access, 128, 64));
        assertEquals(GL15.GL_READ_WRITE, BufferParams.resolve(GL15.GL_BUFFER_ACCESS, 512, GL15.GL_STATIC_DRAW, -1, false, access, 128, 64));
    }

    @Test
    void accessEnumToBits_doesNotAliasOntoInvalidateBit() {
        assertEquals(GL30.GL_MAP_READ_BIT, BufferParams.accessEnumToBits(GL15.GL_READ_ONLY));
        assertEquals(GL30.GL_MAP_WRITE_BIT, BufferParams.accessEnumToBits(GL15.GL_WRITE_ONLY));
        assertEquals(GL30.GL_MAP_READ_BIT | GL30.GL_MAP_WRITE_BIT,
            BufferParams.accessEnumToBits(GL15.GL_READ_WRITE));

        for (int access : new int[] { GL15.GL_READ_ONLY, GL15.GL_WRITE_ONLY, GL15.GL_READ_WRITE }) {
            assertTrue((access & GL30.GL_MAP_INVALIDATE_BUFFER_BIT) != 0, "precondition: raw enum aliases onto the invalidate bit");
            assertEquals(0, BufferParams.accessEnumToBits(access) & GL30.GL_MAP_INVALIDATE_BUFFER_BIT);
            assertEquals(0, BufferParams.accessEnumToBits(access) & GL44.GL_MAP_PERSISTENT_BIT);
        }
    }

    @Test
    void accessBitsToEnum_followsMapBufferRangeTable() {
        assertEquals(GL15.GL_READ_ONLY, BufferParams.accessBitsToEnum(GL30.GL_MAP_READ_BIT));
        assertEquals(GL15.GL_WRITE_ONLY, BufferParams.accessBitsToEnum(GL30.GL_MAP_WRITE_BIT));
        assertEquals(GL15.GL_READ_WRITE, BufferParams.accessBitsToEnum(GL30.GL_MAP_READ_BIT | GL30.GL_MAP_WRITE_BIT));
        assertEquals(GL15.GL_READ_WRITE, BufferParams.accessBitsToEnum(0));
        assertEquals(GL15.GL_WRITE_ONLY, BufferParams.accessBitsToEnum(GL30.GL_MAP_WRITE_BIT | GL30.GL_MAP_UNSYNCHRONIZED_BIT));
    }

    @Test
    void unknownPname_isReportedAndResolvesToZero() {
        assertTrue(BufferParams.isUnknownPname(GL11.GL_TEXTURE_WIDTH));
        assertEquals(0, unmapped(GL11.GL_TEXTURE_WIDTH, 1000, GL15.GL_STREAM_DRAW, 0));

        assertFalse(BufferParams.isUnknownPname(GL15.GL_BUFFER_SIZE));
        assertFalse(BufferParams.isUnknownPname(GL15.GL_BUFFER_USAGE));
        assertFalse(BufferParams.isUnknownPname(GL15.GL_BUFFER_ACCESS));
        assertFalse(BufferParams.isUnknownPname(GL15.GL_BUFFER_MAPPED));
        assertFalse(BufferParams.isUnknownPname(GL30.GL_BUFFER_ACCESS_FLAGS));
        assertFalse(BufferParams.isUnknownPname(GL30.GL_BUFFER_MAP_OFFSET));
        assertFalse(BufferParams.isUnknownPname(GL30.GL_BUFFER_MAP_LENGTH));
        assertFalse(BufferParams.isUnknownPname(GL44.GL_BUFFER_IMMUTABLE_STORAGE));
        assertFalse(BufferParams.isUnknownPname(GL44.GL_BUFFER_STORAGE_FLAGS));
    }
}
