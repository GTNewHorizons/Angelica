package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.AngelicaExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;

import static com.gtnewhorizons.angelica.util.GLSMUtil.*;

@ExtendWith(AngelicaExtension.class)
class GLSM_PushPop_UnitTest {

    public static final boolean[] BOOLEAN_ARRAY_4_TRUE = { true, true, true, true };
    public static final boolean[] BOOLEAN_ARRAY_4_FALSE = { false, false, false, false };
    public static final float[] FLOAT_ARRAY_3_001 = { 0f, 0f, 1f };
    public static final float[] FLOAT_ARRAY_3_POINT_5 = { 0.5f, 0.5f, 0.5f };
    public static final float[] FLOAT_ARRAY_4_0 = { 0f, 0f, 0f, 0f };
    public static final float[] FLOAT_ARRAY_4_POINT_5 = { 0.5f, 0.5f, 0.5f, 0.5f };
    public static final float[] FLOAT_ARRAY_4_1 = { 1f, 1f, 1f, 1f };
    public static final float[] FLOAT_ARRAY_4_0001 = { 0f, 0f, 0f, 1f };

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


        verifyIsEnabled(GL11.GL_ALPHA_TEST, true, "Alpha Test Enable");
        verifyState(GL11.GL_ALPHA_TEST_FUNC, GL11.GL_NEVER, "Alpha Test Function");
        verifyState(GL11.GL_ALPHA_TEST_REF, 1f, "Alpha Test Reference");
        verifyIsEnabled(GL11.GL_BLEND, true, "Blend Enable");
        verifyState(GL11.GL_BLEND_SRC, GL11.GL_SRC_ALPHA, "Blend Source");
        verifyState(GL11.GL_BLEND_DST, GL11.GL_ONE_MINUS_SRC_ALPHA, "Blend Destination");
        verifyState(GL14.GL_BLEND_COLOR, FLOAT_ARRAY_4_1, "Blend Color");
        verifyState(GL14.GL_BLEND_EQUATION, GL14.GL_FUNC_REVERSE_SUBTRACT, "Blend Equation");
        verifyState(GL20.GL_BLEND_EQUATION_ALPHA, GL14.GL_FUNC_REVERSE_SUBTRACT, "Blend Equation Alpha");
        verifyIsEnabled(GL11.GL_DITHER, false, "Dither Enable");
        verifyState(GL11.GL_DRAW_BUFFER, GL11.GL_FRONT_AND_BACK, "Draw Buffer");
        verifyIsEnabled(GL11.GL_COLOR_LOGIC_OP, true, "Color Logic Op Enable");
        verifyIsEnabled(GL11.GL_INDEX_LOGIC_OP, true, "Index Logic Op Enable");
        verifyState(GL11.GL_LOGIC_OP_MODE, GL11.GL_OR, "Logic Op Mode");
        verifyState(GL11.GL_CURRENT_COLOR, FLOAT_ARRAY_4_POINT_5, "Current Color");
        verifyState(GL11.GL_COLOR_WRITEMASK, BOOLEAN_ARRAY_4_FALSE, "Color Write Mask");
        verifyState(GL11.GL_COLOR_CLEAR_VALUE, FLOAT_ARRAY_4_POINT_5, "Color Clear Value");


        GLStateManager.glPopAttrib();
        verifyIsEnabled(GL11.GL_ALPHA_TEST, false, "Alpha Test Enable - Reset");
        verifyState(GL11.GL_ALPHA_TEST_FUNC, GL11.GL_ALWAYS, "Alpha Test Function - Reset");
        verifyState(GL11.GL_ALPHA_TEST_REF, 0f, "Alpha Test Reference - Reset");
        verifyIsEnabled(GL11.GL_BLEND, false, "Blend Enable - Reset");
        verifyState(GL11.GL_BLEND_SRC, GL11.GL_ONE, "Blend Source - Reset");
        verifyState(GL11.GL_BLEND_DST, GL11.GL_ZERO, "Blend Destination - Reset");
        verifyState(GL14.GL_BLEND_COLOR, FLOAT_ARRAY_4_0, "Blend Color - Reset");
        verifyState(GL14.GL_BLEND_EQUATION, GL14.GL_FUNC_ADD, "Blend Equation - Reset");
        verifyState(GL20.GL_BLEND_EQUATION_ALPHA, GL14.GL_FUNC_ADD, "Blend Equation Alpha - Reset");
        verifyIsEnabled(GL11.GL_DITHER, true, "Dither Enable - Reset");
        verifyState(GL11.GL_DRAW_BUFFER, GL11.GL_BACK, "Draw Buffer - Reset");
        verifyIsEnabled(GL11.GL_COLOR_LOGIC_OP, false, "Color Logic Op Enable - Reset");
        verifyIsEnabled(GL11.GL_INDEX_LOGIC_OP, false, "Index Logic Op Enable - Reset");
        verifyState(GL11.GL_LOGIC_OP_MODE, GL11.GL_COPY, "Logic Op Mode - Reset");
        verifyState(GL11.GL_CURRENT_COLOR, FLOAT_ARRAY_4_POINT_5, "Current Color - (Not) Reset"); // This does not get reset
        verifyState(GL11.GL_COLOR_WRITEMASK, BOOLEAN_ARRAY_4_TRUE, "Color Write Mask - Reset");
        verifyState(GL11.GL_COLOR_CLEAR_VALUE, FLOAT_ARRAY_4_0, "Color Clear Value - Reset");

        // Reset State
        GLStateManager.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);

    }

    @Test
    void testPushPopCurrentBit() {
        verifyState(GL11.GL_CURRENT_COLOR, FLOAT_ARRAY_4_1, "Initial Color State"); // Verify no state leakage from other tests
        verifyState(GL11.GL_CURRENT_TEXTURE_COORDS, FLOAT_ARRAY_4_0001, "Initial Texture Coordinates");
        verifyState(GL11.GL_CURRENT_RASTER_POSITION, FLOAT_ARRAY_4_0001, "Initial Raster Position");
        verifyState(GL11.GL_CURRENT_RASTER_POSITION_VALID, true, "Initial Raster Position Valid");
        verifyState(GL11.GL_CURRENT_RASTER_COLOR, FLOAT_ARRAY_4_1, "Initial Raster Color");
        verifyState(GL11.GL_CURRENT_RASTER_INDEX, 1, "Initial Raster Index");
        verifyState(GL11.GL_CURRENT_RASTER_TEXTURE_COORDS, FLOAT_ARRAY_4_0001, "Initial Raster Texture Coordinates");

        GLStateManager.glPushAttrib(GL11.GL_CURRENT_BIT);
        GLStateManager.glColor4f(0.5f, 0.5f, 0.5f, 0.5f);
        // Current color index
        GL11.glNormal3f(0.5f, 0.5f, 0.5f);
        GL11.glTexCoord4f(0.5f, 0.5f, 0.5f, 0.5f); // Current texture coordinates
        GL11.glRasterPos4f(0.5f, 0.5f, 0.5f, 0.5f); // Current raster position
                // GL_CURRENT_RASTER_POSITION_VALID flag
                // RGBA color associated with current raster position
                // Color index associated with current raster position
                // Texture coordinates associated with current raster position
        GL11.glEdgeFlag(false); // GL_EDGE_FLAG flag


        verifyState(GL11.GL_CURRENT_COLOR, FLOAT_ARRAY_4_POINT_5, "Current Color");
        verifyState(GL11.GL_CURRENT_NORMAL, FLOAT_ARRAY_3_POINT_5, "Current Normal");
        verifyState(GL11.GL_CURRENT_TEXTURE_COORDS, FLOAT_ARRAY_4_POINT_5, "Texture coordinates");
        verifyState(GL11.GL_CURRENT_RASTER_POSITION, new float[]{1280f, 1024f, 1f, 0.5f}, "Raster Position");
        verifyState(GL11.GL_CURRENT_RASTER_POSITION_VALID, true, "Raster Position Valid");
        verifyState(GL11.GL_CURRENT_RASTER_COLOR, FLOAT_ARRAY_4_POINT_5, "Raster Color");
        verifyState(GL11.GL_CURRENT_RASTER_INDEX, 1, "Raster Index");
        verifyState(GL11.GL_CURRENT_RASTER_TEXTURE_COORDS, FLOAT_ARRAY_4_POINT_5, "Raster Texture Coordinates");
        verifyState(GL11.GL_EDGE_FLAG, false, "Edge Flag");


        GLStateManager.glPopAttrib();
        verifyState(GL11.GL_CURRENT_COLOR, FLOAT_ARRAY_4_1, "Current Color - reset");
        verifyState(GL11.GL_CURRENT_NORMAL, FLOAT_ARRAY_3_001, "Current normal - reset");
        verifyState(GL11.GL_CURRENT_TEXTURE_COORDS, FLOAT_ARRAY_4_0001, "Texture coordinates - reset");
        verifyState(GL11.GL_CURRENT_RASTER_POSITION, FLOAT_ARRAY_4_0001, "Raster Position - reset");
        verifyState(GL11.GL_CURRENT_RASTER_POSITION_VALID, true, "Raster Position Valid - reset");
        verifyState(GL11.GL_CURRENT_RASTER_COLOR, FLOAT_ARRAY_4_1, "Raster Color - reset");
        verifyState(GL11.GL_CURRENT_RASTER_INDEX, 1, "Raster Index - reset");
        verifyState(GL11.GL_CURRENT_RASTER_TEXTURE_COORDS, FLOAT_ARRAY_4_0001, "Raster Texture Coordinates - reset");
        verifyState(GL11.GL_EDGE_FLAG, true, "Edge Flag - reset");
    }

    @Test
    void testPushPopDepthBufferBit() {
        verifyState(GL11.GL_DEPTH_WRITEMASK, true, "GL_DEPTH_WRITEMASK Initial State");

        GLStateManager.glPushAttrib(GL11.GL_DEPTH_BUFFER_BIT);
        GLStateManager.glEnable(GL11.GL_DEPTH_TEST);
        GLStateManager.glDepthFunc(GL11.GL_NEVER);
        GL11.glClearDepth(0.5f); // Not currently Implemented in GLSM
        GLStateManager.glDepthMask(false);

        verifyState(GL11.GL_DEPTH_TEST, true, "Depth Test");
        verifyState(GL11.GL_DEPTH_FUNC, GL11.GL_NEVER, "Depth Function");
        verifyState(GL11.GL_DEPTH_CLEAR_VALUE, 0.5f, "Depth Clear Value");
        verifyState(GL11.GL_DEPTH_WRITEMASK, false, "Depth Write Mask");

        GLStateManager.glPopAttrib();
        verifyState(GL11.GL_DEPTH_TEST, false, "Depth Test - Reset");
        verifyState(GL11.GL_DEPTH_FUNC, GL11.GL_LESS, "Depth Function - Reset");
        verifyState(GL11.GL_DEPTH_CLEAR_VALUE, 1f, "Depth Clear Value - Reset");
        verifyState(GL11.GL_DEPTH_WRITEMASK, true, "Depth Write Mask - Reset");
    }

}
