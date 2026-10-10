package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

@GLCoreTest
class ReadbackGLTest {

    @AfterEach
    void resetPack() {
        ReadbackFixture.resetPack();
    }

    static Stream<Arguments> readbacks() {
        return Stream.of(
            format("rgba8", ReadbackFixture::rgba8),
            format("packState", ReadbackFixture::packState),
            format("pixelPackBuffer", ReadbackFixture::pixelPackBuffer),
            format("rgb8", ReadbackFixture::rgb8),
            format("rgba16f", ReadbackFixture::rgba16f),
            format("rgba32f", ReadbackFixture::rgba32f),
            format("r8", ReadbackFixture::r8),
            format("rg16f", ReadbackFixture::rg16f),
            format("rgb10a2", ReadbackFixture::rgb10a2),
            format("r11g11b10f", ReadbackFixture::r11g11b10f),
            format("depthStencil", ReadbackFixture::depthStencil));
    }

    private static Arguments format(String name, Runnable body) {
        return Arguments.of(name, body);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("readbacks")
    void readback(String name, Runnable body) {
        body.run();
    }
}
