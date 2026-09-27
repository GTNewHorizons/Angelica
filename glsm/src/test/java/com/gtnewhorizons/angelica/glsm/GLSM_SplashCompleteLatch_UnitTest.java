package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.Display;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@GLCoreTest
public class GLSM_SplashCompleteLatch_UnitTest {

    @BeforeEach
    void enterSplashWindow() throws IllegalAccessException {
        SplashWindow.enter();
    }

    @AfterEach
    void leaveSplashWindow() throws IllegalAccessException {
        SplashWindow.leave();
    }

    @Test
    void markLatchesAndReleasesTheDrawableHolder() {
        assertFalse(GLStateManager.isSplashComplete());

        GLStateManager.markSplashComplete("test");

        assertTrue(GLStateManager.isSplashComplete());
        assertNull(GLStateManager.getDrawableGLHolder(), "holder must be released so caching stops keying off it");
    }

    @Test
    void secondMarkIsANoOp() {
        GLStateManager.markSplashComplete("first");
        final Thread reacquired = new Thread("late-holder");
        GLStateManager.setDrawableGLHolder(reacquired);

        GLStateManager.markSplashComplete("second");

        assertTrue(GLStateManager.isSplashComplete());
        assertSame(reacquired, GLStateManager.getDrawableGLHolder(), "a second mark must not re-run the latch body");
    }

    @Test
    void cachingIsThreadScopedBeforeTheLatchAndGlobalAfter() throws Exception {
        final AtomicBoolean cachingOffThread = new AtomicBoolean(true);
        final Runnable probe = () -> cachingOffThread.set(GLStateManager.isCachingEnabled());

        Thread t = new Thread(probe, "non-holder");
        t.start();
        t.join();
        assertFalse(cachingOffThread.get(), "caching must be holder-scoped during the dual-context window");

        GLStateManager.markSplashComplete("test");

        t = new Thread(probe, "non-holder");
        t.start();
        t.join();
        assertTrue(cachingOffThread.get(), "after the latch caching is global - this is what the latch buys");
    }

    @Test
    void pausedSplashThreadDoesNotCompleteUntilItExits() throws Exception {
        final CountDownLatch released = new CountDownLatch(1);
        final CountDownLatch resume = new CountDownLatch(1);
        final AtomicReference<Throwable> workerError = new AtomicReference<>();

        GLStateManager.releaseContext(Display.getDrawable());

        final Thread worker = new Thread(() -> {
            try {
                GLStateManager.makeCurrent(Display.getDrawable());
                GLStateManager.releaseContext(Display.getDrawable());
                released.countDown();
                resume.await();
            } catch (Throwable t) {
                workerError.set(t);
            }
        }, "SplashPause-Worker-Thread");

        try {
            worker.start();
            assertTrue(released.await(10, TimeUnit.SECONDS), "worker did not release the Display drawable in time");

            GLStateManager.makeCurrent(Display.getDrawable());
            assertFalse(GLStateManager.isSplashComplete());

            resume.countDown();
            worker.join(10000);
            assertFalse(worker.isAlive(), "worker thread did not finish");

            if (workerError.get() != null) {
                fail(workerError.get());
            }

            GLStateManager.releaseContext(Display.getDrawable());
            GLStateManager.makeCurrent(Display.getDrawable());
            assertTrue(GLStateManager.isSplashComplete());
        } finally {
            resume.countDown();
            Reflect.setStatic(GLStateManager.class, "stateSeedPending", false);
            Reflect.setStatic(GLStateManager.class, "splashDisplayReleaser", null);
            GLStateManager.makeCurrent(Display.getDrawable());
        }
    }
}
