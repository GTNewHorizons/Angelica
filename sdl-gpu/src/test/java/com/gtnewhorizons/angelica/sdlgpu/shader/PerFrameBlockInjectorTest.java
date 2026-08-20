package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.glsm.shader.SpirvCompiler;
import com.gtnewhorizons.angelica.glsm.hooks.PerFrameUniformBlock;
import com.gtnewhorizons.angelica.glsm.shader.UniformType;
import com.gtnewhorizons.angelica.glsm.hooks.PerFrameUniformBlock.Member;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.shaderc.Shaderc;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.system.MemoryUtil.memFree;

class PerFrameBlockInjectorTest {

    private static final PerFrameUniformBlock BLOCK = new PerFrameUniformBlock(List.of(
        new Member("cameraPosition", UniformType.VEC3),
        new Member("frameTimeCounter", UniformType.FLOAT),
        new Member("rainStrength", UniformType.FLOAT)));

    @Test
    void dropsLooseDeclarationsAndInjectsTheBlock() {
        final String out = PerFrameBlockInjector.inject("""
            #version 330 core
            uniform vec3 cameraPosition;
            uniform float rainStrength;
            out vec4 fragColor;
            void main() { fragColor = vec4(cameraPosition, rainStrength); }
            """, BLOCK, null);

        assertTrue(out.contains("buffer AngelicaPerFrame"));
        assertFalse(out.contains("uniform vec3 cameraPosition"));
        assertFalse(out.contains("uniform float rainStrength"));
        assertTrue(out.contains("vec4(cameraPosition, rainStrength)"), "use sites must be untouched");
    }

    @Test
    void declaresEveryMemberEvenWhenUnused() {
        final String out = PerFrameBlockInjector.inject("""
            #version 330 core
            uniform float rainStrength;
            out vec4 fragColor;
            void main() { fragColor = vec4(rainStrength); }
            """, BLOCK, null);

        for (Member m : BLOCK.members()) {
            assertTrue(out.contains(PerFrameBlockInjector.std140Type(m.type()) + " " + m.name() + ";"), "missing member " + m.name());
        }
    }

    @Test
    void blockGoesAfterTheVersionDirective() {
        final String out = PerFrameBlockInjector.inject("""
            #version 330 core
            uniform float rainStrength;
            void main() {}
            """, BLOCK, null);
        assertTrue(out.indexOf("#version") < out.indexOf("buffer AngelicaPerFrame"));
    }

    @Test
    void leavesMixedDeclarationAlone() {
        final String out = PerFrameBlockInjector.inject("""
            #version 330 core
            uniform float rainStrength, packOwnedValue;
            void main() {}
            """, BLOCK, null);
        assertTrue(out.contains("uniform float rainStrength, packOwnedValue;"));
    }

    @Test
    void emptyMemberListIsANoOp() {
        final String src = "#version 330 core\nvoid main() {}\n";
        assertEquals(src, PerFrameBlockInjector.inject(src, new PerFrameUniformBlock(List.of()), null));
    }

    @Test
    void memberShadowedByAPackDeclarationIsRenamedNotRedeclared() {
        final String out = PerFrameBlockInjector.inject("""
            #version 330 core
            const float rainStrength = 3.14159265359;
            out vec4 fragColor;
            void main() { fragColor = vec4(cameraPosition, rainStrength); }
            """, BLOCK, null);

        assertFalse(out.contains("    float rainStrength;"), "would redefine the shader's own declaration");
        assertTrue(out.contains("angelica_pfb_unused_rainStrength"), "shadowed member must be renamed, not dropped");
        assertTrue(out.contains("const float rainStrength = 3.14159265359;"), "the pack's declaration must survive");

        final SpirvCompiler.Result r = SpirvCompiler.compile(out, Shaderc.shaderc_fragment_shader, "shadowed.frag", SpirvCompiler.Options.vulkanForced460Core());
        assertNotNull(r.spirv(), () -> "shadowed source failed to compile: " + r.error());
        memFree(r.spirv());
    }

    @Test
    void functionLocalRedeclarationDoesNotShadowTheMember() {
        final String out = PerFrameBlockInjector.inject("""
            #version 330 core
            uniform vec3 cameraPosition;
            vec3 ambient = pow(cameraPosition, vec3(0.75));
            out vec4 fragColor;
            void main() {
                vec4 cameraPosition = vec4(0);
                fragColor = vec4(ambient, 1.0) + cameraPosition;
            }
            """, BLOCK, null);

        assertFalse(out.contains("uniform vec3 cameraPosition;"));
        assertTrue(out.contains("    vec3 cameraPosition;"), "member must keep its real name");
        assertFalse(out.contains("angelica_pfb_unused_cameraPosition"));
        assertTrue(out.contains("vec4 cameraPosition = vec4(0);"), "the local declaration must survive");

        final SpirvCompiler.Result r = SpirvCompiler.compile(out, Shaderc.shaderc_fragment_shader, "localshadow.frag", SpirvCompiler.Options.vulkanForced460Core());
        assertNotNull(r.spirv(), () -> "locally-shadowed source failed to compile: " + r.error());
        memFree(r.spirv());
    }

    @Test
    void shadowingDoesNotChangeAnyOffset() {
        final String plain = PerFrameBlockInjector.inject("""
            #version 330 core
            out vec4 fragColor;
            void main() { fragColor = vec4(cameraPosition, rainStrength); }
            """, BLOCK, null);
        final String shadowed = PerFrameBlockInjector.inject("""
            #version 330 core
            const float rainStrength = 1.0;
            out vec4 fragColor;
            void main() { fragColor = vec4(cameraPosition, rainStrength); }
            """, BLOCK, null);

        assertEquals(offsetsOf(plain).values().stream().toList(), offsetsOf(shadowed).values().stream().toList(), "a renamed member must occupy the same slot as the name it replaced");
    }

    @Test
    void uniformDeclaredWithADifferentTypeIsLeftAlone() {
        final PerFrameUniformBlock block = new PerFrameUniformBlock(List.of(
            new Member("cameraPosition", UniformType.VEC3),
            new Member("isRightHanded", UniformType.INT)));

        final String out = PerFrameBlockInjector.inject("""
            #version 330 core
            uniform bool isRightHanded;
            out vec4 fragColor;
            void main() { fragColor = vec4(cameraPosition, (true ^^ isRightHanded) ? 1.0 : 0.0); }
            """, block, null);

        assertTrue(out.contains("uniform bool isRightHanded;"), "a type mismatch must stay on the push path");
        assertTrue(out.contains("angelica_pfb_unused_isRightHanded"), "its block slot must still be held");
        assertFalse(out.contains("    int isRightHanded;"));

        final SpirvCompiler.Result r = SpirvCompiler.compile(out, Shaderc.shaderc_fragment_shader, "booltype.frag", SpirvCompiler.Options.vulkanForced460Core());
        assertNotNull(r.spirv(), () -> "bool-typed uniform failed to compile: " + r.error());
        memFree(r.spirv());
    }

    @Test
    void uniformDeclaredWithTheMatchingTypeIsStillReplaced() {
        final String out = PerFrameBlockInjector.inject("""
            #version 330 core
            uniform float rainStrength;
            out vec4 fragColor;
            void main() { fragColor = vec4(rainStrength); }
            """, BLOCK, null);
        assertFalse(out.contains("uniform float rainStrength;"));
        assertTrue(out.contains("    float rainStrength;"));
    }

    @Test
    void shaderReferencingNoMemberIsUntouched() {
        final String src = """
            #version 330 core
            uniform vec4 packOwnedColor;
            out vec4 fragColor;
            void main() { fragColor = packOwnedColor; }
            """;
        assertEquals(src, PerFrameBlockInjector.inject(src, BLOCK, null));
    }

    @Test
    void oneReferenceStillDeclaresEveryMember() {
        final String out = PerFrameBlockInjector.inject("""
            #version 330 core
            out vec4 fragColor;
            void main() { fragColor = vec4(rainStrength); }
            """, BLOCK, null);
        for (Member m : BLOCK.members()) {
            assertTrue(out.contains(PerFrameBlockInjector.std140Type(m.type()) + " " + m.name() + ";"), "missing member " + m.name());
        }
    }

    private static final PerFrameUniformBlock PER_PASS = new PerFrameUniformBlock(List.of(
        new Member("gbufferModelView", UniformType.MAT4),
        new Member("fogColor", UniformType.VEC3)));

    @Test
    void bothBlocksInjectInOneParse() {
        final String out = PerFrameBlockInjector.inject("""
            #version 330 core
            uniform vec3 cameraPosition;
            uniform mat4 gbufferModelView;
            out vec4 fragColor;
            void main() { fragColor = gbufferModelView * vec4(cameraPosition, 1.0); }
            """, BLOCK, PER_PASS);

        assertTrue(out.contains("binding = " + ShaderManager.PER_FRAME_BLOCK_BINDING + ") readonly buffer AngelicaPerFrame"));
        assertTrue(out.contains("binding = " + ShaderManager.PER_PASS_BLOCK_BINDING + ") readonly buffer AngelicaPerPass"));
        assertFalse(out.contains("uniform vec3 cameraPosition"));
        assertFalse(out.contains("uniform mat4 gbufferModelView"));
        assertTrue(out.contains("    vec3 fogColor;"), "unused per-pass member still declared in its block");
    }

    @Test
    void unreferencedBlockIsNotInjected() {
        final String out = PerFrameBlockInjector.inject("""
            #version 330 core
            out vec4 fragColor;
            void main() { fragColor = vec4(cameraPosition, 1.0); }
            """, BLOCK, PER_PASS);

        assertTrue(out.contains("buffer AngelicaPerFrame"));
        assertFalse(out.contains("buffer AngelicaPerPass"), "no per-pass member referenced");
    }

    private static LinkedHashMap<String, Integer> offsetsOf(String source) {
        final SpirvCompiler.Result r = SpirvCompiler.compile(source, Shaderc.shaderc_fragment_shader, "off.frag", SpirvCompiler.Options.vulkanForced460Core());
        assertNotNull(r.spirv(), () -> "compile failed: " + r.error());
        final ByteBuffer spirv = r.spirv();
        try {
            final LinkedHashMap<String, Integer> out = new LinkedHashMap<>();
            for (ShaderManager.UboMember m : ShaderManager.reflectStage(spirv, false).blocks()[ShaderManager.BLOCK_PER_FRAME].members()) {
                out.put(m.name(), m.offset());
            }
            return out;
        } finally {
            memFree(spirv);
        }
    }

    @Test
    void injectedSourceCompilesAndReflects() {
        final String out = PerFrameBlockInjector.inject("""
            #version 330 core
            uniform vec3 cameraPosition;
            uniform float rainStrength;
            out vec4 fragColor;
            void main() { fragColor = vec4(cameraPosition, rainStrength); }
            """, BLOCK, null);

        final SpirvCompiler.Result r = SpirvCompiler.compile(out, Shaderc.shaderc_fragment_shader, "injected.frag", SpirvCompiler.Options.vulkanForced460Core());
        assertNotNull(r.spirv(), () -> "injected source failed to compile: " + r.error());

        final ByteBuffer spirv = r.spirv();
        try {
            final ShaderManager.StageReflection refl = ShaderManager.reflectStage(spirv, false);
            final List<ShaderManager.UboMember> members = refl.blocks()[ShaderManager.BLOCK_PER_FRAME].members();
            assertEquals(List.of("cameraPosition", "frameTimeCounter", "rainStrength"), members.stream().map(ShaderManager.UboMember::name).toList());
            assertEquals(0, members.get(0).offset());
            assertEquals(12, members.get(1).offset());
            assertEquals(16, members.get(2).offset());
        } finally {
            memFree(spirv);
        }
    }
}
