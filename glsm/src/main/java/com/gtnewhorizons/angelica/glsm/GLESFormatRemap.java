package com.gtnewhorizons.angelica.glsm;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

public final class GLESFormatRemap {

    private GLESFormatRemap() {}

    /** Remapped (internalformat, format, type) triplet for a GLES texture allocation. */
    public record Result(int internalFormat, int format, int type) {}

    /** Full triplet remap. Desktop path only runs alpha-format promotion; ES adds the
     *  internalformat / packed-type remap and type-match for half-float internal formats. */
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

    /** Deprecated alpha-sized formats -> RGBA equivalents. Always runs (desktop + ES). */
    public static int promoteAlphaFormat(int internalformat) {
        return switch (internalformat) {
            case GL11.GL_ALPHA4 -> GL11.GL_RGBA4;
            case GL11.GL_ALPHA8 -> GL11.GL_RGBA8;
            case GL11.GL_ALPHA12 -> GL11.GL_RGBA12;
            case GL11.GL_ALPHA16 -> GL11.GL_RGBA16;
            default -> internalformat;
        };
    }

    /** 16/12-bit UNORM color formats aren't sized in ES - swap to half-float (same 16 bpc storage,
     *  acceptable for Iris composite color attachments). RGB10/R3_G3_B2 collapse to RGB8. */
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

    /** GLES rejects GL_UNSIGNED_INT_8_8_8_8_REV. On little-endian hosts the in-memory byte order
     *  matches GL_BGRA + GL_UNSIGNED_BYTE (requires GL_EXT_texture_format_BGRA8888; present on Mesa ES). */
    public static int remapPixelType(int format, int type) {
        if (type == GL12.GL_UNSIGNED_INT_8_8_8_8_REV && (format == GL12.GL_BGRA || format == GL11.GL_RGBA)) {
            return GL11.GL_UNSIGNED_BYTE;
        }
        return type;
    }

    /** ES validates (internalFormat, format, type) strictly even for NULL pixels - patch the client
     *  type to match a post-remap internal format (e.g. RGB16F -> HALF_FLOAT). */
    public static int typeForInternalFormatES32(int internalformat, int type) {
        return switch (internalformat) {
            // 16-bit float - ES accepts HALF_FLOAT or FLOAT.
            case GL30.GL_R16F, GL30.GL_RG16F, GL30.GL_RGB16F, GL30.GL_RGBA16F -> GL30.GL_HALF_FLOAT;
            // 32-bit float - FLOAT only.
            case GL30.GL_R32F, GL30.GL_RG32F, GL30.GL_RGB32F, GL30.GL_RGBA32F -> GL11.GL_FLOAT;
            // 8-bit signed normalized - BYTE.
            case GL31.GL_R8_SNORM, GL31.GL_RG8_SNORM, GL31.GL_RGB8_SNORM, GL31.GL_RGBA8_SNORM -> GL11.GL_BYTE;
            // Packed-float color - ES accepts the packed type or HALF_FLOAT/FLOAT.
            case GL30.GL_R11F_G11F_B10F, GL30.GL_RGB9_E5 -> GL30.GL_HALF_FLOAT;
            // 10-bit RGB + 2-bit alpha - only the packed type is legal.
            case GL11.GL_RGB10_A2 -> GL12.GL_UNSIGNED_INT_2_10_10_10_REV;
            // Integer formats (ES 3.0+). Caller sets format = *_INTEGER; here we match the type.
            case GL30.GL_R8I, GL30.GL_RG8I, GL30.GL_RGB8I, GL30.GL_RGBA8I,
                 GL30.GL_R16I, GL30.GL_RG16I, GL30.GL_RGB16I, GL30.GL_RGBA16I,
                 GL30.GL_R32I, GL30.GL_RG32I, GL30.GL_RGB32I, GL30.GL_RGBA32I -> GL11.GL_INT;
            case GL30.GL_R8UI, GL30.GL_RG8UI, GL30.GL_RGB8UI, GL30.GL_RGBA8UI,
                 GL30.GL_R16UI, GL30.GL_RG16UI, GL30.GL_RGB16UI, GL30.GL_RGBA16UI,
                 GL30.GL_R32UI, GL30.GL_RG32UI, GL30.GL_RGB32UI, GL30.GL_RGBA32UI -> GL11.GL_UNSIGNED_INT;
            // Depth / depth-stencil - matches ES Table 8.2.
            case GL14.GL_DEPTH_COMPONENT24 -> GL11.GL_UNSIGNED_INT;
            case GL30.GL_DEPTH_COMPONENT32F -> GL11.GL_FLOAT;
            case GL30.GL_DEPTH24_STENCIL8 -> GL30.GL_UNSIGNED_INT_24_8;
            case GL30.GL_DEPTH32F_STENCIL8 -> GL30.GL_FLOAT_32_UNSIGNED_INT_24_8_REV;
            default -> type;
        };
    }

    /** Only remap types the caller is likely passing as a default; already-valid types
     *  (HALF_FLOAT, FLOAT, packed types, etc.) are left alone. */
    public static boolean isGenericPixelType(int type) {
        return type == GL11.GL_UNSIGNED_BYTE
            || type == GL11.GL_UNSIGNED_SHORT
            || type == GL11.GL_UNSIGNED_INT
            || type == GL11.GL_BYTE
            || type == GL11.GL_SHORT
            || type == GL11.GL_INT;
    }
}
