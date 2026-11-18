package com.gtnewhorizons.angelica.lwjgl3;

import com.gtnewhorizons.angelica.glsm.GLSMCurrentAttribs;
import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMEnable;
import com.gtnewhorizons.angelica.glsm.GLSMFramebuffer;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.GLSMVertexInput;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMInitConfig;
import com.gtnewhorizons.angelica.glsm.testutil.ClipTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLInit;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("gles")
@DisabledOnOs(OS.MAC)
class GlesSmokeTest {

    private static final int SIZE = 64;
    private static int fbo;

    @BeforeAll
    static void boot() {
        Configuration.OPENGL_EXPLICIT_INIT.set(true);
        assertTrue(SDLInit.SDL_Init(SDLInit.SDL_INIT_VIDEO), () -> "SDL_Init failed: " + SDLError.SDL_GetError());
        SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_CONTEXT_PROFILE_MASK, SDLVideo.SDL_GL_CONTEXT_PROFILE_ES);
        SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_CONTEXT_MAJOR_VERSION, 3);
        SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_CONTEXT_MINOR_VERSION, 2);
        SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_DEPTH_SIZE, 24);
        SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_STENCIL_SIZE, 8);
        final long window = SDLVideo.SDL_CreateWindow("glesSmoke", SIZE, SIZE, SDLVideo.SDL_WINDOW_OPENGL | SDLVideo.SDL_WINDOW_HIDDEN);
        assertNotEquals(0L, window, () -> "SDL_CreateWindow failed: " + SDLError.SDL_GetError());
        final long context = SDLVideo.SDL_GL_CreateContext(window);
        assertNotEquals(0L, context, () -> "SDL_GL_CreateContext failed: " + SDLError.SDL_GetError());
        assertTrue(SDLVideo.SDL_GL_MakeCurrent(window, context), () -> "SDL_GL_MakeCurrent failed: " + SDLError.SDL_GetError());

        GL.create(SDLVideo::SDL_GL_GetProcAddress);
        GL.createCapabilities();
        // LWJGL's GL_CONTEXT_PROFILE_MASK probe in createCapabilities leaves INVALID_ENUM on an ES context.
        while (GL11C.glGetError() != GL11C.GL_NO_ERROR) {}

        final String version = GL11C.glGetString(GL11C.GL_VERSION);
        assertTrue(version != null && version.startsWith("OpenGL ES 3."), () -> "not an ES 3.x context: " + version);
        assertTrue(BackendManager.RENDER_BACKEND instanceof Lwjgl3GLRenderBackend, () -> "selected backend is " + BackendManager.RENDER_BACKEND.getName());

        GLStateManager.initialize(GLSMInitConfig.builder().displaySize(SIZE, SIZE).build());
        GLStateManager.setRunningSplash(false);
        GLStateManager.markSplashComplete("glesSmoke");

        assertTrue(RenderSystem.isGLES());
        assertTrue(RenderSystem.supportsSamplerObjects());
        fbo = createTarget();
        assertEquals(GL11C.GL_NO_ERROR, GL11C.glGetError());
    }

    @Test
    void samplerLifecycle() {
        final int sampler = RenderSystem.genSampler();
        assertNotEquals(0, sampler);
        RenderSystem.samplerParameteri(sampler, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR);
        RenderSystem.samplerParameterf(sampler, GL12C.GL_TEXTURE_MAX_LOD, 4.0f);
        RenderSystem.bindSamplerToUnit(0, sampler);
        assertEquals(sampler, GL11C.glGetInteger(GL33C.GL_SAMPLER_BINDING));
        assertTrue(BackendManager.RENDER_BACKEND.isSampler(sampler));

        RenderSystem.destroySampler(sampler);
        assertFalse(BackendManager.RENDER_BACKEND.isSampler(sampler));
        assertEquals(GL11C.GL_NO_ERROR, GL11C.glGetError());
    }

    @Test
    void vertexAttribDivisor() {
        final int vao = GLSMVertexInput.glGenVertexArrays();
        GLSMVertexInput.glBindVertexArray(vao);
        GLSMVertexInput.glVertexAttribDivisor(5, 1);
        assertEquals(1, GL20C.glGetVertexAttribi(5, GL33C.GL_VERTEX_ATTRIB_ARRAY_DIVISOR));
        GLSMVertexInput.glBindVertexArray(0);
        assertEquals(GL11C.GL_NO_ERROR, GL11C.glGetError());
    }

    @Test
    void ffpQuadRenders() {
        bindClearedTarget();
        GLSMDraws.glBegin(GL11C.GL_QUADS);
        GLSMCurrentAttribs.glColor4f(0.0f, 1.0f, 0.0f, 1.0f);
        GLSMDraws.glVertex3f(-1.0f, -1.0f, 0.0f);
        GLSMDraws.glVertex3f(1.0f, -1.0f, 0.0f);
        GLSMDraws.glVertex3f(1.0f, 1.0f, 0.0f);
        GLSMDraws.glVertex3f(-1.0f, 1.0f, 0.0f);
        GLSMDraws.glEnd();

        assertEquals(0xFF00FF00, readCenterPixel());
        assertEquals(GL11C.GL_NO_ERROR, GL11C.glGetError());
    }

    @Test
    void ffpTexturedQuadRenders() {
        final int texel = 0xFF2060A0;
        final int texture = GLSMTextures.glGenTextures();
        GLSMTextures.glBindTexture(GL11C.GL_TEXTURE_2D, texture);
        GLSMTextures.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
        GLSMTextures.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
        final IntBuffer pixels = MemoryUtil.memAllocInt(16);
        try {
            for (int i = 0; i < 16; i++) pixels.put(i, texel);
            GLSMTextures.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RGBA, 4, 4, 0, GL12C.GL_BGRA, GL12C.GL_UNSIGNED_INT_8_8_8_8_REV, pixels);
        } finally {
            MemoryUtil.memFree(pixels);
        }

        bindClearedTarget();
        GLSMTextures.glBindTexture(GL11C.GL_TEXTURE_2D, texture);
        GLSMEnable.glEnable(GL11C.GL_TEXTURE_2D);
        GLSMDraws.glBegin(GL11C.GL_QUADS);
        GLSMCurrentAttribs.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GLSMCurrentAttribs.glTexCoord2f(0.0f, 0.0f);
        GLSMDraws.glVertex3f(-1.0f, -1.0f, 0.0f);
        GLSMCurrentAttribs.glTexCoord2f(1.0f, 0.0f);
        GLSMDraws.glVertex3f(1.0f, -1.0f, 0.0f);
        GLSMCurrentAttribs.glTexCoord2f(1.0f, 1.0f);
        GLSMDraws.glVertex3f(1.0f, 1.0f, 0.0f);
        GLSMCurrentAttribs.glTexCoord2f(0.0f, 1.0f);
        GLSMDraws.glVertex3f(-1.0f, 1.0f, 0.0f);
        GLSMDraws.glEnd();
        GLSMEnable.glDisable(GL11C.GL_TEXTURE_2D);

        assertEquals(texel, readCenterPixel());
        assertEquals(GL11C.GL_NO_ERROR, GL11C.glGetError());
    }

    @ParameterizedTest(name = "emulated={0}")
    @ValueSource(booleans = {false, true})
    void clipPlaneKeepsOnlyTheEnabledHalfSpace(boolean emulated) {
        try (var ignored = ClipTestSupport.emulation(emulated)) {
            try {
                bindClearedTarget();
                ClipTestSupport.setPlane(1, 0.0, 0.0, 0.0, -1.0);
                ClipTestSupport.setPlane(0, 1.0, 0.0, 0.0, 0.0);
                GLSMEnable.glEnable(GL30C.GL_CLIP_DISTANCE0);
                GLSMDraws.glBegin(GL11C.GL_QUADS);
                GLSMCurrentAttribs.glColor4f(0.0f, 1.0f, 0.0f, 1.0f);
                GLSMDraws.glVertex3f(-1.0f, -1.0f, 0.0f);
                GLSMDraws.glVertex3f(1.0f, -1.0f, 0.0f);
                GLSMDraws.glVertex3f(1.0f, 1.0f, 0.0f);
                GLSMDraws.glVertex3f(-1.0f, 1.0f, 0.0f);
                GLSMDraws.glEnd();

                assertEquals(0xFF000000, readPixel(SIZE / 4, SIZE / 2), "x < 0 must be clipped by plane 0");
                assertEquals(0xFF00FF00, readPixel(3 * SIZE / 4, SIZE / 2), "x >= 0 must survive; plane 1 set but not enabled must not clip");
                assertEquals(GL11C.GL_NO_ERROR, GL11C.glGetError());
            } finally {
                GLSMEnable.glDisable(GL30C.GL_CLIP_DISTANCE0);
            }
        }
    }

    @Test
    void proxyTextureProbeFindsTheMaxTextureSize() {
        final int maxSize = Integer.highestOneBit(GL11C.glGetInteger(GL11C.GL_MAX_TEXTURE_SIZE));
        int found = -1;
        for (int size = maxSize * 2; size > 0 && found < 0; size >>= 1) {
            GLSMTextures.glTexImage2D(GL11C.GL_PROXY_TEXTURE_2D, 0, GL11C.GL_RGBA, size, size, 0, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            if (GLSMTextures.glGetTexLevelParameteri(GL11C.GL_PROXY_TEXTURE_2D, 0, GL11C.GL_TEXTURE_WIDTH) != 0) found = size;
        }
        assertEquals(maxSize, found);
        assertEquals(GL11C.GL_NO_ERROR, GL11C.glGetError());
    }

    private static void bindClearedTarget() {
        GLSMFramebuffer.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fbo);
        GLSMRasterizer.glViewport(0, 0, SIZE, SIZE);
        GLSMBlend.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLSMFramebuffer.glClear(GL11C.GL_COLOR_BUFFER_BIT | GL11C.GL_DEPTH_BUFFER_BIT);
    }

    private static int readCenterPixel() {
        return readPixel(SIZE / 2, SIZE / 2);
    }

    private static int readPixel(int x, int y) {
        final ByteBuffer pixel = MemoryUtil.memAlloc(4);
        try {
            GLSMFramebuffer.glReadPixels(x, y, 1, 1, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, pixel);
            return (pixel.get(3) & 0xFF) << 24 | (pixel.get(0) & 0xFF) << 16 | (pixel.get(1) & 0xFF) << 8 | pixel.get(2) & 0xFF;
        } finally {
            MemoryUtil.memFree(pixel);
        }
    }

    private static int createTarget() {
        final int color = GLSMTextures.glGenTextures();
        GLSMTextures.glBindTexture(GL11C.GL_TEXTURE_2D, color);
        GLSMTextures.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
        GLSMTextures.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
        GLSMTextures.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RGBA8, SIZE, SIZE, 0, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        final int depth = GLSMFramebuffer.glGenRenderbuffers();
        GLSMFramebuffer.glBindRenderbuffer(GL30C.GL_RENDERBUFFER, depth);
        GLSMFramebuffer.glRenderbufferStorage(GL30C.GL_RENDERBUFFER, GL30C.GL_DEPTH24_STENCIL8, SIZE, SIZE);
        final int target = GLSMFramebuffer.glGenFramebuffers();
        GLSMFramebuffer.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, target);
        GLSMFramebuffer.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0, GL11C.GL_TEXTURE_2D, color, 0);
        GLSMFramebuffer.glFramebufferRenderbuffer(GL30C.GL_FRAMEBUFFER, GL30C.GL_DEPTH_STENCIL_ATTACHMENT, GL30C.GL_RENDERBUFFER, depth);
        GLSMFramebuffer.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, 0);
        return target;
    }
}
