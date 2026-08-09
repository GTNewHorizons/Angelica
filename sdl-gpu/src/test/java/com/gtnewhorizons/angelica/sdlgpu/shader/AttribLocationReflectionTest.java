package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager.ProgramObject;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager.StageReflection;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager.VsInput;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.util.spvc.Spvc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AttribLocationReflectionTest {

    private static StageReflection reflectionWith(VsInput... inputs) {
        return new StageReflection(
            ShaderManager.ResourceCounts.EMPTY, List.of(), List.of(), List.of(), 0, List.of(),
            List.of(inputs), List.of(), List.of(), -1, 0, 0, 0, 0, ShaderManager.BlockReflection.emptyBlocks());
    }

    private static ByteBuffer spirvWithWord(int offset, int value) {
        final ByteBuffer buf = ByteBuffer.allocateDirect((offset + 1) * 4).order(ByteOrder.nativeOrder());
        buf.asIntBuffer().put(offset, value);
        return buf;
    }

    @Test
    void autoMappedAttributeIsResolved() {
        final ProgramObject prog = new ProgramObject();
        prog.vertexSpirv = spirvWithWord(2, 0);
        ShaderManager.applyAttribLocationsAndInputMask(prog, reflectionWith(new VsInput("vertexId", 2, 0, 1, Spvc.SPVC_BASETYPE_FP32)));

        assertEquals(0, prog.resolvedAttribLocations.getInt("vertexId"));
        assertEquals(1, prog.vertexInputMask);
        assertEquals(-1, prog.resolvedAttribLocations.getInt("doesNotExist"));
    }

    @Test
    void explicitBindingPatchesSpirvAndResolvedMap() {
        final ProgramObject prog = new ProgramObject();
        prog.attribLocationBindings.put("vertexId", 5);
        prog.vertexSpirv = spirvWithWord(2, 0);
        ShaderManager.applyAttribLocationsAndInputMask(prog, reflectionWith(new VsInput("vertexId", 2, 0, 1, Spvc.SPVC_BASETYPE_FP32)));

        assertEquals(5, prog.vertexSpirv.asIntBuffer().get(2));
        assertEquals(5, prog.resolvedAttribLocations.getInt("vertexId"));
        assertEquals(1 << 5, prog.vertexInputMask);
    }

    @Test
    void relinkRefreshesResolvedLocations() {
        final ProgramObject prog = new ProgramObject();
        prog.vertexSpirv = spirvWithWord(2, 0);
        final StageReflection vs = reflectionWith(new VsInput("vertexId", 2, 0, 1, Spvc.SPVC_BASETYPE_FP32));
        ShaderManager.applyAttribLocationsAndInputMask(prog, vs);
        assertEquals(0, prog.resolvedAttribLocations.getInt("vertexId"));

        prog.attribLocationBindings.put("vertexId", 3);
        prog.vertexSpirv = spirvWithWord(2, 0);
        ShaderManager.applyAttribLocationsAndInputMask(prog, vs);
        assertEquals(3, prog.resolvedAttribLocations.getInt("vertexId"));
        assertEquals(1 << 3, prog.vertexInputMask);
    }

    @Test
    void activeAttribNamesComeFromReflectionOnceLinked() {
        final ProgramObject prog = new ProgramObject();
        prog.vertexSpirv = spirvWithWord(6, 1);
        final ByteBuffer buf = prog.vertexSpirv;
        buf.asIntBuffer().put(3, 0);
        ShaderManager.applyAttribLocationsAndInputMask(prog, reflectionWith(
            new VsInput("a_Pos", 3, 0, 3, Spvc.SPVC_BASETYPE_FP32),
            new VsInput("vertexId", 6, 1, 1, Spvc.SPVC_BASETYPE_FP32)));
        prog.linked = true;

        assertEquals(List.of("a_Pos", "vertexId"), prog.getActiveAttribNames());
    }

    @Test
    void activeAttribNamesCacheInvalidatedOnRelink() {
        final ProgramObject prog = new ProgramObject();
        prog.vertexSpirv = spirvWithWord(2, 0);
        ShaderManager.applyAttribLocationsAndInputMask(
            prog, reflectionWith(new VsInput("first", 2, 0, 1, Spvc.SPVC_BASETYPE_FP32)));
        prog.linked = true;
        assertEquals(List.of("first"), prog.getActiveAttribNames());

        prog.vertexSpirv = spirvWithWord(2, 0);
        ShaderManager.applyAttribLocationsAndInputMask(
            prog, reflectionWith(new VsInput("second", 2, 0, 1, Spvc.SPVC_BASETYPE_FP32)));
        assertEquals(List.of("second"), prog.getActiveAttribNames());
    }

    @Test
    void attribGlTypeFromReflection() {
        final ProgramObject prog = new ProgramObject();
        prog.vertexSpirv = spirvWithWord(6, 1);
        prog.vertexSpirv.asIntBuffer().put(3, 0);
        ShaderManager.applyAttribLocationsAndInputMask(prog, reflectionWith(
            new VsInput("vertexId", 3, 0, 1, Spvc.SPVC_BASETYPE_FP32),
            new VsInput("cell", 6, 1, 3, Spvc.SPVC_BASETYPE_INT32)));

        assertEquals(GL11.GL_FLOAT, prog.getAttribGlType("vertexId"));
        assertEquals(GL20.GL_INT_VEC3, prog.getAttribGlType("cell"));
        assertEquals(GL20.GL_FLOAT_VEC4, prog.getAttribGlType("unknown"));
    }
}
