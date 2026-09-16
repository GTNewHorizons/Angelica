package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.PixelUnpackState;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.sdlgpu.SDLGPULWJGLService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlLWJGLServiceRoutingTest {

    private static final int UBO_INDEX = 3;

    private static SDLGPULWJGLService svc;

    @BeforeAll
    static void boot() {
        GlsmSdlHeadlessRig.boot();
        svc = SDLGPULWJGLService.create();
    }

    private static Object glsmContext() {
        return Reflect.getStatic(GLStateManager.class, "primaryContext");
    }

    private static int glsmUnpackAlignment() {
        final PixelUnpackState state = Reflect.get(glsmContext(), "pixelUnpackState");
        return state.alignment();
    }

    @Test
    void deleteBuffersClearsTheGlsmArrayBufferBinding() {
        final int buffer = svc.glGenBuffers();
        svc.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        svc.glBufferData(GL15.GL_ARRAY_BUFFER, 64L, GL15.GL_STATIC_DRAW);
        assertEquals(buffer, GLStateManager.getBoundVBO());

        svc.glDeleteBuffers(buffer);

        assertEquals(0, GLStateManager.getBoundVBO());
    }

    @Test
    void bindBufferBaseUpdatesTheGlsmGenericBinding() {
        final int buffer = svc.glGenBuffers();
        svc.glBindBuffer(GL31.GL_UNIFORM_BUFFER, buffer);
        svc.glBufferData(GL31.GL_UNIFORM_BUFFER, 64L, GL15.GL_DYNAMIC_DRAW);
        svc.glBindBuffer(GL31.GL_UNIFORM_BUFFER, 0);
        assertEquals(0, GlsmSdlHeadlessRig.glsmBufferBinding(GL31.GL_UNIFORM_BUFFER));

        try {
            svc.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, UBO_INDEX, buffer);
            assertEquals(buffer, GlsmSdlHeadlessRig.glsmBufferBinding(GL31.GL_UNIFORM_BUFFER));
        } finally {
            svc.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, UBO_INDEX, 0);
            svc.glDeleteBuffers(buffer);
        }
    }

    @Test
    void clearColorUpdatesTheGlsmCache() {
        svc.glClearColor(0.25f, 0.5f, 0.75f, 1.0f);

        assertEquals(0.25f, GLStateManager.getClearColor().getRed());
        assertEquals(0.5f, GLStateManager.getClearColor().getGreen());
        assertEquals(0.75f, GLStateManager.getClearColor().getBlue());
    }

    @Test
    void pixelStoreiUpdatesTheGlsmUnpackState() {
        assertNotEquals(1, glsmUnpackAlignment());
        try {
            svc.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            assertEquals(1, glsmUnpackAlignment());
        } finally {
            svc.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
        }
        assertEquals(4, glsmUnpackAlignment());
    }

    @Test
    void deleteFramebuffersClearsTheGlsmDrawFramebuffer() {
        final int framebuffer = svc.glGenFramebuffers();
        try {
            svc.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            assertEquals(framebuffer, GLStateManager.getDrawFramebuffer());

            svc.glDeleteFramebuffers(framebuffer);

            assertEquals(0, GLStateManager.getDrawFramebuffer());
        } finally {
            GlsmSdlHeadlessRig.bindTarget();
        }
    }
}
