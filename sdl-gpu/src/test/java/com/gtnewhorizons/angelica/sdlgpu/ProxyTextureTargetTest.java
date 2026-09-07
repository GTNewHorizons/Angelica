package com.gtnewhorizons.angelica.sdlgpu;

import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL40;

import java.nio.ByteBuffer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProxyTextureTargetTest {

    private static final int[] PROXY_TARGETS = {
        0x8063, 0x8064, 0x8070, 0x851B, 0x8C19, 0x8C1B, 0x84F7, 0x900B, 0x9101, 0x9103
    };

    private static SDLGPURenderBackend backend;

    @BeforeAll
    static void setUp() {
        backend = new SDLGPURenderBackend();
    }

    @AfterEach
    void resetContextState() {
        final ContextState st = SdlTestRig.contextState();
        st.activeTextureUnit = 0;
        st.boundTextures[0] = 0;
        st.boundTextures[1] = 0;
        st.proxyTexture.reject(0);
    }

    @Test
    void everyProxyTargetIsRecognized() {
        for (int target : PROXY_TARGETS) {
            assertTrue(SDLGPURenderBackend.isProxyTarget(target), () -> "0x" + Integer.toHexString(target));
        }
    }

    @Test
    void realTargetsAreNotProxies() {
        final int[] real = {
            GL11.GL_TEXTURE_1D, GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_3D, GL13.GL_TEXTURE_CUBE_MAP,
            GL30.GL_TEXTURE_1D_ARRAY, GL30.GL_TEXTURE_2D_ARRAY, GL31.GL_TEXTURE_RECTANGLE,
            GL40.GL_TEXTURE_CUBE_MAP_ARRAY, GL32.GL_TEXTURE_2D_MULTISAMPLE, GL32.GL_TEXTURE_2D_MULTISAMPLE_ARRAY,
            0
        };
        for (int target : real) {
            assertFalse(SDLGPURenderBackend.isProxyTarget(target), () -> "0x" + Integer.toHexString(target));
        }
    }

    static Stream<Arguments> levelSizeCases() {
        return Stream.of(
            Arguments.of("level0", 16384, 0, 16384),
            Arguments.of("level1", 16384, 1, 8192),
            Arguments.of("level14FloorsAtOne", 16384, 14, 1),
            Arguments.of("level15FloorsAtOne", 16384, 15, 1),
            Arguments.of("level64FloorsAtOne", 16384, 64, 1),
            Arguments.of("baseOneFloorsAtOne", 1, 3, 1),
            Arguments.of("oddBaseRoundsDown", 6, 1, 3),
            Arguments.of("rejectedProxyBaseZeroLevel0", 0, 0, 0),
            Arguments.of("rejectedProxyBaseZeroLevel5", 0, 5, 0),
            Arguments.of("rejectedProxyNegativeLevel", 16384, -1, 0));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("levelSizeCases")
    void levelSizeMatchesExpectation(String label, int base, int level, int expected) {
        assertEquals(expected, SDLGPURenderBackend.proxyLevelSize(base, level), label);
    }

    @Test
    void proxyTexImage2DRecordsDimensionsWithoutTouchingTheBoundTexture() {
        final ContextState st = SdlTestRig.contextState();
        st.activeTextureUnit = 1;
        st.boundTextures[1] = 4242;

        backend.texImage2D(GL11.GL_PROXY_TEXTURE_2D, 0, GL11.GL_RGBA, 16384, 16384, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);

        assertEquals(4242, st.boundTextures[1]);
        assertEquals(16384, backend.getTexLevelParameteri(GL11.GL_PROXY_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH));
        assertEquals(16384, backend.getTexLevelParameteri(GL11.GL_PROXY_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT));
        assertEquals(2048, backend.getTexLevelParameteri(GL11.GL_PROXY_TEXTURE_2D, 3, GL11.GL_TEXTURE_WIDTH));
        assertEquals(GL11.GL_RGBA, backend.getTexLevelParameteri(GL11.GL_PROXY_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT));
        assertEquals(0, backend.getTexLevelParameteri(GL11.GL_PROXY_TEXTURE_2D, 0, GL11.GL_TEXTURE_BORDER));
    }

    @Test
    void oversizedProxyTexImage2DZeroesEveryProxyField() {
        final ContextState st = SdlTestRig.contextState();
        st.activeTextureUnit = 0;

        backend.texImage2D(GL11.GL_PROXY_TEXTURE_2D, 0, GL11.GL_RGBA, 16384, 16384, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        backend.texImage2D(GL11.GL_PROXY_TEXTURE_2D, 0, GL11.GL_RGBA, 32768, 32768, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);

        assertEquals(0, backend.getTexLevelParameteri(GL11.GL_PROXY_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH));
        assertEquals(0, backend.getTexLevelParameteri(GL11.GL_PROXY_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT));
        assertEquals(0, backend.getTexLevelParameteri(GL11.GL_PROXY_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT));
    }

    @Test
    void proxyArrayTargetIsLimitedByLayerCount() {
        final int layers = SDLGPURenderBackend.MAX_ARRAY_TEXTURE_LAYERS;

        backend.texImage3D(GL30.GL_PROXY_TEXTURE_2D_ARRAY, 0, GL11.GL_RGBA, 64, 64, layers, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        assertEquals(layers, backend.getTexLevelParameteri(GL30.GL_PROXY_TEXTURE_2D_ARRAY, 0, GL12.GL_TEXTURE_DEPTH));

        backend.texImage3D(GL30.GL_PROXY_TEXTURE_2D_ARRAY, 0, GL11.GL_RGBA, 64, 64, layers + 1, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        assertEquals(0, backend.getTexLevelParameteri(GL30.GL_PROXY_TEXTURE_2D_ARRAY, 0, GL12.GL_TEXTURE_DEPTH));
        assertEquals(0, backend.getTexLevelParameteri(GL30.GL_PROXY_TEXTURE_2D_ARRAY, 0, GL11.GL_TEXTURE_WIDTH));
    }

    @Test
    void proxyStateIsScopedToTheProbedTarget() {
        backend.texImage2D(GL11.GL_PROXY_TEXTURE_2D, 0, GL11.GL_RGBA, 256, 256, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);

        assertEquals(256, backend.getTexLevelParameteri(GL11.GL_PROXY_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH));
        assertEquals(0, backend.getTexLevelParameteri(GL13.GL_PROXY_TEXTURE_CUBE_MAP, 0, GL11.GL_TEXTURE_WIDTH));
        assertEquals(0, backend.getTexLevelParameteri(GL13.GL_PROXY_TEXTURE_CUBE_MAP, 0, GL11.GL_TEXTURE_HEIGHT));
        assertEquals(0, backend.getTexLevelParameteri(GL13.GL_PROXY_TEXTURE_CUBE_MAP, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT));
        assertEquals(0, backend.getTexLevelParameteri(GL12.GL_PROXY_TEXTURE_3D, 0, GL12.GL_TEXTURE_DEPTH));
    }

    @Test
    void unmappableProxyFormatIsRejected() {
        backend.texImage2D(GL11.GL_PROXY_TEXTURE_2D, 0, GL11.GL_RGBA, 256, 256, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        backend.texImage2D(GL11.GL_PROXY_TEXTURE_2D, 0, 0x1BADF00D, 256, 256, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);

        assertEquals(0, backend.getTexLevelParameteri(GL11.GL_PROXY_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH));
        assertEquals(0, backend.getTexLevelParameteri(GL11.GL_PROXY_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT));
    }

    @Test
    void proxyBindIsInert() {
        final ContextState st = SdlTestRig.contextState();
        st.activeTextureUnit = 0;
        st.boundTextures[0] = 77;

        for (int target : PROXY_TARGETS) {
            backend.bindTexture(target, 99);
            assertEquals(77, st.boundTextures[0], () -> "0x" + Integer.toHexString(target));
        }
    }
}
