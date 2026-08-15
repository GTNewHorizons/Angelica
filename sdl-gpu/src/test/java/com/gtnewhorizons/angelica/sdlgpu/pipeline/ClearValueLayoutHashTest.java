package com.gtnewhorizons.angelica.sdlgpu.pipeline;

import org.junit.jupiter.api.Test;

import static com.gtnewhorizons.angelica.sdlgpu.pipeline.PipelineApplier.foldClearColor;
import static com.gtnewhorizons.angelica.sdlgpu.pipeline.PipelineApplier.foldClearDepth;
import static com.gtnewhorizons.angelica.sdlgpu.pipeline.PipelineApplier.foldClearStencil;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ClearValueLayoutHashTest {

    private static final long SEED = 0xA5A5A5A5A5A5A5A5L;

    @Test
    void depthZeroAndDepthOneDoNotCollide() {
        assertNotEquals(foldClearDepth(SEED, 0.0f), foldClearDepth(SEED, 1.0f), "the minimap mask clear must not reuse the world's depth-1 clear target");
    }

    @Test
    void depthFoldIsStableForTheSameValue() {
        assertEquals(foldClearDepth(SEED, 0.0f), foldClearDepth(SEED, 0.0f));
    }

    @Test
    void negativeZeroIsDistinguishedFromZero() {
        assertNotEquals(foldClearDepth(SEED, 0.0f), foldClearDepth(SEED, -0.0f), "raw bits, not numeric equality, so the cached value always matches what was baked");
    }

    @Test
    void everyColorChannelParticipates() {
        final long base = foldClearColor(SEED, 0f, 0f, 0f, 1f);
        assertNotEquals(base, foldClearColor(SEED, 1f, 0f, 0f, 1f), "red");
        assertNotEquals(base, foldClearColor(SEED, 0f, 1f, 0f, 1f), "green");
        assertNotEquals(base, foldClearColor(SEED, 0f, 0f, 1f, 1f), "blue");
        assertNotEquals(base, foldClearColor(SEED, 0f, 0f, 0f, 0f), "alpha");
    }

    @Test
    void stencilValuesDoNotCollide() {
        assertNotEquals(foldClearStencil(SEED, 0), foldClearStencil(SEED, 1));
        assertEquals(foldClearStencil(SEED, 0xFF), foldClearStencil(SEED, 0xFF));
    }
}
