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
        assertTrue(result.contains("vec4 angelica_FrontColor"), "gl_FrontColor should become a local vec4");
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
}
