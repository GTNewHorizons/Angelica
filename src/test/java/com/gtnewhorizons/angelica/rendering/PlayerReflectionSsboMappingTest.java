package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.QuadConverter;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import net.minecraft.client.model.ModelBiped;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import static com.gtnewhorizons.angelica.rendering.PlayerReflectionLayoutTest.MODEL_SCALE;
import static com.gtnewhorizons.angelica.rendering.PlayerReflectionLayoutTest.emit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@GLCoreTest
class PlayerReflectionSsboMappingTest {

    private static final int SSBO_ENTRIES = 216;
    private static final int POSITION_STRIDE = 16;
    private static final int DATA_OFFSET = SSBO_ENTRIES * POSITION_STRIDE;
    private static final int DATA_STRIDE = 8;
    private static final int SSBO_BYTES = DATA_OFFSET + SSBO_ENTRIES * DATA_STRIDE;
    private static final int BINDING = 3;

    private static final int VISITED_BINDING = 4;
    private static final int VISITED_BYTES = PlayerReflectionCapture.VERTEX_COUNT * 4;

    private static final String VS = "#version 430 core\n"
        + "layout(location = 0) in vec3 a_Pos;\n"
        + "layout(location = 1) in vec2 a_Uv;\n"
        + "layout(std430, binding = " + BINDING + ") buffer playerVerticesBuffer {\n"
        + "    vec3 vertexPositions[" + SSBO_ENTRIES + "];\n"
        + "    vec2 vertexData[" + SSBO_ENTRIES + "];\n"
        + "} ssbo;\n"
        + "layout(std430, binding = " + VISITED_BINDING + ") buffer visitedBuffer {\n"
        + "    uint visited[" + PlayerReflectionCapture.VERTEX_COUNT + "];\n"
        + "};\n"
        + "void main() {\n"
        + "    visited[gl_VertexID] = 1u;\n"
        + "    if (gl_VertexID % 4 != 3) {\n"
        + "        int i = gl_VertexID - gl_VertexID / 4;\n"
        + "        ssbo.vertexPositions[i] = a_Pos;\n"
        + "        ssbo.vertexData[i] = a_Uv;\n"
        + "    }\n"
        + "    gl_Position = vec4(0.0, 0.0, 0.0, 1.0);\n"
        + "}\n";

    private static final String FS = "#version 430 core\n"
        + "out vec4 fragColor;\n"
        + "void main() { fragColor = vec4(1.0); }\n";

    private int program;
    private int vao;
    private int vbo;
    private int ssbo;
    private int visitedSsbo;

    @AfterEach
    void cleanup() {
        GLStateManager.glUseProgram(0);
        GLStateManager.glBindVertexArray(0);
        if (program != 0) GLStateManager.glDeleteProgram(program);
        if (vbo != 0) GLStateManager.glDeleteBuffers(vbo);
        if (ssbo != 0) GLStateManager.glDeleteBuffers(ssbo);
        if (visitedSsbo != 0) GLStateManager.glDeleteBuffers(visitedSsbo);
        if (vao != 0) GLStateManager.glDeleteVertexArrays(vao);
        program = vao = vbo = ssbo = visitedSsbo = 0;
    }

    private static int compile(int type, String source) {
        final int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        assertEquals(GL11.GL_TRUE, GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS), "shader failed to compile: " + GL20.glGetShaderInfoLog(shader, 4096));
        return shader;
    }

    private void buildProgram() {
        final int vs = compile(GL20.GL_VERTEX_SHADER, VS);
        final int fs = compile(GL20.GL_FRAGMENT_SHADER, FS);
        program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vs);
        GL20.glAttachShader(program, fs);
        GL20.glLinkProgram(program);
        assertEquals(GL11.GL_TRUE, GL20.glGetProgrami(program, GL20.GL_LINK_STATUS), "program failed to link: " + GL20.glGetProgramInfoLog(program, 4096));
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
    }

    private void uploadVertices(float[] xyzuv) {
        final FloatBuffer verts = BufferUtils.createFloatBuffer(xyzuv.length);
        verts.put(xyzuv).flip();

        vao = GLStateManager.glGenVertexArrays();
        vbo = GLStateManager.glGenBuffers();
        GLStateManager.glBindVertexArray(vao);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, verts, GL15.GL_STATIC_DRAW);
        final int stride = PlayerReflectionLayoutTest.FLOATS * 4;
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, 0L);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, stride, 12L);
        GL20.glEnableVertexAttribArray(1);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    private void allocateSsbo() {
        ssbo = GLStateManager.glGenBuffers();
        GLStateManager.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssbo);
        GLStateManager.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, ByteBuffer.allocateDirect(SSBO_BYTES).order(ByteOrder.nativeOrder()), GL15.GL_DYNAMIC_DRAW);
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, BINDING, ssbo);

        visitedSsbo = GLStateManager.glGenBuffers();
        GLStateManager.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, visitedSsbo);
        GLStateManager.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, ByteBuffer.allocateDirect(VISITED_BYTES).order(ByteOrder.nativeOrder()), GL15.GL_DYNAMIC_DRAW);
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, VISITED_BINDING, visitedSsbo);
        GLStateManager.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }

    private ByteBuffer readback(int buffer, int bytes) {
        GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT | GL42.GL_BUFFER_UPDATE_BARRIER_BIT);
        final ByteBuffer out = BufferUtils.createByteBuffer(bytes);
        GLStateManager.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, buffer);
        GLStateManager.glGetBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0L, out);
        GLStateManager.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
        return out;
    }

    @Test
    void quadIndexPatternProducesTheVertexIdsThePackIndexesBy() {
        assumeTrue(RenderSystem.supportsSSBO(), "SSBOs unsupported");

        final float[] xyzuv = emit(new ModelBiped(0.0f), MODEL_SCALE);
        buildProgram();
        uploadVertices(xyzuv);
        allocateSsbo();

        GLStateManager.glUseProgram(program);
        GLStateManager.glBindVertexArray(vao);
        QuadConverter.drawQuadsAsTriangles(0, PlayerReflectionCapture.VERTEX_COUNT);

        final ByteBuffer ssboData = readback(ssbo, SSBO_BYTES);
        final int floats = PlayerReflectionLayoutTest.FLOATS;

        for (int i = 0; i < SSBO_ENTRIES; i++) {
            final int vertexId = i + i / 3;
            final int src = vertexId * floats;
            assertEquals(xyzuv[src], ssboData.getFloat(i * POSITION_STRIDE), 1e-6f, "entry " + i + " x came from a vertex other than " + vertexId);
            assertEquals(xyzuv[src + 1], ssboData.getFloat(i * POSITION_STRIDE + 4), 1e-6f, "entry " + i + " y came from a vertex other than " + vertexId);
            assertEquals(xyzuv[src + 2], ssboData.getFloat(i * POSITION_STRIDE + 8), 1e-6f, "entry " + i + " z came from a vertex other than " + vertexId);
            assertEquals(xyzuv[src + 3], ssboData.getFloat(DATA_OFFSET + i * DATA_STRIDE), 1e-6f, "entry " + i + " u came from a vertex other than " + vertexId);
            assertEquals(xyzuv[src + 4], ssboData.getFloat(DATA_OFFSET + i * DATA_STRIDE + 4), 1e-6f, "entry " + i + " v came from a vertex other than " + vertexId);
        }
    }

    @Test
    void everyQuadCornerRunsTheVertexShader() {
        assumeTrue(RenderSystem.supportsSSBO(), "SSBOs unsupported");

        final float[] xyzuv = emit(new ModelBiped(0.0f), MODEL_SCALE);
        buildProgram();
        uploadVertices(xyzuv);
        allocateSsbo();

        GLStateManager.glUseProgram(program);
        GLStateManager.glBindVertexArray(vao);
        QuadConverter.drawQuadsAsTriangles(0, PlayerReflectionCapture.VERTEX_COUNT);

        final ByteBuffer visitedData = readback(visitedSsbo, VISITED_BYTES);
        for (int vertexId = 0; vertexId < PlayerReflectionCapture.VERTEX_COUNT; vertexId++) {
            assertEquals(1, visitedData.getInt(vertexId * 4), "vertex " + vertexId + " (corner " + (vertexId % 4) + " of quad " + (vertexId / 4) + ") never ran; the shared quad EBO must visit all four corners of every quad");
        }
    }
}
