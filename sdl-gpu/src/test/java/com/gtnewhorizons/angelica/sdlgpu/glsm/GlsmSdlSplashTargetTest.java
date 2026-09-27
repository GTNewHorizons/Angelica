package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.glsm.testutil.TestThreads;
import com.gtnewhorizons.angelica.sdlgpu.SDLGPURenderBackend;
import com.gtnewhorizons.angelica.sdlgpu.device.Device;
import com.gtnewhorizons.angelica.sdlgpu.frame.OffscreenTarget;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import com.gtnewhorizons.angelica.sdlgpu.splash.SplashDispatcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL30;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlSplashTargetTest {

    @BeforeAll
    static void boot() {
        GlsmSdlHeadlessRig.boot();
    }

    @Test
    void seededSplashContextDrawsIntoSplashTarget() throws Exception {
        final SDLGPURenderBackend backend = (SDLGPURenderBackend) BackendManager.RENDER_BACKEND;
        final OffscreenTarget target = createSplashTarget(backend);
        Reflect.set(backend, "splashTarget", target);
        try {
            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            TestThreads.run("SplashTarget-Seed-Thread", () -> {
                Reflect.invoke(backend, "enterContext", new Class<?>[]{boolean.class}, true);
                GlsmSdlHeadlessRig.solidQuad(0f, 1f, 0f);
                backend.handleSwapBuffers();
                backend.onRenderThreadReleased(Thread.currentThread());
            });

            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, target.fboId());
            GlsmSdlHeadlessRig.assertUniform(GlsmSdlHeadlessRig.readTarget(), 0xFF00FF00, "splash target");
        } finally {
            releaseSplashTarget(backend, target);
        }
    }

    @Test
    void bindZeroOnSplashThreadTargetsSplash() throws Exception {
        final SDLGPURenderBackend backend = (SDLGPURenderBackend) BackendManager.RENDER_BACKEND;
        final OffscreenTarget target = createSplashTarget(backend);
        Reflect.set(backend, "splashTarget", target);
        try {
            GlsmSdlHeadlessRig.bindTarget();
            GlsmSdlHeadlessRig.clearTo(0f, 0f, 1f, 1f);
            TestThreads.run("SplashTarget-BindZero-Thread", () -> {
                Reflect.invoke(backend, "enterContext", new Class<?>[]{boolean.class}, true);
                GlsmSdlHeadlessRig.bindTarget();
                GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
                GlsmSdlHeadlessRig.clearTo(1f, 0f, 0f, 1f);
                GlsmSdlHeadlessRig.solidQuad(0f, 1f, 0f);
                backend.handleSwapBuffers();
                backend.onRenderThreadReleased(Thread.currentThread());
            });

            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, target.fboId());
            GlsmSdlHeadlessRig.assertUniform(GlsmSdlHeadlessRig.readTarget(), 0xFF00FF00, "splash target");

            GlsmSdlHeadlessRig.bindTarget();
            GlsmSdlHeadlessRig.assertUniform(GlsmSdlHeadlessRig.readTarget(), 0xFF0000FF, "rig target");
        } finally {
            releaseSplashTarget(backend, target);
        }
    }

    private static OffscreenTarget createSplashTarget(SDLGPURenderBackend backend) {
        final Device device = Reflect.get(backend, "device");
        final ResourceManager resourceManager = Reflect.get(backend, "resourceManager");
        final int sdlFormat = GlsmSdlHeadlessRig.colorTargetSdlFormat();
        final OffscreenTarget target = new OffscreenTarget();
        target.create(device, resourceManager, GlsmSdlHeadlessRig.SIZE, GlsmSdlHeadlessRig.SIZE, sdlFormat);
        return target;
    }

    private static void releaseSplashTarget(SDLGPURenderBackend backend, OffscreenTarget target) {
        Reflect.set(backend, "splashTarget", null);
        SplashDispatcher.reset();
        GlsmSdlHeadlessRig.bindTarget();
        final ResourceManager resourceManager = Reflect.get(backend, "resourceManager");
        target.destroy(resourceManager);
    }
}
