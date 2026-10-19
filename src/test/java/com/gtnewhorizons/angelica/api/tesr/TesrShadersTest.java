package com.gtnewhorizons.angelica.api.tesr;

import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class TesrShadersTest {

    private static final Runnable NOOP = () -> {};

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
    void nullArgumentsThrow() {
        final ResourceLocation location = new ResourceLocation("test", "shaders/a.vert");
        assertThrows(NullPointerException.class, () -> TesrShaders.register("test:null_bind", null, NOOP));
        assertThrows(NullPointerException.class, () -> TesrShaders.register("test:null_release", NOOP, null));
        assertThrows(NullPointerException.class, () -> TesrShaders.register("test:null_vertex", null, location));
        assertThrows(NullPointerException.class, () -> TesrShaders.register("test:null_fragment", location, null));
    }
}
