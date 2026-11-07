package com.gtnewhorizons.angelica.rendering.tesr;

import org.joml.Matrix4f;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static java.lang.Math.toRadians;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FlattenedItemLightingTest {

    private static Matrix4f camera() {
        return new Matrix4f().rotateX((float) toRadians(30)).rotateY((float) toRadians(70)).translate(-3.2f, -1.1f, -4.5f);
    }

    private static Matrix4f align(Matrix4f m) {
        return m.translate(.5f, .5f, .5f).rotateZ((float) toRadians(180)).rotateY((float) toRadians(90)).translate(-.5f, -.5f, -.5f);
    }

    private static Matrix4f item(Matrix4f m) {
        return m.translate(-2, 3, -3).scale(10, 10, 10).translate(1, 0.5f, 1).scale(1, 1, -1)
            .rotateX((float) toRadians(210)).rotateY((float) toRadians(45)).rotateY((float) toRadians(-90));
    }

    static Stream<Arguments> cases() {
        return Stream.of(
            Arguments.of("storagedrawers 4-drawer", item(align(camera()).translate(0, 0, 0.94f).scale(1 / 16f, 1 / 16f, -0.0001f).translate(2, 10, 0).scale(.25f, .25f, 1)), true),
            Arguments.of("storagedrawers 1-drawer", item(align(camera()).translate(0, 0, 0.94f).scale(1 / 16f, 1 / 16f, -0.0001f).translate(2, 10, 0).scale(.5f, .5f, 1)), true),
            Arguments.of("jabba", item(align(camera()).translate(0, 0, -0.001f).scale(1 / 256f, 1 / 256f, -0.0001f).translate(65, 75, 0).scale(8, 8, 1)), true),
            Arguments.of("ae2 storage monitor", item(camera().scale(1 / 42f).translate(-8, -10.2f, -10.4f).scale(1, 1, 0.005f)), true),
            Arguments.of("hbm mass storage", item(camera().translate(0, 0, -0.005f).scale(1 / 16f, 1 / 16f, -0.0001f).translate(4, 2.5f, 0).scale(.5f, .5f, 1)), true),
            Arguments.of("identity", camera(), false),
            Arguments.of("sign text", camera().translate(.5f, .75f, .5f).scale(0.016666668f, -0.016666668f, 0.016666668f), false),
            Arguments.of("chest model", camera().translate(.5f, .5f, .5f).scale(1, -1, -1), false),
            Arguments.of("10:1 model", camera().scale(1, 1, 0.1f), false),
            Arguments.of("needle", camera().scale(1, 0.1f, 0.1f), false),
            Arguments.of("singular", camera().scale(1, 1, 0), false));
    }

    @ParameterizedTest
    @MethodSource("cases")
    void classifiesFlatness(String name, Matrix4f m, boolean expected) {
        assertEquals(expected, FlattenedItemLighting.isFlattened(m), name);
    }
}
