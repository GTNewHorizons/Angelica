package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlSplashTargetTest {

    @BeforeAll
    static void boot() {
        GlsmSdlHeadlessRig.boot();
    }

    @Test
    void seededSplashContextDrawsIntoSplashTarget() throws Throwable {
        final SDLGPURenderBackend backend = (SDLGPURenderBackend) BackendManager.RENDER_BACKEND;
        final OffscreenTarget target = createSplashTarget(backend);
        Reflect.set(backend, "splashTarget", target);
        try {
            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            runOnWorker(() -> {
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
    void bindZeroOnSplashThreadTargetsSplash() throws Throwable {
        final SDLGPURenderBackend backend = (SDLGPURenderBackend) BackendManager.RENDER_BACKEND;
        final OffscreenTarget target = createSplashTarget(backend);
        Reflect.set(backend, "splashTarget", target);
        try {
            runOnWorker(() -> {
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

            GlsmSdlHeadlessRig.beginFrame();
            GlsmSdlHeadlessRig.bindTarget();
            final int[] rigPixels = GlsmSdlHeadlessRig.readTarget();
            for (int i = 0; i < rigPixels.length; i++) {
                assertNotEquals(0xFF00FF00, rigPixels[i],
                    "rig target pixel " + i + ": " + GlsmSdlHeadlessRig.describe(rigPixels[i]));
            }
        } finally {
            releaseSplashTarget(backend, target);
        }
    }

    private static OffscreenTarget createSplashTarget(SDLGPURenderBackend backend) {
        final Device device = Reflect.get(backend, "device");
        final ResourceManager resourceManager = Reflect.get(backend, "resourceManager");
        final int sdlFormat = Reflect.invokeStatic(GlsmSdlHeadlessRig.class, "colorTargetSdlFormat", new Class<?>[0]);
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

    private static void runOnWorker(Runnable worker) throws Throwable {
        final Throwable[] failure = new Throwable[1];
        final Thread thread = new Thread(() -> {
            try {
                worker.run();
            } catch (Throwable t) {
                failure[0] = t;
            }
        });
        thread.start();
        thread.join(10000);
        assertFalse(thread.isAlive(), "worker thread did not finish");
        if (failure[0] != null) throw failure[0];
    }
}
