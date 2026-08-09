package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.glsm.hooks.PerFrameUniformBlock;
import com.gtnewhorizons.angelica.glsm.shader.UniformType;
import com.gtnewhorizons.angelica.glsm.hooks.PerFrameUniformBlock.Member;
import com.gtnewhorizons.angelica.glsm.shader.SpirvCompiler;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.shaderc.Shaderc;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.system.MemoryUtil.memFree;

class PerFrameBlockLocationRoutingTest {

    private static final PerFrameUniformBlock PER_FRAME = new PerFrameUniformBlock(List.of(
        new Member("cameraPosition", UniformType.VEC3),
        new Member("frameTimeCounter", UniformType.FLOAT)));

    private static final PerFrameUniformBlock PER_PASS = new PerFrameUniformBlock(List.of(
        new Member("gbufferModelView", UniformType.MAT4)));

    private static final String SOURCE = """
        #version 330 core
        uniform vec3 cameraPosition;
        uniform float frameTimeCounter;
        uniform mat4 gbufferModelView;
        uniform float packOwned;
        out vec4 fragColor;
        void main() {
            fragColor = gbufferModelView * vec4(cameraPosition, frameTimeCounter * packOwned);
        }
        """;

    private static ShaderManager.ProgramObject linkedProgram() {
        final String injected = PerFrameBlockInjector.inject(SOURCE, PER_FRAME, PER_PASS);
        final SpirvCompiler.Result r = SpirvCompiler.compile(injected, Shaderc.shaderc_fragment_shader, "routing.frag", SpirvCompiler.Options.vulkanForced460Core());
        assertNotNull(r.spirv(), () -> "compile failed: " + r.error());

        final ByteBuffer spirv = r.spirv();
        try {
            final ShaderManager.StageReflection refl = ShaderManager.reflectStage(spirv, false);
            final ShaderManager.ProgramObject prog = new ShaderManager.ProgramObject();
            ShaderManager.applyUboMembers(prog, refl, false);
            ShaderManager.applyBlockMembers(prog, refl);
            prog.buildUniformSlotArrays();
            return prog;
        } finally {
            memFree(spirv);
        }
    }

    @Test
    void blockMembersGetLocationsCarryingTheReflectedOffset() {
        final ShaderManager.ProgramObject prog = linkedProgram();

        for (String name : List.of("cameraPosition", "frameTimeCounter")) {
            final int loc = prog.nameToLocation.getInt(name);
            assertTrue(loc >= 0, name + " must have a location despite its loose declaration being stripped");
            assertNotNull(prog.blockInfoBySlot[ShaderManager.BLOCK_PER_FRAME][loc], name + " must route to the per-frame block");
        }
        final int mvLoc = prog.nameToLocation.getInt("gbufferModelView");
        assertTrue(mvLoc >= 0);
        assertNotNull(prog.blockInfoBySlot[ShaderManager.BLOCK_PER_PASS][mvLoc], "gbufferModelView must route to the per-pass block");
        assertNull(prog.blockInfoBySlot[ShaderManager.BLOCK_PER_FRAME][mvLoc], "gbufferModelView must not also route to the per-frame block");

        assertEquals(0, prog.blockInfoBySlot[ShaderManager.BLOCK_PER_FRAME][prog.nameToLocation.getInt("cameraPosition")].offset());
        assertEquals(12, prog.blockInfoBySlot[ShaderManager.BLOCK_PER_FRAME][prog.nameToLocation.getInt("frameTimeCounter")].offset());
        assertEquals(0, prog.blockInfoBySlot[ShaderManager.BLOCK_PER_PASS][mvLoc].offset());
    }

    @Test
    void blockMembersNeverAlsoRouteToThePerProgramUbo() {
        final ShaderManager.ProgramObject prog = linkedProgram();
        for (String name : List.of("cameraPosition", "frameTimeCounter", "gbufferModelView")) {
            final int loc = prog.nameToLocation.getInt(name);
            assertNull(prog.vsInfoBySlot[loc], name + " must not also be a VS UBO member");
            assertNull(prog.fsInfoBySlot[loc], name + " must not also be an FS UBO member");
        }
    }

    @Test
    void nonMemberUniformStaysOnTheUboPath() {
        final ShaderManager.ProgramObject prog = linkedProgram();
        final int loc = prog.nameToLocation.getInt("packOwned");
        assertTrue(loc >= 0);
        assertNull(prog.blockInfoBySlot[ShaderManager.BLOCK_PER_FRAME][loc], "packOwned is not a block member");
        assertNull(prog.blockInfoBySlot[ShaderManager.BLOCK_PER_PASS][loc], "packOwned is not a block member");
        assertNotNull(prog.fsInfoBySlot[loc], "packOwned must remain a normal FS UBO member");
    }

    @Test
    void blockSizesAndBindingsAreRecorded() {
        final ShaderManager.ProgramObject prog = linkedProgram();
        assertEquals(16, prog.blockSize[ShaderManager.BLOCK_PER_FRAME], "vec3@0 + float@12");
        assertEquals(64, prog.blockSize[ShaderManager.BLOCK_PER_PASS], "one mat4");
        assertEquals(ShaderManager.PER_FRAME_BLOCK_BINDING, prog.blockBinding[ShaderManager.BLOCK_PER_FRAME]);
        assertEquals(ShaderManager.PER_PASS_BLOCK_BINDING, prog.blockBinding[ShaderManager.BLOCK_PER_PASS]);
    }
}
