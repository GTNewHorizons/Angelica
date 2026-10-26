package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
class ArrayUniformUploadGLTest {

    @Test
    void glUniform2FloatBufferUploadsWholeArray() {
        // Runtime index keeps the whole array active so element locations stay queryable.
        final String fs = "#version 330 core\n"
            + "uniform vec2 cosmicuvs[10];\n"
            + "uniform int idx;\n"
            + "out vec4 o;\n"
            + "void main(){ o = vec4(cosmicuvs[idx], cosmicuvs[(idx+1)%10]); }\n";
        final int p = GlTestSupport.program(GlTestSupport.TRIVIAL_VS, fs);
        try {
            GL20.glUseProgram(p);

            final FloatBuffer buf = BufferUtils.createFloatBuffer(20);
            for (int i = 0; i < 20; i++) buf.put(i + 1.0f); // 1..20, all distinct, all non-zero
            buf.flip();

            GLSMPrograms.glUniform2(GL20.glGetUniformLocation(p, "cosmicuvs"), buf);

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
            GLSMPrograms.glDeleteProgram(p);
        }
    }

    @Test
    void glUniform1IntBufferUploadsWholeArray() {
        final String fs = "#version 330 core\n"
            + "uniform int vals[4];\n"
            + "uniform int idx;\n"
            + "out vec4 o;\n"
            + "void main(){ o = vec4(float(vals[idx])); }\n";
        final int p = GlTestSupport.program(GlTestSupport.TRIVIAL_VS, fs);
        try {
            GL20.glUseProgram(p);

            final IntBuffer buf = BufferUtils.createIntBuffer(4);
            buf.put(11).put(22).put(33).put(44).flip();
            GLSMPrograms.glUniform1(GL20.glGetUniformLocation(p, "vals"), buf);

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
            GLSMPrograms.glDeleteProgram(p);
        }
    }
}
