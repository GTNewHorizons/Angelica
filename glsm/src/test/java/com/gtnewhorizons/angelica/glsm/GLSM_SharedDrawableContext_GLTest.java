package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.ffp.FfpFixture;
import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import com.gtnewhorizons.angelica.glsm.ffp.VAOManager;
import com.gtnewhorizons.angelica.glsm.testutil.TestThreads;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.SharedDrawable;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
public class GLSM_SharedDrawableContext_GLTest {

    private SharedDrawable sharedDrawable;

    @AfterEach
    void cleanup() {
        if (sharedDrawable != null) {
            DrawableContexts.destroy(sharedDrawable);
            sharedDrawable = null;
        }
    }

    @Test
    void sharedDrawableBindsItsOwnContext() throws Exception {
        final GLContextState primary = GLStateManager.ctx();
        sharedDrawable = new SharedDrawable(Display.getDrawable());

        TestThreads.run("SharedDrawableContext-Bind-Thread", () -> {
            GLStateManager.makeCurrent(sharedDrawable);
            try {
                final GLContextState workerCtx = GLStateManager.ctx();
                assertNotSame(primary, workerCtx);
                assertNotEquals(0, workerCtx.defaultVAO);
                assertEquals(workerCtx.defaultVAO, GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING));

                final IntBuffer viewport = BufferUtils.createIntBuffer(16);
                GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
                assertEquals(0, viewport.get(0));
                assertEquals(0, viewport.get(1));
                assertEquals(Display.getWidth(), viewport.get(2));
                assertEquals(Display.getHeight(), viewport.get(3));
            } finally {
                GLStateManager.releaseContext(sharedDrawable);
            }
            assertSame(primary, GLStateManager.ctx());
        });
    }

    @Test
    void failedContextSwitchRestoresPreviousBinding() throws Exception {
        final GLContextState primary = GLStateManager.ctx();
        sharedDrawable = new SharedDrawable(Display.getDrawable());
        final CountDownLatch acquired = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final TestThreads.Worker worker = DrawableContexts.start(sharedDrawable, "SharedDrawableContext-Owner-Thread", () -> {
            acquired.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("release timed out");
        });
        try {
            assertTrue(acquired.await(5, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class, () -> GLStateManager.makeCurrent(sharedDrawable));
            assertSame(primary, GLStateManager.ctx());
            assertEquals(primary.defaultVAO, GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING));
        } finally {
            release.countDown();
            worker.join();
        }
        GLStateManager.makeCurrent(sharedDrawable);
        assertNotSame(primary, GLStateManager.ctx());
        GLStateManager.releaseContext(sharedDrawable);
        GLStateManager.makeCurrent(Display.getDrawable());
        assertSame(primary, GLStateManager.ctx());
    }

    @Test
    void foreignThreadCannotFinishOrRecordIntoActiveList() throws Exception {
        sharedDrawable = new SharedDrawable(Display.getDrawable());
        final int existing = GLStateManager.glGenLists(1);
        final int active = GLStateManager.glGenLists(1);
        final int rejected = GLStateManager.glGenLists(1);
        GLStateManager.glNewList(existing, GL11.GL_COMPILE);
        GLStateManager.enableBlend();
        GLStateManager.glEndList();
        GLStateManager.disableBlend();

        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch finish = new CountDownLatch(1);
        final TestThreads.Worker worker = DrawableContexts.start(sharedDrawable, "SharedDrawableContext-List-Thread", () -> {
            GLStateManager.glNewList(active, GL11.GL_COMPILE);
            started.countDown();
            if (!finish.await(5, TimeUnit.SECONDS)) throw new AssertionError("finish timed out");
            GLStateManager.glEndList();
        });
        try {
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS));
                assertThrows(IllegalStateException.class, () -> GLStateManager.glNewList(rejected, GL11.GL_COMPILE));
                assertThrows(IllegalStateException.class, GLStateManager::glEndList);
                assertEquals(0, GLStateManager.getListMode());
                DisplayListManager.abortIfLeaked();
                assertThrows(IllegalStateException.class, DisplayListManager::abortCompilation);
                GLStateManager.glCallList(existing);
                assertTrue(GLStateManager.glIsEnabled(GL11.GL_BLEND));
                assertEquals(0, GLStateManager.getListMode());
            } finally {
                finish.countDown();
                worker.join();
            }
            assertTrue(DisplayListManager.displayListExists(active));
            GLStateManager.glCallList(active);
        } finally {
            GLStateManager.glDeleteLists(existing, 1);
            GLStateManager.glDeleteLists(active, 1);
            GLStateManager.glDeleteLists(rejected, 1);
            GLStateManager.disableBlend();
        }
    }

    @Test
    void simultaneousListStartsClaimOneOwner() throws Exception {
        sharedDrawable = new SharedDrawable(Display.getDrawable());
        final int first = GLStateManager.glGenLists(1);
        final int second = GLStateManager.glGenLists(1);
        final CyclicBarrier start = new CyclicBarrier(2);
        final CountDownLatch attempted = new CountDownLatch(2);
        final AtomicInteger completed = new AtomicInteger();
        final AtomicInteger rejected = new AtomicInteger();
        final TestThreads.Worker worker = DrawableContexts.start(sharedDrawable, "SharedDrawableContext-List-Race-Thread", () -> {
            start.await();
            try {
                GLStateManager.glNewList(second, GL11.GL_COMPILE);
            } catch (IllegalStateException expected) {
                rejected.incrementAndGet();
                attempted.countDown();
                return;
            }
            attempted.countDown();
            if (!attempted.await(5, TimeUnit.SECONDS)) throw new AssertionError("attempts timed out");
            GLStateManager.glEndList();
            completed.incrementAndGet();
        });
        try {
            start.await();
            try {
                GLStateManager.glNewList(first, GL11.GL_COMPILE);
            } catch (IllegalStateException expected) {
                rejected.incrementAndGet();
                attempted.countDown();
            }
            if (GLStateManager.getListMode() != 0) {
                attempted.countDown();
                assertTrue(attempted.await(5, TimeUnit.SECONDS));
                GLStateManager.glEndList();
                completed.incrementAndGet();
            }
            worker.join();
        } finally {
            GLStateManager.glDeleteLists(first, 1);
            GLStateManager.glDeleteLists(second, 1);
        }
        assertEquals(1, completed.get());
        assertEquals(1, rejected.get());
    }

    @Test
    void reenteredContextRebindsFfpProgram() throws Exception {
        sharedDrawable = new SharedDrawable(Display.getDrawable());

        DrawableContexts.run(sharedDrawable, "SharedDrawableContext-Reentry-Thread", () -> {
            final ShaderManager sm = ShaderManager.getInstance();
            final boolean wasEnabled = ShaderManager.isEnabled();
            ShaderManager.enable();
            sm.activate();
            try {
                final FloatBuffer vertices = BufferUtils.createFloatBuffer(9);
                vertices.put(new float[] { -0.5f, -0.5f, 0f, 0.5f, -0.5f, 0f, 0f, 0.5f, 0f });
                vertices.flip();
                final int vbo = GLStateManager.glGenBuffers();
                GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
                GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STATIC_DRAW);
                FfpFixture.attrib(0, 3, GL11.GL_FLOAT, false, 12, 0);
                VAOManager.setCurrentVertexFlags(0);

                GLStateManager.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

                GLStateManager.releaseContext(sharedDrawable);
                GLStateManager.makeCurrent(sharedDrawable);

                GLStateManager.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

                assertNotEquals(0, GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), "invalidateProgram must force a rebind after replay clears the program to 0");

                GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
                GLStateManager.glDeleteBuffers(vbo);
            } finally {
                sm.deactivate();
                if (!wasEnabled) ShaderManager.disable();
            }
        });
    }

    @Test
    void deferredTextureDeletesWaitForSplashAndScrubDrawableContexts() throws Exception {
        sharedDrawable = new SharedDrawable(Display.getDrawable());
        final int t = GLStateManager.glGenTextures();

        final AtomicReference<GLContextState> workerCtxRef = new AtomicReference<>();
        DrawableContexts.run(sharedDrawable, "SharedDrawableContext-DeferredDelete-Thread", () -> {
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, t);
            workerCtxRef.set(GLStateManager.ctx());
        });

        final GLContextState workerCtx = workerCtxRef.get();
        int deferredGen = 0;
        int flushGen = 0;

        try {
            SplashWindow.setSplashComplete(false);
            GLStateManager.glDeleteTextures(t);
            deferredGen = GLStateManager.glGenTextures();

            assertTrue(GL11.glIsTexture(t), "delete must stay deferred while the splash is not complete");
            assertEquals(t, workerCtx.textures.getTextureUnitBindings(0).getBinding(), "worker ctx binding must survive until the flush");

            SplashWindow.setSplashComplete(true);
            flushGen = GLStateManager.glGenTextures();

            assertFalse(GL11.glIsTexture(t), "the flush must delete the texture once the splash is complete");
            assertEquals(0, workerCtx.textures.getTextureUnitBindings(0).getBinding(), "the flush must scrub the drawable context's binding");
        } finally {
            SplashWindow.setSplashComplete(true);
            if (deferredGen != 0) GL11.glDeleteTextures(deferredGen);
            if (flushGen != 0) GL11.glDeleteTextures(flushGen);
        }
    }

    @Test
    void concurrentContextsDoNotShareAttribOrCurrentState() throws Exception {
        final GLContextState primary = GLStateManager.ctx();
        sharedDrawable = new SharedDrawable(Display.getDrawable());

        final CyclicBarrier barrier = new CyclicBarrier(2);
        final AtomicReference<GLContextState> workerCtxRef = new AtomicReference<>();
        final TestThreads.Worker worker = DrawableContexts.start(sharedDrawable, "SharedDrawableContext-Concurrent-Thread", () -> {
            workerCtxRef.set(GLStateManager.ctx());
            barrier.await();
            for (int i = 0; i < 20000; i++) {
                final int t = GLStateManager.pushState(StateSet.FONT);
                GLStateManager.glNormal3f(0.1f, 0.2f, 0.3f);
                GLStateManager.popStateTo(t);
            }
            GLStateManager.glNormal3f(0.1f, 0.2f, 0.3f);
        });

        try {
            barrier.await();
            for (int i = 0; i < 20000; i++) {
                final int t = GLStateManager.pushState(StateSet.FONT);
                GLStateManager.glNormal3f(0.25f, 0.5f, 0.75f);
                GLStateManager.popStateTo(t);
            }
            GLStateManager.glNormal3f(0.25f, 0.5f, 0.75f);
            worker.join();

            final GLContextState workerCtx = workerCtxRef.get();
            assertNotSame(primary, workerCtx);
            assertEquals(0, primary.attribDepth);
            assertEquals(0, workerCtx.attribDepth);
            assertEquals(0.25f, primary.ffp.currentNormal.x, 0.0f);
            assertEquals(0.5f, primary.ffp.currentNormal.y, 0.0f);
            assertEquals(0.75f, primary.ffp.currentNormal.z, 0.0f);
            assertEquals(0.1f, workerCtx.ffp.currentNormal.x, 0.0f);
            assertEquals(0.2f, workerCtx.ffp.currentNormal.y, 0.0f);
            assertEquals(0.3f, workerCtx.ffp.currentNormal.z, 0.0f);
        } finally {
            GLStateManager.glNormal3f(0.0f, 0.0f, 1.0f);
        }
    }
}
