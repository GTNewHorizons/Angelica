package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.SharedDrawable;

import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for server-side GL state synchronization between SharedDrawable and DrawableGL contexts.
 *
 * Server-side state (texture parameters, texture data) is shared across GL contexts and must
 * be properly synchronized in the GLSM cache. Client-side state (texture bindings, active unit)
 * is per-context and should NOT be shared.
 */
@GLCompatTest
public class GLSM_ServerSideState_UnitTest {

    private SharedDrawable sharedDrawable;
    private Thread backgroundThread;
    private CountDownLatch initLatch;
    private CountDownLatch commandLatch;
    private CountDownLatch completeLatch;
    private CountDownLatch readyLatch;
    private Runnable pendingCommand;
    private Throwable backgroundError;
    private volatile boolean shutdownRequested;
    private volatile GLContextState workerContext;
    private volatile boolean workerCaching;
    private boolean originalSplashComplete;
    private Thread originalDrawableGLHolder;

    @BeforeEach
    void setupSharedDrawable() throws Exception {
        originalSplashComplete = SplashWindow.isSplashComplete();
        originalDrawableGLHolder = GLStateManager.getDrawableGLHolder();

        // Reset splash state to simulate splash screen scenario
        SplashWindow.setSplashComplete(false);

        sharedDrawable = new SharedDrawable(Display.getDrawable());

        // Register the main display context (DrawableGL) so makeCurrent() can identify it
        GLStateManager.setDrawableGL(Display.getDrawable());

        // Switch to DrawableGL to enable caching for main thread
        GLStateManager.makeCurrent(Display.getDrawable());

        // Verify main thread has caching enabled
        assertTrue(GLStateManager.isCachingEnabled(), "Main thread should have caching enabled (it holds DrawableGL)");

        shutdownRequested = false;
        backgroundError = null;
        workerContext = null;
        workerCaching = false;
        initLatch = new CountDownLatch(1);
        commandLatch = new CountDownLatch(1);
        completeLatch = new CountDownLatch(1);
        readyLatch = new CountDownLatch(1);

        backgroundThread = new Thread(() -> {
            try {
                GLStateManager.makeCurrent(sharedDrawable);
                workerContext = GLStateManager.ctx();
                workerCaching = GLStateManager.isCachingEnabled();

                // Signal that initialization is complete
                initLatch.countDown();

                while (!shutdownRequested) {
                    // Signal ready for next command
                    readyLatch.countDown();

                    // Wait for a command
                    if (commandLatch.await(100, TimeUnit.MILLISECONDS)) {
                        if (pendingCommand != null && !shutdownRequested) {
                            try {
                                pendingCommand.run();
                            } catch (Throwable t) {
                                backgroundError = t;
                            }
                            pendingCommand = null;
                            completeLatch.countDown();
                        }
                    }
                }
            } catch (InterruptedException e) {
                // Normal shutdown
            } catch (Throwable t) {
                backgroundError = t;
                initLatch.countDown();
            } finally {
                try {
                    GLStateManager.releaseContext(sharedDrawable);
                } catch (LWJGLException e) {
                    // Ignore on cleanup
                }
            }
        }, "SharedDrawable-Test-Thread");
        backgroundThread.start();

        // Wait for background thread to complete initialization
        assertTrue(initLatch.await(5, TimeUnit.SECONDS), "Background thread failed to initialize");
        if (backgroundError != null) {
            fail("Background thread failed to make SharedDrawable current", backgroundError);
        }
        assertTrue(workerCaching, "SharedDrawable thread should cache into its own context");
        assertNotSame(GLStateManager.ctx(), workerContext, "SharedDrawable thread must not share the main thread's context");
    }

    @AfterEach
    void teardownSharedDrawable() throws Exception {
        shutdownRequested = true;
        commandLatch.countDown(); // Wake up the thread if waiting

        if (backgroundThread != null) {
            backgroundThread.join(1000);
            if (backgroundThread.isAlive()) {
                backgroundThread.interrupt();
                backgroundThread.join(1000);
            }
        }

        if (sharedDrawable != null) {
            DrawableContexts.destroy(sharedDrawable);
        }

        // Restore original state
        GLStateManager.setDrawableGL(null);
        GLStateManager.setDrawableGLHolder(originalDrawableGLHolder);
        SplashWindow.setSplashComplete(originalSplashComplete);
        if (originalSplashComplete) {
            GLStateManager.markSplashComplete("test");
        }
    }

    /**
     * Execute a command on the SharedDrawable thread and wait for completion.
     */
    private void executeOnSharedDrawable(Runnable command) throws InterruptedException {
        // Wait for background thread to be ready for a command
        assertTrue(readyLatch.await(5, TimeUnit.SECONDS), "Background thread not ready");

        // Reset latches for this command cycle
        readyLatch = new CountDownLatch(1);
        commandLatch = new CountDownLatch(1);
        completeLatch = new CountDownLatch(1);

        // Set command and signal
        pendingCommand = command;
        commandLatch.countDown();

        // Wait for completion
        assertTrue(completeLatch.await(5, TimeUnit.SECONDS), "SharedDrawable command timed out");

        if (backgroundError != null) {
            fail("Background thread error: " + backgroundError.getMessage(), backgroundError);
        }
    }

    /**
     * Diagnostic test: Verify that SharedDrawable actually shares GL namespace with Display.
     * This is a prerequisite for all other tests - if sharing doesn't work, texture params won't be shared.
     */
    @Test
    void testContextSharingVerification() throws InterruptedException {
        int texMain = GL11.glGenTextures();

        assertTrue(GLStateManager.isCachingEnabled(), "Main thread should have caching enabled (it holds DrawableGL)");

        final int[] sharedResult = new int[1];
        executeOnSharedDrawable(() -> {
            GL11.glGetError();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texMain);
            GL11.glGetError();
            sharedResult[0] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        });

        assertEquals(texMain, sharedResult[0], "SharedDrawable should be able to bind texture created on main thread - contexts must share GL namespace");

        final int[] texShared = new int[1];
        executeOnSharedDrawable(() -> texShared[0] = GL11.glGenTextures());

        GL11.glGetError();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texShared[0]);
        GL11.glGetError();
        int mainBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

        assertEquals(texShared[0], mainBinding, "Main thread should be able to bind texture created on SharedDrawable - contexts must share GL namespace");

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL11.glDeleteTextures(texMain);
        GL11.glDeleteTextures(texShared[0]);
    }

    /** One texture parameter through its typed GLSM setter and getter, plus the raw driver read. Values travel as float. */
    private static final class TexParam {
        final String name;
        final float initial;
        final float changed;
        final FloatConsumer set;
        final FloatSupplier glsm;
        final FloatSupplier driver;

        TexParam(String name, float initial, float changed, FloatConsumer set, FloatSupplier glsm, FloatSupplier driver) {
            this.name = name;
            this.initial = initial;
            this.changed = changed;
            this.set = set;
            this.glsm = glsm;
            this.driver = driver;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private interface FloatConsumer {
        void accept(float value);
    }

    private interface FloatSupplier {
        float get();
    }

    static Stream<TexParam> texParams() {
        return Stream.of(
            new TexParam("glTexParameteri MIN_FILTER", GL11.GL_LINEAR, GL11.GL_NEAREST,
                v -> GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, (int) v),
                () -> GLStateManager.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER),
                () -> GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER)),
            new TexParam("glTexParameterf LOD_BIAS", 0.0f, 1.5f,
                v -> GLStateManager.glTexParameterf(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_LOD_BIAS, v),
                () -> GLStateManager.glGetTexParameterf(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_LOD_BIAS),
                () -> GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_LOD_BIAS)));
    }

    /**
     * Test: SharedDrawable sets a texture parameter, verify both GLSM and actual GL state are correct on both contexts.
     */
    @ParameterizedTest
    @MethodSource("texParams")
    void testTexParameterFromSharedDrawable(TexParam param) throws InterruptedException {
        int texId = GL11.glGenTextures();

        try {
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texId);
            param.set.accept(param.initial);

            assertEquals(param.initial, param.glsm.get(), 0.001f, "Initial GLSM value");
            assertEquals(param.initial, param.driver.get(), 0.001f, "Initial GL value");

            executeOnSharedDrawable(() -> {
                GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texId);

                int boundTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                assertEquals(texId, boundTex, "SharedDrawable should have texId bound");

                param.set.accept(param.changed);

                assertEquals(param.changed, param.glsm.get(), 0.001f, "SharedDrawable GLSM value");
                assertEquals(param.changed, param.driver.get(), 0.001f, "SharedDrawable GL value");
            });

            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texId);

            assertEquals(param.changed, param.glsm.get(), 0.001f, "Main thread GLSM value");
            assertEquals(param.changed, param.driver.get(), 0.001f, "Main thread GL value");

        } finally {
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GLStateManager.glDeleteTextures(texId);
        }
    }

    /**
     * Test: SharedDrawable binds a DIFFERENT texture than main thread, then sets params.
     *
     * This verifies that when SharedDrawable sets params on texIdA while main thread has texIdB bound, the params go to the correct texture (texIdA).
     */
    @Test
    void testWrongTextureUpdatedFromSharedDrawable() throws InterruptedException {
        int texIdA = GL11.glGenTextures();
        int texIdB = GL11.glGenTextures();

        try {
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texIdA);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);

            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texIdB);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);

            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texIdA);
            int initialA_GLSM = GLStateManager.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
            int initialA_GL = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
            assertEquals(GL11.GL_LINEAR, initialA_GLSM, "texIdA initial GLSM should be LINEAR");
            assertEquals(GL11.GL_LINEAR, initialA_GL, "texIdA initial GL should be LINEAR");

            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texIdB);
            int initialB_GLSM = GLStateManager.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
            int initialB_GL = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
            assertEquals(GL11.GL_LINEAR_MIPMAP_LINEAR, initialB_GLSM, "texIdB initial GLSM should be LINEAR_MIPMAP_LINEAR");
            assertEquals(GL11.GL_LINEAR_MIPMAP_LINEAR, initialB_GL, "texIdB initial GL should be LINEAR_MIPMAP_LINEAR");

            assertEquals(texIdB, GLStateManager.getBoundTextureForServerState(), "Main thread should have texIdB bound");

            // SharedDrawable: bind texIdA and change its MIN_FILTER to NEAREST
            // Main thread has texIdB bound, but SharedDrawable should correctly update texIdA's params, not texIdB's
            executeOnSharedDrawable(() -> {
                GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texIdA);

                int bound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                assertEquals(texIdA, bound, "SharedDrawable should have texIdA bound");

                GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);

                int glsmValue = GLStateManager.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
                assertEquals(GL11.GL_NEAREST, glsmValue, "SharedDrawable GLSM should return NEAREST for texIdA");

                int glValue = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
                assertEquals(GL11.GL_NEAREST, glValue, "SharedDrawable GL should be NEAREST for texIdA");
            });

            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texIdA);
            int finalA_GLSM = GLStateManager.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
            int finalA_GL = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
            assertEquals(GL11.GL_NEAREST, finalA_GLSM, "texIdA final GLSM should be NEAREST (SharedDrawable's update)");
            assertEquals(GL11.GL_NEAREST, finalA_GL, "texIdA final GL should be NEAREST");

            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texIdB);
            int finalB_GLSM = GLStateManager.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
            int finalB_GL = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
            assertEquals(GL11.GL_LINEAR_MIPMAP_LINEAR, finalB_GLSM, "texIdB should NOT be modified by SharedDrawable");
            assertEquals(GL11.GL_LINEAR_MIPMAP_LINEAR, finalB_GL, "texIdB GL should NOT be modified");

        } finally {
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GLStateManager.glDeleteTextures(texIdA);
            GLStateManager.glDeleteTextures(texIdB);
        }
    }

    /**
     * Test that client-side state (texture binding) is correctly separate between contexts. Each context should have its own binding cache.
     */
    @Test
    void testClientStateSeparation() throws InterruptedException {
        int texId1 = GL11.glGenTextures();
        int texId2 = GL11.glGenTextures();

        try {
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texId1);
            assertEquals(texId1, GLStateManager.getBoundTextureForServerState(), "Main thread cache should show texId1 bound");

            executeOnSharedDrawable(() -> {
                GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texId2);
                assertEquals(texId2, GLStateManager.getBoundTextureForServerState(), "SharedDrawable cache should show texId2 bound");
                assertEquals(texId2, GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D), "SharedDrawable actual GL binding should be texId2");
            });

            assertEquals(texId1, GLStateManager.getBoundTextureForServerState(), "Main thread cache should still show texId1 (client-side state is per-context)");

            int actualBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            assertEquals(texId1, actualBinding, "Main thread actual GL binding should still be texId1");

        } finally {
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GLStateManager.glDeleteTextures(texId1);
            GLStateManager.glDeleteTextures(texId2);
        }
    }

    /**
     * Test glTexImage2D: SharedDrawable uploads texture data, verify dimensions cached correctly.
     */
    @Test
    void testTexImage2DFromSharedDrawable() throws InterruptedException {
        int texId = GL11.glGenTextures();

        try {
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texId);
            GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, 64, 64, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);

            int initialWidth = GLStateManager.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int initialHeight = GLStateManager.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            assertEquals(64, initialWidth, "Initial width should be 64");
            assertEquals(64, initialHeight, "Initial height should be 64");

            // SharedDrawable: re-upload with different dimensions
            executeOnSharedDrawable(() -> {
                GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texId);
                GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, 128, 256, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);

                int sharedWidth = GLStateManager.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
                int sharedHeight = GLStateManager.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
                assertEquals(128, sharedWidth, "SharedDrawable GLSM width should be 128");
                assertEquals(256, sharedHeight, "SharedDrawable GLSM height should be 256");

                int glWidth = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
                int glHeight = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
                assertEquals(128, glWidth, "SharedDrawable GL width should be 128");
                assertEquals(256, glHeight, "SharedDrawable GL height should be 256");
            });

            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texId);

            int finalWidth = GLStateManager.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int finalHeight = GLStateManager.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            assertEquals(128, finalWidth, "Main thread GLSM width should be 128");
            assertEquals(256, finalHeight, "Main thread GLSM height should be 256");

            int glWidth = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int glHeight = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            assertEquals(128, glWidth, "Main thread GL width should be 128");
            assertEquals(256, glHeight, "Main thread GL height should be 256");

        } finally {
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GLStateManager.glDeleteTextures(texId);
        }
    }

    @Test
    void testDeleteTexturesFromSharedDrawable() throws InterruptedException {
        final int texId = GLStateManager.glGenTextures();
        int flushGen = 0;

        try {
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texId);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, 32, 32, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);

            assertEquals(GL11.GL_LINEAR, GLStateManager.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER), "Cache should have LINEAR before delete");
            assertTrue(GL11.glIsTexture(texId), "Texture should exist before delete");

            executeOnSharedDrawable(() -> {
                GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texId);
                GLStateManager.glDeleteTextures(texId);
                assertEquals(0, GLStateManager.getBoundTextureForServerState(), "Delete should unbind from the deleting context");
                assertTrue(GL11.glIsTexture(texId), "Texture name should still be valid (deferred delete)");
            });

            assertEquals(texId, GLStateManager.getBoundTextureForServerState(), "Main thread binding should survive until the flush");

            final int deferredGen = GLStateManager.glGenTextures();
            GL11.glDeleteTextures(deferredGen);
            assertTrue(GL11.glIsTexture(texId), "Delete should stay deferred while the splash is not complete");

            SplashWindow.setSplashComplete(true);
            flushGen = GLStateManager.glGenTextures();
            assertFalse(GL11.glIsTexture(texId), "Texture should be gone after the post-splash flush");
            assertEquals(0, GLStateManager.getBoundTextureForServerState(), "Flush should scrub the main thread binding");

        } finally {
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            if (flushGen != 0) GL11.glDeleteTextures(flushGen);
            if (GL11.glIsTexture(texId)) {
                GL11.glDeleteTextures(texId);
            }
        }
    }
}
