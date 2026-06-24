package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@ExtendWith(GLSMCoreExtension.class)
class InstancedQuadDrawGLTest {

    private static final String VS = "#version 330 core\nlayout(location=0) in vec2 aPos;\nvoid main(){ gl_Position = vec4(aPos, 0.0, 1.0); }\n";
    private static final String FS = "#version 330 core\nout vec4 o;\nvoid main(){ o = vec4(1.0); }\n";

    private int program;
    private int vbo;
    private int ebo;

    @BeforeEach
    void setup() {
        program = buildProgram();
        GLStateManager.glUseProgram(program);

        final FloatBuffer verts = BufferUtils.createFloatBuffer(8);
        verts.put(new float[] {0, 0, 1, 0, 1, 1, 0, 1}).flip();
        vbo = GLStateManager.glGenBuffers();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, verts, GL15.GL_STATIC_DRAW);
        GLStateManager.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 0, 0L);
        GLStateManager.glEnableVertexAttribArray(0);

        final IntBuffer idx = BufferUtils.createIntBuffer(4);
        idx.put(new int[] {0, 1, 2, 3}).flip();
        ebo = GLStateManager.glGenBuffers();
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GLStateManager.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, idx, GL15.GL_STATIC_DRAW);

        drainErrors();
    }

    @AfterEach
    void cleanup() {
        GLStateManager.glUseProgram(0);
        if (program != 0) GL20.glDeleteProgram(program);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        if (vbo != 0) GLStateManager.glDeleteBuffers(vbo);
        if (ebo != 0) GLStateManager.glDeleteBuffers(ebo);
        drainErrors();
    }

    @Test
    void drawArraysInstanced_quads_noInvalidEnum() {
        GLStateManager.glDrawArraysInstanced(GL11.GL_QUADS, 0, 4, 3);
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "GL_QUADS instanced array draw must be emulated, not rejected");
    }

    @Test
    void drawArraysInstanced_triangles_fastPathClean() {
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        GLStateManager.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, 3, 3);
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "non-quad instanced array draw passes through cleanly");
    }

    @Test
    void drawElementsInstanced_quads_noInvalidEnum() {
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GLStateManager.glDrawElementsInstanced(GL11.GL_QUADS, 4, GL11.GL_UNSIGNED_INT, 0L, 3);
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "GL_QUADS instanced element draw must be emulated, not rejected");
    }

    private static int buildProgram() {
        final int vs = compile(GL20.GL_VERTEX_SHADER, VS);
        final int fs = compile(GL20.GL_FRAGMENT_SHADER, FS);
        final int p = GL20.glCreateProgram();
        GL20.glAttachShader(p, vs);
        GL20.glAttachShader(p, fs);
        GL20.glLinkProgram(p);
        assertNotEquals(0, GL20.glGetProgrami(p, GL20.GL_LINK_STATUS), "test shader must link: " + GL20.glGetProgramInfoLog(p, 1024));
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        return p;
    }

    private static int compile(int type, String src) {
        final int s = GL20.glCreateShader(type);
        GL20.glShaderSource(s, src);
        GL20.glCompileShader(s);
        assertNotEquals(0, GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS), "test shader must compile: " + GL20.glGetShaderInfoLog(s, 1024));
        return s;
    }

    private static void drainErrors() {
        while (GL11.glGetError() != GL11.GL_NO_ERROR) { /* clear */ }
    }
}
