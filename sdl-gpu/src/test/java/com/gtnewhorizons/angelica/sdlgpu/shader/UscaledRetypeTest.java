package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.glsm.shader.SpirvCompiler;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.shaderc.Shaderc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UscaledRetypeTest {

    private static final String VS = """
        #version 330 core
        layout(location = 0) in vec2 a_TexCoord;
        layout(location = 1) in vec3 at_midBlock;
        out vec2 v_uv;
        void main() {
            v_uv = a_TexCoord * 0.5;
            gl_Position = vec4(at_midBlock, 1.0);
        }
        """;

    @Test
    void unsignedExactWidthRetypesAndConverts() {
        final String out = UscaledRetype.retype(VS, List.of(new UscaledRetype.Attrib("a_TexCoord", 0, 2, 2, false)));
        assertNotNull(out);
        assertTrue(out.contains("in uvec2 a_TexCoord_i"), out);
        assertTrue(out.contains("vec2 a_TexCoord = vec2(a_TexCoord_i);"), out);
        assertTrue(out.contains("v_uv = a_TexCoord * 0.5;"), out);
    }

    @Test
    void signedPromotedWidthSwizzlesDown() {
        final String out = UscaledRetype.retype(VS, List.of(new UscaledRetype.Attrib("at_midBlock", 0, 3, 4, true)));
        assertNotNull(out);
        assertTrue(out.contains("in ivec4 at_midBlock_i"), out);
        assertTrue(out.contains("vec3 at_midBlock = vec3(at_midBlock_i.xyz);"), out);
    }

    @Test
    void multipleAttributesConvertTogether() {
        final String out = UscaledRetype.retype(VS, List.of(
            new UscaledRetype.Attrib("a_TexCoord", 0, 2, 2, false),
            new UscaledRetype.Attrib("at_midBlock", 0, 3, 4, true)));
        assertNotNull(out);
        assertTrue(out.contains("vec2 a_TexCoord = vec2(a_TexCoord_i);"), out);
        assertTrue(out.contains("vec3 at_midBlock = vec3(at_midBlock_i.xyz);"), out);
    }

    @Test
    void emptyRequestIsIdentity() {
        assertEquals(VS, UscaledRetype.retype(VS, List.of()));
    }

    @Test
    void missingAttributeFails() {
        assertNull(UscaledRetype.retype(VS, List.of(new UscaledRetype.Attrib("not_present", 0, 2, 2, false))));
    }

    @Test
    void narrowerBoundWidthPadsWithGlAttributeDefaults() {
        final String out = UscaledRetype.retype(VS, List.of(new UscaledRetype.Attrib("at_midBlock", 0, 3, 2, true)));
        assertNotNull(out);
        assertTrue(out.contains("in ivec2 at_midBlock_i"), out);
        assertTrue(out.contains("vec3 at_midBlock = vec3(vec2(at_midBlock_i), 0.0);"), out);
    }

    @Test
    void vec4FromShort2PadsZeroThenOne() {
        final String src = """
            #version 330 core
            layout(location = 3) in vec4 iris_MultiTexCoord1;
            void main() { gl_Position = iris_MultiTexCoord1; }
            """;
        final String out = UscaledRetype.retype(src, List.of(new UscaledRetype.Attrib("iris_MultiTexCoord1", 0, 4, 2, true)));
        assertNotNull(out);
        assertTrue(out.contains("vec4 iris_MultiTexCoord1 = vec4(vec2(iris_MultiTexCoord1_i), 0.0, 1.0);"), out);
    }

    @Test
    void shadowIsInitializedBeforeAConsumingGlobalInitializer() {
        final String src = """
            #version 330 core
            in vec2 mc_midTexCoord;
            vec4 iris_MidTex = vec4(mc_midTexCoord.xy * 3.0517578E-5, 0.0, 1.0);
            void main() { gl_Position = iris_MidTex; }
            """;
        final String out = UscaledRetype.retype(src, List.of(new UscaledRetype.Attrib("mc_midTexCoord", 0, 2, 2, false)));
        assertNotNull(out);
        assertTrue(out.indexOf("vec2 mc_midTexCoord = vec2(mc_midTexCoord_i);") < out.indexOf("vec4 iris_MidTex ="), "the shadow must initialize before any global initializer that reads it\n\n" + out);
    }

    @Test
    void uniformOfSameNameIsNotRetyped() {
        final String src = """
            #version 330 core
            uniform vec2 a_TexCoord;
            void main() { gl_Position = vec4(a_TexCoord, 0.0, 1.0); }
            """;
        assertNull(UscaledRetype.retype(src, List.of(new UscaledRetype.Attrib("a_TexCoord", 0, 2, 2, false))));
    }

    @Test
    void retypesADeclarationGuardedByIfdefAfterPreprocessing() {
        final String src = """
            #version 460 core
            #define USE_VERTEX_COMPRESSION
            #ifdef USE_VERTEX_COMPRESSION
            in uvec4 a_PosId;
            in vec2 a_TexCoord;
            #else
            in vec3 a_PosId;
            in vec2 a_TexCoord;
            #endif
            out vec2 v_uv;
            void main() {
                v_uv = a_TexCoord * 3.0517578E-5;
                gl_Position = vec4(vec3(a_PosId.xyz), 1.0);
            }
            """;
        assertNull(UscaledRetype.retype(src, List.of(new UscaledRetype.Attrib("a_TexCoord", 0, 2, 2, false))), "the raw AST cannot see inside #ifdef; this is why the variant path preprocesses first");

        final String pre = SpirvCompiler.preprocess(src, Shaderc.shaderc_vertex_shader, "test", SpirvCompiler.Options.vulkanForced460Core());
        assertNotNull(pre, "shaderc preprocessing must succeed");
        final String out = UscaledRetype.retype(pre, List.of(new UscaledRetype.Attrib("a_TexCoord", 0, 2, 2, false)));
        assertNotNull(out, "preprocessed source must be parseable and retypeable\n\n" + pre);
        assertTrue(out.contains("in uvec2 a_TexCoord_i"), out);
        assertTrue(out.contains("vec2 a_TexCoord = vec2(a_TexCoord_i);"), out);
    }

    @Test
    void retypedInputKeepsTheNameSuffixTheLocationFixupKeysOn() {
        final String out = UscaledRetype.retype(VS, List.of(new UscaledRetype.Attrib("a_TexCoord", 4, 2, 2, false)));
        assertNotNull(out);
        assertTrue(out.contains("a_TexCoord" + UscaledRetype.SUFFIX), out);
    }
}
