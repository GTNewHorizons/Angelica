package com.gtnewhorizons.angelica.sdlgpu.pipeline;

import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.sdl.SDLGPU.*;

class PipelineCacheKeyTest {

    @BeforeEach
    void setUp() {
        PipelineCache.setSwapchainFormats(new int[]{SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM});
    }

    private PipelineCache createCache() {
        return new PipelineCache();
    }

    @Test
    void freshContextStateRequestsSeeding() {
        final ContextState st = new ContextState();
        assertTrue(st.needsSeed, "a fresh recording stream must request a state seed");
        assertEquals(SDL_GPU_COMPAREOP_LESS, st.pipeline.depthCompareOp);
    }

    private static final Consumer<PipelineCache> NONE = c -> {};

    private static Arguments change(String name, Consumer<PipelineCache> shared, Consumer<PipelineCache> onlyB) {
        return Arguments.of(name, shared, onlyB);
    }

    static Stream<Arguments> keyChangingState() {
        return Stream.of(
            change("blend enable", NONE, b -> b.blendEnabledPerDrawBuffer[0] = true),
            change("depth test with a depth target", c -> c.hasDepthTarget = true, b -> b.depthTestEnabled = true),
            change("cull enable", NONE, b -> b.cullEnabled = true),
            change("primitive type", NONE, b -> b.primitiveType = SDL_GPU_PRIMITIVETYPE_LINELIST),
            change("blend factors", NONE, b -> b.setBlendFactors(SDL_GPU_BLENDFACTOR_SRC_ALPHA, SDL_GPU_BLENDFACTOR_ONE_MINUS_SRC_ALPHA, SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ZERO)),
            change("color write mask", NONE, b -> b.colorWriteMask = SDL_GPU_COLORCOMPONENT_R | SDL_GPU_COLORCOMPONENT_G | SDL_GPU_COLORCOMPONENT_B),
            change("shaders", NONE, b -> {
                b.vertexShader = 12345L;
                b.fragmentShader = 67890L;
            }),
            change("depth compare op", NONE, b -> b.depthCompareOp = SDL_GPU_COMPAREOP_LESS_OR_EQUAL),
            change("color target format", NONE, b -> b.setColorTargetFormats(new int[]{SDL_GPU_TEXTUREFORMAT_R16G16B16A16_FLOAT})),
            change("depth bias with polygon offset on", c -> c.polygonOffsetFillEnabled = true, b -> {
                b.depthBiasConstant = 1.0f;
                b.depthBiasSlopeFactor = 2.0f;
            }),
            change("depth bias clamp with polygon offset on", c -> {
                c.polygonOffsetFillEnabled = true;
                c.depthBiasConstant = 1.0f;
                c.depthBiasSlopeFactor = 2.0f;
            }, b -> b.depthBiasClamp = 0.5f));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("keyChangingState")
    void stateChangeProducesDifferentKey(String name, Consumer<PipelineCache> shared, Consumer<PipelineCache> onlyB) {
        final PipelineCache a = createCache();
        final PipelineCache b = createCache();
        shared.accept(a);
        shared.accept(b);
        onlyB.accept(b);

        assertNotEquals(computeKey(a), computeKey(b), name + " must change the pipeline key");
    }

    @Test
    void testDepthTestWithoutDsvCollapsesToSameKey() {
        final PipelineCache a = createCache();
        final PipelineCache b = createCache();
        b.depthTestEnabled = true;

        assertEquals(computeKey(a), computeKey(b), "Depth test without DSV must produce same key");
    }

    @Test
    void testCullFaceChangeOnlyMattersWhenEnabled() {
        final PipelineCache a = createCache();
        final PipelineCache b = createCache();
        b.cullFaceMode = SDL_GPU_CULLMODE_FRONT;
        assertEquals(computeKey(a), computeKey(b), "cullFaceMode change must NOT change key when culling is disabled");

        a.cullEnabled = true;
        b.cullEnabled = true;
        assertNotEquals(computeKey(a), computeKey(b),
            "cullFaceMode change should change key when culling is enabled");
    }

    @Test
    void perDrawBufferFactorOnLiveSlotChangesKey() {
        final PipelineCache a = createCache();
        final PipelineCache b = createCache();
        b.setBlendFactors(0, SDL_GPU_BLENDFACTOR_SRC_ALPHA, SDL_GPU_BLENDFACTOR_ONE_MINUS_SRC_ALPHA, SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ZERO);

        assertNotEquals(computeKey(a), computeKey(b), "factor on the only color target must change the key");
    }

    @Test
    void perDrawBufferFactorOnUnusedSlotKeepsKey() {
        final PipelineCache a = createCache();
        final PipelineCache b = createCache();
        b.setBlendFactors(1, SDL_GPU_BLENDFACTOR_SRC_ALPHA, SDL_GPU_BLENDFACTOR_ONE_MINUS_SRC_ALPHA, SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ZERO);

        assertEquals(computeKey(a), computeKey(b), "factor on a draw buffer with no color target must keep the key");
    }

    @Test
    void globalFactorsAfterIndexedSetMatchFreshCache() {
        final PipelineCache fresh = createCache();
        final PipelineCache b = createCache();
        b.setBlendFactors(0, SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ONE);
        b.setBlendFactors(3, SDL_GPU_BLENDFACTOR_SRC_ALPHA, SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ONE);
        b.setBlendFactors(SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ZERO, SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ZERO);

        final int[] fourTargets = new int[4];
        Arrays.fill(fourTargets, SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM);
        fresh.setColorTargetFormats(fourTargets);
        b.setColorTargetFormats(fourTargets);
        assertEquals(computeKey(fresh), computeKey(b), "a global blend func must overwrite every indexed entry");
    }

    @Test
    void blendFactorSettersReportNoChangeForIdenticalValues() {
        final PipelineCache c = createCache();
        assertFalse(c.setBlendFactors(SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ZERO, SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ZERO));
        assertFalse(c.setBlendFactors(2, SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ZERO, SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ZERO));
        assertTrue(c.setBlendFactors(2, SDL_GPU_BLENDFACTOR_SRC_ALPHA, SDL_GPU_BLENDFACTOR_ZERO, SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ZERO));
        assertFalse(c.setBlendFactors(2, SDL_GPU_BLENDFACTOR_SRC_ALPHA, SDL_GPU_BLENDFACTOR_ZERO, SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ZERO));
        assertTrue(c.setBlendFactors(SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ZERO, SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ZERO));
    }

    @Test
    void blendEnableIsIndexedByDrawBufferNotAttachment() {
        final int[] twoTargets = {SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM, SDL_GPU_TEXTUREFORMAT_R16G16B16A16_FLOAT};
        final PipelineCache base = createCache();
        base.setColorTargetFormats(twoTargets);
        base.setDrawBuffers(new int[]{3, 5});

        final PipelineCache slot0 = createCache();
        slot0.setColorTargetFormats(twoTargets);
        slot0.setDrawBuffers(new int[]{3, 5});
        slot0.blendEnabledPerDrawBuffer[0] = true;

        final PipelineCache attachment3 = createCache();
        attachment3.setColorTargetFormats(twoTargets);
        attachment3.setDrawBuffers(new int[]{3, 5});
        attachment3.blendEnabledPerDrawBuffer[3] = true;

        assertNotEquals(computeKey(base), computeKey(slot0), "draw buffer 0 is the first color target");
        assertEquals(computeKey(base), computeKey(attachment3), "index 3 is a draw buffer past the two targets");
    }

    @Test
    void polygonOffsetEnableChangesKeyForFilledTrianglesOnly() {
        final PipelineCache a = createCache();
        final PipelineCache b = createCache();
        b.polygonOffsetFillEnabled = true;
        for (int primitive : new int[]{SDL_GPU_PRIMITIVETYPE_TRIANGLELIST, SDL_GPU_PRIMITIVETYPE_TRIANGLESTRIP}) {
            a.primitiveType = b.primitiveType = primitive;
            assertNotEquals(computeKey(a), computeKey(b), "filled triangle offset needs a distinct pipeline");
        }
        for (int primitive : new int[]{SDL_GPU_PRIMITIVETYPE_LINELIST, SDL_GPU_PRIMITIVETYPE_LINESTRIP, SDL_GPU_PRIMITIVETYPE_POINTLIST}) {
            a.primitiveType = b.primitiveType = primitive;
            assertEquals(computeKey(a), computeKey(b), "polygon offset fill must not bias lines or points");
        }
        a.primitiveType = b.primitiveType = SDL_GPU_PRIMITIVETYPE_TRIANGLELIST;
        a.fillMode = b.fillMode = SDL_GPU_FILLMODE_LINE;
        assertEquals(computeKey(a), computeKey(b), "polygon offset fill must not bias wireframe polygons");
    }

    @Test
    void depthBiasValuesShareAPipelineWhileOffsetIsOff() {
        final PipelineCache a = createCache();
        final PipelineCache b = createCache();
        b.depthBiasConstant = 1.0f;
        b.depthBiasSlopeFactor = 2.0f;
        b.depthBiasClamp = 0.5f;

        assertEquals(computeKey(a), computeKey(b), "glPolygonOffset with the offset disabled must not build a new pipeline");
    }

    private static long computeKey(PipelineCache cache) {
        return cache.computeKey(null, null);
    }
}
