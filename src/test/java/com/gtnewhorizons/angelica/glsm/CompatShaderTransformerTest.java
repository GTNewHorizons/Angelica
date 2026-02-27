package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.Test;

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
}
