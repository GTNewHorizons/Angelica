package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL40;

import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlTextureBindingQueryTest {

    @BeforeAll
    static void boot() {
        GlsmSdlHeadlessRig.boot();
    }

    @Test
    void everyBindingQueryReadsThePerUnitSlot() {
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE3);
        final int tex = GLStateManager.glGenTextures();
        try {
            GLStateManager.glBindTexture(GL12.GL_TEXTURE_3D, tex);
            assertEquals(tex, RENDER_BACKEND.getInteger(GL11.GL_TEXTURE_BINDING_2D), "GL_TEXTURE_BINDING_2D");
            assertEquals(tex, RENDER_BACKEND.getInteger(GL11.GL_TEXTURE_BINDING_1D), "GL_TEXTURE_BINDING_1D");
            assertEquals(tex, RENDER_BACKEND.getInteger(GL12.GL_TEXTURE_BINDING_3D), "GL_TEXTURE_BINDING_3D");
            assertEquals(tex, RENDER_BACKEND.getInteger(GL13.GL_TEXTURE_BINDING_CUBE_MAP), "GL_TEXTURE_BINDING_CUBE_MAP");
            assertEquals(tex, RENDER_BACKEND.getInteger(GL30.GL_TEXTURE_BINDING_1D_ARRAY), "GL_TEXTURE_BINDING_1D_ARRAY");
            assertEquals(tex, RENDER_BACKEND.getInteger(GL30.GL_TEXTURE_BINDING_2D_ARRAY), "GL_TEXTURE_BINDING_2D_ARRAY");
            assertEquals(tex, RENDER_BACKEND.getInteger(GL31.GL_TEXTURE_BINDING_RECTANGLE), "GL_TEXTURE_BINDING_RECTANGLE");
            assertEquals(tex, RENDER_BACKEND.getInteger(GL40.GL_TEXTURE_BINDING_CUBE_MAP_ARRAY), "GL_TEXTURE_BINDING_CUBE_MAP_ARRAY");
        } finally {
            GLStateManager.glBindTexture(GL12.GL_TEXTURE_3D, 0);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glDeleteTextures(tex);
        }
    }
}
