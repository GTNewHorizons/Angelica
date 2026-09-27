package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.ffp.FfpFixture;
import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import com.gtnewhorizons.angelica.glsm.ffp.VAOManager;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
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
import java.util.Arrays;
import java.util.Map;
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
import static org.junit.jupiter.api.Assertions.fail;

@GLCoreTest
public class GLSM_SharedDrawableContext_GLTest {

    private SharedDrawable sharedDrawable;

    @AfterEach
    void cleanup() throws Exception {
        if (sharedDrawable != null) {
            final Map<Object, GLContextState> contexts = Reflect.getStatic(GLStateManager.class, "drawableContexts");
            final GLContextState ctx = contexts.remove(sharedDrawable);
            if (ctx != null) {
                final GLContextState[] states = Reflect.getStatic(GLStateManager.class, "drawableContextStates");
                Reflect.setStatic(GLStateManager.class, "drawableContextStates", Arrays.stream(states).filter(s -> s != ctx).toArray(GLContextState[]::new));
            }
            sharedDrawable.destroy();
            sharedDrawable = null;
        }
    }

    @Test
    void sharedDrawableBindsItsOwnContext() throws Exception {
        final GLContextState primary = Reflect.getStatic(GLStateManager.class, "primaryContext");
        sharedDrawable = new SharedDrawable(Display.getDrawable());

        final AtomicReference<Throwable> error = new AtomicReference<>();
        final Thread worker = new Thread(() -> {
            try {
                GLStateManager.makeCurrent(sharedDrawable);

                final GLContextState workerCtx = GLStateManager.ctx();
                assertNotSame(primary, workerCtx);
                assertNotEquals(0, workerCtx.defaultVAO);
                assertNotEquals(primary.defaultVAO, workerCtx.defaultVAO);
                assertEquals(workerCtx.defaultVAO, GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING));

                final IntBuffer viewport = BufferUtils.createIntBuffer(16);
                GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
                assertEquals(0, viewport.get(0));
                assertEquals(0, viewport.get(1));
                assertEquals(Display.getWidth(), viewport.get(2));
                assertEquals(Display.getHeight(), viewport.get(3));

                GLStateManager.releaseContext(sharedDrawable);
                assertSame(primary, GLStateManager.ctx());
            } catch (Throwable t) {
                error.set(t);
            }
        }, "SharedDrawableContext-Bind-Thread");
        worker.start();
        worker.join(5000);
        assertFalse(worker.isAlive(), "worker thread did not finish");

        if (error.get() != null) {
            fail(error.get());
        }

        final boolean workerContextsActive = Reflect.getStatic(GLStateManager.class, "workerContextsActive");
        assertFalse(workerContextsActive, "worker contexts should be inactive after release");
    }

    @Test
    void failedContextSwitchRestoresPreviousBinding() throws Exception {
        final GLContextState primary = GLStateManager.ctx();
        sharedDrawable = new SharedDrawable(Display.getDrawable());
        final CountDownLatch acquired = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final Thread worker = new Thread(() -> {
            try {
                GLStateManager.makeCurrent(sharedDrawable);
                acquired.countDown();
                if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("release timed out");
                GLStateManager.releaseContext(sharedDrawable);
            } catch (Throwable t) {
                error.set(t);
                acquired.countDown();
            }
        }, "SharedDrawableContext-Owner-Thread");
        worker.start();
        try {
            assertTrue(acquired.await(5, TimeUnit.SECONDS));
            if (error.get() != null) fail(error.get());
            final int count = Reflect.getStatic(GLStateManager.class, "workerContextCount");
            assertThrows(IllegalStateException.class, () -> GLStateManager.makeCurrent(sharedDrawable));
            assertSame(primary, GLStateManager.ctx());
            assertEquals(count, (int) Reflect.getStatic(GLStateManager.class, "workerContextCount"));
            assertEquals(primary.defaultVAO, GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING));
        } finally {
            release.countDown();
            worker.join(5000);
        }
        assertFalse(worker.isAlive());
        if (error.get() != null) fail(error.get());
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
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final Thread worker = new Thread(() -> {
            try {
                GLStateManager.makeCurrent(sharedDrawable);
                GLStateManager.glNewList(active, GL11.GL_COMPILE);
                started.countDown();
                if (!finish.await(5, TimeUnit.SECONDS)) throw new AssertionError("finish timed out");
                GLStateManager.glEndList();
                GLStateManager.releaseContext(sharedDrawable);
            } catch (Throwable t) {
                error.set(t);
                started.countDown();
            }
        }, "SharedDrawableContext-List-Thread");
        worker.start();
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            if (error.get() != null) fail(error.get());
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
            worker.join(5000);
        }
        try {
            assertFalse(worker.isAlive());
            if (error.get() != null) fail(error.get());
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
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final Thread worker = new Thread(() -> {
            try {
                GLStateManager.makeCurrent(sharedDrawable);
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
            } catch (Throwable t) {
                error.set(t);
                attempted.countDown();
            } finally {
                try {
                    GLStateManager.releaseContext(sharedDrawable);
                } catch (Throwable t) {
                    error.set(t);
                }
            }
        }, "SharedDrawableContext-List-Race-Thread");
        worker.start();
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
        } finally {
            worker.join(5000);
            GLStateManager.glDeleteLists(first, 1);
            GLStateManager.glDeleteLists(second, 1);
        }
        assertFalse(worker.isAlive());
        if (error.get() != null) fail(error.get());
        assertEquals(1, completed.get());
        assertEquals(1, rejected.get());
    }

    @Test
    void reenteredContextRebindsFfpProgram() throws Exception {
        sharedDrawable = new SharedDrawable(Display.getDrawable());

        final AtomicReference<Throwable> error = new AtomicReference<>();
        final Thread worker = new Thread(() -> {
            try {
                GLStateManager.makeCurrent(sharedDrawable);

                final ShaderManager sm = ShaderManager.getInstance();
                final boolean wasEnabled = ShaderManager.isEnabled();
                ShaderManager.enable();
                sm.activate();

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

                sm.deactivate();
                if (!wasEnabled) ShaderManager.disable();
                GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
                GLStateManager.glDeleteBuffers(vbo);

                GLStateManager.releaseContext(sharedDrawable);
            } catch (Throwable t) {
                error.set(t);
            }
        }, "SharedDrawableContext-Reentry-Thread");
        worker.start();
        worker.join(10000);
        assertFalse(worker.isAlive(), "worker thread did not finish");

        if (error.get() != null) {
            fail(error.get());
        }
    }

    @Test
    void deferredTextureDeletesWaitForSplashAndScrubDrawableContexts() throws Exception {
        sharedDrawable = new SharedDrawable(Display.getDrawable());
        final int t = GLStateManager.glGenTextures();

        final AtomicReference<Throwable> error = new AtomicReference<>();
        final AtomicReference<GLContextState> workerCtxRef = new AtomicReference<>();
        final Thread worker = new Thread(() -> {
            try {
                GLStateManager.makeCurrent(sharedDrawable);
                GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, t);
                workerCtxRef.set(GLStateManager.ctx());
                GLStateManager.releaseContext(sharedDrawable);
            } catch (Throwable th) {
                error.set(th);
            }
        }, "SharedDrawableContext-DeferredDelete-Thread");
        worker.start();
        worker.join(5000);
        assertFalse(worker.isAlive(), "worker thread did not finish");

        if (error.get() != null) {
            fail(error.get());
        }

        final GLContextState workerCtx = workerCtxRef.get();
        int deferredGen = 0;
        int flushGen = 0;

        try {
            SplashWindow.setSplashComplete(false);
            GLStateManager.glDeleteTextures(t);
            deferredGen = GLStateManager.glGenTextures();

            final IntOpenHashSet deferred = Reflect.getStatic(GLStateManager.class, "deferredDeleteTextures");
            assertTrue(deferred.contains(t), "delete must stay deferred while the splash is not complete");
            assertEquals(t, workerCtx.textures.getTextureUnitBindings(0).getBinding(), "worker ctx binding must survive until the flush");

            SplashWindow.setSplashComplete(true);
            flushGen = GLStateManager.glGenTextures();

            assertTrue(deferred.isEmpty(), "the flush must clear the deferred set once the splash is complete");
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
        final AtomicReference<Throwable> mainError = new AtomicReference<>();
        final AtomicReference<Throwable> workerError = new AtomicReference<>();
        final AtomicReference<GLContextState> workerCtxRef = new AtomicReference<>();

        final Thread worker = new Thread(() -> {
            try {
                GLStateManager.makeCurrent(sharedDrawable);
                workerCtxRef.set(GLStateManager.ctx());
                barrier.await();
                for (int i = 0; i < 20000; i++) {
                    final int t = GLStateManager.pushState(StateSet.FONT);
                    GLStateManager.glNormal3f(0.1f, 0.2f, 0.3f);
                    GLStateManager.popStateTo(t);
                }
                GLStateManager.glNormal3f(0.1f, 0.2f, 0.3f);
            } catch (Throwable t) {
                workerError.set(t);
            } finally {
                try {
                    GLStateManager.releaseContext(sharedDrawable);
                } catch (Throwable t) {
                    if (workerError.get() == null) workerError.set(t);
                }
            }
        }, "SharedDrawableContext-Concurrent-Thread");
        worker.start();

        try {
            barrier.await();
            for (int i = 0; i < 20000; i++) {
                final int t = GLStateManager.pushState(StateSet.FONT);
                GLStateManager.glNormal3f(0.25f, 0.5f, 0.75f);
                GLStateManager.popStateTo(t);
            }
            GLStateManager.glNormal3f(0.25f, 0.5f, 0.75f);
        } catch (Throwable t) {
            mainError.set(t);
        }

        worker.join(10000);
        assertFalse(worker.isAlive(), "worker thread did not finish");

        if (mainError.get() != null) {
            fail(mainError.get());
        }
        if (workerError.get() != null) {
            fail(workerError.get());
        }

        try {
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
