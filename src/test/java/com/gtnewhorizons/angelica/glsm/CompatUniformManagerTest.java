package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.AngelicaExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL20;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@ExtendWith(AngelicaExtension.class)
class CompatUniformManagerTest {

    private int program = 0;

    @AfterEach
    void cleanup() {
        if (program != 0) {
            GL20.glUseProgram(0);
            GL20.glDeleteProgram(program);
            program = 0;
        }
    }

    @Test
    void testProgramWithCompatUniforms() {
        // Create a shader program that uses angelica_* uniforms
        String vertSrc = """
            #version 330 core
            uniform mat4 angelica_ModelViewMatrix;
            uniform mat4 angelica_ProjectionMatrix;
            uniform mat3 angelica_NormalMatrix;
            in vec3 position;
            in vec3 normal;
            out vec3 vNormal;
            void main() {
                vNormal = angelica_NormalMatrix * normal;
                gl_Position = angelica_ProjectionMatrix * angelica_ModelViewMatrix * vec4(position, 1.0);
            }
            """;

        String fragSrc = """
            #version 330 core
            in vec3 vNormal;
            layout (location = 0) out vec4 fragColor;
            void main() {
                fragColor = vec4(vNormal, 1.0);
            }
            """;

        program = createAndLinkProgram(vertSrc, fragSrc);

        // onLinkProgram should discover the uniform locations
        CompatUniformManager.onLinkProgram(program);

        assertTrue(CompatUniformManager.hasProgram(program));

        int[] locs = CompatUniformManager.getLocations(program);
        assertNotNull(locs);
        // ModelView, Projection, NormalMatrix should have valid locations
        assertNotEquals(-1, locs[CompatUniformManager.LOC_MODELVIEW], "angelica_ModelViewMatrix location");
        assertNotEquals(-1, locs[CompatUniformManager.LOC_PROJECTION], "angelica_ProjectionMatrix location");
        assertNotEquals(-1, locs[CompatUniformManager.LOC_NORMAL], "angelica_NormalMatrix location");

        // Use program and upload — should not error
        GL20.glUseProgram(program);
        CompatUniformManager.onUseProgram(program);

        // Verify no GL errors
        assertEquals(0, org.lwjgl.opengl.GL11.glGetError(), "GL error after uniform upload");
    }

    @Test
    void testProgramWithoutCompatUniforms() {
        String vertSrc = """
            #version 330 core
            uniform mat4 mvp;
            in vec3 position;
            void main() {
                gl_Position = mvp * vec4(position, 1.0);
            }
            """;

        String fragSrc = """
            #version 330 core
            layout (location = 0) out vec4 fragColor;
            void main() {
                fragColor = vec4(1.0);
            }
            """;

        program = createAndLinkProgram(vertSrc, fragSrc);

        CompatUniformManager.onLinkProgram(program);

        // No angelica_* uniforms → not tracked
        assertFalse(CompatUniformManager.hasProgram(program));
    }

    @Test
    void testProgramWithFogUniforms() {
        // All fog uniforms must be used in the shader to prevent the GLSL compiler from optimizing them away (which would make glGetUniformLocation return -1)
        String vertSrc = """
            #version 330 core
            uniform float angelica_FogDensity;
            uniform float angelica_FogStart;
            uniform float angelica_FogEnd;
            uniform vec4 angelica_FogColor;
            in vec3 position;
            out vec4 vFogData;
            void main() {
                gl_Position = vec4(position, 1.0);
                float dist = length(position);
                float linearFog = clamp((angelica_FogEnd - dist) / (angelica_FogEnd - angelica_FogStart), 0.0, 1.0);
                float expFog = exp(-angelica_FogDensity * dist);
                vFogData = angelica_FogColor * linearFog * expFog;
            }
            """;

        String fragSrc = """
            #version 330 core
            in vec4 vFogData;
            layout (location = 0) out vec4 fragColor;
            void main() {
                fragColor = vFogData;
            }
            """;

        program = createAndLinkProgram(vertSrc, fragSrc);

        CompatUniformManager.onLinkProgram(program);

        assertTrue(CompatUniformManager.hasProgram(program));

        int[] locs = CompatUniformManager.getLocations(program);
        assertNotNull(locs);
        // Fog uniforms should have valid locations (indices 6-9)
        assertNotEquals(-1, locs[CompatUniformManager.LOC_FOG_DENSITY], "angelica_FogDensity location");
        assertNotEquals(-1, locs[CompatUniformManager.LOC_FOG_START], "angelica_FogStart location");
        assertNotEquals(-1, locs[CompatUniformManager.LOC_FOG_END], "angelica_FogEnd location");
        assertNotEquals(-1, locs[CompatUniformManager.LOC_FOG_COLOR], "angelica_FogColor location");

        // Use program and upload — should not error
        GL20.glUseProgram(program);
        CompatUniformManager.onUseProgram(program);

        assertEquals(0, org.lwjgl.opengl.GL11.glGetError(), "GL error after fog uniform upload");
    }

    @Test
    void testDeleteProgramCleansUp() {
        String vertSrc = """
            #version 330 core
            uniform mat4 angelica_ModelViewMatrix;
            in vec3 position;
            void main() {
                gl_Position = angelica_ModelViewMatrix * vec4(position, 1.0);
            }
            """;

        String fragSrc = """
            #version 330 core
            layout (location = 0) out vec4 fragColor;
            void main() {
                fragColor = vec4(1.0);
            }
            """;

        program = createAndLinkProgram(vertSrc, fragSrc);

        CompatUniformManager.onLinkProgram(program);
        assertTrue(CompatUniformManager.hasProgram(program));

        CompatUniformManager.onDeleteProgram(program);
        assertFalse(CompatUniformManager.hasProgram(program));
    }

    private static int createAndLinkProgram(String vertSrc, String fragSrc) {
        int vs = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        GL20.glShaderSource(vs, vertSrc);
        GL20.glCompileShader(vs);
        if (GL20.glGetShaderi(vs, GL20.GL_COMPILE_STATUS) == 0) {
            String log = GL20.glGetShaderInfoLog(vs, 4096);
            GL20.glDeleteShader(vs);
            fail("Vertex shader compilation failed: " + log);
        }

        int fs = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(fs, fragSrc);
        GL20.glCompileShader(fs);
        if (GL20.glGetShaderi(fs, GL20.GL_COMPILE_STATUS) == 0) {
            String log = GL20.glGetShaderInfoLog(fs, 4096);
            GL20.glDeleteShader(vs);
            GL20.glDeleteShader(fs);
            fail("Fragment shader compilation failed: " + log);
        }

        int prog = GL20.glCreateProgram();
        GL20.glAttachShader(prog, vs);
        GL20.glAttachShader(prog, fs);
        GL20.glLinkProgram(prog);
        if (GL20.glGetProgrami(prog, GL20.GL_LINK_STATUS) == 0) {
            String log = GL20.glGetProgramInfoLog(prog, 4096);
            GL20.glDeleteProgram(prog);
            GL20.glDeleteShader(vs);
            GL20.glDeleteShader(fs);
            fail("Program link failed: " + log);
        }

        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        return prog;
    }
}
