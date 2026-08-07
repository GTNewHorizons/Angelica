package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.glsm.shader.SpirvCompiler;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.shaderc.Shaderc;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.system.MemoryUtil.memFree;
class PerFrameBlockReflectionTest {

    private static final String BLOCK_GLSL = """
        layout(std140) buffer AngelicaPerFrame {
            vec3 cameraPosition;
            float frameTimeCounter;
            mat4 gbufferModelView;
            vec4 fogColor;
            float rainStrength;
        };
        """;

    private static String shaderUsing(String expr) {
        return "#version 330 core\n" + BLOCK_GLSL + "out vec4 fragColor;\nvoid main() { fragColor = " + expr + "; }\n";
    }

    @Test
    void reflectsStorageBlockMembersAtStd140Offsets() {
        final Map<String, Integer> offsets = perFrameOffsetsOf(shaderUsing("vec4(cameraPosition, 1.0)"));

        assertEquals(0, offsets.get("cameraPosition"));
        assertEquals(12, offsets.get("frameTimeCounter"));
        assertEquals(16, offsets.get("gbufferModelView"));
        assertEquals(80, offsets.get("fogColor"));
        assertEquals(96, offsets.get("rainStrength"));
    }


    @Test
    void offsetsAreIdenticalAcrossProgramsReferencingDifferentMembers() {
        assertEquals(perFrameOffsetsOf(shaderUsing("vec4(cameraPosition, 1.0)")), perFrameOffsetsOf(shaderUsing("vec4(rainStrength)")), "a shader touching only the last member must still see every member at the same offset");
    }

    @Test
    void ignoresUnrelatedStorageBlocks() {
        final String src = """
            #version 430 core
            layout(std430) buffer PackOwnedBuffer { vec4 packData[4]; };
            out vec4 fragColor;
            void main() { fragColor = packData[0]; }
            """;
        final ShaderManager.StageReflection refl = reflect(src);
        for (int b = 0; b < ShaderManager.BLOCK_COUNT; b++) {
            assertTrue(refl.blocks()[b].members().isEmpty());
            assertEquals(-1, refl.blocks()[b].binding());
            assertEquals(0, refl.blocks()[b].size());
        }
    }

    @Test
    void reportsBlockSizeAndBinding() {
        final ShaderManager.BlockReflection block = reflect(shaderUsing("vec4(rainStrength)")).blocks()[ShaderManager.BLOCK_PER_FRAME];
        assertEquals(100, block.size());
        assertTrue(block.binding() >= 0);
    }

    @Test
    void reflectsThePerPassBlockIndependently() {
        final String src = """
            #version 330 core
            layout(std140) buffer AngelicaPerPass {
                mat4 gbufferModelView;
                vec3 fogColor;
            };
            out vec4 fragColor;
            void main() { fragColor = gbufferModelView * vec4(fogColor, 1.0); }
            """;
        final ShaderManager.StageReflection refl = reflect(src);
        final ShaderManager.BlockReflection perPass = refl.blocks()[ShaderManager.BLOCK_PER_PASS];
        assertEquals(0, refl.blocks()[ShaderManager.BLOCK_PER_FRAME].size(), "per-frame block absent");
        assertEquals(2, perPass.members().size());
        assertEquals(0, perPass.members().get(0).offset());
        assertEquals(64, perPass.members().get(1).offset());
    }

    private static Map<String, Integer> perFrameOffsetsOf(String source) {
        final Map<String, Integer> out = new LinkedHashMap<>();
        for (ShaderManager.UboMember m : reflect(source).blocks()[ShaderManager.BLOCK_PER_FRAME].members()) out.put(m.name(), m.offset());
        return out;
    }

    private static ShaderManager.StageReflection reflect(String source) {
        final SpirvCompiler.Result r = SpirvCompiler.compile(source, Shaderc.shaderc_fragment_shader, "test.frag", SpirvCompiler.Options.vulkanForced460Core());
        assertNotNull(r.spirv(), () -> "shaderc failed: " + r.error());
        final ByteBuffer spirv = r.spirv();
        try {
            return ShaderManager.reflectStage(spirv, false);
        } finally {
            memFree(spirv);
        }
    }
}
