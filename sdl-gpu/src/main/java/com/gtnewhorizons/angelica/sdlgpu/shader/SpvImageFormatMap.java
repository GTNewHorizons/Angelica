package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.glsm.texture.InternalTextureFormat;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.util.spvc.Spv;
import org.lwjgl.util.spvc.Spvc;

public final class SpvImageFormatMap {
    private SpvImageFormatMap() {}

    public static int glInternalFormat(int spvImageFormat) {
        final InternalTextureFormat fmt = switch (spvImageFormat) {
            case Spv.SpvImageFormatRgba32f -> InternalTextureFormat.RGBA32F;
            case Spv.SpvImageFormatRgba16f -> InternalTextureFormat.RGBA16F;
            case Spv.SpvImageFormatR32f -> InternalTextureFormat.R32F;
            case Spv.SpvImageFormatRgba8 -> InternalTextureFormat.RGBA8;
            case Spv.SpvImageFormatRgba8Snorm -> InternalTextureFormat.RGBA8_SNORM;
            case Spv.SpvImageFormatRg32f -> InternalTextureFormat.RG32F;
            case Spv.SpvImageFormatRg16f -> InternalTextureFormat.RG16F;
            case Spv.SpvImageFormatR11fG11fB10f -> InternalTextureFormat.R11F_G11F_B10F;
            case Spv.SpvImageFormatR16f -> InternalTextureFormat.R16F;
            case Spv.SpvImageFormatRgba16 -> InternalTextureFormat.RGBA16;
            case Spv.SpvImageFormatRgb10A2 -> InternalTextureFormat.RGB10_A2;
            case Spv.SpvImageFormatRg16 -> InternalTextureFormat.RG16;
            case Spv.SpvImageFormatRg8 -> InternalTextureFormat.RG8;
            case Spv.SpvImageFormatR16 -> InternalTextureFormat.R16;
            case Spv.SpvImageFormatR8 -> InternalTextureFormat.R8;
            case Spv.SpvImageFormatRgba16Snorm -> InternalTextureFormat.RGBA16_SNORM;
            case Spv.SpvImageFormatRg16Snorm -> InternalTextureFormat.RG16_SNORM;
            case Spv.SpvImageFormatRg8Snorm -> InternalTextureFormat.RG8_SNORM;
            case Spv.SpvImageFormatR16Snorm -> InternalTextureFormat.R16_SNORM;
            case Spv.SpvImageFormatR8Snorm -> InternalTextureFormat.R8_SNORM;
            case Spv.SpvImageFormatRgba32i -> InternalTextureFormat.RGBA32I;
            case Spv.SpvImageFormatRgba16i -> InternalTextureFormat.RGBA16I;
            case Spv.SpvImageFormatRgba8i -> InternalTextureFormat.RGBA8I;
            case Spv.SpvImageFormatR32i -> InternalTextureFormat.R32I;
            case Spv.SpvImageFormatRg32i -> InternalTextureFormat.RG32I;
            case Spv.SpvImageFormatRg16i -> InternalTextureFormat.RG16I;
            case Spv.SpvImageFormatRg8i -> InternalTextureFormat.RG8I;
            case Spv.SpvImageFormatR16i -> InternalTextureFormat.R16I;
            case Spv.SpvImageFormatR8i -> InternalTextureFormat.R8I;
            case Spv.SpvImageFormatRgba32ui -> InternalTextureFormat.RGBA32UI;
            case Spv.SpvImageFormatRgba16ui -> InternalTextureFormat.RGBA16UI;
            case Spv.SpvImageFormatRgba8ui -> InternalTextureFormat.RGBA8UI;
            case Spv.SpvImageFormatR32ui -> InternalTextureFormat.R32UI;
            case Spv.SpvImageFormatRgb10a2ui -> InternalTextureFormat.RGB10_A2UI;
            case Spv.SpvImageFormatRg32ui -> InternalTextureFormat.RG32UI;
            case Spv.SpvImageFormatRg16ui -> InternalTextureFormat.RG16UI;
            case Spv.SpvImageFormatRg8ui -> InternalTextureFormat.RG8UI;
            case Spv.SpvImageFormatR16ui -> InternalTextureFormat.R16UI;
            case Spv.SpvImageFormatR8ui -> InternalTextureFormat.R8UI;
            default -> null;
        };
        return fmt != null ? fmt.getGlFormat() : 0;
    }

    public static int glTextureTarget(int spvDim, boolean arrayed) {
        return switch (spvDim) {
            case Spv.SpvDim3D -> GL12.GL_TEXTURE_3D;
            case Spv.SpvDimCube -> GL13.GL_TEXTURE_CUBE_MAP;
            default -> arrayed ? GL30.GL_TEXTURE_2D_ARRAY : GL11.GL_TEXTURE_2D;
        };
    }

    public static int glFormatForSampledType(int spvBaseType) {
        return switch (spvBaseType) {
            case Spvc.SPVC_BASETYPE_UINT32 -> InternalTextureFormat.R32UI.getGlFormat();
            case Spvc.SPVC_BASETYPE_INT32 -> InternalTextureFormat.R32I.getGlFormat();
            default -> InternalTextureFormat.RGBA8.getGlFormat();
        };
    }
}
