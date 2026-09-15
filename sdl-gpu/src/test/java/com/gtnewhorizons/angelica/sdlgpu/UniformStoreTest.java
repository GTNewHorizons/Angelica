package com.gtnewhorizons.angelica.sdlgpu;

import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import com.gtnewhorizons.angelica.sdlgpu.shader.UniformStaging;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UniformStoreTest {

    private static final int VEC4 = 0;
    private static final int OUT_OF_RANGE = 7;
    private static final float[] VALUES = {5f, 6f, 7f, 8f};

    private static SDLGPURenderBackend backend;

    private ContextState st;
    private ShaderManager.ProgramObject prog;
    private ShaderManager.ProgramObject savedProgramObj;
    private int savedProgram;
    private IntBuffer ints;

    @BeforeAll
    static void createBackend() {
        backend = new SDLGPURenderBackend();
    }

    @BeforeEach
    void setUp() {
        st = SdlTestRig.contextState();
        savedProgramObj = st.boundProgramObj;
        savedProgram = st.boundProgram;
        prog = new ShaderManager.ProgramObject();
        prog.linked = true;
        prog.vertexUboSize = 16;
        prog.nextUniformLocation = 1;
        prog.vertexMemberInfo.put(VEC4, new ShaderManager.UniformMemberInfo(0, 16, 0, true, 4, 1, 0, 1));
        prog.buildUniformSlotArrays();
        st.boundProgramObj = prog;
        st.boundProgram = 1;
        ints = MemoryUtil.memAllocInt(4);
        ints.put(0, 1).put(1, 2).put(2, 3).put(3, 4);
    }

    @AfterEach
    void tearDown() {
        MemoryUtil.memFree(ints);
        st.releaseUniformStaging(prog);
        st.boundProgramObj = savedProgramObj;
        st.boundProgram = savedProgram;
    }

    private void writeAll(int location) {
        backend.uniform4f(location, 1f, 2f, 3f, 4f);
        backend.uniform4fv(location, VALUES);
        backend.uniform4i(location, 1, 2, 3, 4);
        backend.uniform4iv(location, ints);
    }

    private void dropRound() {
        st.boundProgramObj = null;
        writeAll(VEC4);
        st.boundProgramObj = prog;
        writeAll(OUT_OF_RANGE);
    }

    private void assertNothingStaged() {
        final UniformStaging us = st.uniformStaging(prog);
        assertNull(us.uniformDataBySlot[VEC4]);
        assertEquals(0L, us.uniformValueHashBySlot[VEC4]);
        assertFalse(us.vsUniformDirty);
    }

    private float[] staged(int location) {
        return st.uniformStaging(prog).uniformDataBySlot[location];
    }

    @Test
    void noProgramBoundStoresNothing() {
        st.boundProgramObj = null;
        assertDoesNotThrow(() -> writeAll(VEC4));
        st.boundProgramObj = prog;
        assertNothingStaged();
    }

    @Test
    void outOfRangeLocationStoresNothing() {
        assertEquals(1, prog.uniformSlotCount);
        assertDoesNotThrow(() -> writeAll(OUT_OF_RANGE));
        assertNothingStaged();
    }

    @Test
    void droppedStoresDoNotAllocate() {
        SdlAsserts.assertAllocationFree(this::dropRound, "dropped uniform stores");
        assertNothingStaged();
    }

    @Test
    void boundProgramWriteStillStores() {
        backend.uniform4f(VEC4, 1f, 2f, 3f, 4f);
        assertArrayEquals(new float[]{1f, 2f, 3f, 4f}, staged(VEC4));

        backend.uniform4fv(VEC4, VALUES);
        assertArrayEquals(VALUES, staged(VEC4));

        backend.uniform4iv(VEC4, ints);
        final float[] stagedValues = staged(VEC4);
        for (int i = 0; i < 4; i++) {
            assertEquals(i + 1, Float.floatToRawIntBits(stagedValues[i]));
        }
        assertTrue(st.uniformStaging(prog).vsUniformDirty);
    }

    @Test
    void vectorWriteCopiesTheCallerArrayAndReusesTheSlot() {
        final float[] first = {5f, 6f, 7f, 8f};
        backend.uniform4fv(VEC4, first);
        final float[] slot = staged(VEC4);
        assertNotSame(first, slot, "the staging must not hold the caller's array");
        assertArrayEquals(new float[]{5f, 6f, 7f, 8f}, slot);

        final float[] second = {9f, 10f, 11f, 12f};
        backend.uniform4fv(VEC4, second);
        assertSame(slot, staged(VEC4), "same-length writes must reuse the slot array");
        assertNotSame(second, staged(VEC4));
        assertArrayEquals(new float[]{9f, 10f, 11f, 12f}, slot);

        final float[] equalValues = second.clone();
        backend.uniform4fv(VEC4, equalValues);
        assertSame(slot, staged(VEC4), "an equal-valued caller array must not replace the slot array");

        backend.uniform4f(VEC4, 1f, 2f, 3f, 4f);
        assertArrayEquals(new float[]{5f, 6f, 7f, 8f}, first);
        assertArrayEquals(new float[]{9f, 10f, 11f, 12f}, second);
        assertArrayEquals(new float[]{9f, 10f, 11f, 12f}, equalValues);
        assertArrayEquals(new float[]{1f, 2f, 3f, 4f}, slot);
    }
}
