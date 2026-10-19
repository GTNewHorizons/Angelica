package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.shader.ShaderDiskCache;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompatShaderTransformerTest {

    private static String[] strings(String... values) {
        return values;
    }

    private static String compact(String s) {
        return s.replaceAll("\\s+", "");
    }

    private static void assertNeedles(String result, String[] present, String[] absent) {
        for (final String s : present) {
            assertTrue(result.contains(s), () -> "expected '" + s + "' in\n\n" + result);
        }
        for (final String s : absent) {
            assertFalse(result.contains(s), () -> "'" + s + "' must not remain in\n\n" + result);
        }
    }

    static Stream<Arguments> rewrites() {
        return Stream.of(
            Arguments.of("modelview and projection matrices", false, """
                #version 120
                void main() {
                    vec4 pos = gl_ModelViewMatrix * gl_Vertex;
                    gl_Position = gl_ProjectionMatrix * pos;
                }
                """, strings("angelica_ModelViewMatrix", "angelica_ProjectionMatrix", "#version 330 core"), strings("gl_ModelViewMatrix", "gl_ProjectionMatrix")),
            Arguments.of("normal matrix", false, """
                #version 120
                void main() {
                    vec3 n = gl_NormalMatrix * gl_Normal;
                }
                """, strings("uniform mat3 angelica_NormalMatrix"), strings("gl_NormalMatrix")),
            Arguments.of("modelview-projection matrix", false, """
                #version 120
                void main() {
                    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
                }
                """, strings("angelica_ProjectionMatrix"), strings("gl_ModelViewProjectionMatrix")),
            Arguments.of("inverse matrices", false, """
                #version 120
                void main() {
                    vec4 pos = gl_ModelViewMatrixInverse * vec4(0.0, 0.0, 0.0, 1.0);
                    vec4 proj = gl_ProjectionMatrixInverse * vec4(0.0, 0.0, -1.0, 1.0);
                }
                """, strings(), strings("gl_ModelViewMatrixInverse", "gl_ProjectionMatrixInverse")),
            Arguments.of("texture matrix", false, """
                #version 120
                void main() {
                    vec4 lmCoord = gl_TextureMatrix[1] * gl_MultiTexCoord1;
                }
                """, strings(), strings("gl_TextureMatrix")),
            Arguments.of("several builtins in one fragment shader", true, """
                #version 120
                uniform sampler2D tex;
                void main() {
                    vec3 n = gl_NormalMatrix * gl_Normal;
                    vec4 pos = gl_ModelViewMatrix * gl_Vertex;
                    gl_Position = gl_ProjectionMatrix * pos;
                    vec4 color = texture2D(tex, vec2(0.0));
                    gl_FragColor = color;
                }
                """, strings("angelica_ModelViewMatrix", "angelica_ProjectionMatrix", "angelica_NormalMatrix", "#version 330 core"), strings("gl_ModelViewMatrix", "gl_ProjectionMatrix", "gl_NormalMatrix", "texture2D", "gl_FragColor")),
            Arguments.of("texture2D", false, """
                #version 120
                uniform sampler2D tex;
                void main() {
                    vec4 color = texture2D(tex, vec2(0.0));
                }
                """, strings("texture"), strings("texture2D")),
            Arguments.of("shadow2D", true, """
                #version 120
                uniform sampler2DShadow shadowMap;
                void main() {
                    float s = shadow2D(shadowMap, vec3(0.0)).r;
                    gl_FragColor = vec4(s);
                }
                """, strings("texture"), strings("shadow2D(")),
            Arguments.of("texelFetch3D", true, """
                #version 120
                uniform sampler3D vol;
                void main() {
                    vec4 v = texelFetch3D(vol, ivec3(0), 0);
                    gl_FragColor = v;
                }
                """, strings("texelFetch"), strings("texelFetch3D")),
            Arguments.of("110 becomes 330 core", true, """
                #version 110
                void main() {
                    gl_FragColor = vec4(1.0);
                }
                """, strings("#version 330 core"), strings("#version 110")),
            Arguments.of("compatibility profile is stripped", false, """
                #version 330 compatibility
                void main() {
                    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
                }
                """, strings("#version 330 core"), strings("compatibility")),
            Arguments.of("vertex shader declares an out", false, """
                #version 120
                void main() {
                    gl_FogFragCoord = length(gl_ModelViewMatrix * gl_Vertex);
                }
                """, strings("out float angelica_FogFragCoord"), strings("gl_FogFragCoord")),
            Arguments.of("fragment shader declares an in", true, """
                #version 120
                void main() {
                    float f = gl_FogFragCoord;
                    gl_FragColor = vec4(f);
                }
                """, strings("in float angelica_FogFragCoord"), strings("gl_FogFragCoord")),
            Arguments.of("gl_Vertex at location 0", false, """
                #version 120
                void main() {
                    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
                }
                """, strings("in vec4 angelica_Vertex", "location = 0"), strings("gl_Vertex")),
            Arguments.of("gl_Color at location 1", false, """
                #version 120
                varying vec4 v_Color;
                void main() {
                    v_Color = gl_Color;
                    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
                }
                """, strings("in vec4 angelica_Color", "location = 1"), strings("gl_Color")),
            Arguments.of("gl_MultiTexCoord0 at location 2", false, """
                #version 120
                void main() {
                    gl_TexCoord[0] = gl_MultiTexCoord0;
                    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
                }
                """, strings("in vec4 angelica_MultiTexCoord0", "location = 2"), strings("gl_MultiTexCoord0")),
            Arguments.of("gl_MultiTexCoord1 at location 3", false, """
                #version 120
                void main() {
                    vec4 lm = gl_TextureMatrix[1] * gl_MultiTexCoord1;
                    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
                }
                """, strings("angelica_MultiTexCoord1", "location = 3"), strings("gl_MultiTexCoord1")),
            Arguments.of("gl_Normal at location 4", false, """
                #version 120
                void main() {
                    vec3 n = gl_NormalMatrix * gl_Normal;
                    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
                }
                """, strings("in vec3 angelica_Normal", "location = 4"), strings("gl_Normal")),
            Arguments.of("vertex shader declares an out", false, """
                #version 120
                void main() {
                    gl_TexCoord[0] = gl_MultiTexCoord0;
                    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
                }
                """, strings("out vec4 angelica_TexCoord0"), strings("gl_TexCoord")),
            Arguments.of("fragment shader declares an in", true, """
                #version 120
                void main() {
                    vec2 uv = gl_TexCoord[0].xy;
                    gl_FragColor = vec4(uv, 0.0, 1.0);
                }
                """, strings("in vec4 angelica_TexCoord0"), strings("gl_TexCoord")),
            Arguments.of("one varying per index", false, """
                #version 120
                void main() {
                    gl_TexCoord[0] = gl_MultiTexCoord0;
                    gl_TexCoord[1] = gl_MultiTexCoord1;
                    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
                }
                """, strings("out vec4 angelica_TexCoord0", "out vec4 angelica_TexCoord1"), strings("gl_TexCoord")),
            Arguments.of("gl_FragColor becomes a layout-qualified output", true, """
                #version 120
                void main() {
                    gl_FragColor = vec4(1.0);
                }
                """, strings("angelica_FragData0", "layout"), strings("gl_FragColor")),
            Arguments.of("gl_Fog struct access is renamed and its uniforms injected", true, """
                #version 120
                void main() {
                    vec4 c = gl_Fog.color;
                    float d = gl_Fog.density;
                }
                """, strings("angelica_Fog", "angelica_FogParameters", "uniform float angelica_FogDensity", "uniform vec4 angelica_FogColor"), strings("gl_Fog")),
            Arguments.of("gl_FrontColor becomes an out varying", false, """
                #version 120
                void main() {
                    gl_FrontColor = vec4(1.0, 0.0, 0.0, 1.0);
                }
                """, strings("vec4 angelica_FrontColor"), strings("gl_FrontColor")),
            Arguments.of("texture as a variable name is renamed to gtexture", true, """
                #version 120
                uniform sampler2D texture;
                void main() {
                    gl_FragColor = texture2D(texture, vec2(0.5));
                }
                """, strings("#version 330 core", "gtexture"), strings()),
            Arguments.of("vertex gl_Color is the color attribute", false, """
                #version 120
                void main() {
                    gl_FrontColor = gl_Color;
                    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
                }
                """, strings("angelica_Color", "angelica_FrontColor"), strings("gl_Color", "gl_FrontColor")),
            Arguments.of("fragment gl_Color is the interpolated front color", true, """
                #version 120
                void main() {
                    gl_FragColor = gl_Color;
                }
                """, strings("angelica_FrontColor"), strings("gl_Color")),
            Arguments.of("#define survives the transformation", true, """
                #version 120
                #define M_PI 3.1415926535897932384626433832795
                void main() {
                    float x = 2.0 * M_PI;
                    gl_FragColor = vec4(x);
                }
                """, strings("#define M_PI 3.1415926535897932384626433832795"), strings()),
            Arguments.of("gl_FrontLightModelProduct.sceneColor becomes a uniform", false, """
                #version 120
                void main() {
                    gl_FrontColor = gl_FrontLightModelProduct.sceneColor;
                    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
                }
                """, strings("uniform vec4 angelica_SceneColor"), strings("gl_FrontLightModelProduct")),
            Arguments.of("version fixup does not eat the next line", false, "#version 330\nsomeword\n", strings("someword", "#version 330 core"), strings()),
            Arguments.of("modern shader without core keyword keeps its content", false, """
                #version 430
                in vec3 Position;
                uniform mat4 ModelViewMat;
                uniform mat4 ProjMat;
                out vec4 vertexColor;
                out float fogDistance;
                void main() {
                    vec4 finalPos = vec4(Position, 1.0);
                    vec4 modelPos = ModelViewMat * finalPos;
                    gl_Position = ProjMat * modelPos;
                    fogDistance = length(modelPos.xz);
                    vertexColor = vec4(1.0);
                }
                """, strings("#version 430 core", "in vec3 Position", "ModelViewMat", "ProjMat", "fogDistance"), strings()),
            Arguments.of("vertex attribute and varying become in and out", false, """
                #version 120
                attribute float a_Custom;
                varying vec2 v_TexCoord;
                void main() {
                    v_TexCoord = vec2(a_Custom);
                    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
                }
                """, strings("in float a_Custom", "out vec2 v_TexCoord"), strings("attribute", "varying")),
            Arguments.of("fragment varying becomes in", true, """
                #version 120
                varying vec2 v_TexCoord;
                void main() {
                    gl_FragColor = vec4(v_TexCoord, 0.0, 1.0);
                }
                """, strings("in vec2 v_TexCoord"), strings("varying")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void rewrites(String name, boolean fragment, String src, String[] present, String[] absent) {
        assertNeedles(CompatShaderTransformer.transform(src, fragment), present, absent);
    }

    @Test
    void testAlphaTestDiscardInjected() {
        String src = """
            #version 120
            void main() {
                gl_FragColor = vec4(1.0, 0.0, 0.0, 0.5);
            }
            """;

        String result = CompatShaderTransformer.transform(src, true);
        assertTrue(result.contains("uniform float angelica_currentAlphaTest"), "Alpha test uniform should be injected");
        assertTrue(result.contains("angelica_FragData0 . a <= angelica_currentAlphaTest") || result.contains("angelica_FragData0.a <= angelica_currentAlphaTest"),
            "Alpha test discard should be appended to main()");
    }

    static Stream<Arguments> inputLocations() {
        return Stream.of(
            Arguments.of("custom position attribute is pinned to location zero", """
                #version 120
                attribute vec4 a_position;
                uniform mat4 u_projMat;
                uniform vec2 u_outSize;
                varying vec2 v_texCoord;
                void main(){
                    gl_Position = u_projMat * vec4(a_position.xy, 0.0, 1.0);
                    v_texCoord = a_position.xy / u_outSize;
                }
                """, strings("0:vec4 a_position"), strings(), strings("#version 330 core"), strings()),
            Arguments.of("location-less inputs get sequential locations", """
                #version 120
                attribute vec4 a_pos;
                attribute vec2 a_uv;
                varying vec2 v_uv;
                void main() {
                    v_uv = a_uv;
                    gl_Position = vec4(a_pos.xy, 0.0, 1.0);
                }
                """, strings("0:vec4 a_pos", "1:vec2 a_uv"), strings(), strings(), strings()),
            Arguments.of("a custom input is placed around gl_Vertex", """
                #version 120
                attribute vec4 a_foo;
                void main() {
                    gl_Position = ftransform() + a_foo;
                }
                """, strings("0:vec4 angelica_Vertex", "1:vec4 a_foo"), strings(), strings(), strings()),
            Arguments.of("a custom input skips the FFP slots", """
                #version 120
                attribute vec4 a_foo;
                void main() {
                    gl_Position = vec4(a_foo.xy, 0.0, 1.0) + gl_Color;
                }
                """, strings("0:vec4 a_foo", "1:vec4 angelica_Color"), strings(), strings(), strings()),
            Arguments.of("an explicit low location is reserved", """
                #version 120
                layout(location = 0) in vec4 a_x;
                attribute vec4 a_y;
                void main() {
                    gl_Position = a_x + a_y;
                }
                """, strings("0:vec4 a_x", "1:vec4 a_y"), strings(), strings(), strings()),
            Arguments.of("a 4-slot matrix input is sorted first", """
                #version 120
                attribute vec4 a_a;
                attribute mat4 a_m;
                void main() {
                    gl_Position = a_m * a_a;
                }
                """, strings("0:mat4 a_m", "4:vec4 a_a"), strings(), strings(), strings()),
            Arguments.of("an input named location is still pinned", """
                #version 120
                attribute vec4 a_location;
                void main() {
                    gl_Position = vec4(a_location.xy, 0.0, 1.0);
                }
                """, strings("0:vec4 a_location"), strings(), strings(), strings()),
            Arguments.of("an input with an explicit layout is left alone", """
                #version 120
                layout(location = 3) in vec4 a_data;
                attribute vec4 a_position;
                varying vec2 v_uv;
                void main() {
                    v_uv = a_data.xy;
                    gl_Position = vec4(a_position.xy, 0.0, 1.0);
                }
                """, strings("0:vec4 a_position", "3:vec4 a_data"), strings("0:vec4 a_data"), strings(), strings()),
            Arguments.of("a gl_Vertex shader does not pin a custom attribute", """
                #version 120
                attribute float activelights;
                void main() {
                    gl_Position = ftransform();
                    float x = activelights;
                }
                """, strings("0:vec4 angelica_Vertex"), strings("0:float activelights"), strings(), strings()),
            Arguments.of("array on the declarator round-trips", """
                #version 120
                attribute vec4 positions[2];
                attribute vec4 a_tail;
                void main() {
                    gl_Position = positions[0] + positions[1] + a_tail;
                }
                """, strings("0:vec4 positions[2]", "2:vec4 a_tail"), strings(), strings(), strings()),
            Arguments.of("array on the type round-trips", """
                #version 120
                attribute vec4[2] positions;
                attribute vec4 a_tail;
                void main() {
                    gl_Position = positions[0] + positions[1] + a_tail;
                }
                """, strings("0:vec4 positions[2]", "2:vec4 a_tail"), strings(), strings(), strings()),
            Arguments.of("a multi-declarator is split into separate locations", """
                #version 120
                attribute vec4 a, b;
                void main() {
                    gl_Position = a + b;
                }
                """, strings("0:vec4 a", "1:vec4 b"), strings(), strings("#version 330 core"), strings("attribute")),
            Arguments.of("a 2-slot array declarator sorts before a 1-slot one", """
                #version 120
                attribute vec4 a, b[2];
                void main() {
                    gl_Position = a + b[0] + b[1];
                }
                """, strings("0:vec4 b[2]", "2:vec4 a"), strings(), strings(), strings("attribute")),
            Arguments.of("a hex explicit location is preserved and reserved", """
                #version 120
                layout(location = 0x1) in vec4 a_x;
                attribute vec4 a_y;
                void main() {
                    gl_Position = a_x + a_y;
                }
                """, strings("0x1:vec4 a_x", "0:vec4 a_y"), strings("1:vec4 a_y"), strings(), strings()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void inputLocations(String name, String src, String[] at, String[] notAt, String[] present, String[] absent) {
        final String result = CompatShaderTransformer.transform(src, false);
        final String c = compact(result);
        for (final String slot : at) {
            assertTrue(c.contains(locationDecl(slot)), () -> slot + " expected\n\n" + result);
        }
        for (final String slot : notAt) {
            assertFalse(c.contains(locationDecl(slot)), () -> slot + " must not be assigned\n\n" + result);
        }
        assertNeedles(result, present, absent);
    }

    private static String locationDecl(String slot) {
        final int colon = slot.indexOf(':');
        return compact("layout(location=" + slot.substring(0, colon) + ")in" + slot.substring(colon + 1));
    }

    @Test
    void testPassthroughUnchangedShader() {
        String src = """
            #version 330 core
            in vec3 position;
            uniform mat4 mvp;
            void main() {
                gl_Position = mvp * vec4(position, 1.0);
            }
            """;

        String result = CompatShaderTransformer.transform(src, false);
        assertEquals(src, result);
    }

    @Test
    void testCacheHit() {
        String src = """
            #version 120
            void main() {
                gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
            }
            """;

        String result1 = CompatShaderTransformer.transform(src, false);
        String result2 = CompatShaderTransformer.transform(src, false);
        assertSame(result1, result2, "Second call should return cached instance (same reference)");
    }

    @Test
    void testPassthroughVertexShader_matchesPrecisionQualifiedFragmentInputs() {
        final String esFrag = """
            #version 320 es
            precision mediump float;
            precision highp int;
            in highp vec4 angelica_TexCoord0;
            in highp vec4 angelica_FrontColor;
            in highp float angelica_FogFragCoord;
            layout(location = 0) out highp vec4 angelica_FragData0;
            void main() {
                angelica_FragData0 = angelica_FrontColor * angelica_TexCoord0 + vec4(angelica_FogFragCoord);
            }
            """;

        final String vs = CompatShaderTransformer.generatePassthroughVertexShader(esFrag);
        assertNotNull(vs);
        assertTrue(vs.contains("out vec4 angelica_FrontColor"), "VS must declare angelica_FrontColor output\n" + vs);
        assertTrue(vs.contains("out vec4 angelica_TexCoord0"), "VS must declare angelica_TexCoord0 output\n" + vs);
        assertTrue(vs.contains("out float angelica_FogFragCoord"), "VS must declare angelica_FogFragCoord output\n" + vs);
    }

    @Test
    void testReservedWordNewIsRenamed() {
        String src = """
            #version 120
            uniform sampler2D tex;
            void main() {
                vec4 color = texture2D(tex, vec2(0.5));
                float new = color.r > 0.5 ? 1.0 : 0.0;
                gl_FragColor = vec4(new, new, new, 1.0);
            }
            """;

        String result = CompatShaderTransformer.transform(src, true);
        assertFalse(result.matches("(?s).*\\bnew\\b(?!\\s*\\().*"), "'new' as identifier must be renamed in core profile output\n\n" + result);
        assertTrue(result.contains("angelica_renamed_new"), "'new' should be renamed to angelica_renamed_new\n\n" + result);
    }

    @Test
    void testPinnedShaderRemainsWellFormed() {
        String src = """
            #version 120
            attribute vec4 a_position;
            void main() {
                gl_FrontColor = gl_Color;
                gl_TexCoord[0] = gl_MultiTexCoord0;
                gl_Position = gl_ModelViewMatrix * vec4(a_position.xyz, 1.0);
            }
            """;

        String result = CompatShaderTransformer.transform(src, false);
        String c = compact(result);
        assertTrue(c.contains(locationDecl("0:vec4 a_position")), result);
        assertTrue(c.contains(locationDecl("1:vec4 angelica_Color")), result);
        assertTrue(c.contains("uniformmat4angelica_ModelViewMatrix"), "matrix uniform injected\n\n" + result);
        assertFalse(c.contains("gl_ModelViewMatrix"), "builtin rewritten (not fallback)\n\n" + result);

        final int mainIdx = result.indexOf("void main");
        assertTrue(mainIdx > 0, result);
        assertTrue(result.indexOf("angelica_ModelViewMatrix") < mainIdx, "uniform before main\n\n" + result);
        assertTrue(result.indexOf("a_position") < mainIdx, "pinned input before main\n\n" + result);
    }

    @Test
    void diskCacheHitReplacesTransform(@TempDir Path dir) {
        final String src = """
            #version 120
            void main() {
                gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
            }
            """;
        try {
            ShaderDiskCache.configure(dir, "t");
            CompatShaderTransformer.clearCache();
            final String first = CompatShaderTransformer.transform(src, false);
            assertTrue(first.contains("angelica_ProjectionMatrix"));
            CompatShaderTransformer.clearCache();
            assertEquals(first, CompatShaderTransformer.transform(src, false));
            CompatShaderTransformer.clearCache();
            ShaderDiskCache.putString(CompatShaderTransformer.diskKey(src, false), "POISON");
            assertEquals("POISON", CompatShaderTransformer.transform(src, false));
        } finally {
            CompatShaderTransformer.clearCache();
            Reflect.setStatic(ShaderDiskCache.class, "root", null);
        }
    }
}
