package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.ReadbackFixture;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.sdlgpu.SDLGPUGate;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlReadbackMatrixTest {

    private static final int FBO0_W = 8;
    private static final int FBO0_H = 6;

    private static FrameManager frameManager;

    @BeforeAll
    static void boot() {
        GlsmSdlHeadlessRig.boot();
        frameManager = Reflect.get(BackendManager.RENDER_BACKEND, "frameManager");
        final ResourceManager resourceManager = Reflect.get(BackendManager.RENDER_BACKEND, "resourceManager");
        frameManager.destroyFinalTarget();
        frameManager.finalTarget().create(SDLGPUGate.device(), resourceManager, FBO0_W, FBO0_H, resourceManager.mapTextureFormat(GL11.GL_RGBA8));
    }

    @AfterAll
    static void dropFinalTarget() {
        GlsmSdlHeadlessRig.endFrame();
        if (frameManager != null) frameManager.destroyFinalTarget();
    }

    @AfterEach
    void resetPack() {
        ReadbackFixture.resetPack();
        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    @Test
    void rgba8() {
        ReadbackFixture.rgba8();
    }

    @Test
    void packState() {
        ReadbackFixture.packState();
    }

    @Test
    void pixelPackBuffer() {
        ReadbackFixture.pixelPackBuffer();
    }

    @Test
    void rgb8() {
        ReadbackFixture.rgb8();
    }

    @Test
    void rgba16f() {
        ReadbackFixture.rgba16f();
    }

    @Test
    void rgba32f() {
        ReadbackFixture.rgba32f();
    }

    @Test
    void r8() {
        ReadbackFixture.r8();
    }

    @Test
    void rg16f() {
        ReadbackFixture.rg16f();
    }

    @Test
    void rgb10a2() {
        ReadbackFixture.rgb10a2();
    }

    @Test
    void r11g11b10f() {
        ReadbackFixture.r11g11b10f();
    }

    @Test
    void depthStencil() {
        ReadbackFixture.depthStencil();
    }

    @Test
    void fbo0FloatReadIsBottomUp() {
        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GLStateManager.glViewport(0, 0, FBO0_W, FBO0_H);
        GLStateManager.glClearColor(0f, 0f, 1f, 1f);
        GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GLStateManager.glEnable(GL11.GL_SCISSOR_TEST);
        GLStateManager.glScissor(0, 0, FBO0_W, 1);
        GLStateManager.glClearColor(1f, 0f, 0f, 1f);
        GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GLStateManager.glDisable(GL11.GL_SCISSOR_TEST);

        final ByteBuffer out = BufferUtils.createByteBuffer(FBO0_W * FBO0_H * 16).order(ByteOrder.nativeOrder());
        GLStateManager.glReadPixels(0, 0, FBO0_W, FBO0_H, GL11.GL_RGBA, GL11.GL_FLOAT, out);
        assertEquals(1f, out.getFloat(0), "GL row 0 is the scissored bottom row (red)");
        assertEquals(0f, out.getFloat(8), "GL row 0 blue");
        final int top = (FBO0_H - 1) * FBO0_W * 16;
        assertEquals(0f, out.getFloat(top), "top row red");
        assertEquals(1f, out.getFloat(top + 8), "top row blue");
    }

    @Test
    void fbo0ReadAfterBareClearSeesTheClear() {
        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GLStateManager.glViewport(0, 0, FBO0_W, FBO0_H);
        GLStateManager.glClearColor(0.2f, 0.4f, 0.6f, 1f);
        GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT);

        final ByteBuffer out = BufferUtils.createByteBuffer(FBO0_W * FBO0_H * 4);
        GLStateManager.glReadPixels(0, 0, FBO0_W, FBO0_H, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, out);
        assertEquals(51, out.get(0) & 0xFF, "red after bare clear");
        assertEquals(102, out.get(1) & 0xFF, "green after bare clear");
        assertEquals(153, out.get(2) & 0xFF, "blue after bare clear");
    }

    @Test
    void fbo0DepthAndStencilAreBottomUp() {
        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GLStateManager.glViewport(0, 0, FBO0_W, FBO0_H);
        GLStateManager.glDepthMask(true);
        GLStateManager.glStencilMask(0xFF);
        GLStateManager.glClearDepth(0.75);
        GLStateManager.glClearStencil(0x11);
        GLStateManager.glClear(GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);
        GLStateManager.glEnable(GL11.GL_SCISSOR_TEST);
        GLStateManager.glScissor(0, 0, FBO0_W, 1);
        GLStateManager.glClearDepth(0.25);
        GLStateManager.glClearStencil(0x22);
        GLStateManager.glClear(GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);
        GLStateManager.glDisable(GL11.GL_SCISSOR_TEST);
        GLStateManager.glClearDepth(1.0);
        GLStateManager.glClearStencil(0);

        final ByteBuffer depth = BufferUtils.createByteBuffer(FBO0_W * FBO0_H * 4).order(ByteOrder.nativeOrder());
        GLStateManager.glReadPixels(0, 0, FBO0_W, FBO0_H, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depth);
        assertEquals(0.25f, depth.getFloat(0), 1e-6f, "fbo0 depth bottom row");
        assertEquals(0.75f, depth.getFloat((FBO0_H - 1) * FBO0_W * 4), 1e-6f, "fbo0 depth top row");

        final ByteBuffer stencil = BufferUtils.createByteBuffer(FBO0_W * FBO0_H);
        GLStateManager.glReadPixels(0, 0, FBO0_W, FBO0_H, GL11.GL_STENCIL_INDEX, GL11.GL_UNSIGNED_BYTE, stencil);
        assertEquals(0x22, stencil.get(0) & 0xFF, "fbo0 stencil bottom row");
        assertEquals(0x11, stencil.get((FBO0_H - 1) * FBO0_W) & 0xFF, "fbo0 stencil top row");
    }
}
