package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.shader.ShaderDiskCache;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

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

    private static void assertRewrite(boolean fragment, String src, String[] present, String[] absent) {
        final String result = CompatShaderTransformer.transform(src, fragment);
        for (final String s : present) {
            assertTrue(result.contains(s), () -> "expected '" + s + "' in\n\n" + result);
        }
        for (final String s : absent) {
            assertFalse(result.contains(s), () -> "'" + s + "' must not remain in\n\n" + result);
        }
    }

    static Stream<Arguments> matrixBuiltins() {
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
                """, strings("angelica_ModelViewMatrix", "angelica_ProjectionMatrix", "angelica_NormalMatrix", "#version 330 core"), strings("gl_ModelViewMatrix", "gl_ProjectionMatrix", "gl_NormalMatrix", "texture2D", "gl_FragColor")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("matrixBuiltins")
    void matrixBuiltinsAreRewritten(String name, boolean fragment, String src, String[] present, String[] absent) {
        assertRewrite(fragment, src, present, absent);
    }

    static Stream<Arguments> textureFunctions() {
        return Stream.of(
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
                """, strings("texelFetch"), strings("texelFetch3D")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("textureFunctions")
    void legacyTextureFunctionsAreRenamed(String name, boolean fragment, String src, String[] present, String[] absent) {
        assertRewrite(fragment, src, present, absent);
    }

    @Test
    void testFragColorHandling() {
        String src = """
            #version 120
            void main() {
                gl_FragColor = vec4(1.0);
            }
            """;

        String result = CompatShaderTransformer.transform(src, true);
        assertFalse(result.contains("gl_FragColor"));
        // Should have layout-qualified output declaration
        assertTrue(result.contains("angelica_FragData0"));
        assertTrue(result.contains("layout"));
    }

    static Stream<Arguments> versionDirectives() {
        return Stream.of(
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
                """, strings("#version 330 core"), strings("compatibility")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("versionDirectives")
    void versionIsUpgradedToCore(String name, boolean fragment, String src, String[] present, String[] absent) {
        assertRewrite(fragment, src, present, absent);
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
        // Source should be returned as-is (passthrough)
        assertEquals(src, result);
    }

    @Test
    void testFogStructAccess() {
        String src = """
            #version 120
            void main() {
                vec4 c = gl_Fog.color;
                float d = gl_Fog.density;
            }
            """;

        String result = CompatShaderTransformer.transform(src, true);
        assertTrue(result.contains("angelica_Fog"), "gl_Fog should be renamed to angelica_Fog");
        assertFalse(result.contains("gl_Fog"), "gl_Fog should not remain in output");
        assertTrue(result.contains("angelica_FogParameters"), "Fog struct should be injected");
        assertTrue(result.contains("uniform float angelica_FogDensity"), "Fog density uniform should be injected");
        assertTrue(result.contains("uniform vec4 angelica_FogColor"), "Fog color uniform should be injected");
    }

    static Stream<Arguments> fogFragCoord() {
        return Stream.of(
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
                """, strings("in float angelica_FogFragCoord"), strings("gl_FogFragCoord")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fogFragCoord")
    void fogFragCoordBecomesAVarying(String name, boolean fragment, String src, String[] present, String[] absent) {
        assertRewrite(fragment, src, present, absent);
    }

    @Test
    void testGlFrontColorVertex() {
        String src = """
            #version 120
            void main() {
                gl_FrontColor = vec4(1.0, 0.0, 0.0, 1.0);
            }
            """;

        String result = CompatShaderTransformer.transform(src, false);
        assertTrue(result.contains("vec4 angelica_FrontColor"), "gl_FrontColor should become angelica_FrontColor out varying");
        assertFalse(result.contains("gl_FrontColor"), "gl_FrontColor should not remain");
    }

    @Test
    void testReservedWordTextureAsVariable() {
        // Shader declares 'texture' as a sampler variable - this is valid in GLSL 120
        // but 'texture' is a reserved word in GLSL 330+, causing parse failures without pre-rename
        String src = """
            #version 120
            uniform sampler2D texture;
            void main() {
                gl_FragColor = texture2D(texture, vec2(0.5));
            }
            """;

        // Should not throw - the pre-parse replaceTexture handles this
        String result = CompatShaderTransformer.transform(src, true);
        assertNotNull(result);
        assertTrue(result.contains("#version 330 core"));
        // The variable named 'texture' should have been renamed to gtexture (matching CommonTransformer convention)
        assertTrue(result.contains("gtexture"), "Variable 'texture' should be renamed to gtexture");
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

    static Stream<Arguments> vertexAttributes() {
        return Stream.of(
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
                """, strings("in vec3 angelica_Normal", "location = 4"), strings("gl_Normal")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("vertexAttributes")
    void vertexAttributeBuiltinsGetFixedLocations(String name, boolean fragment, String src, String[] present, String[] absent) {
        assertRewrite(fragment, src, present, absent);
    }

    static Stream<Arguments> texCoordVaryings() {
        return Stream.of(
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
                """, strings("out vec4 angelica_TexCoord0", "out vec4 angelica_TexCoord1"), strings("gl_TexCoord")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("texCoordVaryings")
    void texCoordBecomesPerIndexVaryings(String name, boolean fragment, String src, String[] present, String[] absent) {
        assertRewrite(fragment, src, present, absent);
    }

    @Test
    void testGlColorVertexVsFragmentDifferentTreatment() {
        // Vertex: gl_Color = vertex attribute -> angelica_Color
        // Fragment: gl_Color = interpolated gl_FrontColor -> angelica_FrontColor
        String vertSrc = """
            #version 120
            void main() {
                gl_FrontColor = gl_Color;
                gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
            }
            """;
        String fragSrc = """
            #version 120
            void main() {
                gl_FragColor = gl_Color;
            }
            """;

        String vertResult = CompatShaderTransformer.transform(vertSrc, false);
        String fragResult = CompatShaderTransformer.transform(fragSrc, true);

        // Vertex shader: gl_Color -> angelica_Color (attribute), gl_FrontColor -> angelica_FrontColor (out)
        assertTrue(vertResult.contains("angelica_Color"), "vertex gl_Color -> angelica_Color");
        assertTrue(vertResult.contains("angelica_FrontColor"), "vertex gl_FrontColor -> angelica_FrontColor");
        assertFalse(vertResult.contains("gl_Color"), "no gl_Color should remain in vertex");
        assertFalse(vertResult.contains("gl_FrontColor"), "no gl_FrontColor should remain");

        // Fragment shader: gl_Color -> angelica_FrontColor (in varying)
        assertTrue(fragResult.contains("angelica_FrontColor"), "fragment gl_Color -> angelica_FrontColor");
        assertFalse(fragResult.contains("gl_Color"), "fragment should NOT have gl_Color");
    }

    @Test
    void testDefinePreservedDuringTransformation() {
        String src = """
            #version 120
            #define M_PI 3.1415926535897932384626433832795
            void main() {
                float x = 2.0 * M_PI;
                gl_FragColor = vec4(x);
            }
            """;

        String result = CompatShaderTransformer.transform(src, true);
        assertTrue(result.contains("#define M_PI 3.1415926535897932384626433832795"),
            "#define should be preserved through transformation\n\n" + result);
    }

    @Test
    void testGlFrontLightModelProductSceneColor() {
        String src = """
            #version 120
            void main() {
                gl_FrontColor = gl_FrontLightModelProduct.sceneColor;
                gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
            }
            """;

        String result = CompatShaderTransformer.transform(src, false);
        assertFalse(result.contains("gl_FrontLightModelProduct"), "gl_FrontLightModelProduct should be replaced");
        assertTrue(result.contains("uniform vec4 angelica_SceneColor"), "angelica_SceneColor uniform should be injected");
    }

    @Test
    void testVersionPatternDoesNotEatNextLine() {
        String src = "#version 330\nsomeword\n";

        String result = CompatShaderTransformer.transform(src, false);
        assertTrue(result.contains("someword"), "Next-line word must not be eaten by version fixup");
        assertTrue(result.contains("#version 330 core"), "Version should be upgraded to core");
    }

    @Test
    void testModernShaderWithoutCoreKeyword() {
        String src = """
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
            """;

        String result = CompatShaderTransformer.transform(src, false);
        assertTrue(result.contains("#version 430 core"), "Version should get 'core' profile added");
        assertTrue(result.contains("in vec3 Position"), "'in' keyword must not be eaten");
        assertTrue(result.contains("ModelViewMat"), "Shader content must be preserved");
        assertTrue(result.contains("ProjMat"), "Shader content must be preserved");
        assertTrue(result.contains("fogDistance"), "Shader content must be preserved");
    }

    @Test
    void testLegacyQualifierConversion() {
        String vertSrc = """
            #version 120
            attribute float a_Custom;
            varying vec2 v_TexCoord;
            void main() {
                v_TexCoord = vec2(a_Custom);
                gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
            }
            """;
        String fragSrc = """
            #version 120
            varying vec2 v_TexCoord;
            void main() {
                gl_FragColor = vec4(v_TexCoord, 0.0, 1.0);
            }
            """;

        String vertResult = CompatShaderTransformer.transform(vertSrc, false);
        String fragResult = CompatShaderTransformer.transform(fragSrc, true);

        // attribute -> in, varying -> out (vertex)
        assertFalse(vertResult.contains("attribute"), "no 'attribute' keyword in core profile");
        assertFalse(vertResult.contains("varying"), "no 'varying' keyword in vertex");
        assertTrue(vertResult.contains("in float a_Custom"), "attribute -> in");
        assertTrue(vertResult.contains("out vec2 v_TexCoord"), "varying -> out in vertex");

        // varying -> in (fragment)
        assertFalse(fragResult.contains("varying"), "no 'varying' keyword in fragment");
        assertTrue(fragResult.contains("in vec2 v_TexCoord"), "varying -> in in fragment");
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

    private static String compact(String s) {
        return s.replaceAll("\\s+", "");
    }

    private static String core(String src) {
        return compact(CompatShaderTransformer.transform(src, false));
    }

    private static void assertLoc(String c, int loc, String decl) {
        assertTrue(c.contains(compact("layout(location=" + loc + ")in" + decl)), decl + " expected at location " + loc + "\n\n" + c);
    }

    private static void assertNoLoc(String c, int loc, String decl) {
        assertFalse(c.contains(compact("layout(location=" + loc + ")in" + decl)), decl + " must not be at location " + loc + "\n\n" + c);
    }

    @Test
    void testCustomPositionAttributePinnedToLocationZero() {
        // Betweenlands blshader fix
        String src = """
            #version 120
            attribute vec4 a_position;
            uniform mat4 u_projMat;
            uniform vec2 u_outSize;
            varying vec2 v_texCoord;
            void main(){
                gl_Position = u_projMat * vec4(a_position.xy, 0.0, 1.0);
                v_texCoord = a_position.xy / u_outSize;
            }
            """;

        String result = CompatShaderTransformer.transform(src, false);
        assertLoc(compact(result), 0, "vec4 a_position");
        assertTrue(result.contains("#version 330 core"), result);
    }

    @Test
    void testMultipleLocationlessInputsGetSequentialLocations() {
        String c = core("""
            #version 120
            attribute vec4 a_pos;
            attribute vec2 a_uv;
            varying vec2 v_uv;
            void main() {
                v_uv = a_uv;
                gl_Position = vec4(a_pos.xy, 0.0, 1.0);
            }
            """);
        assertLoc(c, 0, "vec4 a_pos");
        assertLoc(c, 1, "vec2 a_uv");
    }

    @Test
    void testCustomInputReservesAroundGlVertex() {
        String c = core("""
            #version 120
            attribute vec4 a_foo;
            void main() {
                gl_Position = ftransform() + a_foo;
            }
            """);
        assertLoc(c, 0, "vec4 angelica_Vertex");
        assertLoc(c, 1, "vec4 a_foo");
    }

    @Test
    void testCustomInputSkipsFfpSlots() {
        String c = core("""
            #version 120
            attribute vec4 a_foo;
            void main() {
                gl_Position = vec4(a_foo.xy, 0.0, 1.0) + gl_Color;
            }
            """);
        assertLoc(c, 0, "vec4 a_foo");
        assertLoc(c, 1, "vec4 angelica_Color");
    }

    @Test
    void testExplicitLowLocationReserved() {
        String c = core("""
            #version 120
            layout(location = 0) in vec4 a_x;
            attribute vec4 a_y;
            void main() {
                gl_Position = a_x + a_y;
            }
            """);
        assertLoc(c, 0, "vec4 a_x");
        assertLoc(c, 1, "vec4 a_y");
    }

    @Test
    void testMatrixInputSortedFirst() {
        String c = core("""
            #version 120
            attribute vec4 a_a;
            attribute mat4 a_m;
            void main() {
                gl_Position = a_m * a_a;
            }
            """);
        assertLoc(c, 0, "mat4 a_m");   // 4-slot matrix sorted first
        assertLoc(c, 4, "vec4 a_a");
    }

    @Test
    void testInputNamedLocationStillPinned() {
        assertLoc(core("""
            #version 120
            attribute vec4 a_location;
            void main() {
                gl_Position = vec4(a_location.xy, 0.0, 1.0);
            }
            """), 0, "vec4 a_location");
    }

    @Test
    void testInputWithExplicitLayoutLeftAlone() {
        String c = core("""
            #version 120
            layout(location = 3) in vec4 a_data;
            attribute vec4 a_position;
            varying vec2 v_uv;
            void main() {
                v_uv = a_data.xy;
                gl_Position = vec4(a_position.xy, 0.0, 1.0);
            }
            """);
        assertLoc(c, 0, "vec4 a_position");   // sole location-less input
        assertLoc(c, 3, "vec4 a_data");       // explicit location preserved
        assertNoLoc(c, 0, "vec4 a_data");
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
        assertLoc(c, 0, "vec4 a_position");
        assertLoc(c, 1, "vec4 angelica_Color");
        assertTrue(c.contains("uniformmat4angelica_ModelViewMatrix"), "matrix uniform injected\n\n" + result);
        assertFalse(c.contains("gl_ModelViewMatrix"), "builtin rewritten (not fallback)\n\n" + result);

        final int mainIdx = result.indexOf("void main");
        assertTrue(mainIdx > 0, result);
        assertTrue(result.indexOf("angelica_ModelViewMatrix") < mainIdx, "uniform before main\n\n" + result);
        assertTrue(result.indexOf("a_position") < mainIdx, "pinned input before main\n\n" + result);
    }

    @Test
    void testGlVertexShaderDoesNotPinCustomAttribute() {
        String c = core("""
            #version 120
            attribute float activelights;
            void main() {
                gl_Position = ftransform();
                float x = activelights;
            }
            """);
        assertLoc(c, 0, "vec4 angelica_Vertex");
        assertNoLoc(c, 0, "float activelights");
    }

    @ParameterizedTest   // array on the declarator (vec4 a[2]) and on the type (vec4[2] a) must both round-trip
    @ValueSource(strings = {"attribute vec4 positions[2];", "attribute vec4[2] positions;"})
    void testArrayAttributePreservedEitherSyntax(String decl) {
        String c = core("""
            #version 120
            %s
            attribute vec4 a_tail;
            void main() {
                gl_Position = positions[0] + positions[1] + a_tail;
            }
            """.formatted(decl));
        assertLoc(c, 0, "vec4 positions[2]");   // 2-slot array pinned to 0..1
        assertLoc(c, 2, "vec4 a_tail");
    }

    @Test
    void testMultiDeclaratorSplitIntoSeparateLocations() {
        String result = CompatShaderTransformer.transform("""
            #version 120
            attribute vec4 a, b;
            void main() {
                gl_Position = a + b;
            }
            """, false);
        String c = compact(result);
        assertLoc(c, 0, "vec4 a");
        assertLoc(c, 1, "vec4 b");
        assertFalse(result.matches("(?s).*\\battribute\\b.*"), "no deprecated 'attribute' keyword may survive\n\n" + result);
        assertTrue(result.contains("#version 330 core"), result);
    }

    @Test
    void testMultiDeclaratorWithMixedArray() {
        String result = CompatShaderTransformer.transform("""
            #version 120
            attribute vec4 a, b[2];
            void main() {
                gl_Position = a + b[0] + b[1];
            }
            """, false);
        String c = compact(result);
        // b takes 2 slots; sorted before the 1-slot a, so b -> 0..1, a -> 2.
        assertLoc(c, 0, "vec4 b[2]");
        assertLoc(c, 2, "vec4 a");
        assertFalse(result.matches("(?s).*\\battribute\\b.*"), "no deprecated 'attribute' keyword may survive\n\n" + result);
    }

    @Test
    void testHexExplicitLocationReserved() {
        String c = core("""
            #version 120
            layout(location = 0x1) in vec4 a_x;
            attribute vec4 a_y;
            void main() {
                gl_Position = a_x + a_y;
            }
            """);
        assertTrue(c.contains("layout(location=0x1)invec4a_x"), "hex explicit location preserved\n\n" + c);
        assertNoLoc(c, 1, "vec4 a_y");   // must not collide with the hex-reserved slot
        assertLoc(c, 0, "vec4 a_y");
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
