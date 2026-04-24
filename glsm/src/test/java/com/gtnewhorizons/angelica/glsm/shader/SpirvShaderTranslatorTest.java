package com.gtnewhorizons.angelica.glsm.shader;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL32;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure unit tests for {@link SpirvShaderTranslator}. shaderc + spvc are headless natives. */
class SpirvShaderTranslatorTest {

    @Test
    void selectionBoxVs_producesNamedLooseUniforms() {
        final String src = """
            #version 330 core
            uniform mat4 u_MVP;
            uniform vec3 u_Min;
            uniform vec3 u_Max;
            const vec3 edges[24] = vec3[24](
                vec3(0,0,0), vec3(1,0,0),  vec3(1,0,0), vec3(1,0,1),
                vec3(1,0,1), vec3(0,0,1),  vec3(0,0,1), vec3(0,0,0),
                vec3(0,1,0), vec3(1,1,0),  vec3(1,1,0), vec3(1,1,1),
                vec3(1,1,1), vec3(0,1,1),  vec3(0,1,1), vec3(0,1,0),
                vec3(0,0,0), vec3(0,1,0),  vec3(1,0,0), vec3(1,1,0),
                vec3(1,0,1), vec3(1,1,1),  vec3(0,0,1), vec3(0,1,1)
            );
            void main() {
                vec3 pos = mix(u_Min, u_Max, edges[gl_VertexID]);
                gl_Position = u_MVP * vec4(pos, 1.0);
            }
            """;

        final String es = SpirvShaderTranslator.glslToGlslEs(src, GL20.GL_VERTEX_SHADER, "selection_box.vert");
        dumpOnFailure("selection_box.vert", src, es);

        assertNotNull(es);
        assertTrue(es.startsWith("#version 320 es"));
        assertContainsDecl(es, "mat4", "u_MVP");
        assertContainsDecl(es, "vec3", "u_Min");
        assertContainsDecl(es, "vec3", "u_Max");
        assertFalse(es.contains("_RESERVED_IDENTIFIER_FIXUP"), "uniforms must not be wrapped in spvc's default-block struct");
    }

    @Test
    void selectionBoxFs_producesNamedUniformAndLocationedFragOutput() {
        final String src = """
            #version 330 core
            uniform vec4 u_Color;
            out vec4 fragColor;
            void main() {
                fragColor = u_Color;
            }
            """;

        final String es = SpirvShaderTranslator.glslToGlslEs(src, GL20.GL_FRAGMENT_SHADER, "selection_box.frag");
        dumpOnFailure("selection_box.frag", src, es);

        assertNotNull(es);
        assertContainsDecl(es, "vec4", "u_Color");
        assertTrue(es.contains("layout(location = 0)") || es.contains("layout(location=0)"), "fragment output must have layout(location=0)");
    }

    // Minimal repro of celeritas chunk VS constructs Mesa GLES rejects: uvec/vec mix, bit shifts.
    @Test
    void celeritasChunk_impliedUvecVecPromotion_becomesExplicit() {
        final String src = """
            #version 330 core
            uniform mat4 u_ModelViewProjectionMatrix;
            uvec3 unpack(uint p) {
                return uvec3(p) >> uvec3(5u,0u,2u) & uvec3(7u,3u,7u);
            }
            vec3 translation(uint p) {
                return unpack(p) * vec3(16.0);
            }
            void main() {
                gl_Position = u_ModelViewProjectionMatrix * vec4(translation(0u), 1.0);
            }
            """;

        final String es = SpirvShaderTranslator.glslToGlslEs(src, GL20.GL_VERTEX_SHADER, "chunk.vert");
        dumpOnFailure("chunk.vert", src, es);

        assertNotNull(es);
        assertFalse(es.contains("unpack(p) * vec3"), "uvec3 * vec3 must be made explicit by the SPIR-V round-trip");
    }

    // ES forbids non-const global initializers; round-trip must hoist or inline into main().
    @Test
    void globalInitReferencingUniforms_survivesRoundTrip() {
        final String src = """
            #version 330 core
            struct Fog { vec4 color; float density; };
            uniform vec4 fogColor;
            uniform float fogDensity;
            Fog fog = Fog(fogColor, fogDensity);
            void main() {
                gl_Position = vec4(fog.density) + fog.color;
            }
            """;

        final String es = SpirvShaderTranslator.glslToGlslEs(src, GL20.GL_VERTEX_SHADER, "botania_fog.vert");
        dumpOnFailure("botania_fog.vert", src, es);

        assertNotNull(es);
        assertFalse(es.contains("Fog fog = Fog(fogColor, fogDensity);"), "global init must be hoisted / inlined by the SPIR-V round-trip");
    }

    // SPIR-V has no uniform bool; round-trip yields `uniform uint X` which `glUniform1i` rejects.
    @Test
    void uniformBool_roundTripsAsBool_notUint() {
        final String src = """
            #version 330 core
            uniform bool u_FogEnabled;
            in vec4 color;
            out vec4 fragColor;
            void main() {
                vec4 c = color;
                if (u_FogEnabled) {
                    c.a *= 0.5;
                }
                fragColor = c;
            }
            """;

        final String es = SpirvShaderTranslator.glslToGlslEs(src, GL20.GL_FRAGMENT_SHADER, "cloud.frag");
        dumpOnFailure("cloud.frag (bool uniform)", src, es);

        assertNotNull(es);
        assertContainsDecl(es, "bool", "u_FogEnabled");
        assertFalse(es.contains("uniform uint u_FogEnabled") || es.contains("uniform highp uint u_FogEnabled") || es.contains("uniform mediump uint u_FogEnabled"),
                "emitted uniform must not be `uint` - `glUniform1i` only accepts int/bool uniforms");
    }

    // Iris injects common uniforms with explicit initializers: `uniform bool isRightHanded = true;`.
    // Round-trip must still restore the bool type (not leave `uniform uint isRightHanded;`), which
    // would otherwise trip glUniform1i with GL_INVALID_OPERATION.
    @Test
    void uniformBoolWithInitializer_roundTripsAsBool() {
        final String src = """
            #version 330 core
            uniform bool isRightHanded = true;
            uniform bool heavyFog = false;
            uniform bool firstPersonCamera = true;
            in vec4 color;
            out vec4 fragColor;
            void main() {
                float alpha = 1.0;
                if (isRightHanded) alpha *= 0.9;
                if (heavyFog) alpha *= 0.8;
                if (firstPersonCamera) alpha *= 0.95;
                fragColor = vec4(color.rgb, color.a * alpha);
            }
            """;

        final String es = SpirvShaderTranslator.glslToGlslEs(src, GL20.GL_FRAGMENT_SHADER, "iris_common_bool_init.frag");
        dumpOnFailure("iris_common_bool_init.frag", src, es);

        assertNotNull(es);
        assertContainsDecl(es, "bool", "isRightHanded");
        assertContainsDecl(es, "bool", "heavyFog");
        assertContainsDecl(es, "bool", "firstPersonCamera");
        assertFalse(es.contains("uniform uint isRightHanded") || es.contains("uniform highp uint isRightHanded") || es.contains("uniform mediump uint isRightHanded"),
                "isRightHanded must not be emitted as uint\n\n" + es);
    }

    // Varyings must have no layout(location=...) - GL links by name. Attributes + FS outputs keep theirs.
    @Test
    void varyingLocations_strippedFromVsOutAndFsIn_keptOnAttributesAndMrtOutputs() {
        final String vs = """
            #version 330 core
            layout(location = 0) in vec3 a_Position;
            layout(location = 1) in vec4 a_Color;
            uniform mat4 u_MVP;
            out vec4 v_Color;
            out vec2 v_TexCoord;
            void main() {
                v_Color = a_Color;
                v_TexCoord = a_Position.xy;
                gl_Position = u_MVP * vec4(a_Position, 1.0);
            }
            """;
        final String fs = """
            #version 330 core
            in vec2 v_TexCoord;
            in vec4 v_Color;
            uniform sampler2D u_Tex;
            out vec4 fragColor;
            void main() {
                fragColor = v_Color * texture(u_Tex, v_TexCoord);
            }
            """;

        final String esVs = SpirvShaderTranslator.glslToGlslEs(vs, GL20.GL_VERTEX_SHADER, "strip.vert");
        final String esFs = SpirvShaderTranslator.glslToGlslEs(fs, GL20.GL_FRAGMENT_SHADER, "strip.frag");
        dumpOnFailure("strip.vert", vs, esVs);
        dumpOnFailure("strip.frag", fs, esFs);

        assertNotNull(esVs);
        assertNotNull(esFs);

        assertFalse(hasLayoutLocation(esVs, "v_Color"));
        assertFalse(hasLayoutLocation(esVs, "v_TexCoord"));
        assertFalse(hasLayoutLocation(esFs, "v_Color"));
        assertFalse(hasLayoutLocation(esFs, "v_TexCoord"));
        assertTrue(hasLayoutLocation(esVs, "a_Position"));
        assertTrue(hasLayoutLocation(esVs, "a_Color"));
        assertTrue(hasLayoutLocation(esFs, "fragColor"));
    }

    // GS varyings must have no layout(location=...) on either side - a VS->GS->FS pipeline would
    // otherwise have VS-out stripped but GS-in retained, which trips the GLES linker.
    @Test
    void varyingLocations_strippedFromGeometryShaderInAndOut() {
        final String gs = """
            #version 330 core
            layout(triangles) in;
            layout(triangle_strip, max_vertices = 3) out;
            in vec4 v_Color[];
            in vec2 v_TexCoord[];
            out vec4 g_Color;
            out vec2 g_TexCoord;
            void main() {
                for (int i = 0; i < 3; i++) {
                    gl_Position = gl_in[i].gl_Position;
                    g_Color = v_Color[i];
                    g_TexCoord = v_TexCoord[i];
                    EmitVertex();
                }
                EndPrimitive();
            }
            """;

        final String esGs = SpirvShaderTranslator.glslToGlslEs(gs, GL32.GL_GEOMETRY_SHADER, "passthrough.geom");
        dumpOnFailure("passthrough.geom", gs, esGs);

        assertNotNull(esGs);
        assertFalse(hasLayoutLocation(esGs, "v_Color"));
        assertFalse(hasLayoutLocation(esGs, "v_TexCoord"));
        assertFalse(hasLayoutLocation(esGs, "g_Color"));
        assertFalse(hasLayoutLocation(esGs, "g_TexCoord"));
    }

    // Iris terrain VS: explicit iris_* layouts at 0..4 must survive round-trip; mc_Entity,
    // mc_midTexCoord, at_midBlock declared as plain `in vec4 name;` must have their layouts
    // STRIPPED so Iris's glBindAttribLocation calls take effect at GL link time.
    @Test
    void vsInputs_preserveExplicitLayouts_stripAutoAssignedOnes() {
        final String vs = """
            #version 330 core
            layout(location = 0) in vec4 iris_Vertex;
            layout(location = 1) in vec4 iris_Color;
            layout(location = 2) in vec4 iris_MultiTexCoord0;
            layout(location = 3) in vec4 iris_MultiTexCoord1;
            layout(location = 4) in vec3 iris_Normal;
            in vec4 mc_Entity;
            in vec4 mc_midTexCoord;
            in vec4 at_midBlock;
            uniform mat4 iris_MVP;
            out vec4 v_Color;
            void main() {
                v_Color = iris_Color + mc_Entity + at_midBlock + mc_midTexCoord;
                gl_Position = iris_MVP * iris_Vertex;
            }
            """;

        final String es = SpirvShaderTranslator.glslToGlslEs(vs, GL20.GL_VERTEX_SHADER, "gbuffers_terrain.vert");
        dumpOnFailure("gbuffers_terrain.vert", vs, es);

        assertNotNull(es);
        // Explicit iris_* layouts preserved.
        assertTrue(hasLayoutLocation(es, "iris_Vertex"), "iris_Vertex must keep its explicit layout");
        assertTrue(hasLayoutLocation(es, "iris_Color"), "iris_Color must keep its explicit layout");
        assertTrue(hasLayoutLocation(es, "iris_MultiTexCoord0"), "iris_MultiTexCoord0 must keep its explicit layout");
        assertTrue(hasLayoutLocation(es, "iris_MultiTexCoord1"), "iris_MultiTexCoord1 must keep its explicit layout");
        assertTrue(hasLayoutLocation(es, "iris_Normal"), "iris_Normal must keep its explicit layout");
        // Non-explicit inputs must have NO layout(location=N) - rely on glBindAttribLocation.
        assertFalse(hasLayoutLocation(es, "mc_Entity"), "mc_Entity layout must be stripped");
        assertFalse(hasLayoutLocation(es, "mc_midTexCoord"), "mc_midTexCoord layout must be stripped");
        assertFalse(hasLayoutLocation(es, "at_midBlock"), "at_midBlock layout must be stripped");
    }

    private static boolean hasLayoutLocation(String source, String name) {
        final Pattern p = Pattern.compile(
                "layout\\s*\\(\\s*location\\s*=\\s*\\d+\\s*\\)[^;]*?\\b"
                        + Pattern.quote(name) + "\\s*;");
        return p.matcher(source).find();
    }

    // fontFilter.fsh declares `uniform sampler2D sampler` - Vulkan reserves `sampler` as a keyword.
    @Test
    void vulkanReservedSamplerAsIdentifier_isRenamedBeforeShaderc() {
        final String src = """
            #version 330 core
            uniform sampler2D sampler;
            in vec2 texCoord;
            out vec4 fragColor;
            void main() {
                fragColor = texture(sampler, texCoord);
            }
            """;

        final String es = SpirvShaderTranslator.glslToGlslEs(src, GL20.GL_FRAGMENT_SHADER, "fontFilter.frag");
        dumpOnFailure("fontFilter.frag", src, es);

        assertNotNull(es);
        assertTrue(es.contains("angelica_sampler_renamed"));
        assertTrue(es.contains("sampler2D"), "sampler2D type must not be touched by the rename");
    }

    @Test
    void uniformBool_multiDeclarator_allRestored() {
        final String src = """
            #version 330 core
            uniform bool a, b, c;
            in vec4 color;
            out vec4 fragColor;
            void main() {
                float alpha = 1.0;
                if (a) alpha *= 0.9;
                if (b) alpha *= 0.8;
                if (c) alpha *= 0.7;
                fragColor = vec4(color.rgb, color.a * alpha);
            }
            """;
        final String es = SpirvShaderTranslator.glslToGlslEs(src, GL20.GL_FRAGMENT_SHADER, "multi_bool.frag");
        dumpOnFailure("multi_bool.frag", src, es);

        assertNotNull(es);
        assertContainsDecl(es, "bool", "a");
        assertContainsDecl(es, "bool", "b");
        assertContainsDecl(es, "bool", "c");
        assertFalse(es.contains("uniform uint") || es.contains("uniform highp uint") || es.contains("uniform mediump uint"),
                "no bool uniform should leak through as uint");
    }

    @Test
    void uniformBool_compoundExpression_bothBranchesRewritten() {
        final String src = """
            #version 330 core
            uniform bool a;
            uniform bool b;
            in vec4 color;
            out vec4 fragColor;
            void main() {
                vec4 c = color;
                if (a && b) { c.a *= 0.5; }
                fragColor = c;
            }
            """;
        final String es = SpirvShaderTranslator.glslToGlslEs(src, GL20.GL_FRAGMENT_SHADER, "compound_bool.frag");
        dumpOnFailure("compound_bool.frag", src, es);

        assertNotNull(es);
        assertFalse(es.contains("!= 0u"), "no bool use site should still compare against 0u");
        assertFalse(es.contains("uniform uint"), "bool uniforms must not leak as uint");
    }

    @Test
    void uniformBool_throughDefaultBlock_restoresWithoutLeakingMemberSyntax() {
        final String src = """
            #version 330 core
            uniform bool u_Enabled;
            uniform float u_Scale;
            in vec4 color;
            out vec4 fragColor;
            void main() {
                vec4 c = color;
                if (u_Enabled) c.rgb *= u_Scale;
                fragColor = c;
            }
            """;
        final String es = SpirvShaderTranslator.glslToGlslEs(src, GL20.GL_FRAGMENT_SHADER, "bool_through_block.frag");
        dumpOnFailure("bool_through_block.frag", src, es);

        assertNotNull(es);
        assertContainsDecl(es, "bool", "u_Enabled");
        assertFalse(es.contains("uniform uint") || es.contains("uniform highp uint") || es.contains("uniform mediump uint"),
                "bool uniform must be restored, not left as uint");
        assertFalse(es.contains("_RESERVED_IDENTIFIER_FIXUP_"), "default block struct must be unwrapped");
        // The original field-access form must not survive anywhere in the output.
        assertFalse(es.matches("(?s).*_[0-9]+\\.u_Enabled.*"), "no _NN.u_Enabled member access should leak into the emitted source");
        assertFalse(es.contains("!= 0u"), "no bool use-site should still be comparing against 0u");
    }

    private static void assertContainsDecl(String source, String type, String name) {
        final String needle1 = "uniform " + type + " " + name;
        final String needle2 = "uniform highp " + type + " " + name;
        final String needle3 = "uniform mediump " + type + " " + name;
        assertTrue(
                source.contains(needle1) || source.contains(needle2) || source.contains(needle3),
                "expected top-level `uniform " + type + " " + name + "` in emitted GLSL ES");
    }

    private static void dumpOnFailure(String name, String src, String es) {
        System.err.println("=== " + name + " ===");
        System.err.println("--- source ---");
        System.err.println(src);
        System.err.println("--- emitted ---");
        System.err.println(es == null ? "(null - translator failed)" : es);
        System.err.println("================");
    }
}
