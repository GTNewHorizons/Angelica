package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.sdlgpu.SDLGPUGate;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlFbo0ViewportTest {

    private static final int FBO0_W = 64;
    private static final int FBO0_H = 96;
    private static final int VIEW = 32;

    private static final int RED = 0xFFFF0000;
    private static final int GREEN = 0xFF00FF00;
    private static final int BLUE = 0xFF0000FF;

    private static FrameManager frameManager;
    private static ResourceManager resourceManager;

    @BeforeAll
    static void boot() {
        GlsmSdlHeadlessRig.boot();
        frameManager = Reflect.get(BackendManager.RENDER_BACKEND, "frameManager");
        resourceManager = Reflect.get(BackendManager.RENDER_BACKEND, "resourceManager");
        frameManager.destroyFinalTarget();
        frameManager.finalTarget().create(SDLGPUGate.device(), resourceManager, FBO0_W, FBO0_H, resourceManager.mapTextureFormat(GL11.GL_RGBA8));
        assertEquals(FBO0_H, frameManager.getFbo0Height());
    }

    @AfterAll
    static void dropFinalTarget() {
        GlsmSdlHeadlessRig.endFrame();
        if (frameManager != null) frameManager.destroyFinalTarget();
    }

    private static void bindFbo0WithSmallViewport() {
        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GLStateManager.glViewport(0, 0, VIEW, VIEW);
        GLStateManager.glClearColor(0.0f, 0.0f, 1.0f, 1.0f);
        GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    }

    private static int createCopyTarget() {
        final int id = GLStateManager.glGenTextures();
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, VIEW, VIEW, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        return id;
    }

    private static void drawTextureOverTarget(int texture) {
        GlsmSdlHeadlessRig.bindTarget();
        GlsmSdlHeadlessRig.clearTo(0.0f, 0.0f, 0.0f, 1.0f);
        GlsmSdlHeadlessRig.texturedQuad(texture, 1.0f);
    }

    @Test
    void partialViewportDrawCoversOnlyTheGlViewportRows() {
        bindFbo0WithSmallViewport();
        GlsmSdlHeadlessRig.solidQuad(1.0f, 0.0f, 0.0f);

        GlsmSdlHeadlessRig.assertUniform(GlsmSdlHeadlessRig.readTarget(0, 0, VIEW, VIEW), RED, "inside the viewport");
        GlsmSdlHeadlessRig.assertUniform(GlsmSdlHeadlessRig.readTarget(0, FBO0_H - VIEW, VIEW, VIEW), BLUE, "above the viewport");
    }

    @Test
    void copyTexSubImage2DFromAPartialViewportKeepsGlOrientation() {
        bindFbo0WithSmallViewport();
        GlsmSdlHeadlessRig.halfQuad(-1.0f, 0.0f, 0.0f, 1.0f, 0.0f);
        GlsmSdlHeadlessRig.halfQuad(0.0f, 1.0f, 1.0f, 0.0f, 0.0f);

        final int copy = createCopyTarget();
        GLStateManager.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, VIEW, VIEW);

        drawTextureOverTarget(copy);
        final int[] pixels = GlsmSdlHeadlessRig.readTarget();
        GlsmSdlHeadlessRig.assertPixel(pixels, SIZE, SIZE / 2, 4, GREEN, "sampled copy bottom");
        GlsmSdlHeadlessRig.assertPixel(pixels, SIZE, SIZE / 2, SIZE - 5, RED, "sampled copy top");
    }

    @Test
    void copyTexImage2DAllocatesTheDestinationLevel() {
        bindFbo0WithSmallViewport();
        GlsmSdlHeadlessRig.solidQuad(1.0f, 0.0f, 0.0f);

        final int copy = GLStateManager.glGenTextures();
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, copy);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        assertNull(resourceManager.getTextureMeta(copy), "destination was allocated before the copy");

        GLStateManager.glCopyTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 0, 0, VIEW, VIEW, 0);

        final ResourceManager.TextureMeta meta = resourceManager.getTextureMeta(copy);
        assertNotNull(meta, "copyTexImage2D did not allocate the destination");
        assertEquals(VIEW, meta.width());
        assertEquals(VIEW, meta.height());

        GlsmSdlHeadlessRig.endFrame();
        final ByteBuffer out = MemoryUtil.memAlloc(VIEW * VIEW * 4);
        try {
            GLStateManager.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, out);
            for (int i = 0; i < VIEW * VIEW; i++) {
                final int idx = i;
                assertEquals((byte) 0xFF, out.get(i * 4), () -> "R at texel " + idx);
                assertEquals((byte) 0x00, out.get(i * 4 + 1), () -> "G at texel " + idx);
                assertEquals((byte) 0x00, out.get(i * 4 + 2), () -> "B at texel " + idx);
                assertEquals((byte) 0xFF, out.get(i * 4 + 3), () -> "A at texel " + idx);
            }
        } finally {
            MemoryUtil.memFree(out);
        }
    }
}
