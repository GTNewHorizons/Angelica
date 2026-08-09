package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
