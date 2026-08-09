package com.gtnewhorizons.angelica.glsm;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

public final class GLESFormatRemap {

    private GLESFormatRemap() {}

    public record Result(int internalFormat, int format, int type) {}

    public static Result apply(int internalformat, int format, int type, boolean isGLES) {
        internalformat = promoteAlphaFormat(internalformat);
        if (isGLES) {
            internalformat = remapInternalFormat(internalformat);
            if (type == GL12.GL_UNSIGNED_INT_8_8_8_8_REV && (format == GL12.GL_BGRA || format == GL11.GL_RGBA)) {
                type = GL11.GL_UNSIGNED_BYTE;
            }
            if (isGenericPixelType(type)) {
                type = typeForInternalFormatES32(internalformat, type);
            }
        }
        return new Result(internalformat, format, type);
    }

    public static int promoteAlphaFormat(int internalformat) {
        return switch (internalformat) {
            case GL11.GL_ALPHA4 -> GL11.GL_RGBA4;
            case GL11.GL_ALPHA8 -> GL11.GL_RGBA8;
            case GL11.GL_ALPHA12 -> GL11.GL_RGBA12;
            case GL11.GL_ALPHA16 -> GL11.GL_RGBA16;
            default -> internalformat;
        };
    }

    public static int remapInternalFormat(int internalformat) {
        return switch (internalformat) {
            case GL11.GL_RGB16, GL11.GL_RGB12 -> GL30.GL_RGB16F;
            case GL11.GL_RGBA16, GL11.GL_RGBA12 -> GL30.GL_RGBA16F;
            case GL30.GL_R16 -> GL30.GL_R16F;
            case GL30.GL_RG16 -> GL30.GL_RG16F;
            case GL11.GL_RGB10, GL11.GL_R3_G3_B2 -> GL11.GL_RGB8;
            default -> internalformat;
        };
    }

    public static int remapPixelType(int format, int type) {
        if (type == GL12.GL_UNSIGNED_INT_8_8_8_8_REV && (format == GL12.GL_BGRA || format == GL11.GL_RGBA)) {
            return GL11.GL_UNSIGNED_BYTE;
        }
        return type;
    }

    public static int typeForInternalFormatES32(int internalformat, int type) {
        return switch (internalformat) {
            case GL30.GL_R16F, GL30.GL_RG16F, GL30.GL_RGB16F, GL30.GL_RGBA16F -> GL30.GL_HALF_FLOAT;
            case GL30.GL_R32F, GL30.GL_RG32F, GL30.GL_RGB32F, GL30.GL_RGBA32F -> GL11.GL_FLOAT;
            case GL31.GL_R8_SNORM, GL31.GL_RG8_SNORM, GL31.GL_RGB8_SNORM, GL31.GL_RGBA8_SNORM -> GL11.GL_BYTE;
            case GL30.GL_R11F_G11F_B10F, GL30.GL_RGB9_E5 -> GL30.GL_HALF_FLOAT;
            case GL11.GL_RGB10_A2 -> GL12.GL_UNSIGNED_INT_2_10_10_10_REV;
            case GL30.GL_R8I, GL30.GL_RG8I, GL30.GL_RGB8I, GL30.GL_RGBA8I,
                 GL30.GL_R16I, GL30.GL_RG16I, GL30.GL_RGB16I, GL30.GL_RGBA16I,
                 GL30.GL_R32I, GL30.GL_RG32I, GL30.GL_RGB32I, GL30.GL_RGBA32I -> GL11.GL_INT;
            case GL30.GL_R8UI, GL30.GL_RG8UI, GL30.GL_RGB8UI, GL30.GL_RGBA8UI,
                 GL30.GL_R16UI, GL30.GL_RG16UI, GL30.GL_RGB16UI, GL30.GL_RGBA16UI,
                 GL30.GL_R32UI, GL30.GL_RG32UI, GL30.GL_RGB32UI, GL30.GL_RGBA32UI -> GL11.GL_UNSIGNED_INT;
            case GL14.GL_DEPTH_COMPONENT24 -> GL11.GL_UNSIGNED_INT;
            case GL30.GL_DEPTH_COMPONENT32F -> GL11.GL_FLOAT;
            case GL30.GL_DEPTH24_STENCIL8 -> GL30.GL_UNSIGNED_INT_24_8;
            case GL30.GL_DEPTH32F_STENCIL8 -> GL30.GL_FLOAT_32_UNSIGNED_INT_24_8_REV;
            default -> type;
        };
    }

    public static boolean isGenericPixelType(int type) {
        return type == GL11.GL_UNSIGNED_BYTE
            || type == GL11.GL_UNSIGNED_SHORT
            || type == GL11.GL_UNSIGNED_INT
            || type == GL11.GL_BYTE
            || type == GL11.GL_SHORT
            || type == GL11.GL_INT;
    }
}
