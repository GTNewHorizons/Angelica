package com.gtnewhorizons.angelica.sdlgpu.pipeline;

import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.glsm.ffp.VAOManager;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.spvc.Spvc;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_VERTEXELEMENTFORMAT_FLOAT4;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_VERTEXELEMENTFORMAT_UBYTE4;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_VERTEXELEMENTFORMAT_UBYTE4_NORM;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_VERTEXINPUTRATE_INSTANCE;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_VERTEXINPUTRATE_VERTEX;

class PipelineCacheVertexInputTest {
    private static final int PLACEHOLDER_PITCH = 16;

    private PipelineStore store;

    @BeforeEach
    void setUp() {
        VAOManager.init(0);
        PipelineCache.setSwapchainFormats(new int[]{SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM});
        store = new PipelineStore(null);
    }

    private static int nextTestSetId = 1;

    private static PipelineCache cache(int shaderInputMask, int[] vecSizes, int[] baseTypes) {
        return cache(nextTestSetId++, shaderInputMask, vecSizes, baseTypes);
    }

    private static PipelineCache cache(int vertexInputSetId, int shaderInputMask, int[] vecSizes, int[] baseTypes) {
        final PipelineCache c = new PipelineCache();
        c.setMaxAttribs(ContextState.MAX_VERTEX_ATTRIBS);
        c.setVertexInputs(shaderInputMask, vecSizes, baseTypes, new String[ContextState.MAX_VERTEX_ATTRIBS], vertexInputSetId);
        return c;
    }

    private static void copyVaoState(ContextState.VAOState src, ContextState.VAOState dst) {
        System.arraycopy(src.attribEnabled, 0, dst.attribEnabled, 0, src.attribEnabled.length);
        dst.attribEnabledMask = src.attribEnabledMask;
        System.arraycopy(src.attribSize, 0, dst.attribSize, 0, src.attribSize.length);
        System.arraycopy(src.attribType, 0, dst.attribType, 0, src.attribType.length);
        System.arraycopy(src.attribNormalized, 0, dst.attribNormalized, 0, src.attribNormalized.length);
        System.arraycopy(src.attribIsInteger, 0, dst.attribIsInteger, 0, src.attribIsInteger.length);
        System.arraycopy(src.attribStride, 0, dst.attribStride, 0, src.attribStride.length);
        System.arraycopy(src.attribBinding, 0, dst.attribBinding, 0, src.attribBinding.length);
        System.arraycopy(src.attribRelativeOffset, 0, dst.attribRelativeOffset, 0, src.attribRelativeOffset.length);
        System.arraycopy(src.bindingBuffer, 0, dst.bindingBuffer, 0, src.bindingBuffer.length);
        System.arraycopy(src.bindingOffset, 0, dst.bindingOffset, 0, src.bindingOffset.length);
        System.arraycopy(src.bindingStride, 0, dst.bindingStride, 0, src.bindingStride.length);
        System.arraycopy(src.bindingDivisor, 0, dst.bindingDivisor, 0, src.bindingDivisor.length);
    }

    private static void enableFloatAttrib(ContextState cs, int loc, int size, int stride) {
        final ContextState.VAOState vao = cs.currentVao;
        vao.attribEnabled[loc] = true;
        vao.attribEnabledMask |= (1 << loc);
        vao.attribSize[loc] = size;
        vao.attribType[loc] = GL11.GL_FLOAT;
        vao.attribNormalized[loc] = false;
        vao.attribIsInteger[loc] = false;
        vao.attribStride[loc] = stride;
        vao.bindingStride[loc] = stride; // attribBinding[loc]=loc by VAOState default
    }

    private static void enableAttrib(ContextState cs, int loc, int size, int glType, boolean normalized, boolean isInteger, int stride) {
        final ContextState.VAOState vao = cs.currentVao;
        vao.attribEnabled[loc] = true;
        vao.attribEnabledMask |= (1 << loc);
        vao.attribSize[loc] = size;
        vao.attribType[loc] = glType;
        vao.attribNormalized[loc] = normalized;
        vao.attribIsInteger[loc] = isInteger;
        vao.attribStride[loc] = stride;
        vao.bindingStride[loc] = stride;
    }

    private static PipelineCache coercionCache(String name) {
        final int[] vecSizes = new int[ContextState.MAX_VERTEX_ATTRIBS];
        vecSizes[0] = 4;
        final int[] baseTypes = new int[ContextState.MAX_VERTEX_ATTRIBS];
        baseTypes[0] = Spvc.SPVC_BASETYPE_FP32;
        final PipelineCache c = cache(1, vecSizes, baseTypes);
        final String[] names = new String[ContextState.MAX_VERTEX_ATTRIBS];
        names[0] = name;
        c.shaderInputName = names;
        return c;
    }

    @Test
    void unnormalizedUByte4_withoutResolver_fallsBackToNormAndWarnsOnce() {
        final ContextState cs = new ContextState();
        cs.boundProgram = 7;
        enableAttrib(cs, 0, 4, GL11.GL_UNSIGNED_BYTE, false, false, 4);
        final PipelineCache c = coercionCache("a_Object");

        try (MemoryStack stack = MemoryStack.stackPush()) {
            final PipelineCache.VertexInputResult r = c.buildVertexInput(store, cs, stack);
            assertNotNull(r);
            assertEquals(0L, r.vertexShaderOverride());
            assertEquals(SDL_GPU_VERTEXELEMENTFORMAT_UBYTE4_NORM, r.attrs().get(0).format());
        }
        assertEquals(1, store.loggedFormatSubstitution.size());

        try (MemoryStack stack = MemoryStack.stackPush()) {
            c.buildVertexInput(store, cs, stack);
        }
        assertEquals(1, store.loggedFormatSubstitution.size(), "warning must not repeat per pipeline rebuild");
    }

    @Test
    void unnormalizedUByte4_withResolver_bindsIntegerFormatAndUsesVariantShader() {
        final ContextState cs = new ContextState();
        cs.boundProgram = 7;
        enableAttrib(cs, 0, 4, GL11.GL_UNSIGNED_BYTE, false, false, 4);
        final PipelineCache c = coercionCache("a_Object");

        final ShaderManager.VertexVariant variant = new ShaderManager.VertexVariant();
        variant.sdlShader = 0xABCDL;
        variant.inputBaseType[0] = Spvc.SPVC_BASETYPE_UINT32;
        final int[] seenKey = { 0 };
        store.setVertexVariantResolver((program, key, attribs) -> {
            seenKey[0]++;
            assertEquals(7, program);
            assertEquals(1, attribs.size());
            assertEquals("a_Object", attribs.get(0).name());
            assertEquals(0, attribs.get(0).location(), "the attribute's bound location must reach the retype");
            assertEquals(4, attribs.get(0).declVecSize());
            assertEquals(4, attribs.get(0).boundVecSize());
            assertFalse(attribs.get(0).signed());
            return variant;
        });

        try (MemoryStack stack = MemoryStack.stackPush()) {
            final PipelineCache.VertexInputResult r = c.buildVertexInput(store, cs, stack);
            assertNotNull(r);
            assertEquals(0xABCDL, r.vertexShaderOverride());
            assertEquals(SDL_GPU_VERTEXELEMENTFORMAT_UBYTE4, r.attrs().get(0).format());
        }
        assertEquals(1, seenKey[0]);
        assertTrue(store.loggedFormatSubstitution.isEmpty(), "a converted attribute must not warn");
    }

    @Test
    void normalizedAndIntegerAttribsAreNotCoerced() {
        final ContextState cs = new ContextState();
        cs.boundProgram = 7;
        enableAttrib(cs, 0, 4, GL11.GL_UNSIGNED_BYTE, true, false, 4);
        final PipelineCache c = coercionCache("a_Color");
        store.setVertexVariantResolver((program, key, attribs) -> {
            throw new AssertionError("normalized attribute must not request a variant");
        });
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final PipelineCache.VertexInputResult r = c.buildVertexInput(store, cs, stack);
            assertEquals(0L, r.vertexShaderOverride());
            assertEquals(SDL_GPU_VERTEXELEMENTFORMAT_UBYTE4_NORM, r.attrs().get(0).format());
        }
        assertTrue(store.loggedFormatSubstitution.isEmpty());
    }

    @Test
    void sparseLayout_locations0and2_buildsDenseBindingArray() {
        final int[] vecSizes = new int[ContextState.MAX_VERTEX_ATTRIBS];
        vecSizes[0] = 3; vecSizes[2] = 2;
        final int[] baseTypes = new int[ContextState.MAX_VERTEX_ATTRIBS];
        baseTypes[0] = Spvc.SPVC_BASETYPE_FP32; baseTypes[2] = Spvc.SPVC_BASETYPE_FP32;
        final PipelineCache c = cache(0x5, vecSizes, baseTypes);

        final ContextState cs = new ContextState();
        enableFloatAttrib(cs, 0, 3, 20);
        enableFloatAttrib(cs, 2, 2, 20);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            final PipelineCache.VertexInputResult r = c.buildVertexInput(store, cs, stack);
            assertNotNull(r, "expected non-null result for valid VS layout");
            assertEquals(3, r.numBuffers(), "bindings array must cover slots [0, maxSlot]=[0,2]");
            assertEquals(3, r.numAttributes(), "every binding (including gap slot 1) must have a referencing attribute");

            assertEquals(0, r.bindings().get(0).slot());
            assertEquals(20, r.bindings().get(0).pitch());
            assertEquals(SDL_GPU_VERTEXINPUTRATE_VERTEX, r.bindings().get(0).input_rate());
            assertEquals(1, r.bindings().get(1).slot());
            assertEquals(PLACEHOLDER_PITCH, r.bindings().get(1).pitch());
            assertEquals(SDL_GPU_VERTEXINPUTRATE_INSTANCE, r.bindings().get(1).input_rate());
            assertEquals(2, r.bindings().get(2).slot());
            assertEquals(20, r.bindings().get(2).pitch());
            assertEquals(SDL_GPU_VERTEXINPUTRATE_VERTEX, r.bindings().get(2).input_rate());

            assertEquals(0, r.attrs().get(0).location());
            assertEquals(0, r.attrs().get(0).buffer_slot());
            assertEquals(1, r.attrs().get(1).location());
            assertEquals(1, r.attrs().get(1).buffer_slot());
            assertEquals(SDL_GPU_VERTEXELEMENTFORMAT_FLOAT4, r.attrs().get(1).format());
            assertEquals(2, r.attrs().get(2).location());
            assertEquals(2, r.attrs().get(2).buffer_slot());
        }
    }

    @Test
    void floatProducerAgainstIntShaderInput_refusesToBuild() {
        final int[] vecSizes = new int[ContextState.MAX_VERTEX_ATTRIBS];
        vecSizes[0] = 3; vecSizes[1] = 1;
        final int[] baseTypes = new int[ContextState.MAX_VERTEX_ATTRIBS];
        baseTypes[0] = Spvc.SPVC_BASETYPE_FP32;
        baseTypes[1] = Spvc.SPVC_BASETYPE_INT32;
        final PipelineCache c = cache(0x3, vecSizes, baseTypes);

        final ContextState cs = new ContextState();
        enableFloatAttrib(cs, 0, 3, 12);
        enableFloatAttrib(cs, 1, 4, 16);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            assertNull(c.buildVertexInput(store, cs, stack), "a float producer feeding an int shader input must not produce a pipeline");
        }
    }

    @Test
    void correctedDhGenericLayout_buildsSuccessfully() {
        final int[] vecSizes = new int[ContextState.MAX_VERTEX_ATTRIBS];
        vecSizes[0] = 3; vecSizes[1] = 4;
        final int[] baseTypes = new int[ContextState.MAX_VERTEX_ATTRIBS];
        baseTypes[0] = Spvc.SPVC_BASETYPE_FP32;
        baseTypes[1] = Spvc.SPVC_BASETYPE_FP32;
        final PipelineCache c = cache(0x3, vecSizes, baseTypes);

        final ContextState cs = new ContextState();
        enableFloatAttrib(cs, 0, 3, 12);
        enableFloatAttrib(cs, 1, 4, 16);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            assertNotNull(c.buildVertexInput(store, cs, stack));
        }
    }

    @Test
    void denseLayout_locations0_1_2_noPlaceholders() {
        final int[] vecSizes = new int[ContextState.MAX_VERTEX_ATTRIBS];
        vecSizes[0] = 3; vecSizes[1] = 4; vecSizes[2] = 2;
        final int[] baseTypes = new int[ContextState.MAX_VERTEX_ATTRIBS];
        baseTypes[0] = Spvc.SPVC_BASETYPE_FP32; baseTypes[1] = Spvc.SPVC_BASETYPE_FP32; baseTypes[2] = Spvc.SPVC_BASETYPE_FP32;
        final PipelineCache c = cache(0x7, vecSizes, baseTypes);

        final ContextState cs = new ContextState();
        enableFloatAttrib(cs, 0, 3, 24);
        enableFloatAttrib(cs, 1, 4, 24);
        enableFloatAttrib(cs, 2, 2, 24);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            final PipelineCache.VertexInputResult r = c.buildVertexInput(store, cs, stack);
            assertNotNull(r);
            assertEquals(3, r.numBuffers());
            assertEquals(3, r.numAttributes());
            assertEquals(24, r.bindings().get(0).pitch());
            assertEquals(24, r.bindings().get(1).pitch());
            assertEquals(24, r.bindings().get(2).pitch());
        }
    }

    @Test
    void shaderExpectsButVaoMissing_emitsInstanceExpansion() {
        final int[] vecSizes = new int[ContextState.MAX_VERTEX_ATTRIBS];
        vecSizes[0] = 3; vecSizes[1] = 4;
        final int[] baseTypes = new int[ContextState.MAX_VERTEX_ATTRIBS];
        baseTypes[0] = Spvc.SPVC_BASETYPE_FP32; baseTypes[1] = Spvc.SPVC_BASETYPE_FP32;
        final PipelineCache c = cache(0x3, vecSizes, baseTypes);

        final ContextState cs = new ContextState();
        enableFloatAttrib(cs, 0, 3, 12);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            final PipelineCache.VertexInputResult r = c.buildVertexInput(store, cs, stack);
            assertNotNull(r);
            assertEquals(2, r.numBuffers());
            assertEquals(2, r.numAttributes());
            assertEquals(1, r.bindings().get(1).slot());
            assertEquals(PLACEHOLDER_PITCH, r.bindings().get(1).pitch());
            assertEquals(SDL_GPU_VERTEXINPUTRATE_INSTANCE, r.bindings().get(1).input_rate());
            assertEquals(1, r.attrs().get(1).location());
            assertEquals(1, r.attrs().get(1).buffer_slot());
        }
    }

    @Test
    void vaoExtraAttribNotInShader_droppedFromPipeline() {
        final int[] vecSizes = new int[ContextState.MAX_VERTEX_ATTRIBS];
        vecSizes[0] = 4; vecSizes[2] = 4;
        final int[] baseTypes = new int[ContextState.MAX_VERTEX_ATTRIBS];
        baseTypes[0] = Spvc.SPVC_BASETYPE_FP32; baseTypes[2] = Spvc.SPVC_BASETYPE_FP32;
        final PipelineCache c = cache(0x5, vecSizes, baseTypes); // shaderInputMask = 0x5

        final ContextState cs = new ContextState();
        enableFloatAttrib(cs, 0, 3, 24);
        enableFloatAttrib(cs, 2, 2, 24);
        cs.currentVao.attribEnabled[4] = true;
        cs.currentVao.attribEnabledMask |= (1 << 4);
        cs.currentVao.attribSize[4] = 3;
        cs.currentVao.attribType[4] = GL11.GL_BYTE;
        cs.currentVao.attribNormalized[4] = true;
        cs.currentVao.attribStride[4] = 24;
        cs.currentVao.bindingStride[4] = 24;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            final PipelineCache.VertexInputResult r = c.buildVertexInput(store, cs, stack);
            assertNotNull(r);
            assertEquals(3, r.numBuffers(), "bindings cover slots [0..maxSlot=2], not widened to loc 4");
            assertEquals(3, r.numAttributes(), "2 real + 1 dummy for gap slot 1; loc 4 stays dropped");
            for (int i = 0; i < r.numAttributes(); i++) {
                final int loc = r.attrs().get(i).location();
                final int slot = r.attrs().get(i).buffer_slot();
                if (loc == 4 || slot == 4) {
                    throw new AssertionError("attr[" + i + "] references loc=" + loc + " buffer_slot=" + slot + " -- should have been filtered out");
                }
            }

            assertEquals(1, r.attrs().get(1).location());
            assertEquals(1, r.attrs().get(1).buffer_slot());
            assertEquals(SDL_GPU_VERTEXELEMENTFORMAT_FLOAT4, r.attrs().get(1).format());
        }
    }

    @Test
    void interiorGapWithoutExpansion_fillsGapWithPlaceholder() {
        final int[] vecSizes = new int[ContextState.MAX_VERTEX_ATTRIBS];
        vecSizes[0] = 3; vecSizes[3] = 2;
        final int[] baseTypes = new int[ContextState.MAX_VERTEX_ATTRIBS];
        baseTypes[0] = Spvc.SPVC_BASETYPE_FP32; baseTypes[3] = Spvc.SPVC_BASETYPE_FP32;
        final PipelineCache c = cache(0x9, vecSizes, baseTypes);

        final ContextState cs = new ContextState();
        enableFloatAttrib(cs, 0, 3, 20);
        enableFloatAttrib(cs, 3, 2, 20);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            final PipelineCache.VertexInputResult r = c.buildVertexInput(store, cs, stack);
            assertNotNull(r);
            assertEquals(4, r.numBuffers(), "maxSlot=3 -> 4 entries");
            assertEquals(4, r.numAttributes(), "2 real + 2 dummies for gap slots 1 and 2");

            assertEquals(20, r.bindings().get(0).pitch());
            assertEquals(PLACEHOLDER_PITCH, r.bindings().get(1).pitch());
            assertEquals(SDL_GPU_VERTEXINPUTRATE_INSTANCE, r.bindings().get(1).input_rate());
            assertEquals(PLACEHOLDER_PITCH, r.bindings().get(2).pitch());
            assertEquals(SDL_GPU_VERTEXINPUTRATE_INSTANCE, r.bindings().get(2).input_rate());
            assertEquals(20, r.bindings().get(3).pitch());

            assertEquals(0, r.attrs().get(0).buffer_slot());
            assertEquals(1, r.attrs().get(1).buffer_slot());
            assertEquals(SDL_GPU_VERTEXELEMENTFORMAT_FLOAT4, r.attrs().get(1).format());
            assertEquals(2, r.attrs().get(2).buffer_slot());
            assertEquals(SDL_GPU_VERTEXELEMENTFORMAT_FLOAT4, r.attrs().get(2).format());
            assertEquals(3, r.attrs().get(3).buffer_slot());
        }
    }

    @Test
    void enabledAttribWithoutPointer_treatedAsDisabled() {
        final int[] vecSizes = new int[ContextState.MAX_VERTEX_ATTRIBS];
        vecSizes[0] = 3; vecSizes[1] = 4; vecSizes[3] = 2;
        final int[] baseTypes = new int[ContextState.MAX_VERTEX_ATTRIBS];
        baseTypes[0] = Spvc.SPVC_BASETYPE_FP32; baseTypes[1] = Spvc.SPVC_BASETYPE_FP32; baseTypes[3] = Spvc.SPVC_BASETYPE_FP32;
        final PipelineCache c = cache(0xB, vecSizes, baseTypes); // locs 0, 1, 3

        final ContextState cs = new ContextState();
        enableFloatAttrib(cs, 0, 3, 16);
        enableFloatAttrib(cs, 1, 4, 16);
        cs.currentVao.attribEnabled[3] = true;
        cs.currentVao.attribEnabledMask |= (1 << 3);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            final PipelineCache.VertexInputResult r = c.buildVertexInput(store, cs, stack);
            assertNotNull(r, "enabled-but-unpointed attrib must not fail pipeline construction");
            assertEquals(4, r.numBuffers());
            assertEquals(PLACEHOLDER_PITCH, r.bindings().get(3).pitch());
            assertEquals(SDL_GPU_VERTEXINPUTRATE_INSTANCE, r.bindings().get(3).input_rate());
            assertEquals(3, r.attrs().get(3).location());
            assertEquals(3, r.attrs().get(3).buffer_slot());
        }
    }

    @Test
    void everyBindingHasReferencingAttribute_evenAcrossGaps() {
        // Metal MTLVertexDescriptor requirement: every declared buffer layout must be referenced by at least one attribute
        final int[] vecSizes = new int[ContextState.MAX_VERTEX_ATTRIBS];
        vecSizes[0] = 3; vecSizes[15] = 4;
        final int[] baseTypes = new int[ContextState.MAX_VERTEX_ATTRIBS];
        baseTypes[0] = Spvc.SPVC_BASETYPE_FP32; baseTypes[15] = Spvc.SPVC_BASETYPE_FP32;
        final PipelineCache c = cache(0x8001, vecSizes, baseTypes);

        final ContextState cs = new ContextState();
        enableFloatAttrib(cs, 0, 3, 12);
        enableFloatAttrib(cs, 15, 4, 16);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            final PipelineCache.VertexInputResult r = c.buildVertexInput(store, cs, stack);
            assertNotNull(r);
            assertEquals(16, r.numBuffers());
            assertEquals(16, r.numAttributes());
            for (int s = 0; s < r.numBuffers(); s++) {
                boolean covered = false;
                for (int k = 0; k < r.numAttributes(); k++) {
                    if (r.attrs().get(k).buffer_slot() == s) { covered = true; break; }
                }
                assertTrue(covered, "slot " + s + " has no referencing attribute -- Metal will reject");
            }
        }
    }

    @Test
    void enabledAttribWithDeadBuffer_demotedToConstantExpansion() {
        final int[] vecSizes = new int[ContextState.MAX_VERTEX_ATTRIBS];
        vecSizes[0] = 3; vecSizes[1] = 4;
        final int[] baseTypes = new int[ContextState.MAX_VERTEX_ATTRIBS];
        baseTypes[0] = Spvc.SPVC_BASETYPE_FP32; baseTypes[1] = Spvc.SPVC_BASETYPE_FP32;
        final PipelineCache c = cache(0x3, vecSizes, baseTypes);
        store.setBufferHandleResolver(id -> id == 42 ? 0xBEEFL : 0L);

        final ContextState cs = new ContextState();
        enableFloatAttrib(cs, 0, 3, 16);
        cs.currentVao.bindingBuffer[0] = 42; // live
        enableFloatAttrib(cs, 1, 4, 4);
        cs.currentVao.bindingBuffer[1] = 7;  // dead handle

        try (MemoryStack stack = MemoryStack.stackPush()) {
            final PipelineCache.VertexInputResult r = c.buildVertexInput(store, cs, stack);
            assertNotNull(r);
            assertEquals(16, r.bindings().get(0).pitch());
            assertEquals(SDL_GPU_VERTEXINPUTRATE_VERTEX, r.bindings().get(0).input_rate());
            assertEquals(PLACEHOLDER_PITCH, r.bindings().get(1).pitch());
            assertEquals(SDL_GPU_VERTEXINPUTRATE_INSTANCE, r.bindings().get(1).input_rate());
            assertEquals(SDL_GPU_VERTEXELEMENTFORMAT_FLOAT4, r.attrs().get(1).format());
        }
    }

    @Test
    void pipelineKeyDiffersBetweenDeadAndLiveBinding() {
        final int[] vecSizes = new int[ContextState.MAX_VERTEX_ATTRIBS];
        vecSizes[0] = 3; vecSizes[1] = 4;
        final int[] baseTypes = new int[ContextState.MAX_VERTEX_ATTRIBS];
        baseTypes[0] = Spvc.SPVC_BASETYPE_FP32; baseTypes[1] = Spvc.SPVC_BASETYPE_FP32;
        final PipelineCache c = cache(0x3, vecSizes, baseTypes);

        final ContextState cs = new ContextState();
        enableFloatAttrib(cs, 0, 3, 16);
        cs.currentVao.bindingBuffer[0] = 42;
        enableFloatAttrib(cs, 1, 4, 4);
        cs.currentVao.bindingBuffer[1] = 7;

        store.setBufferHandleResolver(id -> 0xBEEFL); // both live
        final long liveKey = c.computeKey(store, cs);
        store.setBufferHandleResolver(id -> id == 42 ? 0xBEEFL : 0L); // loc 1's buffer dead
        store.bumpLivenessGen();
        final long deadKey = c.computeKey(store, cs);

        assertTrue(liveKey != deadKey, "demotion must produce a distinct pipeline key or a cached per-vertex pipeline would be reused for the constant layout");
    }

    @Test
    void bindVertexBufferInvalidatesCachedKeyOnlyWhenLivenessFlips() {
        final PipelineCache c = cache(0x1, new int[ContextState.MAX_VERTEX_ATTRIBS], new int[ContextState.MAX_VERTEX_ATTRIBS]);
        store.setBufferHandleResolver(id -> id == 9 ? 0L : 0xBEEFL);

        assertTrue(c.markInputDirtyIfLivenessChanged(store, 42, 9), "live -> dead must invalidate the cached input layout");
        assertTrue(c.markInputDirtyIfLivenessChanged(store, 9, 42), "dead -> live must invalidate the cached input layout");
        assertFalse(c.markInputDirtyIfLivenessChanged(store, 42, 7), "swapping two live buffers must not force an input rehash");
    }

    @Test
    void vertexInputSetIdChangeInvalidatesInputHash() {
        final int[] vecSizesA = new int[ContextState.MAX_VERTEX_ATTRIBS];
        vecSizesA[0] = 3;
        final int[] baseTypesA = new int[ContextState.MAX_VERTEX_ATTRIBS];
        baseTypesA[0] = Spvc.SPVC_BASETYPE_FP32;
        final String[] namesA = new String[ContextState.MAX_VERTEX_ATTRIBS];
        namesA[0] = "a_Pos";
        final PipelineCache c = cache(1, 0x1, vecSizesA, baseTypesA);

        final ContextState cs = new ContextState();
        enableFloatAttrib(cs, 0, 3, 12);

        final long keyA = Reflect.invoke(c, "currentKey", new Class<?>[] { PipelineStore.class, ContextState.class }, store, cs);
        final boolean dirtyAfterKeyA = Reflect.get(c, "inputDirty");
        assertFalse(dirtyAfterKeyA, "currentKey must clear the dirty flag once recomputed");

        c.setVertexInputs(0x1, vecSizesA, baseTypesA, namesA, 1);
        final boolean dirtyAfterSameId = Reflect.get(c, "inputDirty");
        assertFalse(dirtyAfterSameId, "an identical input-set id must not force a rehash");

        c.setVertexInputs(0x1, vecSizesA.clone(), baseTypesA.clone(), namesA.clone(), 1);
        final boolean dirtyAfterSameIdDistinctArrays = Reflect.get(c, "inputDirty");
        assertFalse(dirtyAfterSameIdDistinctArrays, "the same interned id must not force a rehash even with distinct array instances");

        final int[] vecSizesB = new int[ContextState.MAX_VERTEX_ATTRIBS];
        vecSizesB[0] = 3; vecSizesB[1] = 4;
        final int[] baseTypesB = new int[ContextState.MAX_VERTEX_ATTRIBS];
        baseTypesB[0] = Spvc.SPVC_BASETYPE_FP32; baseTypesB[1] = Spvc.SPVC_BASETYPE_FP32;
        final String[] namesB = new String[ContextState.MAX_VERTEX_ATTRIBS];
        namesB[0] = "a_Pos"; namesB[1] = "a_Extra";

        c.setVertexInputs(0x3, vecSizesB, baseTypesB, namesB, 2);
        final boolean dirtyAfterChangedId = Reflect.get(c, "inputDirty");
        assertTrue(dirtyAfterChangedId, "a changed input-set id must invalidate the cached input hash");

        final long keyB = Reflect.invoke(c, "currentKey", new Class<?>[] { PipelineStore.class, ContextState.class }, store, cs);
        assertTrue(keyA != keyB, "currentKey must reflect the new signature, not a stale cached input hash");
    }

    @Test
    void bindingsArrayIsDenseIndexedBySlot() {
        // D3D12 requirement: SDL's D3D12 backend reads vertex_buffer_descriptions[buffer_slot] as a flat array, so bindings.get(i).slot() must equal i
        final int[] vecSizes = new int[ContextState.MAX_VERTEX_ATTRIBS];
        vecSizes[0] = 3; vecSizes[5] = 2;
        final int[] baseTypes = new int[ContextState.MAX_VERTEX_ATTRIBS];
        baseTypes[0] = Spvc.SPVC_BASETYPE_FP32; baseTypes[5] = Spvc.SPVC_BASETYPE_FP32;
        final PipelineCache c = cache(0x21, vecSizes, baseTypes);

        final ContextState cs = new ContextState();
        enableFloatAttrib(cs, 0, 3, 12);
        enableFloatAttrib(cs, 5, 2, 8);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            final PipelineCache.VertexInputResult r = c.buildVertexInput(store, cs, stack);
            assertNotNull(r);
            assertEquals(6, r.numBuffers());
            for (int i = 0; i < r.numBuffers(); i++) {
                assertEquals(i, r.bindings().get(i).slot(), "binding[" + i + "].slot must equal its array index");
            }
        }
    }

    @Test
    void offsetOnlyPointerChange_keepsKeyAndSkipsRecompute() {
        final int[] vecSizes = new int[ContextState.MAX_VERTEX_ATTRIBS];
        vecSizes[0] = 3;
        final int[] baseTypes = new int[ContextState.MAX_VERTEX_ATTRIBS];
        baseTypes[0] = Spvc.SPVC_BASETYPE_FP32;
        final PipelineCache c = cache(0x1, vecSizes, baseTypes);

        final ContextState cs = new ContextState();
        final VertexAttribs attribs = new VertexAttribs();
        attribs.applyVertexAttribPointer(store, cs, 0, 3, GL11.GL_FLOAT, false, false, 12, 100L);

        final long keyA = c.computeKey(store, cs);
        final int runsAfterA = c.inputHashLoopRuns;

        attribs.applyVertexAttribPointer(store, cs, 0, 3, GL11.GL_FLOAT, false, false, 12, 104L);
        final long keyB = c.computeKey(store, cs);

        assertEquals(keyA, keyB, "an offset change that keeps the alignment class must not change the pipeline key");
        assertEquals(runsAfterA, c.inputHashLoopRuns, "an offset-only change must not force the input-hash loop to rerun");
    }

    @Test
    void identicalReissuedPointer_isNoOp() {
        final int[] vecSizes = new int[ContextState.MAX_VERTEX_ATTRIBS];
        vecSizes[0] = 3;
        final int[] baseTypes = new int[ContextState.MAX_VERTEX_ATTRIBS];
        baseTypes[0] = Spvc.SPVC_BASETYPE_FP32;
        final PipelineCache c = cache(0x1, vecSizes, baseTypes);

        final ContextState cs = new ContextState();
        final VertexAttribs attribs = new VertexAttribs();
        attribs.applyVertexAttribPointer(store, cs, 0, 3, GL11.GL_FLOAT, false, false, 12, 100L);

        final long keyA = c.computeKey(store, cs);
        final int runsAfterA = c.inputHashLoopRuns;

        attribs.applyVertexAttribPointer(store, cs, 0, 3, GL11.GL_FLOAT, false, false, 12, 100L);
        final long keyB = c.computeKey(store, cs);

        assertEquals(keyA, keyB, "an identical re-issued pointer must not change the key");
        assertEquals(runsAfterA, c.inputHashLoopRuns, "an identical re-issued pointer must be a no-op and not force a rehash");
    }

    @Test
    void twoEqualLayoutVaosShareKey() {
        final int[] vecSizes = new int[ContextState.MAX_VERTEX_ATTRIBS];
        vecSizes[0] = 3; vecSizes[1] = 4;
        final int[] baseTypes = new int[ContextState.MAX_VERTEX_ATTRIBS];
        baseTypes[0] = Spvc.SPVC_BASETYPE_FP32; baseTypes[1] = Spvc.SPVC_BASETYPE_FP32;
        final PipelineCache c = cache(0x3, vecSizes, baseTypes);

        final ContextState csA = new ContextState();
        enableFloatAttrib(csA, 0, 3, 28);
        enableFloatAttrib(csA, 1, 4, 28);

        final ContextState csB = new ContextState();
        enableFloatAttrib(csB, 0, 3, 28);
        enableFloatAttrib(csB, 1, 4, 28);

        assertEquals(c.computeKey(store, csA), c.computeKey(store, csB), "two VAOs with identical layouts must hash to the same input key");
    }

    @Test
    void everyLayoutFieldChangesKey() {
        final int[] vecSizes = new int[ContextState.MAX_VERTEX_ATTRIBS];
        vecSizes[0] = 3;
        final int[] baseTypes = new int[ContextState.MAX_VERTEX_ATTRIBS];
        baseTypes[0] = Spvc.SPVC_BASETYPE_FP32;
        final PipelineCache c = cache(0x1, vecSizes, baseTypes);

        final ContextState base = new ContextState();
        enableFloatAttrib(base, 0, 3, 16);
        final long baseKey = c.computeKey(store, base);

        final ContextState sizeVariant = new ContextState();
        enableFloatAttrib(sizeVariant, 0, 4, 16);
        assertTrue(baseKey != c.computeKey(store, sizeVariant), "size must affect the input key");

        final ContextState typeVariant = new ContextState();
        enableAttrib(typeVariant, 0, 3, GL11.GL_UNSIGNED_BYTE, false, false, 16);
        assertTrue(baseKey != c.computeKey(store, typeVariant), "type must affect the input key");

        final ContextState normalizedVariant = new ContextState();
        enableAttrib(normalizedVariant, 0, 3, GL11.GL_FLOAT, true, false, 16);
        assertTrue(baseKey != c.computeKey(store, normalizedVariant), "normalized must affect the input key");

        final ContextState integerVariant = new ContextState();
        enableAttrib(integerVariant, 0, 3, GL11.GL_FLOAT, false, true, 16);
        assertTrue(baseKey != c.computeKey(store, integerVariant), "isInteger must affect the input key");

        final ContextState strideVariant = new ContextState();
        enableFloatAttrib(strideVariant, 0, 3, 20);
        assertTrue(baseKey != c.computeKey(store, strideVariant), "stride must affect the input key");

        final ContextState bindingStrideVariant = new ContextState();
        enableFloatAttrib(bindingStrideVariant, 0, 3, 16);
        bindingStrideVariant.currentVao.bindingStride[0] = 20;
        assertTrue(baseKey != c.computeKey(store, bindingStrideVariant), "binding stride must affect the input key");

        final ContextState divisorVariant = new ContextState();
        enableFloatAttrib(divisorVariant, 0, 3, 16);
        divisorVariant.currentVao.bindingDivisor[0] = 1;
        assertTrue(baseKey != c.computeKey(store, divisorVariant), "binding divisor must affect the input key");

        final ContextState relativeOffsetVariant = new ContextState();
        enableFloatAttrib(relativeOffsetVariant, 0, 3, 16);
        relativeOffsetVariant.currentVao.attribRelativeOffset[0] = 4;
        assertTrue(baseKey != c.computeKey(store, relativeOffsetVariant), "relative offset must affect the input key");

        final ContextState bindingVariant = new ContextState();
        enableFloatAttrib(bindingVariant, 0, 3, 16);
        bindingVariant.currentVao.attribBinding[0] = 1;
        assertTrue(baseKey != c.computeKey(store, bindingVariant), "binding index must affect the input key");

        final ContextState misalignVariant = new ContextState();
        enableFloatAttrib(misalignVariant, 0, 3, 16);
        misalignVariant.currentVao.bindingOffset[0] = 1;
        assertTrue(baseKey != c.computeKey(store, misalignVariant), "binding offset misalignment must affect the input key");

        final ContextState disabledVariant = new ContextState();
        assertTrue(baseKey != c.computeKey(store, disabledVariant), "enabled state must affect the input key");
    }

    @Test
    void livenessGenBumpForcesRecompute() {
        final int[] vecSizes = new int[ContextState.MAX_VERTEX_ATTRIBS];
        vecSizes[0] = 3;
        final int[] baseTypes = new int[ContextState.MAX_VERTEX_ATTRIBS];
        baseTypes[0] = Spvc.SPVC_BASETYPE_FP32;
        final PipelineCache c = cache(0x1, vecSizes, baseTypes);

        final ContextState cs = new ContextState();
        enableFloatAttrib(cs, 0, 3, 12);

        c.computeKey(store, cs);
        final int runsAfterFirst = c.inputHashLoopRuns;

        c.computeKey(store, cs);
        assertEquals(runsAfterFirst, c.inputHashLoopRuns, "an unchanged VAO must hit the cached input hash");

        store.bumpLivenessGen();
        c.computeKey(store, cs);
        assertEquals(runsAfterFirst + 1, c.inputHashLoopRuns, "a liveness generation bump must force the input-hash loop to rerun");
    }

    @Test
    void alternatingTwoInputSetsOnOneVao_loopRunsExactlyTwice() {
        final int[] vecSizesA = new int[ContextState.MAX_VERTEX_ATTRIBS];
        vecSizesA[0] = 3;
        final int[] baseTypesA = new int[ContextState.MAX_VERTEX_ATTRIBS];
        baseTypesA[0] = Spvc.SPVC_BASETYPE_FP32;
        final int[] vecSizesB = new int[ContextState.MAX_VERTEX_ATTRIBS];
        vecSizesB[0] = 3; vecSizesB[1] = 4;
        final int[] baseTypesB = new int[ContextState.MAX_VERTEX_ATTRIBS];
        baseTypesB[0] = Spvc.SPVC_BASETYPE_FP32; baseTypesB[1] = Spvc.SPVC_BASETYPE_FP32;
        final String[] names = new String[ContextState.MAX_VERTEX_ATTRIBS];

        final PipelineCache c = new PipelineCache();
        c.setMaxAttribs(ContextState.MAX_VERTEX_ATTRIBS);

        final ContextState cs = new ContextState();
        enableFloatAttrib(cs, 0, 3, 12);
        enableFloatAttrib(cs, 1, 4, 16);

        c.setVertexInputs(0x1, vecSizesA, baseTypesA, names, 1);
        c.computeKey(store, cs);
        c.setVertexInputs(0x3, vecSizesB, baseTypesB, names, 2);
        c.computeKey(store, cs);
        c.setVertexInputs(0x1, vecSizesA, baseTypesA, names, 1);
        c.computeKey(store, cs);
        c.setVertexInputs(0x3, vecSizesB, baseTypesB, names, 2);
        c.computeKey(store, cs);

        assertEquals(2, c.inputHashLoopRuns, "alternating between two known input sets on one VAO must only recompute twice, then hit the cached slots");
    }

    @Test
    void togglingOneAttribEnable_loopRunsExactlyTwice() {
        final int[] vecSizes = new int[ContextState.MAX_VERTEX_ATTRIBS];
        vecSizes[0] = 3; vecSizes[1] = 4;
        final int[] baseTypes = new int[ContextState.MAX_VERTEX_ATTRIBS];
        baseTypes[0] = Spvc.SPVC_BASETYPE_FP32; baseTypes[1] = Spvc.SPVC_BASETYPE_FP32;
        final PipelineCache c = cache(1, 0x3, vecSizes, baseTypes);

        final ContextState cs = new ContextState();
        enableFloatAttrib(cs, 0, 3, 12);
        enableFloatAttrib(cs, 1, 4, 16);
        final ContextState.VAOState vao = cs.currentVao;

        vao.attribEnabled[1] = false;
        vao.attribEnabledMask &= ~(1 << 1);
        c.computeKey(store, cs);

        vao.attribEnabled[1] = true;
        vao.attribEnabledMask |= (1 << 1);
        c.computeKey(store, cs);

        vao.attribEnabled[1] = false;
        vao.attribEnabledMask &= ~(1 << 1);
        c.computeKey(store, cs);

        vao.attribEnabled[1] = true;
        vao.attribEnabledMask |= (1 << 1);
        c.computeKey(store, cs);

        assertEquals(2, c.inputHashLoopRuns, "toggling one attrib's enabled state between two known masks must only recompute twice, then hit the cached slots");
    }

    @Test
    void nineDistinctInputSets_correctKeysAfterEviction() {
        final ContextState cs = new ContextState();
        enableFloatAttrib(cs, 0, 3, 12);
        final PipelineCache c = new PipelineCache();
        c.setMaxAttribs(ContextState.MAX_VERTEX_ATTRIBS);
        final int[] vecSizes = new int[ContextState.MAX_VERTEX_ATTRIBS];
        final int[] baseTypes = new int[ContextState.MAX_VERTEX_ATTRIBS];
        final String[] names = new String[ContextState.MAX_VERTEX_ATTRIBS];

        for (int mask = 1; mask <= 9; mask++) {
            c.setVertexInputs(mask, vecSizes, baseTypes, names, mask);
            c.computeKey(store, cs);
        }

        for (int mask = 1; mask <= 9; mask++) {
            c.setVertexInputs(mask, vecSizes, baseTypes, names, mask);
            final long recomputed = c.computeKey(store, cs);

            final PipelineCache fresh = new PipelineCache();
            fresh.setMaxAttribs(ContextState.MAX_VERTEX_ATTRIBS);
            fresh.setVertexInputs(mask, vecSizes, baseTypes, names, mask);
            final ContextState freshCs = new ContextState();
            enableFloatAttrib(freshCs, 0, 3, 12);
            final long reference = fresh.computeKey(store, freshCs);

            assertEquals(reference, recomputed, "key for input set mask=" + mask + " must match a fresh, uncached computation even after 8-slot eviction");
        }
    }

    @Test
    void internVertexInputSet_equalSetsShareId_differentSetsDiffer() {
        final ShaderManager sm = new ShaderManager(null);
        final int[] vecSizesA = new int[ContextState.MAX_VERTEX_ATTRIBS];
        vecSizesA[0] = 3;
        final int[] baseTypesA = new int[ContextState.MAX_VERTEX_ATTRIBS];
        baseTypesA[0] = Spvc.SPVC_BASETYPE_FP32;

        final int idA1 = Reflect.invoke(sm, "internVertexInputSet", new Class<?>[] { int.class, int[].class, int[].class }, 0x1, vecSizesA, baseTypesA);
        final int idA2 = Reflect.invoke(sm, "internVertexInputSet", new Class<?>[] { int.class, int[].class, int[].class }, 0x1, vecSizesA.clone(), baseTypesA.clone());
        assertEquals(idA1, idA2, "two equal input sets, even from distinct array instances, must intern to the same id");

        final int[] vecSizesB = new int[ContextState.MAX_VERTEX_ATTRIBS];
        vecSizesB[0] = 3; vecSizesB[1] = 4;
        final int[] baseTypesB = new int[ContextState.MAX_VERTEX_ATTRIBS];
        baseTypesB[0] = Spvc.SPVC_BASETYPE_FP32; baseTypesB[1] = Spvc.SPVC_BASETYPE_FP32;
        final int idB = Reflect.invoke(sm, "internVertexInputSet", new Class<?>[] { int.class, int[].class, int[].class }, 0x3, vecSizesB, baseTypesB);
        assertTrue(idA1 != idB, "different input sets must never share an id");
        assertTrue(idA1 >= 1 && idB >= 1, "interned ids start at 1");
    }

    @Test
    void randomizedCachedKeyMatchesFreshComputation() {
        final Random rnd = new Random(12345L);

        final int programCount = 4;
        final int[] progMasks = { 0x1, 0x3, 0x5, 0x7 };
        final int[][] progVecSizes = new int[programCount][];
        final int[][] progBaseTypes = new int[programCount][];
        for (int p = 0; p < programCount; p++) {
            progVecSizes[p] = new int[ContextState.MAX_VERTEX_ATTRIBS];
            progBaseTypes[p] = new int[ContextState.MAX_VERTEX_ATTRIBS];
            for (int i = 0; i < 3; i++) {
                progVecSizes[p][i] = 1 + ((p + i) % 4);
                progBaseTypes[p][i] = (p + i) % 3;
            }
        }
        final String[] names = new String[ContextState.MAX_VERTEX_ATTRIBS];

        final PipelineCache cachedC = new PipelineCache();
        cachedC.setMaxAttribs(ContextState.MAX_VERTEX_ATTRIBS);
        final ContextState cachedCs = new ContextState();
        int currentProgram = 0;
        cachedC.setVertexInputs(progMasks[0], progVecSizes[0], progBaseTypes[0], names, 1);

        for (int step = 0; step < 300; step++) {
            final ContextState.VAOState vao = cachedCs.currentVao;
            final int action = rnd.nextInt(5);
            switch (action) {
                case 0 -> {
                    final int idx = rnd.nextInt(4);
                    if (vao.attribEnabled[idx]) {
                        vao.attribEnabled[idx] = false;
                        vao.attribEnabledMask &= ~(1 << idx);
                    } else {
                        vao.attribEnabled[idx] = true;
                        vao.attribEnabledMask |= (1 << idx);
                        if (vao.attribType[idx] == 0) vao.attribType[idx] = GL11.GL_FLOAT;
                    }
                }
                case 1 -> {
                    final int idx = rnd.nextInt(4);
                    vao.attribSize[idx] = 1 + rnd.nextInt(4);
                    vao.attribType[idx] = GL11.GL_FLOAT;
                    vao.attribStride[idx] = 4 + rnd.nextInt(28);
                    vao.bindingStride[vao.attribBinding[idx]] = vao.attribStride[idx];
                    vao.invalidateInputHash();
                }
                case 2 -> {
                    final int idx = rnd.nextInt(4);
                    vao.bindingDivisor[vao.attribBinding[idx]] = rnd.nextInt(2);
                    vao.invalidateInputHash();
                }
                case 3 -> {
                    currentProgram = rnd.nextInt(programCount);
                    cachedC.setVertexInputs(progMasks[currentProgram], progVecSizes[currentProgram], progBaseTypes[currentProgram], names, currentProgram + 1);
                }
                case 4 -> store.bumpLivenessGen();
                default -> throw new AssertionError("unreachable");
            }

            final long cachedKey = cachedC.computeKey(store, cachedCs);

            final PipelineCache freshC = new PipelineCache();
            freshC.setMaxAttribs(cachedC.maxAttribs());
            freshC.setVertexInputs(progMasks[currentProgram], progVecSizes[currentProgram], progBaseTypes[currentProgram], names, currentProgram + 1);
            final ContextState freshCs = new ContextState();
            copyVaoState(vao, freshCs.currentVao);
            final long freshKey = freshC.computeKey(store, freshCs);

            assertEquals(freshKey, cachedKey, "step " + step + ": cached key must equal a from-scratch computation");
        }
    }
}
