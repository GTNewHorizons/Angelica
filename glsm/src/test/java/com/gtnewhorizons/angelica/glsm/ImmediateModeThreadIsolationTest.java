package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizon.gtnhlib.client.renderer.DirectTessellator;
import com.gtnewhorizons.angelica.glsm.recording.CompiledDisplayList;
import com.gtnewhorizons.angelica.glsm.recording.GLCommand;
import com.gtnewhorizons.angelica.glsm.recording.ImmediateModeRecorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(GLSMCoreExtension.class)
class ImmediateModeThreadIsolationTest {

    @Test
    void backgroundThreadsGetDistinctTessellators() throws Exception {
        final DirectTessellator mainTess = ImmediateModeRecorder.getInternalTessellator();
        final AtomicReference<DirectTessellator> bg1 = new AtomicReference<>();
        final AtomicReference<DirectTessellator> bg2 = new AtomicReference<>();

        final Thread t1 = new Thread(() -> bg1.set(ImmediateModeRecorder.getInternalTessellator()), "iso-test-1");
        final Thread t2 = new Thread(() -> bg2.set(ImmediateModeRecorder.getInternalTessellator()), "iso-test-2");
        t1.start(); t2.start();
        t1.join(); t2.join();

        assertNotNull(bg1.get());
        assertNotNull(bg2.get());
        assertNotSame(mainTess, bg1.get(), "background thread must not share the main-thread tessellator");
        assertNotSame(mainTess, bg2.get(), "background thread must not share the main-thread tessellator");
        assertNotSame(bg1.get(), bg2.get(), "background threads must not share a tessellator");
    }

    @Test
    void concurrentImmediateModeDoesNotCorruptRecording() throws Exception {
        final List<Throwable> failures = new CopyOnWriteArrayList<>();
        final CountDownLatch start = new CountDownLatch(1);
        final AtomicBoolean stop = new AtomicBoolean(false);

        final Runnable spam = () -> {
            try {
                start.await();
                while (!stop.get()) {
                    GLStateManager.glBegin(GL11.GL_QUADS);
                    GLStateManager.glVertex3f(0, 0, 0);
                    GLStateManager.glVertex3f(1, 0, 0);
                    GLStateManager.glVertex3f(1, 1, 0);
                    GLStateManager.glVertex3f(0, 1, 0);
                    GLStateManager.glEnd();
                }
            } catch (Throwable t) {
                failures.add(t);
            }
        };
        final Thread t1 = new Thread(spam, "iso-spam-1");
        final Thread t2 = new Thread(spam, "iso-spam-2");
        t1.start(); t2.start();
        start.countDown();

        final List<Integer> lists = new ArrayList<>();
        try {
            for (int i = 0; i < 50; i++) {
                final int list = GLStateManager.glGenLists(1);
                GLStateManager.glNewList(list, GL11.GL_COMPILE);
                GLStateManager.glBegin(GL11.GL_QUADS);
                GLStateManager.glVertex3f(0, 0, 0);
                GLStateManager.glVertex3f(1, 0, 0);
                GLStateManager.glVertex3f(1, 1, 0);
                GLStateManager.glVertex3f(0, 1, 0);
                GLStateManager.glEnd();
                GLStateManager.glEndList();
                lists.add(list);
            }
        } finally {
            stop.set(true);
            t1.join(5000); t2.join(5000);
        }

        assertTrue(failures.isEmpty(), "background immediate mode threw: " + failures);
        for (int list : lists) {
            final CompiledDisplayList compiled = DisplayListManager.getDisplayList(list);
            assertNotNull(compiled, "list " + list + " should be compiled");
            assertEquals(1, compiled.getCommandCounts().getOrDefault(GLCommand.DRAW_RANGE, 0), "list " + list + " should contain exactly the main thread's draw");
            GLStateManager.glDeleteLists(list, 1);
        }
    }

    @Test
    void sweepFreesDeadThreadTessellators() throws Exception {
        final Thread t = new Thread(() -> ImmediateModeRecorder.getInternalTessellator(), "iso-sweep");
        t.start();
        t.join();
        final int before = ImmediateModeRecorder.getThreadTessellatorCount();
        assertTrue(before >= 1);
        ImmediateModeRecorder.cleanupOrphanTessellators();
        assertTrue(ImmediateModeRecorder.getThreadTessellatorCount() < before, "dead thread's tessellator should be swept");
    }

    @Test
    void drawLockGatesOnSplashComplete() throws Exception {
        assertFalse(GLStateManager.acquireDrawLock(), "post-splash acquire must be a no-op");

        final Field f = GLStateManager.class.getDeclaredField("splashComplete");
        f.setAccessible(true);
        f.set(null, false);
        try {
            assertTrue(GLStateManager.acquireDrawLock(), "during splash the lock must engage");
            GLStateManager.releaseDrawLock();
        } finally {
            f.set(null, true);
        }
    }
}
