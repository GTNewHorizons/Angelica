package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.function.IntConsumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@GLCoreTest
class InstancedQuadDrawGLTest {

    private static final String VS = "#version 330 core\nlayout(location=0) in vec2 aPos;\nvoid main(){ gl_Position = vec4(aPos, 0.0, 1.0); }\n";
    private static final String FS = "#version 330 core\nout vec4 o;\nvoid main(){ o = vec4(1.0); }\n";

    private int program;
    private int vbo;
    private int ebo;

    @BeforeEach
    void setup() {
        program = GlTestSupport.program(VS, FS);
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
    }

    @AfterEach
    void cleanup() {
        GLStateManager.glUseProgram(0);
        if (program != 0) GLStateManager.glDeleteProgram(program);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        if (vbo != 0) GLStateManager.glDeleteBuffers(vbo);
        if (ebo != 0) GLStateManager.glDeleteBuffers(ebo);
    }

    static Stream<Arguments> draws() {
        return Stream.of(
            draw("GL_QUADS instanced array draw must be emulated, not rejected", ebo -> GLStateManager.glDrawArraysInstanced(GL11.GL_QUADS, 0, 4, 3)),
            draw("non-quad instanced array draw passes through cleanly", ebo -> {
                GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
                GLStateManager.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, 3, 3);
            }),
            draw("GL_QUADS instanced element draw must be emulated, not rejected", ebo -> {
                GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
                GLStateManager.glDrawElementsInstanced(GL11.GL_QUADS, 4, GL11.GL_UNSIGNED_INT, 0L, 3);
            }));
    }

    private static Arguments draw(String name, IntConsumer body) {
        return Arguments.of(name, body);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("draws")
    void instancedDrawRaisesNoGlError(String name, IntConsumer draw) {
        draw.accept(ebo);
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), name);
    }
}
