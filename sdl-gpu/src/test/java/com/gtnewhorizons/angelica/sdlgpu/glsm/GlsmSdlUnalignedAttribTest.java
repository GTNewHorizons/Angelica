package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlUnalignedAttribTest {

    private static final int STRIDE = 24;
    private static final int FACTOR = 204;
    private static final int DECOY = 64;

    private static final String VERT = """
        #version 330 core
        layout(location = 0) in vec3 a_Position;
        layout(location = 5) in float a_Factor;
        out float v_Factor;
        void main() {
            gl_Position = vec4(a_Position, 1.0);
            v_Factor = a_Factor;
        }
        """;

    private static final String FRAG = """
        #version 330 core
        in float v_Factor;
        out vec4 fragColor;
        void main() {
            fragColor = vec4(v_Factor, v_Factor, v_Factor, 1.0);
        }
        """;

    private static int program;

    @BeforeAll
    static void boot() {
        GlsmSdlHeadlessRig.boot();
    }

    private static int buildProgram() {
        if (program != 0) return program;
        final int vs = GLStateManager.glCreateShader(GL20.GL_VERTEX_SHADER);
        GLStateManager.glShaderSource(vs, VERT);
        GLStateManager.glCompileShader(vs);
        assertTrue(GLStateManager.glGetShaderi(vs, GL20.GL_COMPILE_STATUS) != 0,
            () -> "vertex shader: " + GLStateManager.glGetShaderInfoLog(vs));

        final int fs = GLStateManager.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GLStateManager.glShaderSource(fs, FRAG);
        GLStateManager.glCompileShader(fs);
        assertTrue(GLStateManager.glGetShaderi(fs, GL20.GL_COMPILE_STATUS) != 0,
            () -> "fragment shader: " + GLStateManager.glGetShaderInfoLog(fs));

        final int p = GLStateManager.glCreateProgram();
        GLStateManager.glAttachShader(p, vs);
        GLStateManager.glAttachShader(p, fs);
        GLStateManager.glLinkProgram(p);
        assertTrue(GLStateManager.glGetProgrami(p, GL20.GL_LINK_STATUS) != 0, "program did not link");
        program = p;
        return p;
    }

    private static int redChannelForFactorAt(int factorOffset, int decoyOffset) {
        final int p = buildProgram();
        final int vao = GLStateManager.glGenVertexArrays();
        GLStateManager.glBindVertexArray(vao);

        final int vbo = GLStateManager.glGenBuffers();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        final ByteBuffer data = MemoryUtil.memAlloc(3 * STRIDE);
        try {
            for (int i = 0; i < 3 * STRIDE; i++) data.put(i, (byte) 0);
            final float[][] corners = {{-1.0f, -1.0f}, {3.0f, -1.0f}, {-1.0f, 3.0f}};
            for (int v = 0; v < 3; v++) {
                final int base = v * STRIDE;
                data.putFloat(base, corners[v][0]);
                data.putFloat(base + 4, corners[v][1]);
                data.putFloat(base + 8, 0.0f);
                if (decoyOffset >= 0) data.put(base + decoyOffset, (byte) DECOY);
                data.put(base + factorOffset, (byte) FACTOR);
            }
            data.position(0).limit(3 * STRIDE);
            GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STATIC_DRAW);
        } finally {
            MemoryUtil.memFree(data);
        }

        GLStateManager.glEnableVertexAttribArray(0);
        GLStateManager.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, STRIDE, 0L);
        GLStateManager.glEnableVertexAttribArray(5);
        GLStateManager.glVertexAttribPointer(5, 1, GL11.GL_UNSIGNED_BYTE, true, STRIDE, factorOffset);

        GLStateManager.glUseProgram(p);
        try {
            GLStateManager.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        } finally {
            GLStateManager.glUseProgram(0);
        }

        final int[] pixels = GlsmSdlHeadlessRig.readTarget(GlsmSdlHeadlessRig.SIZE / 2, GlsmSdlHeadlessRig.SIZE / 2, 1, 1);
        GLStateManager.glBindVertexArray(0);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        return (pixels[0] >> 16) & 0xFF;
    }

    @Test
    void byteAttribAtAnAlignedOffsetReadsItsOwnByte() {
        assertEquals(FACTOR, redChannelForFactorAt(20, -1), 1);
    }

    @Test
    void byteAttribAtAnUnalignedOffsetReadsItsOwnByte() {
        assertEquals(FACTOR, redChannelForFactorAt(23, 20), 1);
    }

    @Test
    void byteAttribAtEveryMisalignmentReadsItsOwnByte() {
        for (int offset = 20; offset <= 23; offset++) {
            final int decoy = offset == 20 ? -1 : 20;
            final int got = redChannelForFactorAt(offset, decoy);
            assertEquals(FACTOR, got, 1, "factor byte at offset " + offset + " read back as " + got);
        }
    }
}
