package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.AngelicaExtension;
import com.gtnewhorizons.angelica.util.GLBit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;
import java.util.ArrayList;

import static com.gtnewhorizons.angelica.util.GLSMUtil.verifyIsEnabled;
import static com.gtnewhorizons.angelica.util.GLSMUtil.verifyLightState;
import static com.gtnewhorizons.angelica.util.GLSMUtil.verifyNotDefaultState;
import static com.gtnewhorizons.angelica.util.GLSMUtil.verifyState;

@ExtendWith(AngelicaExtension.class)
class GLSM_PushPop_UnitTest {

    public static final boolean[] BOOLEAN_ARRAY_4_TRUE = { true, true, true, true };
    public static final boolean[] BOOLEAN_ARRAY_4_FALSE = { false, false, false, false };
    public static final float[] FLOAT_ARRAY_3_001 = { 0f, 0f, 1f };
    public static final float[] FLOAT_ARRAY_3_POINT_5 = { 0.5f, 0.5f, 0.5f };
    public static final float[] FLOAT_ARRAY_4_0 = { 0f, 0f, 0f, 0f };
    public static final float[] FLOAT_ARRAY_4_POINT_5 = { 0.5f, 0.5f, 0.5f, 0.5f };
    public static final float[] FLOAT_ARRAY_4_1 = { 1f, 1f, 1f, 1f };
    public static final float[] FLOAT_ARRAY_4_1000 = { 1f, 0f, 0f, 0f };
    public static final float[] FLOAT_ARRAY_4_0001 = { 0f, 0f, 0f, 1f };
    public static final float[] FLOAT_ARRAY_MODEL_AMBIENT_DEFAULT = { 0.2f, 0.2f, 0.2f, 1.0f };
    public static final float[] FLOAT_ARRAY_4_0010 = { 0f, 0f, 1f, 0f };
    public static final float[] FLOAT_ARRAY_3_1 = { 1f, 1f, 1f };
    public static final float[] FLOAT_ARRAY_GL_SPOT_DIRECTION_DEFAULT = { 0f, 0f, -1f };

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
        GLStateManager.glBlendColor(1f, 1f, 1f, 1f);
        GLStateManager.glBlendEquationSeparate(GL14.GL_FUNC_REVERSE_SUBTRACT, GL14.GL_FUNC_REVERSE_SUBTRACT);
        GLStateManager.glDrawBuffer(GL11.GL_FRONT_AND_BACK);
        GLStateManager.glLogicOp(GL11.GL_OR);
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
        verifyState(GL11.GL_CURRENT_INDEX, 1, "Initial Index State");
        verifyState(GL11.GL_CURRENT_TEXTURE_COORDS, FLOAT_ARRAY_4_0001, "Initial Texture Coordinates");
        verifyState(GL11.GL_CURRENT_RASTER_POSITION, FLOAT_ARRAY_4_0001, "Initial Raster Position");
        verifyState(GL11.GL_CURRENT_RASTER_POSITION_VALID, true, "Initial Raster Position Valid");
        verifyState(GL11.GL_CURRENT_RASTER_COLOR, FLOAT_ARRAY_4_1, "Initial Raster Color");
        verifyState(GL11.GL_CURRENT_RASTER_INDEX, 1, "Initial Raster Index");
        verifyState(GL11.GL_CURRENT_RASTER_TEXTURE_COORDS, FLOAT_ARRAY_4_0001, "Initial Raster Texture Coordinates");

        GLStateManager.glPushAttrib(GL11.GL_CURRENT_BIT);
        GLStateManager.glColor4f(0.5f, 0.5f, 0.5f, 0.5f);
        // Apparently glIndex is not implemented in lwjgl2?
        GLStateManager.glNormal3f(0.5f, 0.5f, 0.5f);
        GLStateManager.glTexCoord4f(0.5f, 0.5f, 0.5f, 0.5f); // Current texture coordinates
        GLStateManager.glRasterPos4f(0.5f, 0.5f, 0.5f, 0.5f); // Current raster position
                // GL_CURRENT_RASTER_POSITION_VALID flag
                // RGBA color associated with current raster position
                // Color index associated with current raster position
                // Texture coordinates associated with current raster position
        GLStateManager.glEdgeFlag(false); // GL_EDGE_FLAG flag


        verifyState(GL11.GL_CURRENT_COLOR, FLOAT_ARRAY_4_POINT_5, "Current Color");
        verifyState(GL11.GL_CURRENT_INDEX, 1f, "Current Index");
        verifyState(GL11.GL_CURRENT_NORMAL, FLOAT_ARRAY_3_POINT_5, "Current Normal");
        verifyState(GL11.GL_CURRENT_TEXTURE_COORDS, FLOAT_ARRAY_4_POINT_5, "Texture coordinates");
        verifyNotDefaultState(GL11.GL_CURRENT_RASTER_POSITION, FLOAT_ARRAY_4_0001, "Raster Position");
        verifyState(GL11.GL_CURRENT_RASTER_POSITION_VALID, true, "Raster Position Valid");
        verifyState(GL11.GL_CURRENT_RASTER_COLOR, FLOAT_ARRAY_4_POINT_5, "Raster Color");
        verifyState(GL11.GL_CURRENT_RASTER_INDEX, 1, "Raster Index");
        verifyState(GL11.GL_CURRENT_RASTER_TEXTURE_COORDS, FLOAT_ARRAY_4_POINT_5, "Raster Texture Coordinates");
        verifyState(GL11.GL_EDGE_FLAG, false, "Edge Flag");


        GLStateManager.glPopAttrib();
        verifyState(GL11.GL_CURRENT_COLOR, FLOAT_ARRAY_4_1, "Current Color - reset");
        verifyState(GL11.GL_CURRENT_INDEX, 1, "Current Index - reset");
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
        GLStateManager.glClearDepth(0.5f); // Not currently Implemented in GLSM
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


    @Test
    void testPushPopEnableBit() {
        final ArrayList<GLBit> bits = new ArrayList<>();
        int i;

        bits.add(new GLBit(GL11.GL_ALPHA_TEST, "Alpha Test", false));
        bits.add(new GLBit(GL11.GL_AUTO_NORMAL, "Auto Normal", false));
        bits.add(new GLBit(GL11.GL_BLEND, "Blend", false));
        for(i = 0 ; i < GL11.glGetInteger(GL11.GL_MAX_CLIP_PLANES) ;  i++) {
            bits.add(new GLBit(GL11.GL_CLIP_PLANE0 + i, "Clip Plane " + i, false));
        }
        bits.add(new GLBit(GL11.GL_COLOR_MATERIAL, "Color Material", false));
        bits.add(new GLBit(GL11.GL_CULL_FACE, "Cull Face", false));
        bits.add(new GLBit(GL11.GL_DEPTH_TEST, "Depth Test", false));
        bits.add(new GLBit(GL11.GL_DITHER, "Dither", true));
        bits.add(new GLBit(GL11.GL_FOG, "Fog", false));
        // This fails on the RESET test in xvfb
        if(!(GLStateManager.MESA || GLStateManager.AMD)) {
            for(i = 0 ; i < GL11.glGetInteger(GL11.GL_MAX_LIGHTS) ;  i++) {
                bits.add(new GLBit(GL11.GL_LIGHT0 + i, "Light " + i, false));
            }
        }
        bits.add(new GLBit(GL11.GL_LIGHTING, "Lighting", false));
        bits.add(new GLBit(GL11.GL_LINE_SMOOTH, "Line Smooth", false));
        bits.add(new GLBit(GL11.GL_LINE_STIPPLE, "Line Stipple", false));
        bits.add(new GLBit(GL11.GL_COLOR_LOGIC_OP, "Color Logic Op", false));
        bits.add(new GLBit(GL11.GL_INDEX_LOGIC_OP, "Index Logic Op", false))
        ;
        bits.add(new GLBit(GL11.GL_MAP1_VERTEX_3, "Map1 Vertex 3", false));
        bits.add(new GLBit(GL11.GL_MAP1_VERTEX_4, "Map1 Vertex 4", false));
        bits.add(new GLBit(GL11.GL_MAP1_INDEX, "Map1 Index", false));
        bits.add(new GLBit(GL11.GL_MAP1_COLOR_4, "Map1 Color 4", false));
        bits.add(new GLBit(GL11.GL_MAP1_NORMAL, "Map1 Normal", false));
        bits.add(new GLBit(GL11.GL_MAP1_TEXTURE_COORD_1, "Map1 Texture Coord 1", false));
        bits.add(new GLBit(GL11.GL_MAP1_TEXTURE_COORD_2, "Map1 Texture Coord 2", false));
        bits.add(new GLBit(GL11.GL_MAP1_TEXTURE_COORD_3, "Map1 Texture Coord 3", false));
        bits.add(new GLBit(GL11.GL_MAP1_TEXTURE_COORD_4, "Map1 Texture Coord 4", false));

        bits.add(new GLBit(GL11.GL_MAP2_VERTEX_3, "Map2 Vertex 3", false));
        bits.add(new GLBit(GL11.GL_MAP2_VERTEX_4, "Map2 Vertex 4", false));
        bits.add(new GLBit(GL11.GL_MAP2_INDEX, "Map2 Index", false));
        bits.add(new GLBit(GL11.GL_MAP2_COLOR_4, "Map2 Color 4", false));
        bits.add(new GLBit(GL11.GL_MAP2_NORMAL, "Map2 Normal", false));
        bits.add(new GLBit(GL11.GL_MAP2_TEXTURE_COORD_1, "Map2 Texture Coord 1", false));
        bits.add(new GLBit(GL11.GL_MAP2_TEXTURE_COORD_2, "Map2 Texture Coord 2", false));
        bits.add(new GLBit(GL11.GL_MAP2_TEXTURE_COORD_3, "Map2 Texture Coord 3", false));
        bits.add(new GLBit(GL11.GL_MAP2_TEXTURE_COORD_4, "Map2 Texture Coord 4", false));
        // Seems to be broken at least on Nvidia
        if(!GLStateManager.NVIDIA) bits.add(new GLBit(GL13.GL_MULTISAMPLE, "Multisample", true));
        bits.add(new GLBit(GL11.GL_NORMALIZE, "Normalize", false));
        bits.add(new GLBit(GL11.GL_POINT_SMOOTH, "Point Smooth", false));
        bits.add(new GLBit(GL11.GL_POLYGON_OFFSET_LINE, "Polygon Offset Line", false));
        bits.add(new GLBit(GL11.GL_POLYGON_OFFSET_FILL, "Polygon Offset Fill", false));
        bits.add(new GLBit(GL11.GL_POLYGON_OFFSET_POINT, "Polygon Offset Point", false));
        bits.add(new GLBit(GL11.GL_POLYGON_SMOOTH, "Polygon Smooth", false));
        bits.add(new GLBit(GL11.GL_POLYGON_STIPPLE, "Polygon Stipple", false));
        bits.add(new GLBit(GL13.GL_SAMPLE_ALPHA_TO_COVERAGE, "Sample Alpha To Coverage", false));
        bits.add(new GLBit(GL13.GL_SAMPLE_ALPHA_TO_ONE, "Sample Alpha To One", false));
        bits.add(new GLBit(GL13.GL_SAMPLE_COVERAGE, "Sample Coverage", false));
        bits.add(new GLBit(GL11.GL_SCISSOR_TEST, "Scissor Test", false));
        bits.add(new GLBit(GL11.GL_STENCIL_TEST, "Stencil Test", false));
        bits.add(new GLBit(GL11.GL_TEXTURE_1D, "Texture 1D", false));
        bits.add(new GLBit(GL11.GL_TEXTURE_2D, "Texture 2D", false));
        bits.add(new GLBit(GL12.GL_TEXTURE_3D, "Texture 3D", false));
        bits.add(new GLBit(GL11.GL_TEXTURE_GEN_S, "Texture Gen S", false));
        bits.add(new GLBit(GL11.GL_TEXTURE_GEN_T, "Texture Gen T", false));
        bits.add(new GLBit(GL11.GL_TEXTURE_GEN_R, "Texture Gen R", false));
        bits.add(new GLBit(GL11.GL_TEXTURE_GEN_Q, "Texture Gen Q", false));

        GLStateManager.glPushAttrib(GL11.GL_ENABLE_BIT);
        bits.forEach(bit -> {
            verifyState(bit.glEnum(), bit.initial(), bit.name() + " Initial State");
            if(bit.initial()) {
                GLStateManager.glDisable(bit.glEnum());
            } else {
                GLStateManager.glEnable(bit.glEnum());
            }
            verifyState(bit.glEnum(), !bit.initial(), bit.name() + " Toggle State");
        });

        GLStateManager.glPopAttrib();
        bits.forEach(bit -> verifyState(bit.glEnum(), bit.initial(), bit.name() + " Reset State"));

    }

    @Test
    void testPushPopFogBit() {
        final FloatBuffer floatBuffer = BufferUtils.createFloatBuffer(16);
        GLStateManager.glPushAttrib(GL11.GL_FOG_BIT);
        GLStateManager.glEnable(GL11.GL_FOG);
        floatBuffer.put(FLOAT_ARRAY_4_POINT_5).flip();
        GLStateManager.glFog(GL11.GL_FOG_COLOR, floatBuffer);

        GLStateManager.glFogf(GL11.GL_FOG_DENSITY, 0.5f);
        GLStateManager.glFogf(GL11.GL_FOG_END, 0.5f);
        GLStateManager.glFogf(GL11.GL_FOG_START, 0.5f);
        GLStateManager.glFogf(GL11.GL_FOG_MODE, GL11.GL_LINEAR);
        if(!GLStateManager.NVIDIA) GLStateManager.glFogf(GL11.GL_FOG_INDEX, 1f);

        verifyIsEnabled(GL11.GL_FOG, true, "Fog Enable");
        verifyState(GL11.GL_FOG_COLOR, FLOAT_ARRAY_4_POINT_5, "Fog Color");
        verifyState(GL11.GL_FOG_DENSITY, 0.5f, "Fog Density");
        verifyState(GL11.GL_FOG_END, 0.5f, "Fog End");
        verifyState(GL11.GL_FOG_START, 0.5f, "Fog Start");
        verifyState(GL11.GL_FOG_MODE, GL11.GL_LINEAR, "Fog Mode");
        if(!GLStateManager.NVIDIA) verifyState(GL11.GL_FOG_INDEX, 1f, "Fog Index");

        GLStateManager.glPopAttrib();
        verifyIsEnabled(GL11.GL_FOG, false, "Fog Enable - Reset");
        verifyState(GL11.GL_FOG_COLOR, FLOAT_ARRAY_4_0, "Fog Color - Reset");
        verifyState(GL11.GL_FOG_DENSITY, 1f, "Fog Density - Reset");
        verifyState(GL11.GL_FOG_END, 1f, "Fog End - Reset");
        verifyState(GL11.GL_FOG_START, 0f, "Fog Start - Reset");
        verifyState(GL11.GL_FOG_MODE, GL11.GL_EXP, "Fog Mode - Reset");
        if(!GLStateManager.NVIDIA) verifyState(GL11.GL_FOG_INDEX, 0f, "Fog Index - Reset");
    }

    @Test
    void testPushPopLightingBit() {
        final ArrayList<GLBit> bits = new ArrayList<>();
        final FloatBuffer floatBuffer = BufferUtils.createFloatBuffer(16);

        bits.add(new GLBit(GL11.GL_COLOR_MATERIAL, "Color Material", false));
        bits.add(new GLBit(GL11.GL_LIGHTING, "Lighting", false));
        for(int i = 0 ; i < GL11.glGetInteger(GL11.GL_MAX_LIGHTS) ;  i++) {
            bits.add(new GLBit(GL11.GL_LIGHT0 + i, "Light " + i, false));
        }

        GLStateManager.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GLStateManager.glColorMaterial(GL11.GL_FRONT, GL11.GL_AMBIENT);
        // Ambient scene color ??
        GLStateManager.glLightModelf(GL11.GL_LIGHT_MODEL_LOCAL_VIEWER, 1f);
        GLStateManager.glLightModelf(GL11.GL_LIGHT_MODEL_TWO_SIDE, 1f);
        floatBuffer.put(FLOAT_ARRAY_4_POINT_5).flip();
        GLStateManager.glLightModel(GL11.GL_LIGHT_MODEL_AMBIENT, floatBuffer);
        GLStateManager.glLightModeli(GL12.GL_LIGHT_MODEL_COLOR_CONTROL, GL12.GL_SEPARATE_SPECULAR_COLOR);

        floatBuffer.put(FLOAT_ARRAY_4_0010).flip();
        GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_SPOT_DIRECTION, floatBuffer);
        floatBuffer.put(FLOAT_ARRAY_4_0).flip();
        GLStateManager.glShadeModel(GL11.GL_FLAT);


        verifyState(GL11.GL_COLOR_MATERIAL_FACE, GL11.GL_FRONT, "Color Material Face");
        verifyState(GL11.GL_COLOR_MATERIAL_PARAMETER, GL11.GL_AMBIENT, "Color Material Parameter");
        verifyState(GL11.GL_LIGHT_MODEL_LOCAL_VIEWER, 1f, "Light Model Local Viewer");
        verifyState(GL11.GL_LIGHT_MODEL_TWO_SIDE, 1f, "Light Model Two Side");
        verifyState(GL11.GL_LIGHT_MODEL_AMBIENT, FLOAT_ARRAY_4_POINT_5, "Light Model Ambient");
        verifyState(GL12.GL_LIGHT_MODEL_COLOR_CONTROL, GL12.GL_SEPARATE_SPECULAR_COLOR, "Light Model Color Control");
        verifyState(GL11.GL_SHADE_MODEL, GL11.GL_FLAT, "Shade Model");


        bits.forEach(bit -> {
            verifyState(bit.glEnum(), bit.initial(), bit.name() + " Initial State");
            if(bit.initial()) {
                GLStateManager.glDisable(bit.glEnum());
            } else {
                GLStateManager.glEnable(bit.glEnum());
            }
            verifyState(bit.glEnum(), !bit.initial(), bit.name() + " Toggle State");
        });

        GLStateManager.glPopAttrib();
        verifyState(GL11.GL_COLOR_MATERIAL_FACE, GL11.GL_FRONT_AND_BACK, "Color Material Face - Reset");
        verifyState(GL11.GL_COLOR_MATERIAL_PARAMETER, GL11.GL_AMBIENT_AND_DIFFUSE, "Color Material Parameter - Reset");
        verifyState(GL11.GL_LIGHT_MODEL_LOCAL_VIEWER, 0f, "Light Model Local Viewer - Reset");
        verifyState(GL11.GL_LIGHT_MODEL_TWO_SIDE, 0f, "Light Model Two Side - Reset");
        verifyState(GL11.GL_LIGHT_MODEL_AMBIENT, FLOAT_ARRAY_MODEL_AMBIENT_DEFAULT, "Light Model Ambient - Reset");
        verifyState(GL12.GL_LIGHT_MODEL_COLOR_CONTROL, GL12.GL_SINGLE_COLOR, "Light Model Color Control - Reset");
        verifyState(GL11.GL_SHADE_MODEL, GL11.GL_SMOOTH, "Shade Model - Reset");

        bits.forEach(bit -> verifyState(bit.glEnum(), bit.initial(), bit.name() + " Reset State"));
    }

    @Test
    void testPushPopTextureBit() {
        final ArrayList<GLBit> bits = new ArrayList<>();
        bits.add(new GLBit(GL11.GL_TEXTURE_GEN_S, "Texture Gen S", false));
        bits.add(new GLBit(GL11.GL_TEXTURE_GEN_T, "Texture Gen T", false));
        bits.add(new GLBit(GL11.GL_TEXTURE_GEN_R, "Texture Gen R", false));
        bits.add(new GLBit(GL11.GL_TEXTURE_GEN_Q, "Texture Gen Q", false));
        verifyState(GL13.GL_ACTIVE_TEXTURE, GL13.GL_TEXTURE0, "Initial Active Texture");
        verifyState(GL11.GL_TEXTURE_BINDING_2D, 0, "Initial Texture Binding");

        final int tex1 = GL11.glGenTextures();
        final int tex2 = GL11.glGenTextures();

        GLStateManager.glPushAttrib(GL11.GL_TEXTURE_BIT);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, tex1);
        verifyState(GL11.GL_TEXTURE_BINDING_2D, tex1, "Texture Binding - Unit 0");

        GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
        verifyState(GL13.GL_ACTIVE_TEXTURE, GL13.GL_TEXTURE1, "Active Texture");

        verifyState(GL11.GL_TEXTURE_BINDING_2D, 0, "Texture Binding - Unit 1 - Switch");
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, tex2);
        verifyState(GL11.GL_TEXTURE_BINDING_2D, tex2, "Texture Binding - Unit 1 - Set");

        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        verifyState(GL13.GL_ACTIVE_TEXTURE, GL13.GL_TEXTURE0, "Active Texture");
        verifyState(GL11.GL_TEXTURE_BINDING_2D, tex1, "Texture Binding - Unit 0");

        GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
        verifyState(GL13.GL_ACTIVE_TEXTURE, GL13.GL_TEXTURE1, "Active Texture");
        verifyState(GL11.GL_TEXTURE_BINDING_2D, tex2, "Texture Binding - Unit 1");

        bits.forEach(bit -> {
            verifyState(bit.glEnum(), bit.initial(), bit.name() + " Initial State");
            if(bit.initial()) {
                GLStateManager.glDisable(bit.glEnum());
            } else {
                GLStateManager.glEnable(bit.glEnum());
            }
            verifyState(bit.glEnum(), !bit.initial(), bit.name() + " Toggle State");
        });

        GLStateManager.glPopAttrib();
        verifyState(GL13.GL_ACTIVE_TEXTURE, GL13.GL_TEXTURE0, "Active Texture Unit 0 - Reset");
        verifyState(GL11.GL_TEXTURE_BINDING_2D, 0, "Texture Binding Unit 0 - Reset");
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
        verifyState(GL11.GL_TEXTURE_BINDING_2D, 0, "Texture Binding Unit 1 - Reset");

        bits.forEach(bit -> verifyState(bit.glEnum(), bit.initial(), bit.name() + " Reset State"));

        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glDeleteTextures(tex1);
        GLStateManager.glDeleteTextures(tex2);
    }

    @Test
    void testPushPopTextureBitMultiple() {
        verifyState(GL13.GL_ACTIVE_TEXTURE, GL13.GL_TEXTURE0, "Initial Active Texture");
        verifyState(GL11.GL_TEXTURE_BINDING_2D, 0, "Initial Texture Binding");

        final int tex1 = GL11.glGenTextures();
        final int tex2 = GL11.glGenTextures();

        GLStateManager.glPushAttrib(GL11.GL_TEXTURE_BIT);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, tex1);
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, tex1);

        GLStateManager.glPushAttrib(GL11.GL_TEXTURE_BIT);
        verifyState(GL13.GL_ACTIVE_TEXTURE, GL13.GL_TEXTURE1, "Active Texture");
        verifyState(GL11.GL_TEXTURE_BINDING_2D, tex1, "Texture Binding - Unit 1");
        GLStateManager.glDeleteTextures(tex2);
        verifyState(GL11.GL_TEXTURE_BINDING_2D, tex1, "Texture Binding - Unit 1");
        GLStateManager.glDeleteTextures(tex1);
        verifyState(GL11.GL_TEXTURE_BINDING_2D, 0, "Texture Binding Deleted - Unit 1");
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        verifyState(GL11.GL_TEXTURE_BINDING_2D, 0, "Texture Binding Deleted - Unit 0");

        GLStateManager.glPopAttrib();
        verifyState(GL13.GL_ACTIVE_TEXTURE, GL13.GL_TEXTURE1, "Active Texture - Reset 1");
        verifyState(GL11.GL_TEXTURE_BINDING_2D, tex1, "Texture Binding Deleted - Unit 1");
        GLStateManager.glDeleteTextures(tex1);
        verifyState(GL11.GL_TEXTURE_BINDING_2D, 0, "Texture Binding Deleted - Unit 1");
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        verifyState(GL11.GL_TEXTURE_BINDING_2D, 0, "Texture Binding Deleted - Unit 0");

        GLStateManager.glPopAttrib();
        verifyState(GL13.GL_ACTIVE_TEXTURE, GL13.GL_TEXTURE0, "Active Texture - Reset 2");
        verifyState(GL11.GL_TEXTURE_BINDING_2D, 0, "Texture Binding - Reset 2");
    }

    @Test
    void testPushPopTransformBit() {
        verifyState(GL11.GL_MATRIX_MODE, GL11.GL_MODELVIEW, "Initial Matrix Mode");
        verifyState(GL11.GL_NORMALIZE, false, "Initial Normalize");
        verifyState(GL12.GL_RESCALE_NORMAL, false, "Initial Rescale Normal");

        GLStateManager.glPushAttrib(GL11.GL_TRANSFORM_BIT);
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glEnable(GL11.GL_NORMALIZE);
        GLStateManager.glEnable(GL12.GL_RESCALE_NORMAL);

        verifyState(GL11.GL_MATRIX_MODE, GL11.GL_PROJECTION, "Matrix Mode");
        verifyState(GL11.GL_NORMALIZE, true, "Normalize");
        verifyState(GL12.GL_RESCALE_NORMAL, true, "Rescale Normal");

        GLStateManager.glPopAttrib();
        verifyState(GL11.GL_MATRIX_MODE, GL11.GL_MODELVIEW, "Matrix Mode - Reset");
        verifyState(GL11.GL_NORMALIZE, false, "Normalize - Reset");
        verifyState(GL12.GL_RESCALE_NORMAL, false, "Rescale Normal - Reset");
    }

    @Test
    void testPushPopViewportBit() {
        GLStateManager.glViewport(0, 0, 800, 600);
        verifyState(GL11.GL_VIEWPORT, new int[] { 0, 0, 800, 600 }, "Viewport - Initial");
        verifyState(GL11.GL_DEPTH_RANGE, new float[] { 0.0f, 1.0f }, "Depth Range - Initial");

        GLStateManager.glPushAttrib(GL11.GL_VIEWPORT_BIT);
        GLStateManager.glViewport(1, 1, 100, 100);
        GLStateManager.glDepthRange(0.5, 1.0);

        verifyState(GL11.GL_VIEWPORT, new int[] { 1, 1, 100, 100 }, "Viewport");
        verifyState(GL11.GL_DEPTH_RANGE, new float[] { 0.5f, 1.0f }, "Depth Range");

        GLStateManager.glPopAttrib();
        verifyState(GL11.GL_VIEWPORT, new int[] { 0, 0, 800, 600 }, "Viewport - Reset");
        verifyState(GL11.GL_DEPTH_RANGE, new float[] { 0.0f, 1.0f }, "Depth Range - Reset");
    }
}
