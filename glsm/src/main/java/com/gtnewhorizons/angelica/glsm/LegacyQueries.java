package com.gtnewhorizons.angelica.glsm;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

final class LegacyQueries {

    static final int BOOL = 1;
    static final int INT = 2;
    static final int FLOAT = 3;
    static final int FLOATN = 4;

    private static final int COUNT_SHIFT = 3;

    private LegacyQueries() {}

    static int desc(int pname) {
        return switch (pname) {
            case GL11.GL_ALPHA_TEST, GL11.GL_AUTO_NORMAL, GL11.GL_COLOR_MATERIAL, GL14.GL_COLOR_SUM, GL11.GL_FOG,
                 GL11.GL_INDEX_LOGIC_OP, GL11.GL_LIGHTING, GL11.GL_LIGHT0, GL11.GL_LIGHT1, GL11.GL_LIGHT2, GL11.GL_LIGHT3,
                 GL11.GL_LIGHT4, GL11.GL_LIGHT5, GL11.GL_LIGHT6, GL11.GL_LIGHT7, GL11.GL_LINE_STIPPLE, GL11.GL_MAP1_COLOR_4,
                 GL11.GL_MAP1_INDEX, GL11.GL_MAP1_NORMAL, GL11.GL_MAP1_TEXTURE_COORD_1, GL11.GL_MAP1_TEXTURE_COORD_2,
                 GL11.GL_MAP1_TEXTURE_COORD_3, GL11.GL_MAP1_TEXTURE_COORD_4, GL11.GL_MAP1_VERTEX_3, GL11.GL_MAP1_VERTEX_4,
                 GL11.GL_MAP2_COLOR_4, GL11.GL_MAP2_INDEX, GL11.GL_MAP2_NORMAL, GL11.GL_MAP2_TEXTURE_COORD_1,
                 GL11.GL_MAP2_TEXTURE_COORD_2, GL11.GL_MAP2_TEXTURE_COORD_3, GL11.GL_MAP2_TEXTURE_COORD_4,
                 GL11.GL_MAP2_VERTEX_3, GL11.GL_MAP2_VERTEX_4, GL11.GL_NORMALIZE, GL11.GL_POINT_SMOOTH,
                 GL11.GL_POLYGON_STIPPLE, GL12.GL_RESCALE_NORMAL, GL11.GL_TEXTURE_1D, GL12.GL_TEXTURE_3D,
                 GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_GEN_S, GL11.GL_TEXTURE_GEN_T, GL11.GL_TEXTURE_GEN_R,
                 GL11.GL_TEXTURE_GEN_Q, GL11.GL_LIGHT_MODEL_LOCAL_VIEWER, GL11.GL_LIGHT_MODEL_TWO_SIDE,
                 GL11.GL_VERTEX_ARRAY, GL11.GL_NORMAL_ARRAY, GL11.GL_COLOR_ARRAY, GL11.GL_TEXTURE_COORD_ARRAY,
                 GL11.GL_INDEX_ARRAY, GL11.GL_EDGE_FLAG_ARRAY, GL14.GL_SECONDARY_COLOR_ARRAY, GL14.GL_FOG_COORDINATE_ARRAY,
                 GL11.GL_EDGE_FLAG, GL20.GL_POINT_SPRITE, GL20.GL_VERTEX_PROGRAM_TWO_SIDE -> BOOL;
            case GL11.GL_ALPHA_TEST_FUNC, GL11.GL_LIST_BASE, GL11.GL_MATRIX_MODE, GL11.GL_SHADE_MODEL,
                 GL11.GL_COLOR_MATERIAL_FACE, GL11.GL_COLOR_MATERIAL_PARAMETER, GL12.GL_LIGHT_MODEL_COLOR_CONTROL,
                 GL11.GL_FOG_MODE, GL11.GL_LINE_STIPPLE_PATTERN, GL11.GL_LINE_STIPPLE_REPEAT, GL13.GL_CLIENT_ACTIVE_TEXTURE,
                 GL20.GL_MAX_TEXTURE_COORDS, GL11.GL_RENDER_MODE, GL11.GL_FOG_HINT, GL11.GL_PERSPECTIVE_CORRECTION_HINT,
                 GL11.GL_POINT_SMOOTH_HINT, GL14.GL_GENERATE_MIPMAP_HINT, GL11.GL_LINE_SMOOTH_HINT, GL11.GL_POLYGON_SMOOTH_HINT,
                 GL13.GL_TEXTURE_COMPRESSION_HINT, GL20.GL_FRAGMENT_SHADER_DERIVATIVE_HINT, GL20.GL_POINT_SPRITE_COORD_ORIGIN,
                 GL11.GL_VERTEX_ARRAY_SIZE, GL11.GL_VERTEX_ARRAY_TYPE, GL11.GL_VERTEX_ARRAY_STRIDE, GL15.GL_VERTEX_ARRAY_BUFFER_BINDING,
                 GL11.GL_NORMAL_ARRAY_TYPE, GL11.GL_NORMAL_ARRAY_STRIDE, GL15.GL_NORMAL_ARRAY_BUFFER_BINDING,
                 GL11.GL_COLOR_ARRAY_SIZE, GL11.GL_COLOR_ARRAY_TYPE, GL11.GL_COLOR_ARRAY_STRIDE, GL15.GL_COLOR_ARRAY_BUFFER_BINDING,
                 GL11.GL_INDEX_ARRAY_TYPE, GL11.GL_INDEX_ARRAY_STRIDE, GL15.GL_INDEX_ARRAY_BUFFER_BINDING,
                 GL11.GL_TEXTURE_COORD_ARRAY_SIZE, GL11.GL_TEXTURE_COORD_ARRAY_TYPE, GL11.GL_TEXTURE_COORD_ARRAY_STRIDE,
                 GL15.GL_TEXTURE_COORD_ARRAY_BUFFER_BINDING, GL11.GL_EDGE_FLAG_ARRAY_STRIDE, GL15.GL_EDGE_FLAG_ARRAY_BUFFER_BINDING,
                 GL14.GL_SECONDARY_COLOR_ARRAY_SIZE, GL14.GL_SECONDARY_COLOR_ARRAY_TYPE, GL14.GL_SECONDARY_COLOR_ARRAY_STRIDE,
                 GL15.GL_SECONDARY_COLOR_ARRAY_BUFFER_BINDING, GL14.GL_FOG_COORDINATE_ARRAY_TYPE, GL14.GL_FOG_COORDINATE_ARRAY_STRIDE,
                 GL15.GL_FOG_COORDINATE_ARRAY_BUFFER_BINDING -> INT;
            case GL11.GL_FOG_DENSITY, GL11.GL_FOG_START, GL11.GL_FOG_END, GL14.GL_POINT_SIZE_MIN, GL14.GL_POINT_SIZE_MAX,
                 GL14.GL_POINT_FADE_THRESHOLD_SIZE -> FLOAT;
            case GL11.GL_ALPHA_TEST_REF -> FLOATN;
            case GL14.GL_POINT_DISTANCE_ATTENUATION -> FLOAT | 2 << COUNT_SHIFT;
            case GL11.GL_CURRENT_TEXTURE_COORDS -> FLOAT | 3 << COUNT_SHIFT;
            case GL11.GL_MODELVIEW_MATRIX, GL11.GL_PROJECTION_MATRIX, GL11.GL_TEXTURE_MATRIX -> FLOATN | 15 << COUNT_SHIFT;
            case GL11.GL_CURRENT_NORMAL -> FLOATN | 2 << COUNT_SHIFT;
            case GL11.GL_CURRENT_COLOR, GL14.GL_CURRENT_SECONDARY_COLOR, GL11.GL_FOG_COLOR, GL11.GL_LIGHT_MODEL_AMBIENT -> FLOATN | 3 << COUNT_SHIFT;
            default -> 0;
        };
    }

    static int type(int desc) {
        return desc & 7;
    }

    static int count(int desc) {
        return (desc >> COUNT_SHIFT) + 1;
    }

    static int floatToInt(float value) {
        return (int) (2147483647.0 * value);
    }

    static int lround(float value) {
        return value < 0 ? -Math.round(-value) : Math.round(value);
    }

    private static int toInt(int type, float value) {
        return type == FLOATN ? floatToInt(value) : lround(value);
    }

    private static FloatBuffer fillScratch(GLContextState glCtx, int pname) {
        final FloatBuffer scratch = glCtx.queryScratch;
        scratch.clear();
        GLStateManager.glGetFloat(pname, scratch);
        return scratch;
    }

    private static IllegalStateException uncached(int pname) {
        return new IllegalStateException("Legacy pname 0x" + Integer.toHexString(pname) + " has no cached answer for its declared type");
    }

    static int getInteger(GLContextState glCtx, int pname, int desc) {
        final int type = type(desc);
        if (count(desc) > 1) {
            return toInt(type, fillScratch(glCtx, pname).get(0));
        }
        return switch (type) {
            case BOOL -> GLStateManager.glGetBoolean(glCtx, pname) ? GL11.GL_TRUE : GL11.GL_FALSE;
            case FLOAT -> lround(GLStateManager.glGetFloat(glCtx, pname));
            case FLOATN -> floatToInt(GLStateManager.glGetFloat(glCtx, pname));
            default -> throw uncached(pname);
        };
    }

    static float getFloat(GLContextState glCtx, int pname, int desc) {
        if (count(desc) > 1) {
            return fillScratch(glCtx, pname).get(0);
        }
        return switch (type(desc)) {
            case BOOL -> GLStateManager.glGetBoolean(glCtx, pname) ? 1.0f : 0.0f;
            case INT -> (float) GLStateManager.glGetInteger(glCtx, pname);
            default -> throw uncached(pname);
        };
    }

    static boolean getBoolean(GLContextState glCtx, int pname, int desc) {
        if (count(desc) > 1) {
            return fillScratch(glCtx, pname).get(0) != 0.0f;
        }
        return switch (type(desc)) {
            case INT -> GLStateManager.glGetInteger(glCtx, pname) != 0;
            case FLOAT, FLOATN -> GLStateManager.glGetFloat(glCtx, pname) != 0.0f;
            default -> throw uncached(pname);
        };
    }

    static boolean getIntegerv(GLContextState glCtx, int pname, IntBuffer params) {
        final int desc = desc(pname);
        if (desc == 0) return false;
        final int pos = params.position();
        final int type = type(desc);
        final FloatBuffer scratch = fillScratch(glCtx, pname);
        for (int i = 0, count = count(desc); i < count; i++) {
            params.put(pos + i, toInt(type, scratch.get(i)));
        }
        return true;
    }

    static boolean getIntegerv(GLContextState glCtx, int pname, int[] params) {
        final int desc = desc(pname);
        if (desc == 0) return false;
        final int type = type(desc);
        final FloatBuffer scratch = fillScratch(glCtx, pname);
        for (int i = 0, count = Math.min(count(desc), params.length); i < count; i++) {
            params[i] = toInt(type, scratch.get(i));
        }
        return true;
    }

    static boolean getBooleanv(GLContextState glCtx, int pname, ByteBuffer params) {
        final int desc = desc(pname);
        if (desc == 0) return false;
        final int pos = params.position();
        final FloatBuffer scratch = fillScratch(glCtx, pname);
        for (int i = 0, count = count(desc); i < count; i++) {
            params.put(pos + i, (byte) (scratch.get(i) != 0.0f ? GL11.GL_TRUE : GL11.GL_FALSE));
        }
        return true;
    }
}
