package com.gtnewhorizons.angelica.api.tesr;

import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertThrows;

class TesrShadersTest {

    private static final Runnable NOOP = () -> {};

    @Test
    void duplicateNameThrows() {
        TesrShaders.register("test:duplicate", NOOP, NOOP);
        assertThrows(IllegalStateException.class, () -> TesrShaders.register("test:duplicate", NOOP, NOOP));
    }

    @ParameterizedTest
    @ValueSource(strings = {"nonamespace", ":path", "ns:", "a:b:c"})
    void malformedNamesThrow(String name) {
        assertThrows(IllegalArgumentException.class, () -> TesrShaders.register(name, NOOP, NOOP));
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
