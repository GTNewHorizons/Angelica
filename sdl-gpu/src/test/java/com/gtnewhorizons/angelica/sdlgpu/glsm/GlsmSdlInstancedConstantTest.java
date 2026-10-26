package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLSMBuffers;
import com.gtnewhorizons.angelica.glsm.GLSMCurrentAttribs;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMPrograms;
import com.gtnewhorizons.angelica.glsm.GLSMVertexInput;
import com.gtnewhorizons.angelica.glsm.GlTestSupport;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.sdlgpu.SDLGPUGate;
import com.gtnewhorizons.angelica.sdlgpu.device.Device;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Metal steps a per-instance constant binding by 16 bytes per instance.
 */
@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlInstancedConstantTest {

    private static final int INSTANCES = 4;
    private static final int TINT_LOCATION = 3;

    private static final String VERT = """
        #version 330 core
        layout(location = 0) in vec2 a_Position;
        layout(location = 3) in vec4 a_Tint;
        out vec4 v_Tint;
        void main() {
            gl_Position = vec4(-1.0 + 0.5 * (float(gl_InstanceID) + a_Position.x), a_Position.y, 0.0, 1.0);
            v_Tint = a_Tint;
        }
        """;

    private static final String FRAG = """
        #version 330 core
        in vec4 v_Tint;
        out vec4 fragColor;
        void main() {
            fragColor = v_Tint;
        }
        """;

    @Test
    void everyInstanceReadsTheConstantWhenInstancesStepTheBinding() {
        final Device device = SDLGPUGate.device();
        final String driver = device.getDriverName();
        Reflect.set(device, "driverName", "metal");
        final int program = GlTestSupport.program(VERT, FRAG);

        final int vao = GLSMVertexInput.glGenVertexArrays();
        final int vbo = GLSMBuffers.glGenBuffers();
        final FloatBuffer quad = MemoryUtil.memAllocFloat(12);
        try {
            quad.put(new float[] { 0, -1, 1, -1, 1, 1, 0, -1, 1, 1, 0, 1 }).flip();
            GLSMVertexInput.glBindVertexArray(vao);
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GLSMBuffers.glBufferData(GL15.GL_ARRAY_BUFFER, quad, GL15.GL_STATIC_DRAW);
            GLSMVertexInput.glEnableVertexAttribArray(0);
            GLSMVertexInput.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 8, 0L);
            GLSMVertexInput.glDisableVertexAttribArray(TINT_LOCATION);
            GLSMCurrentAttribs.glVertexAttrib4f(TINT_LOCATION, 0.2f, 0.4f, 0.6f, 1.0f);

            GLSMPrograms.glUseProgram(program);
            GLSMDraws.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, 6, INSTANCES);
            GLSMPrograms.glUseProgram(0);

            final int[] row = GlsmSdlHeadlessRig.readTarget(0, GlsmSdlHeadlessRig.SIZE / 2, GlsmSdlHeadlessRig.SIZE, 1);
            final int expected = 0xFF000000 | (51 << 16) | (102 << 8) | 153;
            for (int instance = 0; instance < INSTANCES; instance++) {
                final int x = (2 * instance + 1) * GlsmSdlHeadlessRig.SIZE / (2 * INSTANCES);
                final int got = row[x];
                assertEquals(Integer.toHexString(expected), Integer.toHexString(got), "instance " + instance);
            }
        } finally {
            Reflect.set(device, "driverName", driver);
            MemoryUtil.memFree(quad);
            GLSMVertexInput.glBindVertexArray(0);
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GLSMBuffers.glDeleteBuffers(vbo);
            GLSMVertexInput.glDeleteVertexArrays(vao);
            GLSMPrograms.glDeleteProgram(program);
        }
    }
}
