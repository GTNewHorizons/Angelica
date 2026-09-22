package com.gtnewhorizons.angelica.sdlgpu.pipeline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    @Test
    void freshCacheDoesNotUseBlendConstants() {
        final PipelineCache pc = createCache();
        assertFalse(pc.usesBlendConstants());
    }

    @Test
    void constantColorFactorWithBlendDisabledDoesNotUseBlendConstants() {
        final PipelineCache pc = createCache();
        pc.setBlendFactors(SDL_GPU_BLENDFACTOR_CONSTANT_COLOR, SDL_GPU_BLENDFACTOR_ZERO, SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ZERO);
        assertFalse(pc.usesBlendConstants(), "blend is disabled on every draw buffer; the constant is never sampled");
    }

    @Test
    void constantColorFactorWithBlendEnabledUsesBlendConstants() {
        final PipelineCache pc = createCache();
        pc.blendEnabledPerDrawBuffer[0] = true;
        pc.setBlendFactors(SDL_GPU_BLENDFACTOR_CONSTANT_COLOR, SDL_GPU_BLENDFACTOR_ZERO, SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ZERO);
        assertTrue(pc.usesBlendConstants());
    }

    @Test
    void oneMinusConstantColorFactorAlsoUsesBlendConstants() {
        final PipelineCache pc = createCache();
        pc.blendEnabledPerDrawBuffer[0] = true;
        pc.setBlendFactors(SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ONE_MINUS_CONSTANT_COLOR, SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ZERO);
        assertTrue(pc.usesBlendConstants());
    }

    @Test
    void nonConstantFactorsWithBlendEnabledDoNotUseBlendConstants() {
        final PipelineCache pc = createCache();
        pc.blendEnabledPerDrawBuffer[0] = true;
        pc.setBlendFactors(SDL_GPU_BLENDFACTOR_SRC_ALPHA, SDL_GPU_BLENDFACTOR_ONE_MINUS_SRC_ALPHA, SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ZERO);
        assertFalse(pc.usesBlendConstants());
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

    @Test
    void secondDrawBufferAloneCanTriggerBlendConstants() {
        final PipelineCache pc = createCache();
        pc.blendEnabledPerDrawBuffer[1] = true;
        pc.setBlendFactors(1, SDL_GPU_BLENDFACTOR_CONSTANT_COLOR, SDL_GPU_BLENDFACTOR_ZERO, SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ZERO);
        assertTrue(pc.usesBlendConstants(), "the loop must scan every draw buffer slot, not only slot 0");
    }
}
