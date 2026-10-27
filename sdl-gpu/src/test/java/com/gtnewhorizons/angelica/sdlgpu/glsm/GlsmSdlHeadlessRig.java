package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMCurrentAttribs;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMEnable;
import com.gtnewhorizons.angelica.glsm.GLSMFog;
import com.gtnewhorizons.angelica.glsm.GLSMFramebuffer;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.GlsmRedirectAgent;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.backend.RenderBackend;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMInitConfig;
import com.gtnewhorizons.angelica.glsm.streaming.TessellatorStreamingDrawer;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.sdlgpu.SDLGPUGate;
import com.gtnewhorizons.angelica.sdlgpu.SDLGPURenderBackend;
import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import com.gtnewhorizons.angelica.sdlgpu.pipeline.PipelineCache;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class GlsmSdlHeadlessRig {

    public static final int SIZE = 64;

    public static final String FULLSCREEN_VERTEX = """
        #version 330 core
        void main() {
            vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
            gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
        }
        """;

    public static final int RED = 0xFFFF0000;
    public static final int GREEN = 0xFF00FF00;
    public static final int BLUE = 0xFF0000FF;
    public static final int BLACK = 0xFF000000;

    public static final int TEXEL = 0xFFFF4020;
    public static final int LIGHTMAP_SIZE = 16;

    private static boolean booted;
    private static int fbo;
    private static int colorTexture;
    private static int depthRenderbuffer;

    private GlsmSdlHeadlessRig() {}

    public static void boot() {
        if (booted) return;
        SdlTestRig.initSdlVideo();
        assertTrue(SDLGPUGate.device().createDevice(), () -> "no SDL GPU device: " + SDLError.SDL_GetError());
        Reflect.setStatic(SDLGPUGate.class, "engaged", true);

        final RenderBackend backend = BackendManager.RENDER_BACKEND;
        assertTrue(backend instanceof SDLGPURenderBackend, () -> "selected backend is " + backend.getName());

        backend.onPostWindowCreate(0L);
        Reflect.setStaticFinal(GLStateManager.class, "MainThread", Thread.class, Thread.currentThread());

        GLStateManager.initialize(GLSMInitConfig.builder()
            .displaySize(SIZE, SIZE)
            .directDrawer(TessellatorStreamingDrawer::drawDirect)
            .build());
        GLStateManager.setRunningSplash(false);
        GLStateManager.markSplashComplete("glsmSdlHeadlessRig");

        assertTrue(GlsmRedirectAgent.transformedCount() > 0, "redirect agent transformed no classes");
        GlsmRedirectAgent.assertNoFailures();

        createTarget();
        booted = true;
    }

    private static void createTarget() {
        beginFrame();
        colorTexture = GLSMTextures.glGenTextures();
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, colorTexture);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GLSMTextures.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, SIZE, SIZE, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);

        depthRenderbuffer = GLSMFramebuffer.glGenRenderbuffers();
        GLSMFramebuffer.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthRenderbuffer);
        GLSMFramebuffer.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8, SIZE, SIZE);

        fbo = GLSMFramebuffer.glGenFramebuffers();
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GLSMFramebuffer.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, colorTexture, 0);
        GLSMFramebuffer.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT, GL30.GL_RENDERBUFFER, depthRenderbuffer);
        GLSMRasterizer.glViewport(0, 0, SIZE, SIZE);

        PipelineCache.setSwapchainFormats(new int[]{ colorTargetSdlFormat() });
        endFrame();
    }

    public static int glsmBufferBinding(int target) {
        final Object ctx = Reflect.getStatic(GLStateManager.class, "primaryContext");
        final Int2IntMap bound = Reflect.get(ctx, "boundOtherBuffers");
        return bound.get(target);
    }

    public static FrameManager frameManager() {
        return Reflect.get(BackendManager.RENDER_BACKEND, "frameManager");
    }

    public static ResourceManager resourceManager() {
        return Reflect.get(BackendManager.RENDER_BACKEND, "resourceManager");
    }

    /** Replaces FBO 0's backing target; pair with {@link #dropFinalTarget()}. */
    public static void installFinalTarget(int width, int height) {
        final FrameManager fm = frameManager();
        final ResourceManager rm = resourceManager();
        fm.destroyFinalTarget();
        fm.finalTarget().create(SDLGPUGate.device(), rm, width, height, rm.mapTextureFormat(GL11.GL_RGBA8));
        assertEquals(height, fm.getFbo0Height());
    }

    public static void dropFinalTarget() {
        endFrame();
        if (booted) frameManager().destroyFinalTarget();
    }

    static int colorTargetSdlFormat() {
        final ResourceManager.TextureMeta meta = resourceManager().getTextureMeta(colorTexture);
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
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GLSMRasterizer.glViewport(0, 0, SIZE, SIZE);
    }

    public static int createDepthStencilFbo() {
        final int color = GLSMTextures.glGenTextures();
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, color);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GLSMTextures.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, SIZE, SIZE, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);

        final int depth = GLSMFramebuffer.glGenRenderbuffers();
        GLSMFramebuffer.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depth);
        GLSMFramebuffer.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8, SIZE, SIZE);

        final int fbo = GLSMFramebuffer.glGenFramebuffers();
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GLSMFramebuffer.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, color, 0);
        GLSMFramebuffer.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT, GL30.GL_RENDERBUFFER, depth);
        return fbo;
    }

    public static void writeStencilOneInBottomHalf() {
        GLSMDepthStencilAlpha.glClearStencil(0);
        GLSMDepthStencilAlpha.glStencilMask(0xFF);
        GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);
        GLSMEnable.glEnable(GL11.GL_STENCIL_TEST);
        GLSMDepthStencilAlpha.glStencilFunc(GL11.GL_ALWAYS, 1, 0xFF);
        GLSMDepthStencilAlpha.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
        halfQuad(-1.0f, 0.0f, 0.0f, 1.0f, 0.0f);
        GLSMEnable.glDisable(GL11.GL_STENCIL_TEST);
    }

    public static void drawRedWhereStencilIsOne() {
        GLSMEnable.glEnable(GL11.GL_STENCIL_TEST);
        GLSMDepthStencilAlpha.glStencilFunc(GL11.GL_EQUAL, 1, 0xFF);
        GLSMDepthStencilAlpha.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        solidQuad(1.0f, 0.0f, 0.0f);
        GLSMEnable.glDisable(GL11.GL_STENCIL_TEST);
    }

    public static void clearTo(float r, float g, float b, float a) {
        GLSMBlend.glClearColor(r, g, b, a);
        GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    }

    public static void beginFrameAndClear() {
        beginFrame();
        bindTarget();
        clearTo(0.0f, 0.0f, 0.0f, 1.0f);
    }

    public static void beginFrameAndReset() {
        beginFrame();
        resetState();
        bindTarget();
        clearTo(0.0f, 0.0f, 0.0f, 1.0f);
    }

    public static void resetState() {
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
        GLSMEnable.glDisable(GL11.GL_TEXTURE_2D);
        GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
        GLSMMatrices.glLoadIdentity();
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        GLSMEnable.glDisable(GL11.GL_TEXTURE_2D);
        GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
        GLSMMatrices.glLoadIdentity();

        GLSMEnable.glDisable(GL11.GL_ALPHA_TEST);
        GLSMEnable.glDisable(GL11.GL_FOG);
        GLSMEnable.glDisable(GL11.GL_LIGHTING);
        GLSMEnable.glDisable(GL11.GL_LIGHT0);
        GLSMEnable.glDisable(GL11.GL_LIGHT1);
        GLSMEnable.glDisable(GL11.GL_COLOR_MATERIAL);
        GLSMEnable.glDisable(GL12.GL_RESCALE_NORMAL);
        GLSMEnable.glDisable(GL11.GL_DEPTH_TEST);
        GLSMEnable.glDisable(GL11.GL_BLEND);
        GLSMEnable.glDisable(GL11.GL_CULL_FACE);
        GLSMEnable.glDisable(GL11.GL_SCISSOR_TEST);
        GLSMRasterizer.glCullFace(GL11.GL_BACK);
        GLSMRasterizer.glShadeModel(GL11.GL_SMOOTH);
        GLSMCurrentAttribs.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GLSMCurrentAttribs.glNormal3f(0.0f, 0.0f, 1.0f);

        GLSMMatrices.glMatrixMode(GL11.GL_PROJECTION);
        GLSMMatrices.glLoadIdentity();
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMMatrices.glLoadIdentity();
    }

    public static void terrainRenderState() {
        GLSMEnable.glEnable(GL11.GL_ALPHA_TEST);
        GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_GREATER, 0.1f);
        GLSMEnable.glEnable(GL11.GL_FOG);
        GLSMFog.glFogi(GL11.GL_FOG_MODE, GL11.GL_LINEAR);
        GLSMFog.glFogf(GL11.GL_FOG_START, 1.0e6f);
        GLSMFog.glFogf(GL11.GL_FOG_END, 1.0e7f);
    }

    public static int newNearestTexture() {
        final int id = GLSMTextures.glGenTextures();
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        return id;
    }

    public static int fboWithColor(int texture, int level) {
        final int fbo = GLSMFramebuffer.glGenFramebuffers();
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GLSMFramebuffer.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, level);
        return fbo;
    }

    public static void putArgb(ByteBuffer texels, int index, int argb) {
        texels.put(index * 4, (byte) ((argb >> 16) & 0xFF));
        texels.put(index * 4 + 1, (byte) ((argb >> 8) & 0xFF));
        texels.put(index * 4 + 2, (byte) (argb & 0xFF));
        texels.put(index * 4 + 3, (byte) ((argb >>> 24) & 0xFF));
    }

    /** Uploads one solid RGBA8 level of the GL_TEXTURE_2D bound on the active unit. */
    public static void uploadSolidLevel(int level, int size, int argb) {
        final ByteBuffer texels = MemoryUtil.memAlloc(size * size * 4);
        try {
            for (int i = 0; i < size * size; i++) putArgb(texels, i, argb);
            GLSMTextures.glTexImage2D(GL11.GL_TEXTURE_2D, level, GL11.GL_RGBA8, size, size, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, texels);
        } finally {
            MemoryUtil.memFree(texels);
        }
    }

    public static int createSolidTexture(int argb) {
        final int id = newNearestTexture();
        uploadSolidLevel(0, 2, argb);
        return id;
    }

    public static int createLightmapTexture(boolean brightCornersOnly) {
        final int id = GLSMTextures.glGenTextures();
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GLSMTextures.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, LIGHTMAP_SIZE, LIGHTMAP_SIZE, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        final ByteBuffer texels = MemoryUtil.memAlloc(LIGHTMAP_SIZE * LIGHTMAP_SIZE * 4);
        try {
            final int last = LIGHTMAP_SIZE - 1;
            for (int y = 0; y < LIGHTMAP_SIZE; y++) {
                for (int x = 0; x < LIGHTMAP_SIZE; x++) {
                    final boolean bright = !brightCornersOnly || (x == 0 && y == 0) || (x == last && y == last);
                    putArgb(texels, y * LIGHTMAP_SIZE + x, bright ? 0xFFFFFFFF : GREEN);
                }
            }
            GLSMTextures.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, LIGHTMAP_SIZE, LIGHTMAP_SIZE, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, texels);
        } finally {
            MemoryUtil.memFree(texels);
        }
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        return id;
    }

    public static void bindBaseTexture(int texture) {
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GLSMEnable.glEnable(GL11.GL_TEXTURE_2D);
    }

    public static void quadTwoUnits(short brightness) {
        GLSMDraws.glBegin(GL11.GL_QUADS);
        emitVertex(-1.0f, -1.0f, 0.0f, 0.0f, true, brightness);
        emitVertex(1.0f, -1.0f, 1.0f, 0.0f, true, brightness);
        emitVertex(1.0f, 1.0f, 1.0f, 1.0f, true, brightness);
        emitVertex(-1.0f, 1.0f, 0.0f, 1.0f, true, brightness);
        GLSMDraws.glEnd();
    }

    public static void enableLightmapUnit(int lightmapTexture, boolean vanillaTextureMatrix) {
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, lightmapTexture);
        GLSMEnable.glEnable(GL11.GL_TEXTURE_2D);
        GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
        GLSMMatrices.glLoadIdentity();
        if (vanillaTextureMatrix) {
            final float scale = 0.00390625f;
            GLSMMatrices.glScalef(scale, scale, scale);
            GLSMMatrices.glTranslatef(8.0f, 8.0f, 8.0f);
        }
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
    }

    public static void emitVertex(float x, float y, float u, float v, boolean lightmap, short brightness) {
        GLSMCurrentAttribs.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GLSMCurrentAttribs.glNormal3f(0.0f, 0.0f, 1.0f);
        GLSMCurrentAttribs.glTexCoord2f(u, v);
        if (lightmap) {
            GLSMCurrentAttribs.glMultiTexCoord2s(GL13.GL_TEXTURE1, brightness, brightness);
        }
        GLSMDraws.glVertex3f(x, y, 0.0f);
    }

    private static void vertex(float x, float y, float r, float g, float b) {
        GLSMCurrentAttribs.glColor4f(r, g, b, 1.0f);
        GLSMCurrentAttribs.glNormal3f(0.0f, 0.0f, 1.0f);
        GLSMDraws.glVertex3f(x, y, 0.0f);
    }

    private static void texVertex(float x, float y, float u, float v) {
        GLSMCurrentAttribs.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GLSMCurrentAttribs.glNormal3f(0.0f, 0.0f, 1.0f);
        GLSMCurrentAttribs.glTexCoord2f(u, v);
        GLSMDraws.glVertex3f(x, y, 0.0f);
    }

    public static void halfQuad(float y0, float y1, float r, float g, float b) {
        GLSMDraws.glBegin(GL11.GL_QUADS);
        vertex(-1.0f, y0, r, g, b);
        vertex(1.0f, y0, r, g, b);
        vertex(1.0f, y1, r, g, b);
        vertex(-1.0f, y1, r, g, b);
        GLSMDraws.glEnd();
    }

    public static void solidQuad(float r, float g, float b) {
        halfQuad(-1.0f, 1.0f, r, g, b);
    }

    public static void texturedQuad(int texture, float extent) {
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GLSMEnable.glEnable(GL11.GL_TEXTURE_2D);
        GLSMCurrentAttribs.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GLSMDraws.glBegin(GL11.GL_QUADS);
        texVertex(-extent, -extent, 0.0f, 0.0f);
        texVertex(extent, -extent, 1.0f, 0.0f);
        texVertex(extent, extent, 1.0f, 1.0f);
        texVertex(-extent, extent, 0.0f, 1.0f);
        GLSMDraws.glEnd();
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
            GLSMFramebuffer.glReadPixels(x, y, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            return toArgb(pixels, w * h);
        } finally {
            MemoryUtil.memFree(pixels);
        }
    }

    public static int createSolidMipTexture(int size, int maxLevel, int argb) {
        final int id = GLSMTextures.glGenTextures();
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST_MIPMAP_NEAREST);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, maxLevel);
        for (int level = 0; level <= maxLevel; level++) {
            uploadSolidLevel(level, Math.max(1, size >> level), argb);
        }
        return id;
    }

    public static int[] readTextureLevel(int texture, int level) {
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        final int w = GLSMTextures.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_WIDTH);
        final int h = GLSMTextures.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_HEIGHT);
        final ByteBuffer pixels = MemoryUtil.memAlloc(w * h * 4);
        try {
            GLSMTextures.glGetTexImage(GL11.GL_TEXTURE_2D, level, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            return toArgb(pixels, w * h);
        } finally {
            MemoryUtil.memFree(pixels);
        }
    }

    public static int[] toArgb(ByteBuffer pixels, int count) {
        final int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            final int r = pixels.get(i * 4) & 0xFF;
            final int g = pixels.get(i * 4 + 1) & 0xFF;
            final int b = pixels.get(i * 4 + 2) & 0xFF;
            final int a = pixels.get(i * 4 + 3) & 0xFF;
            out[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
        return out;
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
