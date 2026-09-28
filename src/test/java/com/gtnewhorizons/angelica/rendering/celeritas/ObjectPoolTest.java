package com.gtnewhorizons.angelica.rendering.celeritas;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectPoolTest {

    @Test
    void acquireOnEmptyPoolCallsFactoryOnce() {
        AtomicInteger calls = new AtomicInteger();
        ObjectPool<Object> pool = new ObjectPool<>(() -> {
            calls.incrementAndGet();
            return new Object();
        });

        Object o = pool.acquire();

        assertTrue(o != null);
        assertEquals(1, calls.get());
    }

    @Test
    void releaseThenAcquireReturnsSameInstanceWithoutFactory() {
        AtomicInteger calls = new AtomicInteger();
        ObjectPool<Object> pool = new ObjectPool<>(() -> {
            calls.incrementAndGet();
            return new Object();
        });

        Object o = pool.acquire();
        pool.release(o);
        Object reacquired = pool.acquire();

        assertSame(o, reacquired);
        assertEquals(1, calls.get());
    }

    @Test
    void twoReleasesThenTwoAcquiresReturnBothInstances() {
        AtomicInteger calls = new AtomicInteger();
        ObjectPool<Object> pool = new ObjectPool<>(() -> {
            calls.incrementAndGet();
            return new Object();
        });

        Object a = pool.acquire();
        Object b = pool.acquire();
        pool.release(a);
        pool.release(b);

        Set<Object> reacquired = new HashSet<>();
        reacquired.add(pool.acquire());
        reacquired.add(pool.acquire());

        assertEquals(2, reacquired.size());
        assertTrue(reacquired.contains(a));
        assertTrue(reacquired.contains(b));
        assertEquals(2, calls.get());
    }
}
