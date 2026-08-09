package com.gtnewhorizons.angelica.sdlgpu.shader;

import org.junit.jupiter.api.Test;
import org.lwjgl.util.spvc.Spvc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AttribLocationPatchTest {

    private static final String[] NAMES = {
        "vPosition", "aMaterial", "aTranslateSubChunk", "aTranslateChunk", "aScale", "iris_color"
    };

    private static final int[] DESIRED = {0, 5, 4, 3, 2, 1};
    private static final int[] VEC_SIZES = {3, 1, 3, 3, 3, 4};
    private static final int[] BASE_TYPES = {
        Spvc.SPVC_BASETYPE_FP32, Spvc.SPVC_BASETYPE_INT32, Spvc.SPVC_BASETYPE_FP32,
        Spvc.SPVC_BASETYPE_INT32, Spvc.SPVC_BASETYPE_FP32, Spvc.SPVC_BASETYPE_FP32
    };

    private static ShaderManager.StageReflection reflectionWithInputs(ByteBuffer spirv) {
        final ShaderManager.VsInput[] inputs = new ShaderManager.VsInput[NAMES.length];
        for (int i = 0; i < NAMES.length; i++) {
            spirv.asIntBuffer().put(i, i);
            inputs[i] = new ShaderManager.VsInput(NAMES[i], i, i, VEC_SIZES[i], BASE_TYPES[i]);
        }
        final ShaderManager.StageReflection e = ShaderManager.StageReflection.EMPTY;
        return new ShaderManager.StageReflection(e.counts(), e.samplerNames(), e.extraUniformNames(),
            e.storageImageNames(), e.uboSize(), e.uboMembers(), List.of(inputs), e.vsOutputs(), e.fsInputs(),
            e.maxOutputLocation(), e.numReadonlyStorageBuffers(), e.numReadwriteStorageBuffers(),
            e.numReadonlyStorageTextures(), e.numReadwriteStorageTextures(), e.blocks());
    }

    @Test
    void fullPermutationRemapsEveryInputWithoutCollision() {
        final ShaderManager.ProgramObject prog = new ShaderManager.ProgramObject();
        prog.vertexSpirv = ByteBuffer.allocateDirect(NAMES.length * Integer.BYTES).order(ByteOrder.nativeOrder());
        final ShaderManager.StageReflection vs = reflectionWithInputs(prog.vertexSpirv);
        for (int i = 0; i < NAMES.length; i++) {
            prog.attribLocationBindings.put(NAMES[i], DESIRED[i]);
        }

        ShaderManager.applyAttribLocationsAndInputMask(prog, vs);

        for (int i = 0; i < NAMES.length; i++) {
            final int idx = i;
            assertEquals(DESIRED[i], prog.vertexSpirv.asIntBuffer().get(i), () -> "SPIR-V Location decoration for " + NAMES[idx]);
            assertEquals(DESIRED[i], prog.resolvedAttribLocations.getInt(NAMES[i]), () -> "resolved location for " + NAMES[idx]);
        }
        assertEquals(0x3f, prog.vertexInputMask, "six inputs at 0..5");
    }

    @Test
    void metadataFollowsTheRemappedLocation() {
        final ShaderManager.ProgramObject prog = new ShaderManager.ProgramObject();
        prog.vertexSpirv = ByteBuffer.allocateDirect(NAMES.length * Integer.BYTES).order(ByteOrder.nativeOrder());
        final ShaderManager.StageReflection vs = reflectionWithInputs(prog.vertexSpirv);
        for (int i = 0; i < NAMES.length; i++) {
            prog.attribLocationBindings.put(NAMES[i], DESIRED[i]);
        }

        ShaderManager.applyAttribLocationsAndInputMask(prog, vs);

        assertEquals(4, prog.vertexInputVecSize[1]);
        assertEquals(Spvc.SPVC_BASETYPE_FP32, prog.vertexInputBaseType[1]);
        assertEquals(1, prog.vertexInputVecSize[5]);
        assertEquals(Spvc.SPVC_BASETYPE_INT32, prog.vertexInputBaseType[5]);
    }

    @Test
    void unboundInputsKeepTheirOriginalLocation() {
        final ShaderManager.ProgramObject prog = new ShaderManager.ProgramObject();
        prog.vertexSpirv = ByteBuffer.allocateDirect(NAMES.length * Integer.BYTES).order(ByteOrder.nativeOrder());
        final ShaderManager.StageReflection vs = reflectionWithInputs(prog.vertexSpirv);
        prog.attribLocationBindings.put("vPosition", 0);

        ShaderManager.applyAttribLocationsAndInputMask(prog, vs);

        for (int i = 0; i < NAMES.length; i++) {
            assertEquals(i, prog.resolvedAttribLocations.getInt(NAMES[i]), NAMES[i]);
        }
    }
}
