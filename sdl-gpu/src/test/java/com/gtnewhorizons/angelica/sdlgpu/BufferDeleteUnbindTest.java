package com.gtnewhorizons.angelica.sdlgpu;

import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;

import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BufferDeleteUnbindTest {

    private static final int BUFFER = 7001;
    private static final int OTHER = 7002;
    private static final int UBO_INDEX = 3;
    private static final int SSBO_INDEX = 5;
    private static final int VERTEX_BINDING = 2;
    private static final int ATTRIB = 4;

    private static SDLGPURenderBackend backend;

    private ContextState st;

    @BeforeAll
    static void createBackend() {
        backend = new SDLGPURenderBackend();
    }

    @BeforeEach
    void setUp() {
        st = SdlTestRig.contextState();
        backend.bindBufferRange(GL31.GL_UNIFORM_BUFFER, UBO_INDEX, BUFFER, 16L, 64L);
        backend.bindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, SSBO_INDEX, BUFFER);
        backend.bindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER, BUFFER);
        backend.bindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, BUFFER);
        backend.bindVertexBuffer(VERTEX_BINDING, BUFFER, 8L, 12);
        backend.bindBuffer(GL15.GL_ARRAY_BUFFER, BUFFER);
        backend.bindBuffer(GL43.GL_DISPATCH_INDIRECT_BUFFER, BUFFER);
        backend.bindBuffer(GL31.GL_COPY_READ_BUFFER, BUFFER);
        backend.bindBuffer(GL31.GL_COPY_WRITE_BUFFER, BUFFER);
        backend.bindBuffer(GL30.GL_PIXEL_PACK_BUFFER, BUFFER);
        backend.bindBuffer(GL30.GL_PIXEL_UNPACK_BUFFER, BUFFER);
        st.currentVao.attribVBO[ATTRIB] = BUFFER;
    }

    @AfterEach
    void tearDown() {
        backend.bindBufferBase(GL31.GL_UNIFORM_BUFFER, UBO_INDEX, 0);
        backend.bindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, SSBO_INDEX, 0);
        backend.bindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER, 0);
        backend.bindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        backend.bindVertexBuffer(VERTEX_BINDING, 0, 0L, 0);
        backend.bindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        backend.bindBuffer(GL43.GL_DISPATCH_INDIRECT_BUFFER, 0);
        backend.bindBuffer(GL31.GL_COPY_READ_BUFFER, 0);
        backend.bindBuffer(GL31.GL_COPY_WRITE_BUFFER, 0);
        backend.bindBuffer(GL30.GL_PIXEL_PACK_BUFFER, 0);
        backend.bindBuffer(GL30.GL_PIXEL_UNPACK_BUFFER, 0);
        st.currentVao.attribVBO[ATTRIB] = 0;
    }

    private void assertAllCleared() {
        assertEquals(0, st.boundUboByIndex[UBO_INDEX]);
        assertEquals(0, st.uboRangeOffset[UBO_INDEX]);
        assertEquals(0, st.uboRangeSize[UBO_INDEX]);
        assertEquals(0, st.boundUniformBuffer);
        assertEquals(0, st.boundSsboByIndex[SSBO_INDEX]);
        assertEquals(0, st.boundSSBO);
        assertEquals(0, st.boundIndirectBuffer);
        assertEquals(0, st.currentVao.elementBuffer);
        assertEquals(0, st.currentVao.bindingBuffer[VERTEX_BINDING]);
        assertEquals(0, st.currentVao.attribVBO[ATTRIB]);
        assertEquals(0, st.boundArrayBuffer);
        assertEquals(0, st.boundDispatchIndirectBuffer);
        assertEquals(0, st.boundCopyReadBuffer);
        assertEquals(0, st.boundCopyWriteBuffer);
        assertEquals(0, st.boundPixelPackBuffer);
        assertEquals(0, st.boundPixelUnpackBuffer);
    }

    private void assertAllBound() {
        assertEquals(BUFFER, st.boundUboByIndex[UBO_INDEX]);
        assertEquals(16, st.uboRangeOffset[UBO_INDEX]);
        assertEquals(64, st.uboRangeSize[UBO_INDEX]);
        assertEquals(BUFFER, st.boundUniformBuffer);
        assertEquals(BUFFER, st.boundSsboByIndex[SSBO_INDEX]);
        assertEquals(BUFFER, st.boundSSBO);
        assertEquals(BUFFER, st.boundIndirectBuffer);
        assertEquals(BUFFER, st.currentVao.elementBuffer);
        assertEquals(BUFFER, st.currentVao.bindingBuffer[VERTEX_BINDING]);
        assertEquals(BUFFER, st.currentVao.attribVBO[ATTRIB]);
        assertEquals(BUFFER, st.boundArrayBuffer);
        assertEquals(BUFFER, st.boundDispatchIndirectBuffer);
        assertEquals(BUFFER, st.boundCopyReadBuffer);
        assertEquals(BUFFER, st.boundCopyWriteBuffer);
        assertEquals(BUFFER, st.boundPixelPackBuffer);
        assertEquals(BUFFER, st.boundPixelUnpackBuffer);
    }

    @Test
    void deleteClearsEveryBindingOfTheDeletedBuffer() {
        assertAllBound();
        final int uboGen = st.uboRangeGen;
        final int ssboGen = st.ssboBindGen;
        final long attribGen = st.attribStateGen;

        backend.deleteBuffers(BUFFER);

        assertAllCleared();
        assertEquals(8L, st.currentVao.bindingOffset[VERTEX_BINDING]);
        assertEquals(12, st.currentVao.bindingStride[VERTEX_BINDING]);
        assertNotEquals(uboGen, st.uboRangeGen);
        assertNotEquals(ssboGen, st.ssboBindGen);
        assertNotEquals(attribGen, st.attribStateGen);
    }

    @Test
    void intBufferDeleteClearsEveryBindingOfTheDeletedBuffer() {
        final int uboGen = st.uboRangeGen;
        final int ssboGen = st.ssboBindGen;
        final long attribGen = st.attribStateGen;
        final IntBuffer ids = MemoryUtil.memAllocInt(2);
        try {
            ids.put(0, OTHER).put(1, BUFFER);
            backend.deleteBuffers(ids);
        } finally {
            MemoryUtil.memFree(ids);
        }

        assertAllCleared();
        assertNotEquals(uboGen, st.uboRangeGen);
        assertNotEquals(ssboGen, st.ssboBindGen);
        assertNotEquals(attribGen, st.attribStateGen);
    }

    @Test
    void deletingAnUnrelatedBufferChangesNothing() {
        final int uboGen = st.uboRangeGen;
        final int ssboGen = st.ssboBindGen;
        final long attribGen = st.attribStateGen;

        backend.deleteBuffers(OTHER);
        backend.deleteBuffers(0);

        assertAllBound();
        assertEquals(uboGen, st.uboRangeGen);
        assertEquals(ssboGen, st.ssboBindGen);
        assertEquals(attribGen, st.attribStateGen);
    }
}
