package net.coderbot.iris.pipeline;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.shader.ShaderType;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import net.coderbot.iris.gbuffer_overrides.matching.InputAvailability;
import net.coderbot.iris.gl.shader.GlShader;
import net.coderbot.iris.gl.shader.ProgramCreator;
import net.coderbot.iris.pipeline.transform.PatchShaderType;
import net.coderbot.iris.pipeline.transform.ShaderTransformer;
import net.coderbot.iris.pipeline.transform.TransformPatcher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@GLCoreTest
class AttributeTransformerLinkGLTest {

    private static final String COMPAT_VERTEX = """
        #version 120
        varying vec4 color;
        varying vec3 normal;
        void main() {
            color = gl_Color;
            normal = normalize(gl_NormalMatrix * gl_Normal);
            gl_Position = ftransform();
        }
        """;

    private static final String COMPAT_FRAGMENT = """
        #version 120
        varying vec4 color;
        varying vec3 normal;
        void main() {
            gl_FragData[0] = color * vec4(normal * 0.5 + 0.5, 1.0);
        }
        """;

    private static final String CORE_PROFILE_VERTEX = """
        #version 150
        in vec3 vaPosition;
        in vec3 vaNormal;
        in vec4 vaColor;
        in ivec2 vaUV2;
        uniform mat4 modelViewMatrix;
        uniform mat4 projectionMatrix;
        out vec4 color;
        void main() {
            vec4 start = projectionMatrix * modelViewMatrix * vec4(vaPosition, 1.0);
            vec4 end = projectionMatrix * modelViewMatrix * vec4(vaPosition + vaNormal, 1.0);
            color = vaColor * vec4(vec2(vaUV2) / 240.0, 1.0, 1.0);
            gl_Position = mix(start, end, float(gl_VertexID % 2));
        }
        """;

    private static final String CORE_PROFILE_FRAGMENT = """
        #version 150
        in vec4 color;
        void main() {
            gl_FragData[0] = color;
        }
        """;

    private static int previousMaxGlslVersion;

    @BeforeAll
    static void pinGlslVersion() {
        previousMaxGlslVersion = RenderSystem.getMaxGlslVersion();
        Reflect.setStatic(RenderSystem.class, "maxGlslVersion", 330);
        ShaderTransformer.init();
    }

    @AfterAll
    static void restoreGlslVersion() {
        Reflect.setStatic(RenderSystem.class, "maxGlslVersion", previousMaxGlslVersion);
        ShaderTransformer.init();
    }

    @BeforeEach
    void clearCaches() {
        TransformPatcher.clearCache();
        ShaderTransformer.clearCache();
    }

    private static int link(String name, Map<PatchShaderType, String> result) {
        assertNotNull(result);
        final GlShader vertex = new GlShader(ShaderType.VERTEX, name + "Vertex", result.get(PatchShaderType.VERTEX));
        final GlShader fragment = new GlShader(ShaderType.FRAGMENT, name + "Fragment", result.get(PatchShaderType.FRAGMENT));
        try {
            return ProgramCreator.create(name, vertex, fragment);
        } finally {
            vertex.destroy();
            fragment.destroy();
        }
    }

    @Test
    void wideLineVariantLinksWithLineUniformsLive() {
        final Map<PatchShaderType, String> result = TransformPatcher.patchAttributesWideLines(COMPAT_VERTEX, null, null, null, COMPAT_FRAGMENT, InputAvailability.NONE);
        final int program = link("wideLines", result);
        try {
            assertNotEquals(-1, GLStateManager.glGetUniformLocation(program, "iris_LineWidth"), () -> result.get(PatchShaderType.VERTEX));
            assertNotEquals(-1, GLStateManager.glGetUniformLocation(program, "iris_ScreenSize"), () -> result.get(PatchShaderType.VERTEX));
        } finally {
            GLStateManager.glDeleteProgram(program);
        }
    }

    @Test
    void declaredCoreProfileInputsReadTheBoundAttributes() {
        final Map<PatchShaderType, String> result = TransformPatcher.patchAttributes(CORE_PROFILE_VERTEX, null, null, null, CORE_PROFILE_FRAGMENT, InputAvailability.BOTH);
        final int program = link("coreProfileInputs", result);
        try {
            for (String input : new String[] {"vaPosition", "vaNormal", "vaColor", "vaUV2"}) {
                assertEquals(-1, GLStateManager.glGetAttribLocation(program, input), () -> input + " left unmapped:\n" + result.get(PatchShaderType.VERTEX));
            }
            assertEquals(0, GLStateManager.glGetAttribLocation(program, "iris_Vertex"));
            assertEquals(1, GLStateManager.glGetAttribLocation(program, "iris_Color"));
            assertEquals(4, GLStateManager.glGetAttribLocation(program, "iris_Normal"));
            assertEquals(-1, GLStateManager.glGetUniformLocation(program, "modelViewMatrix"));
            assertEquals(-1, GLStateManager.glGetUniformLocation(program, "projectionMatrix"));
            assertNotEquals(-1, GLStateManager.glGetUniformLocation(program, "iris_ModelViewMatrix"));
            assertNotEquals(-1, GLStateManager.glGetUniformLocation(program, "iris_ProjectionMatrix"));
        } finally {
            GLStateManager.glDeleteProgram(program);
        }
    }
}
