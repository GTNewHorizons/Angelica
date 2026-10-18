package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.function.IntBinaryOperator;
import java.util.stream.Stream;

import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.BLUE;
import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.GREEN;
import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.RED;
import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.SIZE;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlCopyBlitTest {

    private static final int SCISSOR_ROWS = 16;
    private static final byte SENTINEL_BYTE = 0x7B;
    private static final int SENTINEL = 0x7B7B7B7B;

    @BeforeAll
    static void installFinalTarget() {
        GlsmSdlHeadlessRig.installFinalTarget(SIZE, SIZE);
    }

    @AfterAll
    static void dropFinalTarget() {
        GlsmSdlHeadlessRig.dropFinalTarget();
    }

    private static void assertRow(int[] pixels, int y, int expected, String label) {
        for (int x = 0; x < SIZE; x += 8) {
            GlsmSdlHeadlessRig.assertPixel(pixels, SIZE, x, y, expected, label + " row " + y);
        }
    }

    @Test
    void scissorOnTheUserFboClipsTheGlBottomRows() {
        GlsmSdlHeadlessRig.bindTarget();
        GlsmSdlHeadlessRig.clearTo(0.0f, 0.0f, 1.0f, 1.0f);

        GLStateManager.glEnable(GL11.GL_SCISSOR_TEST);
        GLStateManager.glScissor(0, 0, SIZE, SCISSOR_ROWS);
        GlsmSdlHeadlessRig.solidQuad(1.0f, 0.0f, 0.0f);
        GLStateManager.glDisable(GL11.GL_SCISSOR_TEST);

        final int[] pixels = GlsmSdlHeadlessRig.readTarget();
        assertRow(pixels, 0, RED, "inside the scissor");
        assertRow(pixels, SCISSOR_ROWS - 1, RED, "inside the scissor");
        assertRow(pixels, SCISSOR_ROWS, BLUE, "outside the scissor");
        assertRow(pixels, SIZE - 1, BLUE, "outside the scissor");
    }

    private static int createTwoLevelTexture(int level0Argb, int level1Argb) {
        final int id = GLStateManager.glGenTextures();
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST_MIPMAP_NEAREST);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 1);
        GLStateManager.glTexParameterf(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MIN_LOD, 0.0f);
        GLStateManager.glTexParameterf(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LOD, 1.0f);
        GLStateManager.glTexParameterf(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_LOD_BIAS, 0.0f);
        GlsmSdlHeadlessRig.uploadSolidLevel(0, SIZE, level0Argb);
        GlsmSdlHeadlessRig.uploadSolidLevel(1, SIZE / 2, level1Argb);
        return id;
    }

    private static int copyGreenIntoLevelOne() {
        GlsmSdlHeadlessRig.bindTarget();
        GlsmSdlHeadlessRig.clearTo(0.0f, 0.0f, 0.0f, 1.0f);
        GlsmSdlHeadlessRig.solidQuad(0.0f, 1.0f, 0.0f);

        final int texture = createTwoLevelTexture(RED, BLUE);
        GLStateManager.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 1, 0, 0, 0, 0, SIZE / 2, SIZE / 2);
        return texture;
    }

    @Test
    void copyTexSubImage2DWritesTheRequestedMipLevel() {
        final int texture = copyGreenIntoLevelOne();

        GlsmSdlHeadlessRig.bindTarget();
        GlsmSdlHeadlessRig.clearTo(0.0f, 0.0f, 0.0f, 1.0f);
        GlsmSdlHeadlessRig.texturedQuad(texture, 0.5f);

        final int[] pixels = GlsmSdlHeadlessRig.readTarget();
        GlsmSdlHeadlessRig.assertPixel(pixels, SIZE, SIZE / 2, SIZE / 2, GREEN, "minified sample of level 1");
    }

    @Test
    void copyTexSubImage2DLeavesTheOtherMipLevelsUntouched() {
        final int texture = copyGreenIntoLevelOne();

        GlsmSdlHeadlessRig.bindTarget();
        GlsmSdlHeadlessRig.clearTo(0.0f, 0.0f, 0.0f, 1.0f);
        GlsmSdlHeadlessRig.texturedQuad(texture, 1.0f);

        final int[] pixels = GlsmSdlHeadlessRig.readTarget();
        GlsmSdlHeadlessRig.assertPixel(pixels, SIZE, SIZE / 4, SIZE / 4, RED, "magnified sample of the level 0 region the copy would land in");
    }

    @Test
    void readPixelsCrossingTheTopEdgeReturnsTheInRangeRows() {
        GlsmSdlHeadlessRig.bindTarget();
        GlsmSdlHeadlessRig.clearTo(0.0f, 0.0f, 1.0f, 1.0f);
        GlsmSdlHeadlessRig.halfQuad(0.0f, 1.0f, 0.0f, 1.0f, 0.0f);

        final int[] pixels = GlsmSdlHeadlessRig.readTarget(0, SIZE / 2, SIZE, SIZE, SENTINEL_BYTE);
        assertRow(pixels, 0, GREEN, "first in-range row");
        assertRow(pixels, SIZE / 2 - 1, GREEN, "last in-range row");
        assertRow(pixels, SIZE / 2, SENTINEL, "first out-of-range row");
        assertRow(pixels, SIZE - 1, SENTINEL, "last out-of-range row");
    }

    @Test
    void blitFramebufferWithColorAndDepthBitsMovesColor() {
        GlsmSdlHeadlessRig.bindTarget();
        GlsmSdlHeadlessRig.clearTo(0.0f, 1.0f, 0.0f, 1.0f);
        GlsmSdlHeadlessRig.solidQuad(0.0f, 1.0f, 0.0f);

        final int destination = GlsmSdlHeadlessRig.createDepthStencilFbo();
        GLStateManager.glViewport(0, 0, SIZE, SIZE);
        GLStateManager.glClearColor(0.0f, 0.0f, 1.0f, 1.0f);
        GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GlsmSdlHeadlessRig.solidQuad(0.0f, 0.0f, 1.0f);

        GlsmSdlHeadlessRig.bindTarget();
        GLStateManager.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, destination);
        GLStateManager.glBlitFramebuffer(0, 0, SIZE, SIZE, 0, 0, SIZE, SIZE,
            GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);

        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, destination);
        final int[] pixels = GlsmSdlHeadlessRig.readTarget();
        GlsmSdlHeadlessRig.assertPixel(pixels, SIZE, SIZE / 2, SIZE / 2, GREEN, "color aspect of a color+depth blit");
        GlsmSdlHeadlessRig.assertPixel(pixels, SIZE, 4, SIZE - 5, GREEN, "color aspect of a color+depth blit");
    }

    private static void blitLevelOneInto(int dstSize, int dstLevel) {
        final int source = GlsmSdlHeadlessRig.createSolidMipTexture(SIZE, 1, RED);
        GlsmSdlHeadlessRig.uploadSolidLevel(1, SIZE / 2, GREEN);
        final int destination = GlsmSdlHeadlessRig.createSolidMipTexture(dstSize, dstLevel, BLUE);
        final int readFbo = GlsmSdlHeadlessRig.fboWithColor(source, 1);
        final int drawFbo = GlsmSdlHeadlessRig.fboWithColor(destination, dstLevel);

        final int dstExtent = Math.max(1, dstSize >> dstLevel);
        GLStateManager.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
        GLStateManager.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
        GLStateManager.glBlitFramebuffer(0, 0, SIZE / 2, SIZE / 2, 0, 0, dstExtent, dstExtent, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);

        GlsmSdlHeadlessRig.assertUniform(GlsmSdlHeadlessRig.readTextureLevel(destination, dstLevel), GREEN, "destination level " + dstLevel + " after blitting source level 1");
        GlsmSdlHeadlessRig.assertUniform(GlsmSdlHeadlessRig.readTextureLevel(source, 0), RED, "source level 0 is not the blit source");
        if (dstLevel != 0) GlsmSdlHeadlessRig.assertUniform(GlsmSdlHeadlessRig.readTextureLevel(destination, 0), BLUE, "destination level 0 is not the blit target");

        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GLStateManager.glDeleteFramebuffers(readFbo);
        GLStateManager.glDeleteFramebuffers(drawFbo);
        GLStateManager.glDeleteTextures(source);
        GLStateManager.glDeleteTextures(destination);
    }

    @Test
    void attachingToTheReadFramebufferLeavesTheDrawFramebufferAlone() {
        final int source = GlsmSdlHeadlessRig.createSolidTexture(GREEN);
        final int drawColor = GlsmSdlHeadlessRig.createSolidTexture(BLUE);
        final int readFbo = GLStateManager.glGenFramebuffers();
        final int drawFbo = GlsmSdlHeadlessRig.fboWithColor(drawColor, 0);
        try {
            GLStateManager.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
            GLStateManager.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, source, 0);
            GLStateManager.glBlitFramebuffer(0, 0, 2, 2, 0, 0, 2, 2, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            GlsmSdlHeadlessRig.assertUniform(GlsmSdlHeadlessRig.readTextureLevel(drawColor, 0), GREEN, "the blit reads the read framebuffer's attachment");

            GLStateManager.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
            GLStateManager.glClearColor(1.0f, 0.0f, 0.0f, 1.0f);
            GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT);
            GlsmSdlHeadlessRig.assertUniform(GlsmSdlHeadlessRig.readTextureLevel(drawColor, 0), RED, "the draw framebuffer kept its own attachment");
            GlsmSdlHeadlessRig.assertUniform(GlsmSdlHeadlessRig.readTextureLevel(source, 0), GREEN, "the read attachment never became a draw target");
        } finally {
            GLStateManager.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GLStateManager.glDeleteFramebuffers(readFbo);
            GLStateManager.glDeleteFramebuffers(drawFbo);
            GLStateManager.glDeleteTextures(source);
            GLStateManager.glDeleteTextures(drawColor);
        }
    }

    static Stream<Arguments> levelBlits() {
        return Stream.of(
            Arguments.of("1:1 into level 0: reads the source attachment level", SIZE / 2, 0),
            Arguments.of("scaled into level 0: reads the source attachment level", SIZE, 0),
            Arguments.of("into level 1: writes the destination attachment level", SIZE, 1));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("levelBlits")
    void blitFramebufferHonoursAttachmentLevels(String name, int dstSize, int dstLevel) {
        blitLevelOneInto(dstSize, dstLevel);
    }

    @Test
    void blitFramebufferIntoFbo0SurvivesTheFramesFirstFbo0Pass() {
        GlsmSdlHeadlessRig.bindTarget();
        GlsmSdlHeadlessRig.clearTo(0.0f, 1.0f, 0.0f, 1.0f);
        GlsmSdlHeadlessRig.solidQuad(0.0f, 1.0f, 0.0f);

        GLStateManager.glClearColor(0.0f, 0.0f, 1.0f, 1.0f);
        GLStateManager.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
        GLStateManager.glBlitFramebuffer(0, 0, SIZE, SIZE, 0, 0, SIZE, SIZE, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);

        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GLStateManager.glViewport(0, 0, SIZE, SIZE);
        GLStateManager.glClearColor(1.0f, 0.0f, 0.0f, 1.0f);
        GLStateManager.glEnable(GL11.GL_SCISSOR_TEST);
        GLStateManager.glScissor(0, 0, 4, 4);
        GlsmSdlHeadlessRig.solidQuad(1.0f, 0.0f, 0.0f);
        GLStateManager.glDisable(GL11.GL_SCISSOR_TEST);

        final int[] pixels = GlsmSdlHeadlessRig.readTarget();
        GlsmSdlHeadlessRig.assertPixel(pixels, SIZE, SIZE / 2, SIZE / 2, GREEN, "blit into fbo0 survived the first fbo0 pass");
    }

    @Test
    void blitFramebufferFromFbo0ToTheUserFboKeepsGlOrientation() {
        GlsmSdlHeadlessRig.bindTarget();
        GlsmSdlHeadlessRig.clearTo(0.0f, 0.0f, 0.0f, 1.0f);
        GlsmSdlHeadlessRig.solidQuad(0.0f, 0.0f, 0.0f);

        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GLStateManager.glViewport(0, 0, SIZE, SIZE);
        GLStateManager.glClearColor(0.0f, 0.0f, 1.0f, 1.0f);
        GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GlsmSdlHeadlessRig.halfQuad(-1.0f, 0.0f, 0.0f, 1.0f, 0.0f);
        GlsmSdlHeadlessRig.halfQuad(0.0f, 1.0f, 1.0f, 0.0f, 0.0f);

        GlsmSdlHeadlessRig.bindTarget();
        GLStateManager.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
        GLStateManager.glBlitFramebuffer(0, 0, SIZE, SIZE, 0, 0, SIZE, SIZE, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);

        GlsmSdlHeadlessRig.bindTarget();
        final int[] pixels = GlsmSdlHeadlessRig.readTarget();
        GlsmSdlHeadlessRig.assertPixel(pixels, SIZE, SIZE / 2, 4, GREEN, "blitted fbo0 bottom");
        GlsmSdlHeadlessRig.assertPixel(pixels, SIZE, SIZE / 2, SIZE - 5, RED, "blitted fbo0 top");
        GlsmSdlHeadlessRig.assertPixel(pixels, SIZE, SIZE / 2, SIZE / 2 - 4, GREEN, "blitted fbo0 below the split");
        GlsmSdlHeadlessRig.assertPixel(pixels, SIZE, SIZE / 2, SIZE / 2 + 4, RED, "blitted fbo0 above the split");
    }

    @Test
    void blitFramebufferStencilSurvivesTheDestinationsFirstDraw() {
        GlsmSdlHeadlessRig.bindTarget();
        GlsmSdlHeadlessRig.writeStencilOneInBottomHalf();

        final int destination = GlsmSdlHeadlessRig.createDepthStencilFbo();
        GLStateManager.glClearColor(0.0f, 0.0f, 1.0f, 1.0f);
        GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        GlsmSdlHeadlessRig.bindTarget();
        GLStateManager.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, destination);
        GLStateManager.glBlitFramebuffer(0, 0, SIZE, SIZE, 0, 0, SIZE, SIZE, GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT, GL11.GL_NEAREST);

        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, destination);
        GlsmSdlHeadlessRig.drawRedWhereStencilIsOne();

        final int[] pixels = GlsmSdlHeadlessRig.readTarget();
        GlsmSdlHeadlessRig.assertPixel(pixels, SIZE, SIZE / 2, 4, RED, "stencil carried by the blit");
        GlsmSdlHeadlessRig.assertPixel(pixels, SIZE, SIZE / 2, SIZE - 5, BLUE, "outside the blitted stencil");
    }

    private static final int UNTOUCHED = 0xFF101010;
    private static final int CELL = 8;

    private static int pattern(int x, int y) {
        return 0xFF000040 | ((x / CELL) * 32 << 16) | ((y / CELL) * 32 << 8);
    }

    private static int createPatternTexture() {
        final int id = GlsmSdlHeadlessRig.newNearestTexture();
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        final ByteBuffer texels = MemoryUtil.memAlloc(SIZE * SIZE * 4);
        try {
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    GlsmSdlHeadlessRig.putArgb(texels, y * SIZE + x, pattern(x, y));
                }
            }
            GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, SIZE, SIZE, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, texels);
        } finally {
            MemoryUtil.memFree(texels);
        }
        return id;
    }

    private static int[] blitPattern(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1) {
        final int source = createPatternTexture();
        final int destination = GlsmSdlHeadlessRig.createSolidMipTexture(SIZE, 0, UNTOUCHED);
        final int readFbo = GlsmSdlHeadlessRig.fboWithColor(source, 0);
        final int drawFbo = GlsmSdlHeadlessRig.fboWithColor(destination, 0);
        GLStateManager.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
        GLStateManager.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
        GLStateManager.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        final int[] pixels = GlsmSdlHeadlessRig.readTextureLevel(destination, 0);
        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GLStateManager.glDeleteFramebuffers(readFbo);
        GLStateManager.glDeleteFramebuffers(drawFbo);
        GLStateManager.glDeleteTextures(source);
        GLStateManager.glDeleteTextures(destination);
        return pixels;
    }

    private static Arguments mirror(String name, int[] srcRect, int[] dstRect, IntBinaryOperator expected) {
        return Arguments.of(name, srcRect, dstRect, expected);
    }

    /** Rects are x0, y0, x1, y1; {@code expected} maps a destination pixel to the pattern colour it must hold. */
    static Stream<Arguments> mirroredBlits() {
        return Stream.of(
            mirror("reversed source X mirrors horizontally", new int[]{SIZE, 0, 0, SIZE}, new int[]{0, 0, SIZE, SIZE}, (x, y) -> pattern(SIZE - 1 - x, y)),
            mirror("reversed destination Y mirrors vertically", new int[]{0, 0, SIZE, SIZE}, new int[]{0, SIZE, SIZE, 0}, (x, y) -> pattern(x, SIZE - 1 - y)),
            mirror("both ranges reversed does not mirror", new int[]{SIZE, SIZE, 0, 0}, new int[]{SIZE, SIZE, 0, 0}, (x, y) -> pattern(x, y)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mirroredBlits")
    void blitFramebufferMirrorsByRangeDirection(String name, int[] src, int[] dst, IntBinaryOperator expected) {
        final int[] pixels = blitPattern(src[0], src[1], src[2], src[3], dst[0], dst[1], dst[2], dst[3]);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                GlsmSdlHeadlessRig.assertPixel(pixels, SIZE, x, y, expected.applyAsInt(x, y), name);
            }
        }
    }

    @Test
    void mirroredScaledBlitClipsTheOutOfBoundsSourceOnTheMirroredSide() {
        final int[] pixels = blitPattern(SIZE + SIZE / 2, 0, SIZE / 2, SIZE / 2, 0, 0, SIZE / 2, SIZE);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (x < SIZE / 4 || x >= SIZE / 2) {
                    GlsmSdlHeadlessRig.assertPixel(pixels, SIZE, x, y, UNTOUCHED, "outside the in-bounds part of the mirrored source");
                    continue;
                }
                final int sx = SIZE + SIZE / 2 - 1 - 2 * x;
                final int sy = y / 2;
                if ((sx - 1) / CELL != (sx + 1) / CELL || (sy - 1) / CELL != (sy + 1) / CELL) continue;
                GlsmSdlHeadlessRig.assertPixel(pixels, SIZE, x, y, pattern(sx, sy), "mirrored scaled blit");
            }
        }
    }

    @Test
    void blitFramebufferWritesEveryDrawBuffer() {
        final int source = createPatternTexture();
        final int first = GlsmSdlHeadlessRig.createSolidMipTexture(SIZE, 0, UNTOUCHED);
        final int skipped = GlsmSdlHeadlessRig.createSolidMipTexture(SIZE, 0, UNTOUCHED);
        final int third = GlsmSdlHeadlessRig.createSolidMipTexture(SIZE, 0, UNTOUCHED);
        final int readFbo = GlsmSdlHeadlessRig.fboWithColor(source, 0);
        final int drawFbo = GlsmSdlHeadlessRig.fboWithColor(first, 0);
        GLStateManager.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT1, GL11.GL_TEXTURE_2D, skipped, 0);
        GLStateManager.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT2, GL11.GL_TEXTURE_2D, third, 0);
        final IntBuffer bufs = MemoryUtil.memAllocInt(3);
        try {
            bufs.put(0, GL30.GL_COLOR_ATTACHMENT0).put(1, GL11.GL_NONE).put(2, GL30.GL_COLOR_ATTACHMENT2);
            GLStateManager.glDrawBuffers(bufs);
        } finally {
            MemoryUtil.memFree(bufs);
        }

        GLStateManager.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
        GLStateManager.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
        GLStateManager.glBlitFramebuffer(0, 0, SIZE, SIZE, 0, 0, SIZE, SIZE, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);

        final int[] firstPixels = GlsmSdlHeadlessRig.readTextureLevel(first, 0);
        final int[] thirdPixels = GlsmSdlHeadlessRig.readTextureLevel(third, 0);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                GlsmSdlHeadlessRig.assertPixel(firstPixels, SIZE, x, y, pattern(x, y), "draw buffer 0");
                GlsmSdlHeadlessRig.assertPixel(thirdPixels, SIZE, x, y, pattern(x, y), "draw buffer 2");
            }
        }
        GlsmSdlHeadlessRig.assertUniform(GlsmSdlHeadlessRig.readTextureLevel(skipped, 0), UNTOUCHED, "attachment 1 behind a GL_NONE draw buffer");

        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GLStateManager.glDeleteFramebuffers(readFbo);
        GLStateManager.glDeleteFramebuffers(drawFbo);
        GLStateManager.glDeleteTextures(source);
        GLStateManager.glDeleteTextures(first);
        GLStateManager.glDeleteTextures(skipped);
        GLStateManager.glDeleteTextures(third);
    }
}
