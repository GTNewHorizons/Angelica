package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLSMBuffers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlEmptyBufferTransferTest {

    private int id;
    private ByteBuffer empty;

    @BeforeEach
    void setUp() {
        id = GLSMBuffers.glGenBuffers();
        empty = MemoryUtil.memAlloc(16);
        empty.limit(0);
    }

    @AfterEach
    void tearDown() {
        MemoryUtil.memFree(empty);
        GLSMBuffers.glDeleteBuffers(id);
    }

    @Test
    void emptyBufferDataReplacesTheStoreWithoutACopy() {
        final int target = GL40.GL_DRAW_INDIRECT_BUFFER;
        GLSMBuffers.glBindBuffer(target, id);
        GLSMBuffers.glBufferData(target, 256L, GL15.GL_STATIC_DRAW);
        GlsmSdlHeadlessRig.resourceManager().flushUploadArena();
        GLSMBuffers.glBufferData(target, empty, GL15.GL_STATIC_DRAW);
        assertEquals(0L, GlsmSdlHeadlessRig.frameManager().frame().arenaCopyPass);
        assertEquals(0, GLSMBuffers.glGetBufferParameteri(target, GL15.GL_BUFFER_SIZE));
    }

    @Test
    void emptyStorageBufferSubDataIssuesNoCopy() {
        final int target = GL43.GL_SHADER_STORAGE_BUFFER;
        GLSMBuffers.glBindBuffer(target, id);
        GLSMBuffers.glBufferData(target, 256L, GL15.GL_DYNAMIC_DRAW);
        final int before = GlsmSdlHeadlessRig.frameManager().frame().pendingUploadCommands;
        GLSMBuffers.glBufferSubData(target, 0, empty);
        assertEquals(before, GlsmSdlHeadlessRig.frameManager().frame().pendingUploadCommands);
    }

    @Test
    void emptyGetBufferSubDataDoesNotReachTheDevice() {
        final int target = GL43.GL_SHADER_STORAGE_BUFFER;
        GLSMBuffers.glBindBuffer(target, id);
        GLSMBuffers.glBufferData(target, 256L, GL15.GL_DYNAMIC_DRAW);
        GLSMBuffers.glGetBufferSubData(target, 0, empty);
    }
}
