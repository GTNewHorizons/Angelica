package com.gtnewhorizons.angelica.sdlgpu.pipeline;

import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.glsm.ffp.VAOManager;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.spvc.Spvc;

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

    private static PipelineCache cache(int shaderInputMask, int[] vecSizes, int[] baseTypes) {
        final PipelineCache c = new PipelineCache();
        c.maxAttribs = ContextState.MAX_VERTEX_ATTRIBS;
        c.shaderInputMask = shaderInputMask;
        c.shaderInputVecSize = vecSizes;
        c.shaderInputBaseType = baseTypes;
        return c;
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
}
