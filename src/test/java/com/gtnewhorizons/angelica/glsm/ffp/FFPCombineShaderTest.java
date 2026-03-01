package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizons.angelica.AngelicaExtension;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.TexEnvState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FFP TexEnv COMBINE / multi-unit features:
 * TexEnvState tracking, FragmentKey packing, FragmentShaderGenerator COMBINE codegen, and glMultiTexCoord routing.
 */
@ExtendWith(AngelicaExtension.class)
class FFPCombineShaderTest {

    private final List<Integer> shadersToDelete = new ArrayList<>();
    private final List<Integer> programsToDelete = new ArrayList<>();

    @AfterEach
    void cleanup() {
        // Delete GL resources
        for (int p : programsToDelete) GL20.glDeleteProgram(p);
        for (int s : shadersToDelete) GL20.glDeleteShader(s);
        programsToDelete.clear();
        shadersToDelete.clear();

        // Reset texture units to defaults
        for (int i = 3; i >= 0; i--) {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + i);
            GLStateManager.getTextures().getTexEnvState(i).reset();
            if (i > 0) {
                GLStateManager.getTextures().getTextureUnitStates(i).disable();
            }
        }
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
    }

    @Test
    void testTexEnvStateTracking() {
        // Unit 0: COMBINE mode, SUBTRACT RGB, REPLACE alpha
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL13.GL_COMBINE);
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_RGB, GL13.GL_SUBTRACT);
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_ALPHA, GL11.GL_REPLACE);
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_RGB, GL11.GL_TEXTURE);
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_RGB, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE1_RGB, GL13.GL_PRIMARY_COLOR);
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND1_RGB, GL11.GL_ONE_MINUS_SRC_COLOR);

        TexEnvState env0 = GLStateManager.getTextures().getTexEnvState(0);
        assertEquals(GL13.GL_COMBINE, env0.mode);
        assertEquals(GL13.GL_SUBTRACT, env0.combineRgb);
        assertEquals(GL11.GL_REPLACE, env0.combineAlpha);
        assertEquals(GL11.GL_TEXTURE, env0.sourceRgb[0]);
        assertEquals(GL11.GL_ONE_MINUS_SRC_ALPHA, env0.operandRgb[0]);
        assertEquals(GL13.GL_PRIMARY_COLOR, env0.sourceRgb[1]);
        assertEquals(GL11.GL_ONE_MINUS_SRC_COLOR, env0.operandRgb[1]);

        // Unit 2: independent state
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE2);
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL13.GL_COMBINE);
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_RGB, GL11.GL_ADD);

        TexEnvState env2 = GLStateManager.getTextures().getTexEnvState(2);
        assertEquals(GL13.GL_COMBINE, env2.mode);
        assertEquals(GL11.GL_ADD, env2.combineRgb);

        // Verify unit 0 unchanged
        assertEquals(GL13.GL_SUBTRACT, env0.combineRgb, "Unit 0 should be independent of unit 2");
    }

    @Test
    void testFragmentKeyMultiUnitCombine() {
        setupXaeroState();

        FragmentKey fk = FragmentKey.fromState();

        assertEquals(4, fk.nrEnabledUnits(), "highestEnabled is unit 3 → nrEnabledUnits=4");
        assertTrue(fk.unitEnabled(0));
        assertFalse(fk.unitEnabled(1), "unit 1 should be disabled");
        assertTrue(fk.unitEnabled(2));
        assertTrue(fk.unitEnabled(3));

        assertEquals(FragmentKey.TEX_ENV_COMBINE, fk.unitMode(0));
        assertEquals(FragmentKey.COMBINE_SUBTRACT, fk.unitCombineRgb(0));

        assertEquals(FragmentKey.TEX_ENV_COMBINE, fk.unitMode(2));
        assertEquals(FragmentKey.COMBINE_ADD, fk.unitCombineRgb(2));

        assertEquals(FragmentKey.TEX_ENV_MODULATE, fk.unitMode(3));

        // Verify source/operand on unit 0
        assertEquals(FragmentKey.SRC_TEXTURE, fk.unitSourceRgb(0, 0));
        assertEquals(FragmentKey.OP_SRC_COLOR, fk.unitOperandRgb(0, 0));
        assertEquals(FragmentKey.SRC_PRIMARY_COLOR, fk.unitSourceRgb(0, 1));
        assertEquals(FragmentKey.OP_SRC_COLOR, fk.unitOperandRgb(0, 1));
    }

    @Test
    void testSingleUnitCombineCompiles() {
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.enableTexture();
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL13.GL_COMBINE);
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_RGB, GL11.GL_MODULATE);
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_ALPHA, GL11.GL_MODULATE);

        FragmentKey fk = FragmentKey.fromState();
        String fragSource = FragmentShaderGenerator.generate(fk);
        int shader = compileShader(fragSource, GL20.GL_FRAGMENT_SHADER);
        assertTrue(shader > 0, "Single-unit COMBINE fragment shader should compile");
    }

    @Test
    void testXaeroMultiUnitCombineCompiles() {
        setupXaeroState();

        FragmentKey fk = FragmentKey.fromState();
        VertexKey vk = VertexKey.fromState(true, false, true, false);

        String fragSource = FragmentShaderGenerator.generate(fk);
        String vertSource = VertexShaderGenerator.generate(vk);

        int vs = compileShader(vertSource, GL20.GL_VERTEX_SHADER);
        int fs = compileShader(fragSource, GL20.GL_FRAGMENT_SHADER);

        int prog = GL20.glCreateProgram();
        programsToDelete.add(prog);
        GL20.glAttachShader(prog, vs);
        GL20.glAttachShader(prog, fs);
        GL20.glLinkProgram(prog);

        int linkStatus = GL20.glGetProgrami(prog, GL20.GL_LINK_STATUS);
        if (linkStatus == 0) {
            String log = GL20.glGetProgramInfoLog(prog, 4096);
            fail("Xaero multi-unit program link failed:\n" + log
                + "\n\n--- Vertex Shader ---\n" + vertSource
                + "\n\n--- Fragment Shader ---\n" + fragSource);
        }
    }

    static Stream<Arguments> allCombineFunctions() {
        return Stream.of(
            Arguments.of("REPLACE", GL11.GL_REPLACE, 1),
            Arguments.of("MODULATE", GL11.GL_MODULATE, 2),
            Arguments.of("ADD", GL11.GL_ADD, 2),
            Arguments.of("ADD_SIGNED", GL13.GL_ADD_SIGNED, 2),
            Arguments.of("SUBTRACT", GL13.GL_SUBTRACT, 2),
            Arguments.of("INTERPOLATE", GL13.GL_INTERPOLATE, 3),
            Arguments.of("DOT3_RGB", GL13.GL_DOT3_RGB, 2),
            Arguments.of("DOT3_RGBA", GL13.GL_DOT3_RGBA, 2)
        );
    }

    @ParameterizedTest(name = "COMBINE function {0}")
    @MethodSource("allCombineFunctions")
    void testAllCombineFunctionsCompile(String name, int glFunc, int numArgs) {
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.enableTexture();
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL13.GL_COMBINE);
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_RGB, glFunc);
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_ALPHA, GL11.GL_REPLACE);

        // Set up appropriate sources
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_RGB, GL11.GL_TEXTURE);
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_RGB, GL11.GL_SRC_COLOR);
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_ALPHA, GL11.GL_TEXTURE);
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_ALPHA, GL11.GL_SRC_ALPHA);

        if (numArgs >= 2) {
            GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE1_RGB, GL13.GL_PRIMARY_COLOR);
            GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND1_RGB, GL11.GL_SRC_COLOR);
            GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE1_ALPHA, GL13.GL_PREVIOUS);
            GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND1_ALPHA, GL11.GL_SRC_ALPHA);
        }
        if (numArgs >= 3) {
            GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE2_RGB, GL13.GL_PREVIOUS);
            GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND2_RGB, GL11.GL_SRC_ALPHA);
            GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE2_ALPHA, GL13.GL_PREVIOUS);
            GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND2_ALPHA, GL11.GL_SRC_ALPHA);
        }

        FragmentKey fk = FragmentKey.fromState();
        String fragSource = FragmentShaderGenerator.generate(fk);
        int shader = compileShader(fragSource, GL20.GL_FRAGMENT_SHADER);
        assertTrue(shader > 0, "COMBINE function " + name + " should compile");
    }

    @Test
    void testCombineScopeDeclaresColorOutsideBlock() {
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.enableTexture();
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL13.GL_COMBINE);
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_RGB, GL11.GL_MODULATE);
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_ALPHA, GL11.GL_MODULATE);

        FragmentKey fk = FragmentKey.fromState();
        String src = FragmentShaderGenerator.generate(fk);

        // Result variables declared outside the scope block
        assertTrue(src.contains("vec3 combRgb0;"), "combRgb0 should be declared outside scope block");
        assertTrue(src.contains("float combAlpha0;"), "combAlpha0 should be declared outside scope block");

        // color assignment uses the result variables outside the block
        assertTrue(src.contains("vec4(combRgb0, combAlpha0)"), "color should be assigned from combine results outside block");

        // The scope block uses { } to contain arg declarations
        assertTrue(src.contains("  {\n"), "Should have scope block for arg declarations");

        // Bonus: verify it compiles
        int shader = compileShader(src, GL20.GL_FRAGMENT_SHADER);
        assertTrue(shader > 0, "Scope-correct COMBINE shader should compile");
    }

    // ---- Test 7: glMultiTexCoord2f routes to ShaderManager.currentTexCoord ----

    @Test
    void testMultiTexCoordRoutesToShaderManager() {
        // Not in immediate mode or display list recording, so should go to ShaderManager.currentTexCoord
        ShaderManager.setCurrentTexCoord(0.0f, 0.0f, 0.0f, 1.0f); // reset
        GLStateManager.glMultiTexCoord2f(GL13.GL_TEXTURE0, 0.5f, 0.75f);

        var texCoord = ShaderManager.getCurrentTexCoord();
        assertEquals(0.5f, texCoord.x, 1e-6f, "s coordinate should be 0.5");
        assertEquals(0.75f, texCoord.y, 1e-6f, "t coordinate should be 0.75");
    }

    @Test
    void testMultiTexCoord2dDelegatesToFloat() {
        ShaderManager.setCurrentTexCoord(0.0f, 0.0f, 0.0f, 1.0f);
        GLStateManager.glMultiTexCoord2d(GL13.GL_TEXTURE0, 0.5, 0.75);

        var texCoord = ShaderManager.getCurrentTexCoord();
        assertEquals(0.5f, texCoord.x, 1e-6f, "double 0.5 should arrive as float 0.5");
        assertEquals(0.75f, texCoord.y, 1e-6f, "double 0.75 should arrive as float 0.75");
    }

    /**
     * Configure the 4-unit Xaero-like combine chain:
     * Unit 0: COMBINE, RGB=SUBTRACT, SRC0=TEXTURE/SRC_COLOR, SRC1=PRIMARY_COLOR/SRC_COLOR
     * Unit 1: disabled
     * Unit 2: COMBINE, RGB=ADD, SRC0=TEXTURE/SRC_COLOR, SRC1=PREVIOUS/SRC_COLOR
     * Unit 3: MODULATE (simple)
     */
    private void setupXaeroState() {
        // Unit 0: COMBINE / SUBTRACT
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.enableTexture();
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL13.GL_COMBINE);
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_RGB, GL13.GL_SUBTRACT);
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_ALPHA, GL11.GL_REPLACE);
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_RGB, GL11.GL_TEXTURE);
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_RGB, GL11.GL_SRC_COLOR);
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE1_RGB, GL13.GL_PRIMARY_COLOR);
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND1_RGB, GL11.GL_SRC_COLOR);
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_ALPHA, GL11.GL_TEXTURE);
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_ALPHA, GL11.GL_SRC_ALPHA);

        // Unit 1: disabled (skip — default is disabled)

        // Unit 2: COMBINE / ADD
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE2);
        GLStateManager.enableTexture();
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL13.GL_COMBINE);
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_RGB, GL11.GL_ADD);
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_ALPHA, GL11.GL_REPLACE);
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_RGB, GL11.GL_TEXTURE);
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_RGB, GL11.GL_SRC_COLOR);
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE1_RGB, GL13.GL_PREVIOUS);
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND1_RGB, GL11.GL_SRC_COLOR);
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_ALPHA, GL11.GL_TEXTURE);
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_ALPHA, GL11.GL_SRC_ALPHA);

        // Unit 3: simple MODULATE
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE3);
        GLStateManager.enableTexture();
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);

        // Restore active unit
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
    }

    private int compileShader(String source, int type) {
        int shader = GL20.glCreateShader(type);
        shadersToDelete.add(shader);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        int status = GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS);
        if (status == 0) {
            String log = GL20.glGetShaderInfoLog(shader, 4096);
            fail("Shader compilation failed:\n" + log + "\n\n--- Source ---\n" + source);
        }
        return shader;
    }
}
