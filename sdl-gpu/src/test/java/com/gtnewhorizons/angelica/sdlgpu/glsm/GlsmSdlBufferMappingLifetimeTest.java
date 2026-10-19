package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GL44;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlBufferMappingLifetimeTest {

    private static final int SIZE = 4096;
    private static final int PERSISTENT_STORAGE = GL44.GL_MAP_PERSISTENT_BIT | GL30.GL_MAP_WRITE_BIT | GL44.GL_MAP_COHERENT_BIT | GL44.GL_CLIENT_STORAGE_BIT;
    private static final int PERSISTENT_MAP = GL44.GL_MAP_PERSISTENT_BIT | GL30.GL_MAP_WRITE_BIT | GL44.GL_MAP_COHERENT_BIT;
    private static final int PLAIN_MAP = GL30.GL_MAP_WRITE_BIT | GL30.GL_MAP_INVALIDATE_BUFFER_BIT;

    private static void fill(ByteBuffer buf, int seed) {
        for (int i = 0; i < buf.capacity(); i++) buf.put(i, (byte) (seed * 37 + i * 7 + 1));
    }

    private static void assertPattern(ByteBuffer actual, int seed) {
        for (int i = 0; i < actual.capacity(); i++) {
            assertEquals((byte) (seed * 37 + i * 7 + 1), actual.get(i), "byte " + i + " of pattern " + seed);
        }
    }

    private static ByteBuffer readBack(int target, int size) {
        final ByteBuffer out = MemoryUtil.memAlloc(size);
        GLStateManager.glGetBufferSubData(target, 0, out);
        return out;
    }

    private static ByteBuffer mapPersistent(int target, int id) {
        GLStateManager.glBindBuffer(target, id);
        GLStateManager.glBufferStorage(target, SIZE, PERSISTENT_STORAGE);
        return GLStateManager.glMapBufferRange(target, 0, SIZE, PERSISTENT_MAP);
    }

    @Test
    void bufferDataOnPersistentlyMappedBufferIsRefusedAndMappingSurvives() {
        final int target = GL15.GL_ARRAY_BUFFER;
        final int id = GLStateManager.glGenBuffers();
        ByteBuffer out = null;
        try {
            final ByteBuffer mapped = mapPersistent(target, id);
            assertNotNull(mapped);
            GLStateManager.glBufferData(target, SIZE * 2L, GL15.GL_STATIC_DRAW);
            fill(mapped, 1);
            GLStateManager.glFlushMappedBufferRange(target, 0, SIZE);
            out = readBack(target, SIZE);
            assertPattern(out, 1);
        } finally {
            if (out != null) MemoryUtil.memFree(out);
            GLStateManager.glDeleteBuffers(id);
        }
    }

    @Test
    void persistentMapWithoutPersistentStorageReturnsNull() {
        final int target = GL15.GL_ARRAY_BUFFER;
        final int id = GLStateManager.glGenBuffers();
        try {
            GLStateManager.glBindBuffer(target, id);
            GLStateManager.glBufferData(target, (long) SIZE, GL15.GL_STATIC_DRAW);
            assertNull(GLStateManager.glMapBufferRange(target, 0, SIZE, PERSISTENT_MAP));
        } finally {
            GLStateManager.glDeleteBuffers(id);
        }
    }

    @Test
    void secondPersistentMapReturnsNullAndFirstStillRoundTrips() {
        final int target = GL15.GL_ARRAY_BUFFER;
        final int id = GLStateManager.glGenBuffers();
        ByteBuffer out = null;
        try {
            final ByteBuffer first = mapPersistent(target, id);
            assertNotNull(first);
            assertNull(GLStateManager.glMapBufferRange(target, 0, SIZE, PERSISTENT_MAP));
            fill(first, 2);
            GLStateManager.glFlushMappedBufferRange(target, 0, SIZE);
            out = readBack(target, SIZE);
            assertPattern(out, 2);
        } finally {
            if (out != null) MemoryUtil.memFree(out);
            GLStateManager.glDeleteBuffers(id);
        }
    }

    @Test
    void twoBuffersMappedNonPersistentlyKeepTheirOwnData() {
        final int target = GL15.GL_ARRAY_BUFFER;
        final int a = GLStateManager.glGenBuffers();
        final int b = GLStateManager.glGenBuffers();
        ByteBuffer outA = null;
        ByteBuffer outB = null;
        try {
            GLStateManager.glBindBuffer(target, a);
            GLStateManager.glBufferData(target, SIZE, GL15.GL_DYNAMIC_DRAW);
            GLStateManager.glBindBuffer(target, b);
            GLStateManager.glBufferData(target, SIZE, GL15.GL_DYNAMIC_DRAW);

            GLStateManager.glBindBuffer(target, a);
            final ByteBuffer mapA = GLStateManager.glMapBufferRange(target, 0, SIZE, PLAIN_MAP);
            assertNotNull(mapA);
            GLStateManager.glBindBuffer(target, b);
            final ByteBuffer mapB = GLStateManager.glMapBufferRange(target, 0, SIZE, PLAIN_MAP);
            assertNotNull(mapB);
            fill(mapA, 3);
            fill(mapB, 4);

            GLStateManager.glBindBuffer(target, a);
            assertTrue(GLStateManager.glUnmapBuffer(target));
            GLStateManager.glBindBuffer(target, b);
            assertTrue(GLStateManager.glUnmapBuffer(target));

            GLStateManager.glBindBuffer(target, a);
            outA = readBack(target, SIZE);
            assertPattern(outA, 3);
            GLStateManager.glBindBuffer(target, b);
            outB = readBack(target, SIZE);
            assertPattern(outB, 4);
        } finally {
            if (outA != null) MemoryUtil.memFree(outA);
            if (outB != null) MemoryUtil.memFree(outB);
            GLStateManager.glDeleteBuffers(a);
            GLStateManager.glDeleteBuffers(b);
        }
    }

    @Test
    void unmapOfUnmappedBufferReturnsFalseAndLeavesOtherMappingIntact() {
        final int target = GL15.GL_ARRAY_BUFFER;
        final int mappedId = GLStateManager.glGenBuffers();
        final int idle = GLStateManager.glGenBuffers();
        ByteBuffer out = null;
        try {
            GLStateManager.glBindBuffer(target, mappedId);
            GLStateManager.glBufferData(target, SIZE, GL15.GL_DYNAMIC_DRAW);
            final ByteBuffer map = GLStateManager.glMapBufferRange(target, 0, SIZE, PLAIN_MAP);
            assertNotNull(map);
            fill(map, 5);

            GLStateManager.glBindBuffer(target, idle);
            GLStateManager.glBufferData(target, SIZE, GL15.GL_DYNAMIC_DRAW);
            assertFalse(GLStateManager.glUnmapBuffer(target));

            GLStateManager.glBindBuffer(target, mappedId);
            assertTrue(GLStateManager.glUnmapBuffer(target));
            out = readBack(target, SIZE);
            assertPattern(out, 5);
        } finally {
            if (out != null) MemoryUtil.memFree(out);
            GLStateManager.glDeleteBuffers(mappedId);
            GLStateManager.glDeleteBuffers(idle);
        }
    }

    @Test
    void outOfRangeUniformSubDataIsIgnored() {
        final int ubo = GLStateManager.glGenBuffers();
        final ByteBuffer data = MemoryUtil.memAlloc(256);
        final ByteBuffer big = MemoryUtil.memAlloc(64);
        ByteBuffer out = null;
        try {
            fill(data, 6);
            fill(big, 7);
            GLStateManager.glBindBuffer(GL31.GL_UNIFORM_BUFFER, ubo);
            GLStateManager.glBufferData(GL31.GL_UNIFORM_BUFFER, data, GL15.GL_DYNAMIC_DRAW);
            assertDoesNotThrow(() -> {
                big.clear();
                GLStateManager.glBufferSubData(GL31.GL_UNIFORM_BUFFER, 1024, big);
                big.clear();
                GLStateManager.glBufferSubData(GL31.GL_UNIFORM_BUFFER, 224, big);
                big.clear();
                GLStateManager.glBufferSubData(GL31.GL_UNIFORM_BUFFER, -8, big);
            });
            out = readBack(GL31.GL_UNIFORM_BUFFER, 256);
            assertPattern(out, 6);
        } finally {
            if (out != null) MemoryUtil.memFree(out);
            MemoryUtil.memFree(data);
            MemoryUtil.memFree(big);
            GLStateManager.glDeleteBuffers(ubo);
        }
    }

    @Test
    void uniformBufferMapForReadSeesItsContents() {
        final int ubo = GLStateManager.glGenBuffers();
        final ByteBuffer data = MemoryUtil.memAlloc(256);
        try {
            fill(data, 7);
            GLStateManager.glBindBuffer(GL31.GL_UNIFORM_BUFFER, ubo);
            GLStateManager.glBufferData(GL31.GL_UNIFORM_BUFFER, data, GL15.GL_DYNAMIC_DRAW);
            final ByteBuffer mapped = GLStateManager.glMapBufferRange(GL31.GL_UNIFORM_BUFFER, 0, 256, GL30.GL_MAP_READ_BIT);
            assertNotNull(mapped);
            assertPattern(mapped, 7);
            assertTrue(GLStateManager.glUnmapBuffer(GL31.GL_UNIFORM_BUFFER));
        } finally {
            MemoryUtil.memFree(data);
            GLStateManager.glDeleteBuffers(ubo);
        }
    }

    @Test
    void copyBufferSubDataMovesBytesIntoAndOutOfAUniformBuffer() {
        final int src = GLStateManager.glGenBuffers();
        final int ubo = GLStateManager.glGenBuffers();
        final int dst = GLStateManager.glGenBuffers();
        final ByteBuffer srcData = MemoryUtil.memAlloc(256);
        final ByteBuffer uboData = MemoryUtil.memAlloc(256);
        ByteBuffer outUbo = null;
        ByteBuffer outDst = null;
        try {
            fill(srcData, 5);
            fill(uboData, 6);
            GLStateManager.glBindBuffer(GL31.GL_COPY_READ_BUFFER, src);
            GLStateManager.glBufferData(GL31.GL_COPY_READ_BUFFER, srcData, GL15.GL_STATIC_DRAW);
            GLStateManager.glBindBuffer(GL31.GL_UNIFORM_BUFFER, ubo);
            GLStateManager.glBufferData(GL31.GL_UNIFORM_BUFFER, uboData, GL15.GL_DYNAMIC_DRAW);
            GLStateManager.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER, GL31.GL_UNIFORM_BUFFER, 64, 64, 64);
            outUbo = readBack(GL31.GL_UNIFORM_BUFFER, 256);
            for (int i = 0; i < 256; i++) {
                final boolean copied = i >= 64 && i < 128;
                assertEquals(copied ? srcData.get(i) : uboData.get(i), outUbo.get(i), (copied ? "copied" : "untouched") + " byte " + i);
            }

            GLStateManager.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, dst);
            GLStateManager.glBufferData(GL31.GL_COPY_WRITE_BUFFER, 64L, GL15.GL_STATIC_DRAW);
            GLStateManager.glCopyBufferSubData(GL31.GL_UNIFORM_BUFFER, GL31.GL_COPY_WRITE_BUFFER, 0, 0, 64);
            outDst = readBack(GL31.GL_COPY_WRITE_BUFFER, 64);
            assertPattern(outDst, 6);
        } finally {
            if (outUbo != null) MemoryUtil.memFree(outUbo);
            if (outDst != null) MemoryUtil.memFree(outDst);
            MemoryUtil.memFree(srcData);
            MemoryUtil.memFree(uboData);
            GLStateManager.glDeleteBuffers(src);
            GLStateManager.glDeleteBuffers(ubo);
            GLStateManager.glDeleteBuffers(dst);
        }
    }

    @Test
    void outOfRangeCopyBufferSubDataIsIgnored() {
        final int src = GLStateManager.glGenBuffers();
        final int dst = GLStateManager.glGenBuffers();
        final ByteBuffer data = MemoryUtil.memAlloc(256);
        ByteBuffer outDst = null;
        try {
            fill(data, 6);
            GLStateManager.glBindBuffer(GL31.GL_COPY_READ_BUFFER, src);
            GLStateManager.glBufferData(GL31.GL_COPY_READ_BUFFER, data, GL15.GL_STATIC_DRAW);
            GLStateManager.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, dst);
            data.clear();
            GLStateManager.glBufferData(GL31.GL_COPY_WRITE_BUFFER, data, GL15.GL_STATIC_DRAW);
            GLStateManager.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER, GL31.GL_COPY_WRITE_BUFFER, 200, 0, 128);
            GLStateManager.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER, GL31.GL_COPY_WRITE_BUFFER, 0, 200, 128);
            GLStateManager.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER, GL31.GL_COPY_WRITE_BUFFER, -4, 0, 16);
            outDst = readBack(GL31.GL_COPY_WRITE_BUFFER, 256);
            assertPattern(outDst, 6);
        } finally {
            if (outDst != null) MemoryUtil.memFree(outDst);
            MemoryUtil.memFree(data);
            GLStateManager.glDeleteBuffers(src);
            GLStateManager.glDeleteBuffers(dst);
        }
    }

    @ParameterizedTest(name = "target {0}")
    @ValueSource(ints = {GL43.GL_SHADER_STORAGE_BUFFER, GL31.GL_UNIFORM_BUFFER})
    void outOfRangeClearBufferSubDataIsIgnored(int target) {
        final int clearStart = 64;
        final int clearEnd = 96;
        final int buffer = GLStateManager.glGenBuffers();
        final ByteBuffer data = MemoryUtil.memAlloc(256);
        ByteBuffer out = null;
        try {
            fill(data, 8);
            GLStateManager.glBindBuffer(target, buffer);
            GLStateManager.glBufferData(target, data, GL15.GL_DYNAMIC_DRAW);
            GLStateManager.glClearBufferSubData(target, GL30.GL_R8, clearStart, clearEnd - clearStart, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, null);
            GLStateManager.glClearBufferSubData(target, GL30.GL_R8, 200, 128, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, null);
            GLStateManager.glClearBufferSubData(target, GL30.GL_R8, -4, 16, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, null);
            GLStateManager.glClearBufferSubData(target, GL30.GL_R8, 1024, 16, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, null);
            GlsmSdlHeadlessRig.endFrame();
            GlsmSdlHeadlessRig.beginFrame();
            out = readBack(target, 256);
            for (int i = 0; i < 256; i++) {
                final boolean cleared = i >= clearStart && i < clearEnd;
                assertEquals(cleared ? 0 : data.get(i), out.get(i), cleared ? "in-range clear did not zero byte " + i : "out-of-range clear touched byte " + i);
            }
        } finally {
            if (out != null) MemoryUtil.memFree(out);
            MemoryUtil.memFree(data);
            GLStateManager.glDeleteBuffers(buffer);
        }
    }

    @Test
    void secondBufferStorageIsRefused() {
        final int target = GL15.GL_ARRAY_BUFFER;
        final int id = GLStateManager.glGenBuffers();
        try {
            GLStateManager.glBindBuffer(target, id);
            GLStateManager.glBufferStorage(target, SIZE, PERSISTENT_STORAGE);
            GLStateManager.glBufferStorage(target, SIZE * 2L, PERSISTENT_STORAGE);
            assertEquals(GL11.GL_TRUE, GLStateManager.glGetBufferParameteri(target, GL44.GL_BUFFER_IMMUTABLE_STORAGE));
            assertEquals(SIZE, GLStateManager.glGetBufferParameteri(target, GL15.GL_BUFFER_SIZE));
        } finally {
            GLStateManager.glDeleteBuffers(id);
        }
    }

    @Test
    void namedBufferDataOnImmutableStorageIsRefused() {
        final int target = GL15.GL_ARRAY_BUFFER;
        final int id = GLStateManager.glGenBuffers();
        try {
            GLStateManager.glBindBuffer(target, id);
            GLStateManager.glBufferStorage(target, SIZE, PERSISTENT_STORAGE);
            GLStateManager.glNamedBufferData(id, SIZE * 2L, GL15.GL_STATIC_DRAW);
            assertEquals(SIZE, GLStateManager.glGetBufferParameteri(target, GL15.GL_BUFFER_SIZE));
        } finally {
            GLStateManager.glDeleteBuffers(id);
        }
    }

    @Test
    void bufferStorageWithNullDataMarksBufferImmutable() {
        final int target = GL15.GL_ARRAY_BUFFER;
        final int id = GLStateManager.glGenBuffers();
        try {
            GLStateManager.glBindBuffer(target, id);
            GLStateManager.glBufferStorage(target, (ByteBuffer) null, PERSISTENT_STORAGE);
            assertEquals(GL11.GL_TRUE, GLStateManager.glGetBufferParameteri(target, GL44.GL_BUFFER_IMMUTABLE_STORAGE));
        } finally {
            GLStateManager.glDeleteBuffers(id);
        }
    }

    static Stream<Arguments> plainMapTargets() {
        return Stream.of(
            Arguments.of("GL_ARRAY_BUFFER", GL15.GL_ARRAY_BUFFER),
            Arguments.of("GL_UNIFORM_BUFFER", GL31.GL_UNIFORM_BUFFER));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("plainMapTargets")
    void bufferDataOnMappedBufferUnmapsIt(String name, int target) {
        final int id = GLStateManager.glGenBuffers();
        try {
            GLStateManager.glBindBuffer(target, id);
            GLStateManager.glBufferData(target, SIZE, GL15.GL_DYNAMIC_DRAW);
            assertNotNull(GLStateManager.glMapBufferRange(target, 0, SIZE, PLAIN_MAP));
            GLStateManager.glBufferData(target, SIZE, GL15.GL_DYNAMIC_DRAW);
            assertNotNull(GLStateManager.glMapBufferRange(target, 0, SIZE, PLAIN_MAP));
            assertTrue(GLStateManager.glUnmapBuffer(target));
        } finally {
            GLStateManager.glDeleteBuffers(id);
        }
    }

    @Test
    void outOfRangeFlushOfPersistentMappingIsIgnored() {
        final int target = GL15.GL_ARRAY_BUFFER;
        final int id = GLStateManager.glGenBuffers();
        ByteBuffer out = null;
        try {
            final ByteBuffer mapped = mapPersistent(target, id);
            assertNotNull(mapped);
            GLStateManager.glFlushMappedBufferRange(target, SIZE, 64);
            GLStateManager.glFlushMappedBufferRange(target, SIZE - 16, 64);
            GLStateManager.glFlushMappedBufferRange(target, -8, 16);
            fill(mapped, 9);
            GLStateManager.glFlushMappedBufferRange(target, 0, SIZE);
            out = readBack(target, SIZE);
            assertPattern(out, 9);
        } finally {
            if (out != null) MemoryUtil.memFree(out);
            GLStateManager.glDeleteBuffers(id);
        }
    }

    @Test
    void persistentMappingAtNonZeroOffsetRoundTrips() {
        final int target = GL15.GL_ARRAY_BUFFER;
        final int id = GLStateManager.glGenBuffers();
        final ByteBuffer out = MemoryUtil.memAlloc(SIZE);
        try {
            GLStateManager.glBindBuffer(target, id);
            GLStateManager.glBufferStorage(target, SIZE * 2L, PERSISTENT_STORAGE);
            final ByteBuffer mapped = GLStateManager.glMapBufferRange(target, SIZE, SIZE, PERSISTENT_MAP);
            assertNotNull(mapped);
            fill(mapped, 10);
            GLStateManager.glFlushMappedBufferRange(target, 0, SIZE);
            GLStateManager.glGetBufferSubData(target, SIZE, out);
            assertPattern(out, 10);
        } finally {
            MemoryUtil.memFree(out);
            GLStateManager.glDeleteBuffers(id);
        }
    }

    @Test
    void copyFromPersistentMappingShorterThanBufferReadsGpuContents() {
        final int src = GLStateManager.glGenBuffers();
        final int dst = GLStateManager.glGenBuffers();
        final ByteBuffer data = MemoryUtil.memAlloc(SIZE * 2);
        ByteBuffer out = null;
        try {
            fill(data, 11);
            GLStateManager.glBindBuffer(GL31.GL_COPY_READ_BUFFER, src);
            GLStateManager.glBufferStorage(GL31.GL_COPY_READ_BUFFER, data, PERSISTENT_STORAGE);
            assertNotNull(GLStateManager.glMapBufferRange(GL31.GL_COPY_READ_BUFFER, 0, SIZE, PERSISTENT_MAP));
            GLStateManager.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, dst);
            GLStateManager.glBufferData(GL31.GL_COPY_WRITE_BUFFER, SIZE, GL15.GL_STATIC_DRAW);
            GLStateManager.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER, GL31.GL_COPY_WRITE_BUFFER, SIZE, 0, 256);
            out = readBack(GL31.GL_COPY_WRITE_BUFFER, 256);
            for (int i = 0; i < 256; i++) {
                assertEquals(data.get(SIZE + i), out.get(i), "byte " + i);
            }
        } finally {
            if (out != null) MemoryUtil.memFree(out);
            MemoryUtil.memFree(data);
            GLStateManager.glDeleteBuffers(src);
            GLStateManager.glDeleteBuffers(dst);
        }
    }
}
