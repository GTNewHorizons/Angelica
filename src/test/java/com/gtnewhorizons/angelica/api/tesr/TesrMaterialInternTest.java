package com.gtnewhorizons.angelica.api.tesr;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class TesrMaterialInternTest {

    @Test
    void equalSettingsBuildSameInstance() {
        final TesrMaterial a = TesrMaterial.builder().color(1f, 0.5f, 0.25f, 1f).translucent().stream().build();
        final TesrMaterial b = TesrMaterial.builder().color(1f, 0.5f, 0.25f, 1f).translucent().stream().build();
        assertSame(a, b);
    }

    @Test
    void differingSettingsBuildDistinctInstances() {
        final TesrMaterial a = TesrMaterial.builder().color(1f, 0.5f, 0.25f, 1f).translucent().build();
        final TesrMaterial b = TesrMaterial.builder().color(1f, 0.5f, 0.25f, 1f).additive().build();
        assertNotSame(a, b);
    }

    @Test
    void defaultsFoldToCurrentState() {
        assertSame(TesrMaterial.CURRENT_STATE, TesrMaterial.builder().build());
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
