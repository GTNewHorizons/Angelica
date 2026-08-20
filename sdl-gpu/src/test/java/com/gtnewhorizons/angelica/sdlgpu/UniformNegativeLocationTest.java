package com.gtnewhorizons.angelica.sdlgpu;

import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.pipeline.PipelineApplier;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import com.gtnewhorizons.angelica.sdlgpu.shader.UniformStaging;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UniformNegativeLocationTest {

    private PipelineApplier applier;
    private ContextState st;
    private ShaderManager.ProgramObject prog;
    private UniformStaging us;

    @BeforeEach
    void setUp() {
        applier = new PipelineApplier(null, null, null, null, null, null, null, null, null);
        st = new ContextState();
        prog = new ShaderManager.ProgramObject();
        prog.linked = true;
        prog.vertexUboSize = 128;
        prog.nextUniformLocation = 2;
        prog.vertexMemberInfo.put(0, new ShaderManager.UniformMemberInfo(0, 64, 0, true, 4, 4, 0, 1));
        prog.vertexMemberInfo.put(1, new ShaderManager.UniformMemberInfo(64, 16, 0, true, 4, 1, 0, 1));
        prog.buildUniformSlotArrays();
        st.boundProgramObj = prog;
        st.boundProgram = 1;
        us = st.uniformStaging(prog);
    }

    @AfterEach
    void tearDown() {
        st.releaseUniformStaging(prog);
    }

    private static void clearApplier(SDLGPURenderBackend backend) throws Exception {
        final Field f = SDLGPURenderBackend.class.getDeclaredField("pipelineApplier");
        f.setAccessible(true);
        f.set(backend, null);
        assertNull(f.get(backend), "test rig failed to null the applier");
    }

    @Test
    void scalarAndVectorEntryPointsNeverReachTheApplier() throws Exception {
        final SDLGPURenderBackend backend = new SDLGPURenderBackend();
        clearApplier(backend);
        SdlTestRig.contextState().boundProgramObj = null;

        final IntBuffer iv = MemoryUtil.memAllocInt(4);
        try {
            iv.put(0, 1).put(1, 2).put(2, 3).put(3, 4);
            assertDoesNotThrow(() -> {
                backend.uniform1i(-1, 0);
                backend.uniform1f(-1, 1f);
                backend.uniform2f(-1, 1f, 2f);
                backend.uniform2i(-1, 1, 2);
                backend.uniform3f(-1, 1f, 2f, 3f);
                backend.uniform3i(-1, 1, 2, 3);
                backend.uniform4f(-1, 1f, 2f, 3f, 4f);
                backend.uniform4i(-1, 1, 2, 3, 4);
                backend.uniform1iv(-1, iv);
                backend.uniform2iv(-1, iv);
                backend.uniform3iv(-1, iv);
                backend.uniform4iv(-1, iv);
            });
            assertThrows(NullPointerException.class, () -> backend.uniform4f(0, 1f, 2f, 3f, 4f), "a valid location must still reach the applier, or the -1 assertions prove nothing");
        } finally {
            MemoryUtil.memFree(iv);
        }
    }

    @Test
    void negativeLocationLeavesStagingAndSourceBufferUntouched() {
        final FloatBuffer fb = MemoryUtil.memAllocFloat(16);
        try {
            for (int i = 0; i < 16; i++) fb.put(i, i + 1f);

            applier.storeMatrix(st, -1, false, fb, 4);
            applier.storeMatrix(st, -1, false, fb, 3);
            applier.storeMatrix(st, -1, true, fb, 2);
            applier.putUniformFv(st, -1, fb);
            applier.putUniform(st, -1, new float[]{1f, 2f, 3f, 4f});

            assertFalse(us.vsUniformDirty);
            assertFalse(us.fsUniformDirty);
            for (int slot = 0; slot < prog.uniformSlotCount; slot++) {
                assertNull(us.uniformDataBySlot[slot], "slot " + slot + " was staged");
                assertEquals(0L, us.uniformValueHashBySlot[slot], "slot " + slot + " hash was written");
            }
            for (ContextState.UniformBlockState block : st.uniformBlocks) {
                assertFalse(block.dirty);
            }
            assertEquals(0, fb.position());
            assertEquals(16, fb.limit());

            applier.storeMatrix(st, 0, false, fb, 4);
            assertTrue(us.vsUniformDirty, "location 0 must still write, or the -1 assertions prove nothing");
            assertEquals(1f, us.vsUniformFb.get(0));
        } finally {
            MemoryUtil.memFree(fb);
        }
    }
}
