package com.gtnewhorizons.angelica.api.tesr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TesrMaterialInternTest {

    private static final TesrShader SPECIAL_EXCLUDED = TesrShaders.register("test:invalid_special", () -> {}, () -> {});

    static Stream<Arguments> invalidCombinations() {
        return Stream.of(
            Arguments.of("depthOnly + noDepthWrite", (Executable) () -> TesrMaterial.builder().depthOnly().noDepthWrite().build()),
            Arguments.of("depthOnly + depthEqual", (Executable) () -> TesrMaterial.builder().depthOnly().depthEqual().build()),
            Arguments.of("special + shader", (Executable) () -> TesrMaterial.builder().special(TesrMaterial.SpecialRender.GLINT).shader(SPECIAL_EXCLUDED).build()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCombinations")
    void invalidCombinationsThrow(String name, Executable build) {
        assertThrows(IllegalStateException.class, build);
    }

    @Test
    void shaderIdentitySplitsOtherwiseEqualMaterials() {
        final TesrShader s1 = TesrShaders.register("test:intern_a", () -> {}, () -> {});
        final TesrShader s2 = TesrShaders.register("test:intern_b", () -> {}, () -> {});
        final TesrMaterial a = TesrMaterial.builder().translucent().shader(s1).build();
        final TesrMaterial b = TesrMaterial.builder().translucent().shader(s2).build();
        final TesrMaterial c = TesrMaterial.builder().translucent().shader(s1).build();
        assertNotSame(a, b);
        assertSame(a, c);
    }
}
