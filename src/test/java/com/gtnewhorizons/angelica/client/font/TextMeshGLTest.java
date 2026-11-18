package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizons.angelica.config.FontConfig;
import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMFramebuffer;
import com.gtnewhorizons.angelica.glsm.GLSMLighting;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.backend.RenderBackend;
import com.gtnewhorizons.angelica.glsm.ffp.FFPVertexLighting;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
class TextMeshGLTest {

    private static final String[] LINES = { "Hello, world! 0123456789", "\u00a7lBold \u00a7nunder\u00a7r plain \u00a7mstrike\u00a7r", "x: 1.00000 (1) // c: 0 (1)", "\u00a7x\u00a7f\u00a7f\u00a70\u00a70\u00a7a\u00a7argb \u4e2d\u6587 mixed" };
    private static final int VIEW_W = 256;
    private static final int VIEW_H = 64;

    private static final List<TextMesh> meshes = new ArrayList<>();
    private static FontTestFixture fixture;
    private static BatchingFontRenderer batcher;
    private static BatchingFontRenderer other;
    private static boolean shadow;

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void setUp() throws Exception {
        fixture = new FontTestFixture(tempDir);
        FontConfig.shadowCopies = 1;
        FontConfig.boldCopies = 2;
        batcher = fixture.batcher();
        other = new BatchingFontRenderer(fixture.font, FontTestFixture.charWidths(), new int[32], FontTestFixture.ASCII);
    }

    @AfterAll
    static void tearDown() {
        fixture.close();
    }

    @BeforeEach
    void projection() {
        shadow = false;
        GLSMRasterizer.glViewport(0, 0, VIEW_W, VIEW_H);
        GLSMMatrices.glMatrixMode(GL11.GL_PROJECTION);
        GLSMMatrices.glLoadIdentity();
        GLSMMatrices.glOrtho(0.0, VIEW_W, VIEW_H, 0.0, -1.0, 1.0);
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMMatrices.glLoadIdentity();
    }

    @AfterEach
    void reset() {
        batcher.resetAfterCrash();
        other.resetAfterCrash();
        meshes.forEach(TextMesh::free);
        meshes.clear();
        endFrames();
        BatchingFontRenderer.exitButtonSection(false);
        BatchingFontRenderer.exitRecolorSection(false);
        GLSMMatrices.glMatrixMode(GL11.GL_PROJECTION);
        GLSMMatrices.glLoadIdentity();
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMMatrices.glLoadIdentity();
        GLSMDepthStencilAlpha.disableDepthTest();
        GLSMLighting.disableLighting();
        GLSMLighting.disableColorMaterial();
        GLSMBlend.glColorMask(true, true, true, true);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    private static void draw(String s, float y) {
        batcher.drawString(2.0f, y, 0xFFE0E0E0, shadow, false, s, 0, s.length());
    }

    private static void drawLines() {
        batcher.fillRect(0.0f, 40.0f, 100.0f, 50.0f, 0xFF00FF00);
        for (int i = 0; i < LINES.length; i++) draw(LINES[i], 2.0f + 9.0f * i);
    }

    private static void drawOther(String s, float y) {
        other.drawString(2.0f, y, 0xFFE0E0E0, false, false, s, 0, s.length());
    }

    private static void endFrames() {
        for (int i = 0; i < RenderBackend.MAX_FRAMES_IN_FLIGHT; i++) BatchingFontRenderer.endFrame();
    }

    private static TextMesh mesh() {
        final TextMesh mesh = new TextMesh();
        meshes.add(mesh);
        return mesh;
    }

    private static TextMesh capture(Runnable body) {
        final TextMesh mesh = mesh();
        batcher.beginBatch();
        batcher.beginCapture(mesh);
        body.run();
        batcher.endCapture();
        batcher.resetAfterCrash();
        return mesh;
    }

    private static byte[] frame(Runnable body) {
        GLSMBlend.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT);
        body.run();
        final ByteBuffer pixels = BufferUtils.createByteBuffer(VIEW_W * VIEW_H * 4);
        GL11.glReadPixels(0, 0, VIEW_W, VIEW_H, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        final byte[] out = new byte[pixels.capacity()];
        pixels.get(out);
        return out;
    }

    private static List<Object> contents(TextMesh mesh) {
        final int n = mesh.commands;
        final int quadBytes = Reflect.getStatic(BatchingFontRenderer.class, "QUAD_BYTES");
        final ByteBuffer vertices = mesh.vertices.duplicate();
        vertices.limit(mesh.quadCount * quadBytes);
        return List.of(vertices, mesh.quadCount, Arrays.toString(Arrays.copyOf(mesh.commandStart, n)), Arrays.toString(Arrays.copyOf(mesh.commandCount, n)),
            Arrays.toString(Arrays.copyOf(mesh.commandTexture, n)), Arrays.toString(Arrays.copyOf(mesh.commandLayer, n)), Arrays.toString(Arrays.copyOf(mesh.commandUnicode, n)));
    }

    @Test
    void replayEmitsTheCapturedBytesWhereverItLands() {
        final TextMesh direct = capture(TextMeshGLTest::drawLines);
        assertTrue(direct.isValid(GlyphSettings.current()));
        batcher.beginBatch();
        draw("prefix line", 100.0f);
        final TextMesh afterPrefix = capture(TextMeshGLTest::drawLines);
        batcher.beginBatch();
        draw("prefix line", 100.0f);
        final TextMesh replayed = capture(() -> assertTrue(batcher.replay(direct, GlyphSettings.current())));
        assertTrue(direct.quadCount > 0);
        assertEquals(contents(direct), contents(afterPrefix));
        assertEquals(contents(direct), contents(replayed));
        batcher.beginBatch();
        assertTrue(batcher.replay(direct, GlyphSettings.current()));
        final int arenaBytes = Reflect.getStatic(BatchingFontRenderer.class, "vertexDataPos");
        assertEquals(0, arenaBytes);
        batcher.endBatch();
        direct.free();
        assertFalse(direct.isValid(GlyphSettings.current()));
        assertFalse(batcher.replay(direct, GlyphSettings.current()));
    }

    @ParameterizedTest(name = "shadow={0}")
    @ValueSource(booleans = { false, true })
    void replayDrawsTheSamePixelsAsTheDirectDraw(boolean shadowed) {
        shadow = shadowed;
        final byte[] direct = frame(() -> {
            batcher.beginBatch();
            drawLines();
            batcher.endBatch();
        });
        final TextMesh mesh = capture(TextMeshGLTest::drawLines);
        final byte[] uploaded = frame(() -> assertTrue(batcher.replay(mesh, GlyphSettings.current())));
        assertTrue(mesh.regionStart >= 0);
        final byte[] resident = frame(() -> assertTrue(batcher.replay(mesh, GlyphSettings.current())));
        assertFalse(Arrays.equals(direct, frame(() -> {})));
        assertArrayEquals(direct, uploaded);
        assertArrayEquals(direct, resident);
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
    }

    @Test
    void replayedAndDirectTextKeepTheirPushOrder() {
        final String text = "overlap";
        final Runnable red = () -> batcher.drawString(2.0f, 2.0f, 0x80FF0000, false, false, text, 0, text.length());
        final Runnable green = () -> batcher.drawString(2.0f, 2.0f, 0x8000FF00, false, false, text, 0, text.length());
        final byte[] direct = frame(() -> {
            batcher.beginBatch();
            red.run();
            green.run();
            batcher.endBatch();
        });
        final TextMesh mesh = capture(red);
        final byte[] mixed = frame(() -> {
            batcher.beginBatch();
            assertTrue(batcher.replay(mesh, GlyphSettings.current()));
            green.run();
            batcher.endBatch();
        });
        assertTrue(mesh.regionStart >= 0);
        assertArrayEquals(direct, mixed);
    }

    @Test
    void freedAndRecapturedMeshesReuploadAfterTheFrameDelay() {
        endFrames();
        final byte[] direct = frame(() -> {
            batcher.beginBatch();
            drawLines();
            batcher.endBatch();
        });
        final TextMesh resident = capture(() -> draw("resident", 2.0f));
        assertTrue(batcher.replay(resident, GlyphSettings.current()));
        final TextMesh freed = capture(TextMeshGLTest::drawLines);
        assertTrue(batcher.replay(freed, GlyphSettings.current()));
        final int freedStart = freed.regionStart;
        freed.free();
        final TextMesh sameFrame = capture(TextMeshGLTest::drawLines);
        assertArrayEquals(direct, frame(() -> assertTrue(batcher.replay(sameFrame, GlyphSettings.current()))));
        assertNotEquals(freedStart, sameFrame.regionStart);
        sameFrame.free();
        final TextMesh recaptured = capture(TextMeshGLTest::drawLines);
        endFrames();
        assertArrayEquals(direct, frame(() -> assertTrue(batcher.replay(recaptured, GlyphSettings.current()))));
        assertEquals(freedStart, recaptured.regionStart);
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
    }

    static List<Named<Runnable>> abortedCaptures() {
        return List.of(
            Named.of("plain lines", TextMeshGLTest::drawLines),
            Named.of("matrix change seals a segment", () -> {
                draw("before", 2.0f);
                GLSMMatrices.glTranslatef(1.0f, 0.0f, 0.0f);
                draw("after", 11.0f);
            }),
            Named.of("draw state change seals a segment", () -> {
                draw("before", 2.0f);
                GLSMBlend.glColorMask(false, false, false, false);
                draw("after", 11.0f);
            }));
    }

    @ParameterizedTest
    @MethodSource("abortedCaptures")
    void abortRollsBackEverythingSinceBeginCapture(Runnable body) {
        final byte[] prefixOnly = frame(() -> draw("prefix line", 30.0f));
        final TextMesh mesh = mesh();
        final byte[] aborted = frame(() -> {
            batcher.beginBatch();
            draw("prefix line", 30.0f);
            batcher.beginCapture(mesh);
            body.run();
            batcher.abortCapture();
            assertThrows(IllegalStateException.class, batcher::abortCapture);
            batcher.endBatch();
        });
        assertFalse(mesh.isValid(GlyphSettings.current()));
        assertArrayEquals(prefixOnly, aborted);
    }

    @Test
    void rejectedCaptureCallsLeaveTheBatchDepthBalanced() {
        final byte[] direct = frame(() -> draw("depth", 2.0f));
        final byte[] after = frame(() -> {
            batcher.beginBatch();
            batcher.beginCapture(mesh());
            assertThrows(IllegalStateException.class, () -> batcher.beginCapture(mesh()));
            batcher.endCapture();
            assertThrows(IllegalStateException.class, batcher::endCapture);
            draw("depth", 2.0f);
            batcher.endBatch();
        });
        assertArrayEquals(direct, after);
    }

    private static Arguments refused(String name, boolean animated, Runnable body) {
        return Arguments.of(Named.of(name, body), animated);
    }

    static List<Arguments> refusedCaptures() {
        return List.of(
            refused("matrix change mid-capture", false, () -> {
                draw("before", 2.0f);
                GLSMMatrices.glTranslatef(1.0f, 0.0f, 0.0f);
                draw("after", 11.0f);
            }),
            refused("draw state change before the first glyph", false, () -> {
                GLSMDepthStencilAlpha.enableDepthTest();
                draw("depth", 2.0f);
            }),
            refused("another renderer flushes mid-capture", false, () -> {
                draw("before", 2.0f);
                drawOther("other", 11.0f);
            }),
            refused("obfuscated text", true, () -> draw("\u00a7kobfuscated", 2.0f)));
    }

    @ParameterizedTest
    @MethodSource("refusedCaptures")
    void captureIsRefusedWhenTheMeshCouldNotReplayFaithfully(Runnable body, boolean animated) {
        final TextMesh mesh = capture(body);
        assertFalse(mesh.isValid(GlyphSettings.current()));
        assertEquals(animated, mesh.isAnimated());
        batcher.beginBatch();
        assertFalse(batcher.replay(mesh, GlyphSettings.current()));
    }

    @Test
    void abortAfterAForeignFlushKeepsTheBatchUsable() {
        final byte[] expected = frame(() -> {
            draw("prefix", 2.0f);
            drawOther("other", 20.0f);
            draw("after", 38.0f);
        });
        final byte[] actual = frame(() -> {
            batcher.beginBatch();
            draw("prefix", 2.0f);
            batcher.beginCapture(mesh());
            drawOther("other", 20.0f);
            batcher.abortCapture();
            draw("after", 38.0f);
            batcher.endBatch();
        });
        assertArrayEquals(expected, actual);
    }

    @ParameterizedTest(name = "{0} fillRect={1}")
    @CsvSource({ "none, false", "lit, false", "lit, true", "button, false", "darkMode, false" })
    void replayIsRefusedWhereADirectDrawWouldDiffer(String context, boolean rect) {
        GLSMMatrices.glMatrixMode(GL11.GL_PROJECTION);
        GLSMMatrices.glLoadIdentity();
        GLSMMatrices.glFrustum(-1.0, 1.0, -1.0, 1.0, 1.0, 100.0);
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        final Runnable body = rect ? () -> batcher.fillRect(0.0f, 0.0f, 4.0f, 4.0f, 0xFF00FF00) : () -> draw("text", 2.0f);
        final TextMesh outside = capture(body);
        assertTrue(outside.isValid(GlyphSettings.current()));
        switch (context) {
            case "lit" -> {
                GLSMLighting.enableLighting();
                GLSMLighting.enableColorMaterial();
                assertTrue(FFPVertexLighting.modulatesVertexColor(new Vector3f()));
                assertTrue(capture(body).isAnimated());
            }
            case "button" -> BatchingFontRenderer.enterButtonSection(0, 0, 0);
            case "darkMode" -> {
                BatchingFontRenderer.enterRecolorSection(true);
                // Whitespace only: a visible glyph would run DarkModeUtils' resource-pack setup in the test JVM.
                assertTrue(capture(() -> draw(" ", 2.0f)).isAnimated());
            }
            default -> { }
        }
        batcher.beginBatch();
        assertEquals(context.equals("none"), batcher.replay(outside, GlyphSettings.current()));
    }
}
