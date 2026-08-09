package com.gtnewhorizons.angelica.sdlgpu.resource;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertThrows;

class CopyMappedFromDataBoundsTest {

    @Test
    void throws_whenSizeExceedsDataRemaining() {
        final ByteBuffer mapped = MemoryUtil.memAlloc(64);
        final ByteBuffer data = MemoryUtil.memAlloc(32);
        try {
            assertThrows(IllegalStateException.class,
                () -> ResourceManager.copyMappedFromData(mapped, data, 64, 1));
        } finally {
            MemoryUtil.memFree(mapped);
            MemoryUtil.memFree(data);
        }
    }

    @Test
    void throws_whenSizeExceedsMappedCapacityFromPosition() {
        final ByteBuffer mapped = MemoryUtil.memAlloc(64);
        final ByteBuffer data = MemoryUtil.memAlloc(128);
        mapped.position(48);
        try {
            assertThrows(IllegalStateException.class,
                () -> ResourceManager.copyMappedFromData(mapped, data, 32, 1));
        } finally {
            MemoryUtil.memFree(mapped);
            MemoryUtil.memFree(data);
        }
    }

    @Test
    void throws_onNegativeSize() {
        final ByteBuffer mapped = MemoryUtil.memAlloc(16);
        final ByteBuffer data = MemoryUtil.memAlloc(16);
        try {
            assertThrows(IllegalStateException.class,
                () -> ResourceManager.copyMappedFromData(mapped, data, -1, 1));
        } finally {
            MemoryUtil.memFree(mapped);
            MemoryUtil.memFree(data);
        }
    }

    @Test
    void copies_whenWithinBounds() {
        final ByteBuffer mapped = MemoryUtil.memAlloc(64);
        final ByteBuffer data = MemoryUtil.memAlloc(64);
        for (int i = 0; i < 32; i++) data.put(i, (byte) (i + 1));
        try {
            ResourceManager.copyMappedFromData(mapped, data, 32, 1);
            for (int i = 0; i < 32; i++) {
                if (mapped.get(i) != (byte) (i + 1)) {
                    throw new AssertionError("byte " + i + " mismatch");
                }
            }
        } finally {
            MemoryUtil.memFree(mapped);
            MemoryUtil.memFree(data);
        }
    }
}
