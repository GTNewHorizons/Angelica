package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(GLSMCoreExtension.class)
class ArrayUniformUploadGLTest {

    private static int compile(int type, String src) {
        final int s = GL20.glCreateShader(type);
        GL20.glShaderSource(s, src);
        GL20.glCompileShader(s);
        assertEquals(GL11.GL_TRUE, GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS), () -> "shader compile failed: " + GL20.glGetShaderInfoLog(s, 4096));
        return s;
    }

    private static int link(String vs, String fs) {
        final int v = compile(GL20.GL_VERTEX_SHADER, vs);
        final int f = compile(GL20.GL_FRAGMENT_SHADER, fs);
        final int p = GL20.glCreateProgram();
        GL20.glAttachShader(p, v);
        GL20.glAttachShader(p, f);
        GL20.glLinkProgram(p);
        assertEquals(GL11.GL_TRUE, GL20.glGetProgrami(p, GL20.GL_LINK_STATUS), () -> "link failed: " + GL20.glGetProgramInfoLog(p, 4096));
        GL20.glDeleteShader(v);
        GL20.glDeleteShader(f);
        return p;
    }

    private static final String VS = "#version 330 core\nvoid main(){ gl_Position = vec4(0.0); }\n";

    @Test
    void glUniform2FloatBufferUploadsWholeArray() {
        // Runtime index keeps the whole array active so element locations stay queryable.
        final String fs = "#version 330 core\n"
            + "uniform vec2 cosmicuvs[10];\n"
            + "uniform int idx;\n"
            + "out vec4 o;\n"
            + "void main(){ o = vec4(cosmicuvs[idx], cosmicuvs[(idx+1)%10]); }\n";
        final int p = link(VS, fs);
        try {
            GL20.glUseProgram(p);

            final FloatBuffer buf = BufferUtils.createFloatBuffer(20);
            for (int i = 0; i < 20; i++) buf.put(i + 1.0f); // 1..20, all distinct, all non-zero
            buf.flip();

            GLStateManager.glUniform2(GL20.glGetUniformLocation(p, "cosmicuvs"), buf);

            final FloatBuffer read = BufferUtils.createFloatBuffer(2);
            for (int i = 0; i < 10; i++) {
                final int loc = GL20.glGetUniformLocation(p, "cosmicuvs[" + i + "]");
                assertTrue(loc >= 0, "element " + i + " optimized out");
                read.clear();
                GL20.glGetUniform(p, loc, read);
                assertEquals(2 * i + 1.0f, read.get(0), 0.0f, "cosmicuvs[" + i + "].x");
                assertEquals(2 * i + 2.0f, read.get(1), 0.0f, "cosmicuvs[" + i + "].y");
            }
        } finally {
            GL20.glUseProgram(0);
            GL20.glDeleteProgram(p);
        }
    }

    @Test
    void glUniform1IntBufferUploadsWholeArray() {
        final String fs = "#version 330 core\n"
            + "uniform int vals[4];\n"
            + "uniform int idx;\n"
            + "out vec4 o;\n"
            + "void main(){ o = vec4(float(vals[idx])); }\n";
        final int p = link(VS, fs);
        try {
            GL20.glUseProgram(p);

            final IntBuffer buf = BufferUtils.createIntBuffer(4);
            buf.put(11).put(22).put(33).put(44).flip();
            GLStateManager.glUniform1(GL20.glGetUniformLocation(p, "vals"), buf);

            final IntBuffer read = BufferUtils.createIntBuffer(1);
            final int[] expect = {11, 22, 33, 44};
            for (int i = 0; i < 4; i++) {
                final int loc = GL20.glGetUniformLocation(p, "vals[" + i + "]");
                assertTrue(loc >= 0, "element " + i + " optimized out");
                read.clear();
                GL20.glGetUniform(p, loc, read);
                assertEquals(expect[i], read.get(0), "vals[" + i + "]");
            }
        } finally {
            GL20.glUseProgram(0);
            GL20.glDeleteProgram(p);
        }
    }
}
