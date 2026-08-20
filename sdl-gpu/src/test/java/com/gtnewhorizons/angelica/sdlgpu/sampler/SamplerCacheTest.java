package com.gtnewhorizons.angelica.sdlgpu.sampler;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SamplerCacheTest {

    private static SamplerCache.Key key(int minF, int magF) {
        return new SamplerCache.Key(minF, magF, 0x2901, 0x2901, 0x2901, 0f, 1000f, 0f, 1f, 0, 0x0203);
    }

    @Test
    void sameKeyReturnsSameHandleAndFactoryCalledOnce() {
        final SamplerCache cache = new SamplerCache();
        final AtomicLong nextHandle = new AtomicLong(0x1000);
        final AtomicInteger calls = new AtomicInteger();

        final SamplerCache.Factory f = k -> {
            calls.incrementAndGet();
            return nextHandle.getAndIncrement();
        };

        final SamplerCache.Key k = key(0x2702, 0x2600);
        final long h1 = cache.getOrCreate(k, f);
        final long h2 = cache.getOrCreate(k, f);
        final long h3 = cache.getOrCreate(k, f);

        assertEquals(h1, h2, "second lookup with same key must return cached handle");
        assertEquals(h1, h3, "third lookup with same key must return cached handle");
        assertEquals(1, calls.get(), "factory must be invoked exactly once per unique key");
        assertEquals(1, cache.size());
    }

    @Test
    void distinctKeysReturnDistinctHandles() {
        final SamplerCache cache = new SamplerCache();
        final AtomicLong nextHandle = new AtomicLong(0x2000);

        final SamplerCache.Factory f = k -> nextHandle.getAndIncrement();

        final long mipped = cache.getOrCreate(key(0x2702, 0x2600), f);
        final long flat   = cache.getOrCreate(key(0x2600, 0x2600), f);

        assertNotEquals(mipped, flat, "different sampler states must produce different handles");
        assertEquals(2, cache.size());

        assertEquals(mipped, cache.getOrCreate(key(0x2702, 0x2600), f));
        assertEquals(flat,   cache.getOrCreate(key(0x2600, 0x2600), f));
    }

    @Test
    void factoryReturningZeroIsNotCached() {
        final SamplerCache cache = new SamplerCache();
        final AtomicInteger calls = new AtomicInteger();

        final SamplerCache.Factory zeroFactory = k -> { calls.incrementAndGet(); return 0L; };
        final SamplerCache.Key k = key(0x2702, 0x2600);

        assertEquals(0L, cache.getOrCreate(k, zeroFactory));
        assertEquals(0L, cache.getOrCreate(k, zeroFactory));
        assertEquals(2, calls.get(), "0 result must not be cached -- factory must be invoked again");
        assertEquals(0, cache.size());
    }

    @Test
    void recordKeyEqualityAndHashCode() {
        final SamplerCache.Key a = key(0x2702, 0x2600);
        final SamplerCache.Key b = key(0x2702, 0x2600);
        final SamplerCache.Key c = key(0x2600, 0x2600);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertNotNull(a.toString());
    }
}
