package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMBuffers;
import com.gtnewhorizons.angelica.glsm.GLSMCurrentAttribs;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMEnable;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLSMTexEnvGen;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.GLSMVertexInput;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.function.IntBinaryOperator;

import static com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags.TEXTURE_BIT;
import static org.joml.Math.clamp;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
class TextureSupersamplingGLTest {
    private static final int SIZE = 200;
    private int texture;
    private int vao;
    private int vbo;

    private static int dotColor(int x, int y) {
        final boolean dot = (x == 3 || x == 6 || x == 9 || x == 12)
            && (y == 3 || y == 6 || y == 9 || y == 12)
            && (x == 3 || x == 12 || y == 3 || y == 12);
        return dot ? 0x444444FF : 0xFFFFFFFF;
    }

    private static int uploadTexture(int spriteSize, int gutter, IntBinaryOperator color) {
        final int atlasSize = Integer.highestOneBit(spriteSize + 2 * gutter) << 1;
        final ByteBuffer pixels = BufferUtils.createByteBuffer(atlasSize * atlasSize * 4);
        for (int y = 0; y < atlasSize; y++) {
            for (int x = 0; x < atlasSize; x++) {
                final int rgba = x < spriteSize + 2 * gutter && y < spriteSize + 2 * gutter
                    ? color.applyAsInt(clamp(0, spriteSize - 1, x - gutter), clamp(0, spriteSize - 1, y - gutter))
                    : 0xFF00FFFF;
                pixels.put((byte) (rgba >>> 24)).put((byte) (rgba >>> 16)).put((byte) (rgba >>> 8)).put((byte) rgba);
            }
        }
        pixels.flip();
        final int texture = GLSMTextures.glGenTextures();
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        GLSMTextures.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, atlasSize, atlasSize, 0,
            GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        return texture;
    }

    private static void vertex(ByteBuffer vertices, float x, float z, float u, float v) {
        vertices.putFloat(x).putFloat(0.5f).putFloat(z).putFloat(u).putFloat(v);
    }

    private static int countDotRegions(int[] pixels) {
        final boolean[] remaining = new boolean[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            final int gray = pixels[i] & 255;
            remaining[i] = gray > 0 && gray < 250
                && ((pixels[i] >> 8) & 255) == gray && ((pixels[i] >> 16) & 255) == gray;
        }
        int regions = 0;
        final ArrayDeque<Integer> pending = new ArrayDeque<>();
        for (int i = 0; i < remaining.length; i++) {
            if (!remaining[i]) continue;
            regions++;
            remaining[i] = false;
            pending.add(i);
            while (!pending.isEmpty()) {
                final int pixel = pending.removeFirst();
                for (int neighbor : new int[]{pixel - 1, pixel + 1, pixel - SIZE, pixel + SIZE}) {
                    if (neighbor >= 0 && neighbor < remaining.length && remaining[neighbor]) {
                        remaining[neighbor] = false;
                        pending.add(neighbor);
                    }
                }
            }
        }
        return regions;
    }

    @BeforeEach
    void setup() {
        FfpFixture.resetFfpState();
        GLSMTextures.setTextureSupersamplingSamples(1);
        GLSMEnable.glDisable(GL11.GL_LIGHTING);
        GLSMEnable.glDisable(GL11.GL_FOG);
        GLSMCurrentAttribs.glColor4f(1, 1, 1, 1);
        GLSMEnable.glEnable(GL11.GL_TEXTURE_2D);
        GLSMTexEnvGen.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
        GLSMRasterizer.glViewport(0, 0, SIZE, SIZE);
        GLSMMatrices.glMatrixMode(GL11.GL_PROJECTION);
        GLSMMatrices.glLoadIdentity();
        GLSMMatrices.glOrtho(0, SIZE, SIZE, 0, -100, 100);
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        FfpFixture.activateFfp();
    }

    @AfterEach
    void cleanup() {
        GLSMTextures.setTextureSupersamplingSamples(1);
        FfpFixture.resetFfpState();
        if (texture != 0) GLSMTextures.glDeleteTextures(texture);
        GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        if (vbo != 0) GLSMBuffers.glDeleteBuffers(vbo);
        if (vao != 0) GLSMVertexInput.glDeleteVertexArrays(vao);
        GLSMMatrices.glMatrixMode(GL11.GL_PROJECTION);
        GLSMMatrices.glLoadIdentity();
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMMatrices.glLoadIdentity();
        GLSMRasterizer.glViewport(0, 0, 800, 600);
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 16})
    void recoversTwelveDotsAtScaleThreeAndRestoresNearestAfterward(int samples) {
        createDotAtlas();

        final int[] nearest = drawTop(3);
        assertEquals(0, countDotRegions(nearest));
        final int previous = GLSMTextures.setTextureSupersamplingSamples(samples);
        assertEquals(12, countDotRegions(drawTop(3)));
        GLSMTextures.setTextureSupersamplingSamples(previous);
        assertArrayEquals(nearest, drawTop(3));
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 16})
    void highResolutionCheckerboardsStaySmoothWithTheDefaultAtlasGutter(int samples) {
        createAtlas(256, 16, (x, y) -> ((x ^ y) & 1) == 0 ? 0xFFFFFFFF : 0x000000FF);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 4);
        GLSMTextures.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
        final int[] filtered = drawFlat();
        GLSMTextures.setTextureSupersamplingSamples(samples);
        final int[] supersampled = drawFlat();
        for (int y = 97; y <= 102; y++) {
            for (int x = 97; x <= 102; x++) {
                final int index = y * SIZE + x;
                assertEquals(128, filtered[index] & 255, 1, "The mip-filtered checkerboard must average to gray");
                assertEquals(filtered[index] & 255, supersampled[index] & 255, 1,
                    "Supersampling must preserve the mip average instead of aliasing the base-level checkerboard");
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 16})
    void transparentTexelsDoNotTintPartialCoverage(int samples) {
        createAtlas(16, (x, y) -> x < 8 ? 0xFF0000FF : 0x0000FF00);
        GLSMEnable.glEnable(GL11.GL_BLEND);
        GLSMBlend.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GLSMTextures.setTextureSupersamplingSamples(samples);
        final int[] pixels = drawTop(3);
        int partialPixels = 0;
        for (int pixel : pixels) {
            final int red = pixel & 255;
            if (red > 0 && red < 255) {
                partialPixels++;
                assertEquals(0, (pixel >> 8) & 255);
                assertEquals(0, (pixel >> 16) & 255, "Transparent blue texels must not tint the red edge");
            }
        }
        assertTrue(partialPixels > 0, "The test must cover a partially transparent pixel");
    }

    @Test
    void modelTexturesKeepTheirOwnFilteringWhileSupersamplingIsActive() {
        createDotAtlas();
        final int modelTexture = uploadTexture(16, 1, TextureSupersamplingGLTest::dotColor);
        try {
            GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
            final int[] normal = drawTop(3);
            GLSMTextures.setTextureSupersamplingSamples(16);
            assertArrayEquals(normal, drawTop(3));
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            assertFalse(Arrays.equals(normal, drawTop(3)));
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, modelTexture);
            assertArrayEquals(normal, drawTop(3));
        } finally {
            GLSMTextures.glDeleteTextures(modelTexture);
        }
    }

    @Test
    void atlasFetchesIgnoreSamplerFilteringAndRepeatAcrossNegativeUvs() {
        createAtlas(16, 4, TextureSupersamplingGLTest::dotColor);
        GLSMTextures.setTextureSupersamplingSamples(16);
        final int[] nearest = drawTop(3);
        final int sampler = RenderSystem.genSampler();
        try {
            RenderSystem.samplerParameteri(sampler, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            RenderSystem.samplerParameteri(sampler, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            RenderSystem.samplerParameteri(sampler, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            RenderSystem.samplerParameteri(sampler, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            RenderSystem.bindSamplerToUnit(0, sampler);
            GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
            GLSMMatrices.glTranslatef(-2, 3, 0);
            GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
            assertArrayEquals(nearest, drawTop(3));
        } finally {
            GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
            GLSMMatrices.glLoadIdentity();
            GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
            RenderSystem.bindSamplerToUnit(0, 0);
            RenderSystem.destroySampler(sampler);
        }
    }

    @Test
    void changingTheAtlasGutterSwitchesBetweenMipFilteringAndFullFootprintSampling() {
        createAtlas(32, 4, (x, y) -> 0xFF0000FF);
        final int[] baseLevel = drawFlat();
        for (int level = 1; level <= 6; level++) {
            final int size = 64 >> level;
            final ByteBuffer pixels = BufferUtils.createByteBuffer(size * size * 4);
            for (int i = 0; i < size * size; i++) pixels.putInt(0xFFFF0000);
            pixels.flip();
            GLSMTextures.glTexImage2D(GL11.GL_TEXTURE_2D, level, GL11.GL_RGBA8, size, size, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        }
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 6);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST_MIPMAP_NEAREST);
        final int[] mipFiltered = drawFlat();
        assertFalse(Arrays.equals(baseLevel, mipFiltered));
        GLSMTextures.setTextureSupersamplingSamples(16);
        assertArrayEquals(baseLevel, drawFlat());
        GLSMTextures.markTextureAsAtlas(texture, 1);
        assertArrayEquals(mipFiltered, drawFlat());
        GLSMTextures.markTextureAsAtlas(texture, 4);
        assertArrayEquals(baseLevel, drawFlat());
    }

    private int[] drawFlat() {
        FfpFixture.clear();
        GLSMMatrices.glLoadIdentity();
        GLSMMatrices.glTranslatef(100.25f, 100.125f, 0);
        GLSMMatrices.glScalef(11, 11, 1);
        GLSMMatrices.glRotatef(90, 1, 0, 0);
        GLSMDraws.glDrawArrays(GL11.GL_QUADS, 0, 4);
        return FfpFixture.readRegion(SIZE);
    }

    private void createDotAtlas() {
        createAtlas(16, TextureSupersamplingGLTest::dotColor);
    }

    private void createAtlas(int spriteSize, IntBinaryOperator color) {
        createAtlas(spriteSize, 1, color);
    }

    private void createAtlas(int spriteSize, int gutter, IntBinaryOperator color) {
        texture = uploadTexture(spriteSize, gutter, color);
        GLSMTextures.markTextureAsAtlas(texture, gutter);
        final int atlasSize = Integer.highestOneBit(spriteSize + 2 * gutter) << 1;
        final float minUv = (gutter + 0.01f) / atlasSize;
        final float maxUv = (spriteSize + gutter - 0.01f) / atlasSize;
        final ByteBuffer vertices = BufferUtils.createByteBuffer(4 * 20);
        vertex(vertices, -0.5f, -0.5f, minUv, minUv);
        vertex(vertices, 0.5f, -0.5f, maxUv, minUv);
        vertex(vertices, 0.5f, 0.5f, maxUv, maxUv);
        vertex(vertices, -0.5f, 0.5f, minUv, maxUv);
        vertices.flip();
        vao = GLSMVertexInput.glGenVertexArrays();
        GLSMVertexInput.glBindVertexArray(vao);
        vbo = GLSMBuffers.glGenBuffers();
        GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GLSMBuffers.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STATIC_DRAW);
        FfpFixture.attrib(0, 3, GL11.GL_FLOAT, false, 20, 0);
        FfpFixture.attrib(2, 2, GL11.GL_FLOAT, false, 20, 12);
        VAOManager.setCurrentVertexFlags(TEXTURE_BIT);
    }

    private int[] drawTop(int scale) {
        FfpFixture.clear();
        GLSMMatrices.glLoadIdentity();
        GLSMMatrices.glTranslatef(100 + 8 * scale, 100 + 8 * scale, 0);
        GLSMMatrices.glScalef(10 * scale, 10 * scale, -10 * scale);
        GLSMMatrices.glRotatef(210, 1, 0, 0);
        GLSMMatrices.glRotatef(45, 0, 1, 0);
        GLSMDraws.glDrawArrays(GL11.GL_QUADS, 0, 4);
        return FfpFixture.readRegion(SIZE);
    }
}
