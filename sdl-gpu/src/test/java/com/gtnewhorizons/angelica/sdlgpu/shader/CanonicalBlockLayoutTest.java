package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.glsm.shader.SpirvCompiler;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

class CanonicalBlockLayoutTest {

    private static final String BLOCK_GLSL = """
        layout(std140) uniform IrisPerFrame {
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
    void unusedMembersKeepTheirOffsetsAcrossPrograms() {
        final Map<String, Integer> usesFirst = offsetsOf(shaderUsing("vec4(cameraPosition, 1.0)"));
        final Map<String, Integer> usesLast = offsetsOf(shaderUsing("vec4(rainStrength)"));

        assertEquals(0, usesFirst.get("cameraPosition"));
        assertEquals(12, usesFirst.get("frameTimeCounter"));
        assertEquals(16, usesFirst.get("gbufferModelView"));
        assertEquals(80, usesFirst.get("fogColor"));
        assertEquals(96, usesFirst.get("rainStrength"));

        assertEquals(usesFirst, usesLast, "a shader referencing only the last member must still see every member at the same offset; without this one shared per-frame buffer cannot serve all programs");
    }

    @Test
    void looseUniformOffsetsDependOnEachShadersDeclarationSet() {
        final String withoutLeadingDecl = """
            #version 330 core
            uniform vec3 cameraPosition;
            out vec4 fragColor;
            void main() { fragColor = vec4(cameraPosition, 1.0); }
            """;
        final String withLeadingDecl = """
            #version 330 core
            uniform mat4 gbufferModelView;
            uniform vec3 cameraPosition;
            out vec4 fragColor;
            void main() { fragColor = gbufferModelView * vec4(cameraPosition, 1.0); }
            """;

        final Integer a = offsetsOf(withoutLeadingDecl).get("cameraPosition");
        final Integer b = offsetsOf(withLeadingDecl).get("cameraPosition");

        assertNotNull(a);
        assertNotNull(b);
        assertNotEquals(a, b, "if loose-uniform offsets were stable across declaration sets, no canonical block would be needed");
    }

    private static Map<String, Integer> offsetsOf(String source) {
        final ByteBuffer spirv = compile(source);
        try {
            final ShaderManager.StageReflection refl = ShaderManager.reflectStage(spirv, false);
            final Map<String, Integer> out = new LinkedHashMap<>();
            for (ShaderManager.UboMember m : refl.uboMembers()) out.put(m.name(), m.offset());
            return out;
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }

    private static ByteBuffer compile(String source) {
        final SpirvCompiler.Result r = SpirvCompiler.compile(source, Shaderc.shaderc_fragment_shader, "test.frag", SpirvCompiler.Options.vulkanRelaxed());
        if (r.spirv() == null) {
            fail("shaderc failed: " + r.error());
        }
        return r.spirv();
    }
}
