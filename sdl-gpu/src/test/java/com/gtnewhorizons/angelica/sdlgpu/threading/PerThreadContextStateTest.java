package com.gtnewhorizons.angelica.sdlgpu.threading;

import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PerThreadContextStateTest {

    @Test
    void twoThreadsHaveIndependentBoundProgram() throws Exception {
        final ThreadLocal<ContextState> tl = ThreadLocal.withInitial(ContextState::new);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch step1 = new CountDownLatch(2);
        final CountDownLatch step2 = new CountDownLatch(1);
        final AtomicInteger seenA = new AtomicInteger(-1);
        final AtomicInteger seenB = new AtomicInteger(-1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        final Thread a = new Thread(() -> {
            try {
                start.await();
                tl.get().boundProgram = 100;
                tl.get().boundTextures[0] = 1001;
                tl.get().viewportX = 10.0f; tl.get().viewportY = 20.0f;
                step1.countDown();
                step2.await();
                seenA.set(tl.get().boundProgram);
                assertEquals(1001, tl.get().boundTextures[0]);
                assertEquals(10.0f, tl.get().viewportX);
            } catch (Throwable e) { failure.compareAndSet(null, e); }
        }, "ctxstate-a");

        final Thread b = new Thread(() -> {
            try {
                start.await();
                tl.get().boundProgram = 200;
                tl.get().boundTextures[0] = 2002;
                tl.get().viewportX = 30.0f; tl.get().viewportY = 40.0f;
                step1.countDown();
                step2.await();
                seenB.set(tl.get().boundProgram);
                assertEquals(2002, tl.get().boundTextures[0]);
                assertEquals(30.0f, tl.get().viewportX);
            } catch (Throwable e) { failure.compareAndSet(null, e); }
        }, "ctxstate-b");

        a.start(); b.start();
        start.countDown();
        assertTrue(step1.await(5, TimeUnit.SECONDS));
        step2.countDown();
        a.join(TimeUnit.SECONDS.toMillis(5));
        b.join(TimeUnit.SECONDS.toMillis(5));

        assertNull(failure.get(), () -> "race: " + failure.get());
        assertEquals(100, seenA.get(), "thread A should see its own boundProgram");
        assertEquals(200, seenB.get(), "thread B should see its own boundProgram");
    }

    @Test
    void freshContextStateHasIdentityAttribBinding() {
        final ContextState cs = new ContextState();
        for (int i = 0; i < ContextState.MAX_VERTEX_ATTRIBS; i++) {
            assertEquals(i, cs.currentVao.attribBinding[i], "currentVao.attribBinding[" + i + "] should default to identity per GL spec");
        }
    }

    @Test
    void freshContextStateHasGLSpecDefaults() {
        final ContextState cs = new ContextState();
        assertEquals(0.0f, cs.viewportDepthNear);
        assertEquals(1.0f, cs.viewportDepthFar);
        assertTrue(cs.viewportDirty);
        assertTrue(cs.scissorDirty);
        assertTrue(cs.blendColorDirty);
    }
}
