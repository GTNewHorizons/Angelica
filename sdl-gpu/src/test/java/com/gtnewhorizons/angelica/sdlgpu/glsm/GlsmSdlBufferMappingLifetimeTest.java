package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLSMBuffers;
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
        GLSMBuffers.glGetBufferSubData(target, 0, out);
        return out;
    }

    private static ByteBuffer mapPersistent(int target, int id) {
        GLSMBuffers.glBindBuffer(target, id);
        GLSMBuffers.glBufferStorage(target, SIZE, PERSISTENT_STORAGE);
        return GLSMBuffers.glMapBufferRange(target, 0, SIZE, PERSISTENT_MAP);
    }

    @Test
    void bufferDataOnPersistentlyMappedBufferIsRefusedAndMappingSurvives() {
        final int target = GL15.GL_ARRAY_BUFFER;
        final int id = GLSMBuffers.glGenBuffers();
        ByteBuffer out = null;
        try {
            final ByteBuffer mapped = mapPersistent(target, id);
            assertNotNull(mapped);
            GLSMBuffers.glBufferData(target, SIZE * 2L, GL15.GL_STATIC_DRAW);
            fill(mapped, 1);
            GLSMBuffers.glFlushMappedBufferRange(target, 0, SIZE);
            out = readBack(target, SIZE);
            assertPattern(out, 1);
        } finally {
            if (out != null) MemoryUtil.memFree(out);
            GLSMBuffers.glDeleteBuffers(id);
        }
    }

    @Test
    void persistentMapWithoutPersistentStorageReturnsNull() {
        final int target = GL15.GL_ARRAY_BUFFER;
        final int id = GLSMBuffers.glGenBuffers();
        try {
            GLSMBuffers.glBindBuffer(target, id);
            GLSMBuffers.glBufferData(target, (long) SIZE, GL15.GL_STATIC_DRAW);
            assertNull(GLSMBuffers.glMapBufferRange(target, 0, SIZE, PERSISTENT_MAP));
        } finally {
            GLSMBuffers.glDeleteBuffers(id);
        }
    }

    @Test
    void secondPersistentMapReturnsNullAndFirstStillRoundTrips() {
        final int target = GL15.GL_ARRAY_BUFFER;
        final int id = GLSMBuffers.glGenBuffers();
        ByteBuffer out = null;
        try {
            final ByteBuffer first = mapPersistent(target, id);
            assertNotNull(first);
            assertNull(GLSMBuffers.glMapBufferRange(target, 0, SIZE, PERSISTENT_MAP));
            fill(first, 2);
            GLSMBuffers.glFlushMappedBufferRange(target, 0, SIZE);
            out = readBack(target, SIZE);
            assertPattern(out, 2);
        } finally {
            if (out != null) MemoryUtil.memFree(out);
            GLSMBuffers.glDeleteBuffers(id);
        }
    }

    @Test
    void twoBuffersMappedNonPersistentlyKeepTheirOwnData() {
        final int target = GL15.GL_ARRAY_BUFFER;
        final int a = GLSMBuffers.glGenBuffers();
        final int b = GLSMBuffers.glGenBuffers();
        ByteBuffer outA = null;
        ByteBuffer outB = null;
        try {
            GLSMBuffers.glBindBuffer(target, a);
            GLSMBuffers.glBufferData(target, SIZE, GL15.GL_DYNAMIC_DRAW);
            GLSMBuffers.glBindBuffer(target, b);
            GLSMBuffers.glBufferData(target, SIZE, GL15.GL_DYNAMIC_DRAW);

            GLSMBuffers.glBindBuffer(target, a);
            final ByteBuffer mapA = GLSMBuffers.glMapBufferRange(target, 0, SIZE, PLAIN_MAP);
            assertNotNull(mapA);
            GLSMBuffers.glBindBuffer(target, b);
            final ByteBuffer mapB = GLSMBuffers.glMapBufferRange(target, 0, SIZE, PLAIN_MAP);
            assertNotNull(mapB);
            fill(mapA, 3);
            fill(mapB, 4);

            GLSMBuffers.glBindBuffer(target, a);
            assertTrue(GLSMBuffers.glUnmapBuffer(target));
            GLSMBuffers.glBindBuffer(target, b);
            assertTrue(GLSMBuffers.glUnmapBuffer(target));

            GLSMBuffers.glBindBuffer(target, a);
            outA = readBack(target, SIZE);
            assertPattern(outA, 3);
            GLSMBuffers.glBindBuffer(target, b);
            outB = readBack(target, SIZE);
            assertPattern(outB, 4);
        } finally {
            if (outA != null) MemoryUtil.memFree(outA);
            if (outB != null) MemoryUtil.memFree(outB);
            GLSMBuffers.glDeleteBuffers(a);
            GLSMBuffers.glDeleteBuffers(b);
        }
    }

    @Test
    void unmapOfUnmappedBufferReturnsFalseAndLeavesOtherMappingIntact() {
        final int target = GL15.GL_ARRAY_BUFFER;
        final int mappedId = GLSMBuffers.glGenBuffers();
        final int idle = GLSMBuffers.glGenBuffers();
        ByteBuffer out = null;
        try {
            GLSMBuffers.glBindBuffer(target, mappedId);
            GLSMBuffers.glBufferData(target, SIZE, GL15.GL_DYNAMIC_DRAW);
            final ByteBuffer map = GLSMBuffers.glMapBufferRange(target, 0, SIZE, PLAIN_MAP);
            assertNotNull(map);
            fill(map, 5);

            GLSMBuffers.glBindBuffer(target, idle);
            GLSMBuffers.glBufferData(target, SIZE, GL15.GL_DYNAMIC_DRAW);
            assertFalse(GLSMBuffers.glUnmapBuffer(target));

            GLSMBuffers.glBindBuffer(target, mappedId);
            assertTrue(GLSMBuffers.glUnmapBuffer(target));
            out = readBack(target, SIZE);
            assertPattern(out, 5);
        } finally {
            if (out != null) MemoryUtil.memFree(out);
            GLSMBuffers.glDeleteBuffers(mappedId);
            GLSMBuffers.glDeleteBuffers(idle);
        }
    }

    @Test
    void outOfRangeUniformSubDataIsIgnored() {
        final int ubo = GLSMBuffers.glGenBuffers();
        final ByteBuffer data = MemoryUtil.memAlloc(256);
        final ByteBuffer big = MemoryUtil.memAlloc(64);
        ByteBuffer out = null;
        try {
            fill(data, 6);
            fill(big, 7);
            GLSMBuffers.glBindBuffer(GL31.GL_UNIFORM_BUFFER, ubo);
            GLSMBuffers.glBufferData(GL31.GL_UNIFORM_BUFFER, data, GL15.GL_DYNAMIC_DRAW);
            assertDoesNotThrow(() -> {
                big.clear();
                GLSMBuffers.glBufferSubData(GL31.GL_UNIFORM_BUFFER, 1024, big);
                big.clear();
                GLSMBuffers.glBufferSubData(GL31.GL_UNIFORM_BUFFER, 224, big);
                big.clear();
                GLSMBuffers.glBufferSubData(GL31.GL_UNIFORM_BUFFER, -8, big);
            });
            out = readBack(GL31.GL_UNIFORM_BUFFER, 256);
            assertPattern(out, 6);
        } finally {
            if (out != null) MemoryUtil.memFree(out);
            MemoryUtil.memFree(data);
            MemoryUtil.memFree(big);
            GLSMBuffers.glDeleteBuffers(ubo);
        }
    }

    @Test
    void uniformBufferMapForReadSeesItsContents() {
        final int ubo = GLSMBuffers.glGenBuffers();
        final ByteBuffer data = MemoryUtil.memAlloc(256);
        try {
            fill(data, 7);
            GLSMBuffers.glBindBuffer(GL31.GL_UNIFORM_BUFFER, ubo);
            GLSMBuffers.glBufferData(GL31.GL_UNIFORM_BUFFER, data, GL15.GL_DYNAMIC_DRAW);
            final ByteBuffer mapped = GLSMBuffers.glMapBufferRange(GL31.GL_UNIFORM_BUFFER, 0, 256, GL30.GL_MAP_READ_BIT);
            assertNotNull(mapped);
            assertPattern(mapped, 7);
            assertTrue(GLSMBuffers.glUnmapBuffer(GL31.GL_UNIFORM_BUFFER));
        } finally {
            MemoryUtil.memFree(data);
            GLSMBuffers.glDeleteBuffers(ubo);
        }
    }

    @Test
    void copyBufferSubDataMovesBytesIntoAndOutOfAUniformBuffer() {
        final int src = GLSMBuffers.glGenBuffers();
        final int ubo = GLSMBuffers.glGenBuffers();
        final int dst = GLSMBuffers.glGenBuffers();
        final ByteBuffer srcData = MemoryUtil.memAlloc(256);
        final ByteBuffer uboData = MemoryUtil.memAlloc(256);
        ByteBuffer outUbo = null;
        ByteBuffer outDst = null;
        try {
            fill(srcData, 5);
            fill(uboData, 6);
            GLSMBuffers.glBindBuffer(GL31.GL_COPY_READ_BUFFER, src);
            GLSMBuffers.glBufferData(GL31.GL_COPY_READ_BUFFER, srcData, GL15.GL_STATIC_DRAW);
            GLSMBuffers.glBindBuffer(GL31.GL_UNIFORM_BUFFER, ubo);
            GLSMBuffers.glBufferData(GL31.GL_UNIFORM_BUFFER, uboData, GL15.GL_DYNAMIC_DRAW);
            GLSMBuffers.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER, GL31.GL_UNIFORM_BUFFER, 64, 64, 64);
            outUbo = readBack(GL31.GL_UNIFORM_BUFFER, 256);
            for (int i = 0; i < 256; i++) {
                final boolean copied = i >= 64 && i < 128;
                assertEquals(copied ? srcData.get(i) : uboData.get(i), outUbo.get(i), (copied ? "copied" : "untouched") + " byte " + i);
            }

            GLSMBuffers.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, dst);
            GLSMBuffers.glBufferData(GL31.GL_COPY_WRITE_BUFFER, 64L, GL15.GL_STATIC_DRAW);
            GLSMBuffers.glCopyBufferSubData(GL31.GL_UNIFORM_BUFFER, GL31.GL_COPY_WRITE_BUFFER, 0, 0, 64);
            outDst = readBack(GL31.GL_COPY_WRITE_BUFFER, 64);
            assertPattern(outDst, 6);
        } finally {
            if (outUbo != null) MemoryUtil.memFree(outUbo);
            if (outDst != null) MemoryUtil.memFree(outDst);
            MemoryUtil.memFree(srcData);
            MemoryUtil.memFree(uboData);
            GLSMBuffers.glDeleteBuffers(src);
            GLSMBuffers.glDeleteBuffers(ubo);
            GLSMBuffers.glDeleteBuffers(dst);
        }
    }

    @Test
    void outOfRangeCopyBufferSubDataIsIgnored() {
        final int src = GLSMBuffers.glGenBuffers();
        final int dst = GLSMBuffers.glGenBuffers();
        final ByteBuffer data = MemoryUtil.memAlloc(256);
        ByteBuffer outDst = null;
        try {
            fill(data, 6);
            GLSMBuffers.glBindBuffer(GL31.GL_COPY_READ_BUFFER, src);
            GLSMBuffers.glBufferData(GL31.GL_COPY_READ_BUFFER, data, GL15.GL_STATIC_DRAW);
            GLSMBuffers.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, dst);
            data.clear();
            GLSMBuffers.glBufferData(GL31.GL_COPY_WRITE_BUFFER, data, GL15.GL_STATIC_DRAW);
            GLSMBuffers.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER, GL31.GL_COPY_WRITE_BUFFER, 200, 0, 128);
            GLSMBuffers.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER, GL31.GL_COPY_WRITE_BUFFER, 0, 200, 128);
            GLSMBuffers.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER, GL31.GL_COPY_WRITE_BUFFER, -4, 0, 16);
            outDst = readBack(GL31.GL_COPY_WRITE_BUFFER, 256);
            assertPattern(outDst, 6);
        } finally {
            if (outDst != null) MemoryUtil.memFree(outDst);
            MemoryUtil.memFree(data);
            GLSMBuffers.glDeleteBuffers(src);
            GLSMBuffers.glDeleteBuffers(dst);
        }
    }

    @ParameterizedTest(name = "target {0}")
    @ValueSource(ints = {GL43.GL_SHADER_STORAGE_BUFFER, GL31.GL_UNIFORM_BUFFER})
    void outOfRangeClearBufferSubDataIsIgnored(int target) {
        final int clearStart = 64;
        final int clearEnd = 96;
        final int buffer = GLSMBuffers.glGenBuffers();
        final ByteBuffer data = MemoryUtil.memAlloc(256);
        ByteBuffer out = null;
        try {
            fill(data, 8);
            GLSMBuffers.glBindBuffer(target, buffer);
            GLSMBuffers.glBufferData(target, data, GL15.GL_DYNAMIC_DRAW);
            GLSMBuffers.glClearBufferSubData(target, GL30.GL_R8, clearStart, clearEnd - clearStart, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, null);
            GLSMBuffers.glClearBufferSubData(target, GL30.GL_R8, 200, 128, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, null);
            GLSMBuffers.glClearBufferSubData(target, GL30.GL_R8, -4, 16, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, null);
            GLSMBuffers.glClearBufferSubData(target, GL30.GL_R8, 1024, 16, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, null);
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
            GLSMBuffers.glDeleteBuffers(buffer);
        }
    }

    @Test
    void secondBufferStorageIsRefused() {
        final int target = GL15.GL_ARRAY_BUFFER;
        final int id = GLSMBuffers.glGenBuffers();
        try {
            GLSMBuffers.glBindBuffer(target, id);
            GLSMBuffers.glBufferStorage(target, SIZE, PERSISTENT_STORAGE);
            GLSMBuffers.glBufferStorage(target, SIZE * 2L, PERSISTENT_STORAGE);
            assertEquals(GL11.GL_TRUE, GLSMBuffers.glGetBufferParameteri(target, GL44.GL_BUFFER_IMMUTABLE_STORAGE));
            assertEquals(SIZE, GLSMBuffers.glGetBufferParameteri(target, GL15.GL_BUFFER_SIZE));
        } finally {
            GLSMBuffers.glDeleteBuffers(id);
        }
    }

    @Test
    void namedBufferDataOnImmutableStorageIsRefused() {
        final int target = GL15.GL_ARRAY_BUFFER;
        final int id = GLSMBuffers.glGenBuffers();
        try {
            GLSMBuffers.glBindBuffer(target, id);
            GLSMBuffers.glBufferStorage(target, SIZE, PERSISTENT_STORAGE);
            GLSMBuffers.glNamedBufferData(id, SIZE * 2L, GL15.GL_STATIC_DRAW);
            assertEquals(SIZE, GLSMBuffers.glGetBufferParameteri(target, GL15.GL_BUFFER_SIZE));
        } finally {
            GLSMBuffers.glDeleteBuffers(id);
        }
    }

    @Test
    void bufferStorageWithNullDataMarksBufferImmutable() {
        final int target = GL15.GL_ARRAY_BUFFER;
        final int id = GLSMBuffers.glGenBuffers();
        try {
            GLSMBuffers.glBindBuffer(target, id);
            GLSMBuffers.glBufferStorage(target, (ByteBuffer) null, PERSISTENT_STORAGE);
            assertEquals(GL11.GL_TRUE, GLSMBuffers.glGetBufferParameteri(target, GL44.GL_BUFFER_IMMUTABLE_STORAGE));
        } finally {
            GLSMBuffers.glDeleteBuffers(id);
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
        final int id = GLSMBuffers.glGenBuffers();
        try {
            GLSMBuffers.glBindBuffer(target, id);
            GLSMBuffers.glBufferData(target, SIZE, GL15.GL_DYNAMIC_DRAW);
            assertNotNull(GLSMBuffers.glMapBufferRange(target, 0, SIZE, PLAIN_MAP));
            GLSMBuffers.glBufferData(target, SIZE, GL15.GL_DYNAMIC_DRAW);
            assertNotNull(GLSMBuffers.glMapBufferRange(target, 0, SIZE, PLAIN_MAP));
            assertTrue(GLSMBuffers.glUnmapBuffer(target));
        } finally {
            GLSMBuffers.glDeleteBuffers(id);
        }
    }

    @Test
    void outOfRangeFlushOfPersistentMappingIsIgnored() {
        final int target = GL15.GL_ARRAY_BUFFER;
        final int id = GLSMBuffers.glGenBuffers();
        ByteBuffer out = null;
        try {
            final ByteBuffer mapped = mapPersistent(target, id);
            assertNotNull(mapped);
            GLSMBuffers.glFlushMappedBufferRange(target, SIZE, 64);
            GLSMBuffers.glFlushMappedBufferRange(target, SIZE - 16, 64);
            GLSMBuffers.glFlushMappedBufferRange(target, -8, 16);
            fill(mapped, 9);
            GLSMBuffers.glFlushMappedBufferRange(target, 0, SIZE);
            out = readBack(target, SIZE);
            assertPattern(out, 9);
        } finally {
            if (out != null) MemoryUtil.memFree(out);
            GLSMBuffers.glDeleteBuffers(id);
        }
    }

    @Test
    void persistentMappingAtNonZeroOffsetRoundTrips() {
        final int target = GL15.GL_ARRAY_BUFFER;
        final int id = GLSMBuffers.glGenBuffers();
        final ByteBuffer out = MemoryUtil.memAlloc(SIZE);
        try {
            GLSMBuffers.glBindBuffer(target, id);
            GLSMBuffers.glBufferStorage(target, SIZE * 2L, PERSISTENT_STORAGE);
            final ByteBuffer mapped = GLSMBuffers.glMapBufferRange(target, SIZE, SIZE, PERSISTENT_MAP);
            assertNotNull(mapped);
            fill(mapped, 10);
            GLSMBuffers.glFlushMappedBufferRange(target, 0, SIZE);
            GLSMBuffers.glGetBufferSubData(target, SIZE, out);
            assertPattern(out, 10);
        } finally {
            MemoryUtil.memFree(out);
            GLSMBuffers.glDeleteBuffers(id);
        }
    }

    @Test
    void copyFromPersistentMappingShorterThanBufferReadsGpuContents() {
        final int src = GLSMBuffers.glGenBuffers();
        final int dst = GLSMBuffers.glGenBuffers();
        final ByteBuffer data = MemoryUtil.memAlloc(SIZE * 2);
        ByteBuffer out = null;
        try {
            fill(data, 11);
            GLSMBuffers.glBindBuffer(GL31.GL_COPY_READ_BUFFER, src);
            GLSMBuffers.glBufferStorage(GL31.GL_COPY_READ_BUFFER, data, PERSISTENT_STORAGE);
            assertNotNull(GLSMBuffers.glMapBufferRange(GL31.GL_COPY_READ_BUFFER, 0, SIZE, PERSISTENT_MAP));
            GLSMBuffers.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, dst);
            GLSMBuffers.glBufferData(GL31.GL_COPY_WRITE_BUFFER, SIZE, GL15.GL_STATIC_DRAW);
            GLSMBuffers.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER, GL31.GL_COPY_WRITE_BUFFER, SIZE, 0, 256);
            out = readBack(GL31.GL_COPY_WRITE_BUFFER, 256);
            for (int i = 0; i < 256; i++) {
                assertEquals(data.get(SIZE + i), out.get(i), "byte " + i);
            }
        } finally {
            if (out != null) MemoryUtil.memFree(out);
            MemoryUtil.memFree(data);
            GLSMBuffers.glDeleteBuffers(src);
            GLSMBuffers.glDeleteBuffers(dst);
        }
    }
}
