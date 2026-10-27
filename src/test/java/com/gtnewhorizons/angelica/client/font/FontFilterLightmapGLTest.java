package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMBuffers;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMFramebuffer;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.glsm.GLSMPrograms;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.GLSMVertexInput;
import com.gtnewhorizons.angelica.glsm.GlTestSupport;
import com.gtnewhorizons.angelica.glsm.testutil.TestPaths;
import net.coderbot.iris.uniforms.builtin.BuiltinReplacementUniforms;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

@GLCoreTest
class FontFilterLightmapGLTest {

    private static final int LIGHTMAP_TEX_UNIT = 1;
    private static final int LIGHTMAP_SIZE = 16;
    private static final int VERTEX_SIZE = 36;

    private static final float BRIGHTNESS_X = 128.0f;
    private static final float BRIGHTNESS_Y = 64.0f;
    private static final int LIT_TEXEL_X = 8;
    private static final int LIT_TEXEL_Y = 4;

    private static final int TEXEL_R = 0x40;
    private static final int TEXEL_G = 0x80;
    private static final int TEXEL_B = 0xC0;

    private static final int VERTEX_COLOR = 0xFFFFFFFF;

    private int program;
    private int vao;
    private int vbo;
    private int lightmapTexture;

    @AfterEach
    void cleanup() {
        GLSMPrograms.glUseProgram(0);
        GLSMVertexInput.glBindVertexArray(0);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + LIGHTMAP_TEX_UNIT);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        if (lightmapTexture != 0) { GL11.glDeleteTextures(lightmapTexture); lightmapTexture = 0; }
        if (vbo != 0) { GLSMBuffers.glDeleteBuffers(vbo); vbo = 0; }
        if (vao != 0) { GLSMVertexInput.glDeleteVertexArrays(vao); vao = 0; }
        if (program != 0) { GLSMPrograms.glDeleteProgram(program); program = 0; }
    }

    private void buildProgram() {
        final String dir = "src/main/resources/assets/angelica/shaders/";
        program = GlTestSupport.program(TestPaths.readString(dir + "fontFilter.vsh"), TestPaths.readString(dir + "fontFilter.fsh"));
    }

    private void buildLightmapTexture() {
        final ByteBuffer pixels = BufferUtils.createByteBuffer(LIGHTMAP_SIZE * LIGHTMAP_SIZE * 4);
        for (int y = 0; y < LIGHTMAP_SIZE; y++) {
            for (int x = 0; x < LIGHTMAP_SIZE; x++) {
                final boolean lit = x == LIT_TEXEL_X && y == LIT_TEXEL_Y;
                final int base = (y * LIGHTMAP_SIZE + x) * 4;
                pixels.put(base, (byte) (lit ? TEXEL_R : 0));
                pixels.put(base + 1, (byte) (lit ? TEXEL_G : 0));
                pixels.put(base + 2, (byte) (lit ? TEXEL_B : 0));
                pixels.put(base + 3, (byte) 0xFF);
            }
        }

        lightmapTexture = GL11.glGenTextures();
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + LIGHTMAP_TEX_UNIT);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, lightmapTexture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, LIGHTMAP_SIZE, LIGHTMAP_SIZE, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
    }

    private void buildQuad() {
        vao = GLSMVertexInput.glGenVertexArrays();
        GLSMVertexInput.glBindVertexArray(vao);

        final ByteBuffer data = BufferUtils.createByteBuffer(3 * VERTEX_SIZE);
        putVertex(data, -0.9f, -0.9f);
        putVertex(data, 0.9f, -0.9f);
        putVertex(data, 0.0f, 0.9f);
        data.flip();

        vbo = GLSMBuffers.glGenBuffers();
        GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GLSMBuffers.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STATIC_DRAW);
        GLSMVertexInput.glEnableVertexAttribArray(0);
        GLSMVertexInput.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, VERTEX_SIZE, 0L);
        GLSMVertexInput.glEnableVertexAttribArray(1);
        GLSMVertexInput.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, VERTEX_SIZE, 8L);
        GLSMVertexInput.glEnableVertexAttribArray(2);
        GLSMVertexInput.glVertexAttribPointer(2, 4, GL11.GL_UNSIGNED_BYTE, true, VERTEX_SIZE, 16L);
        GLSMVertexInput.glEnableVertexAttribArray(3);
        GLSMVertexInput.glVertexAttribPointer(3, 4, GL11.GL_FLOAT, false, VERTEX_SIZE, 20L);
    }

    private static void putVertex(ByteBuffer buf, float x, float y) {
        final int base = buf.position();
        buf.putFloat(base, x).putFloat(base + 4, y);
        buf.putFloat(base + 8, 0.0f).putFloat(base + 12, 0.0f);
        buf.putInt(base + 16, VERTEX_COLOR);
        buf.putFloat(base + 20, 0.0f).putFloat(base + 24, 1.0f)
           .putFloat(base + 28, 0.0f).putFloat(base + 32, 1.0f);
        buf.position(base + VERTEX_SIZE);
    }

    private static void applyVanillaLightmapMatrix() {
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + LIGHTMAP_TEX_UNIT);
        GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
        GLSMMatrices.glLoadIdentity();
        GLSMMatrices.glScalef(0.00390625f, 0.00390625f, 0.00390625f);
        GLSMMatrices.glTranslatef(8.0f, 8.0f, 8.0f);
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
    }

    private float[] draw(boolean lightmapEnabled) {
        buildProgram();
        buildLightmapTexture();
        buildQuad();
        applyVanillaLightmapMatrix();

        assertEquals(BuiltinReplacementUniforms.lightmapTextureMatrix, new Matrix4f(GLSMTextures.getTextures().getTextureUnitMatrix(LIGHTMAP_TEX_UNIT)), "EntityRenderer#enableLightmap must reproduce the lightmap texture matrix Iris documents");

        final Vector4f uv = new Vector4f(BRIGHTNESS_X, BRIGHTNESS_Y, 0.0f, 1.0f).mul(GLSMTextures.getTextures().getTextureUnitMatrix(LIGHTMAP_TEX_UNIT));

        GLSMRasterizer.glViewport(0, 0, 800, 600);
        GLSMDepthStencilAlpha.disableDepthTest();
        GLSMRasterizer.disableCull();
        GLSMBlend.disableBlend();

        GLSMPrograms.glUseProgram(program);
        GLSMPrograms.glUniform1i(GLSMPrograms.glGetUniformLocation(program, "sampler"), 0);
        GLSMPrograms.glUniform1i(GLSMPrograms.glGetUniformLocation(program, "lightmap"), LIGHTMAP_TEX_UNIT);
        GLSMPrograms.glUniform1i(GLSMPrograms.glGetUniformLocation(program, "aaMode"), 0);
        GLSMPrograms.glUniform1f(GLSMPrograms.glGetUniformLocation(program, "strength"), 1.0f);
        GLSMPrograms.glUniform1f(GLSMPrograms.glGetUniformLocation(program, "alphaTestRef"), 0.0f);
        GLSMPrograms.glUniform3f(GLSMPrograms.glGetUniformLocation(program, "u_Lightmap"), uv.x, uv.y, lightmapEnabled ? 1.0f : 0.0f);

        GLSMPrograms.glUniformMatrix4(GLSMPrograms.glGetUniformLocation(program, "u_MVPMatrix"), false, new Matrix4f().get(BufferUtils.createFloatBuffer(16)));

        GLSMBlend.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GLSMDraws.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "fontFilter draw must not raise a GL error");

        final ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        GL11.glReadPixels(400, 200, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
        return new float[] {
            (pixel.get(0) & 0xFF) / 255.0f,
            (pixel.get(1) & 0xFF) / 255.0f,
            (pixel.get(2) & 0xFF) / 255.0f };
    }

    @Test
    void enabledLightmapModulatesTheGlyphColor() {
        final float[] actual = draw(true);
        assertEquals(TEXEL_R / 255.0f, actual[0], 2.0f / 255.0f, "red must come from lightmap texel");
        assertEquals(TEXEL_G / 255.0f, actual[1], 2.0f / 255.0f, "green must come from lightmap texel");
        assertEquals(TEXEL_B / 255.0f, actual[2], 2.0f / 255.0f, "blue must come from lightmap texel");
    }

    @Test
    void disabledLightmapLeavesTheGlyphColorAlone() {
        final float[] actual = draw(false);
        assertEquals(1.0f, actual[0], 2.0f / 255.0f, "u_Lightmap.z = 0 must leave the colour unmodulated");
        assertEquals(1.0f, actual[1], 2.0f / 255.0f, "u_Lightmap.z = 0 must leave the colour unmodulated");
        assertEquals(1.0f, actual[2], 2.0f / 255.0f, "u_Lightmap.z = 0 must leave the colour unmodulated");
    }
}
