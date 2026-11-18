package com.gtnewhorizons.angelica.rendering.celeritas;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectPoolTest {

    @ParameterizedTest(name = "n={0}")
    @ValueSource(ints = {1, 2, 3})
    void releaseThenAcquireReturnsSameInstancesWithoutFactory(int n) {
        AtomicInteger calls = new AtomicInteger();
        ObjectPool<Object> pool = new ObjectPool<>(() -> {
            calls.incrementAndGet();
            return new Object();
        }, n);

        Object[] acquired = new Object[n];
        for (int i = 0; i < n; i++) {
            acquired[i] = pool.acquire();
            assertNotNull(acquired[i]);
        }
        for (Object o : acquired) pool.release(o);

        Set<Object> reacquired = new HashSet<>();
        for (int i = 0; i < n; i++) reacquired.add(pool.acquire());

        assertEquals(n, reacquired.size());
        for (Object o : acquired) assertTrue(reacquired.contains(o));
        assertEquals(n, calls.get());
    }

    @Test
    void releaseIsLifoAndDropsBeyondMaxFree() {
        AtomicInteger calls = new AtomicInteger();
        ObjectPool<Object> pool = new ObjectPool<>(() -> {
            calls.incrementAndGet();
            return new Object();
        }, 2);

        Object a = pool.acquire();
        Object b = pool.acquire();
        Object c = pool.acquire();
        pool.release(a);
        pool.release(b);
        pool.release(c);

        assertSame(b, pool.acquire());
        assertSame(a, pool.acquire());
        pool.acquire();
        assertEquals(4, calls.get());
    }

    @Test
    void trimKeepsRecentPeakThenHalvesExcess() {
        final long w = ObjectPool.TRIM_WINDOW_NANOS;
        final int floor = 4;
        ObjectPool<Object> pool = new ObjectPool<>(Object::new, Integer.MAX_VALUE);

        Object[] burst = new Object[100];
        for (int i = 0; i < burst.length; i++) burst[i] = pool.acquire();
        pool.trim(0, floor);
        for (int i = 1; i < burst.length; i++) pool.release(burst[i]);

        for (long t : new long[] {w / 2, w, 2 * w}) {
            pool.trim(t, floor);
            assertEquals(99, pool.freeCount(), "peak from the last two windows still holds at t=" + t);
        }

        int[] expected = {51, 27, 15, 9, 6, 5, 4, 4};
        for (int i = 0; i < expected.length; i++) {
            pool.trim((3 + i) * w, floor);
            assertEquals(expected[i], pool.freeCount(), "window " + (3 + i));
        }
    }
}
