package com.gtnewhorizons.angelica.glsm.debug;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

// Adapted from https://github.com/makamys/CoreTweaks/blob/master/src/main/java/makamys/coretweaks/util/OpenGLDebugging.java;
// originally from https://github.com/TheGreyGhost/MinecraftByExample/blob/1-8final/src/main/java/minecraftbyexample/usefultools/OpenGLdebugging.java

public class OpenGLDebugging {

    public static final Logger LOGGER = LogManager.getLogger("gldumper");

    private static final Consumer<String> LINE_LOGGER = LOGGER::debug;
    public static final String GL_IS_ENABLED = "glIsEnabled()";
    public static final String GL_GET_BOOLEANV = "glGetBooleanv()";
    public static final String GL_GET_INTEGERV = "glGetIntegerv()";
    public static final String GL_GET_FLOATV = "glGetFloatv()";

    public class GLproperty {

        public GLproperty(int glConstant, String name, String description, String category, String fetchCommand) {
            this.gLConstant = glConstant;
            this.name = name;
            this.description = description;
            this.category = category;
            this.fetchCommand = fetchCommand;
        }

        public int gLConstant;
        public String name;
        public String description;
        public String category;
        public String fetchCommand;

        static final ByteBuffer byteBuffer = BufferUtils.createByteBuffer(16);
        static final IntBuffer intBuffer = BufferUtils.createIntBuffer(16);
        static final FloatBuffer floatBuffer = BufferUtils.createFloatBuffer(16);

        public String getAsString(boolean glsm) {
            switch(fetchCommand) {
                case GL_IS_ENABLED:  {
                    return "" + (glsm ? GLStateManager.glIsEnabled(gLConstant) : GL11.glIsEnabled(gLConstant));
                }
                case GL_GET_BOOLEANV:  {
                    if(GLStateManager.HAS_MULTIPLE_SET.contains(gLConstant)) {
                        byteBuffer.clear();
                        if(glsm) {
                            GLStateManager.glGetBoolean(gLConstant, byteBuffer);
                        } else {
                            GL11.glGetBoolean(gLConstant, byteBuffer);
                        }
                        final StringBuilder out = new StringBuilder();
                        for (int i = 0; i < byteBuffer.capacity(); ++i) {
                            out.append(i == 0 ? "" : ", ").append(byteBuffer.get(i));
                        }
                        return out.toString();
                    } else {
                        return "" + (glsm ? GLStateManager.glGetBoolean(gLConstant) : GL11.glGetBoolean(gLConstant));
                    }
                }
                case GL_GET_INTEGERV:  {
                    if(GLStateManager.HAS_MULTIPLE_SET.contains(gLConstant)) {
                        intBuffer.clear();
                        if(glsm) {
                            GLStateManager.glGetInteger(gLConstant, intBuffer);
                        } else {
                            GL11.glGetInteger(gLConstant, intBuffer);
                        }
                        final StringBuilder out = new StringBuilder();
                        for (int i = 0; i < intBuffer.remaining(); ++i) {
                            out.append(i == 0 ? "" : ", ").append(intBuffer.get(i));
                        }
                        return out.toString();
                    } else {
                        return "" + (glsm ? GLStateManager.glGetInteger(gLConstant) : GL11.glGetInteger(gLConstant));
                    }
                }
                case GL_GET_FLOATV: {
                    if(GLStateManager.HAS_MULTIPLE_SET.contains(gLConstant)) {
                        floatBuffer.clear();
                        if(glsm) {
                            GLStateManager.glGetFloat(gLConstant, floatBuffer);
                        } else {
                            GL11.glGetFloat(gLConstant, floatBuffer);
                        }
                        final StringBuilder out = new StringBuilder();
                        for (int i = 0; i < floatBuffer.remaining(); ++i) {
                            out.append(i == 0 ? "" : ", ").append(String.format("%f", floatBuffer.get(i)));
                        }
                        return out.toString();
                    } else {
                        return "" + (glsm ? GLStateManager.glGetFloat(gLConstant) : GL11.glGetFloat(gLConstant));
                    }
                }
                default: return "";
            }

        }
        static final Matrix4f cachedMatrix = new Matrix4f();
        static final Matrix4f uncachedMatrix = new Matrix4f();
        public Matrix4f getAsMatrix4f(boolean glsm) {
            if(!fetchCommand.equals(GL_GET_FLOATV)) {
                throw new IllegalArgumentException("This property does not return a matrix");
            }
            floatBuffer.clear();
            if(glsm) {
                GLStateManager.glGetFloat(gLConstant, floatBuffer);
                return cachedMatrix.set(0, floatBuffer);
            } else {
                GL11.glGetFloat(gLConstant, floatBuffer);
                return uncachedMatrix.set(0, floatBuffer);
            }
        }

    }


    public static OpenGLDebugging instance = new OpenGLDebugging();

    public GLproperty[] propertyList = {
        new GLproperty(GL11.GL_ACCUM_ALPHA_BITS, "GL_ACCUM_ALPHA_BITS", "Number of bits per alpha component in the accumulation buffer", "capability", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_ACCUM_BLUE_BITS, "GL_ACCUM_BLUE_BITS", "Number of bits per blue component in the accumulation buffer", "capability", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_ACCUM_CLEAR_VALUE, "GL_ACCUM_CLEAR_VALUE", "Accumulation-buffer clear value", "accum-buffer", GL_GET_FLOATV),
        new GLproperty(GL11.GL_ACCUM_GREEN_BITS, "GL_ACCUM_GREEN_BITS", "Number of bits per green component in the accumulation buffer", "capability", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_ACCUM_RED_BITS, "GL_ACCUM_RED_BITS", "Number of bits per red component in the accumulation buffer", "capability", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_ALPHA_BITS, "GL_ALPHA_BITS", "Number of bits per alpha component in color buffers", "capability", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_ALPHA_TEST, "GL_ALPHA_TEST", "Alpha test enabled", "color-buffer/enable", GL_IS_ENABLED),
        new GLproperty(GL11.GL_ALPHA_TEST_FUNC, "GL_ALPHA_TEST_FUNC", "Alpha test function", "color-buffer", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_ALPHA_TEST_REF, "GL_ALPHA_TEST_REF", "Alpha test reference value", "color-buffer", GL_GET_FLOATV),
        new GLproperty(GL11.GL_AMBIENT, "GL_AMBIENT", "Ambient intensity of light i", "lighting", "glGetLightfv()"),
        new GLproperty(GL11.GL_AMBIENT, "GL_AMBIENT", "Ambient material color", "lighting", "glGetMaterialfv()"),
        new GLproperty(GL11.GL_ATTRIB_STACK_DEPTH, "GL_ATTRIB_STACK_DEPTH", "Attribute stack pointer", "current", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_AUTO_NORMAL, "GL_AUTO_NORMAL", "True if automatic normal generation enabled", "eval", GL_IS_ENABLED),
        new GLproperty(GL11.GL_AUX_BUFFERS, "GL_AUX_BUFFERS", "Number of auxiliary buffers", "capability", GL_GET_BOOLEANV),
        new GLproperty(GL11.GL_BLEND, "GL_BLEND", "Blending enabled", "color-buffer/enable", GL_IS_ENABLED),
        new GLproperty(GL11.GL_BLEND_DST, "GL_BLEND_DST", "Blending destination function", "color-buffer", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_BLEND_SRC, "GL_BLEND_SRC", "Blending source function", "color-buffer", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_BLUE_BITS, "GL_BLUE_BITS", "Number of bits per blue component in color buffers", "capability", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_CLIENT_ATTRIB_STACK_DEPTH, "GL_CLIENT_ATTRIB_STACK_DEPTH", "Client attribute stack pointer", "current", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_COEFF, "GL_COEFF", "1D control points", "capability", "glGetMapfv()"),
        new GLproperty(GL11.GL_COEFF, "GL_COEFF", "2D control points", "capability", "glGetMapfv()"),
        new GLproperty(GL11.GL_COLOR_ARRAY, "GL_COLOR_ARRAY", "RGBA color array enable", "vertex-array", GL_IS_ENABLED),
        new GLproperty(GL11.GL_COLOR_ARRAY_POINTER, "GL_COLOR_ARRAY_POINTER", "Pointer to the color array", "vertex-array", "glGetPointerv()"),
        new GLproperty(GL11.GL_COLOR_ARRAY_SIZE, "GL_COLOR_ARRAY_SIZE", "Colors per vertex", "vertex-array", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_COLOR_ARRAY_STRIDE, "GL_COLOR_ARRAY_STRIDE", "Stride between colors", "vertex-array", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_COLOR_ARRAY_TYPE, "GL_COLOR_ARRAY_TYPE", "Type of color components", "vertex-array", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_COLOR_CLEAR_VALUE, "GL_COLOR_CLEAR_VALUE", "Color-buffer clear value (RGBA mode)", "color-buffer", GL_GET_FLOATV),
        new GLproperty(GL11.GL_COLOR_INDEXES, "GL_COLOR_INDEXES", "ca, cd, and cs for color-index lighting", "lighting/e nable", "glGetMaterialfv()"),
        new GLproperty(GL11.GL_COLOR_LOGIC_OP, "GL_COLOR_LOGIC_OP", "RGBA color logical operation enabled", "color-buffer/enable", GL_IS_ENABLED),
        new GLproperty(GL11.GL_COLOR_MATERIAL, "GL_COLOR_MATERIAL", "True if color tracking is enabled", "lighting", GL_IS_ENABLED),
        new GLproperty(GL11.GL_COLOR_MATERIAL_FACE, "GL_COLOR_MATERIAL_FACE", "Face(s) affected by color tracking", "lighting", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_COLOR_MATERIAL_PARAMETER, "GL_COLOR_MATERIAL_PARAMETER", "Material properties tracking current color", "lighting", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_COLOR_WRITEMASK, "GL_COLOR_WRITEMASK", "Color write enables; R, G, B, or A", "color-buffer", GL_GET_BOOLEANV),
        new GLproperty(GL11.GL_CONSTANT_ATTENUATION, "GL_CONSTANT_ATTENUATION", "Constant attenuation factor", "lighting", "glGetLightfv()"),
        new GLproperty(GL11.GL_CULL_FACE, "GL_CULL_FACE", "Polygon culling enabled", "polygon/enable", GL_IS_ENABLED),
        new GLproperty(GL11.GL_CULL_FACE_MODE, "GL_CULL_FACE_MODE", "Cull front-/back-facing polygons", "polygon", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_CURRENT_COLOR, "GL_CURRENT_COLOR", "Current color", "current", GL_GET_FLOATV),
        new GLproperty(GL11.GL_CURRENT_INDEX, "GL_CURRENT_INDEX", "Current color index", "current", GL_GET_FLOATV),
        new GLproperty(GL11.GL_CURRENT_NORMAL, "GL_CURRENT_NORMAL", "Current normal", "current", GL_GET_FLOATV),
        new GLproperty(GL11.GL_CURRENT_RASTER_COLOR, "GL_CURRENT_RASTER_COLOR", "Color associated with raster position", "current", GL_GET_FLOATV),
        new GLproperty(GL11.GL_CURRENT_RASTER_DISTANCE, "GL_CURRENT_RASTER_DISTANCE", "Current raster distance", "current", GL_GET_FLOATV),
        new GLproperty(GL11.GL_CURRENT_RASTER_INDEX, "GL_CURRENT_RASTER_INDEX", "Color index associated with raster position", "current", GL_GET_FLOATV),
        new GLproperty(GL11.GL_CURRENT_RASTER_POSITION, "GL_CURRENT_RASTER_POSITION", "Current raster position", "current", GL_GET_FLOATV),
        new GLproperty(GL11.GL_CURRENT_RASTER_POSITION_VALID, "GL_CURRENT_RASTER_POSITION_VALID", "Raster position valid bit", "current", GL_GET_BOOLEANV),
        new GLproperty(GL11.GL_CURRENT_RASTER_TEXTURE_COORDS, "GL_CURRENT_RASTER_TEXTURE_COORDS", "Texture coordinates associated with raster position", "current", GL_GET_FLOATV),
        new GLproperty(GL11.GL_CURRENT_TEXTURE_COORDS, "GL_CURRENT_TEXTURE_COORDS", "Current texture coordinates", "current", GL_GET_FLOATV),
        new GLproperty(GL11.GL_DEPTH_BITS, "GL_DEPTH_BITS", "Number of depth-buffer bitplanes", "capability", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_DEPTH_CLEAR_VALUE, "GL_DEPTH_CLEAR_VALUE", "Depth-buffer clear value", "depth-buffer", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_DEPTH_FUNC, "GL_DEPTH_FUNC", "Depth buffer test function", "depth-buffer", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_DEPTH_RANGE, "GL_DEPTH_RANGE", "Depth range near and far", "viewport", GL_GET_FLOATV),
        new GLproperty(GL11.GL_DEPTH_TEST, "GL_DEPTH_TEST", "Depth buffer enabled", "depth-buffer/ena ble", GL_IS_ENABLED),
        new GLproperty(GL11.GL_DEPTH_WRITEMASK, "GL_DEPTH_WRITEMASK", "Depth buffer enabled for writing", "depth-buffer", GL_GET_BOOLEANV),
        new GLproperty(GL11.GL_DIFFUSE, "GL_DIFFUSE", "Diffuse intensity of light i", "lighting", "glGetLightfv()"),
        new GLproperty(GL11.GL_DIFFUSE, "GL_DIFFUSE", "Diffuse material color", "lighting", "glGetMaterialfv()"),
        new GLproperty(GL11.GL_DITHER, "GL_DITHER", "Dithering enabled", "color-buffer/enable", GL_IS_ENABLED),
        new GLproperty(GL11.GL_DOMAIN, "GL_DOMAIN", "1D domain endpoints", "capability", "glGetMapfv()"),
        new GLproperty(GL11.GL_DOMAIN, "GL_DOMAIN", "2D domain endpoints", "capability", "glGetMapfv()"),
        new GLproperty(GL11.GL_DOUBLEBUFFER, "GL_DOUBLEBUFFER", "True if front and back buffers exist", "capability", GL_GET_BOOLEANV),
        new GLproperty(GL11.GL_DRAW_BUFFER, "GL_DRAW_BUFFER", "Buffers selected for drawing", "color-buffer", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_EDGE_FLAG, "GL_EDGE_FLAG", "Edge flag", "current", GL_GET_BOOLEANV),
        new GLproperty(GL11.GL_EDGE_FLAG_ARRAY, "GL_EDGE_FLAG_ARRAY", "Edge flag array enable", "vertex-array", GL_IS_ENABLED),
        new GLproperty(GL11.GL_EDGE_FLAG_ARRAY_POINTER, "GL_EDGE_FLAG_ARRAY_POINTER", "Pointer to the edge flag array", "vertex-array", "glGetPointerv()"),
        new GLproperty(GL11.GL_EDGE_FLAG_ARRAY_STRIDE, "GL_EDGE_FLAG_ARRAY_STRIDE", "Stride between edge flags", "vertex-array", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_EMISSION, "GL_EMISSION", "Emissive material color", "lighting", "glGetMaterialfv()"),
        new GLproperty(GL11.GL_EYE_PLANE, "GL_EYE_PLANE", "Texgen plane equation coefficients", "texture", "glGetTexGenfv()"),
        new GLproperty(GL11.GL_FEEDBACK_BUFFER_POINTER, "GL_FEEDBACK_BUFFER_POINTER", "Pointer to feedback buffer", "feedback", "glGetPointerv()"),
        new GLproperty(GL11.GL_FEEDBACK_BUFFER_SIZE, "GL_FEEDBACK_BUFFER_SIZE", "Size of feedback buffer", "feedback", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_FEEDBACK_BUFFER_TYPE, "GL_FEEDBACK_BUFFER_TYPE", "Type of feedback buffer", "feedback", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_FOG, "GL_FOG", "True if fog enabled", "fog/enable", GL_IS_ENABLED),
        new GLproperty(GL11.GL_FOG_COLOR, "GL_FOG_COLOR", "Fog color", "fog", GL_GET_FLOATV),
        new GLproperty(GL11.GL_FOG_DENSITY, "GL_FOG_DENSITY", "Exponential fog density", "fog", GL_GET_FLOATV),
        new GLproperty(GL11.GL_FOG_END, "GL_FOG_END", "Linear fog end", "fog", GL_GET_FLOATV),
        new GLproperty(GL11.GL_FOG_HINT, "GL_FOG_HINT", "Fog hint", "hint", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_FOG_INDEX, "GL_FOG_INDEX", "Fog index", "fog", GL_GET_FLOATV),
        new GLproperty(GL11.GL_FOG_MODE, "GL_FOG_MODE", "Fog mode", "fog", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_FOG_START, "GL_FOG_START", "Linear fog start", "fog", GL_GET_FLOATV),
        new GLproperty(GL11.GL_FRONT_FACE, "GL_FRONT_FACE", "Polygon front-face CW/CCW indicator", "polygon", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_GREEN_BITS, "GL_GREEN_BITS", "Number of bits per green component in color buffers", "capability", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_INDEX_ARRAY, "GL_INDEX_ARRAY", "Color-index array enable", "vertex-array", GL_IS_ENABLED),
        new GLproperty(GL11.GL_INDEX_ARRAY_POINTER, "GL_INDEX_ARRAY_POINTER", "Pointer to the index array", "vertex-array", "glGetPointerv()"),
        new GLproperty(GL11.GL_INDEX_ARRAY_STRIDE, "GL_INDEX_ARRAY_STRIDE", "Stride between color indices", "vertex-array", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_INDEX_ARRAY_TYPE, "GL_INDEX_ARRAY_TYPE", "Type of color indices", "vertex-array", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_INDEX_BITS, "GL_INDEX_BITS", "Number of bits per index in color buffers", "capability", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_INDEX_CLEAR_VALUE, "GL_INDEX_CLEAR_VALUE", "Color-buffer clear value (color-index mode)", "color-buffer", GL_GET_FLOATV),
        new GLproperty(GL11.GL_INDEX_LOGIC_OP, "GL_INDEX_LOGIC_OP", "Color index logical operation enabled", "color-buffer/enable", GL_IS_ENABLED),
        new GLproperty(GL11.GL_INDEX_MODE, "GL_INDEX_MODE", "True if color buffers store indices", "capability", GL_GET_BOOLEANV),
        new GLproperty(GL11.GL_INDEX_OFFSET, "GL_INDEX_OFFSET", "Value of GL_INDEX_OFFSET", "pixel", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_INDEX_SHIFT, "GL_INDEX_SHIFT", "Value of GL_INDEX_SHIFT", "pixel", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_INDEX_WRITEMASK, "GL_INDEX_WRITEMASK", "Color-index writemask", "color-buffer", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_LIGHT0, "GL_LIGHT0", "True if light 0 enabled", "lighting/enable", GL_IS_ENABLED),
        new GLproperty(GL11.GL_LIGHT1, "GL_LIGHT1", "True if light 1 enabled", "lighting/enable", GL_IS_ENABLED),
        new GLproperty(GL11.GL_LIGHT2, "GL_LIGHT2", "True if light 2 enabled", "lighting/enable", GL_IS_ENABLED),
        new GLproperty(GL11.GL_LIGHT3, "GL_LIGHT3", "True if light 3 enabled", "lighting/enable", GL_IS_ENABLED),
        new GLproperty(GL11.GL_LIGHT4, "GL_LIGHT4", "True if light 4 enabled", "lighting/enable", GL_IS_ENABLED),
        new GLproperty(GL11.GL_LIGHT5, "GL_LIGHT5", "True if light 5 enabled", "lighting/enable", GL_IS_ENABLED),
        new GLproperty(GL11.GL_LIGHT6, "GL_LIGHT6", "True if light 6 enabled", "lighting/enable", GL_IS_ENABLED),
        new GLproperty(GL11.GL_LIGHT7, "GL_LIGHT7", "True if light 7 enabled", "lighting/enable", GL_IS_ENABLED),
        new GLproperty(GL11.GL_LIGHTING, "GL_LIGHTING", "True if lighting is enabled", "lighting/e nable", GL_IS_ENABLED),
        new GLproperty(GL11.GL_LIGHT_MODEL_AMBIENT, "GL_LIGHT_MODEL_AMBIENT", "Ambient scene color", "lighting", GL_GET_FLOATV),
        new GLproperty(GL11.GL_LIGHT_MODEL_LOCAL_VIEWER, "GL_LIGHT_MODEL_LOCAL_VIEWER", "Viewer is local", "lighting", GL_GET_BOOLEANV),
        new GLproperty(GL11.GL_LIGHT_MODEL_TWO_SIDE, "GL_LIGHT_MODEL_TWO_SIDE", "Use two-sided lighting", "lighting", GL_GET_BOOLEANV),
        new GLproperty(GL11.GL_LINEAR_ATTENUATION, "GL_LINEAR_ATTENUATION", "Linear attenuation factor", "lighting", "glGetLightfv()"),
        new GLproperty(GL11.GL_LINE_SMOOTH, "GL_LINE_SMOOTH", "Line antialiasing on", "line/enable", GL_IS_ENABLED),
        new GLproperty(GL11.GL_LINE_SMOOTH_HINT, "GL_LINE_SMOOTH_HINT", "Line smooth hint", "hint", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_LINE_STIPPLE, "GL_LINE_STIPPLE", "Line stipple enable", "line/enable", GL_IS_ENABLED),
        new GLproperty(GL11.GL_LINE_STIPPLE_PATTERN, "GL_LINE_STIPPLE_PATTERN", "Line stipple", "line", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_LINE_STIPPLE_REPEAT, "GL_LINE_STIPPLE_REPEAT", "Line stipple repeat", "line", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_LINE_WIDTH, "GL_LINE_WIDTH", "Line width", "line", GL_GET_FLOATV),
        new GLproperty(GL11.GL_LINE_WIDTH_GRANULARITY, "GL_LINE_WIDTH_GRANULARITY", "Antialiased line-width granularity", "capability", GL_GET_FLOATV),
        new GLproperty(GL11.GL_LINE_WIDTH_RANGE, "GL_LINE_WIDTH_RANGE", "Range (low to high) of antialiased line widths", "capability", GL_GET_FLOATV),
        new GLproperty(GL11.GL_LIST_BASE, "GL_LIST_BASE", "Setting of glListBase()", "list", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_LIST_INDEX, "GL_LIST_INDEX", "Number of display list under construction; 0 if none", "current", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_LIST_MODE, "GL_LIST_MODE", "Mode of display list under construction; undefined if none", "current", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_LOGIC_OP_MODE, "GL_LOGIC_OP_MODE", "Logical operation function", "color-buffer", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_MAP1_GRID_DOMAIN, "GL_MAP1_GRID_DOMAIN", "1D grid endpoints", "eval", GL_GET_FLOATV),
        new GLproperty(GL11.GL_MAP1_GRID_SEGMENTS, "GL_MAP1_GRID_SEGMENTS", "1D grid divisions", "eval", GL_GET_FLOATV),
        new GLproperty(GL11.GL_MAP2_GRID_DOMAIN, "GL_MAP2_GRID_DOMAIN", "2D grid endpoints", "eval", GL_GET_FLOATV),
        new GLproperty(GL11.GL_MAP2_GRID_SEGMENTS, "GL_MAP2_GRID_SEGMENTS", "2D grid divisions", "eval", GL_GET_FLOATV),
        new GLproperty(GL11.GL_MAP_COLOR, "GL_MAP_COLOR", "True if colors are mapped", "pixel", GL_GET_BOOLEANV),
        new GLproperty(GL11.GL_MAP_STENCIL, "GL_MAP_STENCIL", "True if stencil values are mapped", "pixel", GL_GET_BOOLEANV),
        new GLproperty(GL11.GL_MATRIX_MODE, "GL_MATRIX_MODE", "Current matrix mode", "transform", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_MAX_ATTRIB_STACK_DEPTH, "GL_MAX_ATTRIB_STACK_DEPTH", "Maximum depth of the attribute stack", "capability", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_MAX_CLIENT_ATTRIB_STACK_DEPTH, "GL_MAX_CLIENT_ATTRIB_STACK_DEPTH", "Maximum depth of the client attribute stack", "capability", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_MAX_CLIP_PLANES, "GL_MAX_CLIP_PLANES", "Maximum number of user clipping planes", "capability", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_MAX_EVAL_ORDER, "GL_MAX_EVAL_ORDER", "Maximum evaluator polynomial order", "capability", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_MAX_LIGHTS, "GL_MAX_LIGHTS", "Maximum number of lights", "capability", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_MAX_LIST_NESTING, "GL_MAX_LIST_NESTING", "Maximum display-list call nesting", "capability", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_MAX_MODELVIEW_STACK_DEPTH, "GL_MAX_MODELVIEW_STACK_DEPTH", "Maximum modelview-matrix stack depth", "capability", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_MAX_NAME_STACK_DEPTH, "GL_MAX_NAME_STACK_DEPTH", "Maximum selection-name stack depth", "capability", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_MAX_PIXEL_MAP_TABLE, "GL_MAX_PIXEL_MAP_TABLE", "Maximum size of a glPixelMap() translation table", "capability", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_MAX_PROJECTION_STACK_DEPTH, "GL_MAX_PROJECTION_STACK_DEPTH", "Maximum projection-matrix stack depth", "capability", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_MAX_TEXTURE_SIZE, "GL_MAX_TEXTURE_SIZE", "See discussion in Texture Proxy in Chapter 9", "capability", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_MAX_TEXTURE_STACK_DEPTH, "GL_MAX_TEXTURE_STACK_DEPTH", "Maximum depth of texture matrix stack", "capability", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_MAX_VIEWPORT_DIMS, "GL_MAX_VIEWPORT_DIMS", "Maximum viewport dimensions", "capability", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_MODELVIEW_MATRIX, "GL_MODELVIEW_MATRIX", "Modelview matrix stack", "matrix", GL_GET_FLOATV),
        new GLproperty(GL11.GL_MODELVIEW_STACK_DEPTH, "GL_MODELVIEW_STACK_DEPTH", "Modelview matrix stack pointer", "matrix", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_NAME_STACK_DEPTH, "GL_NAME_STACK_DEPTH", "Name stack depth", "current", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_NORMALIZE, "GL_NORMALIZE", "Current normal normalization on/off", "transform/ enable", GL_IS_ENABLED),
        new GLproperty(GL11.GL_NORMAL_ARRAY, "GL_NORMAL_ARRAY", "Normal array enable", "vertex-array", GL_IS_ENABLED),
        new GLproperty(GL11.GL_NORMAL_ARRAY_POINTER, "GL_NORMAL_ARRAY_POINTER", "Pointer to the normal array", "vertex-array", "glGetPointerv()"),
        new GLproperty(GL11.GL_NORMAL_ARRAY_STRIDE, "GL_NORMAL_ARRAY_STRIDE", "Stride between normals", "vertex-array", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_NORMAL_ARRAY_TYPE, "GL_NORMAL_ARRAY_TYPE", "Type of normal coordinates", "vertex-array", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_OBJECT_PLANE, "GL_OBJECT_PLANE", "Texgen object linear coefficients", "texture", "glGetTexGenfv()"),
        new GLproperty(GL11.GL_ORDER, "GL_ORDER", "1D map order", "capability", "glGetMapiv()"),
        new GLproperty(GL11.GL_ORDER, "GL_ORDER", "2D map orders", "capability", "glGetMapiv()"),
        new GLproperty(GL11.GL_PACK_ALIGNMENT, "GL_PACK_ALIGNMENT", "Value of GL_PACK_ALIGNMENT", "pixel-store", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_PACK_LSB_FIRST, "GL_PACK_LSB_FIRST", "Value of GL_PACK_LSB_FIRST", "pixel-store", GL_GET_BOOLEANV),
        new GLproperty(GL11.GL_PACK_ROW_LENGTH, "GL_PACK_ROW_LENGTH", "Value of GL_PACK_ROW_LENGTH", "pixel-store", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_PACK_SKIP_PIXELS, "GL_PACK_SKIP_PIXELS", "Value of GL_PACK_SKIP_PIXELS", "pixel-store", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_PACK_SKIP_ROWS, "GL_PACK_SKIP_ROWS", "Value of GL_PACK_SKIP_ROWS", "pixel-store", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_PACK_SWAP_BYTES, "GL_PACK_SWAP_BYTES", "Value of GL_PACK_SWAP_BYTES", "pixel-store", GL_GET_BOOLEANV),
        new GLproperty(GL11.GL_PERSPECTIVE_CORRECTION_HINT, "GL_PERSPECTIVE_CORRECTION_HINT", "Perspective correction hint", "hint", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_POINT_SIZE, "GL_POINT_SIZE", "Point size", "point", GL_GET_FLOATV),
        new GLproperty(GL11.GL_POINT_SIZE_GRANULARITY, "GL_POINT_SIZE_GRANULARITY", "Antialiased point-size granularity", "capability", GL_GET_FLOATV),
        new GLproperty(GL11.GL_POINT_SIZE_RANGE, "GL_POINT_SIZE_RANGE", "Range (low to high) of antialiased point sizes", "capability", GL_GET_FLOATV),
        new GLproperty(GL11.GL_POINT_SMOOTH, "GL_POINT_SMOOTH", "Point antialiasing on", "point/enable", GL_IS_ENABLED),
        new GLproperty(GL11.GL_POINT_SMOOTH_HINT, "GL_POINT_SMOOTH_HINT", "Point smooth hint", "hint", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_POLYGON_MODE, "GL_POLYGON_MODE", "Polygon rasterization mode (front and back)", "polygon", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_POLYGON_OFFSET_FACTOR, "GL_POLYGON_OFFSET_FACTOR", "Polygon offset factor", "polygon", GL_GET_FLOATV),
        new GLproperty(GL11.GL_POLYGON_OFFSET_FILL, "GL_POLYGON_OFFSET_FILL", "Polygon offset enable for GL_FILL mode rasterization", "polygon/enable", GL_IS_ENABLED),
        new GLproperty(GL11.GL_POLYGON_OFFSET_LINE, "GL_POLYGON_OFFSET_LINE", "Polygon offset enable for GL_LINE mode rasterization", "polygon/enable", GL_IS_ENABLED),
        new GLproperty(GL11.GL_POLYGON_OFFSET_POINT, "GL_POLYGON_OFFSET_POINT", "Polygon offset enable for GL_POINT mode rasterization", "polygon/enable", GL_IS_ENABLED),
        new GLproperty(GL11.GL_POLYGON_SMOOTH, "GL_POLYGON_SMOOTH", "Polygon antialiasing on", "polygon/enable", GL_IS_ENABLED),
        new GLproperty(GL11.GL_POLYGON_SMOOTH_HINT, "GL_POLYGON_SMOOTH_HINT", "Polygon smooth hint", "hint", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_POLYGON_STIPPLE, "GL_POLYGON_STIPPLE", "Polygon stipple enable", "polygon/enable", GL_IS_ENABLED),
        new GLproperty(GL11.GL_POSITION, "GL_POSITION", "Position of light i", "lighting", "glGetLightfv()"),
        new GLproperty(GL11.GL_PROJECTION_MATRIX, "GL_PROJECTION_MATRIX", "Projection matrix stack", "matrix", GL_GET_FLOATV),
        new GLproperty(GL11.GL_PROJECTION_STACK_DEPTH, "GL_PROJECTION_STACK_DEPTH", "Projection matrix stack pointer", "matrix", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_QUADRATIC_ATTENUATION, "GL_QUADRATIC_ATTENUATION", "Quadratic attenuation factor", "lighting", "glGetLightfv()"),
        new GLproperty(GL11.GL_READ_BUFFER, "GL_READ_BUFFER", "Read source buffer", "pixel", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_RED_BITS, "GL_RED_BITS", "Number of bits per red component in color buffers", "capability", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_RENDER_MODE, "GL_RENDER_MODE", "glRenderMode() setting", "current", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_RGBA_MODE, "GL_RGBA_MODE", "True if color buffers store RGBA", "capability", GL_GET_BOOLEANV),
        new GLproperty(GL11.GL_SCISSOR_BOX, "GL_SCISSOR_BOX", "Scissor box", "scissor", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_SCISSOR_TEST, "GL_SCISSOR_TEST", "Scissoring enabled", "scissor/enable", GL_IS_ENABLED),
        new GLproperty(GL11.GL_SELECTION_BUFFER_POINTER, "GL_SELECTION_BUFFER_POINTER", "Pointer to selection buffer", "select", "glGetPointerv()"),
        new GLproperty(GL11.GL_SELECTION_BUFFER_SIZE, "GL_SELECTION_BUFFER_SIZE", "Size of selection buffer", "select", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_SHADE_MODEL, "GL_SHADE_MODEL", "glShadeModel() setting", "lighting", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_SHININESS, "GL_SHININESS", "Specular exponent of material", "lighting", "glGetMaterialfv()"),
        new GLproperty(GL11.GL_SPECULAR, "GL_SPECULAR", "Specular intensity of light i", "lighting", "glGetLightfv()"),
        new GLproperty(GL11.GL_SPECULAR, "GL_SPECULAR", "Specular material color", "lighting", "glGetMaterialfv()"),
        new GLproperty(GL11.GL_SPOT_CUTOFF, "GL_SPOT_CUTOFF", "Spotlight angle of light i", "lighting", "glGetLightfv()"),
        new GLproperty(GL11.GL_SPOT_DIRECTION, "GL_SPOT_DIRECTION", "Spotlight direction of light i", "lighting", "glGetLightfv()"),
        new GLproperty(GL11.GL_SPOT_EXPONENT, "GL_SPOT_EXPONENT", "Spotlight exponent of light i", "lighting", "glGetLightfv()"),
        new GLproperty(GL11.GL_STENCIL_BITS, "GL_STENCIL_BITS", "Number of stencil bitplanes", "capability", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_STENCIL_CLEAR_VALUE, "GL_STENCIL_CLEAR_VALUE", "Stencil-buffer clear value", "stencil-buffer", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_STENCIL_FAIL, "GL_STENCIL_FAIL", "Stencil fail action", "stencil-buffer", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_STENCIL_FUNC, "GL_STENCIL_FUNC", "Stencil function", "stencil-buffer", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_STENCIL_PASS_DEPTH_FAIL, "GL_STENCIL_PASS_DEPTH_FAIL", "Stencil depth buffer fail action", "stencil-buffer", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_STENCIL_PASS_DEPTH_PASS, "GL_STENCIL_PASS_DEPTH_PASS", "Stencil depth buffer pass action", "stencil-buffer", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_STENCIL_REF, "GL_STENCIL_REF", "Stencil reference value", "stencil-buffer", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_STENCIL_TEST, "GL_STENCIL_TEST", "Stenciling enabled", "stencil-buffer/enable", GL_IS_ENABLED),
        new GLproperty(GL11.GL_STENCIL_VALUE_MASK, "GL_STENCIL_VALUE_MASK", "Stencil mask", "stencil-buffer", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_STENCIL_WRITEMASK, "GL_STENCIL_WRITEMASK", "Stencil-buffer writemask", "stencil-buffer", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_STEREO, "GL_STEREO", "True if left and right buffers exist", "capability", GL_GET_BOOLEANV),
        new GLproperty(GL11.GL_SUBPIXEL_BITS, "GL_SUBPIXEL_BITS", "Number of bits of subpixel precision in x and y", "capability", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_TEXTURE, "GL_TEXTURE", "x-D texture image at level of detail i", "UNUSED", "glGetTexImage()"),
        new GLproperty(GL11.GL_TEXTURE_1D, "GL_TEXTURE_1D", "True if 1-D texturing enabled ", "texture/enable", GL_IS_ENABLED),
        new GLproperty(GL11.GL_TEXTURE_2D, "GL_TEXTURE_2D", "True if 2-D texturing enabled ", "texture/enable", GL_IS_ENABLED),
        new GLproperty(GL11.GL_TEXTURE_ALPHA_SIZE, "GL_TEXTURE_ALPHA_SIZE", "x-D texture image i's alpha resolution", "UNUSED", "glGetTexLevelParameter*()"),
        new GLproperty(GL11.GL_TEXTURE_BINDING_1D, "GL_TEXTURE_BINDING_1D", "Texture object bound to GL_TEXTURE_1D", "texture", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_TEXTURE_BINDING_2D, "GL_TEXTURE_BINDING_2D", "Texture object bound to GL_TEXTURE_2D", "texture", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_TEXTURE_BLUE_SIZE, "GL_TEXTURE_BLUE_SIZE", "x-D texture image i's blue resolution", "UNUSED", "glGetTexLevelParameter*()"),
        new GLproperty(GL11.GL_TEXTURE_BORDER, "GL_TEXTURE_BORDER", "x-D texture image i's border width", "UNUSED", "glGetTexLevelParameter*()"),
        new GLproperty(GL11.GL_TEXTURE_BORDER_COLOR, "GL_TEXTURE_BORDER_COLOR", "Texture border color", "texture", "glGetTexParameter*()"),
        new GLproperty(GL11.GL_TEXTURE_COORD_ARRAY, "GL_TEXTURE_COORD_ARRAY", "Texture coordinate array enable", "vertex-array", GL_IS_ENABLED),
        new GLproperty(GL11.GL_TEXTURE_COORD_ARRAY_POINTER, "GL_TEXTURE_COORD_ARRAY_POINTER", "Pointer to the texture coordinate array", "vertex-array", "glGetPointerv()"),
        new GLproperty(GL11.GL_TEXTURE_COORD_ARRAY_SIZE, "GL_TEXTURE_COORD_ARRAY_SIZE", "Texture coordinates per element", "vertex-array", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_TEXTURE_COORD_ARRAY_STRIDE, "GL_TEXTURE_COORD_ARRAY_STRIDE", "Stride between texture coordinates", "vertex-array", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_TEXTURE_COORD_ARRAY_TYPE, "GL_TEXTURE_COORD_ARRAY_TYPE", "Type of texture coordinates", "vertex-array", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_TEXTURE_ENV_COLOR, "GL_TEXTURE_ENV_COLOR", "Texture environment color", "texture", "glGetTexEnvfv()"),
        new GLproperty(GL11.GL_TEXTURE_ENV_MODE, "GL_TEXTURE_ENV_MODE", "Texture application function", "texture", "glGetTexEnviv()"),
        new GLproperty(GL11.GL_TEXTURE_GEN_MODE, "GL_TEXTURE_GEN_MODE", "Function used for texgen", "texture", "glGetTexGeniv()"),
        new GLproperty(GL11.GL_TEXTURE_GEN_Q, "GL_TEXTURE_GEN_Q", "Texgen enabled (x is S, T, R, or Q)", "texture/enable", GL_IS_ENABLED),
        new GLproperty(GL11.GL_TEXTURE_GEN_R, "GL_TEXTURE_GEN_R", "Texgen enabled (x is S, T, R, or Q)", "texture/enable", GL_IS_ENABLED),
        new GLproperty(GL11.GL_TEXTURE_GEN_S, "GL_TEXTURE_GEN_S", "Texgen enabled (x is S, T, R, or Q)", "texture/enable", GL_IS_ENABLED),
        new GLproperty(GL11.GL_TEXTURE_GEN_T, "GL_TEXTURE_GEN_T", "Texgen enabled (x is S, T, R, or Q)", "texture/enable", GL_IS_ENABLED),
        new GLproperty(GL11.GL_TEXTURE_GREEN_SIZE, "GL_TEXTURE_GREEN_SIZE", "x-D texture image i's green resolution", "UNUSED", "glGetTexLevelParameter*()"),
        new GLproperty(GL11.GL_TEXTURE_HEIGHT, "GL_TEXTURE_HEIGHT", "x-D texture image i's height", "UNUSED", "glGetTexLevelParameter*()"),
        new GLproperty(GL11.GL_TEXTURE_INTENSITY_SIZE, "GL_TEXTURE_INTENSITY_SIZE", "x-D texture image i's intensity resolution", "UNUSED", "glGetTexLevelParameter*()"),
        new GLproperty(GL11.GL_TEXTURE_LUMINANCE_SIZE, "GL_TEXTURE_LUMINANCE_SIZE", "x-D texture image i's luminance resolution", "UNUSED", "glGetTexLevelParameter*()"),
        new GLproperty(GL11.GL_TEXTURE_MAG_FILTER, "GL_TEXTURE_MAG_FILTER", "Texture magnification function", "texture", "glGetTexParameter*()"),
        new GLproperty(GL11.GL_TEXTURE_MATRIX, "GL_TEXTURE_MATRIX", "Texture matrix stack", "matrix", GL_GET_FLOATV),
        new GLproperty(GL11.GL_TEXTURE_MIN_FILTER, "GL_TEXTURE_MIN_FILTER", "Texture minification function", "texture", "glGetTexParameter*()"),
        new GLproperty(GL11.GL_TEXTURE_PRIORITY, "GL_TEXTURE_PRIORITY", "Texture object priority", "texture", "glGetTexParameter*()"),
        new GLproperty(GL11.GL_TEXTURE_RED_SIZE, "GL_TEXTURE_RED_SIZE", "x-D texture image i's red resolution", "UNUSED", "glGetTexLevelParameter*()"),
        new GLproperty(GL11.GL_TEXTURE_STACK_DEPTH, "GL_TEXTURE_STACK_DEPTH", "Texture matrix stack pointer", "matrix", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_TEXTURE_WIDTH, "GL_TEXTURE_WIDTH", "x-D texture image i's width", "UNUSED", "glGetTexLevelParameter*()"),
        new GLproperty(GL11.GL_TEXTURE_WRAP_S, "GL_TEXTURE_WRAP_S", "Texture wrap mode (x is S or T)", "texture", "glGetTexParameter*()"),
        new GLproperty(GL11.GL_TEXTURE_WRAP_T, "GL_TEXTURE_WRAP_T", "Texture wrap mode (x is S or T)", "texture", "glGetTexParameter*()"),
        new GLproperty(GL11.GL_UNPACK_ALIGNMENT, "GL_UNPACK_ALIGNMENT", "Value of GL_UNPACK_ALIGNMENT", "pixel-store", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_UNPACK_LSB_FIRST, "GL_UNPACK_LSB_FIRST", "Value of GL_UNPACK_LSB_FIRST", "pixel-store", GL_GET_BOOLEANV),
        new GLproperty(GL11.GL_UNPACK_ROW_LENGTH, "GL_UNPACK_ROW_LENGTH", "Value of GL_UNPACK_ROW_LENGTH", "pixel-store", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_UNPACK_SKIP_PIXELS, "GL_UNPACK_SKIP_PIXELS", "Value of GL_UNPACK_SKIP_PIXELS", "pixel-store", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_UNPACK_SKIP_ROWS, "GL_UNPACK_SKIP_ROWS", "Value of GL_UNPACK_SKIP_ROWS", "pixel-store", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_UNPACK_SWAP_BYTES, "GL_UNPACK_SWAP_BYTES", "Value of GL_UNPACK_SWAP_BYTES", "pixel-store", GL_GET_BOOLEANV),
        new GLproperty(GL11.GL_VERTEX_ARRAY, "GL_VERTEX_ARRAY", "Vertex array enable", "vertex-array", GL_IS_ENABLED),
        new GLproperty(GL11.GL_VERTEX_ARRAY_POINTER, "GL_VERTEX_ARRAY_POINTER", "Pointer to the vertex array", "vertex-array", "glGetPointerv()"),
        new GLproperty(GL11.GL_VERTEX_ARRAY_SIZE, "GL_VERTEX_ARRAY_SIZE", "Coordinates per vertex", "vertex-array", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_VERTEX_ARRAY_STRIDE, "GL_VERTEX_ARRAY_STRIDE", "Stride between vertices", "vertex-array", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_VERTEX_ARRAY_TYPE, "GL_VERTEX_ARRAY_TYPE", "Type of vertex coordinates", "vertex-array", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_VIEWPORT, "GL_VIEWPORT", "Viewport origin and extent", "viewport", GL_GET_INTEGERV),
        new GLproperty(GL11.GL_ZOOM_X, "GL_ZOOM_X", "x zoom factor", "pixel", GL_GET_FLOATV),
        new GLproperty(GL11.GL_ZOOM_Y, "GL_ZOOM_Y", "y zoom factor", "pixel", GL_GET_FLOATV),
    };

    public static void dumpToFile(Consumer<Consumer<String>> dumper, String fileName) {
        final List<String> lines = new ArrayList<>();
        dumper.accept(lines::add);
        try {
            FileUtils.writeLines(new File(fileName), lines);
        } catch (IOException e) {
            LOGGER.error("Failed to write file");
            e.printStackTrace();
        }
    }

    public static void dumpStateToFile() {
        dumpToFile(p -> dumpState(p, false), "gl-dump-state.txt");
    }
    public static void dumpGLSMStateToFile() {
        dumpToFile(p -> dumpState(p, true), "glsm-dump-state.txt");
    }

    public static void dumpState(boolean glsm) {
        dumpState(LINE_LOGGER, glsm);
    }

    public static void dumpState(Consumer<String> printer, boolean glsm) {
        for (int i = 0; i < instance.propertyList.length; ++i) {
            final GLproperty gLProperty = instance.propertyList[i];
            printer.accept(gLProperty.name + ":" + gLProperty.getAsString(glsm) + " (" + gLProperty.description + ")");
        }
    }

    public static void dumpIsEnabledToFile(boolean glsm) {
        dumpToFile(p -> dumpIsEnabled(p, glsm), (glsm ? "glsm" : "gl")  + "-dump-enabled.txt");
    }

    public static void dumpIsEnabled(boolean glsm) {
        dumpIsEnabled(LINE_LOGGER, glsm);
    }

    public static void dumpIsEnabled(Consumer<String> printer, boolean glsm) {
        for (int i = 0; i < instance.propertyList.length; ++i) {
            final GLproperty gLProperty = instance.propertyList[i];
            if (gLProperty.fetchCommand.equals(GL_IS_ENABLED)) {
                printer.accept(gLProperty.name + ":");
                printer.accept("" + gLProperty.getAsString(glsm));
                printer.accept(" (" + gLProperty.description + ")");
            }
        }
    }

    public static void dumpTypeToFile(String type, boolean glsm) {
        dumpToFile(p -> dumpType(type, p, glsm), (glsm ? "glsm" : "gl") + "-dump-type-" + type + ".txt");
    }

    public static void dumpType(String type, boolean glsm) {
        dumpType(type, LINE_LOGGER, glsm);
    }

    public static void dumpType(String type, Consumer<String> printer, boolean glsm) {
        for (int i = 0; i < instance.propertyList.length; ++i) {
            final GLproperty gLProperty = instance.propertyList[i];
            if (gLProperty.category.equals(type)) {
                printer.accept(gLProperty.name + ":");
                printer.accept(gLProperty.getAsString(glsm));
                printer.accept(" (" + gLProperty.description + ")");
            }
        }
    }

    public static void checkGLSM() {
        boolean mismatch = false;
        for (int i = 0; i < instance.propertyList.length; ++i) {
            final GLproperty gLProperty = instance.propertyList[i];
            if(gLProperty.fetchCommand.equals(GL_GET_FLOATV) && gLProperty.name.endsWith("_MATRIX")) {
                final Matrix4f cached = gLProperty.getAsMatrix4f(true);
                final Matrix4f uncached = gLProperty.getAsMatrix4f(false);
                // Some precision issues due to RAD vs degrees, and translated (vs f)
                if (!cached.equals(uncached, 0.001f)) {
                    LOGGER.error("GLSM mismatch: " + gLProperty.name + "\ncached:\n" + cached + "uncached:\n" + uncached);
                    mismatch = true;
                }
            } else {
                final String cached = gLProperty.getAsString(true);
                final String uncached = gLProperty.getAsString(false);
                if (!cached.equals(uncached)) {
                    LOGGER.error("GLSM mismatch: " + gLProperty.name + " cached: " + cached + " uncached: " + uncached);
                    mismatch = true;
                }
            }
        }
        if (!mismatch) {
            LOGGER.info("GLSM state matches!");
        }
    }

}
