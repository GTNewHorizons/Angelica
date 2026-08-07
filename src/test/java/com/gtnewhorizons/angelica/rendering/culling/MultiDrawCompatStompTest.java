package com.gtnewhorizons.angelica.rendering.culling;

import com.gtnewhorizons.angelica.glsm.CompatUniformManager;
import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL40;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@GLCoreTest
class MultiDrawCompatStompTest {

    private static final Matrix4f SENTINEL = new Matrix4f().scaling(2f, 3f, 4f).setTranslation(5f, 6f, 7f);
    private static final Matrix4f LEGACY_CAMERA = new Matrix4f().rotationY(0.7f).setTranslation(-11f, -22f, -33f);

    private static final String IRIS_MVM_VS = "#version 330 core\n"
        + "uniform mat4 iris_ModelViewMatrix;\n"
        + "layout(location = 0) in vec3 a_Pos;\n"
        + "void main() { gl_Position = iris_ModelViewMatrix * vec4(a_Pos, 1.0); }\n";

    private int vsh;
    private int fsh;
    private int program;
    private int vao;
    private int indirectBuffer;
    private int elementBuffer;
    private int location;

    private static void putMatrix(Matrix4f m, FloatBuffer buf) {
        buf.clear();
        m.get(buf);
        buf.position(0).limit(16);
    }

    private static Matrix4f readUniform(int program, int location) {
        final FloatBuffer buf = BufferUtils.createFloatBuffer(16);
        GLStateManager.glGetUniform(program, location, buf);
        return new Matrix4f(buf);
    }

    @BeforeEach
    void armSentinel() {
        vsh = IndirectCullDrawParityTest.compileShader(GL20.GL_VERTEX_SHADER, IRIS_MVM_VS);
        fsh = IndirectCullDrawParityTest.compileShader(GL20.GL_FRAGMENT_SHADER, IndirectCullDrawParityTest.FLAT_FS);
        program = GLStateManager.glCreateProgram();
        GLStateManager.glAttachShader(program, vsh);
        GLStateManager.glAttachShader(program, fsh);
        GLStateManager.glLinkProgram(program);
        assertEquals(GL11.GL_TRUE, GLStateManager.glGetProgrami(program, GL20.GL_LINK_STATUS), () -> "program failed to link:\n" + GLStateManager.glGetProgramInfoLog(program, 4096));
        assertTrue(CompatUniformManager.hasProgram(program), "program with iris_ModelViewMatrix was not registered by CompatUniformManager at link");
        location = GLStateManager.glGetUniformLocation(program, "iris_ModelViewMatrix");
        assertTrue(location >= 0, "iris_ModelViewMatrix location not found");

        vao = GLStateManager.glGenVertexArrays();
        GLStateManager.glBindVertexArray(vao);
        elementBuffer = GLStateManager.glGenBuffers();
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, elementBuffer);
        GLStateManager.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, BufferUtils.createByteBuffer(12), GL15.GL_STATIC_DRAW);
        indirectBuffer = GLStateManager.glGenBuffers();
        GLStateManager.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER, indirectBuffer);
        final ByteBuffer zeroCommand = BufferUtils.createByteBuffer(20).order(ByteOrder.nativeOrder());
        GLStateManager.glBufferData(GL40.GL_DRAW_INDIRECT_BUFFER, zeroCommand, GL15.GL_STATIC_DRAW);

        final FloatBuffer matBuf = BufferUtils.createFloatBuffer(16);
        GLStateManager.glUseProgram(program);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        putMatrix(LEGACY_CAMERA, matBuf);
        GLStateManager.glLoadMatrix(matBuf);
        putMatrix(SENTINEL, matBuf);
        GLStateManager.glUniformMatrix4(location, false, matBuf);
        assertEquals(SENTINEL, readUniform(program, location), "sentinel upload did not take");
    }

    @AfterEach
    void tearDown() {
        GLStateManager.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER, 0);
        GLStateManager.glBindVertexArray(0);
        GLStateManager.glUseProgram(0);
        if (vao != 0) GLStateManager.glDeleteVertexArrays(vao);
        if (indirectBuffer != 0) GLStateManager.glDeleteBuffers(indirectBuffer);
        if (elementBuffer != 0) GLStateManager.glDeleteBuffers(elementBuffer);
        GLStateManager.glDeleteProgram(program);
        GLStateManager.glDeleteShader(vsh);
        GLStateManager.glDeleteShader(fsh);
        final FloatBuffer identity = BufferUtils.createFloatBuffer(16);
        new Matrix4f().get(identity);
        GLStateManager.glLoadMatrix(identity);
    }

    @Test
    void multiDrawIndirectDoesNotStompIrisMatrix() {
        assumeTrue(RenderSystem.supportsMultiDrawIndirect(), "glMultiDrawElementsIndirect needs GL 4.3 / ARB_multi_draw_indirect; macOS core profile caps at 4.1");

        GLStateManager.glMultiDrawElementsIndirect(GL11.GL_TRIANGLES, GL11.GL_UNSIGNED_INT, 0L, 1, 0);
        assertEquals(SENTINEL, readUniform(program, location), "glMultiDrawElementsIndirect stomped iris_ModelViewMatrix from the legacy matrix stack; celeritas-managed programs own this uniform and the shadow pass renders with the camera view if it is overwritten");
    }

    @Test
    void baseVertexDrawsDoNotStompIrisMatrixButRegularDrawDoes() {
        GLStateManager.glDrawElementsBaseVertex(GL11.GL_TRIANGLES, 0, GL11.GL_UNSIGNED_INT, 0L, 0);
        assertEquals(SENTINEL, readUniform(program, location), "glDrawElementsBaseVertex stomped iris_ModelViewMatrix; the Individual draw emitter renders shadows with the camera view if it is overwritten");

        GLStateManager.glDrawRangeElementsBaseVertex(GL11.GL_TRIANGLES, 0, 0, 0, GL11.GL_UNSIGNED_INT, 0L, 0);
        assertEquals(SENTINEL, readUniform(program, location), "glDrawRangeElementsBaseVertex stomped iris_ModelViewMatrix");

        GLStateManager.glDrawElements(GL11.GL_TRIANGLES, 0, GL11.GL_UNSIGNED_INT, 0L);
        final Matrix4f afterRegularDraw = readUniform(program, location);
        assertNotEquals(SENTINEL, afterRegularDraw, "regular glDrawElements no longer refreshes compat uniforms; entity/hand programs rely on the legacy-stack upload");
        assertTrue(afterRegularDraw.equals(LEGACY_CAMERA, 1e-6f), "regular draw refreshed iris_ModelViewMatrix but not from the legacy modelview stack");
    }
}
