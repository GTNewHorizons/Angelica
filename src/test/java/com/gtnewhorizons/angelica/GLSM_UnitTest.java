package com.gtnewhorizons.angelica;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.PixelFormat;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GLSM_UnitTest {
    private static DisplayMode mode;
    @BeforeAll
    static void setup() throws LWJGLException {
        mode = findDisplayMode(800, 600, Display.getDisplayMode().getBitsPerPixel());
        Display.setDisplayModeAndFullscreen(mode);
        final PixelFormat format = new PixelFormat().withDepthBits(24);
        Display.create(format);
        GLStateManager.preInit();
    }

    @AfterAll
    static void cleanup() {
        Display.destroy();
    }

    private static DisplayMode findDisplayMode(int width, int height, int bpp) throws LWJGLException {
        final DisplayMode[] modes = Display.getAvailableDisplayModes();
        for ( DisplayMode mode : modes ) {
            if ( mode.getWidth() == width && mode.getHeight() == height && mode.getBitsPerPixel() >= bpp && mode.getFrequency() <= 60 ) {
                return mode;
            }
        }
        return Display.getDesktopDisplayMode();
    }

    void verifyState(int glCap, boolean expected) {
        Stream.of(GLStateManager.glIsEnabled(glCap), GL11.glIsEnabled(glCap)).forEach(b -> assertEquals(expected, b));
    }

    void verifyState(int glCap, int expected) {
        Stream.of(GLStateManager.glGetInteger(glCap), GL11.glGetInteger(glCap)).forEach(i -> assertEquals(expected, i));
    }

    void verifyState(int glCap, float expected) {
        Stream.of(GLStateManager.glGetFloat(glCap), GL11.glGetFloat(glCap)).forEach(f -> assertEquals(expected, f, 0.0001f));
    }

    void verifyState(int glCap, float[] expected) {
        final FloatBuffer glBuffer = BufferUtils.createFloatBuffer(16);
        final FloatBuffer glsmBuffer = BufferUtils.createFloatBuffer(16);

        GL11.glGetFloat(glCap, glBuffer);
        GLStateManager.glGetFloat(glCap, glsmBuffer);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], glBuffer.get(i), 0.0001f, "GL State Mismatch");
            assertEquals(expected[i], glsmBuffer.get(i),  0.0001f, "GLSM State Mismatch");
        }
    }

    private void verifyState(int glCap, boolean[] expected) {
        final ByteBuffer glBuffer = BufferUtils.createByteBuffer(16);
        final ByteBuffer glsmBuffer = BufferUtils.createByteBuffer(16);

        GL11.glGetBoolean(glCap, glBuffer);
        GLStateManager.glGetBoolean(glCap, glsmBuffer);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i] ? GL11.GL_TRUE : GL11.GL_FALSE, glBuffer.get(i), "GL State Mismatch");
            assertEquals(expected[i] ? GL11.GL_TRUE : GL11.GL_FALSE, glsmBuffer.get(i), "GLSM State Mismatch");
        }
    }

    @Test
    void testPushPopColorBufferBit() {

        GLStateManager.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT);
        /*
         * GL_COLOR_BUFFER_BIT
         *     GL_ALPHA_TEST enable bit
         *     Alpha test function and reference value
         *     GL_BLEND enable bit
         *     Blending source and destination functions
         *     Constant blend color
         *     Blending equation
         *     GL_DITHER enable bit
         *     GL_DRAW_BUFFER setting
         *     GL_COLOR_LOGIC_OP enable bit
         *     GL_INDEX_LOGIC_OP enable bit
         *     Logic op function
         *     Color mode and index mode clear values
         *     Color mode and index mode writemasks
         */

        GLStateManager.enableAlphaTest();
        GLStateManager.glAlphaFunc(GL11.GL_NEVER, 1f);
        GLStateManager.enableBlend();
        GLStateManager.glDisable(GL11.GL_DITHER);
        GLStateManager.glEnable(GL11.GL_COLOR_LOGIC_OP);
        GLStateManager.glEnable(GL11.GL_INDEX_LOGIC_OP);
        GLStateManager.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL14.glBlendColor(1f, 1f, 1f, 1f); // Not currently Implemented in GLSM
        GL20.glBlendEquationSeparate(GL14.GL_FUNC_REVERSE_SUBTRACT, GL14.GL_FUNC_REVERSE_SUBTRACT); // Not currently Implemented in GLSM
        GL11.glDrawBuffer(GL11.GL_FRONT_AND_BACK); // Not currently Implemented in GLSM
        GL11.glLogicOp(GL11.GL_OR);// Not currently Implemented in GLSM
        GLStateManager.glColor4f(0.5f, 0.5f, 0.5f, 0.5f); // This should not be reset
        GLStateManager.glColorMask(false, false, false, false);
        GLStateManager.glClearColor(0.5f, 0.5f, 0.5f, 0.5f);


        verifyState(GL11.GL_ALPHA_TEST, true);
        verifyState(GL11.GL_ALPHA_TEST_FUNC, GL11.GL_NEVER);
        verifyState(GL11.GL_ALPHA_TEST_REF, 1f);
        verifyState(GL11.GL_BLEND, true);
        verifyState(GL11.GL_BLEND_SRC, GL11.GL_SRC_ALPHA);
        verifyState(GL11.GL_BLEND_DST, GL11.GL_ONE_MINUS_SRC_ALPHA);
        verifyState(GL14.GL_BLEND_COLOR, new float[]{1f, 1f, 1f, 1f});
        verifyState(GL14.GL_BLEND_EQUATION, GL14.GL_FUNC_REVERSE_SUBTRACT);
        verifyState(GL20.GL_BLEND_EQUATION_ALPHA, GL14.GL_FUNC_REVERSE_SUBTRACT);
        verifyState(GL11.GL_DITHER, false);
        verifyState(GL11.GL_DRAW_BUFFER, GL11.GL_FRONT_AND_BACK);
        verifyState(GL11.GL_COLOR_LOGIC_OP, true);
        verifyState(GL11.GL_INDEX_LOGIC_OP, true);
        verifyState(GL11.GL_LOGIC_OP_MODE, GL11.GL_OR);
        verifyState(GL11.GL_CURRENT_COLOR, new float[]{0.5f, 0.5f, 0.5f, 0.5f});
        verifyState(GL11.GL_COLOR_WRITEMASK, new boolean[]{false, false, false, false});
        verifyState(GL11.GL_COLOR_CLEAR_VALUE, new float[]{0.5f, 0.5f, 0.5f, 0.5f});


        GLStateManager.glPopAttrib();
        verifyState(GL11.GL_ALPHA_TEST, false);
        verifyState(GL11.GL_ALPHA_TEST_FUNC, GL11.GL_ALWAYS);
        verifyState(GL11.GL_ALPHA_TEST_REF, 0f);
        verifyState(GL11.GL_BLEND, false);
        verifyState(GL11.GL_BLEND_SRC, GL11.GL_ONE);
        verifyState(GL11.GL_BLEND_DST, GL11.GL_ZERO);
        verifyState(GL14.GL_BLEND_COLOR, new float[]{0f, 0f, 0f, 0f});
        verifyState(GL14.GL_BLEND_EQUATION, GL14.GL_FUNC_ADD);
        verifyState(GL20.GL_BLEND_EQUATION_ALPHA, GL14.GL_FUNC_ADD);
        verifyState(GL11.GL_DITHER, true);
        verifyState(GL11.GL_DRAW_BUFFER, GL11.GL_BACK);
        verifyState(GL11.GL_COLOR_LOGIC_OP, false);
        verifyState(GL11.GL_INDEX_LOGIC_OP, false);
        verifyState(GL11.GL_LOGIC_OP_MODE, GL11.GL_COPY);
        verifyState(GL11.GL_CURRENT_COLOR, new float[]{0.5f, 0.5f, 0.5f, 0.5f}); // This does not get reset
        verifyState(GL11.GL_COLOR_WRITEMASK, new boolean[]{true, true, true, true});
        verifyState(GL11.GL_COLOR_CLEAR_VALUE, new float[]{0f, 0f, 0f, 0f});

    }

}
