package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.backend.RenderBackend;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMInitConfig;
import com.gtnewhorizons.angelica.glsm.streaming.TessellatorStreamingDrawer;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.sdlgpu.SDLGPUGate;
import com.gtnewhorizons.angelica.sdlgpu.SDLGPURenderBackend;
import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;
import com.gtnewhorizons.angelica.sdlgpu.pipeline.PipelineCache;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public final class GlsmSdlHeadlessRig {

    public static final int SIZE = 64;

    private static boolean booted;
    private static int fbo;
    private static int colorTexture;
    private static int depthRenderbuffer;

    private GlsmSdlHeadlessRig() {}

    public static void boot() {
        if (booted) return;
        SdlTestRig.initSdlVideo();
        assumeTrue(SDLGPUGate.device().createDevice(), () -> "no SDL GPU device: " + SDLError.SDL_GetError());
        Reflect.setStatic(SDLGPUGate.class, "engaged", true);

        final RenderBackend backend = BackendManager.RENDER_BACKEND;
        assumeTrue(backend instanceof SDLGPURenderBackend, () -> "selected backend is " + backend.getName());

        backend.onPostWindowCreate(0L);
        Reflect.setStaticFinal(GLStateManager.class, "MainThread", Thread.class, Thread.currentThread());

        GLStateManager.initialize(GLSMInitConfig.builder()
            .displaySize(SIZE, SIZE)
            .directDrawer(TessellatorStreamingDrawer::drawDirect)
            .streamingDrawerDestroy(TessellatorStreamingDrawer::destroy)
            .build());
        GLStateManager.setRunningSplash(false);
        GLStateManager.markSplashComplete("glsmSdlHeadlessRig");

        assertTrue(GlsmSdlRedirectAgent.transformedCount() > 0, "redirect agent transformed no classes");
        assertTrue(GlsmSdlRedirectAgent.failures().isEmpty(),
            () -> "redirect agent recorded failures: " + GlsmSdlRedirectAgent.failures());

        createTarget();
        booted = true;
    }

    private static void createTarget() {
        beginFrame();
        colorTexture = GLStateManager.glGenTextures();
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, colorTexture);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, SIZE, SIZE, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);

        depthRenderbuffer = GLStateManager.glGenRenderbuffers();
        GLStateManager.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthRenderbuffer);
        GLStateManager.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8, SIZE, SIZE);

        fbo = GLStateManager.glGenFramebuffers();
        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GLStateManager.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, colorTexture, 0);
        GLStateManager.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT, GL30.GL_RENDERBUFFER, depthRenderbuffer);
        GLStateManager.glViewport(0, 0, SIZE, SIZE);

        PipelineCache.setSwapchainFormats(new int[]{ colorTargetSdlFormat() });
        endFrame();
    }

    private static int colorTargetSdlFormat() {
        final ResourceManager rm = Reflect.get(BackendManager.RENDER_BACKEND, "resourceManager");
        final ResourceManager.TextureMeta meta = rm.getTextureMeta(colorTexture);
        return meta != null ? meta.sdlFormat() : 0;
    }

    private static boolean frameOpen;

    public static void beginFrame() {
        if (frameOpen) return;
        BackendManager.RENDER_BACKEND.onFrameBegin();
        frameOpen = true;
    }

    public static void endFrame() {
        if (!frameOpen) return;
        frameOpen = false;
        BackendManager.RENDER_BACKEND.onFrameEnd();
    }

    public static void bindTarget() {
        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GLStateManager.glViewport(0, 0, SIZE, SIZE);
    }

    public static void clearTo(float r, float g, float b, float a) {
        GLStateManager.glClearColor(r, g, b, a);
        GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    }

    public static void beginFrameAndReset() {
        beginFrame();
        resetState();
        bindTarget();
        clearTo(0.0f, 0.0f, 0.0f, 1.0f);
    }

    public static void resetState() {
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
        GLStateManager.glDisable(GL11.GL_TEXTURE_2D);
        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        GLStateManager.glLoadIdentity();
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glDisable(GL11.GL_TEXTURE_2D);
        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        GLStateManager.glLoadIdentity();

        GLStateManager.glDisable(GL11.GL_ALPHA_TEST);
        GLStateManager.glDisable(GL11.GL_FOG);
        GLStateManager.glDisable(GL11.GL_LIGHTING);
        GLStateManager.glDisable(GL11.GL_LIGHT0);
        GLStateManager.glDisable(GL11.GL_LIGHT1);
        GLStateManager.glDisable(GL11.GL_COLOR_MATERIAL);
        GLStateManager.glDisable(GL12.GL_RESCALE_NORMAL);
        GLStateManager.glDisable(GL11.GL_DEPTH_TEST);
        GLStateManager.glDisable(GL11.GL_BLEND);
        GLStateManager.glDisable(GL11.GL_CULL_FACE);
        GLStateManager.glDisable(GL11.GL_SCISSOR_TEST);
        GLStateManager.glCullFace(GL11.GL_BACK);
        GLStateManager.glShadeModel(GL11.GL_SMOOTH);
        GLStateManager.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GLStateManager.glNormal3f(0.0f, 0.0f, 1.0f);

        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glLoadIdentity();
    }

    public static void terrainRenderState() {
        GLStateManager.glEnable(GL11.GL_ALPHA_TEST);
        GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.1f);
        GLStateManager.glEnable(GL11.GL_FOG);
        GLStateManager.glFogi(GL11.GL_FOG_MODE, GL11.GL_LINEAR);
        GLStateManager.glFogf(GL11.GL_FOG_START, 1.0e6f);
        GLStateManager.glFogf(GL11.GL_FOG_END, 1.0e7f);
    }

    public static int createSolidTexture(int argb) {
        final int id = GLStateManager.glGenTextures();
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        final ByteBuffer texels = MemoryUtil.memAlloc(2 * 2 * 4);
        try {
            for (int i = 0; i < 4; i++) {
                texels.put((byte) ((argb >> 16) & 0xFF));
                texels.put((byte) ((argb >> 8) & 0xFF));
                texels.put((byte) (argb & 0xFF));
                texels.put((byte) ((argb >>> 24) & 0xFF));
            }
            texels.flip();
            GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 2, 2, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, texels);
        } finally {
            MemoryUtil.memFree(texels);
        }
        return id;
    }

    public static void enableLightmapUnit(int lightmapTexture, boolean vanillaTextureMatrix) {
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, lightmapTexture);
        GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        GLStateManager.glLoadIdentity();
        if (vanillaTextureMatrix) {
            final float scale = 0.00390625f;
            GLStateManager.glScalef(scale, scale, scale);
            GLStateManager.glTranslatef(8.0f, 8.0f, 8.0f);
        }
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
    }

    public static void emitVertex(float x, float y, float u, float v, boolean lightmap, short brightness) {
        GLStateManager.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GLStateManager.glNormal3f(0.0f, 0.0f, 1.0f);
        GLStateManager.glTexCoord2f(u, v);
        if (lightmap) {
            GLStateManager.glMultiTexCoord2s(GL13.GL_TEXTURE1, brightness, brightness);
        }
        GLStateManager.glVertex3f(x, y, 0.0f);
    }

    private static void vertex(float x, float y, float r, float g, float b) {
        GLStateManager.glColor4f(r, g, b, 1.0f);
        GLStateManager.glNormal3f(0.0f, 0.0f, 1.0f);
        GLStateManager.glVertex3f(x, y, 0.0f);
    }

    private static void texVertex(float x, float y, float u, float v) {
        GLStateManager.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GLStateManager.glNormal3f(0.0f, 0.0f, 1.0f);
        GLStateManager.glTexCoord2f(u, v);
        GLStateManager.glVertex3f(x, y, 0.0f);
    }

    public static void halfQuad(float y0, float y1, float r, float g, float b) {
        GLStateManager.glBegin(GL11.GL_QUADS);
        vertex(-1.0f, y0, r, g, b);
        vertex(1.0f, y0, r, g, b);
        vertex(1.0f, y1, r, g, b);
        vertex(-1.0f, y1, r, g, b);
        GLStateManager.glEnd();
    }

    public static void solidQuad(float r, float g, float b) {
        halfQuad(-1.0f, 1.0f, r, g, b);
    }

    public static void texturedQuad(int texture, float extent) {
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
        GLStateManager.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GLStateManager.glBegin(GL11.GL_QUADS);
        texVertex(-extent, -extent, 0.0f, 0.0f);
        texVertex(extent, -extent, 1.0f, 0.0f);
        texVertex(extent, extent, 1.0f, 1.0f);
        texVertex(-extent, extent, 0.0f, 1.0f);
        GLStateManager.glEnd();
    }

    public static int[] readTarget() {
        return readTarget(0, 0, SIZE, SIZE, (byte) 0);
    }

    public static int[] readTarget(int x, int y, int w, int h) {
        return readTarget(x, y, w, h, (byte) 0);
    }

    public static int[] readTarget(int x, int y, int w, int h, byte fill) {
        endFrame();
        final ByteBuffer pixels = MemoryUtil.memAlloc(w * h * 4);
        try {
            for (int i = 0; i < w * h * 4; i++) pixels.put(i, fill);
            GLStateManager.glReadPixels(x, y, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            final int[] out = new int[w * h];
            for (int i = 0; i < out.length; i++) {
                final int r = pixels.get(i * 4) & 0xFF;
                final int g = pixels.get(i * 4 + 1) & 0xFF;
                final int b = pixels.get(i * 4 + 2) & 0xFF;
                final int a = pixels.get(i * 4 + 3) & 0xFF;
                out[i] = (a << 24) | (r << 16) | (g << 8) | b;
            }
            return out;
        } finally {
            MemoryUtil.memFree(pixels);
        }
    }

    public static int pixelAt(int[] pixels, int rowStride, int x, int y) {
        return pixels[y * rowStride + x];
    }

    public static String describe(int argb) {
        return String.format("a=%d r=%d g=%d b=%d", (argb >>> 24) & 0xFF, (argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF);
    }

    public static void assertPixel(int[] pixels, int stride, int x, int y, int expected, String label) {
        final int got = pixelAt(pixels, stride, x, y);
        assertEquals(expected, got, () -> label + " at (" + x + "," + y + "): " + describe(got));
    }

    public static void assertUniform(int[] pixels, int expected, String label) {
        for (int i = 0; i < pixels.length; i++) {
            final int got = pixels[i];
            final int idx = i;
            assertEquals(expected, got, () -> label + " at index " + idx + ": " + describe(got));
        }
    }
}
