package com.gtnewhorizons.angelica.sdlgpu;

import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL31;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UboShadowSubDataTest {

    private static SDLGPURenderBackend backend;
    private static ResourceManager resourceManager;

    @BeforeAll
    static void createBackend() throws Exception {
        backend = new SDLGPURenderBackend();
        final Field f = SDLGPURenderBackend.class.getDeclaredField("resourceManager");
        f.setAccessible(true);
        resourceManager = (ResourceManager) f.get(backend);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void subDataFromANonZeroPositionLandsAtTheAbsoluteShadowOffset(boolean direct) {
        final int glId = backend.genBuffers();
        backend.bindBuffer(GL31.GL_UNIFORM_BUFFER, glId);
        backend.bufferData(GL31.GL_UNIFORM_BUFFER, 64, GL15.GL_DYNAMIC_DRAW);

        final ByteBuffer payload = direct ? ByteBuffer.allocateDirect(32) : ByteBuffer.allocate(32);
        try {
            for (int i = 16; i < 24; i++) payload.put(i, (byte) (0x10 + (i - 16)));
            payload.position(16).limit(24);

            backend.bufferSubData(GL31.GL_UNIFORM_BUFFER, 40, payload);

            final ByteBuffer shadow = resourceManager.getUboShadow(glId);
            for (int i = 0; i < 8; i++) {
                assertEquals((byte) (0x10 + i), shadow.get(40 + i), "byte " + i + " must land at the absolute shadow offset, not payload.position()+offset");
            }
            assertEquals(16, payload.position(), "bufferSubData must not move the caller's buffer position");
            assertEquals(24, payload.limit(), "bufferSubData must not move the caller's buffer limit");
        } finally {
            backend.bindBuffer(GL31.GL_UNIFORM_BUFFER, 0);
            backend.deleteBuffers(glId);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void eboSubDataCopiesPositionedSourcesExactlyOnce(boolean direct) {
        final int glId = backend.genBuffers();
        backend.bindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, glId);
        final ByteBuffer shadow = resourceManager.getOrAllocEboShadow(glId, 64);
        final ByteBuffer payload = direct ? ByteBuffer.allocateDirect(32) : ByteBuffer.allocate(32);
        try {
            for (int i = 0; i < 32; i++) payload.put(i, (byte) (i + 1));
            payload.position(8).limit(16);
            shadow.position(3);
            backend.bufferSubData(GL15.GL_ELEMENT_ARRAY_BUFFER, 24, payload);
            for (int i = 0; i < 8; i++) assertEquals((byte) (9 + i), shadow.get(24 + i));
            assertEquals(8, payload.position());
            assertEquals(16, payload.limit());
        } finally {
            backend.bindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
            backend.deleteBuffers(glId);
        }
    }

    @Test
    void secondSubDataDoesNotDriftFromAStalePriorShadowPosition() {
        final int glId = backend.genBuffers();
        backend.bindBuffer(GL31.GL_UNIFORM_BUFFER, glId);
        backend.bufferData(GL31.GL_UNIFORM_BUFFER, 64, GL15.GL_DYNAMIC_DRAW);

        final ByteBuffer a = MemoryUtil.memAlloc(8);
        final ByteBuffer b = MemoryUtil.memAlloc(8);
        try {
            for (int i = 0; i < 8; i++) a.put(i, (byte) 1);
            for (int i = 0; i < 8; i++) b.put(i, (byte) 2);

            backend.bufferSubData(GL31.GL_UNIFORM_BUFFER, 0, a);
            backend.bufferSubData(GL31.GL_UNIFORM_BUFFER, 8, b);

            final ByteBuffer shadow = resourceManager.getUboShadow(glId);
            for (int i = 0; i < 8; i++) assertEquals((byte) 1, shadow.get(i));
            for (int i = 0; i < 8; i++) assertEquals((byte) 2, shadow.get(8 + i));
        } finally {
            MemoryUtil.memFree(a);
            MemoryUtil.memFree(b);
        }
    }
}
