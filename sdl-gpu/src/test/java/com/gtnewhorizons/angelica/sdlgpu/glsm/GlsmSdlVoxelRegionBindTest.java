package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL43;

import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlVoxelRegionBindTest {

    private static final int BOUND_SLOT = 5;
    private static final int EMPTY_SLOT = 6;
    private static final int UNLINKED_PROGRAM = Integer.MAX_VALUE;

    @BeforeAll
    static void boot() {
        GlsmSdlHeadlessRig.boot();
    }

    private static int glsmGenericSsbo() {
        return GlsmSdlHeadlessRig.glsmBufferBinding(GL43.GL_SHADER_STORAGE_BUFFER);
    }

    private static void unbind(int slot) {
        GLStateManager.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, slot, 0);
    }

    @Test
    void glsmBindBufferBaseReachesBothCaches() {
        final ContextState st = SdlTestRig.contextState();
        final int buf = GLStateManager.glGenBuffers();
        try {
            GLStateManager.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, BOUND_SLOT, buf);
            assertEquals(buf, st.boundSsboByIndex[BOUND_SLOT], "SDL indexed SSBO slot");
            assertEquals(buf, glsmGenericSsbo(), "GLSM generic SSBO binding");
        } finally {
            unbind(BOUND_SLOT);
            GLStateManager.glDeleteBuffers(buf);
        }
    }

    @Test
    void regionHookRefusesWithoutProgramOrBoundSlotAndNeverRebinds() {
        final ContextState st = SdlTestRig.contextState();
        final int savedProgram = st.boundProgram;
        final int buf = GLStateManager.glGenBuffers();
        try {
            unbind(EMPTY_SLOT);
            GLStateManager.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, BOUND_SLOT, buf);

            st.boundProgram = 0;
            assertFalse(RENDER_BACKEND.bindVoxelizationRegion(BOUND_SLOT, 0L, 0f, 0f, 0f), "no program bound");
            assertEquals(buf, st.boundSsboByIndex[BOUND_SLOT], "refusal must leave the slot alone");

            st.boundProgram = UNLINKED_PROGRAM;
            assertFalse(RENDER_BACKEND.bindVoxelizationRegion(EMPTY_SLOT, 0L, 0f, 0f, 0f), "slot has no buffer");
            assertEquals(0, st.boundSsboByIndex[EMPTY_SLOT], "the hook must not bind a buffer itself");

            assertTrue(RENDER_BACKEND.bindVoxelizationRegion(BOUND_SLOT, 0L, 1f, 2f, 3f), "a GLSM-bound slot with a program must be accepted, or the refusals prove nothing");
            assertEquals(buf, st.boundSsboByIndex[BOUND_SLOT]);
            assertEquals(buf, glsmGenericSsbo(), "GLSM and SDL must still agree after the hook");
        } finally {
            st.boundProgram = savedProgram;
            unbind(BOUND_SLOT);
            GLStateManager.glDeleteBuffers(buf);
        }
    }

    @Test
    void deletingTheRegionBufferClearsTheGlsmGenericBinding() {
        final int buf = GLStateManager.glGenBuffers();
        try {
            GLStateManager.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, BOUND_SLOT, buf);
            assertEquals(buf, glsmGenericSsbo());
            GLStateManager.glDeleteBuffers(buf);
            assertEquals(0, glsmGenericSsbo(), "delete must see the tracked SSBO binding");
        } finally {
            unbind(BOUND_SLOT);
        }
    }
}
