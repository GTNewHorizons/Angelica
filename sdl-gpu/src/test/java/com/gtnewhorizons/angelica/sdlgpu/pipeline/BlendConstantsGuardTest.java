package com.gtnewhorizons.angelica.sdlgpu.pipeline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.sdl.SDLGPU.*;

class BlendConstantsGuardTest {

    @BeforeEach
    void setUp() {
        PipelineCache.setSwapchainFormats(new int[]{SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM});
    }

    private static PipelineCache createCache() {
        return new PipelineCache();
    }

    private static Arguments state(String name, boolean expected, Consumer<PipelineCache> setup) {
        return Arguments.of(name, expected, setup);
    }

    static Stream<Arguments> states() {
        return Stream.of(
            state("fresh cache", false, pc -> {}),
            state("constant color factor, blend disabled on every draw buffer", false,
                pc -> pc.setBlendFactors(SDL_GPU_BLENDFACTOR_CONSTANT_COLOR, SDL_GPU_BLENDFACTOR_ZERO, SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ZERO)),
            state("constant color factor, blend enabled", true, pc -> {
                pc.blendEnabledPerDrawBuffer[0] = true;
                pc.setBlendFactors(SDL_GPU_BLENDFACTOR_CONSTANT_COLOR, SDL_GPU_BLENDFACTOR_ZERO, SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ZERO);
            }),
            state("one-minus-constant-color factor, blend enabled", true, pc -> {
                pc.blendEnabledPerDrawBuffer[0] = true;
                pc.setBlendFactors(SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ONE_MINUS_CONSTANT_COLOR, SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ZERO);
            }),
            state("non-constant factors, blend enabled", false, pc -> {
                pc.blendEnabledPerDrawBuffer[0] = true;
                pc.setBlendFactors(SDL_GPU_BLENDFACTOR_SRC_ALPHA, SDL_GPU_BLENDFACTOR_ONE_MINUS_SRC_ALPHA, SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ZERO);
            }),
            state("constant color factor on draw buffer 1 only", true, pc -> {
                pc.blendEnabledPerDrawBuffer[1] = true;
                pc.setBlendFactors(1, SDL_GPU_BLENDFACTOR_CONSTANT_COLOR, SDL_GPU_BLENDFACTOR_ZERO, SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ZERO);
            }));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("states")
    void usesBlendConstants(String name, boolean expected, Consumer<PipelineCache> setup) {
        final PipelineCache pc = createCache();
        setup.accept(pc);
        assertEquals(expected, pc.usesBlendConstants());
    }

    @Test
    void disablingBlendAfterConstantColorClearsUsesBlendConstants() {
        final PipelineCache pc = createCache();
        pc.blendEnabledPerDrawBuffer[0] = true;
        pc.setBlendFactors(SDL_GPU_BLENDFACTOR_CONSTANT_COLOR, SDL_GPU_BLENDFACTOR_ZERO, SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ZERO);
        assertTrue(pc.usesBlendConstants());

        pc.blendEnabledPerDrawBuffer[0] = false;
        pc.markOutputDirty();
        assertFalse(pc.usesBlendConstants());
    }
}
