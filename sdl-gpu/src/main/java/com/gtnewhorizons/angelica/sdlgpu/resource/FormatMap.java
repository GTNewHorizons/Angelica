package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.glsm.GLTypes;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL43;
import org.lwjgl.util.spvc.Spvc;

import static org.lwjgl.sdl.SDLGPU.*;

public final class FormatMap {
    private FormatMap() {}

    public static int mapPrimitiveType(int glMode) {
        return switch (glMode) {
            case GL11.GL_TRIANGLES -> SDL_GPU_PRIMITIVETYPE_TRIANGLELIST;
            case GL11.GL_TRIANGLE_STRIP -> SDL_GPU_PRIMITIVETYPE_TRIANGLESTRIP;
            case GL11.GL_LINES -> SDL_GPU_PRIMITIVETYPE_LINELIST;
            case GL11.GL_LINE_STRIP -> SDL_GPU_PRIMITIVETYPE_LINESTRIP;
            case GL11.GL_POINTS -> SDL_GPU_PRIMITIVETYPE_POINTLIST;
            case GL11.GL_TRIANGLE_FAN -> SDL_GPU_PRIMITIVETYPE_TRIANGLELIST;
            case GL11.GL_QUADS -> SDL_GPU_PRIMITIVETYPE_TRIANGLELIST;
            default -> SDL_GPU_PRIMITIVETYPE_TRIANGLELIST;
        };
    }

    public static int mapIndexElementSize(int glType) {
        return switch (glType) {
            case GL11.GL_UNSIGNED_SHORT -> SDL_GPU_INDEXELEMENTSIZE_16BIT;
            case GL11.GL_UNSIGNED_INT -> SDL_GPU_INDEXELEMENTSIZE_32BIT;
            default -> SDL_GPU_INDEXELEMENTSIZE_32BIT;
        };
    }

    public static int indexElementSize(int glType) {
        return GLTypes.sizeBytes(glType);
    }

    public static int mapFilter(int glFilter) {
        return switch (glFilter) {
            case GL11.GL_NEAREST, GL11.GL_NEAREST_MIPMAP_NEAREST, GL11.GL_NEAREST_MIPMAP_LINEAR -> SDL_GPU_FILTER_NEAREST;
            default -> SDL_GPU_FILTER_LINEAR;
        };
    }

    public static int mapMipmapMode(int glMinFilter) {
        return switch (glMinFilter) {
            case GL11.GL_NEAREST_MIPMAP_NEAREST, GL11.GL_LINEAR_MIPMAP_NEAREST -> SDL_GPU_SAMPLERMIPMAPMODE_NEAREST;
            case GL11.GL_NEAREST_MIPMAP_LINEAR, GL11.GL_LINEAR_MIPMAP_LINEAR -> SDL_GPU_SAMPLERMIPMAPMODE_LINEAR;
            default -> SDL_GPU_SAMPLERMIPMAPMODE_NEAREST;
        };
    }

    public static boolean isMipmapMinFilter(int glMinFilter) {
        return switch (glMinFilter) {
            case GL11.GL_NEAREST_MIPMAP_NEAREST,
                 GL11.GL_LINEAR_MIPMAP_NEAREST,
                 GL11.GL_NEAREST_MIPMAP_LINEAR,
                 GL11.GL_LINEAR_MIPMAP_LINEAR -> true;
            default -> false;
        };
    }

    public static int mapAddressMode(int glWrap) {
        return switch (glWrap) {
            case GL12.GL_CLAMP_TO_EDGE -> SDL_GPU_SAMPLERADDRESSMODE_CLAMP_TO_EDGE;
            case GL14.GL_MIRRORED_REPEAT -> SDL_GPU_SAMPLERADDRESSMODE_MIRRORED_REPEAT;
            default -> SDL_GPU_SAMPLERADDRESSMODE_REPEAT;
        };
    }

    private static final int GEOMETRY_USAGE = SDL_GPU_BUFFERUSAGE_VERTEX | SDL_GPU_BUFFERUSAGE_INDEX | SDL_GPU_BUFFERUSAGE_COMPUTE_STORAGE_READ;

    public static int mapBufferUsage(int glTarget) {
        return switch (glTarget) {
            case GL15.GL_ARRAY_BUFFER          -> GEOMETRY_USAGE;
            case GL15.GL_ELEMENT_ARRAY_BUFFER  -> GEOMETRY_USAGE;
            case GL40.GL_DRAW_INDIRECT_BUFFER  -> SDL_GPU_BUFFERUSAGE_INDIRECT;
            case GL31.GL_UNIFORM_BUFFER        -> SDL_GPU_BUFFERUSAGE_GRAPHICS_STORAGE_READ;
            case GL43.GL_SHADER_STORAGE_BUFFER -> SDL_GPU_BUFFERUSAGE_GRAPHICS_STORAGE_READ
                | SDL_GPU_BUFFERUSAGE_COMPUTE_STORAGE_READ
                | SDL_GPU_BUFFERUSAGE_COMPUTE_STORAGE_WRITE
                | SDL_GPU_BUFFERUSAGE_INDIRECT;
            default -> SDL_GPU_BUFFERUSAGE_VERTEX | SDL_GPU_BUFFERUSAGE_INDEX | SDL_GPU_BUFFERUSAGE_INDIRECT;
        };
    }

    public static int mapStencilOp(int glOp) {
        return switch (glOp) {
            case GL11.GL_KEEP -> SDL_GPU_STENCILOP_KEEP;
            case GL11.GL_ZERO -> SDL_GPU_STENCILOP_ZERO;
            case GL11.GL_REPLACE -> SDL_GPU_STENCILOP_REPLACE;
            case GL11.GL_INCR -> SDL_GPU_STENCILOP_INCREMENT_AND_CLAMP;
            case GL11.GL_DECR -> SDL_GPU_STENCILOP_DECREMENT_AND_CLAMP;
            case GL11.GL_INVERT -> SDL_GPU_STENCILOP_INVERT;
            case GL14.GL_INCR_WRAP -> SDL_GPU_STENCILOP_INCREMENT_AND_WRAP;
            case GL14.GL_DECR_WRAP -> SDL_GPU_STENCILOP_DECREMENT_AND_WRAP;
            default -> SDL_GPU_STENCILOP_KEEP;
        };
    }

    public static int mapBlendFactor(int glFactor) {
        return switch (glFactor) {
            case GL11.GL_ZERO -> SDL_GPU_BLENDFACTOR_ZERO;
            case GL11.GL_ONE -> SDL_GPU_BLENDFACTOR_ONE;
            case GL11.GL_SRC_COLOR -> SDL_GPU_BLENDFACTOR_SRC_COLOR;
            case GL11.GL_ONE_MINUS_SRC_COLOR -> SDL_GPU_BLENDFACTOR_ONE_MINUS_SRC_COLOR;
            case GL11.GL_SRC_ALPHA -> SDL_GPU_BLENDFACTOR_SRC_ALPHA;
            case GL11.GL_ONE_MINUS_SRC_ALPHA -> SDL_GPU_BLENDFACTOR_ONE_MINUS_SRC_ALPHA;
            case GL11.GL_DST_ALPHA -> SDL_GPU_BLENDFACTOR_DST_ALPHA;
            case GL11.GL_ONE_MINUS_DST_ALPHA -> SDL_GPU_BLENDFACTOR_ONE_MINUS_DST_ALPHA;
            case GL11.GL_DST_COLOR -> SDL_GPU_BLENDFACTOR_DST_COLOR;
            case GL11.GL_ONE_MINUS_DST_COLOR -> SDL_GPU_BLENDFACTOR_ONE_MINUS_DST_COLOR;
            case GL11.GL_SRC_ALPHA_SATURATE -> SDL_GPU_BLENDFACTOR_SRC_ALPHA_SATURATE;
            case GL14.GL_CONSTANT_COLOR -> SDL_GPU_BLENDFACTOR_CONSTANT_COLOR;
            case GL14.GL_ONE_MINUS_CONSTANT_COLOR -> SDL_GPU_BLENDFACTOR_ONE_MINUS_CONSTANT_COLOR;
            default -> SDL_GPU_BLENDFACTOR_ONE;
        };
    }

    public static int mapBlendOp(int glMode) {
        return switch (glMode) {
            case GL14.GL_FUNC_ADD -> SDL_GPU_BLENDOP_ADD;
            case GL14.GL_FUNC_SUBTRACT -> SDL_GPU_BLENDOP_SUBTRACT;
            case GL14.GL_FUNC_REVERSE_SUBTRACT -> SDL_GPU_BLENDOP_REVERSE_SUBTRACT;
            case GL14.GL_MIN -> SDL_GPU_BLENDOP_MIN;
            case GL14.GL_MAX -> SDL_GPU_BLENDOP_MAX;
            default -> SDL_GPU_BLENDOP_ADD;
        };
    }

    public static int mapCompareOp(int glFunc) {
        return switch (glFunc) {
            case GL11.GL_NEVER -> SDL_GPU_COMPAREOP_NEVER;
            case GL11.GL_LESS -> SDL_GPU_COMPAREOP_LESS;
            case GL11.GL_EQUAL -> SDL_GPU_COMPAREOP_EQUAL;
            case GL11.GL_LEQUAL -> SDL_GPU_COMPAREOP_LESS_OR_EQUAL;
            case GL11.GL_GREATER -> SDL_GPU_COMPAREOP_GREATER;
            case GL11.GL_NOTEQUAL -> SDL_GPU_COMPAREOP_NOT_EQUAL;
            case GL11.GL_GEQUAL -> SDL_GPU_COMPAREOP_GREATER_OR_EQUAL;
            case GL11.GL_ALWAYS -> SDL_GPU_COMPAREOP_ALWAYS;
            default -> SDL_GPU_COMPAREOP_LESS;
        };
    }

    public static int mapMemberInfoToGlType(ShaderManager.UniformMemberInfo umi) {
        final int base = umi.baseType();
        final int v = umi.vectorSize();
        final int c = umi.columns();
        if (base == Spvc.SPVC_BASETYPE_FP32 || base == Spvc.SPVC_BASETYPE_FP16) {
            if (c == 1) return switch (v) {
                case 1 -> GL11.GL_FLOAT;
                case 2 -> GL20.GL_FLOAT_VEC2;
                case 3 -> GL20.GL_FLOAT_VEC3;
                default -> GL20.GL_FLOAT_VEC4;
            };
            if (c == v) return switch (v) {
                case 2 -> GL20.GL_FLOAT_MAT2;
                case 3 -> GL20.GL_FLOAT_MAT3;
                default -> GL20.GL_FLOAT_MAT4;
            };
            return GL20.GL_FLOAT_MAT4;
        }
        if (base == Spvc.SPVC_BASETYPE_INT32) {
            return switch (v) {
                case 1 -> GL11.GL_INT;
                case 2 -> GL20.GL_INT_VEC2;
                case 3 -> GL20.GL_INT_VEC3;
                default -> GL20.GL_INT_VEC4;
            };
        }
        if (base == Spvc.SPVC_BASETYPE_UINT32) {
            return switch (v) {
                case 1 -> GL30.GL_UNSIGNED_INT;
                case 2 -> GL30.GL_UNSIGNED_INT_VEC2;
                case 3 -> GL30.GL_UNSIGNED_INT_VEC3;
                default -> GL30.GL_UNSIGNED_INT_VEC4;
            };
        }
        if (base == Spvc.SPVC_BASETYPE_BOOLEAN) {
            return switch (v) {
                case 1 -> GL20.GL_BOOL;
                case 2 -> GL20.GL_BOOL_VEC2;
                case 3 -> GL20.GL_BOOL_VEC3;
                default -> GL20.GL_BOOL_VEC4;
            };
        }
        return GL20.GL_FLOAT_VEC4;
    }

    public static int remapUnsignedIntToBool(int glType) {
        return switch (glType) {
            case GL11.GL_UNSIGNED_INT -> GL20.GL_BOOL;
            case GL30.GL_UNSIGNED_INT_VEC2 -> GL20.GL_BOOL_VEC2;
            case GL30.GL_UNSIGNED_INT_VEC3 -> GL20.GL_BOOL_VEC3;
            case GL30.GL_UNSIGNED_INT_VEC4 -> GL20.GL_BOOL_VEC4;
            default -> glType;
        };
    }
}
