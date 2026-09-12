package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.SharedDrawable;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
class SharedTextureDeletionTest {

    @Test
    void reallocationSurvivesPendingDeletesFromSharedContext() throws Exception {
        final int[] textures = new int[32];
        final SharedDrawable shared = new SharedDrawable(Display.getDrawable());
        final CountDownLatch deleted = new CountDownLatch(1);
        final CountDownLatch finish = new CountDownLatch(1);
        final CompletableFuture<Void> completed = new CompletableFuture<>();
        final boolean splashComplete = SplashWindow.isSplashComplete();
        SplashWindow.setSplashComplete(false);
        GLStateManager.setDrawableGL(Display.getDrawable());
        GLStateManager.makeCurrent(Display.getDrawable());
        final Thread worker = new Thread(() -> {
            try {
                GLStateManager.makeCurrent(shared);
                for (int texture : textures) GLStateManager.glDeleteTextures(texture);
                deleted.countDown();
                assertTrue(finish.await(5, TimeUnit.SECONDS));
                GL11.glFinish();
                shared.releaseContext();
                completed.complete(null);
            } catch (Throwable t) {
                deleted.countDown();
                completed.completeExceptionally(t);
            }
        }, "texture-delete-test");
        try {
            for (int i = 0; i < textures.length; i++) textures[i] = allocateTexture(64);
            GL11.glFinish();
            worker.start();
            assertTrue(deleted.await(5, TimeUnit.SECONDS));

            // Recycle the names on the display context before the shared context finishes
            // its commands, as happens when BLS recreates Smooth Font pages during reload.
            for (int i = 0; i < textures.length; i++) textures[i] = allocateTexture(128);
            GL11.glFinish();
            finish.countDown();
            completed.get(5, TimeUnit.SECONDS);

            for (int texture : textures) {
                GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture);
                assertEquals(128, GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH));
                assertEquals(GL11.GL_RGBA, GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT));
            }
        } finally {
            finish.countDown();
            worker.join(5000);
            shared.destroy();
            GLStateManager.setDrawableGL(null);
            GLStateManager.setDrawableGLHolder(Thread.currentThread());
            SplashWindow.setSplashComplete(splashComplete);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            for (int texture : textures) GL11.glDeleteTextures(texture);
        }
    }

    private static int allocateTexture(int size) {
        final int texture = GLStateManager.glGenTextures();
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, size, size, 0,
            GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        return texture;
    }
}
