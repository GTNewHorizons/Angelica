package com.gtnewhorizons.angelica.debug.screen.renderers;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement;
import com.gtnewhorizons.angelica.debug.render.DebugDrawScope;
import com.gtnewhorizons.angelica.debug.render.DebugGeometry;
import com.gtnewhorizons.angelica.debug.render.DebugProgram;
import com.gtnewhorizons.angelica.debug.render.DebugPrograms;
import com.gtnewhorizons.angelica.debug.render.NativeScratch;
import com.gtnewhorizons.angelica.debug.screen.renderers.DebugMeshData.LineWidth;
import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.GlTestSupport;
import com.gtnewhorizons.angelica.glsm.ffp.FfpFixture;
import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import com.gtnewhorizons.angelica.glsm.states.BlendState;
import com.gtnewhorizons.angelica.glsm.streaming.TessellatorStreamingDrawer;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutFloat;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutInt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
class DebugMeshGLTest {

    private static final int SIZE = 64;
    private static final int BACKGROUND = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;

    private final DebugMeshData data = new DebugMeshData();
    private final DebugMesh mesh = new DebugMesh();
    private final Matrix4f base = new Matrix4f();
    private final NativeScratch uploadScratch = new NativeScratch(0x4000);
    private final DebugDrawScope scope = new DebugDrawScope();
    private int vao;
    private int vbo;

    @BeforeEach
    void setUp() {
        FfpFixture.resetFfpState();
        ShaderManager.enable();
        ShaderManager.getInstance().activate();
        GLStateManager.glViewport(0, 0, SIZE, SIZE);
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glLoadIdentity();
    }

    @AfterEach
    void tearDown() {
        GLStateManager.glBindVertexArray(0);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        mesh.delete();
        data.free();
        uploadScratch.free();
        if (vbo != 0) GLStateManager.glDeleteBuffers(vbo);
        if (vao != 0) GLStateManager.glDeleteVertexArrays(vao);
        FfpFixture.resetFfpState();
    }

    private static float pixelCenter(int pixel) {
        return (pixel + 0.5F) * 2.0F / SIZE - 1.0F;
    }

    private static int litInColumn(int[] pixels, int x) {
        int lit = 0;
        for (int row = 0; row < SIZE; row++) {
            if (pixels[row * SIZE + x] != BACKGROUND) lit++;
        }
        return lit;
    }

    private void drawMeshPasses(boolean onTop, double camX) {
        mesh.prepare(base, camX, 0.0, 0.0);
        mesh.drawPlain(onTop, DebugMesh.useColor());
        mesh.drawWide(onTop, DebugMesh.useLines());
    }

    private void drawMesh(boolean onTop, double camX) {
        scope.beginWorld();
        try {
            drawMeshPasses(onTop, camX);
        } finally {
            scope.end();
        }
    }

    private int[] draw(boolean onTop) {
        FfpFixture.clear();
        drawMesh(onTop, 0.0);
        return FfpFixture.readRegion(SIZE);
    }

    @ParameterizedTest
    @CsvSource({"THICK,4", "MEDIUM,2", "THIN,1"})
    void lineWidthSetsPixelRows(LineWidth width, int rows) {
        final double y = pixelCenter(16);
        data.begin(0, 0, 0);
        data.line(width, -0.8, y, 0.0, 0.8, y, 0.0, WHITE);
        mesh.upload(data, uploadScratch);
        assertEquals(rows, litInColumn(draw(false), SIZE / 2));
    }

    @Test
    void anchorIsSubtractedFromTheCamera() {
        data.begin(1000, 0, 0);
        data.line(LineWidth.THICK, 999.2, 0.0, 0.0, 1000.8, 0.0, 0.0, WHITE);
        mesh.upload(data, uploadScratch);
        FfpFixture.clear();
        drawMesh(false, 1000.0);
        assertEquals(4, litInColumn(FfpFixture.readRegion(SIZE), SIZE / 2));
    }

    @Test
    void fillColorBytesAreRedGreenBlueAlpha() {
        data.begin(0, 0, 0);
        data.quad(-0.5, -0.5, 0.0, 0.5, -0.5, 0.0, 0.5, 0.5, 0.0, -0.5, 0.5, 0.0, 0xFFFF0000);
        mesh.upload(data, uploadScratch);
        FfpFixture.clear();
        drawMesh(false, 0.0);
        assertArrayEquals(new int[] { 255, 0, 0, 255 }, FfpFixture.readPixel(SIZE / 2, SIZE / 2));
        assertArrayEquals(new int[] { 0, 0, 0, 255 }, FfpFixture.readPixel(2, 2));
    }

    @Test
    void fillCullsQuadsWoundClockwise() {
        data.begin(0, 0, 0);
        data.quad(-0.5, 0.5, 0.0, 0.5, 0.5, 0.0, 0.5, -0.5, 0.0, -0.5, -0.5, 0.0, 0xFFFF0000);
        mesh.upload(data, uploadScratch);
        FfpFixture.clear();
        drawMesh(false, 0.0);
        assertArrayEquals(new int[] { 0, 0, 0, 255 }, FfpFixture.readPixel(SIZE / 2, SIZE / 2));
    }

    @Test
    void onTopGeometryDrawsOnlyInTheOnTopPass() {
        data.begin(0, 0, 0);
        data.quad(-0.5, -0.5, 0.0, 0.5, -0.5, 0.0, 0.5, 0.5, 0.0, -0.5, 0.5, 0.0, 0xFFFF0000);
        data.alwaysOnTop(true);
        data.line(LineWidth.THICK, -0.8, 0.75, 0.0, 0.8, 0.75, 0.0, WHITE);
        mesh.upload(data, uploadScratch);
        final int[] normal = draw(false);
        final int[] top = draw(true);
        assertEquals(SIZE / 2, litInColumn(normal, SIZE / 2));
        assertEquals(4, litInColumn(top, SIZE / 2));
    }

    @Test
    void reuploadReplacesTheGeometry() {
        data.begin(0, 0, 0);
        data.line(LineWidth.THICK, -0.8, 0.0, 0.0, 0.8, 0.0, 0.0, WHITE);
        data.line(LineWidth.THICK, -0.8, 0.5, 0.0, 0.8, 0.5, 0.0, WHITE);
        mesh.upload(data, uploadScratch);
        assertEquals(8, litInColumn(draw(false), SIZE / 2));
        data.begin(0, 0, 0);
        data.line(LineWidth.THICK, -0.8, 0.0, 0.0, 0.8, 0.0, 0.0, WHITE);
        mesh.upload(data, uploadScratch);
        assertEquals(4, litInColumn(draw(false), SIZE / 2));
        data.begin(0, 0, 0);
        mesh.upload(data, uploadScratch);
        assertEquals(0, litInColumn(draw(false), SIZE / 2));
    }

    @Test
    void scopeRestoresCallerState() {
        data.begin(0, 0, 0);
        data.quad(-0.5, -0.5, 0.0, 0.5, -0.5, 0.0, 0.5, 0.5, 0.0, -0.5, 0.5, 0.0, 0xFFFF0000);
        data.line(LineWidth.THICK, -0.8, 0.0, 0.0, 0.8, 0.0, 0.0, WHITE);
        data.line(LineWidth.THIN, -0.8, 0.5, 0.0, 0.8, 0.5, 0.0, WHITE);
        mesh.upload(data, uploadScratch);

        final int program = GlTestSupport.program(GlTestSupport.TRIVIAL_VS, GlTestSupport.TRIVIAL_FS);
        final int ambient = GLStateManager.glGenTextures();
        final int glyphs = GLStateManager.glGenTextures();
        vao = GLStateManager.glGenVertexArrays();
        GLStateManager.glBindVertexArray(vao);
        vbo = GLStateManager.glGenBuffers();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        try {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, ambient);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
            GLStateManager.enableDepthTest();
            GLStateManager.glDepthFunc(GL11.GL_ALWAYS);
            GLStateManager.glDepthMask(true);
            GLStateManager.disableBlend();
            GLStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
            GLStateManager.enableCull();
            GLStateManager.glCullFace(GL11.GL_FRONT);
            GLStateManager.glFrontFace(GL11.GL_CW);
            GLStateManager.enableScissorTest();
            GLStateManager.glScissor(0, 0, SIZE, SIZE);
            GLStateManager.glPolygonMode(GL11.GL_FRONT, GL11.GL_FILL);
            GLStateManager.glPolygonMode(GL11.GL_BACK, GL11.GL_LINE);
            GLStateManager.glLineWidth(3.0F);
            GLStateManager.glUseProgram(program);

            final long drawCalls = GLStateManager.drawCalls;
            scope.beginWorld();
            try {
                drawMeshPasses(false, 0.0);
                GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, glyphs);
            } finally {
                scope.end();
            }

            assertTrue(GLStateManager.drawCalls > drawCalls);
            assertTrue(GLStateManager.getDepthTest().isEnabled());
            assertEquals(GL11.GL_ALWAYS, GLStateManager.getDepthState().getFunc());
            assertTrue(GLStateManager.getDepthState().isEnabled());
            assertFalse(GLStateManager.getBlendMode().isEnabled());
            final BlendState blend = GLStateManager.getBlendState();
            assertEquals(GL11.GL_ONE, blend.getSrcRgb());
            assertEquals(GL11.GL_ONE, blend.getDstRgb());
            assertEquals(GL11.GL_ZERO, blend.getSrcAlpha());
            assertEquals(GL11.GL_ONE, blend.getDstAlpha());
            assertTrue(GLStateManager.getCullState().isEnabled());
            assertEquals(GL11.GL_FRONT, GLStateManager.getPolygonState().getCullFaceMode());
            assertEquals(GL11.GL_CW, GLStateManager.getPolygonState().getFrontFace());
            assertTrue(GLStateManager.getScissorTest().isEnabled());
            assertEquals(GL11.GL_FILL, GLStateManager.getPolygonState().getFrontMode());
            assertEquals(GL11.GL_LINE, GLStateManager.getPolygonState().getBackMode());
            assertEquals(3.0F, GLStateManager.getLineState().getWidth());
            assertEquals(1, GLStateManager.getActiveTextureUnit());
            assertEquals(ambient, GLStateManager.getTextures().getTextureUnitBindings(0).getBinding());
            assertEquals(ambient, GlTestSupport.driverTextureBinding(0));
            assertEquals(program, GLStateManager.getActiveProgram());
            assertEquals(program, GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
            assertEquals(vao, GLStateManager.getBoundVAO());
            assertEquals(vbo, GLStateManager.getBoundVBO());
            assertEquals(vao, GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING));
            assertEquals(vbo, GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING));
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            GLStateManager.glUseProgram(0);
            GLStateManager.glDeleteProgram(program);
            GLStateManager.glLineWidth(1.0F);
            GLStateManager.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
            GLStateManager.disableScissorTest();
            GLStateManager.glCullFace(GL11.GL_BACK);
            GLStateManager.glFrontFace(GL11.GL_CCW);
            GLStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GLStateManager.glDeleteTextures(ambient);
            GLStateManager.glDeleteTextures(glyphs);
        }
    }

    @Test
    void texturedProgramSamplesUnitZero() {
        final int flags = VertexFlags.TEXTURE_BIT | VertexFlags.COLOR_BIT;
        final VertexFormat format = DefaultVertexFormat.ALL_FORMATS[flags];
        final int stride = format.getVertexSize();
        final int uvOffset = DebugGeometry.offsetOf(format, VertexFormatElement.Usage.PRIMARY_UV);
        final int colorOffset = DebugGeometry.offsetOf(format, VertexFormatElement.Usage.COLOR);
        final ByteBuffer texels = BufferUtils.createByteBuffer(2 * 2 * 4);
        texels.put(new byte[] { (byte) 255, 0, 0, (byte) 255, 0, (byte) 255, 0, (byte) 255, 0, 0, (byte) 255, (byte) 255, (byte) 255, (byte) 255, 0, (byte) 255 }).flip();
        final int texture = GLStateManager.glGenTextures();
        final NativeScratch vertices = new NativeScratch(4 * stride);
        try {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 2, 2, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, texels);
            vertices.ensure(4 * stride);
            final long p = vertices.address();
            texturedVertex(p, -1.0F, -1.0F, 0.0F, 0.0F, uvOffset, colorOffset);
            texturedVertex(p + stride, 1.0F, -1.0F, 1.0F, 0.0F, uvOffset, colorOffset);
            texturedVertex(p + 2L * stride, 1.0F, 1.0F, 1.0F, 1.0F, uvOffset, colorOffset);
            texturedVertex(p + 3L * stride, -1.0F, 1.0F, 0.0F, 1.0F, uvOffset, colorOffset);

            FfpFixture.clear();
            scope.beginWorld();
            try {
                final DebugProgram textured = DebugPrograms.textured();
                textured.use();
                textured.mvp(base.identity());
                TessellatorStreamingDrawer.drawPacked(vertices.window(4 * stride), GL11.GL_QUADS, flags, 4);
            } finally {
                scope.end();
            }

            assertArrayEquals(new int[] { 255, 0, 0, 255 }, FfpFixture.readPixel(SIZE / 4, SIZE / 4));
            assertArrayEquals(new int[] { 0, 255, 0, 255 }, FfpFixture.readPixel(3 * SIZE / 4, SIZE / 4));
            assertArrayEquals(new int[] { 0, 0, 255, 255 }, FfpFixture.readPixel(SIZE / 4, 3 * SIZE / 4));
            assertArrayEquals(new int[] { 255, 255, 0, 255 }, FfpFixture.readPixel(3 * SIZE / 4, 3 * SIZE / 4));
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            vertices.free();
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GLStateManager.glDeleteTextures(texture);
        }
    }

    private static void texturedVertex(long p, float x, float y, float u, float v, int uvOffset, int colorOffset) {
        memPutFloat(p, x);
        memPutFloat(p + 4, y);
        memPutFloat(p + 8, 0.0F);
        memPutFloat(p + uvOffset, u);
        memPutFloat(p + uvOffset + 4, v);
        memPutInt(p + colorOffset, -1);
    }
}
