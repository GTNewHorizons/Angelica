package com.gtnewhorizons.angelica.rendering.translucency;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMFramebuffer;
import com.gtnewhorizons.angelica.glsm.GLSMPrograms;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.GLSMVertexInput;
import com.gtnewhorizons.angelica.glsm.GlTestSupport;
import net.coderbot.iris.gl.framebuffer.GlFramebuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

@GLCoreTest
class TranslucencyCompositeGLTest {

    private static final int WIDTH = 4;
    private static final String QUAD_VS = """
        #version 330 core
        uniform float u_Depth;
        void main() {
            vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
            gl_Position = vec4(p * 2.0 - 1.0, u_Depth * 2.0 - 1.0, 1.0);
        }
        """;
    private static final String QUAD_FS = """
        #version 330 core
        uniform vec4 u_Color;
        out vec4 color;
        void main() { color = u_Color; }
        """;

    private int colorTex;
    private int depthTex;
    private GlFramebuffer main;
    private int quadProgram;
    private int vao;
    private TranslucencyLayers layers;

    @BeforeEach
    void setUp() {
        colorTex = texture(GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE);
        depthTex = texture(GL30.GL_DEPTH24_STENCIL8, GL30.GL_DEPTH_STENCIL, GL30.GL_UNSIGNED_INT_24_8);
        main = new GlFramebuffer();
        main.addColorAttachment(0, colorTex);
        main.addDepthAttachment(depthTex);
        main.drawBuffers(new int[] { 0 });
        main.readBuffer(0);
        main.bind();
        GLSMRasterizer.glViewport(0, 0, WIDTH, 1);
        quadProgram = GlTestSupport.program(QUAD_VS, QUAD_FS);
        vao = GLSMVertexInput.glGenVertexArrays();
        layers = new TranslucencyLayers(new TranslucencyLayers.MainTarget() {
            @Override public int framebuffer() { return main.getId(); }
            @Override public int depthTexture() { return depthTex; }
            @Override public int width() { return WIDTH; }
            @Override public int height() { return 1; }
            @Override public int depthVersion() { return 0; }
        });
        GLSMDepthStencilAlpha.enableDepthTest();
        GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_LEQUAL);
        GLSMDepthStencilAlpha.glDepthMask(true);
        GLSMBlend.glClearColor(1f, 0f, 0f, 1f);
        GLSMDepthStencilAlpha.glClearDepth(0.9);
        GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    }

    @AfterEach
    void tearDown() {
        layers.destroy();
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        main.destroy();
        GLSMTextures.glDeleteTextures(colorTex);
        GLSMTextures.glDeleteTextures(depthTex);
        GLSMPrograms.glUseProgram(0);
        GLSMPrograms.glDeleteProgram(quadProgram);
        GLSMVertexInput.glDeleteVertexArrays(vao);
        GLSMBlend.disableBlend();
        GLSMDepthStencilAlpha.disableDepthTest();
        GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_LESS);
        GLSMBlend.glClearColor(0f, 0f, 0f, 0f);
        GLSMDepthStencilAlpha.glClearDepth(1.0);
    }

    private static int texture(int internalFormat, int format, int type) {
        final int tex = GLSMTextures.glGenTextures();
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GLSMTextures.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, WIDTH, 1, 0, format, type, (ByteBuffer) null);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return tex;
    }

    private void drawInto(TranslucentLayer layer, int column, float depth, float r, float g, float b, float a, int srcRgb, int dstRgb) {
        layers.begin(layer);
        GLSMBlend.enableBlend();
        GLSMBlend.glBlendFunc(srcRgb, dstRgb);
        draw(column, depth, r, g, b, a);
        GLSMBlend.disableBlend();
        layers.end();
    }

    private void draw(int column, float depth, float r, float g, float b, float a) {
        GLSMRasterizer.glViewport(column, 0, 1, 1);
        GLSMPrograms.glUseProgram(quadProgram);
        GLSMPrograms.glUniform1f(GLSMPrograms.glGetUniformLocation(quadProgram, "u_Depth"), depth);
        GLSMPrograms.glUniform4f(GLSMPrograms.glGetUniformLocation(quadProgram, "u_Color"), r, g, b, a);
        GLSMVertexInput.glBindVertexArray(vao);
        GLSMDraws.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        GLSMVertexInput.glBindVertexArray(0);
        GLSMPrograms.glUseProgram(0);
        GLSMRasterizer.glViewport(0, 0, WIDTH, 1);
    }

    private static void assertColumn(ByteBuffer rgba, int column, int r, int g, int b) {
        final int at = column * 4;
        assertEquals(r, rgba.get(at) & 0xFF, 2, "column " + column + " red");
        assertEquals(g, rgba.get(at + 1) & 0xFF, 2, "column " + column + " green");
        assertEquals(b, rgba.get(at + 2) & 0xFF, 2, "column " + column + " blue");
    }

    private static ByteBuffer readColor() {
        final ByteBuffer rgba = BufferUtils.createByteBuffer(WIDTH * 4);
        GLSMFramebuffer.glReadPixels(0, 0, WIDTH, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, rgba);
        return rgba;
    }

    private static FloatBuffer readDepth() {
        final FloatBuffer depth = BufferUtils.createFloatBuffer(WIDTH);
        GLSMFramebuffer.glReadPixels(0, 0, WIDTH, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depth);
        return depth;
    }

    @Test
    void compositesLayersByDepthAndWritesNearestDepth() {
        layers.beginFrame(true);
        drawInto(TranslucentLayer.TERRAIN, 0, 0.3f, 0f, 1f, 0f, 0.5f, GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        drawInto(TranslucentLayer.PARTICLES, 0, 0.5f, 0f, 0f, 1f, 0.5f, GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        drawInto(TranslucentLayer.TERRAIN, 1, 0.95f, 0f, 0f, 1f, 0.5f, GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        drawInto(TranslucentLayer.CLOUDS, 2, 0.5f, 0f, 0.4f, 0f, 0f, GL11.GL_ONE, GL11.GL_ONE);
        drawInto(TranslucentLayer.TERRAIN, 3, 0.5f, 0f, 1f, 0f, 0.5f, GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        draw(3, 0.2f, 0f, 0f, 1f, 1f);
        layers.composite();

        final ByteBuffer color = readColor();
        assertColumn(color, 0, 64, 128, 64);
        assertColumn(color, 1, 255, 0, 0);
        assertColumn(color, 2, 255, 102, 0);
        assertColumn(color, 3, 0, 0, 255);
        final FloatBuffer depth = readDepth();
        final float[] expected = { 0.3f, 0.9f, 0.5f, 0.2f };
        for (int i = 0; i < WIDTH; i++) assertEquals(expected[i], depth.get(i), 1e-4f, "column " + i + " depth");

        layers.composite();
        final ByteBuffer again = readColor();
        for (int i = 0; i < WIDTH * 4; i++) assertEquals(color.get(i), again.get(i), "a composite with no new layer draws changes nothing");
    }
}
