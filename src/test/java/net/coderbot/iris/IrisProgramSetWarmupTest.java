package net.coderbot.iris;

import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrisProgramSetWarmupTest {
    @AfterEach
    void reset() {
        Reflect.setStatic(Iris.class, "programSetWarmup", null);
    }

    private static void await() {
        Reflect.invokeStatic(Iris.class, "awaitProgramSetWarmup", new Class<?>[0]);
    }

    @Test
    void noPendingWarmupIsNoOp() {
        Reflect.setStatic(Iris.class, "programSetWarmup", null);
        await();
        assertNull(Reflect.getStatic(Iris.class, "programSetWarmup"));
    }

    @Test
    void blocksUntilWarmupCompletes() throws Exception {
        final CompletableFuture<Void> warmup = new CompletableFuture<>();
        Reflect.setStatic(Iris.class, "programSetWarmup", warmup);
        final CountDownLatch done = new CountDownLatch(1);
        final Thread joiner = new Thread(() -> {
            await();
            done.countDown();
        });
        joiner.start();
        assertFalse(done.await(200, TimeUnit.MILLISECONDS));
        warmup.complete(null);
        assertTrue(done.await(5, TimeUnit.SECONDS));
        joiner.join();
        assertNull(Reflect.getStatic(Iris.class, "programSetWarmup"));
    }

    @Test
    void failedWarmupIsSwallowedAndCleared() {
        final CompletableFuture<Void> warmup = new CompletableFuture<>();
        warmup.completeExceptionally(new IllegalStateException("boom"));
        Reflect.setStatic(Iris.class, "programSetWarmup", warmup);
        await();
        assertNull(Reflect.getStatic(Iris.class, "programSetWarmup"));
    }
}
