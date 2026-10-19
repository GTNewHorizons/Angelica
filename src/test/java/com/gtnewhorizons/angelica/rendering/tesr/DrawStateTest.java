package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.opengl.GL11;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class DrawStateTest {

    static Stream<?> packCullCases() {
        return Stream.of(
            arguments(true, GL11.GL_BACK, DrawState.CULL_BACK),
            arguments(true, GL11.GL_FRONT, DrawState.CULL_FRONT),
            arguments(true, GL11.GL_FRONT_AND_BACK, DrawState.CULL_BOTH),
            arguments(false, GL11.GL_BACK, DrawState.DISABLED)
        );
    }

    @ParameterizedTest
    @MethodSource("packCullCases")
    void packCullReturnsExpectedCode(boolean enabled, int cullFaceMode, int expected) {
        assertEquals(expected, DrawState.packCull(enabled, cullFaceMode));
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
