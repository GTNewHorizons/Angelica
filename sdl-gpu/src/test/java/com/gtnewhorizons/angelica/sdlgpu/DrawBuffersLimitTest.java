package com.gtnewhorizons.angelica.sdlgpu;

import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.resource.FboState;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL30;

import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class DrawBuffersLimitTest {

    private static final int BOUND_FBO = 901;
    private static final int UNBOUND_FBO = 902;

    private static SDLGPURenderBackend backend;
    private static ResourceManager resourceManager;

    private ContextState st;
    private int savedFbo;

    @BeforeAll
    static void createBackend() {
        backend = new SDLGPURenderBackend();
        resourceManager = Reflect.get(backend, "resourceManager");
    }

    @BeforeEach
    void setUp() {
        st = SdlTestRig.contextState();
        savedFbo = st.boundFboId;
        st.boundFboId = BOUND_FBO;
    }

    @AfterEach
    void tearDown() {
        st.boundFboId = savedFbo;
        resourceManager.deleteFbo(BOUND_FBO);
        resourceManager.deleteFbo(UNBOUND_FBO);
    }

    private static IntBuffer attachments(int count) {
        final int[] bufs = new int[count];
        for (int i = 0; i < count; i++) bufs[i] = GL30.GL_COLOR_ATTACHMENT0 + i;
        return IntBuffer.wrap(bufs);
    }

    @Test
    void namedDrawBuffersOverTheLimitAreIgnored() {
        final FboState fbo = resourceManager.createFbo(UNBOUND_FBO);
        backend.namedFramebufferDrawBuffers(UNBOUND_FBO, attachments(2));
        final int[] before = fbo.drawBuffers;
        assertArrayEquals(new int[]{0, 1}, before, "a legal count must apply, or the refusal proves nothing");

        backend.namedFramebufferDrawBuffers(UNBOUND_FBO, attachments(ContextState.MAX_COLOR_ATTACHMENTS + 1));
        assertSame(before, fbo.drawBuffers, "a count above MAX_DRAW_BUFFERS must leave the draw buffers untouched");
        assertArrayEquals(new int[]{0, 1}, fbo.drawBuffers);

        backend.namedFramebufferDrawBuffers(UNBOUND_FBO, attachments(ContextState.MAX_COLOR_ATTACHMENTS));
        assertEquals(ContextState.MAX_COLOR_ATTACHMENTS, fbo.drawBuffers.length, "exactly the limit is legal");
    }

    @Test
    void boundDrawBuffersOverTheLimitAreIgnored() {
        final FboState fbo = resourceManager.createFbo(BOUND_FBO);
        assertSame(fbo, resourceManager.getFbo(st.boundFboId));
        final int[] before = fbo.drawBuffers;

        backend.drawBuffers(attachments(ContextState.MAX_COLOR_ATTACHMENTS + 1));
        assertSame(before, fbo.drawBuffers, "a count above MAX_DRAW_BUFFERS must leave the draw buffers untouched");
        assertArrayEquals(new int[]{0}, fbo.drawBuffers);
        assertNull(fbo.cachedColorFormats, "an ignored call must not reach the pipeline color formats");
    }
}
