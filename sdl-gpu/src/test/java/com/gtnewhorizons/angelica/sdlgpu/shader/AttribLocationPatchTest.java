package com.gtnewhorizons.angelica.sdlgpu.shader;

import org.junit.jupiter.api.Test;
import org.lwjgl.util.spvc.Spvc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        return reflectionWithInputs(spirv, NAMES);
    }

    private static ShaderManager.StageReflection reflectionWithInputs(ByteBuffer spirv, String[] names) {
        final ShaderManager.VsInput[] inputs = new ShaderManager.VsInput[names.length];
        for (int i = 0; i < names.length; i++) {
            spirv.asIntBuffer().put(i, i);
            inputs[i] = new ShaderManager.VsInput(names[i], i, i, i < VEC_SIZES.length ? VEC_SIZES[i] : 4, i < BASE_TYPES.length ? BASE_TYPES[i] : Spvc.SPVC_BASETYPE_FP32);
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
    void anUnboundInputMovesOffALocationABindingClaimed() {
        final String[] names = {
            "a_PosId", "a_Color", "a_TexCoord", "a_LightCoord", "a_RdhFactor",
            "mc_midTexCoord", "at_tangent", "iris_Normal", "mc_Entity", "at_midBlock"
        };
        final ShaderManager.ProgramObject prog = new ShaderManager.ProgramObject();
        prog.vertexSpirv = ByteBuffer.allocateDirect(names.length * Integer.BYTES).order(ByteOrder.nativeOrder());
        final ShaderManager.StageReflection vs = reflectionWithInputs(prog.vertexSpirv, names);

        int loc = 0;
        for (String name : names) {
            if (!name.equals("a_RdhFactor")) prog.attribLocationBindings.put(name, loc++);
        }

        ShaderManager.applyAttribLocationsAndInputMask(prog, vs);

        assertEquals(4, prog.resolvedAttribLocations.getInt("mc_midTexCoord"), "the binding is authoritative");
        assertEquals(9, prog.resolvedAttribLocations.getInt("a_RdhFactor"), "the unbound input takes the lowest free slot");
        assertEquals(0x3ff, prog.vertexInputMask, "ten inputs at 0..9");

        final Set<Integer> seen = new HashSet<>();
        for (String name : names) {
            final int resolved = prog.resolvedAttribLocations.getInt(name);
            assertTrue(seen.add(resolved), () -> "duplicate location " + resolved + " for " + name);
            assertEquals(resolved, prog.vertexSpirv.asIntBuffer().get(indexOf(names, name)), () -> "SPIR-V Location decoration for " + name);
        }
    }

    @Test
    void aBoundNameAbsentFromTheShaderKeepsItsLocationReserved() {
        final String[] bound = {
            "a_PosId", "a_Color", "a_TexCoord", "a_LightCoord", "mc_midTexCoord",
            "at_tangent", "iris_Normal", "mc_Entity", "at_midBlock"
        };
        final String[] declared = {
            "a_PosId", "a_Color", "a_TexCoord", "a_LightCoord", "a_RdhFactor",
            "mc_midTexCoord", "iris_Normal", "mc_Entity", "at_midBlock"
        };
        final ShaderManager.ProgramObject prog = new ShaderManager.ProgramObject();
        prog.vertexSpirv = ByteBuffer.allocateDirect(declared.length * Integer.BYTES).order(ByteOrder.nativeOrder());
        final ShaderManager.StageReflection vs = reflectionWithInputs(prog.vertexSpirv, declared);

        for (int i = 0; i < bound.length; i++) {
            prog.attribLocationBindings.put(bound[i], i);
        }

        ShaderManager.applyAttribLocationsAndInputMask(prog, vs);

        assertEquals(9, prog.resolvedAttribLocations.getInt("a_RdhFactor"), "location 5 stays reserved for the optimized-out at_tangent");
        assertEquals(0x3df, prog.vertexInputMask, "nine inputs at 0..4 and 6..9");
    }

    private static int indexOf(String[] names, String name) {
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(name)) return i;
        }
        throw new IllegalArgumentException(name);
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
