package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLSMBuffers;
import com.gtnewhorizons.angelica.glsm.GLSMPrograms;
import com.gtnewhorizons.angelica.glsm.GlTestSupport;
import com.gtnewhorizons.angelica.glsm.ffp.LogicOpFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL31;

import java.nio.FloatBuffer;

import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.SIZE;
import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.describe;
import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.pixelAt;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlExternalUboPushTest {

    private static final String VERT = """
        #version 330 core
        layout(location = 0) in vec3 a_Position;
        void main() {
            gl_Position = vec4(a_Position, 1.0);
        }
        """;

    private static final String FRAG = """
        #version 330 core
        layout(std140) uniform Tint {
            vec4 color;
        };
        out vec4 fragColor;
        void main() {
            fragColor = color;
        }
        """;

    private static final int BINDING = 2;
    private static final int[] UNUSED_COLOR = LogicOpFixture.SOURCES[0];

    private int program;
    private int ubo;

    @AfterEach
    void cleanup() {
        GLSMPrograms.glUseProgram(0);
        GLSMBuffers.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, BINDING, 0);
        if (ubo != 0) GLSMBuffers.glDeleteBuffers(ubo);
        if (program != 0) GLSMPrograms.glDeleteProgram(program);
        LogicOpFixture.deleteResources();
    }

    @Test
    void subDataBetweenDrawsReachesTheSecondDraw() {
        program = GlTestSupport.program(VERT, FRAG);
        GLSMPrograms.glUniformBlockBinding(program, GLSMPrograms.glGetUniformBlockIndex(program, "Tint"), BINDING);

        ubo = GLSMBuffers.glGenBuffers();
        GLSMBuffers.glBindBuffer(GL31.GL_UNIFORM_BUFFER, ubo);
        GLSMBuffers.glBufferData(GL31.GL_UNIFORM_BUFFER, color(1f, 0f, 0f), GL15.GL_DYNAMIC_DRAW);
        GLSMBuffers.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, BINDING, ubo);

        GLSMPrograms.glUseProgram(program);
        LogicOpFixture.quad(UNUSED_COLOR);

        GLSMBuffers.glBindBuffer(GL31.GL_UNIFORM_BUFFER, ubo);
        GLSMBuffers.glBufferSubData(GL31.GL_UNIFORM_BUFFER, 0, color(0f, 1f, 0f));
        LogicOpFixture.quad(UNUSED_COLOR);

        final int got = pixelAt(GlsmSdlHeadlessRig.readTarget(), SIZE, SIZE / 2, SIZE / 2);
        assertEquals(0xFF00FF00, got, () -> "second draw must see the sub-data written after the first: " + describe(got));
    }

    private static FloatBuffer color(float r, float g, float b) {
        final FloatBuffer buf = BufferUtils.createFloatBuffer(4);
        buf.put(r).put(g).put(b).put(1f).flip();
        return buf;
    }
}
