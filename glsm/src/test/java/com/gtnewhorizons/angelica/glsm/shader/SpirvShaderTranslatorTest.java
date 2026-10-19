package com.gtnewhorizons.angelica.glsm.shader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL32;

import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpirvShaderTranslatorTest {

    static Stream<Arguments> translates() {
        return Stream.of(
            Arguments.of("selection box VS produces named loose uniforms", GL20.GL_VERTEX_SHADER, "selection_box.vert", """
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
                """,
                new String[] { "mat4 u_MVP", "vec3 u_Min", "vec3 u_Max" }, new String[0], new String[] { "_RESERVED_IDENTIFIER_FIXUP" }),
            Arguments.of("selection box FS produces a named uniform and a locationed frag output", GL20.GL_FRAGMENT_SHADER, "selection_box.frag", """
                #version 330 core
                uniform vec4 u_Color;
                out vec4 fragColor;
                void main() {
                    fragColor = u_Color;
                }
                """,
                new String[] { "vec4 u_Color" }, new String[] { "layout(location = 0)" }, new String[0]),
            Arguments.of("uvec3 * vec3 must be made explicit by the SPIR-V round-trip", GL20.GL_VERTEX_SHADER, "chunk.vert", """
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
                """,
                new String[0], new String[0], new String[] { "unpack(p) * vec3" }),
            Arguments.of("global init must be hoisted or inlined by the SPIR-V round-trip", GL20.GL_VERTEX_SHADER, "botania_fog.vert", """
                #version 330 core
                struct Fog { vec4 color; float density; };
                uniform vec4 fogColor;
                uniform float fogDensity;
                Fog fog = Fog(fogColor, fogDensity);
                void main() {
                    gl_Position = vec4(fog.density) + fog.color;
                }
                """,
                new String[0], new String[0], new String[] { "Fog fog = Fog(fogColor, fogDensity);" }),
            Arguments.of("uniform bool round-trips as bool, not uint", GL20.GL_FRAGMENT_SHADER, "cloud.frag", """
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
                """,
                new String[] { "bool u_FogEnabled" }, new String[0], new String[] { "uniform uint u_FogEnabled", "uniform highp uint u_FogEnabled", "uniform mediump uint u_FogEnabled" }),
            Arguments.of("redefined builtin translates", GL20.GL_FRAGMENT_SHADER, "redefines_clamp.frag", SpirvCompilerDiskCacheTest.REDEFINES_CLAMP,
                new String[0], new String[0], new String[0]),
            Arguments.of("uniform bool with initializer round-trips as bool", GL20.GL_FRAGMENT_SHADER, "iris_common_bool_init.frag", """
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
                """,
                new String[] { "bool isRightHanded", "bool heavyFog", "bool firstPersonCamera" }, new String[0], new String[] { "uniform uint isRightHanded", "uniform highp uint isRightHanded", "uniform mediump uint isRightHanded" }),
            Arguments.of("Vulkan-reserved sampler identifier is renamed before shaderc and sampler2D is untouched", GL20.GL_FRAGMENT_SHADER, "fontFilter.frag", """
                #version 330 core
                uniform sampler2D sampler;
                in vec2 texCoord;
                out vec4 fragColor;
                void main() {
                    fragColor = texture(sampler, texCoord);
                }
                """,
                new String[0], new String[] { "angelica_sampler_renamed", "sampler2D" }, new String[0]),
            Arguments.of("C++ keywords as identifiers are renamed and the not() builtin is untouched", GL20.GL_FRAGMENT_SHADER, "reserved.frag", """
                #version 330 core
                in vec2 texCoord;
                out vec4 fragColor;
                void main() {
                    vec4 try = vec4(texCoord, 0.0, 1.0);
                    float catch = try.x;
                    bvec2 flipped = not(lessThan(texCoord, vec2(0.5)));
                    fragColor = flipped.x ? try : vec4(catch);
                }
                """,
                new String[0], new String[] { "angelica_renamed_try", "angelica_renamed_catch", "not(" }, new String[] { "angelica_renamed_not" }),
            Arguments.of("multi-declarator bool uniforms are all restored", GL20.GL_FRAGMENT_SHADER, "multi_bool.frag", """
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
                """,
                new String[] { "bool a", "bool b", "bool c" }, new String[0], new String[] { "uniform uint", "uniform highp uint", "uniform mediump uint" }),
            Arguments.of("compound bool expression rewrites both branches", GL20.GL_FRAGMENT_SHADER, "compound_bool.frag", """
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
                """,
                new String[0], new String[0], new String[] { "!= 0u", "uniform uint" }));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void translates(String label, int stage, String file, String src, String[] uniformDecls, String[] present, String[] absent) {
        final String es = SpirvShaderTranslator.glslToGlslEs(src, stage, file);
        dumpOnFailure(file, src, es);

        assertNotNull(es);
        assertTrue(es.startsWith("#version 320 es"));
        for (String decl : uniformDecls) assertContainsDecl(es, decl.substring(0, decl.indexOf(' ')), decl.substring(decl.indexOf(' ') + 1));
        for (String needle : present) assertTrue(compact(es).contains(compact(needle)), needle);
        for (String needle : absent) assertFalse(compact(es).contains(compact(needle)), needle);
    }

    static Stream<Arguments> varyingLocations() {
        return Stream.of(
            Arguments.of("varyings stripped from VS outputs, kept on attributes", GL20.GL_VERTEX_SHADER, "strip.vert", """
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
                """,
                new String[] { "a_Position", "a_Color" }, new String[] { "v_Color", "v_TexCoord" }),
            Arguments.of("varyings stripped from FS inputs, kept on MRT outputs", GL20.GL_FRAGMENT_SHADER, "strip.frag", """
                #version 330 core
                in vec2 v_TexCoord;
                in vec4 v_Color;
                uniform sampler2D u_Tex;
                out vec4 fragColor;
                void main() {
                    fragColor = v_Color * texture(u_Tex, v_TexCoord);
                }
                """,
                new String[] { "fragColor" }, new String[] { "v_Color", "v_TexCoord" }),
            Arguments.of("varyings stripped from geometry shader inputs and outputs", GL32.GL_GEOMETRY_SHADER, "passthrough.geom", """
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
                """,
                new String[0], new String[] { "v_Color", "v_TexCoord", "g_Color", "g_TexCoord" }),
            Arguments.of("VS inputs keep explicit layouts and strip auto-assigned ones", GL20.GL_VERTEX_SHADER, "gbuffers_terrain.vert", """
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
                """,
                new String[] { "iris_Vertex", "iris_Color", "iris_MultiTexCoord0", "iris_MultiTexCoord1", "iris_Normal" }, new String[] { "mc_Entity", "mc_midTexCoord", "at_midBlock" }));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void varyingLocations(String label, int stage, String file, String src, String[] withLocation, String[] withoutLocation) {
        final String es = SpirvShaderTranslator.glslToGlslEs(src, stage, file);
        dumpOnFailure(file, src, es);

        assertNotNull(es);
        for (String name : withLocation) assertTrue(hasLayoutLocation(es, name), name + " must keep its layout");
        for (String name : withoutLocation) assertFalse(hasLayoutLocation(es, name), name + " layout must be stripped");
    }

    private static boolean hasLayoutLocation(String source, String name) {
        final Pattern p = Pattern.compile(
                "layout\\s*\\(\\s*location\\s*=\\s*\\d+\\s*\\)[^;]*?\\b"
                        + Pattern.quote(name) + "\\s*;");
        return p.matcher(source).find();
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
        assertFalse(es.contains("uniform uint") || es.contains("uniform highp uint") || es.contains("uniform mediump uint"), "bool uniform must be restored, not left as uint");
        assertFalse(es.contains("_RESERVED_IDENTIFIER_FIXUP_"), "default block struct must be unwrapped");

        assertFalse(es.matches("(?s).*_[0-9]+\\.u_Enabled.*"), "no _NN.u_Enabled member access should leak into the emitted source");
        assertFalse(es.contains("!= 0u"), "no bool use-site should still be comparing against 0u");
    }

    @Test
    void readonlyImage3D_staysAnImageUnderGLES() {
        final String src = """
            #version 420 core
            layout(r32ui) readonly uniform uimage3D voxel_img;
            layout(location = 0) out vec4 fragColor;
            void main() {
                uint v = imageLoad(voxel_img, ivec3(1, 2, 3)).x;
                fragColor = vec4(float(v));
            }
            """;

        final String es = SpirvShaderTranslator.glslToGlslEs(src, GL20.GL_FRAGMENT_SHADER, "readonly_image.frag");
        dumpOnFailure("readonly_image.frag", src, es);

        assertNotNull(es);
        assertTrue(es.startsWith("#version 320 es"));
        assertTrue(es.contains("uimage3D"), "a read-only image must stay an image on GLES; SDL_GPU's separate-texture rewrite must not leak here\n\n" + es);
        assertTrue(es.contains("imageLoad"), "the image must still be read with imageLoad\n\n" + es);
        assertFalse(es.contains("usampler3D"), "rewriting to a sampler would silently unbind it - glBindImageTexture feeds an image unit, not a sampler\n\n" + es);
        assertFalse(es.contains("texelFetch"), "texelFetch implies the sampler rewrite leaked into the GLES path\n\n" + es);
        assertFalse(es.contains("GL_EXT_samplerless_texture_functions"), "the samplerless extension is an SDL_GPU concern and is not valid to inject here\n\n" + es);
    }

    private static String compact(String s) {
        return s.replaceAll("\\s+", "");
    }

    private static void assertContainsDecl(String source, String type, String name) {
        final String needle1 = "uniform " + type + " " + name;
        final String needle2 = "uniform highp " + type + " " + name;
        final String needle3 = "uniform mediump " + type + " " + name;
        assertTrue(source.contains(needle1) || source.contains(needle2) || source.contains(needle3), "expected top-level `uniform " + type + " " + name + "` in emitted GLSL ES");
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
