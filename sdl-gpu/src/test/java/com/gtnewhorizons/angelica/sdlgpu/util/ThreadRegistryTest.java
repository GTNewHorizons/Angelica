package com.gtnewhorizons.angelica.sdlgpu.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ThreadRegistryTest {

    private static ThreadRegistry<Object> registry() {
        return new ThreadRegistry<>(new Object[0]);
    }

    @Test
    void soleTracksTheSingleEntry() {
        final ThreadRegistry<Object> r = registry();
        final Object a = new Object();
        final Object b = new Object();

        assertNull(r.sole(), "empty registry has no sole entry");
        r.add(a);
        assertSame(a, r.sole());
        r.add(b);
        assertNull(r.sole(), "two entries means no sole entry");
        r.remove(b);
        assertSame(a, r.sole());
        r.remove(a);
        assertNull(r.sole());
        assertEquals(0, r.size());
    }

    @Test
    void removeOfAnAbsentEntryLeavesTheRegistryAlone() {
        final ThreadRegistry<Object> r = registry();
        final Object a = new Object();
        r.add(a);
        final Object[] before = r.snapshot();

        assertFalse(r.remove(new Object()));
        assertSame(before, r.snapshot(), "a no-op remove must not republish");
        assertSame(a, r.sole());
    }

}
