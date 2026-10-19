package com.gtnewhorizons.angelica.rendering.celeritas;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectPoolTest {

    @ParameterizedTest(name = "n={0}")
    @ValueSource(ints = {1, 2, 3})
    void releaseThenAcquireReturnsSameInstancesWithoutFactory(int n) {
        AtomicInteger calls = new AtomicInteger();
        ObjectPool<Object> pool = new ObjectPool<>(() -> {
            calls.incrementAndGet();
            return new Object();
        });

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
}
