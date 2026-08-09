package com.gtnewhorizons.angelica.glsm;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
public class GLSM_PerThread_UnitTest {

    private static void runThreaded(int n, ThreadBody body) throws Throwable {
        final CyclicBarrier barrier = new CyclicBarrier(n);
        final Throwable[] errs = new Throwable[n];
        final Thread[] ts = new Thread[n];
        for (int i = 0; i < n; i++) {
            final int tid = i;
            ts[i] = new Thread(() -> {
                try {
                    body.run(tid, barrier);
                } catch (Throwable t) {
                    errs[tid] = t;
                }
            }, "glsm-perthread-" + i);
        }
        for (Thread t : ts) t.start();
        for (Thread t : ts) t.join();
        for (int i = 0; i < n; i++) if (errs[i] != null) throw errs[i];
    }

    interface ThreadBody {
        void run(int tid, CyclicBarrier barrier) throws Exception;
    }

    @Test
    void holdersAreDistinctPerThreadAndDoNotBleed() throws Throwable {
        final int n = 6;
        final GLContextState mainHolder = GLStateManager.ctx();
        final GLContextState[] holders = new GLContextState[n];

        runThreaded(n, (tid, barrier) -> {
            final GLContextState c = GLStateManager.enterWorkerContext();
            try {
                holders[tid] = c;
                assertSame(c, GLStateManager.ctx(), "ctx() must be stable within a worker thread");
                assertNotSame(mainHolder, c, "worker holder must differ from the primary (main-thread) holder");

                c.activeProgram = 1000 + tid;
                c.boundVBO = 2000 + tid;
                c.mvGeneration = 3000 + tid;
                c.savedMvGen[0] = 4000 + tid;
                c.activeTextureUnit.setValue(tid);

                barrier.await();

                assertEquals(1000 + tid, c.activeProgram, "activeProgram bled across threads");
                assertEquals(2000 + tid, c.boundVBO, "boundVBO bled across threads");
                assertEquals(3000 + tid, c.mvGeneration, "mvGeneration bled across threads");
                assertEquals(4000 + tid, c.savedMvGen[0], "savedMvGen bled across threads");
                assertEquals(tid, c.activeTextureUnit.getValue(), "activeTextureUnit stack bled across threads");
            } finally {
                GLStateManager.exitWorkerContext();
            }
        });

        final Set<GLContextState> distinct = Collections.newSetFromMap(new IdentityHashMap<>());
        distinct.add(mainHolder);
        for (GLContextState h : holders) distinct.add(h);
        assertEquals(n + 1, distinct.size(), "every thread must own a distinct holder");
    }

    @Test
    void matrixStackAndMvGenerationArePerThread() throws Throwable {
        final int n = 6;
        runThreaded(n, (tid, barrier) -> {
            GLStateManager.enterWorkerContext();
            try {
                final int count = tid + 1;
                GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
                GLStateManager.glLoadIdentity();
                final int gen0 = GLStateManager.getMvGeneration();

                for (int k = 0; k < count; k++) GLStateManager.glTranslatef(1.0f, 0.0f, 0.0f);

                barrier.await();

                assertEquals(gen0 + count, GLStateManager.getMvGeneration(), "mvGeneration must advance only by this thread's own translate count");
                final Matrix4f m = new Matrix4f(GLStateManager.getModelViewMatrix());
                assertEquals((float) count, m.m30(), 1e-6f, "accumulated X translation must reflect only this thread's ops");
            } finally {
                GLStateManager.exitWorkerContext();
            }
        });
    }

    @Test
    void pushPopMatrixBalancesPerThread() throws Throwable {
        final int n = 4;
        runThreaded(n, (tid, barrier) -> {
            GLStateManager.enterWorkerContext();
            try {
                GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
                GLStateManager.glLoadIdentity();
                GLStateManager.glPushMatrix();
                GLStateManager.glTranslatef(5.0f + tid, 0.0f, 0.0f);
                assertEquals(5.0f + tid, new Matrix4f(GLStateManager.getModelViewMatrix()).m30(), 1e-6f, "translation must apply within the pushed scope");
                barrier.await();
                GLStateManager.glPopMatrix();
                assertEquals(0.0f, new Matrix4f(GLStateManager.getModelViewMatrix()).m30(), 1e-6f, "pop must restore this thread's matrix independent of other threads");
            } finally {
                GLStateManager.exitWorkerContext();
            }
        });
    }

    @Test
    void globalImmutableStateIdenticalAcrossThreads() throws Throwable {
        final int n = 6;
        final int units = GLStateManager.MAX_TEXTURE_UNITS;
        final Thread mainThread = GLStateManager.getMainThread();
        runThreaded(n, (tid, barrier) -> {
            assertEquals(units, GLStateManager.MAX_TEXTURE_UNITS, "limits must be global-immutable");
            assertSame(mainThread, GLStateManager.getMainThread(), "MainThread must be global-immutable");
        });
    }

    @Test
    void concurrentGetterSetterAndPushPopStress() throws Throwable {
        final int n = 8;
        final int iterations = 20_000;
        final AtomicInteger mismatches = new AtomicInteger();
        runThreaded(n, (tid, barrier) -> {
            final GLContextState c = GLStateManager.enterWorkerContext();
            try {
                GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
                GLStateManager.glLoadIdentity();
                barrier.await();
                for (int i = 0; i < iterations; i++) {
                    c.activeProgram = tid;
                    GLStateManager.glPushMatrix();
                    GLStateManager.glTranslatef(1.0f, 0.0f, 0.0f);
                    GLStateManager.glPopMatrix();
                    if (GLStateManager.getActiveProgram() != tid) mismatches.incrementAndGet();
                    if (c.activeProgram != tid) mismatches.incrementAndGet();
                }
                assertEquals(0.0f, new Matrix4f(GLStateManager.getModelViewMatrix()).m30(), 1e-6f, "balanced push/pop must leave this thread's matrix at identity");
            } finally {
                GLStateManager.exitWorkerContext();
            }
        });
        assertTrue(mismatches.get() == 0, "cross-thread state bleed detected under contention: " + mismatches.get());
    }
}
