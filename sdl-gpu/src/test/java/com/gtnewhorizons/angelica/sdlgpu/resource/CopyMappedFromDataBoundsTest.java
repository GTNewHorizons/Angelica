package com.gtnewhorizons.angelica.sdlgpu.resource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CopyMappedFromDataBoundsTest {

    static Stream<Arguments> outOfBounds() {
        return Stream.of(
            Arguments.of("size exceeds data remaining", 64, 0, 32, 64),
            Arguments.of("size exceeds mapped capacity from its position", 64, 48, 128, 32),
            Arguments.of("negative size", 16, 0, 16, -1));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("outOfBounds")
    void throws_whenOutOfBounds(String name, int mappedCapacity, int mappedPosition, int dataCapacity, int size) {
        final ByteBuffer mapped = MemoryUtil.memAlloc(mappedCapacity);
        final ByteBuffer data = MemoryUtil.memAlloc(dataCapacity);
        mapped.position(mappedPosition);
        try {
            assertThrows(IllegalStateException.class,
                () -> ResourceManager.copyMappedFromData(mapped, data, size, 1));
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
                assertEquals((byte) (i + 1), mapped.get(i), "byte " + i);
            }
        } finally {
            MemoryUtil.memFree(mapped);
            MemoryUtil.memFree(data);
        }
    }
}
