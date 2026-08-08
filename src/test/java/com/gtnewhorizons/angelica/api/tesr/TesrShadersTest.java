package com.gtnewhorizons.angelica.api.tesr;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TesrShadersTest {

    private static final Runnable NOOP = () -> {};

    @Test
    void registerReturnsHandleWithAccessors() {
        final Runnable bind = () -> {};
        final Runnable release = () -> {};
        final TesrShader shader = TesrShaders.register("test:accessors", bind, release);
        assertEquals("test:accessors", shader.name());
        assertSame(bind, shader.bind());
        assertSame(release, shader.release());
    }

    @Test
    void duplicateNameThrows() {
        TesrShaders.register("test:duplicate", NOOP, NOOP);
        assertThrows(IllegalStateException.class, () -> TesrShaders.register("test:duplicate", NOOP, NOOP));
    }

    @Test
    void malformedNamesThrow() {
        assertThrows(IllegalArgumentException.class, () -> TesrShaders.register("nonamespace", NOOP, NOOP));
        assertThrows(IllegalArgumentException.class, () -> TesrShaders.register(":path", NOOP, NOOP));
        assertThrows(IllegalArgumentException.class, () -> TesrShaders.register("ns:", NOOP, NOOP));
        assertThrows(IllegalArgumentException.class, () -> TesrShaders.register("a:b:c", NOOP, NOOP));
    }

    @Test
    void nullRunnablesThrow() {
        assertThrows(NullPointerException.class, () -> TesrShaders.register("test:null_bind", null, NOOP));
        assertThrows(NullPointerException.class, () -> TesrShaders.register("test:null_release", NOOP, null));
    }
}
