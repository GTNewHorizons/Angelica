package net.coderbot.iris.pipeline.transform;

import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import net.coderbot.iris.gbuffer_overrides.matching.InputAvailability;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.taumc.glsl.ShaderParser;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttributeTransformerInstancedTest {

    private static final InputAvailability TEX_LM = new InputAvailability(true, true);

    private static final String VERTEX = """
        #version 120
        attribute vec4 mc_Entity;
        attribute vec4 mc_midTexCoord;
        varying vec2 texcoord;
        varying vec2 lmcoord;
        varying vec4 color;
        varying float mats;
        varying vec2 midtex;
        varying vec3 nrm;
        void main() {
            texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
            lmcoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
            color = gl_Color;
            mats = mc_Entity.x;
            midtex = mc_midTexCoord.st;
            nrm = normalize(gl_NormalMatrix * gl_Normal);
            gl_Position = ftransform();
        }
        """;

    private static final String VERTEX_MV_INVERSE = """
        #version 120
        varying vec3 wpos;
        void main() {
            wpos = (gl_ModelViewMatrixInverse * (gl_ModelViewMatrix * gl_Vertex)).xyz;
            gl_Position = gl_ProjectionMatrix * gl_ModelViewMatrix * gl_Vertex;
        }
        """;

    private static final String FRAGMENT = """
        #version 120
        varying vec4 color;
        void main() {
            gl_FragData[0] = color;
        }
        """;

    @BeforeAll
    static void initShaderTransformer() throws Exception {
        Reflect.setStatic(RenderSystem.class, "maxGlslVersion", 460);
        ShaderTransformer.init();
    }

    @BeforeEach
    void clearCaches() {
        TransformPatcher.clearCache();
        ShaderTransformer.clearCache();
    }

    private static String instancedVertex(String vertex) {
        Map<PatchShaderType, String> result = TransformPatcher.patchAttributesInstanced(vertex, null, null, null, FRAGMENT, TEX_LM, false);
        assertNotNull(result);
        String v = result.get(PatchShaderType.VERTEX);
        assertNotNull(v);
        ShaderParser.parseShader(v);
        return v;
    }

    private static String squash(String s) {
        return s.replaceAll("\\s+", "");
    }

    private static void assertHas(String shader, String snippet) {
        assertTrue(squash(shader).contains(squash(snippet)), "expected snippet: " + snippet);
    }

    private static void assertLacks(String shader, String snippet) {
        assertFalse(squash(shader).contains(squash(snippet)), "unexpected snippet: " + snippet);
    }

    private static int indexOfSquashed(String shader, String snippet) {
        return squash(shader).indexOf(squash(snippet));
    }

    @Test
    void declaresInstanceAttribsAndGlobalMatrices() {
        final String v = instancedVertex(VERTEX);
        assertHas(v, "layout(location = 7) in vec4 iris_InstMat0");
        assertHas(v, "layout(location = 8) in vec4 iris_InstMat1");
        assertHas(v, "layout(location = 9) in vec4 iris_InstMat2");
        assertHas(v, "layout(location = 10) in vec4 iris_InstMat3");
        assertHas(v, "layout(location = 11) in vec4 iris_InstColor");
        assertHas(v, "layout(location = 12) in vec2 iris_InstLightmap");
        assertLacks(v, "uniform mat4 iris_ModelViewMatrix;");
        assertLacks(v, "uniform mat3 iris_NormalMatrix;");
        assertHas(v, "mat4 iris_ModelViewMatrix;");
        assertHas(v, "iris_ModelViewMatrix = mat4(iris_InstMat0, iris_InstMat1, iris_InstMat2, iris_InstMat3)");
        assertHas(v, "iris_NormalMatrix = mat3(normalize(iris_InstMat0.xyz), normalize(iris_InstMat1.xyz), normalize(iris_InstMat2.xyz))");
    }

    @Test
    void lightmapComesFromInstanceAttribute() {
        final String v = instancedVertex(VERTEX);
        assertLacks(v, "layout(location = 3)");
        assertHas(v, "iris_MultiTexCoord1 = vec4(iris_InstLightmap, 0.0, 1.0)");
        assertHas(v, "iris_LightmapTextureMatrix * iris_MultiTexCoord1");
    }

    @Test
    void colorComposesInstanceColor() {
        assertHas(instancedVertex(VERTEX), "iris_Color * iris_ColorModulator * iris_InstColor");
    }

    @Test
    void foldsEntityAndMidTexCoordAttributes() {
        final String v = instancedVertex(VERTEX);
        assertLacks(v, "mc_Entity");
        assertLacks(v, "mc_midTexCoord");
        assertHas(v, "vec4(-1.0, -1.0, 0.0, 1.0)");
        assertHas(v, "vec4(0.5, 0.5, 0.0, 1.0)");
    }

    @Test
    void mvInverseOnlyWhenReferenced() {
        assertLacks(instancedVertex(VERTEX), "inverse(iris_ModelViewMatrix)");

        final String with = instancedVertex(VERTEX_MV_INVERSE);
        assertHas(with, "iris_ModelViewMatrixInverse = inverse(iris_ModelViewMatrix)");
        assertLacks(with, "uniform mat4 iris_ModelViewMatrixInverse;");
    }

    @Test
    void assignmentBlockRunsFirstInMain() {
        final String v = instancedVertex(VERTEX);
        final int mainIdx = indexOfSquashed(v, "void main()");
        final int assignIdx = indexOfSquashed(v, "iris_ModelViewMatrix = mat4(");
        final int bodyUseIdx = indexOfSquashed(v, "iris_TextureMatrix * iris_MultiTexCoord0");
        assertTrue(mainIdx >= 0 && assignIdx > mainIdx);
        assertTrue(bodyUseIdx > assignIdx);
    }

    @Test
    void standardPathUnchanged() {
        Map<PatchShaderType, String> result = TransformPatcher.patchAttributes(VERTEX, null, null, null, FRAGMENT, TEX_LM, false);
        assertNotNull(result);
        final String v = result.get(PatchShaderType.VERTEX);
        assertHas(v, "uniform mat4 iris_ModelViewMatrix;");
        assertHas(v, "layout(location = 3) in vec4 iris_MultiTexCoord1");
        assertHas(v, "mc_Entity");
        assertLacks(v, "iris_InstMat0");
    }
}
