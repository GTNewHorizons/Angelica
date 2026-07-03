package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompatShaderTransformerTest {

    @Test
    void testModelViewMatrixReplacement() {
        String src = """
            #version 120
            void main() {
                vec4 pos = gl_ModelViewMatrix * gl_Vertex;
                gl_Position = gl_ProjectionMatrix * pos;
            }
            """;

        String result = CompatShaderTransformer.transform(src, false);
        assertTrue(result.contains("angelica_ModelViewMatrix"));
        assertTrue(result.contains("angelica_ProjectionMatrix"));
        assertFalse(result.contains("gl_ModelViewMatrix"));
        assertFalse(result.contains("gl_ProjectionMatrix"));
        assertTrue(result.contains("#version 330 core"));
    }

    @Test
    void testNormalMatrixReplacement() {
        String src = """
            #version 120
            void main() {
                vec3 n = gl_NormalMatrix * gl_Normal;
            }
            """;

        String result = CompatShaderTransformer.transform(src, false);
        assertTrue(result.contains("uniform mat3 angelica_NormalMatrix"));
        assertFalse(result.contains("gl_NormalMatrix"));
    }

    @Test
    void testModelViewProjectionMatrixExpression() {
        String src = """
            #version 120
            void main() {
                gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
            }
            """;

        String result = CompatShaderTransformer.transform(src, false);
        assertFalse(result.contains("gl_ModelViewProjectionMatrix"));
        assertTrue(result.contains("angelica_ProjectionMatrix"));
    }

    @Test
    void testTextureFunctionRenames() {
        String src = """
            #version 120
            uniform sampler2D tex;
            void main() {
                vec4 color = texture2D(tex, vec2(0.0));
            }
            """;

        String result = CompatShaderTransformer.transform(src, false);
        // texture2D → texture
        assertFalse(result.contains("texture2D"));
        assertTrue(result.contains("texture"));
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

    @Test
    void testVersionUpgradeTo330() {
        String src = """
            #version 110
            void main() {
                gl_FragColor = vec4(1.0);
            }
            """;

        String result = CompatShaderTransformer.transform(src, true);
        assertTrue(result.contains("#version 330 core"));
        assertFalse(result.contains("#version 110"));
    }

    @Test
    void testCompatibilityProfileStripped() {
        String src = """
            #version 330 compatibility
            void main() {
                gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
            }
            """;

        String result = CompatShaderTransformer.transform(src, false);
        assertTrue(result.contains("#version 330 core"));
        assertFalse(result.contains("compatibility"));
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
    void testCombinedBuiltinsInSingleShader() {
        String src = """
            #version 120
            uniform sampler2D tex;
            void main() {
                vec3 n = gl_NormalMatrix * gl_Normal;
                vec4 pos = gl_ModelViewMatrix * gl_Vertex;
                gl_Position = gl_ProjectionMatrix * pos;
                vec4 color = texture2D(tex, vec2(0.0));
                gl_FragColor = color;
            }
            """;

        String result = CompatShaderTransformer.transform(src, true);
        assertTrue(result.contains("angelica_ModelViewMatrix"));
        assertTrue(result.contains("angelica_ProjectionMatrix"));
        assertTrue(result.contains("angelica_NormalMatrix"));
        assertFalse(result.contains("gl_ModelViewMatrix"));
        assertFalse(result.contains("gl_ProjectionMatrix"));
        assertFalse(result.contains("gl_NormalMatrix"));
        assertFalse(result.contains("texture2D"));
        assertFalse(result.contains("gl_FragColor"));
        assertTrue(result.contains("#version 330 core"));
    }

    @Test
    void testInverseMatrixReplacement() {
        String src = """
            #version 120
            void main() {
                vec4 pos = gl_ModelViewMatrixInverse * vec4(0.0, 0.0, 0.0, 1.0);
                vec4 proj = gl_ProjectionMatrixInverse * vec4(0.0, 0.0, -1.0, 1.0);
            }
            """;

        String result = CompatShaderTransformer.transform(src, false);
        assertFalse(result.contains("gl_ModelViewMatrixInverse"));
        assertFalse(result.contains("gl_ProjectionMatrixInverse"));
    }

    @Test
    void testTextureMatrixReplacement() {
        String src = """
            #version 120
            void main() {
                vec4 lmCoord = gl_TextureMatrix[1] * gl_MultiTexCoord1;
            }
            """;

        String result = CompatShaderTransformer.transform(src, false);
        assertFalse(result.contains("gl_TextureMatrix"));
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

    @Test
    void testFogFragCoordVertex() {
        String src = """
            #version 120
            void main() {
                gl_FogFragCoord = length(gl_ModelViewMatrix * gl_Vertex);
            }
            """;

        String result = CompatShaderTransformer.transform(src, false);
        assertTrue(result.contains("out float angelica_FogFragCoord"), "Vertex shader should get 'out' declaration");
        assertTrue(result.contains("angelica_FogFragCoord"), "gl_FogFragCoord should be renamed");
        assertFalse(result.contains("gl_FogFragCoord"), "gl_FogFragCoord should not remain");
    }

    @Test
    void testFogFragCoordFragment() {
        String src = """
            #version 120
            void main() {
                float f = gl_FogFragCoord;
                gl_FragColor = vec4(f);
            }
            """;

        String result = CompatShaderTransformer.transform(src, true);
        assertTrue(result.contains("in float angelica_FogFragCoord"), "Fragment shader should get 'in' declaration");
        assertFalse(result.contains("gl_FogFragCoord"), "gl_FogFragCoord should not remain");
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
    void testShadow2DRename() {
        String src = """
            #version 120
            uniform sampler2DShadow shadowMap;
            void main() {
                float s = shadow2D(shadowMap, vec3(0.0)).r;
                gl_FragColor = vec4(s);
            }
            """;

        String result = CompatShaderTransformer.transform(src, true);
        assertFalse(result.contains("shadow2D("), "shadow2D should be renamed");
        // shadow2D → texture with vec4 wrapping
        assertTrue(result.contains("texture"), "shadow2D should be renamed to texture");
    }

    @Test
    void testReservedWordTextureAsVariable() {
        // Shader declares 'texture' as a sampler variable — this is valid in GLSL 120
        // but 'texture' is a reserved word in GLSL 330+, causing parse failures without pre-rename
        String src = """
            #version 120
            uniform sampler2D texture;
            void main() {
                gl_FragColor = texture2D(texture, vec2(0.5));
            }
            """;

        // Should not throw — the pre-parse replaceTexture handles this
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

    @Test
    void testTexelFetch3DTriggersTransformation() {
        String src = """
            #version 120
            uniform sampler3D vol;
            void main() {
                vec4 v = texelFetch3D(vol, ivec3(0), 0);
                gl_FragColor = v;
            }
            """;

        String result = CompatShaderTransformer.transform(src, true);
        // texelFetch3D should be renamed to texelFetch
        assertFalse(result.contains("texelFetch3D"), "texelFetch3D should be renamed");
        assertTrue(result.contains("texelFetch"), "texelFetch3D should become texelFetch");
    }

    @Test
    void testVertexAttributeGlVertex() {
        String src = """
            #version 120
            void main() {
                gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
            }
            """;

        String result = CompatShaderTransformer.transform(src, false);
        assertFalse(result.contains("gl_Vertex"), "gl_Vertex should be replaced");
        assertTrue(result.contains("angelica_Vertex"), "should become angelica_Vertex");
        assertTrue(result.contains("in vec4 angelica_Vertex"), "should have in declaration");
        assertTrue(result.contains("location = 0"), "should use attribute location 0");
    }

    @Test
    void testVertexAttributeGlColorInVertexShader() {
        // gl_Color in a vertex shader is the per-vertex color attribute, not the fragment varying
        String src = """
            #version 120
            varying vec4 v_Color;
            void main() {
                v_Color = gl_Color;
                gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
            }
            """;

        String result = CompatShaderTransformer.transform(src, false);
        assertTrue(result.contains("angelica_Color"), "gl_Color → angelica_Color (vertex attribute)");
        assertTrue(result.contains("in vec4 angelica_Color"), "should have in declaration");
        assertTrue(result.contains("location = 1"), "should use attribute location 1");
        assertFalse(result.contains("gl_Color"), "gl_Color should not remain");
    }

    @Test
    void testVertexAttributeMultiTexCoord0() {
        String src = """
            #version 120
            void main() {
                gl_TexCoord[0] = gl_MultiTexCoord0;
                gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
            }
            """;

        String result = CompatShaderTransformer.transform(src, false);
        assertFalse(result.contains("gl_MultiTexCoord0"), "gl_MultiTexCoord0 should be replaced");
        assertTrue(result.contains("angelica_MultiTexCoord0"), "should become angelica_MultiTexCoord0");
        assertTrue(result.contains("in vec4 angelica_MultiTexCoord0"), "should have in declaration");
        assertTrue(result.contains("location = 2"), "should use attribute location 2");
    }

    @Test
    void testVertexAttributeMultiTexCoord1() {
        String src = """
            #version 120
            void main() {
                vec4 lm = gl_TextureMatrix[1] * gl_MultiTexCoord1;
                gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
            }
            """;

        String result = CompatShaderTransformer.transform(src, false);
        assertFalse(result.contains("gl_MultiTexCoord1"), "gl_MultiTexCoord1 should be replaced");
        assertTrue(result.contains("angelica_MultiTexCoord1"), "should become angelica_MultiTexCoord1");
        assertTrue(result.contains("location = 3"), "should use attribute location 3");
    }

    @Test
    void testVertexAttributeGlNormal() {
        String src = """
            #version 120
            void main() {
                vec3 n = gl_NormalMatrix * gl_Normal;
                gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
            }
            """;

        String result = CompatShaderTransformer.transform(src, false);
        assertFalse(result.contains("gl_Normal"), "gl_Normal should be replaced");
        assertTrue(result.contains("angelica_Normal"), "should become angelica_Normal");
        assertTrue(result.contains("in vec3 angelica_Normal"), "should have in vec3 declaration");
        assertTrue(result.contains("location = 4"), "should use attribute location 4");
    }

    @Test
    void testTexCoordVaryingVertexShader() {
        String src = """
            #version 120
            void main() {
                gl_TexCoord[0] = gl_MultiTexCoord0;
                gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
            }
            """;

        String result = CompatShaderTransformer.transform(src, false);
        assertFalse(result.contains("gl_TexCoord"), "gl_TexCoord should be replaced");
        assertTrue(result.contains("angelica_TexCoord0"), "should become angelica_TexCoord0");
        assertTrue(result.contains("out vec4 angelica_TexCoord0"), "vertex shader should have 'out' declaration");
    }

    @Test
    void testTexCoordVaryingFragmentShader() {
        String src = """
            #version 120
            void main() {
                vec2 uv = gl_TexCoord[0].xy;
                gl_FragColor = vec4(uv, 0.0, 1.0);
            }
            """;

        String result = CompatShaderTransformer.transform(src, true);
        assertFalse(result.contains("gl_TexCoord"), "gl_TexCoord should be replaced");
        assertTrue(result.contains("angelica_TexCoord0"), "should become angelica_TexCoord0");
        assertTrue(result.contains("in vec4 angelica_TexCoord0"), "fragment shader should have 'in' declaration");
    }

    @Test
    void testTexCoordMultipleIndices() {
        String src = """
            #version 120
            void main() {
                gl_TexCoord[0] = gl_MultiTexCoord0;
                gl_TexCoord[1] = gl_MultiTexCoord1;
                gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
            }
            """;

        String result = CompatShaderTransformer.transform(src, false);
        assertTrue(result.contains("out vec4 angelica_TexCoord0"), "should have TexCoord0 out");
        assertTrue(result.contains("out vec4 angelica_TexCoord1"), "should have TexCoord1 out");
        assertFalse(result.contains("gl_TexCoord"), "no gl_TexCoord should remain");
    }

    @Test
    void testGlColorVertexVsFragmentDifferentTreatment() {
        // Vertex: gl_Color = vertex attribute → angelica_Color
        // Fragment: gl_Color = interpolated gl_FrontColor → angelica_FrontColor
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

        // Vertex shader: gl_Color → angelica_Color (attribute), gl_FrontColor → angelica_FrontColor (out)
        assertTrue(vertResult.contains("angelica_Color"), "vertex gl_Color → angelica_Color");
        assertTrue(vertResult.contains("angelica_FrontColor"), "vertex gl_FrontColor → angelica_FrontColor");
        assertFalse(vertResult.contains("gl_Color"), "no gl_Color should remain in vertex");
        assertFalse(vertResult.contains("gl_FrontColor"), "no gl_FrontColor should remain");

        // Fragment shader: gl_Color → angelica_FrontColor (in varying)
        assertTrue(fragResult.contains("angelica_FrontColor"), "fragment gl_Color → angelica_FrontColor");
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
        assertTrue(result.contains("angelica_SceneColor"), "should reference angelica_SceneColor");
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

        // attribute → in, varying → out (vertex)
        assertFalse(vertResult.contains("attribute"), "no 'attribute' keyword in core profile");
        assertFalse(vertResult.contains("varying"), "no 'varying' keyword in vertex");
        assertTrue(vertResult.contains("in float a_Custom"), "attribute → in");
        assertTrue(vertResult.contains("out vec2 v_TexCoord"), "varying → out in vertex");

        // varying → in (fragment)
        assertFalse(fragResult.contains("varying"), "no 'varying' keyword in fragment");
        assertTrue(fragResult.contains("in vec2 v_TexCoord"), "varying → in in fragment");
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
}
