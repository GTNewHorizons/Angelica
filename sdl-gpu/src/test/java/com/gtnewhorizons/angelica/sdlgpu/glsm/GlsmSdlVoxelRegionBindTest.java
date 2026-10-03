package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL43;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlVoxelRegionBindTest {

    private static final int BOUND_SLOT = 5;

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
