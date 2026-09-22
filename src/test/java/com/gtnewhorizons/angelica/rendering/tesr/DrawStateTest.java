package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.opengl.GL11;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class DrawStateTest {

    static Stream<?> packCullCases() {
        return Stream.of(
            arguments(true, GL11.GL_BACK, DrawState.CULL_BACK),
            arguments(true, GL11.GL_FRONT, DrawState.CULL_FRONT),
            arguments(true, GL11.GL_FRONT_AND_BACK, DrawState.CULL_BOTH),
            arguments(false, GL11.GL_BACK, DrawState.DISABLED),
            arguments(false, GL11.GL_FRONT, DrawState.DISABLED),
            arguments(false, GL11.GL_FRONT_AND_BACK, DrawState.DISABLED)
        );
    }

    @ParameterizedTest
    @MethodSource("packCullCases")
    void packCullReturnsExpectedCode(boolean enabled, int cullFaceMode, int expected) {
        assertEquals(expected, DrawState.packCull(enabled, cullFaceMode));
    }

    @Test
    void equalValuesInternToOneInstance() {
        assertSame(DrawState.of(DrawState.CULL_BACK, DrawState.OPAQUE, false, true, true, 0f, true, 0f, 0f), DrawState.of(DrawState.CULL_BACK, DrawState.OPAQUE, false, true, true, 0f, true, 0f, 0f));
        assertNotSame(DrawState.of(DrawState.CULL_BACK, DrawState.OPAQUE, false, true, true, 0f, true, 0f, 0f), DrawState.of(DrawState.CULL_FRONT, DrawState.OPAQUE, false, true, true, 0f, true, 0f, 0f));
    }

    @Test
    void lifecycleResetBoundsInterningAndReinternsIdentity() {
        DrawState.clearInterning();
        final DrawState before = DrawState.of(DrawState.CULL_BACK, DrawState.OPAQUE, false, true, true, 0f, true, 0f, 0f);
        DrawState.of(DrawState.CULL_FRONT, DrawState.TRANSLUCENT, false, false, true, 0f, false, 1f, 1f);
        assertEquals(2, DrawState.internedStateCount());

        DrawState.clearInterning();
        DrawState.clearInterning();
        assertEquals(0, DrawState.internedStateCount());

        final DrawState after = DrawState.of(DrawState.CULL_BACK, DrawState.OPAQUE, false, true, true, 0f, true, 0f, 0f);
        assertNotSame(before, after);
        assertSame(after, DrawState.of(DrawState.CULL_BACK, DrawState.OPAQUE, false, true, true, 0f, true, 0f, 0f));
    }

    @Test
    void noCullMaterialNormalizesToDisabled() {
        final TesrMaterial noCull = TesrMaterial.builder().noCull().build();
        assertSame(DrawState.forMaterial(noCull, DrawState.CULL_FRONT, true, 0f, 0f), DrawState.forMaterial(noCull, DrawState.CULL_BACK, true, 0f, 0f));
        assertEquals(DrawState.DISABLED, DrawState.forMaterial(noCull, DrawState.CULL_FRONT, true, 0f, 0f).getCull());
    }

    @Test
    void unlitMaterialNormalizesToUnlit() {
        final TesrMaterial unlit = TesrMaterial.builder().unlit().build();
        assertSame(DrawState.forMaterial(unlit, DrawState.CULL_BACK, true, 0f, 0f), DrawState.forMaterial(unlit, DrawState.CULL_BACK, false, 0f, 0f));
    }

    @Test
    void transparencyMapsToBlendMode() {
        assertEquals(DrawState.OPAQUE, DrawState.blendFor(TesrMaterial.Transparency.OPAQUE));
        assertEquals(DrawState.TRANSLUCENT, DrawState.blendFor(TesrMaterial.Transparency.TRANSLUCENT));
        assertEquals(DrawState.ADDITIVE, DrawState.blendFor(TesrMaterial.Transparency.ADDITIVE));
        assertEquals(DrawState.ADDITIVE_ALPHA, DrawState.blendFor(TesrMaterial.Transparency.ADDITIVE_ALPHA));
        assertEquals(DrawState.GLINT, DrawState.blendFor(TesrMaterial.Transparency.GLINT));
    }
}
