package com.gtnewhorizons.angelica.sdlgpu.pipeline;


import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.glsm.ffp.VAOManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.sdl.SDLGPU.*;

class PipelineCacheKeyTest {

    @BeforeEach
    void setUp() {
        VAOManager.init(0);
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

    @Test
    void testIdenticalStateProducesSameKey() {
        final PipelineCache a = createCache();
        final PipelineCache b = createCache();

        final long keyA = computeKey(a);
        final long keyB = computeKey(b);
        assertEquals(keyA, keyB, "Identical state should produce identical keys");
    }

    @Test
    void testBlendChangeProducesDifferentKey() {
        final PipelineCache a = createCache();
        final PipelineCache b = createCache();
        b.blendEnabledPerDrawBuffer[0] = true;

        assertNotEquals(computeKey(a), computeKey(b), "Blend state change should produce different key");
    }

    @Test
    void testDepthTestChangeProducesDifferentKey() {
        final PipelineCache a = createCache();
        final PipelineCache b = createCache();
        a.hasDepthTarget = true;
        b.hasDepthTarget = true;
        b.depthTestEnabled = true;

        assertNotEquals(computeKey(a), computeKey(b), "Depth test change should produce different key");
    }

    @Test
    void testDepthTestWithoutDsvCollapsesToSameKey() {
        final PipelineCache a = createCache();
        final PipelineCache b = createCache();
        b.depthTestEnabled = true;

        assertEquals(computeKey(a), computeKey(b), "Depth test without DSV must produce same key");
    }

    @Test
    void testCullEnableChangeProducesDifferentKey() {
        final PipelineCache a = createCache();
        final PipelineCache b = createCache();
        b.cullEnabled = true;

        assertNotEquals(computeKey(a), computeKey(b), "Enabling cull should produce different key");
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
    void testPrimitiveTypeChangeProducesDifferentKey() {
        final PipelineCache a = createCache();
        final PipelineCache b = createCache();
        b.primitiveType = SDL_GPU_PRIMITIVETYPE_LINELIST;

        assertNotEquals(computeKey(a), computeKey(b), "Primitive type change should produce different key");
    }

    @Test
    void testBlendFactorChangeProducesDifferentKey() {
        final PipelineCache a = createCache();
        final PipelineCache b = createCache();
        b.setBlendFactors(SDL_GPU_BLENDFACTOR_SRC_ALPHA, SDL_GPU_BLENDFACTOR_ONE_MINUS_SRC_ALPHA, SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ZERO);

        assertNotEquals(computeKey(a), computeKey(b), "Blend factor change should produce different key");
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
    void testColorWriteMaskChangeProducesDifferentKey() {
        final PipelineCache a = createCache();
        final PipelineCache b = createCache();
        b.colorWriteMask = SDL_GPU_COLORCOMPONENT_R | SDL_GPU_COLORCOMPONENT_G | SDL_GPU_COLORCOMPONENT_B;

        assertNotEquals(computeKey(a), computeKey(b), "Color write mask change should produce different key");
    }

    @Test
    void testShaderChangeProducesDifferentKey() {
        final PipelineCache a = createCache();
        final PipelineCache b = createCache();
        b.vertexShader = 12345L;
        b.fragmentShader = 67890L;

        assertNotEquals(computeKey(a), computeKey(b), "Shader change should produce different key");
    }

    @Test
    void testDepthCompareOpChangeProducesDifferentKey() {
        final PipelineCache a = createCache();
        final PipelineCache b = createCache();
        b.depthCompareOp = SDL_GPU_COMPAREOP_LESS_OR_EQUAL;

        assertNotEquals(computeKey(a), computeKey(b), "Depth compare op change should produce different key");
    }

    @Test
    void testColorTargetFormatChangeProducesDifferentKey() {
        final PipelineCache a = createCache();
        final PipelineCache b = createCache();
        b.setColorTargetFormats(new int[]{SDL_GPU_TEXTUREFORMAT_R16G16B16A16_FLOAT});

        assertNotEquals(computeKey(a), computeKey(b), "Color target format change should produce different key");
    }

    @Test
    void testDepthBiasChangeProducesDifferentKey() {
        final PipelineCache a = createCache();
        final PipelineCache b = createCache();
        b.depthBiasConstant = 1.0f;
        b.depthBiasSlopeFactor = 2.0f;

        assertNotEquals(computeKey(a), computeKey(b), "Depth bias change should produce different key");
    }

    @Test
    void testDepthBiasClampProducesDifferentKey() {
        final PipelineCache a = createCache();
        final PipelineCache b = createCache();
        a.depthBiasConstant = b.depthBiasConstant = 1.0f;
        a.depthBiasSlopeFactor = b.depthBiasSlopeFactor = 2.0f;
        b.depthBiasClamp = 0.5f;

        assertNotEquals(computeKey(a), computeKey(b), "two draws differing only in polygon-offset clamp must not share a pipeline");
    }

    private long computeKey(PipelineCache cache) {
        try {
            final Method m = PipelineCache.class.getDeclaredMethod("computeKey", PipelineStore.class, ContextState.class);
            m.setAccessible(true);
            return (long) m.invoke(cache, (PipelineStore) null, (ContextState) null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke computeKey", e);
        }
    }
}
